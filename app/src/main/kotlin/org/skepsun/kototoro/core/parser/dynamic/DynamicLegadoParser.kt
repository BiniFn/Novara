package org.skepsun.kototoro.core.parser.dynamic

import org.jsoup.Jsoup
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.parser.rule.RuleEngine
import org.skepsun.kototoro.parsers.MangaLoaderContext
import org.skepsun.kototoro.parsers.config.ConfigKey
import org.skepsun.kototoro.parsers.core.PagedMangaParser
import org.skepsun.kototoro.parsers.model.*
import org.skepsun.kototoro.parsers.util.*
import java.util.*

/**
 * Dynamic parser implementation for Legado book sources
 * 
 * This parser interprets Legado JSON configurations at runtime to parse manga/novel content.
 * It uses the RuleEngine to execute CSS selectors and regex patterns defined in the configuration.
 */
// TODO: DynamicLegadoParser needs to be refactored to not extend PagedMangaParser
// since PagedMangaParser requires MangaParserSource enum which cannot be extended.
// For now, this class is commented out until we implement MangaParser directly.
/*
class DynamicLegadoParser(
	context: MangaLoaderContext,
	private val legadoConfig: LegadoBookSource,
	private val sourceId: String,
	private val ruleEngine: RuleEngine,
) : PagedMangaParser(
	context,
	DynamicMangaSource(sourceId, legadoConfig.bookSourceName),
	pageSize = 20
) {
	
	override val configKeyDomain: ConfigKey.Domain
		get() {
			// Extract domain from bookSourceUrl
			val url = try {
				java.net.URI(config.bookSourceUrl)
			} catch (e: Exception) {
				return ConfigKey.Domain("example.com")
			}
			return ConfigKey.Domain(url.host ?: "example.com")
		}
	
	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.POPULARITY,
	)
	
	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = !config.searchUrl.isNullOrBlank(),
			isMultipleTagsSupported = false,
			isTagsExclusionSupported = false,
		)
	
	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = emptySet(),
		)
	}
	
	/**
	 * Get a page of manga list
	 * 
	 * Uses ruleSearch to parse search results or explore page
	 */
	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val searchRule = config.ruleSearch ?: return emptyList()
		
		// Build URL
		val url = buildSearchUrl(filter.query, page)
		if (url.isNullOrBlank()) {
			return emptyList()
		}
		
		return try {
			// Fetch HTML
			val html = webClient.httpGet(url).parseHtml()
			
			// Parse list using rule engine
			val itemRules = mapOf(
				"name" to searchRule.name,
				"author" to (searchRule.author ?: ""),
				"coverUrl" to (searchRule.coverUrl ?: ""),
				"bookUrl" to searchRule.bookUrl,
				"intro" to (searchRule.intro ?: ""),
			)
			
			val items = ruleEngine.parseList(html, searchRule.bookList, itemRules)
			
			// Convert to Manga objects
			items.mapNotNull { item ->
				val name = item["name"] ?: return@mapNotNull null
				val bookUrl = item["bookUrl"] ?: return@mapNotNull null
				
				// Convert relative URL to absolute
				val absoluteUrl = bookUrl.toAbsoluteUrl(domain)
				
				Manga(
					id = generateUid(absoluteUrl),
					url = absoluteUrl,
					publicUrl = absoluteUrl,
					title = name,
					altTitles = emptySet(),
					coverUrl = item["coverUrl"]?.toAbsoluteUrl(domain) ?: "",
					largeCoverUrl = null,
					authors = item["author"]?.let { setOf(it) } ?: emptySet(),
					tags = emptySet(),
					description = item["intro"],
					rating = RATING_UNKNOWN,
					contentRating = null,
					state = null,
					source = source,
				)
			}
		} catch (e: Exception) {
			// Log error but don't crash
			DynamicParserLogger.logListPageError(sourceId, url, searchRule.bookList, e)
			emptyList()
		}
	}
	
	/**
	 * Get manga details
	 * 
	 * Uses ruleBookInfo and ruleToc to parse book details and chapter list
	 */
	override suspend fun getDetails(manga: Manga): Manga {
		return try {
			// Fetch HTML
			val html = webClient.httpGet(manga.url).parseHtml()
			
			// Parse book info
			val bookInfoRule = config.ruleBookInfo
			val title = if (bookInfoRule?.name != null) {
				ruleEngine.parseField(html.body(), bookInfoRule.name)
			} else {
				manga.title
			}
			
			val author = if (bookInfoRule?.author != null) {
				ruleEngine.parseField(html.body(), bookInfoRule.author)
			} else {
				null
			}
			
			val coverUrl = if (bookInfoRule?.coverUrl != null) {
				ruleEngine.parseField(html.body(), bookInfoRule.coverUrl).toAbsoluteUrl(domain)
			} else {
				manga.coverUrl
			}
			
			val intro = if (bookInfoRule?.intro != null) {
				ruleEngine.parseField(html.body(), bookInfoRule.intro)
			} else {
				manga.description
			}
			
			val kind = if (bookInfoRule?.kind != null) {
				ruleEngine.parseField(html.body(), bookInfoRule.kind)
			} else {
				null
			}
			
			// Parse chapters
			val chapters = parseChapters(html)
			
			// Build tags
			val tags = if (!kind.isNullOrBlank()) {
				setOf(MangaTag(kind, kind, source))
			} else {
				emptySet()
			}
			
			manga.copy(
				title = title?.ifBlank { manga.title } ?: manga.title,
				authors = if (!author.isNullOrBlank()) setOf(author) else manga.authors,
				coverUrl = coverUrl?.ifBlank { manga.coverUrl } ?: manga.coverUrl,
				largeCoverUrl = coverUrl?.ifBlank { manga.coverUrl } ?: manga.coverUrl,
				description = intro,
				tags = tags,
				chapters = chapters,
			)
		} catch (e: Exception) {
			// Log error but return original manga
			DynamicParserLogger.logDetailsError(sourceId, manga.url, null, e)
			manga
		}
	}
	
	/**
	 * Get pages for a chapter
	 * 
	 * Uses ruleContent to parse chapter content
	 */
	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val contentRule = config.ruleContent ?: return emptyList()
		
		return try {
			// Fetch HTML
			val html = webClient.httpGet(chapter.url).parseHtml()
			
			// Parse content
			val content = ruleEngine.parseField(html.body(), contentRule.content)
			
			if (content.isBlank()) {
				return listOf(createErrorPage("章节内容为空"))
			}
			
			// Build HTML page
			val pageHtml = buildChapterHtml(chapter.title ?: "Chapter", content)
			val dataUrl = pageHtml.toDataUrl()
			
			listOf(
				MangaPage(
					id = generateUid(chapter.url),
					url = dataUrl,
					preview = null,
					source = source,
				)
			)
		} catch (e: Exception) {
			// Log error and return error page
			DynamicParserLogger.logContentError(sourceId, chapter.url, contentRule.content, e)
			listOf(createErrorPage("加载失败: ${e.message}"))
		}
	}
	
	override suspend fun getPageUrl(page: MangaPage): String {
		return page.url
	}
	
	// ========== Helper Methods ==========
	
	/**
	 * Build search URL from query and page number
	 */
	private fun buildSearchUrl(query: String?, page: Int): String? {
		val baseUrl = if (!query.isNullOrBlank() && !config.searchUrl.isNullOrBlank()) {
			config.searchUrl
		} else if (!config.exploreUrl.isNullOrBlank()) {
			config.exploreUrl
		} else {
			return null
		}
		
		// Replace placeholders
		var url = baseUrl
			.replace("{{key}}", query?.urlEncoded() ?: "")
			.replace("{{page}}", page.toString())
			.replace("{key}", query?.urlEncoded() ?: "")
			.replace("{page}", page.toString())
		
		// Convert to absolute URL
		url = url.toAbsoluteUrl(domain)
		
		return url
	}
	
	/**
	 * Parse chapters from HTML document
	 */
	private fun parseChapters(html: org.jsoup.nodes.Document): List<MangaChapter> {
		val tocRule = config.ruleToc ?: return emptyList()
		
		return try {
			val itemRules = mapOf(
				"chapterName" to tocRule.chapterName,
				"chapterUrl" to tocRule.chapterUrl,
			)
			
			val items = ruleEngine.parseList(html, tocRule.chapterList, itemRules)
			
			items.mapIndexedNotNull { index, item ->
				val name = item["chapterName"] ?: return@mapIndexedNotNull null
				val url = item["chapterUrl"] ?: return@mapIndexedNotNull null
				
				// Convert relative URL to absolute
				val absoluteUrl = url.toAbsoluteUrl(domain)
				
				MangaChapter(
					id = generateUid(absoluteUrl),
					title = name,
					number = (index + 1).toFloat(),
					volume = 0,
					url = absoluteUrl,
					scanlator = null,
					uploadDate = 0,
					branch = null,
					source = source,
				)
			}
		} catch (e: Exception) {
			DynamicParserLogger.logChapterError(sourceId, "", tocRule.chapterList, e)
			emptyList()
		}
	}
	
	/**
	 * Build HTML for chapter content
	 */
	private fun buildChapterHtml(title: String, content: String): String {
		return buildString {
			append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\"/>\n")
			append("<style>\n")
			append("body{font-family:\"Noto Serif SC\",\"PingFang SC\",sans-serif;")
			append("padding:16px;margin:0;line-height:1.9;font-size:1.05rem;}\n")
			append("h1{font-size:1.3rem;margin-bottom:1rem;}\n")
			append("p{margin:0 0 1rem;text-indent:2em;display:block;}\n")
			append("</style>\n</head>\n<body>\n")
			append("<h1>").append(title).append("</h1>\n")
			
			// Split content into paragraphs
			val paragraphs = content.split("\n")
			for (para in paragraphs) {
				val trimmed = para.trim()
				if (trimmed.isNotEmpty()) {
					append("<p>").append(trimmed).append("</p>\n")
				}
			}
			
			append("</body>\n</html>")
		}
	}
	
	/**
	 * Create error page
	 */
	private fun createErrorPage(message: String): MangaPage {
		val html = """
			<!DOCTYPE html><html><head><meta charset="utf-8"/>
			<style>body{font-family:sans-serif;padding:16px;}</style>
			</head><body><h1>错误</h1><p>$message</p></body></html>
		""".trimIndent()
		
		return MangaPage(
			id = generateUid(message),
			url = html.toDataUrl(),
			preview = null,
			source = source,
		)
	}
	
	/**
	 * Convert HTML to Data URL
	 */
	private fun String.toDataUrl(): String {
		val encoded = context.encodeBase64(toByteArray(Charsets.UTF_8))
		return "data:text/html;charset=utf-8;base64,$encoded"
	}
}
*/

/**
 * Dynamic manga source implementation
 * 
 * This is a simple implementation of MangaSource for dynamic sources.
 * Since MangaParserSource is generated by codegen, we create a simple wrapper.
 */
data class DynamicMangaSource(
	private val sourceId: String,
	override val name: String,
) : org.skepsun.kototoro.parsers.model.MangaSource {
	
	override fun toString(): String = sourceId
}
