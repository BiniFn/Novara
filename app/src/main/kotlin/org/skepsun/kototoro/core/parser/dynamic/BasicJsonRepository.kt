package org.skepsun.kototoro.core.parser.dynamic

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jsoup.Jsoup
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.core.jsonsource.JsonMangaSource
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.model.jsonsource.SearchRule
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.core.parser.rule.EnhancedRuleEngine
import org.skepsun.kototoro.core.javascript.JavaScriptContext
import org.skepsun.kototoro.core.javascript.BookInfo
import org.skepsun.kototoro.core.javascript.ChapterInfo
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
			
			// Build the search/list URL
			val url = buildListUrl(filter?.query, offset)
			if (url.isBlank()) {
				Log.w(TAG, "No ${if (isSearching) "search" else "explore"} URL configured for ${source.name}")
				return emptyList()
			}
			
			Log.d(TAG, "Fetching list from: $url (using ${if (isSearching) "ruleSearch" else "ruleExplore"})")
			Log.d(TAG, "Using bookList rule: ${rule.bookList}")
			
			// Make HTTP request using LegadoHttpClient (handles User-Agent rotation and Cloudflare)
			// Merge custom headers from source config with default headers
			val headers = mutableMapOf(
				"Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
				"Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8"
			)
			headers.putAll(customHeaders) // Custom headers override defaults
			
			val response = legadoHttpClient.get(url, headers, source = source)
			if (!response.isSuccessful) {
				Log.e(TAG, "HTTP request failed: ${response.code} ${response.message} for URL: $url")
				return emptyList()
			}
			
			val html = response.body?.string() ?: ""
			response.close()
			
			Log.d(TAG, "Received HTML response: ${html.length} bytes")
			
			// Check for common issues and handle them
			val processedHtml = handleCommonIssues(html, url, rule)
			if (processedHtml.isEmpty()) {
				Log.w(TAG, "HTML processing failed or returned empty content")
				return emptyList()
			}
			
			// Parse HTML
			val document = Jsoup.parse(processedHtml, url)
			
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
			searchContext.setVariable("baseUrl", url)
			searchContext.setVariable("url", url)
			
			val items = ruleEngine.parseList(document, bookListRule, itemRules, searchContext)
			Log.d(TAG, "Parsed ${items.size} items from list")
			
			if (items.isEmpty()) {
				Log.w(TAG, "No items found. HTML preview: ${processedHtml.take(500)}")
				// Try alternative parsing strategies
				return tryAlternativeParsing(document, url, rule, searchContext)
			}
			
			// Convert to Manga objects
			return items.mapNotNull { item ->
				val title = item["name"]?.trim()
				val bookUrl = item["bookUrl"]?.trim()
				
				if (title.isNullOrBlank() || bookUrl.isNullOrBlank()) {
					Log.w(TAG, "Skipping item with missing title or URL: $item")
					return@mapNotNull null
				}
				
				val absoluteUrl = resolveUrl(url, bookUrl)
				val coverUrl = item["coverUrl"]?.let { resolveUrl(url, it) }
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
		val baseUrl = config.bookSourceUrl
		
		// If there's a query, use searchUrl
		if (!query.isNullOrBlank()) {
			val searchUrl = config.searchUrl
			if (!searchUrl.isNullOrBlank()) {
				// Check if searchUrl contains JavaScript
				if (searchUrl.startsWith("@js:") || searchUrl.contains("<js>")) {
					// Create JavaScript context for search
					val context = JavaScriptContext.forSearch(
						key = query,
						page = (offset / 20) + 1,
						source = config
					)
					context.setVariable("baseUrl", baseUrl)
					
					// Execute JavaScript to generate URL
					val jsResult = ruleEngine.parseField(
						org.jsoup.Jsoup.parse("").body(),
						searchUrl,
						context
					)
					
					if (jsResult.isNotBlank()) {
						return jsResult
					}
					
					Log.w(TAG, "JavaScript searchUrl returned empty result, falling back to template replacement")
				}
				
				// Standard template replacement
				val encodedQuery = URLEncoder.encode(query, "UTF-8")
				return searchUrl
					.replace("{{key}}", encodedQuery)
					.replace("{key}", encodedQuery)
					.replace("{{page}}", ((offset / 20) + 1).toString())
					.replace("{page}", ((offset / 20) + 1).toString())
			}
		}
		
		// Otherwise use exploreUrl or base URL
		val exploreUrl = config.exploreUrl
		if (!exploreUrl.isNullOrBlank()) {
			// Check if exploreUrl is a JSON array (explore page with categories)
			if (exploreUrl.trim().startsWith("[")) {
				Log.d(TAG, "exploreUrl is a JSON array (explore page), extracting first valid URL")
				try {
					// Parse the JSON array to extract URLs
					val json = Json { ignoreUnknownKeys = true; isLenient = true }
					val exploreItems = json.decodeFromString<List<ExploreItem>>(exploreUrl)
					
					// Find the first item with a non-empty URL
					val firstValidUrl = exploreItems.firstOrNull { !it.url.isNullOrBlank() }?.url
					if (firstValidUrl != null) {
						val fullUrl = if (firstValidUrl.startsWith("http")) {
							firstValidUrl
						} else {
							resolveUrl(baseUrl, firstValidUrl)
						}
						Log.d(TAG, "Using first valid explore URL: $fullUrl")
						return fullUrl
							.replace("{{page}}", ((offset / 20) + 1).toString())
							.replace("{page}", ((offset / 20) + 1).toString())
					} else {
						Log.w(TAG, "No valid URLs found in explore array, using base URL")
						return baseUrl
					}
				} catch (e: Exception) {
					Log.e(TAG, "Failed to parse exploreUrl JSON array: ${e.message}")
					return baseUrl
				}
			} else if (exploreUrl.contains("::")) {
				// Check if exploreUrl is a string with title::url format (newline separated)
				Log.d(TAG, "exploreUrl is a title::url string format, extracting first valid URL")
				try {
					// Split by newlines and parse each line
					val lines = exploreUrl.split("\n", "\r\n")
					for (line in lines) {
						Log.d(TAG, line.trim())
					}
					
					// Find the first line with a valid URL (non-empty after ::)
					val firstValidUrl = lines
						.map { it.trim() }
						.filter { it.contains("::") }
						.map { it.substringAfter("::").trim() }
						.firstOrNull { it.isNotBlank() }
					
					if (firstValidUrl != null) {
						val fullUrl = if (firstValidUrl.startsWith("http")) {
							firstValidUrl
						} else {
							resolveUrl(baseUrl, firstValidUrl)
						}
						Log.d(TAG, "Using first valid explore URL: $fullUrl")
						return fullUrl
							.replace("{{page}}", ((offset / 20) + 1).toString())
							.replace("{page}", ((offset / 20) + 1).toString())
					} else {
						Log.w(TAG, "No valid URLs found in explore string, using base URL")
						return baseUrl
					}
				} catch (e: Exception) {
					Log.e(TAG, "Failed to parse exploreUrl string format: ${e.message}")
					return baseUrl
				}
			} else {
				// Regular URL string
				return exploreUrl
					.replace("{{page}}", ((offset / 20) + 1).toString())
					.replace("{page}", ((offset / 20) + 1).toString())
			}
		}
		
		return baseUrl
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
	private suspend fun handleCommonIssues(html: String, url: String, rule: SearchRule): String {
		// Check for Cloudflare verification
		if (isCloudflareVerification(html)) {
			Log.w(TAG, "Detected Cloudflare verification page for ${source.name}")
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
		
		// Check for common error pages
		if (isErrorPage(html)) {
			Log.w(TAG, "Detected error page for ${source.name}")
			return ""
		}
		
		return html
	}
	
	/**
	 * Check if the HTML contains Cloudflare verification
	 */
	private fun isCloudflareVerification(html: String): Boolean {
		return html.contains("Just a moment...") ||
			html.contains("cloudflare") ||
			html.contains("cf-browser-verification") ||
			html.contains("cf-challenge-form") ||
			html.contains("DDoS protection by Cloudflare")
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
		
		// If no init script or it failed, log the issue and return empty
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
		
		Log.w(TAG, "All alternative parsing strategies failed for ${source.name}")
		return emptyList()
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
		if (isAntiBotProtection(html) || isErrorPage(html)) {
			Log.w(TAG, "Detected protection or error on details page for ${source.name}")
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
		if (isAntiBotProtection(html) || isErrorPage(html)) {
			Log.w(TAG, "Detected protection or error on content page for ${source.name}")
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
			val response = legadoHttpClient.get(manga.url, customHeaders, source = source)
			if (!response.isSuccessful) {
				Log.e(TAG, "HTTP request failed: ${response.code}")
				return manga
			}
			
			val html = response.body?.string() ?: ""
			response.close()
			
			// Handle common issues in the HTML response
			val processedHtml = handleCommonIssuesForDetails(html, manga.url)
			if (processedHtml.isEmpty()) {
				Log.w(TAG, "HTML processing failed for details page")
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
				
				chapterItems.mapIndexedNotNull { index, item ->
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
			val response = legadoHttpClient.get(chapter.url, customHeaders, source = source)
			if (!response.isSuccessful) {
				Log.e(TAG, "HTTP request failed: ${response.code}")
				return emptyList()
			}
			
			val html = response.body?.string() ?: ""
			response.close()
			
			// Handle common issues in the HTML response
			val processedHtml = handleCommonIssuesForContent(html, chapter.url)
			if (processedHtml.isEmpty()) {
				Log.w(TAG, "HTML processing failed for content page")
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
