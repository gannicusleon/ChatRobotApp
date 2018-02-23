package com.example.mychattext;

import android.app.Application;
import android.content.Context;

/**
 * Created by lituancheng on 2017/10/25.
 */

public class MyApplication extends Application {
    private static MyApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }
    // 获取Application
    public static Context getApplication() {
        return instance;
    }
}
