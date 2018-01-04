package com.phanz.classic;

import com.phanz.common.BluetoothUtils;
import com.phanz.common.Config;
import com.phanz.common.PoolController;
import com.phanz.common.Request;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Created by hanzai.peng on 2017/12/13.
 */

public class ClassicController  extends PoolController{
    public static final String TAG = "[Bluetooth]ClassicController";

    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private Set<byte[]> mReceivedDataSet;

    public ClassicController(){
        mReceivedDataSet = new LinkedHashSet<>();
    }

    public void init(InputStream inputStream, OutputStream outputStream){
        mInputStream = inputStream;
        mOutputStream = outputStream;
    }

    /*public void onSend(UUID character, int status, byte[] value) {
        Request requestNow = getRequestNow();

        if(requestNow.getType() == Request.Type.LONG_SEND
                && requestNow.hasNextPacket()){ //长数据的拆包发送,未发送完，发送下一包
            byte[] sendValue = requestNow.getNextPacket();
            try {
                mOutputStream.write(sendValue);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else{// 长数据传输结束,通知结果
            requestNow.onResponse(Request.SUCCESS,null);
            scheduleNext();
        }
    }*/

    // TODO: 2017/12/29 设置标记位，如果正在接受数据则不发送数据
    public void onReceived(UUID character, byte[] value) {
        if(BluetoothUtils.isLongPacket(character)){ // 长数据传输
            byte[] headBytes = Arrays.copyOfRange(value,0,Config.RESERVE_BYTES);
            int[] packetNumInfo = parseHeadBytes(headBytes);
            mReceivedDataSet.add(Arrays.copyOfRange(value,Config.RESERVE_BYTES,value.length));
            if((packetNumInfo[0] != 0) && (packetNumInfo[0] == packetNumInfo[1])){ //最后一个包
                byte[] data = BluetoothUtils.mergeBytes(mReceivedDataSet);
                mReceivedDataSet.clear();
                notifyDataReceived(character,data);
            }
        }
    }

    @Override
    protected void executeRequest(Request request) {
        try {
            while(request.hasNextPacket()){
                byte[] data = request.getNextPacket();
                mOutputStream.write(data);
            }
            request.onResponse(Request.SUCCESS,null);
            scheduleNext();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void send(UUID uuid, byte[] data) {
        Request request = new Request.Builder()
                .setType(Request.Type.LONG_SEND)
                .setPriority(Request.PRIORITY_NORMAL)
                .setUuid(Config.CLASSIC_RFC)
                .setData(data)
                .apply();

        post(request);
    }
}
