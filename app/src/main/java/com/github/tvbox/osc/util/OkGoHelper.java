package com.github.tvbox.osc.util;

import com.github.tvbox.osc.base.App;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;

public class OkGoHelper {
    public static DnsOverHttps dnsOverHttps = null;

    private static final long DEFAULT_TIMEOUT_MS = 10000L;
    private static OkHttpClient defaultClient;
    private static OkHttpClient noRedirectClient;

    public static void init() {
        if (defaultClient != null) return;
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS));
        File cacheDir = App.getInstance().getCacheDir();
        if (cacheDir != null) {
            builder.cache(new Cache(new File(cacheDir, "tvbox_okhttp_cache"), 10L * 1024L * 1024L));
        }
        defaultClient = builder.build();
        noRedirectClient = defaultClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    public static OkHttpClient getDefaultClient() {
        if (defaultClient == null) {
            init();
        }
        return defaultClient;
    }

    public static OkHttpClient getNoRedirectClient() {
        if (noRedirectClient == null) {
            init();
        }
        return noRedirectClient;
    }
}
