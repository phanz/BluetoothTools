package com.phanz.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;

import com.phanz.common.BluetoothUtils;
import com.phanz.common.Config;
import com.phanz.common.PoolController;
import com.phanz.common.Request;
import com.phanz.common.LogUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Created by hanzai.peng on 2016/12/19.
 */

public class BleServerController extends PoolController {
    public static final String TAG =  "[Bluetooth]BleServerController";

    private Set<byte[]> mReceivedDataSet;

    private BluetoothGattServer mGattServer;
    private BluetoothDevice mRemoteDevice;

    public BleServerController(){
        mReceivedDataSet = new LinkedHashSet<>();
    }

    public void init(BluetoothDevice device, BluetoothGattServer gattServer){
        mRemoteDevice = device;
        mGattServer = gattServer;
    }

    // TODO: 2017/12/29 设置标记位，如果正在接受数据则不发送数据
    public void onReceived(UUID character, byte[] value){
        if(BluetoothUtils.isLongPacket(character)){ // 长数据传输
            byte[] headBytes = Arrays.copyOfRange(value,0, Config.RESERVE_BYTES);
            int[] packetNumInfo = parseHeadBytes(headBytes);
            mReceivedDataSet.add(Arrays.copyOfRange(value,Config.RESERVE_BYTES,value.length));
            if((packetNumInfo[0] != 0) && (packetNumInfo[0] == packetNumInfo[1])){
                byte[] data = BluetoothUtils.mergeBytes(mReceivedDataSet);
                mReceivedDataSet.clear();
                notifyDataReceived(character,data);
            }

        }else{ // 短数据传输
            notifyDataReceived(character,value);
        }
    }

    public void onSend(int status){
        Request requestNow = getRequestNow();
        if(requestNow.getType() == Request.Type.LONG_SEND
                && status == BluetoothGatt.GATT_SUCCESS
                && requestNow.hasNextPacket()){ //长数据的拆包发送,未发送完，发送下一包

            byte[] sendValue = requestNow.getNextPacket();
            BluetoothGattCharacteristic gattChar = BluetoothUtils.getGattChar(mGattServer.getServices(),requestNow.getUuid());
            if(gattChar != null && BluetoothUtils.isCharacterNotifiable(gattChar)){
                gattChar.setValue(sendValue);
                mGattServer.notifyCharacteristicChanged(mRemoteDevice,gattChar,true);
            }else{
                LogUtils.d(TAG,"特征为null，或者该特征不可notify");
                requestNow.onResponse(Request.FAILED, null);
                scheduleNext();
            }

        } else {// 短数据传输或者 长数据传输结束(可能成功可能失败)
            int result = (status == BluetoothGatt.GATT_SUCCESS) ? Request.SUCCESS : Request.FAILED;
            requestNow.onResponse(result,null);
            scheduleNext();
        }
    }

    /** 发送长数据 */
    @Override
    protected void executeRequest(Request request){
        BluetoothGattCharacteristic notifyChar = BluetoothUtils.getGattChar(mGattServer.getServices(),request.getUuid());
        if(notifyChar == null){
            request.onResponse(Request.FAILED, null);
            scheduleNext();
            return;
        }

        byte[] value = request.getData();
        if(request.getType() == Request.Type.LONG_SEND){//拆包发送
            value = request.getNextPacket();
        }else if(request.getType() == Request.Type.SEND){//直接发送
            value = request.getData();
        }
        if(notifyChar != null && BluetoothUtils.isCharacterNotifiable(notifyChar)){
            notifyChar.setValue(value);
            mGattServer.notifyCharacteristicChanged(mRemoteDevice,notifyChar,true);
        }else{
            LogUtils.d(TAG,"特征为null，或者该特征不可notify");
            request.onResponse(Request.FAILED, null);
            scheduleNext();
        }
    }

    @Override
    public synchronized void send(UUID uuid,byte[] data) {
        Request.Type type = Request.Type.SEND;
        if(BluetoothUtils.isLongPacket(uuid)){
            type = Request.Type.LONG_SEND;
        }
        Request request = new Request.Builder()
                .setType(type)
                .setPriority(Request.PRIORITY_NORMAL)
                .setUuid(uuid)
                .setData(data)
                .apply();

        post(request);
    }

}
