package com.github.tvbox.osc.util;

import android.app.Activity;

public final class AppManager {
    private static final AppManager INSTANCE = new AppManager();

    private AppManager() {
    }

    public static AppManager getInstance() {
        return INSTANCE;
    }

    public Activity currentActivity() {
        return null;
    }
}
