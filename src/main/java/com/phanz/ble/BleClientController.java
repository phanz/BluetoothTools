package com.phanz.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import com.phanz.common.BluetoothUtils;
import com.phanz.common.Config;
import com.phanz.common.PoolController;
import com.phanz.common.Request;
import com.phanz.common.Response;
import com.phanz.common.LogUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Created by hanzai.peng on 2016/11/22.
 */
public class BleClientController extends PoolController {
    public static final String TAG = "[Bluetooth]BleClientController";

    public BluetoothGatt mGatt;
    private Set<byte[]> mReceivedDataSet;


    public BleClientController(){
        mReceivedDataSet = new LinkedHashSet<>();
    }

    public void init(BluetoothGatt gatt){
        mGatt = gatt;
    }

    public void onSend(UUID character, int status, byte[] value) {
        Request requestNow = getRequestNow();
        if(requestNow.getType() == Request.Type.LONG_SEND
                && status == BluetoothGatt.GATT_SUCCESS
                && requestNow.hasNextPacket()){ //长数据的拆包发送,未发送完，发送下一包

            byte[] sendValue = requestNow.getNextPacket();
            BluetoothGattCharacteristic gattChar = BluetoothUtils.getGattChar(mGatt.getServices(), character);

            if(gattChar != null && BluetoothUtils.isCharacterWritable(gattChar)){
                gattChar.setValue(sendValue);
                mGatt.writeCharacteristic(gattChar);

            }else{
                LogUtils.d(TAG,"特征为null，或者该特征不可写");
                requestNow.onResponse(Request.FAILED, null);
                scheduleNext();
            }

        }else{ // 短数据传输或者 长数据传输结束(可能成功可能失败),通知结果
            int result = (status == BluetoothGatt.GATT_SUCCESS) ? Request.SUCCESS : Request.FAILED;
            requestNow.onResponse(result,null);
            scheduleNext();
        }
    }

    // TODO: 2017/12/29 设置标记位，如果正在接受数据则不发送数据
    public void onReceived(UUID character, byte[] value) {
        if(BluetoothUtils.isLongPacket(character)){ // 长数据传输
            byte[] headBytes = Arrays.copyOfRange(value,0, Config.RESERVE_BYTES);
            int[] packetNumInfo = parseHeadBytes(headBytes);
            mReceivedDataSet.add(Arrays.copyOfRange(value,Config.RESERVE_BYTES,value.length));
            if((packetNumInfo[0] != 0) && (packetNumInfo[0] == packetNumInfo[1])){  //最后一个包
                byte[] data = BluetoothUtils.mergeBytes(mReceivedDataSet);
                mReceivedDataSet.clear();
                notifyDataReceived(character,data);
            }

        }else{ // 短数据传输
            notifyDataReceived(character,value);
            Request requestNow = getRequestNow();
            //当前发和当前收的UUID相同，说明是read属性的Character
            if(requestNow != null && requestNow.getUuid().equals(character)){
                requestNow.onResponse(Request.SUCCESS,null);
                scheduleNext();
            }
        }
    }

    /** 发送长数据 */
    @Override
    protected void executeRequest(Request request){
        Request.Type type = request.getType();
        if(Request.Type.READ == type){
            LogUtils.d(TAG,String.format("尝试从%s读取数据",request.getUuid()));
            BluetoothGattCharacteristic readCharacter =
                    BluetoothUtils.getGattChar(mGatt.getServices(),request.getUuid());


            if(readCharacter != null && BluetoothUtils.isCharacterReadable(readCharacter)){
                mGatt.readCharacteristic(readCharacter);

            }else{
                LogUtils.d(TAG,"特征为null,或者该特征不可读");
                request.onResponse(Request.FAILED,null);
                scheduleNext();
            }


        }else if(Request.Type.SEND == type || Request.Type.LONG_SEND == type){
            LogUtils.d(TAG,String.format("尝试往%s发送数据",request.getUuid()));
            BluetoothGattCharacteristic writeChar =
                    BluetoothUtils.getGattChar(mGatt.getServices(),request.getUuid());

            if(writeChar != null && BluetoothUtils.isCharacterWritable(writeChar)){
                byte[] value = request.getData();
                if(request.getType() == Request.Type.LONG_SEND){//拆包发送
                    value = request.getNextPacket();
                }else if(request.getType() == Request.Type.SEND){//直接发送
                    value = request.getData();
                }
                writeChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                //writeChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                writeChar.setValue(value);
                mGatt.writeCharacteristic(writeChar);

            }else{
                LogUtils.d(TAG,"特征为null，或者该特征不可写");
                request.onResponse(Request.FAILED, null);
                scheduleNext();
            }

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

    @Override
    public void registerReceiveListener(UUID uuid, Response response) {
        enableNotify(uuid,true);
        super.registerReceiveListener(uuid, response);
    }

    @Override
    public void unregisterReceiveListener(UUID uuid) {
        enableNotify(uuid,false);
        super.unregisterReceiveListener(uuid);
    }

    public void enableNotify(UUID character,boolean enable) {
        if(mGatt == null){
            LogUtils.e(TAG,"mGatt为null");
            return;
        }
        boolean found = false;
        List<BluetoothGattService> gattServiceList = mGatt.getServices();
        if(gattServiceList != null){
            for(BluetoothGattService gattService : gattServiceList){
                for(BluetoothGattCharacteristic gattCharacter : gattService.getCharacteristics()){
                    if(gattCharacter != null && character.equals(gattCharacter.getUuid())){
                        if( (gattCharacter.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0 ){
                            mGatt.setCharacteristicNotification(gattCharacter,enable);
                        }
                        found = true;
                        break;
                    }
                }
            }
        }

        if( !found ){
            LogUtils.d(TAG,"未找到需要enableNotify的特征");
        }
    }

}
