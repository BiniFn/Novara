package com.orhanobut.hawk;

import android.content.Context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Hawk {
    private static final Map<String, Object> STORE = new ConcurrentHashMap<>();

    private Hawk() {
    }

    public static Builder init(Context context) {
        return new Builder();
    }

    public static boolean contains(String key) {
        return STORE.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        return (T) STORE.get(key);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key, T defaultValue) {
        Object value = STORE.get(key);
        return value == null ? defaultValue : (T) value;
    }

    public static boolean put(String key, Object value) {
        STORE.put(key, value);
        return true;
    }

    public static final class Builder {
        public Builder build() {
            return this;
        }
    }
}
