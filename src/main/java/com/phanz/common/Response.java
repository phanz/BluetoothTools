package com.phanz.common;

/**
 * Created by phanz on 2017/12/3.
 */

public interface Response{
    void onResponse(Request request,int rspCode,byte[] data);
}