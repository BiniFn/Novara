package com.github.tvbox.osc.util;

public final class EpgUtil {
    private static volatile boolean initialized = false;

    private EpgUtil() {
    }

    public static void init() {
        initialized = true;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
