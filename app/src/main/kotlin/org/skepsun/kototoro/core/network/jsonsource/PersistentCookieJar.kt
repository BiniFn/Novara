package org.skepsun.kototoro.core.network.jsonsource

import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.skepsun.kototoro.core.network.cookies.MutableCookieJar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent cookie jar for JSON sources
 * Wraps the app's MutableCookieJar with JSON-source-specific utilities
 * 
 * Features:
 * - Automatic cookie persistence via Android's CookieManager
 * - Cross-request cookie sharing
 * - Domain-specific cookie management
 */
@Singleton
class PersistentCookieJar @Inject constructor(
    private val cookieJar: MutableCookieJar
) {

    /**
     * Get all cookies for a URL
     * @param url Target URL
     * @return List of cookies
     */
    fun getCookies(url: String): List<Cookie> {
        return cookieJar.loadForRequest(url.toHttpUrl())
    }

    /**
     * Get all cookies for a domain
     * @param domain Domain name (e.g., "example.com")
     * @return List of cookies
     */
    fun getCookiesForDomain(domain: String): List<Cookie> {
        // Use https by default for cookie retrieval
        val url = "https://$domain".toHttpUrl()
        return cookieJar.loadForRequest(url)
    }

    /**
     * Set cookies for a URL
     * @param url Target URL
     * @param cookies Cookies to set
     */
    fun setCookies(url: String, cookies: List<Cookie>) {
        cookieJar.saveFromResponse(url.toHttpUrl(), cookies)
    }

    /**
     * Remove all cookies for a URL
     * @param url Target URL
     */
    fun removeCookies(url: String) {
        cookieJar.removeCookies(url.toHttpUrl(), null)
    }

    /**
     * Remove specific cookies for a URL
     * @param url Target URL
     * @param cookieNames Names of cookies to remove
     */
    fun removeCookies(url: String, cookieNames: Set<String>) {
        cookieJar.removeCookies(url.toHttpUrl()) { cookie ->
            cookie.name in cookieNames
        }
    }

    /**
     * Clear all cookies
     */
    suspend fun clearAll(): Boolean {
        return cookieJar.clear()
    }

    /**
     * Get a specific cookie by name
     * @param url Target URL
     * @param name Cookie name
     * @return Cookie value or null if not found
     */
    fun getCookie(url: String, name: String): String? {
        return getCookies(url).find { it.name == name }?.value
    }

    /**
     * Check if a cookie exists
     * @param url Target URL
     * @param name Cookie name
     * @return true if cookie exists
     */
    fun hasCookie(url: String, name: String): Boolean {
        return getCookie(url, name) != null
    }

    /**
     * Get the underlying MutableCookieJar for advanced usage
     */
    fun getUnderlyingJar(): MutableCookieJar = cookieJar
}
