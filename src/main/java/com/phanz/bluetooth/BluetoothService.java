package com.phanz.bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.phanz.ble.BleAdvertiser;
import com.phanz.ble.BleConnector;
import com.phanz.common.Advertiser;
import com.phanz.common.BluetoothUtils;
import com.phanz.common.Connector;
import com.phanz.common.ConnectorFactory;
import com.phanz.common.LogUtils;
import com.phanz.common.Request;
import com.phanz.common.Response;
import com.phanz.common.Scanner;

import java.util.List;
import java.util.UUID;

/**
 * Created by phanz on 2017/12/3.
 */

public class BluetoothService extends Service {
    public static final String TAG = "BluetoothService";


    private Connector mConnector;
    public BluetoothBinder mBinder = new BluetoothBinder();
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public void init(boolean isBle, boolean isClient){
        mConnector = ConnectorFactory.getConnector(this,isBle,isClient);
    }

    public void startAdvertisement(){
        if(mConnector != null && mConnector instanceof Advertiser){
            ((Advertiser) mConnector).startAdvertisement();
        }else{
            LogUtils.e(TAG,"当前Connector不是未继承Advertiser");
        }
    }

    public void startScan(Scanner.OnScanListener onScanListener){
        if(mConnector != null && mConnector instanceof Scanner){
            Scanner scanner = ((Scanner)mConnector);
            if(!scanner.isScanning()){
                scanner.startScan(onScanListener);
            }else{
                LogUtils.d(TAG,"当前已经处于扫描状态");
            }
        }else{
            LogUtils.e(TAG,"当前Connector不是未继承Scanner");
        }
    }

    public void connect(BluetoothDevice device){
        mConnector.connect(device);
    }

    public void connect(String address){
        mConnector.connect(address);
    }

    public int[] getConnectStates(){
        if(mConnector == null){
            return null;
        }
        return mConnector.getConnectStates();
    }

    public void disconnect(){
        mConnector.disconnect();
    }

    public void close(){
        mConnector.close();
    }

    public Connector getConnector(){
        return mConnector;
    }

    public List<BluetoothGattCharacteristic> getCharacterList(){
        if(mConnector instanceof BleConnector){
            BleConnector connector = (BleConnector) BluetoothHelper.getInstance().getConnector();
            return BluetoothUtils.getGattList(connector.getServices());
        }else if(mConnector instanceof BleAdvertiser){
            BleAdvertiser connector = (BleAdvertiser) BluetoothHelper.getInstance().getConnector();
            return BluetoothUtils.getGattList(connector.getServices());
        }
        return null;
    }

    public void send(UUID uuid,byte[] data){
        mConnector.getController().send(uuid,data);
    }

    public void post(Request request){
        mConnector.getController().post(request);
    }

    public void registerStateListener(Connector.IStateListener listener){
        mConnector.registerStateListener(listener);
    }

    public void unregisterStateListener(Connector.IStateListener listener){
        mConnector.unregisterStateListener(listener);
    }

    public void registerReceiverListener(UUID uuid, Response response){
        mConnector.getController().registerReceiveListener(uuid, response);
    }

    public void unRegisterReceiverListener(UUID uuid){
        mConnector.getController().unregisterReceiveListener(uuid);
    }

    public class BluetoothBinder extends Binder{
        BluetoothService getService(){
            return BluetoothService.this;
        }
    }
}
