package com.github.tvbox.osc.util;

import android.util.Log;

public class LOG {
    private static final String TAG = "TVBox";

    public static void e(Throwable t) {
        if (t != null) {
            Log.e(TAG, t.getMessage(), t);
        }
    }

    public static void e(String msg) {
        Log.e(TAG, String.valueOf(msg));
    }

    public static void e(String tag, Throwable t) {
        if (t != null) {
            Log.e(tag, t.getMessage(), t);
        }
    }

    public static void e(String tag, String msg) {
        Log.e(tag, String.valueOf(msg));
    }

    public static void i(String msg) {
        Log.i(TAG, String.valueOf(msg));
    }

    public static void i(String tag, String msg) {
        Log.i(tag, String.valueOf(msg));
    }
}
