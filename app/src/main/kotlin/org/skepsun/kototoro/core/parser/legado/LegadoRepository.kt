package org.skepsun.kototoro.core.parser.legado

import android.util.Log
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
     * Get User-Agent, preferring source config over default
     */
    private fun getSourceUserAgent(): String {
        val headers = getConfigHeaders()
        return headers["User-Agent"] ?: headers["user-agent"] ?: defaultUserAgent
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
        
        val urlAnalyzer = AnalyzeUrl(
            ruleUrl = ruleUrl,
            key = query,
            page = page,
            baseUrl = config.bookSourceUrl,
            ruleData = sandbox.getRuleData(),
            sandbox = sandbox
        )
        
        val request = urlAnalyzer.build()
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
        
        repeat(maxRetries) { attempt ->
            try {
                // Extract priority from context
                val priority = kotlin.coroutines.coroutineContext[RequestPriority]?.priority ?: RequestPriority.FOREGROUND

                // Apply concurrency limit
                return rateLimiter.withLimit(priority) {
                    // Sync CloudFlare cookies
                    LegadoCloudFlareResolver.syncCloudFlareCookies(config.bookSourceUrl)
                    
                    val headersWithUa = request.headers.toMutableMap()
                    if (!headersWithUa.containsKey("User-Agent") && !headersWithUa.containsKey("user-agent")) {
                        headersWithUa["User-Agent"] = getSourceUserAgent()
                    }

                    // Use WebView for sources that require JavaScript execution
                    if (request.useWebView && request.method == "GET") {
                        Log.d(TAG, "[WebView] Loading URL: ${request.url}")
                        val delayMs = if (request.webViewDelayTime > 0) request.webViewDelayTime else 2500L
                        val content = httpClient.getWithWebView(
                            url = request.url, 
                            headers = headersWithUa, 
                            delayMs = delayMs,
                            webJs = request.webJs
                        )
                        
                        if (content.isBlank()) return@withLimit null
                        
                        // For WebView, the final URL is the same as the request URL
                        return@withLimit content to request.url
                    }

                    val response = if (request.method == "POST") {
                        val body = request.body ?: ""
                        val mediaType = "application/x-www-form-urlencoded; charset=${request.charset}".toMediaTypeOrNull()
                        val requestBody = body.toRequestBody(mediaType)
                        httpClient.post(request.url, requestBody, headersWithUa, source = source)
                    } else {
                        httpClient.get(request.url, headersWithUa, source = source)
                    }
                    
                    val content = getResponseBodyWithCharset(response)
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


    override suspend fun getDetails(manga: Manga): Manga {
        val normalizedMangaUrl = AnalyzeUrl.normalizeUrl(manga.url)
        memoryCache?.getDetails(source, normalizedMangaUrl)?.let {
            android.util.Log.d(TAG, "Memory cache HIT (getDetails) for book: ${manga.title}")
            return it
        }

        val request = AnalyzeUrl(manga.url, baseUrl = config.bookSourceUrl, sandbox = sandbox).build()
        val result = executeRequest(request)
        
        if (result == null) {
            return manga
        }
        val (content, finalUrl) = result
        
        val infoResult = BookInfo.parse(manga, content, finalUrl, config, sandbox)
        
        // TOC parsing - often combined or needed for chapters
        val tocUrl = infoResult.tocUrl ?: finalUrl
        
        val chapters = if (tocUrl == finalUrl) {
            // Already on TOC page, parse chapters from current content
            BookChapterList.parse(content, finalUrl, source, config, sandbox).chapters
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


    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val normalizedUrl = AnalyzeUrl.normalizeUrl(chapter.url)
        memoryCache?.getPages(source, normalizedUrl)?.let {
            android.util.Log.d(TAG, "Memory cache HIT (getPages) for chapter: ${chapter.title}")
            return it
        }
        return getPagesFlow(chapter, null).toList().lastOrNull() ?: emptyList()
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

            val request = AnalyzeUrl(url, baseUrl = config.bookSourceUrl, sandbox = sandbox).build()
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

            // 安全限制：单章节页数超过 100 页（通常是规则误触了下一章）
            if (pages.size > 100) {
                android.util.Log.e("LegadoRepository", "Chapter has too many pages (>100), possible rule leakage. Stopping.")
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
        
        return headers
    }

    override suspend fun getRelated(seed: Manga): List<Manga> {
        return emptyList()
    }

    private suspend fun getChaptersHelper(manga: Manga, tocUrl: String): List<MangaChapter> {
        val visited = mutableSetOf<String>()
        val chapters = mutableListOf<MangaChapter>()
        var reverseFlag = false
        val queue: ArrayDeque<String> = ArrayDeque()
        queue.add(tocUrl)

        while (queue.isNotEmpty()) {
            val url = queue.removeFirst()
            
            if (!visited.add(url)) {
                continue
            }

            val request = AnalyzeUrl(url, baseUrl = config.bookSourceUrl, sandbox = sandbox).build()
            val result = executeRequest(request)
            if (result == null) {
                continue
            }
            val (content, finalUrl) = result

            val parseResult = BookChapterList.parse(content, finalUrl, source, config, sandbox)
            
            if (parseResult.reverse) {
                reverseFlag = true
            }
            chapters.addAll(parseResult.chapters)

            parseResult.nextPageUrls.forEach { next ->
                if (!visited.contains(next)) {
                    queue.add(next)
                }
            }
        }
        
        val deduped = linkedMapOf<String, MangaChapter>()
        chapters.forEach { chapter ->
            deduped.putIfAbsent(chapter.url, chapter)
        }
        val ordered = deduped.values.toMutableList()
        
        // Legado rule: if starts with '-', it means the source provides chapters in descending order.
        // We reverse it to get ascending order (first -> last).
        if (reverseFlag) {
            ordered.reverse()
        }
        
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

    /**
     * Decode response body with proper charset detection.
     * Uses EncodingDetect with ICU4J for accurate charset identification.
     */
    private fun getResponseBodyWithCharset(response: Response): String? {
        val body = response.body ?: return null
        val bytes = body.bytes()
        
        // Try to detect charset from Content-Type header first
        val contentType = body.contentType()
        val headerCharset = contentType?.charset()
        
        if (headerCharset != null) {
            return String(bytes, headerCharset)
        }
        
        // Use EncodingDetect (ICU4J) for HTML charset detection
        val charsetName = org.skepsun.kototoro.core.util.EncodingDetect.getHtmlEncode(bytes)
        
        return try {
            String(bytes, java.nio.charset.Charset.forName(charsetName))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode with $charsetName, falling back to UTF-8", e)
            String(bytes, kotlin.text.Charsets.UTF_8)
        }
    }

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
}
