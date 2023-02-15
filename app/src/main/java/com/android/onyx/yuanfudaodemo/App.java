package com.android.onyx.yuanfudaodemo;

import android.app.Application;

import com.onyx.android.sdk.OnyxSdk;

/**
 * Created by Edward.
 * Date: 2022/8/23
 * Time: 14:49
 * Desc:
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        OnyxSdk.init(this);
    }
}
