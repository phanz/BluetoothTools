package com.phanz.common;

import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by hanzai.peng on 2017/12/13.
 */

public abstract class PoolController implements Controller{
    public static final String TAG = "[Bluetooth]PoolController";

    private List<Request> mRequestPool;
    private Request mRequestNow;

    private HandlerThread mWorkThread;
    private Handler mHandler;
    private Map<UUID,Response> mReceiverMap;

    protected PoolController(){
        mRequestPool = new LinkedList<>();
        mWorkThread = new HandlerThread("BluetoothWorkThread");
        mWorkThread.start();
        mHandler = new Handler(mWorkThread.getLooper());
        mReceiverMap = new HashMap<>();
    }

    /**
     * Request中具体的数据如何发送由子类自行实现
     * @param request
     */
    protected abstract void executeRequest(Request request);

    /**
     * 将data封装成Request，放入请求池等待调度，具体的封装交由子类处理
     * @param data 待发送数据
     */
    public abstract void send(UUID uuid,byte[] data);

    @Override
    public void post(Request request){
        mRequestPool.add(request);
        scheduleNext();
    }

    public void notifyDataReceived(UUID uuid,byte[] data){
        LogUtils.d(TAG,"接收到数据：" + new String(data));
        Response response = mReceiverMap.get(uuid);
        if(response != null){
            Request request = new Request.Builder()
                    .setUuid(uuid)
                    .apply();
            response.onResponse(request,Request.SUCCESS,data);

        } else {
            LogUtils.d(TAG,"该数据无人订阅，丢弃");
        }
    }

    @Override
    public void registerReceiveListener(UUID uuid, Response response){
        mReceiverMap.put(uuid,response);
    }

    @Override
    public void unregisterReceiveListener(UUID uuid){
        mReceiverMap.remove(uuid);
    }

    public Request getRequestNow(){
        return mRequestNow;
    }

    public void closePool(int errStatus){
        LogUtils.d(TAG,"传输发生错误，销毁所有任务");
        List<Request> failedList = new ArrayList<>();
        failedList.add(mRequestNow);
        failedList.addAll(mRequestPool);
        mRequestNow = null;
        mRequestPool.clear();
        for(int i = 0; i < failedList.size(); i++){
            if(failedList.get(i) != null){
                failedList.get(i).onResponse(errStatus,null);
            }
        }
    }

    protected synchronized void scheduleNext(){
        LogUtils.d(TAG,"尝试从任务池获取下一个任务");
        if(!mRequestPool.isEmpty() ){
            mRequestNow = mRequestPool.remove(0);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(mRequestNow != null){
                        mRequestNow.onResponse(Request.TIMEOUT,null);
                        scheduleNext();
                    }
                }
            },mRequestNow.getTimeout());
            executeRequest(mRequestNow);
        }
    }

    protected static byte[] makeHeadBytes(int total,int current,int byteCounts){
        ByteBuffer buffer = ByteBuffer.allocate(byteCounts);
        if(byteCounts == 2){
            buffer.put((byte)total);
            buffer.put((byte)current);
        }else if(byteCounts == 4){
            buffer.putShort((short)total);
            buffer.putShort((short)current);
        }else if(byteCounts == 8){
            buffer.putInt(total);
            buffer.putInt(current);
        }else{
            LogUtils.e(TAG,"用作协议保留的字节长度只能为2、4、8中的一个");
        }
        return buffer.array();
    }

    protected static int[] parseHeadBytes(@NonNull byte[] headBytes){
        int byteCounts = headBytes.length;
        int[] packetNumInfo = new int[2];
        ByteBuffer buffer = ByteBuffer.wrap(headBytes);

        if(byteCounts == 2){
            packetNumInfo[0] = buffer.get() & 0xff;
            packetNumInfo[1] = buffer.get() & 0xff;
        }else if(byteCounts == 4){
            packetNumInfo[0] = buffer.getShort() & 0xffff;
            packetNumInfo[1] = buffer.getShort() & 0xffff;
        }else if(byteCounts == 8){
            packetNumInfo[0] = buffer.getInt();
            packetNumInfo[1] = buffer.getInt();
        }else{
            LogUtils.e(TAG,"用作协议保留的字节长度只能为2、4、8中的一个");
        }
        return packetNumInfo;
    }
}
