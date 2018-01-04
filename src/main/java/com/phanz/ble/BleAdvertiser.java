package com.phanz.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.ParcelUuid;
import android.widget.Toast;

import com.phanz.common.Advertiser;
import com.phanz.common.BluetoothUtils;
import com.phanz.common.Config;
import com.phanz.common.Connector;
import com.phanz.common.Controller;
import com.phanz.common.LogUtils;
import com.phanz.common.Request;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by hanzai.peng on 2017/1/6.
 */

public class BleAdvertiser extends Connector implements Advertiser{
    public static final String TAG = "[Bluetooth]BleAdvertiser";

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private Context mContext;
    private Handler mHandler;

    //广播相关
    private boolean mAdvertising;
    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            LogUtils.d(TAG,"BLE广播开启成功");
            Toast.makeText(mContext,"BLE广播开启成功！",Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            LogUtils.e(TAG,"BLE广播开启失败，错误码：" + errorCode);
            Toast.makeText(mContext,"BLE广播开启失败，错误码：" + errorCode,Toast.LENGTH_SHORT).show();
        }
    };

    private IStateListener mBleStateListener;
    private BluetoothGattServer mBluetoothGattServer;

    //发送相关
    private BleServerController mController;

    private BluetoothGattServerCallback mBluetoothGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device,int status, int newState) {
            if(device != null){
                mController.init(device,mBluetoothGattServer);
                setConnectStates(newState);
            }else{
                LogUtils.e(TAG,"onConnectionStateChange: device为空");
            }

        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                 int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset, byte[] value) {

            if(responseNeeded){
                mBluetoothGattServer.sendResponse(device,requestId, BluetoothGatt.GATT_SUCCESS,offset,value);
            }
            UUID characterUuid = characteristic.getUuid();
            mController.onReceived(characterUuid,value);
            LogUtils.v(TAG,"onCharacteristicWriteRequest");
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            mController.onSend(status);
            LogUtils.v(TAG,"onNotificationSent");
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            // TODO: 2017/12/21 处理Master端发来的修改MTU请求
            Config.MTU = mtu;
            super.onMtuChanged(device, mtu);
            LogUtils.d(TAG,"onMtuChanged");
        }
    };

    private static BleAdvertiser sInstance = null;

    public static synchronized BleAdvertiser getInstance(Context context){
        if(sInstance == null){
            synchronized (BleAdvertiser.class){
                Context appContext = context.getApplicationContext();
                sInstance = new BleAdvertiser(appContext);
            }
        }
        return sInstance;
    }

    private BleAdvertiser(Context context){
        mContext = context;
        mBluetoothManager = (BluetoothManager)
                mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mHandler = new Handler();
        if( !mBluetoothAdapter.isEnabled() ){
            mBluetoothAdapter.enable();
        }
        mController = new BleServerController();

        mAdvertising = false;

        mBleStateListener = new IStateListener() {
            @Override
            public void onStateChange(int newState) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    LogUtils.d(TAG, "BluetoothGatt.STATE_CONNECTED");
                }else if(newState == BluetoothGatt.STATE_CONNECTING){
                    LogUtils.d(TAG, "BluetoothGatt.STATE_CONNECTING");

                } else if(newState == BluetoothGatt.STATE_DISCONNECTED) {
                    mController.closePool(Request.CONNECT_LOST);
                    LogUtils.d(TAG, "BluetoothGatt.STATE_DISCONNECTED");
                }
            }
        };
        registerStateListener(mBleStateListener);

        LogUtils.v(TAG,"BLE服务端开始监听蓝牙状态变化");
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mBluetoothReceiver,intentFilter);
    }

    private AdvertiseSettings buildAdvertiseSettings() {
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();
        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
        settingsBuilder.setConnectable(true);
        settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        return settingsBuilder.build();
    }

    private AdvertiseData buildAdvertiseData() {
        ParcelUuid uuid = new ParcelUuid(Config.ADVERTISE_SERVICE);
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        dataBuilder.setIncludeDeviceName(true);
        byte[] macByte = BluetoothUtils.getMacByte();
        dataBuilder.addServiceData(uuid,macByte);
        return dataBuilder.build();
    }

    @Override
    public void startAdvertisement(){
        LogUtils.d(TAG,"开始打开BLE广播");
        if( !BluetoothUtils.isBleSupported(mContext) ){
            LogUtils.e(TAG,"本手机硬件不支持BLE特性");
            return;
        }

        if( !mBluetoothAdapter.isMultipleAdvertisementSupported() ){
            LogUtils.e(TAG,"当前系统版本不支持开启BLE广播");
            return;
        }

        if( !BluetoothUtils.isBluetoothEnable() ){
            LogUtils.e(TAG,"当前设备蓝牙未开启");
            return;
        }
        mAdvertising = true;
        mBluetoothGattServer = mBluetoothManager.openGattServer(mContext,mBluetoothGattServerCallback);
        addAllGattService();
        AdvertiseSettings advertiseSettings = buildAdvertiseSettings();
        AdvertiseData advertiseData = buildAdvertiseData();
        BluetoothLeAdvertiser advertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        advertiser.startAdvertising(advertiseSettings, advertiseData, mAdvertiseCallback);
    }

    @Override
    public boolean isAdvertising(){
        return mAdvertising;
    }

    @Override
    public void stopAdvertisement(){
        LogUtils.d(TAG,"尝试停止BLE广播,mAdvertising:" + mAdvertising);
        if(mAdvertising){
            mAdvertising = false;
            BluetoothLeAdvertiser advertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
            if(advertiser != null){
                advertiser.stopAdvertising(mAdvertiseCallback);
            }
        }
    }

    @Override
    public void connect(String address) {
        LogUtils.d(TAG,"尝试通过地址连接设备,地址：" + address);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        connect(device);
    }

    @Override
    public void connect(BluetoothDevice device){
        LogUtils.d(TAG,"尝试通过BluetoothDevice对象连接设备,地址：" + device.getAddress());
        if(mBluetoothGattServer != null){
            mBluetoothGattServer.connect(device,false);
        }
    }

    @Override
    public Controller getController(){
        return mController;
    }

    @Override
    public void disconnect(){
        if(mBluetoothGattServer == null || mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER).size() == 0){
            LogUtils.d(TAG,"尝试断开连接，当前无已连接设备");
        }else{
            BluetoothDevice device = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER).get(0);
            LogUtils.d(TAG,"断开连接，设备地址：" + device.getAddress());
            if(device != null){
                mBluetoothGattServer.cancelConnection(device);
            }
        }
    }

    @Override
    public void close() {
        LogUtils.d(TAG,"BLE广播端close，释放连接相关资源");
        stopAdvertisement();
        disconnect();
        if(mBluetoothGattServer != null){
            mBluetoothGattServer.close();
            mBluetoothGattServer = null;
        }
    }

    public List<BluetoothGattService> getServices() {
        return mBluetoothGattServer.getServices();
    }

    public void addAllGattService(){
        List<UUID> readUuidList = new ArrayList<>();
        List<UUID> writeUuidList = new ArrayList<>();
        List<UUID> notifyUuidList = new ArrayList<>();
        readUuidList.add(Config.LONG_READ_CHAR);
        writeUuidList.add(Config.LONG_WRITE_CHAR);
        writeUuidList.add(Config.LONG_WRITE_CHAR2);
        notifyUuidList.add(Config.LONG_NOTIFY_CHAR);
        notifyUuidList.add(Config.LONG_NOTIFY_CHAR2);
        BluetoothUtils.addService(mBluetoothGattServer,Config.LONG_SERVICE,
                readUuidList,writeUuidList,notifyUuidList);
        LogUtils.d(TAG,"添加BLE服务，内容如下：");
        BluetoothUtils.printGattServices(mBluetoothGattServer.getServices());
    }

    public void onDestroy(){
        mContext.unregisterReceiver(mBluetoothReceiver);
    }

    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR);
                if(state == BluetoothAdapter.STATE_ON /*&& mBleAdvertiser.isAdvertising()*/){
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            LogUtils.d(TAG,"监测到蓝牙重启，重新打开BLE广播");
                            // TODO: 2017/12/5 蓝牙重启后mBluetoothGattServer等对象可能已死亡，需要重新启动
                            startAdvertisement();
                        }
                    },500);
                }else if(state == BluetoothAdapter.STATE_OFF){
                    stopAdvertisement();
                    setConnectStates(Connector.STATE_NONE);
                }
            }
        }
    };
}
