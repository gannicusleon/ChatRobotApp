package utils;

import android.content.Context;

import com.example.mychattext.MyApplication;

/**
 * UI工具类
 */

public class UIUtils {

    public static Context getContext() {
        return MyApplication.getApplication();
    }
}
