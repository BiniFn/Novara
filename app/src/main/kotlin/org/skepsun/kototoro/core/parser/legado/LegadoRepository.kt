package org.skepsun.kototoro.core.parser.legado

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.json.JSONObject
import okhttp3.Response
import org.skepsun.kototoro.core.jsonsource.JsonMangaSource
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.core.parser.legado.book.BookChapterList
import org.skepsun.kototoro.core.parser.legado.book.BookContent
import org.skepsun.kototoro.core.parser.legado.book.BookInfo
import org.skepsun.kototoro.core.parser.legado.book.BookList
import org.skepsun.kototoro.core.parser.legado.sandbox.LegadoSandbox
import org.skepsun.kototoro.core.javascript.JavaScriptEngine
import org.skepsun.kototoro.parsers.model.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.charset.Charset
import org.skepsun.kototoro.core.cache.MemoryContentCache
import org.skepsun.kototoro.core.cache.SafeDeferred
import org.skepsun.kototoro.core.util.ext.processLifecycleScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers

import java.util.*
import java.net.URLDecoder

/**
 * Main repository implementation for Legado JSON sources.
 * Orchestrates modular components for search, details, TOC, and content parsing.
 * 
 * Target size: ~200 lines (Refactored from 1875 lines)
 */
class LegadoRepository(
    override val source: MangaSource,
    private val httpClient: LegadoHttpClient,
    private val jsEngine: JavaScriptEngine,
    private val memoryCache: MemoryContentCache? = null,
    private val browserLauncher: org.skepsun.kototoro.core.javascript.BrowserLauncher? = null
) : MangaRepository {

    companion object {
        private const val TAG = "LegadoRepository"
        private const val MAX_AUTO_TOC_PAGES = 200
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            allowTrailingComma = true
        }
    }

    private val jsonSource: JsonMangaSource = source as JsonMangaSource
    private val config: LegadoBookSource by lazy {
        json.decodeFromString<LegadoBookSource>(jsonSource.entity.config)
    }

    private val sandbox: LegadoSandbox by lazy {
        LegadoSandbox(jsEngine, httpClient, config)
    }

    /**
     * 清理该源的内存缓存（详情/目录/页面）。
     *
     * 用途：
     * - 在设置页修改 source/book 变量后，让规则重新生效
     * - 排查网络错误导致的“脏缓存”
     */
    fun invalidateCache() {
        memoryCache?.clear(source)
    }

    /**
     * Get headers from source config, evaluating JS each time (like legado's getHeaderMap)
     * This allows dynamic headers from JS scripts to work properly
     */
    private fun getConfigHeaders(): Map<String, String> {
        return parseConfigHeaders(config.header)
    }

    /**
     * Default UA for when source doesn't specify one
     * Using Windows Chrome desktop UA like legado does for better source compatibility
     * Many sources return different HTML for mobile vs desktop
     */
    private val defaultUserAgent: String = 
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    /**
     * Get User-Agent, preferring source config over default (case-insensitive)
     */
    private fun getSourceUserAgent(): String {
        val headers = getConfigHeaders()
        return headers.entries.find { it.key.equals("User-Agent", ignoreCase = true) }?.value ?: defaultUserAgent
    }

    override val sortOrders: Set<SortOrder> = EnumSet.allOf(SortOrder::class.java)
    override var defaultSortOrder: SortOrder = SortOrder.NEWEST

    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isSearchSupported = !config.searchUrl.isNullOrBlank(),
            isSearchWithFiltersSupported = false,
            isMultipleTagsSupported = false,
            isTagsExclusionSupported = false,
            isAuthorSearchSupported = false
        )

    /**
     * Parses exploreUrl into a list of ExploreKind categories.
     * Supports:
     * 1. JSON array format: [{'title':'...', 'url':'...'}, ...]
     * 2. Text format: title1::url1\ntitle2::url2 (or separated by &&)
     */
    private fun parseExploreKinds(): List<ExploreKind> {
        val exploreUrl = config.exploreUrl
        if (exploreUrl.isNullOrBlank()) return emptyList()
        
        val trimmed = exploreUrl.trim()
        
        return try {
            if (trimmed.startsWith("[")) {
                // JSON array format
                val normalized = trimmed.replace("'", "\"")
                json.decodeFromString<List<ExploreKind>>(normalized)
            } else {
                // Text format: title::url separated by && or newline
                trimmed.split("(&&|\n)+".toRegex()).mapNotNull { kindStr ->
                    val parts = kindStr.trim().split("::")
                    if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                        ExploreKind(parts[0].trim(), parts.getOrNull(1)?.trim())
                    } else null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse exploreUrl", e)
            emptyList()
        }
    }

    /**
     * Gets the first valid URL from explore kinds (fallback behavior).
     */
    private fun getFirstExploreUrl(): String? {
        return parseExploreKinds().firstOrNull { !it.url.isNullOrBlank() }?.url
    }

    override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
        val query = filter?.query
        val isSearch = !query.isNullOrBlank()
        val pageSize = 18 
        val page = (offset / pageSize) + 1
        
        val selectedCategoryUrl = filter?.tags?.find { it.key.startsWith("category:") }
            ?.key?.removePrefix("category:")
        
        val ruleUrl = when {
            isSearch -> config.searchUrl!!
            selectedCategoryUrl != null -> selectedCategoryUrl
            else -> getFirstExploreUrl() ?: config.searchUrl!!
        }
        
        val request = AnalyzeUrl(
            ruleUrl = ruleUrl,
            key = query,
            page = page,
            baseUrl = effectiveBaseUrlForRequest(ruleUrl),
            ruleData = sandbox.getRuleData(),
            sandbox = sandbox,
            useWebViewDefault = config.ruleSearch?.webView == true
        ).build()
        val result = executeRequest(request)
        
        if (result == null) return emptyList()
        val (content, finalUrl) = result
        
        return BookList.parse(content, finalUrl, source, config, isSearch, sandbox)
    }

    private val rateLimiter by lazy {
        ConcurrentRateLimiter(config.bookSourceUrl, config.concurrentRate)
    }


    /**
     * Centralized request executor with retry and protection logic
     */
    private suspend fun executeRequest(request: AnalyzeUrl.UrlResult): Pair<String, String>? {
        // Legado sources often have 0 retries by default, but we need more for stability
        // Increase to 5 to handle aggressive sites like bilinovel
        val maxRetries = (request.retry).coerceAtLeast(5)
        
        // Ensure URL is a valid web URL
        if (!request.url.startsWith("http", ignoreCase = true)) {
            Log.w(TAG, "Skipping non-HTTP request in LegadoRepository: ${request.url}")
            return null
        }
        
        repeat(maxRetries) { attempt ->
            try {
                // Extract priority from context
                val priority = kotlin.coroutines.coroutineContext[RequestPriority]?.priority ?: RequestPriority.FOREGROUND

                // Apply concurrency limit
                return rateLimiter.withLimit(priority) {
                    // Sync CloudFlare cookies (部分书源的 bookSourceUrl 可能不是合法 URL，需回退到当前请求 URL)
                    val cookieSyncUrl = config.bookSourceUrl.takeIf { it.startsWith("http", ignoreCase = true) } ?: request.url
                    LegadoCloudFlareResolver.syncCloudFlareCookies(cookieSyncUrl)
                    
                    val headersWithUa = getConfigHeaders().toMutableMap()
                    
                    // Helper to check for key existence case-insensitively
                    fun MutableMap<String, String>.containsKeyIgnoreCase(key: String): Boolean =
                        this.keys.any { it.equals(key, ignoreCase = true) }

                    // Request-specific headers override source config headers
                    request.headers.forEach { (k, v) ->
                        // Remove existing similar key to avoid duplicates with different casing
                        val existingKey = headersWithUa.keys.find { it.equals(k, ignoreCase = true) }
                        if (existingKey != null) headersWithUa.remove(existingKey)
                        headersWithUa[k] = v
                    }

                    // Ensure User-Agent is present
                    if (!headersWithUa.containsKeyIgnoreCase("User-Agent")) {
                        headersWithUa["User-Agent"] = getSourceUserAgent()
                    }

                    // Many sites (including JSON APIs and image CDNs) require Referer to avoid blocking.
                    if (!headersWithUa.containsKeyIgnoreCase("Referer")) {
                        originForReferer(request.url)?.let { headersWithUa["Referer"] = it }
                    }

                    // OkHttp header values must be ASCII; drop unsafe values to avoid IllegalArgumentException.
                    val sanitizedHeaders = headersWithUa.filterValues { isHeaderValueSafe(it) }.toMutableMap()
                    if (sanitizedHeaders.size != headersWithUa.size) {
                        val dropped = headersWithUa.keys - sanitizedHeaders.keys
                        Log.w(TAG, "Dropping unsafe headers for ${request.url}: $dropped")
                    }
                    
                    // Use WebView for sources that require JavaScript execution
                    if (request.useWebView && request.method == "GET") {
                        Log.d(TAG, "Final headers for ${request.url}: $sanitizedHeaders")
                        Log.d(TAG, "[WebView] Loading URL: ${request.url}")
                        val delayMs = if (request.webViewDelayTime > 0) request.webViewDelayTime else 2500L
                        val content = httpClient.getWithWebView(
                            url = request.url, 
                            headers = sanitizedHeaders, 
                            delayMs = delayMs,
                            webJs = request.webJs
                        )
                        
                        if (content.isBlank()) return@withLimit null
                        
                        // For WebView, the final URL is the same as the request URL
                        return@withLimit content to request.url
                    }

                    val response = if (request.method == "POST") {
                        val body = request.body ?: ""
                        // legado-with-MD3 兼容：body 为 JSON 时应使用 application/json，否则部分 API 会返回 502/4xx
                        // 允许通过显式 Content-Type 覆盖自动推断
                        val explicitContentType = sanitizedHeaders.entries
                            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                            ?.value
                        val isJsonBody = body.trimStart().startsWith("{") || body.trimStart().startsWith("[")
                        val inferredContentType = when {
                            !explicitContentType.isNullOrBlank() -> explicitContentType
                            isJsonBody -> "application/json; charset=${request.charset}"
                            else -> "application/x-www-form-urlencoded; charset=${request.charset}"
                        }
                        val inferredMediaType = inferredContentType.toMediaTypeOrNull()
                        val mediaType = inferredMediaType

                        // 让最终 headers 可观测且与 MD3 更一致（OkHttp 也会从 RequestBody 写入 Content-Type，但这里显式补齐）。
                        if (!sanitizedHeaders.containsKeyIgnoreCase("Content-Type")) {
                            sanitizedHeaders["Content-Type"] = inferredContentType
                        }
                        if (!sanitizedHeaders.containsKeyIgnoreCase("Accept")) {
                            sanitizedHeaders["Accept"] = if (isJsonBody) "application/json, text/plain, */*" else "*/*"
                        }

                        val bodyPreview = body.replace("\r", "").replace("\n", "\\n").take(180)
                        Log.d(
                            TAG,
                            "Final request for ${request.url}: method=POST contentType=$inferredContentType bodyPreview=$bodyPreview headers=$sanitizedHeaders"
                        )
                        val requestBody = body.toRequestBody(mediaType)
                        httpClient.post(request.url, requestBody, sanitizedHeaders, source = source)
                    } else {
                        Log.d(TAG, "Final request for ${request.url}: method=GET headers=$sanitizedHeaders")
                        httpClient.get(request.url, sanitizedHeaders, source = source)
                    }
                    
                    val content = getResponseBodyWithCharset(response)
                    Log.d(TAG, "Response for ${request.url}: code=${response.code} contentType=${response.header("Content-Type")}")
                    val responseClone = response.newBuilder().build()
                    response.close()
                    
                    if (content == null) return@withLimit null
                    
                    // Check for CloudFlare challenge
                    val cfStatus = LegadoCloudFlareResolver.checkResponseForProtection(responseClone, content)
                    if (cfStatus == LegadoCloudFlareResolver.PROTECTION_CAPTCHA) {
                        Log.w(TAG, "CF challenge detected, throwing exception for UI verification")
                        val headersForException = toHeaders(headersWithUa)
                        throw LegadoCloudFlareResolver.createException(request.url, source, headersForException)
                    } else if (cfStatus == LegadoCloudFlareResolver.PROTECTION_BLOCKED) {
                        Log.e(TAG, "CloudFlare BLOCKED this request!")
                        return@withLimit null
                    }
                    
                    val finalUrl = response.request.url.toString()
                    content to finalUrl
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e is org.skepsun.kototoro.core.exceptions.CloudFlareProtectedException) throw e
                
                if (e is org.skepsun.kototoro.parsers.exception.TooManyRequestExceptions) {
                    val waitTime = e.getRetryDelay().coerceAtLeast(1000L)
                    Log.w(TAG, "Rate limit hit (429), waiting ${waitTime}ms before retry $attempt/$maxRetries")
                    delay(waitTime)
                    // We don't throw here if we have more attempts, we just let the loop continue
                    if (attempt == maxRetries - 1) throw e
                } else {
                    Log.e(TAG, "Request failed: ${request.url} (attempt $attempt/$maxRetries)", e)
                    if (attempt == maxRetries - 1) throw e
                    // For other errors, add a small 500ms delay before retrying
                    delay(500)
                }
            }
        }
        
        return null
    }

    private fun originForReferer(url: String): String? {
        return runCatching {
            val parsed = java.net.URL(url)
            val host = parsed.host?.takeIf { it.isNotBlank() } ?: return null
            "${parsed.protocol}://$host/"
        }.getOrNull()
    }

    private fun effectiveBaseUrlForRequest(url: String): String {
        val configured = config.bookSourceUrl.trim()
        return when {
            configured.startsWith("http", ignoreCase = true) -> configured
            url.startsWith("http", ignoreCase = true) -> url
            else -> ""
        }
    }

    private fun isHeaderValueSafe(value: String): Boolean {
        // OkHttp 对 header value 有严格限制：不允许控制字符与非 ASCII。
        for (ch in value) {
            if (ch == '\t') continue
            val code = ch.code
            if (code < 0x20 || code >= 0x7f) return false
        }
        return true
    }


    override suspend fun getDetails(manga: Manga): Manga {
        val normalizedMangaUrl = AnalyzeUrl.normalizeUrl(manga.url)
        memoryCache?.getDetails(source, normalizedMangaUrl)?.let {
            val hasTocRule = !config.ruleToc?.chapterList.isNullOrBlank()
            val cachedChapters = it.chapters.orEmpty()
            if (!hasTocRule || cachedChapters.isNotEmpty()) {
                android.util.Log.d(TAG, "Memory cache HIT (getDetails) for book: ${manga.title}")
                return it
            }
            // 章节为空且存在目录规则：视为“脏缓存”，触发重新拉取（常见于网络错误/规则调整后）。
            android.util.Log.w(TAG, "Memory cache BYPASS (getDetails) for book: ${manga.title} (cached chapters empty)")
        }

        val request = AnalyzeUrl(
            manga.url, 
            baseUrl = effectiveBaseUrlForRequest(manga.url),
            sandbox = sandbox,
            useWebViewDefault = config.ruleBookInfo?.webView == true
        ).build()
        val result = executeRequest(request)
        
        if (result == null) {
            return manga
        }
        val (content, finalUrl) = result
        
        val infoResult = BookInfo.parse(manga, content, finalUrl, config, sandbox)
        
        // TOC parsing - often combined or needed for chapters
        // 对聚合源/POST JSON 目录：必须保留 options（body/method），否则无法翻页（表现为永远 20 章）。
        // `finalUrl` 是请求的 urlPart（AnalyzeUrl.build 后不含 ",{...}"），更适合作为解析基址而非“目录请求串”。
        val tocUrl = infoResult.tocUrl
            ?.takeIf { it.isNotBlank() }
            ?: manga.url.takeIf { it.contains(",{") || it.contains(", {") }
            ?: finalUrl
        
        val chapters = if (tocUrl == manga.url) {
            // 复用首包响应，避免重复请求目录第一页
            getChaptersHelper(infoResult.manga, tocUrl, initialContent = content, initialFinalUrl = finalUrl)
        } else {
            getChaptersHelper(infoResult.manga, tocUrl)
        }
        
        val finalManga = infoResult.manga.copy(chapters = chapters)
        
        android.util.Log.d(TAG, "Memory cache FILL (getDetails) for book: ${manga.title}")
        memoryCache?.putDetails(
            source, 
            normalizedMangaUrl, 
            SafeDeferred(processLifecycleScope.async(Dispatchers.Default) { Result.success(finalManga) })
        )
        
        return finalManga
    }


    override suspend fun getPages(chapter: MangaChapter, nextChapterUrl: String?): List<MangaPage> {
        val normalizedUrl = AnalyzeUrl.normalizeUrl(chapter.url)
        memoryCache?.getPages(source, normalizedUrl)?.let {
            android.util.Log.d(TAG, "Memory cache HIT (getPages) for chapter: ${chapter.title}")
            return it
        }
        return getPagesFlow(chapter, nextChapterUrl).toList().lastOrNull() ?: emptyList()
    }

    override fun getPagesFlow(chapter: MangaChapter, nextChapterUrl: String?): Flow<List<MangaPage>> = kotlinx.coroutines.flow.channelFlow {
        val normalizedUrl = AnalyzeUrl.normalizeUrl(chapter.url)
        memoryCache?.getPages(source, normalizedUrl)?.let {
            android.util.Log.d(TAG, "Memory cache HIT (getPagesFlow) for chapter: ${chapter.title}")
            send(it)
            return@channelFlow
        }
        
        val visited = mutableSetOf<String>()
        val pages = mutableListOf<MangaPage>()
        val queue: ArrayDeque<String> = ArrayDeque()
        queue.add(chapter.url)

        var pageCount = 0
        while (queue.isNotEmpty()) {
            val url = queue.removeFirst()
            if (!visited.add(url)) continue
            
            // 安全检查：如果这个 URL 是下一章的 URL，或者是已经处理过的下一章 URL，停止加载
            // 注意：Legado URL 可能带有 ",{'webView': true}" 这种后缀，需要剥离后再对比
            val normalizedUrl = url.substringBefore(",")
            val normalizedNextChapter = nextChapterUrl?.substringBefore(",")
            android.util.Log.d("LegadoRepository", "[BoundaryCheck] url=$normalizedUrl, nextChapterUrl=$normalizedNextChapter")
            if (normalizedNextChapter != null && normalizedUrl == normalizedNextChapter) {
                android.util.Log.w("LegadoRepository", "Reached next chapter URL: $url, stopping page load.")
                break
            }

            val contentRule = config.ruleContent
            val request = AnalyzeUrl(
                url, 
                baseUrl = effectiveBaseUrlForRequest(url),
                sandbox = sandbox,
                useWebViewDefault = isWebViewEnabled(contentRule?.webView),
                webJsDefault = contentRule?.webJs,
                webViewDelayDefault = contentRule?.webViewDelayTime ?: 0L
            ).build()
            val result = executeRequest(request) ?: run {
                continue
            }
            val (content, finalUrl) = result
            val parseResult = BookContent.parse(content, finalUrl, source, config, sandbox)
            val startIndex = pages.size
            parseResult.pages.forEachIndexed { index, page ->
                val pageId = (source.name.hashCode().toLong() shl 32) + startIndex + index
                pages.add(page.copy(id = pageId))
            }
            
            // Emit current progress
            send(pages.toList())
            pageCount++

            // 安全限制：单章节页数超过 500 页（通常是规则误触了下一章）
            if (pages.size > 500) {
                android.util.Log.e("LegadoRepository", "Chapter has too many pages (>500), possible rule leakage. Stopping.")
                break
            }

            parseResult.nextPageUrls.forEach { next ->
                val normalizedNext = next.substringBefore(",")
                val normalizedNextChapter = nextChapterUrl?.substringBefore(",")
                android.util.Log.d("LegadoRepository", "[NextPageFilter] next=$normalizedNext, nextChapter=$normalizedNextChapter, match=${normalizedNext == normalizedNextChapter}")
                
                if (!visited.contains(next)) {
                    // 再次检查 next URL
                    if (normalizedNextChapter != null && normalizedNext == normalizedNextChapter) {
                        android.util.Log.d("LegadoRepository", "[NextPageFilter] SKIPPING next chapter URL: $next")
                        // Skip adding next chapter to queue
                    } else {
                        queue.add(next)
                    }
                }
            }
        }
        
        if (pages.isNotEmpty()) {
            android.util.Log.d(TAG, "Memory cache FILL for chapter: ${chapter.title}, pages: ${pages.size}")
            memoryCache?.putPages(
                source, 
                normalizedUrl, 
                SafeDeferred(processLifecycleScope.async(Dispatchers.Default) { Result.success(pages.toList()) })
            )
        }
    }

    override suspend fun getPageUrl(page: MangaPage): String {
        return page.url
    }

    override suspend fun getChapterContent(chapter: MangaChapter, nextChapterUrl: String?): NovelChapterContent? {
        val pages = getPages(chapter, nextChapterUrl)
        if (pages.isEmpty()) return null

        val htmlBuilder = StringBuilder()
        val images = mutableListOf<NovelChapterContent.NovelImage>()

        pages.forEach { page ->
            if (page.url.startsWith("data:", ignoreCase = true)) {
                decodeDataUrl(page.url)?.let { decoded ->
                    htmlBuilder.append(decoded.html)
                    images.addAll(decoded.images)
                }
            } else {
                // Legado novel chapters might return image URLs for illustrations
                images.add(NovelChapterContent.NovelImage(page.url, page.headers ?: emptyMap()))
                // Note: The HTML usually already contains <img> tags pointing to these URLs
            }
        }

        return NovelChapterContent(
            html = htmlBuilder.toString(),
            images = images
        )
    }

    private fun decodeDataUrl(url: String): NovelChapterContent? {
        val data = url.removePrefix("data:")
        val commaIndex = data.indexOf(',')
        if (commaIndex <= 0) return null
        val meta = data.substring(0, commaIndex)
        val contentPart = data.substring(commaIndex + 1)
        val isBase64 = meta.contains(";base64", ignoreCase = true)

        return try {
            val html = if (isBase64) {
                String(Base64.getDecoder().decode(contentPart), Charsets.UTF_8)
            } else {
                URLDecoder.decode(contentPart, "UTF-8")
            }
            NovelChapterContent(html = html, images = emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode data URL", e)
            null
        }
    }

    override suspend fun getFilterOptions(): MangaListFilterOptions {
        val kinds = parseExploreKinds()
        
        // Convert ExploreKind to MangaTag for the filter system
        val categoryTags = kinds.mapNotNull { kind ->
            if (kind.url.isNullOrBlank()) null
            else MangaTag(kind.title, "category:${kind.url}", source)
        }.toSet()
        
        if (categoryTags.isEmpty()) {
            return MangaListFilterOptions()
        }
        
        return MangaListFilterOptions(
            availableTags = categoryTags,
            tagGroups = listOf(MangaTagGroup("分类", categoryTags))
        )
    }

    override fun getRequestHeaders(): Map<String, String> {
        val headers = getConfigHeaders().toMutableMap()

        // Ensure User-Agent is included in headers for Browser/WebView consistency
        if (!headers.containsKey("User-Agent") && !headers.containsKey("user-agent")) {
            headers["User-Agent"] = getSourceUserAgent()
        }

        // 图片站常见防盗链：没有 Referer 会直接 403
        val hasReferer = headers.keys.any { it.equals("Referer", ignoreCase = true) }
        if (!hasReferer) {
            val fallback = config.bookSourceUrl.takeIf { it.startsWith("http", ignoreCase = true) }
            originForReferer(fallback ?: "")?.let { headers["Referer"] = it }
        }

        return headers.filterValues { isHeaderValueSafe(it) }
    }

    override suspend fun getRelated(seed: Manga): List<Manga> {
        return emptyList()
    }

    private suspend fun getChaptersHelper(
        manga: Manga,
        tocUrl: String,
        initialContent: String? = null,
        initialFinalUrl: String? = null
    ): List<MangaChapter> {
        // 设置当前书籍上下文，供 JS 侧 book.getVariable("custom") 等读取。
        sandbox.setBook(
            LegadoSandbox.BookContext(
                name = manga.title,
                url = manga.url,
                tocUrl = tocUrl
            )
        )

        // legado-with-MD3 聚合源常用：通过 source/book 变量控制目录翻页上限（cmpVariable）。
        // 返回值语义：0 表示不限制（含 -1 的约定），>0 表示最多加载多少“目录页”。
        val tocPageLimit = computeTocPageLimitFromSourceComment() // nullable

        val visited = mutableSetOf<String>()
        val chapters = mutableListOf<MangaChapter>()
        var shouldReverse = false
        val queue: ArrayDeque<String> = ArrayDeque()
        queue.add(tocUrl)
        var usedInitial = false
        
        var pageCount = 0

        while (queue.isNotEmpty()) {
            val url = queue.removeFirst()
            
            if (!visited.add(url)) {
                continue
            }
            
            pageCount++

            val (content, finalUrl) = if (!usedInitial && url == tocUrl && initialContent != null && initialFinalUrl != null) {
                usedInitial = true
                initialContent to initialFinalUrl
            } else {
                val request = AnalyzeUrl(
                    url,
                    baseUrl = effectiveBaseUrlForRequest(url),
                    sandbox = sandbox,
                    useWebViewDefault = config.ruleToc?.webView == true
                ).build()
                val result = executeRequest(request) ?: continue
                result
            }

            val parseResult = BookChapterList.parse(content, finalUrl, source, config, sandbox)
            
            shouldReverse = shouldReverse || parseResult.shouldReverse
            chapters.addAll(parseResult.chapters)
            
            android.util.Log.d(TAG, "TOC page $pageCount: loaded ${parseResult.chapters.size} chapters, total: ${chapters.size}, nextPages: ${parseResult.nextPageUrls.size}")

            parseResult.nextPageUrls.forEach { next ->
                if (!visited.contains(next)) {
                    queue.add(next)
                }
            }

            // 若源未提供 nextTocUrl，但 tocUrl 是“带 page 的 POST JSON 请求”，则尝试自动翻页：
            // - 依赖书籍变量/源变量（cmpVariable）控制最多翻多少页；0 表示直到无数据为止
            // - 仅对 options URL 生效，避免对普通 HTML URL 造成意外请求
            if (parseResult.nextPageUrls.isEmpty()) {
                enqueueAutoNextTocPageIfPossible(
                    rawUrl = url,
                    tocPageLimit = tocPageLimit,
                    visited = visited,
                    queue = queue,
                    lastParseHadItems = parseResult.chapters.isNotEmpty()
                )
            }
        }
        
        val deduped = linkedMapOf<String, MangaChapter>()
        chapters.forEach { chapter ->
            deduped.putIfAbsent(chapter.url, chapter)
        }
        val ordered = deduped.values.toMutableList()
        
        // legado 规则约定：`chapterList` 以 '-' 开头表示“反转章节列表”
        if (shouldReverse) ordered.reverse()
        
        // Re-assign sequential indices and numbers to ensure they are 1, 2, 3... in ascending order.
        return ordered.mapIndexed { index, chapter ->
            // Use stable ID based on normalized URL hash instead of list index
            val normalizedUrl = AnalyzeUrl.normalizeUrl(chapter.url)
            val stableId = (source.name.hashCode().toLong() shl 32) + (normalizedUrl.hashCode().toLong() and 0xFFFFFFFFL)
            chapter.copy(
                id = stableId,
                number = index.toFloat() + 1f
            )
        }
    }

    private fun computeTocPageLimitFromSourceComment(): Int? {
        val comment = config.bookSourceComment?.trim().orEmpty()
        if (comment.isBlank()) return null
        // 仅当源脚本定义了 cmpVariable 时才尝试，避免对其它源产生副作用。
        if (!comment.contains("cmpVariable", ignoreCase = true)) return null

        // 兼容 legado-with-MD3 的写法：eval(String(source.bookSourceComment)); cmpVariable();
        val script = """
            try {
              eval(String(source.bookSourceComment));
              var n = (typeof cmpVariable === 'function') ? cmpVariable() : null;
              n;
            } catch (e) {
              null;
            }
        """.trimIndent()

        val result = sandbox.eval(script) ?: return null
        val value = when (result) {
            is Number -> result.toInt()
            is String -> result.trim().toIntOrNull()
            else -> result.toString().trim().toIntOrNull()
        } ?: return null
        return value.coerceAtLeast(0)
    }

    private fun enqueueAutoNextTocPageIfPossible(
        rawUrl: String,
        tocPageLimit: Int?,
        visited: Set<String>,
        queue: ArrayDeque<String>,
        lastParseHadItems: Boolean
    ) {
        // 无数据则不再翻页（避免无限请求空页）。
        if (!lastParseHadItems) return

        val parsed = parsePagedPostJsonOptions(rawUrl) ?: return
        val currentPage = parsed.page ?: return

        // 若 cmpVariable 未定义：不自动翻页（保持当前行为）。
        val limit = tocPageLimit ?: return

        // limit == 0 表示不限制（含 -1 的约定）。
        if (limit > 0 && currentPage >= limit) return

        val nextPage = currentPage + 1

        // 额外安全阈值：即使“不限制”，也避免规则/站点异常导致无限增长。
        if (nextPage > MAX_AUTO_TOC_PAGES) return

        val nextUrl = buildPagedPostJsonOptionsUrl(parsed, nextPage) ?: return
        if (!visited.contains(nextUrl)) {
            android.util.Log.d(TAG, "[TOC-AutoPage] page=$currentPage -> $nextPage, limit=$limit, urlPart=${parsed.urlPart}")
            queue.add(nextUrl)
        }
    }

    private data class PagedPostJsonOptions(
        val urlPart: String,
        val optionsJson: JSONObject,
        val bodyJson: JSONObject,
        val page: Int?
    )

    private fun parsePagedPostJsonOptions(rawUrl: String): PagedPostJsonOptions? {
        val splitMatch = Regex("\\s*,\\s*(?=\\{)").find(rawUrl) ?: return null
        val urlPart = rawUrl.substring(0, splitMatch.range.first).trim()
        val optionsPart = rawUrl.substring(splitMatch.range.last + 1).trim()

        val optionsJson = runCatching {
            val normalized = if (optionsPart.contains("'")) optionsPart.replace("'", "\"") else optionsPart
            JSONObject(normalized)
        }.getOrNull() ?: return null

        val method = optionsJson.optString("method", "GET").uppercase()
        if (method == "GET") return null

        val bodyAny = optionsJson.opt("body") ?: return null
        val bodyText = when (bodyAny) {
            is String -> bodyAny
            is JSONObject -> bodyAny.toString()
            else -> bodyAny.toString()
        }.trim()
        if (bodyText.isBlank() || !(bodyText.startsWith("{") && bodyText.endsWith("}"))) return null

        val bodyJson = runCatching { JSONObject(bodyText) }.getOrNull() ?: return null
        val page = when (val p = bodyJson.opt("page")) {
            is Number -> p.toInt()
            is String -> p.trim().toIntOrNull()
            else -> null
        }

        return PagedPostJsonOptions(urlPart = urlPart, optionsJson = optionsJson, bodyJson = bodyJson, page = page)
    }

    private fun buildPagedPostJsonOptionsUrl(parsed: PagedPostJsonOptions, page: Int): String? {
        val newBody = JSONObject(parsed.bodyJson.toString())
        newBody.put("page", page)
        val newOptions = JSONObject(parsed.optionsJson.toString())
        // 为兼容 legado 源常见写法，保持 body 为 JSON 字符串（而非嵌套对象）。
        newOptions.put("body", newBody.toString())
        return parsed.urlPart + "," + newOptions.toString()
    }


    /**
     * Decode response body with proper charset detection.
     * Uses EncodingDetect with ICU4J for accurate charset identification.
     */
    private fun getResponseBodyWithCharset(response: Response): String? {
        val body = response.body ?: return null
        val bytes = body.bytes()
        if (bytes.isEmpty()) return ""
        
        val encoding = response.header("Content-Encoding")
        var decodedBytes = bytes
        if (encoding != null) {
            try {
                if (encoding.equalsIgnoreCase("gzip")) {
                    decodedBytes = java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(bytes)).readBytes()
                } else if (encoding.equalsIgnoreCase("deflate")) {
                    decodedBytes = java.util.zip.InflaterInputStream(java.io.ByteArrayInputStream(bytes)).readBytes()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Manual decompression failed: ${e.message}")
            }
        }

        val contentTypeHeader = response.header("Content-Type") ?: ""
        val isJson = contentTypeHeader.contains("json", ignoreCase = true)
        
        var charset: java.nio.charset.Charset? = null
        
        // Try to get from OkHttp's own parsing first
        body.contentType()?.charset()?.let {
            charset = it
        }

        if (charset == null) {
            val parts = contentTypeHeader.split(";")
            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.startsWith("charset", ignoreCase = true) && trimmed.contains("=")) {
                    val charsetName = trimmed.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                    try {
                        charset = java.nio.charset.Charset.forName(charsetName)
                    } catch (e: Exception) {}
                }
            }
        }

        if (charset == null) {
            if (isJson) {
                charset = Charsets.UTF_8
            } else {
                val detected = org.skepsun.kototoro.core.util.EncodingDetect.getHtmlEncode(decodedBytes)
                charset = try {
                    java.nio.charset.Charset.forName(detected)
                } catch (e: Exception) {
                    Charsets.UTF_8
                }
            }
        }
        
        Log.d(TAG, "Decoded response using charset: ${charset?.name()} (Header: $contentTypeHeader)")
        return String(decodedBytes, charset!!)
    }

    private fun String.equalsIgnoreCase(other: String): Boolean = this.equals(other, ignoreCase = true)

    private fun parseConfigHeaders(headerStr: String?): Map<String, String> {
        if (headerStr.isNullOrBlank()) return emptyMap()
        
        var jsonStr = headerStr.trim()
        
        // Check if header is JavaScript code that needs execution
        if (jsonStr.startsWith("<js>", ignoreCase = true) || 
            jsonStr.startsWith("@js:", ignoreCase = true)) {
            
            // Extract and execute JavaScript
            val jsCode = when {
                jsonStr.startsWith("<js>", ignoreCase = true) -> {
                    jsonStr.removePrefix("<js>").removeSuffix("</js>").trim()
                }
                jsonStr.startsWith("@js:", ignoreCase = true) -> {
                    jsonStr.removePrefix("@js:").trim()
                }
                else -> jsonStr
            }
            
            try {
                val result = sandbox.eval(jsCode.trim())?.toString()
                if (!result.isNullOrBlank()) {
                    jsonStr = result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute header JS: ${e.message}", e)
                return emptyMap()
            }
        }
        
        return try {
            val headers = mutableMapOf<String, String>()
            val normalized = jsonStr.replace("'", "\"")
            val jsonObj = JSONObject(normalized)
            jsonObj.keys().forEach { key ->
                val value = jsonObj.opt(key)
                if (value != null && value != JSONObject.NULL) {
                    val content = value.toString().removeSurrounding("\"")
                    if (content.isNotBlank()) {
                        headers[key] = content
                    }
                }
            }
            headers
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse headers JSON: $jsonStr", e)
            emptyMap()
        }
    }

    private fun toHeaders(headersMap: Map<String, String>): okhttp3.Headers {
        val builder = okhttp3.Headers.Builder()
        headersMap.forEach { (k, v) ->
            builder.add(k, v)
        }
        return builder.build()
    }

    private fun isWebViewEnabled(ruleValue: String?): Boolean {
        if (ruleValue == null) return false
        if (ruleValue == "true" || ruleValue == "1") return true
        if (ruleValue == "false" || ruleValue == "0") return false
        
        // Evaluate as JS if it looks like a script
        if (ruleValue.startsWith("<js>") || ruleValue.startsWith("@js:")) {
            return try {
                val script = if (ruleValue.startsWith("<js>")) {
                    ruleValue.removePrefix("<js>").removeSuffix("</js>")
                } else {
                    ruleValue.removePrefix("@js:")
                }
                sandbox.eval(script)?.toString() == "true"
            } catch (e: Exception) {
                false
            }
        }
        
        return false
    }
}
