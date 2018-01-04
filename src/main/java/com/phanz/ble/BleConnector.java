package com.phanz.ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;

import com.phanz.common.BluetoothUtils;
import com.phanz.common.Config;
import com.phanz.common.Connector;
import com.phanz.common.Controller;
import com.phanz.common.LogUtils;
import com.phanz.common.Request;
import com.phanz.common.Scanner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Created by hanzai.peng on 2016/12/26.
 */

public class BleConnector extends Connector implements Scanner{
    public static final String TAG = "[Bluetooth]BleConnector";

    private BluetoothAdapter mBluetoothAdapter;
    private Context mContext;
    private Handler mHandler;

    //扫描相关
    private BluetoothLeScanner mBleScanner;
    private List<ScanFilter> mBleScanFilters;
    private ScanSettings mScanSettings;
    private OnScanListener mOnScanListener;
    private Set<BluetoothDevice> mBluetoothDeviceSet;
    private static final  int SCAN_TIMES = 5;  // scan 5 time
    private static final int SCAN_DURATION = 5; // scan 5 seconds per scan
    private boolean mScanning;
    private int mScanCount;

    //连接相关
    private BluetoothDevice mRemoteDevice;
    private BluetoothGatt mBluetoothGatt;

    private IStateListener mBleStateListener;

    //发送相关
    private BleClientController mController;

    private static BleConnector sInstance = null;

    public static synchronized BleConnector getInstance(Context context){
        if(sInstance == null){
            synchronized (BleConnector.class){
                Context appContext = context.getApplicationContext();
                sInstance = new BleConnector(appContext);
            }
        }
        return sInstance;
    }

    private BleConnector(Context context){
        mContext = context;
        BluetoothManager bluetoothManager =
                (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mHandler = new Handler(Looper.getMainLooper());

        mBleScanFilters = new ArrayList<>();
        ParcelUuid parcelUuid = new ParcelUuid(Config.ADVERTISE_SERVICE);
        if(android.os.Build.VERSION.SDK_INT >= 21){
            mBleScanner = mBluetoothAdapter.getBluetoothLeScanner();
            mScanSettings = new ScanSettings.Builder().build();
            ScanFilter scanFilter = new ScanFilter.Builder().setServiceUuid(parcelUuid).build();
            mBleScanFilters.add(scanFilter);
        }
        mBluetoothDeviceSet = new HashSet<>();
        mScanning = false;
        mScanCount = SCAN_TIMES;

        mController = new BleClientController();

        mBleStateListener = new IStateListener() {
            @Override
            public void onStateChange(int newState) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    LogUtils.d(TAG, "BluetoothGatt.STATE_CONNECTED");
                }else if(newState == BluetoothGatt.STATE_CONNECTING){
                    stopScan();
                    LogUtils.d(TAG, "BluetoothGatt.STATE_CONNECTING");

                } else if(newState == BluetoothGatt.STATE_DISCONNECTED) {
                    mController.closePool(Request.CONNECT_LOST);
                    LogUtils.d(TAG, "BluetoothGatt.STATE_DISCONNECTED");
                }
            }
        };
        registerStateListener(mBleStateListener);

