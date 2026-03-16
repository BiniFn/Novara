package com.github.tvbox.osc.server;

import android.content.Context;

public final class ControlManager {
    private static volatile Context context;

    private ControlManager() {
    }

    public static void init(Context value) {
        context = value == null ? null : value.getApplicationContext();
    }

    public static Context getContext() {
        return context;
    }
}
