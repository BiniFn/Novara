package com.github.tvbox.osc.util;

import android.app.Activity;

import java.lang.ref.WeakReference;

public final class AppManager {
    private static final AppManager INSTANCE = new AppManager();
    private volatile WeakReference<Activity> current = new WeakReference<>(null);

    private AppManager() {
    }

    public static AppManager getInstance() {
        return INSTANCE;
    }

    public void setCurrentActivity(Activity activity) {
        current = new WeakReference<>(activity);
    }

    public Activity currentActivity() {
        return current.get();
    }
}
