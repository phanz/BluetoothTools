package com.phanz.common;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by hanzai.peng on 2017/1/3.
 */

public class Request {
    public static final String TAG = "Request";

    public enum Type{
        READ,SEND,LONG_SEND
    }

    public static final int PRIORITY_HIGH = 1;
    public static final int PRIORITY_NORMAL = 2;
    public static final int PRIORITY_LOW = 3;

    public static final int SUCCESS = 0;
    public static final int FAILED = 1;
    public static final int TIMEOUT = 2;
    public static final int CONNECT_LOST = 3;

    private Type type;
    private long timeout;
    private int priority;

    private UUID uuid;
    private byte[] data;
    private List<byte[]> packetList;

    private Response mResponse;

    private int current = 0;

    private Request(Type type, UUID uuid, byte[] data, Response response){
        this.type = type;
        this.uuid = uuid;
        this.data = data;
        this.mResponse = response;
        initPacketList();
    }

    public void onResponse(int rspCode,byte[] data){
        if(mResponse != null){
            mResponse.onResponse(this,rspCode,data);
            mResponse = null;
        }
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID characterUuid) {
        this.uuid = characterUuid;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
        initPacketList();
    }

    public boolean hasNextPacket(){
        return (packetList != null) && (current < packetList.size());
    }

    /**
     * 从Request中获取索引为 index 的拆包
     * @return
     */
    public byte[] getNextPacket(){
        if(packetList == null || current >= packetList.size()){
            LogUtils.e(TAG,"无待发送分包数据");
            return null;
        }
        return packetList.get(current ++);
    }

    private void initPacketList(){
        if(type == Type.LONG_SEND && data != null){
            //设置数据的时候就将包切割好
            int payloadLen = Config.MTU - Config.RESERVE_BYTES;
            int packetCount = (int)Math.ceil((double)data.length / payloadLen);
            packetList = new ArrayList<>(packetCount);
            for(int i = 0; i < packetCount; i++){
                int byteCountNow = i * payloadLen;
                int len = Math.min(payloadLen, data.length - byteCountNow);
                byte[] sendValue = new byte[ len + Config.RESERVE_BYTES ];
                byte[] headBytes = PoolController.makeHeadBytes(packetCount,i + 1,Config.RESERVE_BYTES);
                System.arraycopy(headBytes,0,sendValue,0,Config.RESERVE_BYTES);
                System.arraycopy(data,byteCountNow,sendValue,Config.RESERVE_BYTES,len);
                packetList.add(sendValue);
            }
        }
    }

    @Override
    public String toString() {
        return uuid +"\t"+new String(data);
    }

    public static class Builder{
        private Type type = Type.SEND;
        private long timeout = 15 * 1000;//超时15秒
        private int priority = PRIORITY_NORMAL;

        private UUID uuid;
        private byte[] data;

        private Response response;

        public Builder setType(Type type) {
            this.type = type;
            return this;
        }

        public Builder setTimeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder setPriority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder setUuid(UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder setData(byte[] data) {
            this.data = data;
            return this;
        }

        public Builder setResponse(Response response) {
            this.response = response;
            return this;
        }

        public Request apply(){
            Request request = new Request(type, uuid,data, response);
            request.setTimeout(timeout);
            request.setPriority(priority);
            return request;
        }
    }
}
