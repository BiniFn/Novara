package com.github.tvbox.osc.util;

public final class PlayerHelper {
    private static volatile boolean initialized = false;

    private PlayerHelper() {
    }

    public static void init() {
        initialized = true;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
