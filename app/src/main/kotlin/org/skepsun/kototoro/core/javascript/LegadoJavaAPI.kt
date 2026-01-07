package org.skepsun.kototoro.core.javascript

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.util.Base64
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import java.net.CookieManager
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Legado Java API 实现
 * 
 * 提供 Legado 兼容的 Java API，供 JavaScript 代码调用
 * 参考 Legado 源码: app/src/main/java/io/legado/app/help/http/HttpHelper.kt
 */
class LegadoJavaAPI(
    private val httpClient: LegadoHttpClient,
    private val cookieManager: CookieManager,
    private val context: Context,
    private val cookieJar: org.skepsun.kototoro.core.network.jsonsource.PersistentCookieJar? = null
) {
    
    private var currentHtml: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val browserLauncher = BrowserLauncher(context, cookieJar)
    
    // 存储 JavaScript 变量的 Map
    private val jsVariables = mutableMapOf<String, Any?>()
    
    // JavaScript 上下文引用（由 RhinoJavaScriptEngine 设置）
    var jsContext: JavaScriptContext? = null
    
    companion object {
        private const val TAG = "LegadoJavaAPI"
    }
    
    /**
     * 执行 HTTP 请求 (GET)
     * 
     * @param url 请求 URL
     * @return 响应内容
     */
    fun ajax(url: String): String {
        return ajax(url, null)
    }
    
    /**
     * 执行 HTTP 请求 (支持 GET/POST)
     * 
     * @param url 请求 URL
     * @param options 请求选项（可选）
     *   - method: 请求方法 (GET/POST)
     *   - headers: 请求头
     *   - body: 请求体
     *   - charset: 字符编码
     * @return 响应内容
     */
    fun ajax(url: String, options: Map<String, Any>?): String {
        return try {
            val method = options?.get("method") as? String ?: "GET"
            val headers = (options?.get("headers") as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() } ?: emptyMap()
            val body = options?.get("body") as? String
            val charset = options?.get("charset") as? String ?: "UTF-8"
            
            val response = runBlocking {
                when (method.uppercase()) {
                    "POST" -> {
                        // Convert body to form data if it's a string
                        val formData = if (body != null) {
                            // Parse body as form data (key=value&key2=value2)
                            body.split("&").associate {
                                val parts = it.split("=", limit = 2)
                                parts[0] to (parts.getOrNull(1) ?: "")
                            }
                        } else {
                            emptyMap()
                        }
                        httpClient.post(url, formData, headers)
                    }
                    else -> httpClient.get(url, headers)
                }
            }
            
            response.body?.string() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Ajax request failed: $url", e)
            throw e
        }
    }
    
    /**
     * 使用 CSS 选择器解析 HTML
     * 
     * @param selector CSS 选择器
     * @return 匹配的元素列表
     */
    fun getElement(selector: String): Elements {
        val html = currentHtml ?: throw IllegalStateException("No HTML content set. Call setContent() first.")
        
        // 转换 Legado 选择器到 Jsoup 选择器
        val jsoupSelector = convertLegadoSelectorToJsoup(selector)
        
        val doc = Jsoup.parse(html)
        val elements = doc.select(jsoupSelector)
        Log.d(TAG, "getElement($selector -> $jsoupSelector) found ${elements.size} elements")
        
        // 在JavaScript上下文中设置选择器，以便后续使用
        jsContext?.setVariable("lastSelector", selector)
        put("lastSelector", selector)
        
        return elements
    }
    
    /**
     * 转换 Legado 选择器到 Jsoup 选择器
     */
    private fun convertLegadoSelectorToJsoup(selector: String): String {
        return when {
            selector.startsWith("@css:") -> selector.removePrefix("@css:")
            selector.startsWith("class.") -> ".${selector.removePrefix("class.")}"
            selector.startsWith("id.") -> "#${selector.removePrefix("id.")}"
            selector.startsWith("tag.") -> selector.removePrefix("tag.")
            else -> selector
        }
    }
    
    /**
     * 设置当前 HTML 内容
     * 
     * @param html HTML 字符串
     */
    fun setContent(html: String) {
        currentHtml = html
    }
    
    /**
     * Base64 编码
     * 
     * @param str 要编码的字符串
     * @return Base64 编码后的字符串
     */
    fun base64Encode(str: String): String {
        return Base64.getEncoder().encodeToString(str.toByteArray())
    }
    
    /**
     * Base64 解码
     * 
     * @param str Base64 编码的字符串
     * @return 解码后的字符串
     */
    fun base64Decode(str: String): String {
        return String(Base64.getDecoder().decode(str))
    }
    
    /**
     * 十六进制字符串解码为普通字符串
     * 
     * @param hex 十六进制字符串
     * @return 解码后的字符串
     */
    fun hexDecodeToString(hex: String): String {
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return String(bytes)
    }
    
    /**
     * 时间格式化
     * 
     * @param timestamp 时间戳（毫秒）
     * @param format 格式字符串 (如 "yyyy-MM-dd HH:mm:ss")
     * @param timezone 时区 (如 "GMT+8")
     * @return 格式化后的时间字符串
     */
    fun timeFormat(timestamp: Long, format: String, timezone: String? = null): String {
        val sdf = SimpleDateFormat(format, Locale.getDefault())
        if (timezone != null) {
            sdf.timeZone = TimeZone.getTimeZone(timezone)
        }
        return sdf.format(Date(timestamp))
    }
    
    /**
     * UTC 时间格式化
     * 
     * @param timestamp 时间戳（毫秒）
     * @param format 格式字符串
     * @param offset UTC 偏移量（小时）
     * @return 格式化后的时间字符串
     */
    fun timeFormatUTC(timestamp: Long, format: String, offset: Int = 0): String {
        val sdf = SimpleDateFormat(format, Locale.getDefault())
        val timezone = if (offset >= 0) {
            "GMT+$offset"
        } else {
            "GMT$offset"
        }
        sdf.timeZone = TimeZone.getTimeZone(timezone)
        return sdf.format(Date(timestamp))
    }
    
    /**
     * 显示 Toast 提示
     * 
     * @param message 提示消息
     */
    fun toast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示长时间 Toast 提示
     * 
     * @param message 提示消息
     */
    fun longToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 获取设备的 Android ID
     * 
     * @return Android ID
     */
    fun androidId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
    }
    
    /**
     * 繁体转简体
     * 
     * @param text 繁体中文文本
     * @return 简体中文文本
     */
    fun t2s(text: String): String {
        // TODO: 集成繁简转换库
        // 目前返回原文本
        Log.w(TAG, "t2s not implemented yet, returning original text")
        return text
    }
    
    /**
     * 简体转繁体
     * 
     * @param text 简体中文文本
     * @return 繁体中文文本
     */
    fun s2t(text: String): String {
        // TODO: 集成繁简转换库
        // 目前返回原文本
        Log.w(TAG, "s2t not implemented yet, returning original text")
        return text
    }
    
    /**
     * 从 JSON 数据中提取字符串值
     * 
     * @param jsonPath JSONPath 表达式
     * @return 提取的字符串值
     */
    fun getString(jsonPath: String): String? {
        // TODO: 实现 JSONPath 提取
        // 需要集成 JSONPath 库
        Log.w(TAG, "getString not implemented yet")
        return null
    }
    
    /**
     * 从上下文中获取指定键的值
     * 
     * 支持从 JavaScript 变量存储和上下文中获取值
     * 
     * @param key 键名
     * @return 值
     */
    fun get(key: String): Any? {
        // 首先检查 JavaScript 变量存储
        if (jsVariables.containsKey(key)) {
            return jsVariables[key]
        }
        
        // 然后检查 JavaScript 上下文
        return jsContext?.getVariable(key)
    }
    
    /**
     * 设置 JavaScript 变量
     * 
     * 用于在 JavaScript 代码中存储临时变量
     * 
     * @param key 键名
     * @param value 值
     */
    fun put(key: String, value: Any?) {
        jsVariables[key] = value
        Log.d(TAG, "Set JavaScript variable: $key = $value")
    }
    
    /**
     * 获取当前源的唯一标识符
     * 
     * 用于 Cookie 管理等需要源标识的场景
     * 
     * @return 源标识符
     */
    fun getSourceKey(): String {
        return jsContext?.source?.bookSourceUrl ?: "unknown_source"
    }
    
    /**
     * 打开内置浏览器并等待用户操作完成
     * 
     * 此方法会阻塞 JavaScript 执行，直到用户关闭浏览器或完成操作
     * 专门为起点中文网等需要 Cookie 验证的网站设计
     * 
     * @param url 要打开的 URL
     * @param title 浏览器标题
     * @return 浏览器最终页面的 HTML 内容
     */
    fun startBrowserAwait(url: String, title: String): String {
        Log.i(TAG, "startBrowserAwait called: url=$url, title=$title")
        
        try {
            // 显示提示信息
            toast("正在启动浏览器进行验证...")
            
            // 使用 BrowserLauncher 启动浏览器
            val result = browserLauncher.launchAndWait(url, title, null)
            
            // 等待一段时间让用户完成验证
            Thread.sleep(2000)
            
            // 同步 Cookie 从 WebView 到 OkHttp CookieJar
            // 这样后续的 HTTP 请求可以使用浏览器中获取的 Cookie
            browserLauncher.syncCookiesFromWebView(url)
            
            // 显示完成提示
            toast("浏览器验证完成，Cookie 已同步")
            
            Log.i(TAG, "Browser verification completed for: $url")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch browser", e)
            toast("启动浏览器失败: ${e.message}")
            return ""
        }
    }
}
