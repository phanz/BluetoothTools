package com.phanz.common;

import android.content.Context;

import com.phanz.ble.BleAdvertiser;
import com.phanz.ble.BleConnector;
import com.phanz.classic.ClassicAdvertiser;
import com.phanz.classic.ClassicConnector;

/**
 * Created by hanzai.peng on 2017/12/15.
 */

public class ConnectorFactory {
    public static final String TAG = "[Bluetooth]ConnectorFactory";

    public static Connector getConnector(Context context, boolean isBle, boolean isClient){
        Connector mConnector = null;
        if(isBle && isClient) {  // BLE客户端
            LogUtils.d(TAG,"获取BLE客户端");
            mConnector = BleConnector.getInstance(context);
        } else if(isBle && !isClient) { // BLE服务端
            LogUtils.d(TAG,"获取BLE服务端");
            mConnector = BleAdvertiser.getInstance(context);
        }else if(!isBle && isClient) { // Classic 客户端
            LogUtils.d(TAG,"获取Classic客户端");
            mConnector = ClassicConnector.getInstance(context);
        }else if(!isBle && !isClient){ //Classic 服务端
            LogUtils.d(TAG,"获取Classic服务端");
            mConnector = ClassicAdvertiser.getInstance(context);
        }
        return mConnector;
    }
}
