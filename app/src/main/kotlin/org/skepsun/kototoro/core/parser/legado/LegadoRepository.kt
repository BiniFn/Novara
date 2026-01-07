package org.skepsun.kototoro.core.parser.legado

import android.util.Log
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

    private val configHeaders: Map<String, String> by lazy { parseConfigHeaders(config.header) }

    private val sourceUserAgent: String by lazy {
        configHeaders["User-Agent"] ?: configHeaders["user-agent"] ?: run {
            if (source.name.startsWith("JSON_LEGADO", ignoreCase = true)) {
                // Use desktop UA for Legado to avoid mobile redirects and stay consistent
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            } else {
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.203 Mobile Safari/537.36"
            }
        }
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
        Log.d(TAG, "getList isSearch=$isSearch page=$page url=${request.url}")
        val result = executeRequest(request)
        
        if (result == null) return emptyList()
        val (content, finalUrl) = result
        
        val list = BookList.parse(content, finalUrl, source, config, isSearch, sandbox)
        Log.d(TAG, "getList parsed=${list.size} items for url=$finalUrl")
        if (list.isEmpty()) {
            val preview = content.take(400).replace("\n", " ")
            Log.d(TAG, "getList empty for url=$finalUrl ruleUrl=$ruleUrl preview=${preview}")
        }
        return list
    }

    /**
     * Centralized request executor with retry and protection logic
     */
    private suspend fun executeRequest(request: AnalyzeUrl.UrlResult): Pair<String, String>? {
        val maxRetries = request.retry.coerceAtLeast(1)
        var lastContent: String? = null
        
        repeat(maxRetries) { attempt ->
            try {
                // Sync CloudFlare cookies
                LegadoCloudFlareResolver.syncCloudFlareCookies(config.bookSourceUrl)
                
                val headersWithUa = request.headers.toMutableMap()
                if (!headersWithUa.containsKey("User-Agent") && !headersWithUa.containsKey("user-agent")) {
                    headersWithUa["User-Agent"] = sourceUserAgent
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
                
                if (content == null) return@repeat
                
                // Check for CloudFlare challenge
                val cfStatus = LegadoCloudFlareResolver.checkResponseForProtection(responseClone, content)
                if (cfStatus == LegadoCloudFlareResolver.PROTECTION_CAPTCHA) {
                    Log.w(TAG, "CF challenge detected, throwing exception for UI verification")
                    val headersForException = toHeaders(headersWithUa)
                    throw LegadoCloudFlareResolver.createException(request.url, source, headersForException)
                } else if (cfStatus == LegadoCloudFlareResolver.PROTECTION_BLOCKED) {
                    Log.e(TAG, "CloudFlare BLOCKED this request!")
                    return null
                }
                
                val finalUrl = response.request.url.toString()
                
                return content to finalUrl
            } catch (e: Exception) {
                if (e is org.skepsun.kototoro.core.exceptions.CloudFlareProtectedException) throw e
                Log.e(TAG, "Request failed: ${request.url}", e)
                if (attempt == maxRetries - 1) throw e
            }
        }
        
        return null
    }


    override suspend fun getDetails(manga: Manga): Manga {
        Log.d(TAG, "===== getDetails START =====")
        Log.d(TAG, "manga.url=${manga.url}")
        Log.d(TAG, "manga.title=${manga.title}")
        
        val request = AnalyzeUrl(manga.url, baseUrl = config.bookSourceUrl, sandbox = sandbox).build()
        val result = executeRequest(request)
        
        if (result == null) {
            Log.e(TAG, "Response body is null for details!")
            return manga
        }
        val (content, finalUrl) = result
        
        Log.d(TAG, "Content length: ${content.length}, finalUrl: $finalUrl")
        
        val infoResult = BookInfo.parse(manga, content, finalUrl, config, sandbox)
        Log.d(TAG, "Parsed info: tocUrl=${infoResult.tocUrl}")
        
        // TOC parsing - often combined or needed for chapters
        val tocUrl = infoResult.tocUrl ?: finalUrl
        Log.d(TAG, "Using TOC URL: $tocUrl")
        
        val chapters = if (tocUrl == finalUrl) {
            // Already on TOC page, parse chapters from current content
            BookChapterList.parse(content, finalUrl, source, config, sandbox).chapters
        } else {
            getChaptersHelper(infoResult.manga, tocUrl)
        }
        Log.d(TAG, "Got ${chapters.size} chapters")
        Log.d(TAG, "===== getDetails END =====")
        
        return infoResult.manga.copy(chapters = chapters)
    }


    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
        val visited = mutableSetOf<String>()
        val pages = mutableListOf<MangaPage>()
        val queue: ArrayDeque<String> = ArrayDeque()
        queue.add(chapter.url)

        while (queue.isNotEmpty()) {
            val url = queue.removeFirst()
            if (!visited.add(url)) continue

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

            parseResult.nextPageUrls.forEach { next ->
                if (!visited.contains(next)) {
                    queue.add(next)
                }
            }
        }

        return pages
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
        val headers = configHeaders.toMutableMap()

        // Ensure User-Agent is included in headers for Browser/WebView consistency
        if (!headers.containsKey("User-Agent") && !headers.containsKey("user-agent")) {
            headers["User-Agent"] = sourceUserAgent
        }
        
        return headers
    }

    override suspend fun getRelated(seed: Manga): List<Manga> {
        return emptyList()
    }

    private suspend fun getChaptersHelper(manga: Manga, tocUrl: String): List<MangaChapter> {
        Log.d(TAG, "===== getChaptersHelper START =====")
        Log.d(TAG, "tocUrl=$tocUrl")
        
        val visited = mutableSetOf<String>()
        val chapters = mutableListOf<MangaChapter>()
        var reverseFlag = false
        val queue: ArrayDeque<String> = ArrayDeque()
        queue.add(tocUrl)

        var iteration = 0
        while (queue.isNotEmpty()) {
            iteration++
            val url = queue.removeFirst()
            Log.d(TAG, "[TOC iteration $iteration] Processing: $url")
            
            if (!visited.add(url)) {
                Log.d(TAG, "[TOC iteration $iteration] Already visited, skipping")
                continue
            }

            val request = AnalyzeUrl(url, baseUrl = config.bookSourceUrl, sandbox = sandbox).build()
            val result = executeRequest(request)
            if (result == null) {
                Log.e(TAG, "[TOC iteration $iteration] Response body is null!")
                continue
            }
            val (content, finalUrl) = result

            val parseResult = BookChapterList.parse(content, finalUrl, source, config, sandbox)
            Log.d(TAG, "[TOC iteration $iteration] Parsed ${parseResult.chapters.size} chapters, reverse=${parseResult.reverse}")
            Log.d(TAG, "[TOC iteration $iteration] Next page URLs: ${parseResult.nextPageUrls}")
            
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
        
        Log.d(TAG, "Total chapters collected: ${chapters.size}")

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
        val sequentialChapters = ordered.mapIndexed { index, chapter ->
            // Use stable ID based on URL hash instead of list index
            val stableId = (source.name.hashCode().toLong() shl 32) + (chapter.url.hashCode().toLong() and 0xFFFFFFFFL)
            chapter.copy(
                id = stableId,
                number = index.toFloat() + 1f
            )
        }
        
        Log.d(TAG, "Final chapters: ${sequentialChapters.size}, reversed by rule=$reverseFlag")
        Log.d(TAG, "===== getChaptersHelper END =====")
        
        return sequentialChapters
    }

    /**
     * Decode response body with proper charset detection.
     * Uses EncodingDetect with ICU4J for accurate charset identification.
     */
    private fun getResponseBodyWithCharset(response: Response): String? {
        val body = response.body ?: run {
            Log.e(TAG, "Response body is null")
            return null
        }
        
        val bytes = body.bytes()
        Log.d(TAG, "Response body size: ${bytes.size} bytes")
        
        // Try to detect charset from Content-Type header first
        val contentType = body.contentType()
        val headerCharset = contentType?.charset()
        
        Log.d(TAG, "Content-Type: $contentType, headerCharset: $headerCharset")
        
        if (headerCharset != null) {
            Log.d(TAG, "Using charset from header: $headerCharset")
            return String(bytes, headerCharset)
        }
        
        // Use EncodingDetect (ICU4J) for HTML charset detection
        val charsetName = org.skepsun.kototoro.core.util.EncodingDetect.getHtmlEncode(bytes)
        Log.d(TAG, "Detected charset: $charsetName")
        
        return try {
            String(bytes, java.nio.charset.Charset.forName(charsetName))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode with $charsetName, falling back to UTF-8", e)
            String(bytes, kotlin.text.Charsets.UTF_8)
        }
    }

    private fun parseConfigHeaders(headerStr: String?): Map<String, String> {
        if (headerStr.isNullOrBlank()) return emptyMap()
        return try {
            val headers = mutableMapOf<String, String>()
            val jsonObj = JSONObject(headerStr.replace("'", "\""))
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
        } catch (_: Exception) {
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
