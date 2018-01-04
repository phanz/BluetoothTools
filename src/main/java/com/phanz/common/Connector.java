package com.phanz.common;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by phanz on 2017/12/3.
 */

public abstract  class Connector{

    public static final int STATE_NONE = 5;
    public static final int STATE_DISCONNECTED = BluetoothProfile.STATE_DISCONNECTED;
    public static final int STATE_CONNECTING = BluetoothProfile.STATE_CONNECTING;
    public static final int STATE_CONNECTED = BluetoothProfile.STATE_CONNECTED;
    public static final int STATE_DISCONNECTING = BluetoothProfile.STATE_DISCONNECTING;
    public static final int STATE_LISTENING = 4;

    //连接相关
    protected int mOldState;
    protected int mStateNow;
    private List<IStateListener> mStateListener;

    protected Connector(){
        mOldState = BluetoothProfile.STATE_DISCONNECTED;
        mStateNow = BluetoothProfile.STATE_DISCONNECTED;
        mStateListener = new ArrayList<>();
    }

    public abstract void connect(BluetoothDevice device);
    public abstract void connect(String address);
    public abstract Controller getController();
    public abstract void disconnect();
    public abstract void close();

    public int[] getConnectStates(){
        return new int[]{mOldState,mStateNow};
    }

    public void setConnectStates(int newState){
        mOldState = mStateNow;
        mStateNow = newState;
        notifyBleDeviceStateChange(newState);
    }

    public void registerStateListener(IStateListener listener){
        if(!mStateListener.contains(listener)){
            mStateListener.add(listener);
        }
    }

    public void unregisterStateListener(IStateListener listener){
        mStateListener.remove(listener);
    }

    protected void notifyBleDeviceStateChange(int newState){
        for(int i=0; i < mStateListener.size();i++){
            mStateListener.get(i).onStateChange(newState);
        }
    }

    public interface IStateListener {
        void onStateChange(int newState);
    }

}
