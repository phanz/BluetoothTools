package com.phanz.classic;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.phanz.common.Config;
import com.phanz.common.Connector;
import com.phanz.common.Controller;
import com.phanz.common.Request;
import com.phanz.common.Scanner;
import com.phanz.common.LogUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by hanzai.peng on 2017/12/13.
 */

public class ClassicConnector extends Connector implements Scanner{
    public static final String TAG = "[Bluetooth]ClassicConnector";

    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;
    private OnScanListener mOnScanListener;
    private Set<BluetoothDevice> mBluetoothDeviceSet;
    private boolean mScanning;
    //连接相关
    private BluetoothDevice mRemoteDevice;
    private IStateListener mBleStateListener;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    //发送相关
    private ClassicController mController;

    private static ClassicConnector sInstance = null;

    public static synchronized ClassicConnector getInstance(Context context){
        if(sInstance == null){
            synchronized (ClassicConnector.class){
                Context appContext = context.getApplicationContext();
                sInstance = new ClassicConnector(appContext);
            }
        }
        return sInstance;
    }

    public ClassicConnector(@NonNull Context context){
        mContext = context.getApplicationContext();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothDeviceSet = new HashSet<>();
        mScanning = false;
        mController = new ClassicController();

        mBleStateListener = new IStateListener() {
            @Override
            public void onStateChange(int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    LogUtils.d(TAG, "BluetoothProfile.STATE_CONNECTED");
                }else if(newState == BluetoothProfile.STATE_CONNECTING){
                    stopScan();
                    LogUtils.d(TAG, "BluetoothProfile.STATE_CONNECTING");

                } else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                    mController.closePool(Request.CONNECT_LOST);
                    LogUtils.d(TAG, "BluetoothProfile.STATE_DISCONNECTED");
                }
            }
        };
        registerStateListener(mBleStateListener);

        LogUtils.v(TAG,"Classic客户端监听蓝牙状态变化");
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        mContext.registerReceiver(mBtStatusChangeReceiver,filter);
    }

    @Override
    public void startScan(OnScanListener listener) {
        LogUtils.d(TAG,"Classic客户端开始扫描");
        mScanning = true;
        mBluetoothDeviceSet.clear();
        mOnScanListener = listener;
        mBluetoothAdapter.startDiscovery();
    }

    @Override
    public boolean isScanning() {
        return mScanning;
    }

    @Override
    public void stopScan() {
        LogUtils.d(TAG,"Classic客户端开始扫描");
        mScanning = false;
        mBluetoothDeviceSet.clear();
        mBluetoothAdapter.cancelDiscovery();
    }

    @Override
    public void connect(BluetoothDevice device) {
        LogUtils.d(TAG,"尝试通过BluetoothDevice对象连接设备,地址：" + device.getAddress());
        setConnectStates(Connector.STATE_CONNECTING);
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
    }

    @Override
    public void connect(final String address) {
        LogUtils.d(TAG,"尝试通过地址连接设备,地址：" + address);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if(device.getType() != BluetoothDevice.DEVICE_TYPE_UNKNOWN){
            connect(device);
        }else{
            startScan(new OnScanListener() {
                @Override
                public void onDeviceFound(BluetoothDevice device, Map<String, Object> extras) {
                    if(device.getAddress().equalsIgnoreCase(address)){
                        connect(device);
                    }
                }

                @Override
                public void onScanTimeout() {
                    LogUtils.d(TAG,String.format("没有扫描到地址为：%d的设备",address));
                }
            });
        }
    }

    @Override
    public Controller getController() {
        return mController;
    }

    @Override
    public void disconnect() {
        String address = (mRemoteDevice != null) ? mRemoteDevice.getAddress() : "null";
        LogUtils.d(TAG,"Classic客户端断开连接（关闭Socket及其IO流）,地址：" + address);

        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

    }

    @Override
    public void close() {
        LogUtils.d(TAG,"Classic客户端释放蓝牙资源");
        disconnect();
        unregisterStateListener(mBleStateListener);
    }

    private void manageConnectedSocket(BluetoothSocket socket){
        if(socket == null){
            LogUtils.e(TAG,"Classic客户端Socket为null");
            return;
        }

        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                tmp = device.createRfcommSocketToServiceRecord(Config.CLASSIC_RFC);
            } catch (IOException e) {
                e.printStackTrace();
                setConnectStates(Connector.STATE_DISCONNECTED);
            }
            mmSocket = tmp;
        }

        public void run() {
            mBluetoothAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
            } catch (IOException connectException) {
                connectException.printStackTrace();
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
                return;
            }
            manageConnectedSocket(mmSocket);
            setConnectStates(Connector.STATE_CONNECTED);
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    private class ConnectedThread extends Thread {
        private BluetoothSocket mmSocket;
        private InputStream mmInStream;
        private OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                mmSocket = socket;
                mRemoteDevice = socket.getRemoteDevice();
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
                String name = TextUtils.isEmpty(mRemoteDevice.getName()) ? "Unknown" : mRemoteDevice.getAddress();
                String address = mRemoteDevice.getAddress();
                LogUtils.d(TAG,String.format("Classic服务端有新连接，远程设备名：%s, 地址：%s",name,address));
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            mController.init(mmInStream,mmOutStream);
            setConnectStates(Connector.STATE_CONNECTED);
        }

        public void run() {
            byte[] buf = new byte[1024];
            // Keep listening to the InputStream while connected
            while (getConnectStates()[1] == Connector.STATE_CONNECTED) {
                try {
                    // Read from the InputStream
                    int len = mmInStream.read(buf);
                    byte[] temp = new byte[len];
                    System.arraycopy(buf,0,temp,0,len);
                    mController.onReceived(Config.CLASSIC_RFC,temp);
                } catch (IOException e) {
                    e.printStackTrace();
                    // TODO: 2017/12/25 这里是连接断开的地方
                    setConnectStates(Connector.STATE_DISCONNECTED);
                    break;
                }
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public BroadcastReceiver mBtStatusChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                // TODO: 2017/12/3 获取需要连接的地址
                final String address = mRemoteDevice.getAddress();
                if(state == BluetoothAdapter.STATE_ON && BluetoothAdapter.checkBluetoothAddress(address)){
                    LogUtils.d(TAG,"蓝牙服务打开，尝试重连设备，当前线程名称:"+ Thread.currentThread().getName());
                    connect(address);
                }
            }else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if( !mBluetoothDeviceSet.contains(device) ){
                    String name = TextUtils.isEmpty(device.getName()) ? "Unknown" : device.getName();
                    String address = device.getAddress();
                    LogUtils.d(TAG,String.format("扫描到蓝牙设备，名称：%s, 地址：%s ",name,address));
                    mBluetoothDeviceSet.add(device);
                    if(mOnScanListener != null){
                        mOnScanListener.onDeviceFound(device,null);
                    }
                }
            }
        }
    };
}
