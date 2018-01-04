package com.phanz.common;

import java.util.UUID;

/**
 * Created by phanz on 2017/12/3.
 */

public interface Controller {

    /**
     * 向特定 UUID 数据。
     * 对于BLE，短数据要求data的长度不能超过 MTU 的长度，否则会发生截断，长数据不超过256 * (MTU -2)B),MTU默认为20
     * 对于Classic Bluetooth 对数据长度无限制
     *
     * uuid 为BluetoothUtils.LONG_WRITE_CHAR 或 BluetoothUtils.LONG_NOTIFY_CHAR 是BLE长数据请求
     * uuid 为BluetoothUtils.CLASSIC_RFC 时为 Classic Bluetooth 请求
     * @param data 待发送数据
     */
    void send(UUID uuid, byte[] data);

    void post(Request request);

    void registerReceiveListener(UUID uuid,Response response);

    void unregisterReceiveListener(UUID uuid);

}
