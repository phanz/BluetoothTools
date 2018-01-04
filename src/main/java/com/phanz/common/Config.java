package com.phanz.common;

import java.util.UUID;

/**
 * Created by hanzai.peng on 2017/12/21.
 */

public class Config {

    public static final UUID ADVERTISE_SERVICE = UUID.fromString("00001800-0000-1000-8000-00805F9B34FB");
    public static final UUID LONG_SERVICE = UUID.fromString("0000bbbb-0000-1000-8000-00805f9b34fb");
    public static final UUID LONG_READ_CHAR = UUID.fromString("0000bbbc-0000-1000-8000-00805f9b34fb");
    public static final UUID LONG_WRITE_CHAR = UUID.fromString("0000bbbd-0000-1000-8000-00805f9b34fb");
    public static final UUID LONG_WRITE_CHAR2 = UUID.fromString("0000bbbf-0000-1000-8000-00805f9b34fb");
    public static final UUID LONG_NOTIFY_CHAR = UUID.fromString("0000bbbe-0000-1000-8000-00805f9b34fb");
    public static final UUID LONG_NOTIFY_CHAR2 = UUID.fromString("0000bbca-0000-1000-8000-00805f9b34fb");

    public static final UUID CLASSIC_RFC = UUID.fromString("0000ffff-0000-1000-8000-00805f9b34fb");

    public static int MTU = 20; //BLE连接时的最大传输单元

    //协议保留字节数，用做拆包时的总包数和包序号，如为2，表示用1个字节表示总包数，用1个字节表示包序号
    public static final int RESERVE_BYTES = 2;//值只能为2、4、8中的一个
}
