package org.skepsun.kototoro.core.parser.legado

import android.net.Uri
import androidx.media3.common.MediaItem
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.InputStream
import okhttp3.Headers
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.skepsun.kototoro.core.javascript.JavaScriptBridgeBindings
import org.skepsun.kototoro.core.parser.legado.runtime.LegadoHttpExecutor
import org.skepsun.kototoro.core.parser.legado.runtime.LegadoRequestPlan

/**
 * 为 loginCheckJs 提供更接近 MD3 AnalyzeUrl 的 `java` 桥接。
 *
 * 当前优先补齐高频能力：
 * - `getHeaderMap()`
 * - `getResponse()`
 * - `getStrResponse()`
 * - `initUrl()`
 *
 * 这覆盖了登录检测脚本里最常见的“登录后重放请求”路径。
 */
internal class AnalyzeUrlLoginJsBridge(
    private val basePlan: LegadoRequestPlan,
    private val httpExecutor: LegadoHttpExecutor,
    initialHeaders: Map<String, String>,
    private val sourceTag: String? = null,
    private val sourceObject: Any? = null,
    private val infoMap: MutableMap<String, String>? = null,
) : JavaScriptBridgeBindings {
    companion object {
        private const val MEDIA_ITEM_SPLIT_TAG = ",{"
    }

    private val headerMap = LinkedHashMap(initialHeaders)
    private val variables = LinkedHashMap<String, String>()

    fun initUrl() {
        // 当前阶段不重新解析 ruleUrl，只同步一次最新 headerMap。
        // 这样至少可对齐 MD3 登录后“修改 header 再重放”的主路径。
    }

    fun getHeaderMap(): MutableMap<String, String> = headerMap

    fun getResponse(): Response {
        return executeResponse().first
    }

    @Suppress("unused")
    fun getStrResponse(
        jsStr: String? = null,
        sourceRegex: String? = null,
        useWebView: Boolean = true,
    ): LoginCheckStrResponse {
        val (response, bodyText) = executeResponse(
            jsStr = jsStr,
            sourceRegex = sourceRegex,
            useWebView = useWebView,
        )
        return LoginCheckStrResponse(response, bodyText)
    }

    @Suppress("unused")
    fun getStrResponse(
        jsStr: String? = null,
        sourceRegex: String? = null,
        overrideUrlRegex: String? = null,
        useWebView: Boolean = true,
    ): LoginCheckStrResponse {
        val (response, bodyText) = executeResponse(
            jsStr = jsStr,
            sourceRegex = sourceRegex,
            overrideUrlRegex = overrideUrlRegex,
            useWebView = useWebView,
        )
        return LoginCheckStrResponse(response, bodyText)
    }

    fun getErrResponse(e: Throwable): Response {
        return buildOkHttpResponse(
            url = basePlan.url,
            code = 500,
            headers = emptyMap(),
            bodyBytes = e.stackTraceToString().toByteArray(Charsets.UTF_8),
            message = e.message ?: "Error Response",
        )
    }

    fun getErrStrResponse(e: Throwable): LoginCheckStrResponse {
        val bodyText = e.stackTraceToString()
        return LoginCheckStrResponse(getErrResponse(e), bodyText)
    }

    fun getByteArray(): ByteArray {
        return runBlocking {
            getByteArrayAwait()
        }
    }

    suspend fun getByteArrayAwait(): ByteArray {
        return executeResponse().first.body?.bytes() ?: ByteArray(0)
    }

    fun getInputStream(): InputStream {
        return runBlocking {
            getInputStreamAwait()
        }
    }

    suspend fun getInputStreamAwait(): InputStream {
        return ByteArrayInputStream(getByteArrayAwait())
    }

    fun getGlideUrl(): Any {
        return getUrlAndHeaders()
    }

    fun getMediaItem(): MediaItem {
        val plan = currentPlan()
        val mediaUri = formatMediaItemUri(plan.url, plan.headers)
        return MediaItem.Builder()
            .setMediaId(mediaUri)
            .setUri(mediaUri)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(Uri.parse(mediaUri))
                    .build(),
            )
            .build()
    }

    fun getUrlAndHeaders(): Pair<String, Map<String, String>> {
        val plan = currentPlan()
        return plan.url to plan.headers
    }

    fun getUserAgent(): String {
        return headerMap.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value.orEmpty()
    }

    fun isPost(): Boolean = basePlan.method.equals("POST", ignoreCase = true)

    fun put(key: String, value: String): String {
        variables[key] = value
        infoMap?.put(key, value)
        invokeSourceMethod("put", key, value)
        return value
    }

    fun get(key: String): String {
        return variables[key]
            ?.takeIf { it.isNotEmpty() }
            ?: infoMap?.get(key)?.takeIf { it.isNotEmpty() }
            ?: invokeSourceMethod("get", key)?.toString()?.takeIf { it.isNotEmpty() }
            ?: ""
    }

    fun getTag(): String? = sourceTag
        ?: invokeSourceMethod("getTag")?.toString()
        ?: LegadoReflectiveAccess.readProperty(sourceObject, "bookSourceName")?.toString()
        ?: LegadoReflectiveAccess.readProperty(sourceObject, "sourceName")?.toString()

    fun getSource(): Any? = sourceObject

    override fun getBridgeBindings(): Map<String, Any?> {
        return buildMap {
            put("infoMap", infoMap ?: emptyMap<String, String>())
        }
    }

    private fun executeResponse(
        jsStr: String? = null,
        sourceRegex: String? = null,
        overrideUrlRegex: String? = null,
        useWebView: Boolean? = null,
    ): Pair<Response, String> {
        val httpResponse = runBlocking {
            httpExecutor.execute(currentPlan(jsStr, sourceRegex, overrideUrlRegex, useWebView))
        }
        val bodyText = httpResponse.body
        val response = buildOkHttpResponse(
            url = httpResponse.url,
            code = httpResponse.code ?: 200,
            headers = httpResponse.headers,
            bodyText = bodyText,
        )
        return response to bodyText
    }

    private fun currentPlan(
        jsStr: String? = null,
        sourceRegex: String? = null,
        overrideUrlRegex: String? = null,
        useWebView: Boolean? = null,
    ): LegadoRequestPlan {
        return basePlan.copy(
            headers = headerMap.toMap(),
            webJs = jsStr?.takeIf { it.isNotBlank() } ?: basePlan.webJs,
            sourceRegex = sourceRegex?.takeIf { it.isNotBlank() } ?: basePlan.sourceRegex,
            overrideUrlRegex = overrideUrlRegex?.takeIf { it.isNotBlank() } ?: basePlan.overrideUrlRegex,
            useWebView = useWebView ?: basePlan.useWebView,
        )
    }

    private fun invokeSourceMethod(name: String, vararg args: Any?): Any? {
        val source = sourceObject ?: return null
        return runCatching {
            source.javaClass.methods.firstOrNull { method ->
                method.name == name && method.parameterTypes.size == args.size
            }?.apply { isAccessible = true }
                ?.invoke(source, *args)
        }.getOrNull()
    }

    private fun formatMediaItemUri(
        url: String,
        headers: Map<String, String>,
    ): String {
        if (headers.isEmpty()) return url
        val headersJson = org.json.JSONObject()
        headers.forEach { (key, value) ->
            headersJson.put(key, value)
        }
        return url + MEDIA_ITEM_SPLIT_TAG + headersJson.toString()
    }

    fun buildOkHttpResponse(
        url: String,
        code: Int,
        headers: Map<String, String>,
        bodyText: String,
    ): Response {
        return buildOkHttpResponse(
            url = url,
            code = code,
            headers = headers,
            bodyBytes = bodyText.toByteArray(Charsets.UTF_8),
        )
    }

    private fun buildOkHttpResponse(
        url: String,
        code: Int,
        headers: Map<String, String>,
        bodyBytes: ByteArray,
        message: String = "",
    ): Response {
        val request = Request.Builder()
            .url(url)
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .headers(
                Headers.Builder().apply {
                    headers.forEach { (name, value) -> add(name, value) }
                }.build(),
            )
            .body(bodyBytes.toResponseBody(null))
            .build()
    }
}

