package com.phanz.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.phanz.common.Connector;
import com.phanz.common.Request;
import com.phanz.common.Response;
import com.phanz.common.Scanner;
import com.phanz.common.LogUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Created by phanz on 2017/12/3.
 */

public class BluetoothHelper {
    public static final String TAG = "[Bluetooth]BluetoothHelper";

    private Context mContext;
    private BluetoothService mBluetoothService;
    public static BluetoothHelper sInstance = null;

    private CountDownLatch mCountDownLatch;

    public static synchronized BluetoothHelper getInstance(){
        if(sInstance == null){
            synchronized (BluetoothHelper.class){
                sInstance = new BluetoothHelper();
            }
        }
        return sInstance;
    }

    private BluetoothHelper(){
        mCountDownLatch = new CountDownLatch(1);
        /*try {
            mCountDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/
    }

    public void init(Context context){
        LogUtils.initXLog();
        mContext = context.getApplicationContext();
        Intent serviceIntent = new Intent(mContext,BluetoothService.class);
        mContext.bindService(serviceIntent,mConn,Context.BIND_AUTO_CREATE);
    }

    public void makeConnector(boolean isBle, boolean isClient){
        mBluetoothService.init(isBle,isClient);
    }

    public void startAdvertisement(){
        mBluetoothService.startAdvertisement();
    }

    public void startScan(Scanner.OnScanListener onScanListener){
        mBluetoothService.startScan(onScanListener);
    }

    public void connect(BluetoothDevice device){
        mBluetoothService.connect(device);
    }

    public void connect(String address){
        mBluetoothService.connect(address);
    }

    public int[] getConnectStates(){
        return mBluetoothService.getConnectStates();
    }

    public void disconnect(){
        mBluetoothService.disconnect();
    }

    public void close(){
        mBluetoothService.close();
    }

    public Connector getConnector(){
        return mBluetoothService.getConnector();
    }

    public List<BluetoothGattCharacteristic> getCharacterList(){
        return mBluetoothService.getCharacterList();
    }

    public void send(UUID uuid, byte[] data){
        mBluetoothService.send(uuid,data);
    }

    public void post(Request request){
        mBluetoothService.post(request);
    }

    public void registerStateListener(Connector.IStateListener listener){
        mBluetoothService.registerStateListener(listener);
    }

    public void unregisterStateListener(Connector.IStateListener listener){
        mBluetoothService.unregisterStateListener(listener);
    }

    public void registerReceiverListener(UUID uuid,Response response){
        mBluetoothService.registerReceiverListener(uuid,response);
    }

    public void unRegisterReceiverListener(UUID uuid){
        mBluetoothService.unRegisterReceiverListener(uuid);
    }

    private ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            LogUtils.d(TAG,"BluetoothService已启动");
            BluetoothService.BluetoothBinder mBinder = (BluetoothService.BluetoothBinder)iBinder;
            mBluetoothService = mBinder.getService();
            //mCountDownLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

}
