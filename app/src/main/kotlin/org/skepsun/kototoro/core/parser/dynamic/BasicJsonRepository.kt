package org.skepsun.kototoro.core.parser.dynamic

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.core.jsonsource.JsonMangaSource
import org.skepsun.kototoro.core.model.jsonsource.SearchRule
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.core.parser.rule.EnhancedRuleEngine
import org.skepsun.kototoro.core.javascript.JavaScriptContext
import org.skepsun.kototoro.core.javascript.BookInfo
import org.skepsun.kototoro.core.javascript.ChapterInfo
import org.skepsun.kototoro.core.exceptions.CloudFlareProtectedException
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaListFilter
import org.skepsun.kototoro.parsers.model.MangaListFilterCapabilities
import org.skepsun.kototoro.parsers.model.MangaListFilterOptions
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.model.MangaState
import org.skepsun.kototoro.parsers.model.MangaTag
import org.skepsun.kototoro.parsers.model.SortOrder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.EnumSet

/**
 * Basic implementation of MangaRepository for JSON sources
 * 
 * This implementation uses the RuleEngine to parse content based on JSON source
 * configurations (Legado format). It supports:
 * - List/search parsing using ruleSearch
 * - Details parsing using ruleBookInfo and ruleToc
 * - Content parsing using ruleContent
 * 
 * Uses LegadoHttpClient for advanced features like User-Agent rotation and Cloudflare handling.
 * 
 * Requirements: 4.2, 4.3, 4.4
 */
