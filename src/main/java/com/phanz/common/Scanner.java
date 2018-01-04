package com.phanz.common;

import android.bluetooth.BluetoothDevice;

import java.util.Map;

/**
 * Created by hanzai.peng on 2017/12/12.
 */

public interface Scanner {
    void startScan(OnScanListener listener);
    boolean isScanning();
    void stopScan();

    interface OnScanListener {

        // TODO: 2017/12/3 定义一个统一的类来同时兼容21以上及以下的扫描结果返回
        void onDeviceFound(BluetoothDevice device, Map<String,Object> extras);

        void onScanTimeout();
    }
}