        LogUtils.v(TAG,"BLE客户端开始监听蓝牙状态变化");
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mBtStatusChangeReceiver,filter);
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback(){

        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if( !mBluetoothDeviceSet.contains(device) ){
                String name = TextUtils.isEmpty(device.getName()) ? "Unknown" : device.getName();
                String address = device.getAddress();
                LogUtils.d(TAG,String.format("扫描到蓝牙设备，名称：%s, 地址：%s ",name,address));
                mBluetoothDeviceSet.add(device);
                Map<String,Object> extras = new HashMap<>();
                extras.put("RSSI",rssi);
                extras.put("RECORD",scanRecord);
                if(mOnScanListener != null){
                    mOnScanListener.onDeviceFound(device,extras);
                }
            }
        }

        @Override
        public String toString() {
            return super.toString();
        }
    };

    private ScanCallback mNewScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if( !mBluetoothDeviceSet.contains(device) ){
                String name = TextUtils.isEmpty(device.getName()) ? "Unknown" : device.getName();
                String address = device.getAddress();
                LogUtils.d(TAG,String.format("扫描到蓝牙设备，名称：%s, 地址：%s ",name,address));
                mBluetoothDeviceSet.add(device);
                ScanRecord record = result.getScanRecord();
                Map<String,Object> extras = new HashMap<>();
                extras.put("RESULT",record);
                if(mOnScanListener != null){
                    mOnScanListener.onDeviceFound(device,extras);
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback(){
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState){
            // BluetoothProfile.STATE_CONNECTED 事件放到了onServicesDiscovered后再通知
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "已连接至Gatt Server,开始查找服务");
                if (!gatt.discoverServices()) {
                    LogUtils.d(TAG,"服务查找失败，执行连接断开操作");
                    gatt.disconnect();
                }
            }else{
                setConnectStates(newState);
            } /*else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server. status:"+status+"\tnewState:"+newState);
                if(status == 19){  //show toast when user click
                    Log.d(TAG, "Connection closed by remote device");
                }
            }*/

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            setConnectStates(BluetoothProfile.STATE_CONNECTED);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "查找服务完成，结果如下：");
                BluetoothUtils.printGattServices(gatt.getServices());
            } else {
                // TODO: 2017/12/22 查找服务失败可以再尝试一次试试
                Log.d(TAG, "查找服务失败，状态码：" + status);
                disconnect();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            UUID characterUuid = characteristic.getUuid();
            byte[] value = characteristic.getValue();
            mController.onReceived(characterUuid,value);
            LogUtils.v(TAG,"onCharacteristicChanged");
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {

            UUID characterUuid = characteristic.getUuid();
            byte[] value = characteristic.getValue();
            mController.onSend(characterUuid,status,value);
            LogUtils.v(TAG,"onCharacteristicWrite");
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            UUID characterUuid = characteristic.getUuid();
            byte[] value = characteristic.getValue();
            mController.onReceived(characterUuid,value);
            LogUtils.v(TAG,"onCharacteristicRead");
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Config.MTU = mtu;
            super.onMtuChanged(gatt, mtu, status);
            LogUtils.v(TAG,"onMtuChanged");
        }
    };

    // BLE模式下的扫描，要求开启位置权限
    @Override
    public void startScan(OnScanListener listener){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            int checkResult = ActivityCompat.checkSelfPermission(mContext,
                    Manifest.permission.ACCESS_COARSE_LOCATION);

            if(checkResult != PackageManager.PERMISSION_GRANTED){
                LogUtils.d(TAG,"Android6.0及以上的手机扫描需要授予位置权限");
                return;
            }
        }
        LogUtils.d(TAG,"开始扫描");
        mScanCount = 0;
        mOnScanListener = listener;
        mScanning = true;
        mBluetoothDeviceSet.clear();
        search();
    }

    @Override
    public boolean isScanning() {
        return mScanning;
    }

    /** 将长扫描切割成多次短扫描 */
    private void search(){
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopSearch();
                if( mScanCount < SCAN_TIMES){
                    search();
                }else{
                    if(mOnScanListener != null){
                        mOnScanListener.onScanTimeout();
                    }
                }
            }
        },SCAN_DURATION * 1000);

        mScanCount++;
        if(android.os.Build.VERSION.SDK_INT >= 21){
            if(null == mBleScanner){
                mBleScanner = mBluetoothAdapter.getBluetoothLeScanner();
            }
            mBleScanner.startScan(/*mBleScanFilters*/null,mScanSettings,mNewScanCallback);
        }else{
            mBluetoothAdapter.startLeScan(/*new UUID[]{BluetoothUtils.ADVERTISE_SERVICE},*/mLeScanCallback);
        }
    }

    private void stopSearch(){
        if(android.os.Build.VERSION.SDK_INT >= 21){
            mBleScanner.stopScan(mNewScanCallback);
        }else{
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    @Override
    public void stopScan(){
        LogUtils.d(TAG,"停止扫描");
        mHandler.removeCallbacksAndMessages(null);
        mScanCount = SCAN_TIMES;
        mOnScanListener = null;
        mScanning = false;
        mBluetoothDeviceSet.clear();
        stopSearch();
    }

    @Override
    public void connect(final BluetoothDevice device){
        LogUtils.d(TAG,"尝试通过BluetoothDevice对象连接设备,地址：" + device.getAddress());
        if(mBluetoothGatt != null){
            close();
        }
        stopScan();
        mRemoteDevice = device;
        if(mStateNow != BluetoothProfile.STATE_CONNECTING
                && mStateNow != BluetoothProfile.STATE_CONNECTED){

            // 务必在主线程中发起连接
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    LogUtils.d(TAG,"开始连接设备，当前线程：" + Thread.currentThread().getName());
                    mBluetoothGatt = device.connectGatt(mContext,false, mGattCallback);
                    mController.init(mBluetoothGatt);
                }
            });
        }
    }

    @Override
    public void connect(final String address) {
        LogUtils.d(TAG,"尝试通过地址连接设备,地址：" + address);
        if(!BluetoothAdapter.checkBluetoothAddress(address)){
            LogUtils.e(TAG,"尝试连接格式非法的MAC地址");
            return;
        }
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if(device.getType() != BluetoothDevice.DEVICE_TYPE_UNKNOWN){//本地已有相关设备的信息
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
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    connect(device);
                }
            });
        }
    }

    @Override
    public Controller getController(){
        return mController;
    }

    @Override
    public void disconnect(){
        LogUtils.d(TAG,"断开连接，设备地址：" + mRemoteDevice.getAddress());
        if(mBluetoothGatt != null){
            mBluetoothGatt.disconnect();
        }else{
            LogUtils.d(TAG,"mBluetoothGatt为null,放弃操作");
        }
    }

    @Override
    public void close(){
        LogUtils.d(TAG,"BLE客户端断开连接释放资源");
        if(mBluetoothGatt != null){
            mBluetoothGatt.disconnect();
        }

        if(mBluetoothGatt != null){
            clearGattCache(mBluetoothGatt);
        }

        if(mBluetoothGatt != null){
            mBluetoothGatt.close();
        }
        mBluetoothGatt = null;
    }

    public void requestMtu(int mtu){
        LogUtils.d(TAG,"尝试修改MTU，目标大小：" + mtu);
        mBluetoothGatt.requestMtu(mtu);
    }

    public List<BluetoothGattService> getServices() {
        return mBluetoothGatt.getServices();
    }

    public void onDestroy(){
        close();
        mContext.unregisterReceiver(mBtStatusChangeReceiver);
        unregisterStateListener(mBleStateListener);
    }

    private void clearGattCache(BluetoothGatt bluetoothGatt) {
        try {
            if (bluetoothGatt != null) {
                Method refresh = BluetoothGatt.class.getMethod("refresh");
                if (refresh != null) {
                    refresh.setAccessible(true);
                    refresh.invoke(bluetoothGatt, new Object[0]);
                }
            }
        } catch (Exception e) {
            Log.d(TAG,e.getMessage());
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