/**
 * loginCheckJs 对齐 MD3 的最小结果对象。
 *
 * 关键差异：
 * - 既保留 `StrResponse.body()` 的字符串语义
 * - 也补上 `result.body().string()` 这种基于 okhttp `Response` 的访问方式
 */
internal class LoginCheckStrResponse(
    private val response: Response,
    private val bodyText: String,
) {
    val raw: Response
        get() = response

    val body: String
        get() = bodyText

    val url: String
        get() = url()

    fun raw(): Response = response

    fun body(): BodyAccessor = BodyAccessor(bodyText, response.body?.contentType())

    fun url(): String = response.request.url.toString()

    fun code(): Int = response.code

    fun message(): String = response.message

    fun headers(): Headers = response.headers

    fun header(name: String): String? = response.header(name)

    fun isSuccessful(): Boolean = response.isSuccessful

    class BodyAccessor(
        private val text: String,
        private val contentType: okhttp3.MediaType?,
    ) {
        fun string(): String = text

        fun bytes(): ByteArray = text.toByteArray(Charsets.UTF_8)

        fun byteStream(): java.io.InputStream = bytes().inputStream()

        fun contentType(): okhttp3.MediaType? = contentType

        fun contentLength(): Long = bytes().size.toLong()

        override fun toString(): String = text
    }
}
