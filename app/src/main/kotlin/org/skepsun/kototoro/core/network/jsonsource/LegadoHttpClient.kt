package org.skepsun.kototoro.core.network.jsonsource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.skepsun.kototoro.core.network.MangaHttpClient
import org.skepsun.kototoro.core.network.cookies.MutableCookieJar
import org.skepsun.kototoro.parsers.model.MangaSource
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
     * Execute a POST request
     * @param url Target URL
     * @param formData Form data to post
     * @param headers Optional custom headers
     * @param source Optional MangaSource for request tagging
     * @return HTTP response
     */
    suspend fun post(
        url: String,
        formData: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        source: MangaSource? = null
    ): Response {
        return withContext(Dispatchers.IO) {
            val formBody = FormBody.Builder().apply {
                formData.forEach { (key, value) ->
                    add(key, value)
                }
            }.build()

            val request = buildRequest(url, headers, method = "POST", body = formBody, source = source)
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
            headersBuilder.add("User-Agent", userAgentManager.getUserAgent())
        }
        
        // Add custom headers
        customHeaders.forEach { (key, value) ->
            headersBuilder.add(key, value)
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .headers(headersBuilder.build())
            .method(method, body)
        
        // Tag the request with the source if provided
        if (source != null) {
            requestBuilder.tag(MangaSource::class.java, source)
        }
        
        return requestBuilder.build()
    }

    /**
     * Get the current cookie jar for manual cookie management
     */
    fun getCookieJar(): MutableCookieJar = cookieJar
}