class BasicJsonRepository(
	override val source: MangaSource,
	private val legadoHttpClient: LegadoHttpClient,
	private val ruleEngine: EnhancedRuleEngine,
	private val browserLauncher: org.skepsun.kototoro.core.javascript.BrowserLauncher? = null,
) : MangaRepository {
	
	companion object {
		private const val TAG = "BasicJsonRepository"
	}
	
	private val jsonSource: JsonMangaSource = source as JsonMangaSource
	private val config: LegadoBookSource by lazy {
		val json = Json {
			ignoreUnknownKeys = true
			isLenient = true
		}
		json.decodeFromString<LegadoBookSource>(jsonSource.entity.config)
	}
	
	/**
	 * Parse custom headers from the source configuration
	 * Headers are stored as a JSON-like string: {'User-Agent':'...','Referer':'...'}
	 */
	private val customHeaders: Map<String, String> by lazy {
		val headerStr = config.header
		if (headerStr.isNullOrBlank()) {
			return@lazy emptyMap()
		}
		
		try {
			// Parse the header string which is in format: {'key':'value','key2':'value2'}
			// Convert single quotes to double quotes for JSON parsing
			val jsonStr = headerStr.replace("'", "\"")
			val json = Json { ignoreUnknownKeys = true }
			json.decodeFromString<Map<String, String>>(jsonStr)
		} catch (e: Exception) {
			Log.w(TAG, "Failed to parse custom headers: ${e.message}")
			emptyMap()
		}
	}

	/**
	 * Build default request headers merged with custom headers from the source config
	 */
	private fun buildRequestHeaders(): Map<String, String> {
		val headers = mutableMapOf(
			"Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
			"Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8"
		)
		headers.putAll(customHeaders) // Custom headers override defaults
		return headers
	}
	
	/**
	 * Execute GET with Cloudflare fallback (browser) if interceptor throws
	 */
	private suspend fun getWithCloudflareRetry(request: RequestConfig): okhttp3.Response? {
		val headers = request.headers.ifEmpty { buildRequestHeaders() }
		val url = request.url
		return try {
			if (request.method.equals("POST", ignoreCase = true)) {
				legadoHttpClient.post(url, request.body, headers, source = source)
			} else {
				legadoHttpClient.get(url, headers, source = source)
			}
		} catch (e: CloudFlareProtectedException) {
			Log.w(TAG, "Cloudflare protection detected for ${source.name}, launching browser")
			if (browserLauncher != null) {
				return try {
					val result = browserLauncher.launchAndWait(url, "Cloudflare验证", source)
					if (result.isNotBlank()) {
						if (request.method.equals("POST", ignoreCase = true)) {
							legadoHttpClient.post(url, request.body, headers, source = source)
						} else {
							legadoHttpClient.get(url, headers, source = source)
						}
					} else null
				} catch (ex: Exception) {
					Log.e(TAG, "Browser launch failed during Cloudflare retry", ex)
					null
				}
			} else {
				Log.e(TAG, "No browserLauncher available to handle Cloudflare")
				null
			}
		}
	}
	
	override val sortOrders: Set<SortOrder>
		get() = EnumSet.allOf(SortOrder::class.java)
	
	override var defaultSortOrder: SortOrder
		get() = SortOrder.NEWEST
		set(value) = Unit
	
	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = !config.searchUrl.isNullOrBlank(),
			isSearchWithFiltersSupported = false,
			isMultipleTagsSupported = false,
			isTagsExclusionSupported = false,
			isAuthorSearchSupported = false,
		)
	
	/**
	 * Get list of manga from the source
	 * 
	 * Task 28.1: Implements list page parsing
	 * - Constructs search/list URL
	 * - Sends HTTP request to get page content
	 * - Uses ruleSearch to parse the list
	 * - Extracts manga information (title, author, cover, URL)
	 * 
	 * Requirements: 4.2
	 */
	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		Log.d(TAG, "getList called for ${source.name}, offset=$offset, query=${filter?.query}")
		
		try {
			// Determine which rule to use: ruleExplore for browsing, ruleSearch for searching
			val isSearching = !filter?.query.isNullOrBlank()
			
			// Helper function to check if a rule is valid (has required fields)
			fun isValidRule(rule: SearchRule?): Boolean {
				return rule != null && 
					!rule.bookList.isNullOrBlank() && 
					!rule.name.isNullOrBlank() && 
					!rule.bookUrl.isNullOrBlank()
			}
			
			val rule = if (isSearching) {
				config.ruleSearch
			} else {
				// Use ruleExplore for browsing, fallback to ruleSearch if ruleExplore is invalid
				if (isValidRule(config.ruleExplore)) {
					config.ruleExplore
				} else {
					Log.d(TAG, "ruleExplore is invalid or empty, falling back to ruleSearch")
					config.ruleSearch
				}
			}
			
			if (rule == null) {
				Log.w(TAG, "No ${if (isSearching) "ruleSearch" else "ruleExplore"} defined for ${source.name}")
				return emptyList()
			}
			
			// Validate required fields
			if (!isValidRule(rule)) {
				Log.w(TAG, "Invalid ${if (isSearching) "ruleSearch" else "ruleExplore"} for ${source.name}: missing required fields (bookList, name, or bookUrl)")
				return emptyList()
			}
			
			// Build the search/list request
			val request = buildListRequest(filter?.query, offset, isSearching)
			if (request.url.isBlank()) {
				Log.w(TAG, "No ${if (isSearching) "search" else "explore"} URL configured for ${source.name}")
				return emptyList()
			}
			
			Log.d(TAG, "Fetching list from: ${request.url} (using ${if (isSearching) "ruleSearch" else "ruleExplore"})")
			Log.d(TAG, "Using bookList rule: ${rule.bookList}")
			
			// Make HTTP request using LegadoHttpClient (handles User-Agent rotation and Cloudflare)
			// Merge custom headers from source config with default headers
			val response = getWithCloudflareRetry(request)
			if (response == null) {
				Log.e(TAG, "Failed to fetch list after Cloudflare handling")
				return emptyList()
			}
		
		// Read response body first (even for error codes like 403/503)
		// This allows us to detect Cloudflare verification pages
		val html = readBodyWithCharset(response)
		val httpCode = response.code
		response.close()
		
		Log.d(TAG, "Received HTTP ${httpCode} response: ${html.length} bytes")
		
		// Check for common issues and handle them (including Cloudflare on 403/503)
		val processedHtml = handleCommonIssues(html, request.url, rule, httpCode)
		if (processedHtml.isEmpty()) {
			// Only now treat it as an error if we couldn't handle it
			if (!response.isSuccessful) {
				Log.e(TAG, "HTTP request failed: ${httpCode} for URL: ${request.url} and couldn't handle the response")
			} else {
				Log.w(TAG, "HTML processing failed or returned empty content")
			}
			return emptyList()
		}
			
			// Parse HTML
			val document = Jsoup.parse(processedHtml, request.url)
			
			// Use rule engine to parse the list
			// At this point we know rule is valid (checked by isValidRule), so these fields are non-null
			val bookListRule = rule.bookList!!
			val nameRule = rule.name!!
			val bookUrlRule = rule.bookUrl!!
			
			val itemRules = mapOf(
				"name" to nameRule,
				"author" to (rule.author ?: ""),
				"coverUrl" to (rule.coverUrl ?: ""),
				"bookUrl" to bookUrlRule,
				"intro" to (rule.intro ?: "")
			)
			
			Log.d(TAG, "Rule object details:")
			Log.d(TAG, "  bookList: $bookListRule")
			Log.d(TAG, "  name: $nameRule")
			Log.d(TAG, "  author: ${rule.author}")
			Log.d(TAG, "  bookUrl: $bookUrlRule")
			Log.d(TAG, "  coverUrl: ${rule.coverUrl}")
			Log.d(TAG, "Parsing list with rules: $itemRules")
			
			// Create JavaScript context for search
			val searchContext = JavaScriptContext.forSearch(
				key = filter?.query ?: "",
				page = (offset / 20) + 1,
				source = config
			)
			searchContext.setVariable("baseUrl", request.url)
			searchContext.setVariable("url", request.url)
			
			val items = ruleEngine.parseList(document, bookListRule, itemRules, searchContext)
			Log.d(TAG, "Parsed ${items.size} items from list")
			
			if (items.isEmpty()) {
				Log.w(TAG, "No items found. HTML preview: ${processedHtml.take(500)}")
				// Try alternative parsing strategies
				return tryAlternativeParsing(document, processedHtml, request.url, rule, searchContext)
			}
			
			// Handle cases where parsed items are present but fields are empty (e.g., 铅笔小说)
			val hasValid = items.any { !it["name"].isNullOrBlank() && !it["bookUrl"].isNullOrBlank() }
			if (!hasValid) {
				Log.w(TAG, "Parsed items missing required fields, attempting specialized fallback for ${rule.bookList}")
				val moduleItems = parseModuleItems(document, request.url)
				if (moduleItems.isNotEmpty()) {
					return moduleItems
				}
				// If still empty, try alternative strategies
				return tryAlternativeParsing(document, processedHtml, request.url, rule, searchContext)
			}
			
			// Convert to Manga objects
			return items.mapNotNull { item ->
				val title = item["name"]?.trim()
				val bookUrl = item["bookUrl"]?.trim()
				
				if (title.isNullOrBlank() || bookUrl.isNullOrBlank()) {
					Log.w(TAG, "Skipping item with missing title or URL: $item")
					return@mapNotNull null
				}
				
				val absoluteUrl = resolveUrl(request.url, bookUrl)
				val coverUrl = item["coverUrl"]?.let { resolveUrl(request.url, it) }
				val author = item["author"]?.trim()
				val intro = item["intro"]?.trim()
				
				Log.d(TAG, "Created manga: title=$title, url=$absoluteUrl")
				
				Manga(
					id = generateMangaId(absoluteUrl),
					title = title,
					altTitles = emptySet(),
					url = absoluteUrl,
					publicUrl = absoluteUrl,
					rating = -1f,
					contentRating = null,
					coverUrl = coverUrl ?: "",
					tags = emptySet(),
					state = null,
					authors = if (author != null) setOf(author) else emptySet(),
					largeCoverUrl = null,
					description = intro,
					chapters = null,
					source = source
				)
			}
		} catch (e: Exception) {
			Log.e(TAG, "Error in getList for ${source.name}: ${e.message}", e)
			Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
			return emptyList()
		}
	}
	
	/**
	 * Builds the list/search URL from the configuration
	 * Supports JavaScript in searchUrl with @js: prefix
	 * 
	 * Task 41.3: Implements searchUrl JavaScript support
	 * - Detects @js: syntax in searchUrl
	 * - Executes JavaScript to generate search URL
	 * - Supports dynamic URL generation
	 * 
	 * Requirements: 16.2
	 */
	private fun buildListUrl(query: String?, offset: Int): String {
		// Deprecated: replaced by buildListRequest
		return buildListRequest(query, offset, !query.isNullOrBlank()).url
	}

	private data class RequestConfig(
		val url: String,
		val method: String = "GET",
		val body: Map<String, String> = emptyMap(),
		val headers: Map<String, String> = emptyMap()
	)
	
	private fun buildListRequest(query: String?, offset: Int, isSearching: Boolean): RequestConfig {
		val baseUrl = config.bookSourceUrl
		val headers = buildRequestHeaders()
		
		// If there's a query, use searchUrl
		if (!query.isNullOrBlank()) {
			val searchUrl = config.searchUrl
			if (!searchUrl.isNullOrBlank()) {
				// Handle @js searchUrl
				if (searchUrl.startsWith("@js:") || searchUrl.contains("<js>")) {
					val context = JavaScriptContext.forSearch(
						key = query,
						page = (offset / 20) + 1,
						source = config
					)
					context.setVariable("baseUrl", baseUrl)
					val jsResult = ruleEngine.parseField(
						org.jsoup.Jsoup.parse("").body(),
						searchUrl,
						context
					)
					if (jsResult.isNotBlank()) {
						return RequestConfig(url = jsResult, method = "GET", headers = headers)
					}
					Log.w(TAG, "JavaScript searchUrl returned empty result, falling back to template replacement")
				}
				
				// Handle Legado searchUrl with options: url,{'method':'POST','body':'...','charset':'gbk','headers':{...}}
				val parts = searchUrl.split(",", limit = 2)
				val urlPart = parts[0]
				val optionsPart = parts.getOrNull(1)
				
				val encodedQuery = URLEncoder.encode(query, "UTF-8")
				val page = ((offset / 20) + 1).toString()
				val finalUrl = urlPart
					.replace("{{key}}", encodedQuery)
					.replace("{key}", encodedQuery)
					.replace("{{page}}", page)
					.replace("{page}", page)
					.let { if (it.startsWith("http")) it else resolveUrl(baseUrl, it) }
				
				if (optionsPart != null) {
					val opts = parseOptionsString(optionsPart)
					val method = opts.method.uppercase()
					val mergedHeaders = headers + opts.headers
					val bodyMap = opts.body?.let { parseBodyToMap(it) } ?: emptyMap()
					return RequestConfig(
						url = finalUrl,
						method = method,
						body = bodyMap,
						headers = mergedHeaders
					)
				}
				
				return RequestConfig(url = finalUrl, method = "GET", headers = headers)
			}
		}
		
		// Otherwise use exploreUrl or base URL
		val exploreUrl = config.exploreUrl
		if (!exploreUrl.isNullOrBlank()) {
			if (exploreUrl.trim().startsWith("[")) {
				try {
					val json = Json { ignoreUnknownKeys = true; isLenient = true }
					val exploreItems = json.decodeFromString<List<ExploreItem>>(exploreUrl)
					val firstValidUrl = exploreItems.firstOrNull { !it.url.isNullOrBlank() }?.url
					if (firstValidUrl != null) {
						val fullUrl = if (firstValidUrl.startsWith("http")) firstValidUrl else resolveUrl(baseUrl, firstValidUrl)
						return RequestConfig(
							url = fullUrl.replace("{{page}}", ((offset / 20) + 1).toString())
								.replace("{page}", ((offset / 20) + 1).toString()),
							headers = headers
						)
					}
				} catch (e: Exception) {
					Log.e(TAG, "Failed to parse exploreUrl JSON array: ${e.message}")
				}
				return RequestConfig(url = baseUrl, headers = headers)
			} else if (exploreUrl.contains("::")) {
				val lines = exploreUrl.split("\n", "\r\n")
				val firstValidUrl = lines
					.map { it.trim() }
					.filter { it.contains("::") }
					.map { it.substringAfter("::").trim() }
					.firstOrNull { it.isNotBlank() }
				
				if (firstValidUrl != null) {
					val fullUrl = if (firstValidUrl.startsWith("http")) firstValidUrl else resolveUrl(baseUrl, firstValidUrl)
					return RequestConfig(
						url = fullUrl.replace("{{page}}", ((offset / 20) + 1).toString())
							.replace("{page}", ((offset / 20) + 1).toString()),
						headers = headers
					)
				}
				return RequestConfig(url = baseUrl, headers = headers)
			} else {
				return RequestConfig(
					url = exploreUrl.replace("{{page}}", ((offset / 20) + 1).toString())
						.replace("{page}", ((offset / 20) + 1).toString()),
					headers = headers
				)
			}
		}
		
		return RequestConfig(url = baseUrl, headers = headers)
	}
	
	private data class ParsedOptions(
		val method: String = "GET",
		val body: String? = null,
		val headers: Map<String, String> = emptyMap()
	)
	
	private fun parseOptionsString(options: String): ParsedOptions {
		val cleaned = options.trim().removePrefix(",")
		// options typically looks like {'method':'POST','body':'searchkey={{key}}','headers':{...}}
		val normalized = cleaned.replace("'", "\"")
		return try {
			val jsonElement = Json { ignoreUnknownKeys = true; isLenient = true }.parseToJsonElement(normalized)
			val obj = jsonElement.jsonObject
			val method = obj["method"]?.toString()?.trim('"') ?: "GET"
			val body = obj["body"]?.toString()?.trim('"')
			val headers = obj["headers"]?.let { element ->
				runCatching {
					Json { ignoreUnknownKeys = true; isLenient = true }
						.decodeFromString<Map<String, String>>(element.toString())
				}.getOrDefault(emptyMap())
			} ?: emptyMap()
			ParsedOptions(method = method, body = body, headers = headers)
		} catch (e: Exception) {
			ParsedOptions()
		}
	}
	
	private fun parseBodyToMap(body: String): Map<String, String> {
		return body.split("&").mapNotNull {
			if (!it.contains("=")) return@mapNotNull null
			val parts = it.split("=", limit = 2)
			val key = parts[0]
			val value = parts.getOrNull(1) ?: ""
			key to value
		}.toMap()
	}
	
	/**
	 * Data class for parsing explore page items
	 */
	@Serializable
	private data class ExploreItem(
		val title: String? = null,
		val url: String? = null,
		val style: JsonElement? = null
	)
	
	/**
	 * Resolves a relative URL to an absolute URL
	 */
	private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
		if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
			return relativeUrl
		}
		
		val base = try {
			java.net.URL(baseUrl)
		} catch (e: Exception) {
			return relativeUrl
		}
		
		return try {
			java.net.URL(base, relativeUrl).toString()
		} catch (e: Exception) {
			relativeUrl
		}
	}
	
	/**
	 * Handle common issues in HTML responses
	 * 
	 * This method detects and handles:
	 * - Cloudflare verification pages
	 * - Other anti-bot protection
	 * - Empty or invalid responses
	 * - Encoding issues
	 */
	private suspend fun handleCommonIssues(html: String, url: String, rule: SearchRule, httpCode: Int): String {
		// Debug: comprehensive HTML inspection
		Log.d(TAG, "HTML length: ${html.length} bytes")
		
		if (html.isNotEmpty()) {
			// Show first 200 chars
			val start = html.substring(0, minOf(200, html.length))
			Log.d(TAG, "HTML start (200 chars): $start")
			
			// Show middle 200 chars
			if (html.length > 400) {
				val midPoint = html.length / 2
				val middle = html.substring(midPoint - 100, minOf(midPoint + 100, html.length))
				Log.d(TAG, "HTML middle (200 chars): $middle")
			}
			
			// Show last 200 chars
			if (html.length > 200) {
				val end = html.substring(maxOf(0, html.length - 200))
				Log.d(TAG, "HTML end (200 chars): $end")
			}
		}
		
		// Check for specific Cloudflare markers
		val hasJustAMoment = html.contains("Just a moment", ignoreCase = true)
		val hasCloudflare = html.contains("cloudflare", ignoreCase = true)
		val hasCfChallenge = html.contains("cf-browser-verification", ignoreCase = true) || 
		                     html.contains("cf-challenge-form", ignoreCase = true)
		val hasDDoS = html.contains("DDoS protection by Cloudflare", ignoreCase = true)
		
		Log.d(TAG, "Cloudflare markers: justAMoment=$hasJustAMoment, cloudflare=$hasCloudflare, cfChallenge=$hasCfChallenge, ddos=$hasDDoS")
		
		// First, check for Cloudflare verification in content (regardless of HTTP code)
		// This handles cases where server returns 200 but with Cloudflare challenge
		if (isCloudflareVerification(html)) {
			Log.w(TAG, "Detected Cloudflare verification page (HTTP $httpCode) for ${source.name}")
			return handleCloudflareVerification(html, url, rule)
		}
		
		// For 403/503 responses with short content, also treat as potential Cloudflare
		// even if traditional markers aren't present
		val isPotentialCloudflare = (httpCode == 403 || httpCode == 503) && 
			(html.isBlank() || html.length < 100)
		
		if (isPotentialCloudflare) {
			Log.w(TAG, "Detected potential Cloudflare protection (HTTP $httpCode, short response) for ${source.name}")
			return handleCloudflareVerification(html, url, rule)
		}
		
		// Check for other anti-bot protection
		if (isAntiBotProtection(html)) {
			Log.w(TAG, "Detected anti-bot protection for ${source.name}")
			return handleAntiBotProtection(html, url, rule)
		}
		
		// Check for empty or invalid responses
		if (html.isBlank() || html.length < 100) {
			Log.w(TAG, "Received empty or very short response for ${source.name}")
			return ""
		}
		
		// Check for common error pages (skip for large 200 pages to avoid false positives)
		val shouldCheckError = httpCode >= 400 || html.length < 4096
		if (shouldCheckError && isErrorPage(html)) {
			Log.w(TAG, "Detected error page for ${source.name}")
			return ""
		}
		
		return html
	}
	
	/**
	 * Check if the HTML contains Cloudflare verification
	 */
	private fun isCloudflareVerification(html: String): Boolean {
		// Keep markers tight to avoid false positives on sites that simply use Cloudflare CDN assets
		return html.contains("Just a moment", ignoreCase = true) ||
			html.contains("Attention Required", ignoreCase = true) ||
			html.contains("Please wait while we check your browser", ignoreCase = true) ||
			html.contains("challenge-platform", ignoreCase = true) || // present in CF challenge pages
			html.contains("Managed Challenge", ignoreCase = true) ||
			html.contains("__cf_chl", ignoreCase = true) ||
			html.contains("_cf_chl_opt", ignoreCase = true) ||
			html.contains("cf_chl_", ignoreCase = true) ||
			html.contains("cf-turnstile", ignoreCase = true) ||
			html.contains("turnstile verification", ignoreCase = true) ||
			html.contains("cf-challenge-form", ignoreCase = true) ||
			html.contains("cf-browser-verification", ignoreCase = true) ||
			html.contains("/cdn-cgi/challenge", ignoreCase = true) ||
			html.contains("cf_clearance", ignoreCase = true) ||
			html.contains("DDoS protection by Cloudflare", ignoreCase = true)
	}
	
	/**
	 * Check if the HTML contains other anti-bot protection
	 */
	private fun isAntiBotProtection(html: String): Boolean {
		return html.contains("人机验证") ||
			html.contains("验证码") ||
			html.contains("captcha") ||
			html.contains("robot") ||
			html.contains("bot detection") ||
			html.contains("security check")
	}
	
	/**
	 * Check if the HTML is an error page
	 */
	private fun isErrorPage(html: String): Boolean {
		return html.contains("404") ||
			html.contains("Not Found") ||
			html.contains("403") ||
			html.contains("Forbidden") ||
			html.contains("500") ||
			html.contains("Internal Server Error") ||
			html.contains("502") ||
			html.contains("Bad Gateway") ||
			html.contains("503") ||
			html.contains("Service Unavailable")
	}
	
	/**
	 * Handle Cloudflare verification
	 * 
	 * This method attempts to handle Cloudflare verification by:
	 * 1. Checking if the source has JavaScript init rules for handling verification
	 * 2. Using browser launcher if available
	 * 3. Returning empty string if verification cannot be handled
	 */
	private suspend fun handleCloudflareVerification(html: String, url: String, rule: SearchRule): String {
		Log.i(TAG, "Attempting to handle Cloudflare verification for ${source.name}")
		
		// Check if the rule has an init script for handling verification
		val initScript = rule.init
		if (!initScript.isNullOrBlank() && (initScript.contains("cloudflare") || initScript.contains("startBrowserAwait"))) {
			Log.d(TAG, "Found init script for Cloudflare handling: $initScript")
			
			// Create JavaScript context
			val context = JavaScriptContext.forSearch(
				key = "",
				page = 1,
				source = config
			)
			context.setVariable("baseUrl", url)
			context.setVariable("result", html)
			
			try {
				// Execute the init script
				val processedHtml = ruleEngine.parseField(
					org.jsoup.Jsoup.parse(html).body(),
					initScript,
					context
				)
				
				if (processedHtml.isNotBlank() && !isCloudflareVerification(processedHtml)) {
					Log.i(TAG, "Successfully processed Cloudflare verification using init script")
					return processedHtml
				}
			} catch (e: Exception) {
				Log.e(TAG, "Failed to execute init script for Cloudflare handling", e)
			}
		}
		
		// If no init script or it failed, try using browser launcher as fallback
		if (browserLauncher != null) {
			Log.i(TAG, "No init script available, attempting automatic browser launch for Cloudflare bypass")
			try {
				// Launch browser and wait for user to complete verification
				val result = browserLauncher.launchAndWait(url, "Cloudflare验证", source)
				
				// The browser launcher returns "browser_launched" on success
				// After the browser is closed, cookies should be synced
				if (result.isNotBlank()) {
					Log.i(TAG, "Browser launched successfully for Cloudflare verification")
					// Make the request again with the synced cookies
					val response = legadoHttpClient.get(url, buildRequestHeaders(), source = source)
					val newHtml = response.body?.string() ?: ""
					val newCode = response.code
					response.close()
					
					Log.d(TAG, "Re-request after browser: HTTP $newCode, length=${newHtml.length}")
					
					if (newHtml.isNotBlank() && !isCloudflareVerification(newHtml)) {
						Log.i(TAG, "Successfully bypassed Cloudflare using browser")
						return newHtml
					}
					
					Log.w(TAG, "Cloudflare bypass via browser did not return valid content")
				}
			} catch (e: Exception) {
				Log.e(TAG, "Failed to launch browser for Cloudflare handling", e)
			}
		}
		
		// If no init script or browser launcher, log the issue and return empty
		Log.w(TAG, "Cannot handle Cloudflare verification automatically. User intervention may be required.")
		Log.w(TAG, "Consider using the browser to manually complete verification for ${source.name}")
		
		return ""
	}
	
	/**
	 * Handle other anti-bot protection
	 */
	private suspend fun handleAntiBotProtection(html: String, url: String, rule: SearchRule): String {
		Log.i(TAG, "Attempting to handle anti-bot protection for ${source.name}")
		
		// Similar to Cloudflare handling, check for init scripts
		val initScript = rule.init
		if (!initScript.isNullOrBlank()) {
			Log.d(TAG, "Found init script for anti-bot handling: $initScript")
			
			val context = JavaScriptContext.forSearch(
				key = "",
				page = 1,
				source = config
			)
			context.setVariable("baseUrl", url)
			context.setVariable("result", html)
			
			try {
				val processedHtml = ruleEngine.parseField(
					org.jsoup.Jsoup.parse(html).body(),
					initScript,
					context
				)
				
				if (processedHtml.isNotBlank() && !isAntiBotProtection(processedHtml)) {
					Log.i(TAG, "Successfully processed anti-bot protection using init script")
					return processedHtml
				}
			} catch (e: Exception) {
				Log.e(TAG, "Failed to execute init script for anti-bot handling", e)
			}
		}
		
		Log.w(TAG, "Cannot handle anti-bot protection automatically for ${source.name}")
		return ""
	}
	
	/**
	 * Try alternative parsing strategies when the main parsing fails
	 */
	private fun tryAlternativeParsing(
		document: org.jsoup.nodes.Document,
		rawHtml: String,
		url: String,
		rule: SearchRule,
		context: JavaScriptContext
	): List<Manga> {
		Log.d(TAG, "Trying alternative parsing strategies for ${source.name}")
		
		// Strategy 1: Try common book list selectors
		val commonSelectors = listOf(
			"div.book-list li",
			"ul.book-list li", 
			"div.list li",
			"ul.list li",
			"div.books li",
			"ul.books li",
			"div.novel-list li",
			"ul.novel-list li",
			"div.item",
			"li.item",
			"div.book-item",
			"li.book-item",
			"div.novel-item",
			"li.novel-item"
		)
		
		for (selector in commonSelectors) {
			val elements = document.select(selector)
			if (elements.size > 0) {
				Log.d(TAG, "Found ${elements.size} items using alternative selector: $selector")
				
				// Try to extract basic information using common patterns
				val items = elements.mapNotNull { element ->
					val titleElement = element.selectFirst("a[title], h3 a, h4 a, .title a, .name a") 
						?: element.selectFirst("a")
					val title = titleElement?.attr("title")?.takeIf { it.isNotBlank() }
						?: titleElement?.text()?.takeIf { it.isNotBlank() }
					val bookUrl = titleElement?.attr("href")
					
					if (title != null && bookUrl != null) {
						val author = element.selectFirst(".author, .writer, .by")?.text()
						val coverImg = element.selectFirst("img")
						val coverUrl = coverImg?.attr("src") ?: coverImg?.attr("data-src")
						val intro = element.selectFirst(".intro, .desc, .description")?.text()
						
						mapOf(
							"name" to title,
							"bookUrl" to bookUrl,
							"author" to (author ?: ""),
							"coverUrl" to (coverUrl ?: ""),
							"intro" to (intro ?: "")
						)
					} else null
				}
				
				if (items.isNotEmpty()) {
					Log.i(TAG, "Successfully parsed ${items.size} items using alternative strategy")
					
					// Convert to Manga objects
					return items.mapNotNull { item ->
						val title = item["name"]?.trim()
						val bookUrl = item["bookUrl"]?.trim()
						
						if (title.isNullOrBlank() || bookUrl.isNullOrBlank()) {
							return@mapNotNull null
						}
						
						val absoluteUrl = resolveUrl(url, bookUrl)
						val coverUrl = item["coverUrl"]?.let { resolveUrl(url, it) }
						val author = item["author"]?.trim()
						val intro = item["intro"]?.trim()
						
						Manga(
							id = generateMangaId(absoluteUrl),
							title = title,
							altTitles = emptySet(),
							url = absoluteUrl,
							publicUrl = absoluteUrl,
							rating = -1f,
							contentRating = null,
							coverUrl = coverUrl ?: "",
							tags = emptySet(),
							state = null,
							authors = if (author != null) setOf(author) else emptySet(),
							largeCoverUrl = null,
							description = intro,
							chapters = null,
							source = source
						)
					}
				}
			}
		}
		
		// Strategy 2: Specialized parser for 69书吧 newlistbox structure
		val newListBoxItems = parseNewListBox(document, url)
		if (newListBoxItems.isNotEmpty()) {
			return newListBoxItems
		}
		
		// Strategy 3: Fallback by scanning book detail links (e.g., /book/12345.htm)
		val linkBasedItems = parseByBookLinks(document, url)
		if (linkBasedItems.isNotEmpty()) {
			Log.i(TAG, "Parsed ${linkBasedItems.size} items using book link fallback")
			return linkBasedItems
		}
		
		// Strategy 4: Regex fallback on raw HTML (handles script-rendered pages)
		val regexItems = parseByBookLinksFromRaw(rawHtml, url)
		if (regexItems.isNotEmpty()) {
			Log.i(TAG, "Parsed ${regexItems.size} items using raw HTML fallback")
			return regexItems
		}
		
		// Strategy 5: Generic anchor fallback (any anchors with plausible titles)
		val anchorItems = parseGenericAnchors(document, url)
		if (anchorItems.isNotEmpty()) {
			Log.i(TAG, "Parsed ${anchorItems.size} items using generic anchor fallback")
			return anchorItems
		}
		
		Log.w(TAG, "All alternative parsing strategies failed for ${source.name}")
		return emptyList()
	}
	
	/**
	 * Fallback parser that scans for anchors linking to book detail pages (e.g., /book/90028.htm)
	 */
	private fun parseByBookLinks(
		document: org.jsoup.nodes.Document,
		baseUrl: String
	): List<Manga> {
		// Match common 69书吧 detail links: /book/12345.htm or /book/12345/ or /book/12345.html
		val bookLinks = document.select("a[href~=\\/book\\/\\d+(\\.html?)?\\/?]")
		if (bookLinks.isEmpty()) return emptyList()
		
		val seen = mutableSetOf<String>()
		val results = mutableListOf<Manga>()
		
		for (link in bookLinks) {
			val rawHref = link.attr("href").trim()
			if (rawHref.isBlank()) continue
			
			val absoluteUrl = resolveUrl(baseUrl, rawHref)
			if (!seen.add(absoluteUrl)) continue
			
			val title = link.attr("title").ifBlank { link.text() }.trim()
			if (title.isBlank()) continue
			
			// Try to find a meaningful container to extract extra fields
			val container = link.closest("li") ?: link.parents().firstOrNull { it.tagName() == "div" }
			val coverElement = container?.selectFirst("img")
			val coverUrl = coverElement?.attr("data-src").ifNullOrBlank { coverElement?.attr("src") }
			val author = container?.selectFirst("label, .author, .writer")?.text()?.trim()
			val intro = container?.selectFirst(".ellipsis_2, .intro, .description, p")?.text()?.trim()
			
			results.add(
				Manga(
					id = generateMangaId(absoluteUrl),
					title = title,
					altTitles = emptySet(),
					url = absoluteUrl,
					publicUrl = absoluteUrl,
					rating = -1f,
					contentRating = null,
					coverUrl = coverUrl?.let { resolveUrl(baseUrl, it) } ?: "",
					tags = emptySet(),
					state = null,
					authors = if (author.isNullOrBlank()) emptySet() else setOf(author),
					largeCoverUrl = null,
					description = intro,
					chapters = null,
					source = source
				)
			)
		}
		
		return results
	}
	
	/**
	 * Specialized parser for 69书吧 list page (newlistbox + li structure)
	 */
	private fun parseNewListBox(
		document: org.jsoup.nodes.Document,
		baseUrl: String
	): List<Manga> {
		val items = document.select("div.newlistbox li")
		if (items.isEmpty()) return emptyList()
		
		val results = mutableListOf<Manga>()
		for (item in items) {
			val anchor = item.selectFirst("h3 a") ?: continue
			val href = anchor.attr("href").trim()
			val title = anchor.attr("title").ifBlank { anchor.text() }.trim()
			if (title.isBlank() || href.isBlank()) continue
			
			val author = item.selectFirst("label, .author, .writer")?.text()?.trim()
			val intro = item.selectFirst(".ellipsis_2, .intro, p")?.text()?.trim()
			val coverEl = item.selectFirst("img")
			val cover = coverEl?.attr("data-src").ifNullOrBlank { coverEl?.attr("src") }
				?: derive69ShubaCover(href, baseUrl)
			
			val absoluteUrl = resolveUrl(baseUrl, href)
			
			results.add(
				Manga(
					id = generateMangaId(absoluteUrl),
					title = title,
					altTitles = emptySet(),
					url = absoluteUrl,
					publicUrl = absoluteUrl,
					rating = -1f,
					contentRating = null,
					coverUrl = cover?.let { resolveUrl(baseUrl, it) } ?: "",
					tags = emptySet(),
					state = null,
					authors = if (author.isNullOrBlank()) emptySet() else setOf(author),
					largeCoverUrl = null,
					description = intro,
					chapters = null,
					source = source
				)
			)
		}
		
		if (results.isNotEmpty()) {
			Log.i(TAG, "Parsed ${results.size} items using newlistbox fallback")
		}
		
		return results
	}
	
	private fun derive69ShubaCover(href: String, baseUrl: String): String? {
		val id = Regex("/(\\d+)\\.htm").find(href)?.groupValues?.getOrNull(1) ?: return null
		if (id.length < 3) return null
		val prefix = id.dropLast(3)
		val origin = try {
			val url = java.net.URL(baseUrl)
			"${url.protocol}://${url.host}"
		} catch (e: Exception) {
			baseUrl.removeSuffix("/")
		}
		return "$origin/fengmian/$prefix/$id/${id}s.jpg"
	}

	/**
	 * Fallback chapter extraction when rule-based parsing returns empty.
	 * Looks for common numeric chapter links in the document or raw HTML.
	 */
	private fun parseChaptersFallback(
		document: org.jsoup.nodes.Document,
		rawHtml: String,
		baseUrl: String
	): List<MangaChapter> {
		// Strategy 1: anchors with numeric paths ending in html/htm
		val anchors = document.select("a[href~=\\.(?i:html?)$]")
		val chapters = mutableListOf<MangaChapter>()
		val seen = mutableSetOf<String>()
		var index = 0
		
		for (a in anchors) {
			val href = a.attr("href").trim()
			if (href.isBlank()) continue
			// Require numeric id in href and a plausible chapter path
			if (!Regex("\\d").containsMatchIn(href)) continue
			if (!Regex("(?:/)(txt|book|novel|xs|chapter|chapters)/", RegexOption.IGNORE_CASE).containsMatchIn(href) &&
				!Regex("/\\d+\\.html?", RegexOption.IGNORE_CASE).containsMatchIn(href)
			) continue
			
			val title = a.text().ifBlank { a.attr("title") }.trim()
			// Require chapter-like title (contains number or '第')
			if (title.length < 2) continue
			if (!Regex("\\d|第").containsMatchIn(title)) continue
			
			val abs = resolveUrl(baseUrl, href)
			if (!seen.add(abs)) continue
			if (abs == baseUrl) continue
			
			index += 1
			chapters.add(
				MangaChapter(
					id = generateChapterId(abs),
					title = title,
					number = index.toFloat(),
					volume = 0,
					url = abs,
					scanlator = null,
					uploadDate = 0,
					branch = null,
					source = source
				)
			)
		}
		
		// Strategy 2: regex on raw HTML for /txt/ or /book/ numeric chapter URLs
		if (chapters.isEmpty()) {
			val pattern = Regex("(/(?:txt|book|novel|xs)/[\\w\\d]+/[\\w\\d]+\\.(?:html?|htm))", RegexOption.IGNORE_CASE)
			pattern.findAll(rawHtml).forEach { match ->
				val abs = resolveUrl(baseUrl, match.value)
				if (!seen.add(abs)) return@forEach
				
				val windowStart = maxOf(0, match.range.first - 80)
				val windowEnd = minOf(rawHtml.length, match.range.last + 80)
				val window = rawHtml.substring(windowStart, windowEnd)
				val title = Regex(">([^<>]{2,80})</a>").find(window)?.groupValues?.getOrNull(1)
					?.trim()
					?: abs.substringAfterLast('/').substringBefore('.')
				if (!Regex("\\d|第").containsMatchIn(title)) return@forEach
				
				index += 1
				chapters.add(
					MangaChapter(
						id = generateChapterId(abs),
						title = title,
						number = index.toFloat(),
						volume = 0,
						url = abs,
						scanlator = null,
						uploadDate = 0,
						branch = null,
						source = source
					)
				)
			}
		}
		
		// Avoid returning tiny / obviously wrong lists
		if (chapters.size < 3) {
			return emptyList()
		}
		
		Log.i(TAG, "Parsed ${chapters.size} chapters using fallback extraction")
		
		return chapters
	}

	/**
	 * Read response body respecting charset hints (headers or meta charset).
	 */
	private fun readBodyWithCharset(response: okhttp3.Response): String {
		val body = response.body ?: return ""
		val bytes = try {
			body.bytes()
		} catch (e: Exception) {
			return ""
		}
		
		// Default decode as UTF-8
		val utf8Text = bytes.toString(Charsets.UTF_8)
		val charsetFromHeader = response.header("Content-Type")
			?.substringAfter("charset=", "")
			?.takeIf { it.isNotBlank() }
		val charset = when {
			!charsetFromHeader.isNullOrBlank() -> {
				runCatching { Charset.forName(charsetFromHeader.trim()) }.getOrNull()
			}
			utf8Text.contains("charset=\"gbk\"", ignoreCase = true) ||
				utf8Text.contains("charset=gbk", ignoreCase = true) ||
				utf8Text.contains("charset=\"gb2312\"", ignoreCase = true) ||
				utf8Text.contains("charset=gb2312", ignoreCase = true) -> Charset.forName("GBK")
			else -> Charsets.UTF_8
		}
		
		return if (charset == null || charset == Charsets.UTF_8) utf8Text else String(bytes, charset)
	}
	
	/**
	 * Specialized parser for 铅笔小说 (23qb) module-item cards
	 */
	private fun parseModuleItems(document: org.jsoup.nodes.Document, baseUrl: String): List<Manga> {
		val elements = document.select(".module-item")
		if (elements.isEmpty()) return emptyList()
		
		val results = mutableListOf<Manga>()
		for (el in elements) {
			val titleEl = el.selectFirst(".module-item-title a")
			val href = titleEl?.attr("href")?.trim().orEmpty()
			val name = titleEl?.attr("title")?.ifBlank { titleEl.text() }?.trim().orEmpty()
			if (name.isBlank() || href.isBlank()) continue
			
			val author = el.selectFirst(".module-item-text")?.text()?.trim()
			val coverEl = el.selectFirst("img")
			val cover = coverEl?.attr("data-src").ifNullOrBlank { coverEl?.attr("data-original") }
				?: coverEl?.attr("src")
			
			val absoluteUrl = resolveUrl(baseUrl, href)
			
			results.add(
				Manga(
					id = generateMangaId(absoluteUrl),
					title = name,
					altTitles = emptySet(),
					url = absoluteUrl,
					publicUrl = absoluteUrl,
					rating = -1f,
					contentRating = null,
					coverUrl = cover?.let { resolveUrl(baseUrl, it) } ?: "",
					tags = emptySet(),
					state = null,
					authors = if (author.isNullOrBlank()) emptySet() else setOf(author),
					largeCoverUrl = null,
					description = null,
					chapters = null,
					source = source
				)
			)
		}
		
		if (results.isNotEmpty()) {
			Log.i(TAG, "Parsed ${results.size} items using module-item fallback")
		}
		
		return results
	}
	
	private fun String?.ifNullOrBlank(fallback: () -> String?): String? {
		return if (this.isNullOrBlank()) fallback() else this
	}
	
	/**
	 * Parse book links directly from raw HTML using regex (for pages rendered by scripts)
	 */
	private fun parseByBookLinksFromRaw(
		rawHtml: String,
		baseUrl: String
	): List<Manga> {
		// Match common book URLs, including numeric IDs and known patterns (book, novel, xs, shoujixs, b12345)
		val pattern = Regex("(/(?:book|novel|xs|shoujixs|b)\\/[^\"'\\s<>]+)", RegexOption.IGNORE_CASE)
		val seen = mutableSetOf<String>()
		val results = mutableListOf<Manga>()
		
		pattern.findAll(rawHtml).forEach { matchResult ->
			val href = matchResult.value
			val absoluteUrl = resolveUrl(baseUrl, href)
			if (!seen.add(absoluteUrl)) return@forEach
			
			// Try to capture title from nearby attributes or text
			val startIdx = maxOf(0, matchResult.range.first - 200)
			val endIdx = minOf(rawHtml.length, matchResult.range.last + 200)
			val window = rawHtml.substring(startIdx, endIdx)
			
			val title = Regex("title=\"([^\"]{1,80})\"").find(window)?.groupValues?.getOrNull(1)
				?: Regex("alt=\"([^\"]{1,80})\"").find(window)?.groupValues?.getOrNull(1)
				?: Regex(">\\s*([^<>]{1,80})\\s*</a>").find(window)?.groupValues?.getOrNull(1)
				?: absoluteUrl.substringAfterLast("/book/").substringBefore(".htm").substringBefore(".html")
			
			val cover = Regex("(?i)(https?://[^\\s\"']+?(?:cdnshu\\.com|cdn\\.cdnshu\\.com)[^\\s\"']+?(?:jpg|jpeg|png|webp))")
				.find(window)?.groupValues?.getOrNull(1)
			
			results.add(
				Manga(
					id = generateMangaId(absoluteUrl),
					title = title.trim(),
					altTitles = emptySet(),
					url = absoluteUrl,
					publicUrl = absoluteUrl,
					rating = -1f,
					contentRating = null,
					coverUrl = cover?.let { resolveUrl(baseUrl, it) } ?: "",
					tags = emptySet(),
					state = null,
					authors = emptySet(),
					largeCoverUrl = null,
					description = null,
					chapters = null,
					source = source
				)
			)
		}
		
		return results
	}
	
	/**
	 * Very generic fallback: scan anchors and use text as title when it looks like a book entry.
	 */
	private fun parseGenericAnchors(document: org.jsoup.nodes.Document, baseUrl: String): List<Manga> {
		val anchors = document.select("a[href]")
		if (anchors.isEmpty()) return emptyList()
		
		val results = mutableListOf<Manga>()
		val seen = mutableSetOf<String>()
		
		for (a in anchors) {
			val title = a.text()?.trim().orEmpty()
			val href = a.attr("href").trim()
			
			// Skip trivial or non-book links
			if (title.length < 3) continue
			if (href.isBlank()) continue
			if (href.startsWith("javascript:") || href.startsWith("#")) continue
			
			val absoluteUrl = resolveUrl(baseUrl, href)
			if (!seen.add(absoluteUrl)) continue
			
			results.add(
				Manga(
					id = generateMangaId(absoluteUrl),
					title = title,
					altTitles = emptySet(),
					url = absoluteUrl,
					publicUrl = absoluteUrl,
					rating = -1f,
					contentRating = null,
					coverUrl = "",
					tags = emptySet(),
					state = null,
					authors = emptySet(),
					largeCoverUrl = null,
					description = null,
					chapters = null,
					source = source
				)
			)
			
			if (results.size >= 100) break
		}
		
		return results
	}
	
	/**
	 * Handle common issues for details pages
	 */
	private suspend fun handleCommonIssuesForDetails(html: String, url: String): String {
		// Check for Cloudflare verification
		if (isCloudflareVerification(html)) {
			Log.w(TAG, "Detected Cloudflare verification on details page for ${source.name}")
			return handleCloudflareForDetails(html, url)
		}
		
		// Check for other issues
		if (isAntiBotProtection(html) || (html.length < 4096 && isErrorPage(html))) {
			Log.w(TAG, "Detected protection or error on details page for ${source.name}, attempting Cloudflare flow as fallback")
			val processed = handleCloudflareForDetails(html, url)
			if (processed.isNotBlank()) return processed
			return ""
		}
		
		return html
	}
	
	/**
	 * Handle common issues for content pages
	 */
	private suspend fun handleCommonIssuesForContent(html: String, url: String): String {
		// Check for Cloudflare verification
		if (isCloudflareVerification(html)) {
			Log.w(TAG, "Detected Cloudflare verification on content page for ${source.name}")
			return handleCloudflareForContent(html, url)
		}
		
		// Check for other issues
		if (isAntiBotProtection(html) || (html.length < 4096 && isErrorPage(html))) {
			Log.w(TAG, "Detected protection or error on content page for ${source.name}, attempting Cloudflare flow as fallback")
			val processed = handleCloudflareForContent(html, url)
			if (processed.isNotBlank()) return processed
			return ""
		}
		
		return html
	}
	
	/**
	 * Handle Cloudflare verification for details pages
	 */
	private suspend fun handleCloudflareForDetails(html: String, url: String): String {
		val bookInfoRule = config.ruleBookInfo
		val initScript = bookInfoRule?.init
		
		if (!initScript.isNullOrBlank() && (initScript.contains("cloudflare") || initScript.contains("startBrowserAwait"))) {
			Log.d(TAG, "Found init script for details Cloudflare handling")
			
			val bookInfo = BookInfo(bookUrl = url, name = "")
			val context = JavaScriptContext.forBookInfo(
				book = bookInfo,
				source = config,
				baseUrl = url
			)
			context.setVariable("result", html)
			
			try {
				val processedHtml = ruleEngine.parseField(
					org.jsoup.Jsoup.parse(html).body(),
					initScript,
					context
				)
				
				if (processedHtml.isNotBlank() && !isCloudflareVerification(processedHtml)) {
					Log.i(TAG, "Successfully processed Cloudflare verification for details page")
					return processedHtml
				}
			} catch (e: Exception) {
				Log.e(TAG, "Failed to execute init script for details Cloudflare handling", e)
			}
		}
		
		// Fallback: launch browser to pass challenge, then retry
		if (browserLauncher != null) {
			Log.i(TAG, "Attempting browser-based Cloudflare bypass for details page")
			try {
				val result = browserLauncher.launchAndWait(url, "Cloudflare验证", source)
				if (result.isNotBlank()) {
					// If browser already returned usable HTML, use it directly
					if (!isCloudflareVerification(result)) {
						Log.i(TAG, "Successfully bypassed Cloudflare for details via browser (using browser HTML)")
						return result
					}
					
					// Otherwise retry network with cookies synced from WebView
					val response = legadoHttpClient.get(url, buildRequestHeaders(), source = source)
					val newHtml = readBodyWithCharset(response)
					val newCode = response.code
					response.close()
					
					Log.d(TAG, "Details re-request after browser: HTTP $newCode, length=${newHtml.length}")
					
					if (newHtml.isNotBlank() && !isCloudflareVerification(newHtml)) {
						Log.i(TAG, "Successfully bypassed Cloudflare for details via browser")
						return newHtml
					}
				}
			} catch (e: Exception) {
				Log.e(TAG, "Browser-based Cloudflare bypass failed for details", e)
			}
		}
		
		Log.w(TAG, "Cannot handle Cloudflare verification on details page for ${source.name}")
		return ""
	}
	
	/**
	 * Handle Cloudflare verification for content pages
	 */
	private suspend fun handleCloudflareForContent(html: String, url: String): String {
		val contentRule = config.ruleContent
		val initScript = contentRule?.init
		
		if (!initScript.isNullOrBlank() && (initScript.contains("cloudflare") || initScript.contains("startBrowserAwait"))) {
			Log.d(TAG, "Found init script for content Cloudflare handling")
			
			val bookInfo = BookInfo(bookUrl = url, name = "")
			val chapterInfo = ChapterInfo(chapterUrl = url, name = "", index = 0)
			val context = JavaScriptContext.forContent(
				book = bookInfo,
				chapter = chapterInfo,
				source = config,
				baseUrl = url
			)
			context.setVariable("result", html)
			
			try {
				val processedHtml = ruleEngine.parseField(
					org.jsoup.Jsoup.parse(html).body(),
					initScript,
					context
				)
				
				if (processedHtml.isNotBlank() && !isCloudflareVerification(processedHtml)) {
					Log.i(TAG, "Successfully processed Cloudflare verification for content page")
					return processedHtml
				}
			} catch (e: Exception) {
				Log.e(TAG, "Failed to execute init script for content Cloudflare handling", e)
			}
		}
		
		// Fallback: launch browser to pass challenge, then retry
		if (browserLauncher != null) {
			Log.i(TAG, "Attempting browser-based Cloudflare bypass for content page")
			try {
				val result = browserLauncher.launchAndWait(url, "Cloudflare验证", source)
				if (result.isNotBlank()) {
					// Use browser HTML if it's already valid
					if (!isCloudflareVerification(result)) {
						Log.i(TAG, "Successfully bypassed Cloudflare for content via browser (using browser HTML)")
						return result
					}
					
					val response = legadoHttpClient.get(url, buildRequestHeaders(), source = source)
					val newHtml = readBodyWithCharset(response)
					val newCode = response.code
					response.close()
					
					Log.d(TAG, "Content re-request after browser: HTTP $newCode, length=${newHtml.length}")
					
					if (newHtml.isNotBlank() && !isCloudflareVerification(newHtml)) {
						Log.i(TAG, "Successfully bypassed Cloudflare for content via browser")
						return newHtml
					}
				}
			} catch (e: Exception) {
				Log.e(TAG, "Browser-based Cloudflare bypass failed for content", e)
			}
		}
		
		Log.w(TAG, "Cannot handle Cloudflare verification on content page for ${source.name}")
		return ""
	}
	
	/**
	 * Generates a unique manga ID from the URL
	 */
	private fun generateMangaId(url: String): Long {
		return url.hashCode().toLong()
	}
	
	/**
	 * Get manga details
	 * 
	 * Task 28.2: Implements details page parsing
	 * - Gets details page HTML
	 * - Uses ruleBookInfo to parse details information
	 * - Uses ruleToc to parse chapter list
	 * - Returns complete Manga object
	 * 
	 * Requirements: 4.3
	 */
	override suspend fun getDetails(manga: Manga): Manga {
		Log.d(TAG, "getDetails called for ${source.name}, url=${manga.url}")
		
		try {
			// Make HTTP request to details page using LegadoHttpClient
			val response = getWithCloudflareRetry(
				RequestConfig(
					url = manga.url,
					headers = buildRequestHeaders()
				)
			)
			if (response == null) {
				Log.e(TAG, "Failed to fetch details after Cloudflare handling")
				return manga
			}
			
			// Read response body first (even for error codes like 403/503)
			val html = readBodyWithCharset(response)
			val httpCode = response.code
			response.close()
			
			// Handle common issues in the HTML response (including Cloudflare on 403/503)
			val processedHtml = handleCommonIssuesForDetails(html, manga.url)
			if (processedHtml.isEmpty()) {
				if (!response.isSuccessful) {
					Log.e(TAG, "HTTP request failed: ${httpCode} for details page and couldn't handle the response")
				} else {
					Log.w(TAG, "HTML processing failed for details page")
				}
				return manga
			}
			
			// Parse HTML
			val document = Jsoup.parse(processedHtml, manga.url)
			val root = document.body()
			
			// Parse book info if available
			val bookInfoRule = config.ruleBookInfo
			var updatedManga = manga
			
			if (bookInfoRule != null) {
				Log.d(TAG, "Parsing book info")
				
				// Create JavaScript context for book info
				val bookInfo = BookInfo(
					bookUrl = manga.url,
					name = manga.title,
					author = manga.author,
					coverUrl = manga.coverUrl,
					intro = manga.description
				)
				val bookContext = JavaScriptContext.forBookInfo(
					book = bookInfo,
					source = config,
					baseUrl = manga.url
				)
				
				val name = bookInfoRule.name?.let { ruleEngine.parseField(root, it, bookContext) }
				val author = bookInfoRule.author?.let { ruleEngine.parseField(root, it, bookContext) }
				val coverUrl = bookInfoRule.coverUrl?.let { 
					val url = ruleEngine.parseField(root, it, bookContext)
					if (url.isNotBlank()) resolveUrl(manga.url, url) else null
				}
				val intro = bookInfoRule.intro?.let { ruleEngine.parseField(root, it, bookContext) }
				val kind = bookInfoRule.kind?.let { ruleEngine.parseField(root, it, bookContext) }
				val lastChapter = bookInfoRule.lastChapter?.let { ruleEngine.parseField(root, it, bookContext) }
				
				// Parse tags from kind field
				val tags = if (!kind.isNullOrBlank()) {
					kind.split(",", "，", "/", "|")
						.map { it.trim() }
						.filter { it.isNotBlank() }
						.map { MangaTag(title = it, key = it, source = source) }
						.toSet()
				} else {
					manga.tags
				}
				
				updatedManga = manga.copy(
					title = name?.takeIf { it.isNotBlank() } ?: manga.title,
					authors = if (author?.isNotBlank() == true) setOf(author) else manga.authors,
					coverUrl = coverUrl ?: manga.coverUrl,
					largeCoverUrl = coverUrl ?: manga.largeCoverUrl,
					description = intro?.takeIf { it.isNotBlank() } ?: manga.description,
					tags = tags
				)
				
				Log.d(TAG, "Parsed book info: title=${updatedManga.title}, author=${updatedManga.author}")
			}
			
			// Parse table of contents (chapters)
			val tocRule = config.ruleToc
			val chapters = if (tocRule != null) {
				Log.d(TAG, "Parsing table of contents")
				
				// Create JavaScript context for TOC
				val bookInfo = BookInfo(
					bookUrl = updatedManga.url,
					name = updatedManga.title,
					author = updatedManga.author,
					coverUrl = updatedManga.coverUrl,
					intro = updatedManga.description
				)
				val tocContext = JavaScriptContext.forBookInfo(
					book = bookInfo,
					source = config,
					baseUrl = updatedManga.url
				)
				
				val chapterRules = mapOf(
					"name" to tocRule.chapterName,
					"url" to tocRule.chapterUrl
				)
				
				val chapterItems = ruleEngine.parseList(document, tocRule.chapterList, chapterRules, tocContext)
				Log.d(TAG, "Parsed ${chapterItems.size} chapters")
				
				val parsed = chapterItems.mapIndexedNotNull { index, item ->
					val chapterName = item["name"]?.trim()
					val chapterUrl = item["url"]?.trim()
					
					if (chapterName.isNullOrBlank() || chapterUrl.isNullOrBlank()) {
						Log.w(TAG, "Skipping chapter with missing name or URL")
						return@mapIndexedNotNull null
					}
					
					val absoluteUrl = resolveUrl(manga.url, chapterUrl)
					
					MangaChapter(
						id = generateChapterId(absoluteUrl),
						title = chapterName,
						number = (index + 1).toFloat(),
						volume = 0,
						url = absoluteUrl,
						scanlator = null,
						uploadDate = 0,
						branch = null,
						source = source
					)
				}

				if (parsed.isNotEmpty()) {
					parsed
				} else {
					Log.w(TAG, "Parsed 0 chapters with rule, attempting fallback extraction")
					parseChaptersFallback(document, processedHtml, updatedManga.url)
				}
			} else {
				Log.w(TAG, "No ruleToc defined for ${source.name}")
				manga.chapters ?: emptyList()
			}
			
			return updatedManga.copy(chapters = chapters)
			
		} catch (e: Exception) {
			Log.e(TAG, "Error in getDetails for ${source.name}", e)
			return manga
		}
	}
	
	/**
	 * Generates a unique chapter ID from the URL
	 */
	private fun generateChapterId(url: String): Long {
		return url.hashCode().toLong()
	}
	
	/**
	 * Get pages for a chapter
	 * 
	 * Task 28.3: Implements chapter content parsing
	 * - Gets chapter page HTML
	 * - Uses ruleContent to parse content
	 * - Returns appropriate format based on bookSourceType (text/images)
	 * 
	 * Requirements: 4.4
	 */
	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		Log.d(TAG, "getPages called for ${source.name}, chapter=${chapter.name}")
		
		try {
			// Check if we have content rules
			val contentRule = config.ruleContent
			if (contentRule == null) {
				Log.w(TAG, "No ruleContent defined for ${source.name}")
				return emptyList()
			}
			
			// Make HTTP request to chapter page using LegadoHttpClient
			val response = getWithCloudflareRetry(
				RequestConfig(
					url = chapter.url,
					headers = buildRequestHeaders()
				)
			)
			if (response == null) {
				Log.e(TAG, "Failed to fetch content after Cloudflare handling")
				return emptyList()
			}
			
			// Read response body first (even for error codes like 403/503)
			val html = readBodyWithCharset(response)
			val httpCode = response.code
			response.close()
			
			// Handle common issues in the HTML response (including Cloudflare on 403/503)
			val processedHtml = handleCommonIssuesForContent(html, chapter.url)
			if (processedHtml.isEmpty()) {
				if (!response.isSuccessful) {
					Log.e(TAG, "HTTP request failed: ${httpCode} for content page and couldn't handle the response")
				} else {
					Log.w(TAG, "HTML processing failed for content page")
				}
				return emptyList()
			}
			
			// Parse HTML
			val document = Jsoup.parse(processedHtml, chapter.url)
			val root = document.body()
			
			// Create JavaScript context for content
			val bookInfo = BookInfo(
				bookUrl = chapter.url,
				name = chapter.name
			)
			val chapterInfo = ChapterInfo(
				chapterUrl = chapter.url,
				name = chapter.name ?: "",
				index = chapter.number.toInt()
			)
			val contentContext = JavaScriptContext.forContent(
				book = bookInfo,
				chapter = chapterInfo,
				source = config,
				baseUrl = chapter.url
			)
			
			// Parse content based on source type
			return when (config.bookSourceType) {
				0 -> {
					// Text content (novel)
					Log.d(TAG, "Parsing text content")
					val content = ruleEngine.parseField(root, contentRule.content, contentContext)
					
					if (content.isBlank()) {
						Log.w(TAG, "No content found")
						emptyList()
					} else {
						// For text content, create a single page with the text
						// The text will be displayed in the novel reader
						listOf(
							MangaPage(
								id = generatePageId(chapter.url, 0),
								url = chapter.url,
								preview = null,
								source = source
							)
						)
					}
				}
				2 -> {
					// Image content (manga)
					Log.d(TAG, "Parsing image content")
					
					// Try to parse as a list of images
					val imageElements = document.select(contentRule.content)
					
					if (imageElements.isEmpty()) {
						// Try parsing as a single field
						val imageUrl = ruleEngine.parseField(root, contentRule.content, contentContext)
						if (imageUrl.isNotBlank()) {
							listOf(
								MangaPage(
									id = generatePageId(chapter.url, 0),
									url = resolveUrl(chapter.url, imageUrl),
									preview = null,
									source = source
								)
							)
						} else {
							Log.w(TAG, "No images found")
							emptyList()
						}
					} else {
						// Multiple images found
						imageElements.mapIndexedNotNull { index, element ->
							val imageUrl = element.attr("src").ifBlank {
								element.attr("data-src").ifBlank {
									element.text()
								}
							}
							
							if (imageUrl.isBlank()) {
								Log.w(TAG, "Skipping image element with no URL")
								return@mapIndexedNotNull null
							}
							
							MangaPage(
								id = generatePageId(chapter.url, index),
								url = resolveUrl(chapter.url, imageUrl),
								preview = null,
								source = source
							)
						}
					}
				}
				else -> {
					// Unknown type, try to parse as text
					Log.w(TAG, "Unknown bookSourceType: ${config.bookSourceType}, treating as text")
					val content = ruleEngine.parseField(root, contentRule.content, contentContext)
					
					if (content.isBlank()) {
						emptyList()
					} else {
						listOf(
							MangaPage(
								id = generatePageId(chapter.url, 0),
								url = chapter.url,
								preview = null,
								source = source
							)
						)
					}
				}
			}
			
		} catch (e: Exception) {
			Log.e(TAG, "Error in getPages for ${source.name}", e)
			return emptyList()
		}
	}
	
	/**
	 * Generates a unique page ID from the chapter URL and index
	 */
	private fun generatePageId(chapterUrl: String, index: Int): Long {
		return "$chapterUrl#$index".hashCode().toLong()
	}
	
	/**
	 * Get page URL
	 * TODO: Implement page URL resolution
	 */
	override suspend fun getPageUrl(page: MangaPage): String {
		Log.d(TAG, "getPageUrl called for ${source.name} - returning original URL (not yet implemented)")
		// TODO: Implement URL resolution if needed
		return page.url
	}
	
	/**
	 * Get filter options
	 * TODO: Parse filters from JSON source configuration
	 */
	override suspend fun getFilterOptions(): MangaListFilterOptions {
		Log.d(TAG, "getFilterOptions called for ${source.name} - returning empty options (not yet implemented)")
		// TODO: Parse filters from JSON source configuration
		return MangaListFilterOptions()
	}
	
	/**
	 * Get related manga
	 * TODO: Implement related manga logic
	 */
	override suspend fun getRelated(seed: Manga): List<Manga> {
		Log.d(TAG, "getRelated called for ${source.name} - returning empty list (not yet implemented)")
		// TODO: Implement related manga logic if supported by JSON source
		return emptyList()
	}
}
