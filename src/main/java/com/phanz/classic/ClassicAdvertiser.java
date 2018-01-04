package com.phanz.classic;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.phanz.ble.BleAdvertiser;
import com.phanz.common.Advertiser;
import com.phanz.common.Config;
import com.phanz.common.Connector;
import com.phanz.common.Controller;
import com.phanz.common.LogUtils;
import com.phanz.common.Request;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by hanzai.peng on 2017/12/13.
 */

public class ClassicAdvertiser extends Connector implements Advertiser{
    public static final String TAG = "[Bluetooth]ClassicAdvertiser";

    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;

    //广播相关
    private boolean mAdvertising;

    //连接相关
    private BluetoothDevice mRemoteDevice;
    private IStateListener mClassicStateListener;
    private AcceptThread mAcceptThread;
    private ConnectedThread mConnectedThread;
    private ClassicController mController;

    private static ClassicAdvertiser sInstance = null;

    public static synchronized ClassicAdvertiser getInstance(Context context){
        if(sInstance == null){
            synchronized (BleAdvertiser.class){
                Context appContext = context.getApplicationContext();
                sInstance = new ClassicAdvertiser(appContext);
            }
        }
        return sInstance;
    }

    private  ClassicAdvertiser(@NonNull Context context){
        mContext = context.getApplicationContext();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mAdvertising = false;
        mController = new ClassicController();

        mClassicStateListener = new IStateListener() {
            @Override
            public void onStateChange(int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    LogUtils.d(TAG, "BluetoothProfile.STATE_CONNECTED");
                }else if(newState == BluetoothProfile.STATE_CONNECTING){
                    LogUtils.d(TAG, "BluetoothProfile.STATE_CONNECTING");

                } else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                    mController.closePool(Request.CONNECT_LOST);
                    LogUtils.d(TAG, "BluetoothProfile.STATE_DISCONNECTED");
                    startAdvertisement();
                }
            }
        };
        registerStateListener(mClassicStateListener);

        LogUtils.v(TAG,"Classic服务端监听蓝牙状态变化");
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mBtStatusChangeReceiver,filter);
    }

    @Override
    public void startAdvertisement() {
        LogUtils.d(TAG,"Classic服务端开启蓝牙广播，使本机可见");
        if(mBluetoothAdapter == null){
            LogUtils.e(TAG,"本机不支持蓝牙");
            return;
        }
        if(!mBluetoothAdapter.enable()){
            LogUtils.e(TAG,"本机蓝牙未开启");
            return;
        }
        if(mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE){
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            mContext.startActivity(discoverableIntent);

        }
        if(mAcceptThread != null){
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        mAcceptThread = new AcceptThread();
        mAcceptThread.start();
        mAdvertising = true;
        setConnectStates(Connector.STATE_LISTENING);
    }

    @Override
    public boolean isAdvertising() {
        return mAdvertising;
    }

    @Override
    public void stopAdvertisement() {
        LogUtils.d(TAG,"关闭经典蓝牙广播[经典蓝牙没有关闭广播的选项]");
        mAdvertising = false;
        close();
    }

    @Override
    public void connect(BluetoothDevice device) {
        LogUtils.d(TAG,"暂不支持服务端主动连接");
    }

    @Override
    public void connect(String address) {
        LogUtils.d(TAG,"暂不支持服务端主动连接");
    }

    @Override
    public Controller getController() {
        return mController;
    }

    @Override
    public void disconnect() {
        String address = (mRemoteDevice != null) ? mRemoteDevice.getAddress() : "null";
        LogUtils.d(TAG,"Classic服务端断开连接（关闭Socket及其IO流）,地址：" + address);
        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
    }

    @Override
    public void close() {
        LogUtils.d(TAG,"Classic服务端释放蓝牙资源");
        disconnect();
        if(mAcceptThread != null){
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
    }

    private void manageConnectedSocket(BluetoothSocket socket){
        // TODO: 2017/12/25 服务端应针对每个Socket连接创建一个新线程进行交互
        if(socket == null){
            LogUtils.e(TAG,"Classic服务端Socket为null");
            return;
        }

        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("ClassicServer", Config.CLASSIC_RFC);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    // Do work to manage the connection (in a separate thread)
                    manageConnectedSocket(socket);
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                mmServerSocket.close();
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
                    mController.notifyDataReceived(Config.CLASSIC_RFC,temp);
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
                final String address = "";
                if(state == BluetoothAdapter.STATE_ON && BluetoothAdapter.checkBluetoothAddress(address)){
                    LogUtils.d(TAG,"Thread:"+ Thread.currentThread().getName());
                    connect(address);
                }
            }
        }
    };
}
