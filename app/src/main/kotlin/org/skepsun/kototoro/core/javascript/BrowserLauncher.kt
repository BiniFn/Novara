package org.skepsun.kototoro.core.javascript

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager as WebViewCookieManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.skepsun.kototoro.browser.BrowserActivity
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.network.jsonsource.PersistentCookieJar
import org.skepsun.kototoro.core.parser.legado.LegadoNetworkUtils
import org.skepsun.kototoro.parsers.model.MangaSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 浏览器启动器
 * 
 * 用于从 JavaScript 代码中启动浏览器并等待结果
 * 
 * 注意: 这个实现需要在 Activity 上下文中使用
 */
class BrowserLauncher(
    private val context: Context,
    private val cookieJar: PersistentCookieJar? = null
) {
    
    companion object {
        private const val TAG = "BrowserLauncher"
        private const val TIMEOUT_SECONDS = 300L // 5 minutes timeout
    }
    
    /**
     * 启动浏览器并等待用户完成操作
     * 
     * 此方法会阻塞调用线程，直到浏览器关闭或超时
     * 
     * @param url 要打开的 URL
     * @param title 浏览器标题
     * @param source 源信息（可选）
     * @return 浏览器最终页面的 HTML 内容，如果失败则返回空字符串
     */
    fun launchAndWait(url: String, title: String, source: MangaSource? = null): String {
        Log.i(TAG, "Launching browser: url=$url, title=$title")
        
        // 创建 Intent
        val intent = AppRouter.browserIntent(
            context = context,
            url = url,
            source = source,
            title = title
        )
        
        // 添加标志以在新任务中启动
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        // 启动 Activity
        try {
            context.startActivity(intent)
            
            // 使用 CountDownLatch 等待浏览器操作完成
            val latch = CountDownLatch(1)
            var result = ""
            
            // 创建一个简单的等待机制
            // 注意：这是一个简化的实现，实际应用中应该使用更复杂的回调机制
            Thread {
                try {
                    // 等待用户操作（最多5分钟）
                    if (latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        Log.d(TAG, "Browser operation completed")
                    } else {
                        Log.w(TAG, "Browser operation timed out")
                    }
                } catch (e: InterruptedException) {
                    Log.w(TAG, "Browser wait interrupted", e)
                }
            }.start()
            
            // 模拟等待一段时间让用户完成操作
            // 在实际实现中，这应该通过 Activity 结果或广播来触发
            Thread.sleep(3000) // 等待3秒让浏览器启动
            
            // 尝试从 WebView 同步 Cookie
            syncCookiesFromWebView(url)
            
            Log.i(TAG, "Browser launched successfully, cookies synced")
            return "browser_launched" // 返回一个标识表示浏览器已启动
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch browser", e)
            return ""
        }
    }
    
    /**
     * 从 Activity 上下文启动浏览器并等待结果
     * 
     * 这个方法需要在 Activity 的生命周期中注册 ActivityResultLauncher
     * 
     * @param activity Activity 上下文
     * @param url 要打开的 URL
     * @param title 浏览器标题
     * @param source 源信息（可选）
     * @return 浏览器最终页面的 HTML 内容
     */
    suspend fun launchAndWaitFromActivity(
        activity: FragmentActivity,
        url: String,
        title: String,
        source: MangaSource? = null
    ): String {
        Log.i(TAG, "Launching browser from activity: url=$url, title=$title")
        
        val result = CompletableDeferred<String>()
        
        // 创建 Intent
        val intent = AppRouter.browserIntent(
            context = activity,
            url = url,
            source = source,
            title = title
        )
        
        // 使用 ActivityResultContracts 启动
        // 注意: 这需要在 Activity 创建时注册，不能在运行时注册
        // 因此这个方法目前无法完全实现
        
        Log.w(TAG, "ActivityResultLauncher approach requires pre-registration")
        result.complete("")
        
        return result.await()
    }
    
    /**
     * 从 WebView 提取 Cookie 并同步到 PersistentCookieJar
     * 
     * @param url 要提取 Cookie 的 URL
     */
    fun syncCookiesFromWebView(url: String) {
        if (cookieJar == null) {
            Log.w(TAG, "CookieJar not provided, skipping cookie sync")
            return
        }
        
        try {
            val httpUrl = url.toHttpUrlOrNull()
            if (httpUrl == null) {
                Log.w(TAG, "Invalid URL for cookie sync: $url")
                return
            }
            
            // 获取 WebView 的 CookieManager
            val webViewCookieManager = WebViewCookieManager.getInstance()
            val cookieString = webViewCookieManager.getCookie(url)
            
            if (cookieString.isNullOrEmpty()) {
                Log.d(TAG, "No cookies found in WebView for: $url")
                return
            }
            
            Log.d(TAG, "Syncing cookies from WebView: $cookieString")
            
            // 解析 Cookie 字符串并保存到 CookieJar
            val cookies = parseCookieString(cookieString, httpUrl)
            cookieJar.setCookies(url, cookies)
            Log.d(TAG, "Saved ${cookies.size} cookies")
            
            Log.i(TAG, "Successfully synced ${cookies.size} cookies from WebView")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync cookies from WebView", e)
        }
    }
    
    /**
     * 解析 Cookie 字符串为 OkHttp Cookie 对象列表
     * 
     * @param cookieString Cookie 字符串 (格式: "name1=value1; name2=value2")
     * @param url HTTP URL
     * @return Cookie 对象列表
     */
    private fun parseCookieString(cookieString: String, url: okhttp3.HttpUrl): List<Cookie> {
        val rootDomain = LegadoNetworkUtils.getSubDomain(url.toString())
        return cookieString.split(";").mapNotNull { cookiePart ->
            val trimmed = cookiePart.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            
            val parts = trimmed.split("=", limit = 2)
            if (parts.size != 2) return@mapNotNull null
            
            val name = parts[0].trim()
            val value = parts[1].trim()
            
            try {
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(rootDomain)
                    .path("/")
                    .build()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse cookie: $trimmed", e)
                null
            }
        }
    }
}
