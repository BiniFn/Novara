package com.github.tvbox.osc.data;

public final class AppDataManager {
    private static volatile boolean initialized = false;

    private AppDataManager() {
    }

    public static void init() {
        initialized = true;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
