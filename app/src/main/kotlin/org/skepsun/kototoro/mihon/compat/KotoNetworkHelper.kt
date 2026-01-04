package org.skepsun.kototoro.mihon.compat

import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.OkHttpClient
import org.skepsun.kototoro.parsers.network.UserAgents

/**
 * Kototoro's implementation of Mihon's NetworkHelper interface.
 * 
 * Wraps Kototoro's existing OkHttpClient to provide Mihon extensions with
 * access to the network stack, including CloudFlare bypassing and cookie management.
 * 
 * Note: We create a new client without GZipInterceptor because Mihon extensions
 * handle their own request encoding. Kototoro's GZipInterceptor incorrectly
 * adds Content-Encoding: gzip header without actually compressing the body,
 * which causes server-side decompression errors (e.g., Picacomic login fails with
 * "incorrect header check").
 */
class KotoNetworkHelper(
    baseClient: OkHttpClient,
    val cookieJar: okhttp3.CookieJar,
) : NetworkHelper() {
    
    /**
     * The OkHttpClient for Mihon extensions.
     * We rebuild without GZipInterceptor to prevent incorrect Content-Encoding headers.
     */
    override val client: OkHttpClient = run {
        val builder = OkHttpClient.Builder()
        
        // Copy configuration from base client
        builder.connectTimeout(baseClient.connectTimeoutMillis.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
        builder.readTimeout(baseClient.readTimeoutMillis.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
        builder.writeTimeout(baseClient.writeTimeoutMillis.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
        builder.cookieJar(baseClient.cookieJar)
        builder.dns(baseClient.dns)
        builder.cache(baseClient.cache)
        builder.dispatcher(baseClient.dispatcher)
        builder.connectionPool(baseClient.connectionPool)
        builder.followRedirects(baseClient.followRedirects)
        builder.followSslRedirects(baseClient.followSslRedirects)
        builder.retryOnConnectionFailure(baseClient.retryOnConnectionFailure)
        
        // Copy interceptors but exclude GZipInterceptor
        baseClient.interceptors.forEach { interceptor ->
            if (interceptor.javaClass.simpleName != "GZipInterceptor") {
                builder.addInterceptor(interceptor)
            } else {
                android.util.Log.d("KotoNetworkHelper", "Skipping GZipInterceptor for Mihon client")
            }
        }
        
        // Copy network interceptors
        baseClient.networkInterceptors.forEach { interceptor ->
            builder.addNetworkInterceptor(interceptor)
        }
        
        // Add debug logging interceptor for Mihon extensions
        builder.addInterceptor { chain ->
            val request = chain.request()
            android.util.Log.d("MihonNetwork", "Request: ${request.method} ${request.url}")
            
            val response = chain.proceed(request)
            
            // Log response info
            val responseCode = response.code
            val contentType = response.header("Content-Type")
            android.util.Log.d("MihonNetwork", "Response: $responseCode, Content-Type: $contentType, URL: ${request.url}")
            
            // If response is not successful, log the first 200 chars of body for debugging
            if (!response.isSuccessful) {
                val source = response.body.source()
                source.request(200)
                val buffer = source.buffer.clone()
                val preview = buffer.readUtf8(minOf(200, buffer.size))
                android.util.Log.w("MihonNetwork", "Non-successful response ($responseCode) preview: $preview")
            }
            
            response
        }
        
        builder.build()
    }
    
    /**
     * @deprecated Since extension-lib 1.5, CloudFlare is handled by the regular client.
     */
    @Deprecated("The regular client handles Cloudflare by default")
    override val cloudflareClient: OkHttpClient = client
    
    /**
     * Returns the default user agent string.
     */
    override fun defaultUserAgentProvider(): String = UserAgents.FIREFOX_MOBILE
}
