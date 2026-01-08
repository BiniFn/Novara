package org.skepsun.kototoro.core.network.jsonsource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.skepsun.kototoro.core.network.MangaHttpClient
import org.skepsun.kototoro.core.network.cookies.MutableCookieJar
import org.skepsun.kototoro.parsers.model.MangaSource
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP client specifically for Legado JSON sources
 * Provides GET/POST methods with User-Agent management and cookie support
 */
@Singleton
class LegadoHttpClient @Inject constructor(
    @JsonSourceHttpClient private val okHttpClient: OkHttpClient,
    private val cookieJar: MutableCookieJar,
    private val userAgentManager: UserAgentManager,
) {

    /**
     * Execute a GET request
     * @param url Target URL
     * @param headers Optional custom headers
     * @param source Optional MangaSource for request tagging
     * @return HTTP response
     */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), source: MangaSource? = null): Response {
        return withContext(Dispatchers.IO) {
            val request = buildRequest(url, headers, source = source)
            okHttpClient.newCall(request).execute()
        }
    }

    /**
     * Execute a POST request with form data
     */
    suspend fun post(
        url: String,
        formData: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        source: MangaSource? = null
    ): Response {
        val formBody = FormBody.Builder().apply {
            formData.forEach { (key, value) ->
                add(key, value)
            }
        }.build()
        return post(url, formBody, headers, source)
    }

    /**
     * Execute a POST request with a raw body
     */
    suspend fun post(
        url: String,
        body: okhttp3.RequestBody,
        headers: Map<String, String> = emptyMap(),
        source: MangaSource? = null
    ): Response {
        return withContext(Dispatchers.IO) {
            val request = buildRequest(url, headers, method = "POST", body = body, source = source)
            okHttpClient.newCall(request).execute()
        }
    }

    /**
     * Build an HTTP request with appropriate headers
     */
    private fun buildRequest(
        url: String,
        customHeaders: Map<String, String>,
        method: String = "GET",
        body: okhttp3.RequestBody? = null,
        source: MangaSource? = null
    ): Request {
        val headersBuilder = Headers.Builder()
        
        // Add User-Agent if not provided
        if (!customHeaders.containsKey("User-Agent")) {
            // Use mobile User-Agent for better compatibility with mobile-first web sources
            headersBuilder.add("User-Agent", userAgentManager.getUserAgent())
        }
        
        // Add Referer if not provided (many sites require this)
        if (!customHeaders.containsKey("Referer")) {
            try {
                val parsedUrl = url.toHttpUrlOrNull() ?: URL(url).let { 
                    HttpUrl.Builder()
                        .scheme(it.protocol)
                        .host(it.host)
                        .build()
                }
                headersBuilder.add("Referer", "${parsedUrl.scheme}://${parsedUrl.host}/")
            } catch (_: Exception) {
                // Ignore if URL parsing fails
            }
        }
        
        // Add custom headers
        customHeaders.forEach { (key, value) ->
            headersBuilder.add(key, value)
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .headers(headersBuilder.build())
            .method(method, body)

        // 强制直连以避免 OkHttp 基于缓存的条件请求（部分站点会拒绝未来时间的 If-Modified-Since）
        if (source?.name?.startsWith("JSON_LEGADO", ignoreCase = true) == true) {
            requestBuilder.cacheControl(CacheControl.FORCE_NETWORK)
        }
        
        // Tag the request with the source if provided
        if (source != null) {
            requestBuilder.tag(MangaSource::class.java, source)
        }
        
        // Log cookies for debugging
        try {
            val httpUrl = url.toHttpUrlOrNull()
            if (httpUrl != null) {
                val cookies = cookieJar.loadForRequest(httpUrl)
                if (cookies.isNotEmpty()) {
                    android.util.Log.d("LegadoHttpClient", "Cookies for $url: ${cookies.map { "${it.name}=${it.value.take(20)}..." }}")
                } else {
                    android.util.Log.d("LegadoHttpClient", "No cookies for $url")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("LegadoHttpClient", "Failed to log cookies", e)
        }
        
        return requestBuilder.build()
    }



    /**
     * Get the current cookie jar for manual cookie management
     */
    fun getCookieJar(): MutableCookieJar = cookieJar
}
