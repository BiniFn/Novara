package org.skepsun.kototoro.core.lnreader

import android.content.Context
import android.util.Log
import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.skepsun.kototoro.core.jsonsource.JsonContentSource
import org.skepsun.kototoro.core.parser.ContentRepository
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentListFilter
import org.skepsun.kototoro.parsers.model.ContentListFilterCapabilities
import org.skepsun.kototoro.parsers.model.ContentListFilterOptions
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.NovelChapterContent
import org.skepsun.kototoro.parsers.model.SortOrder
import java.util.EnumSet

/**
 * ContentRepository implementation for LNReader JS plugins.
 * 
 * Maps LNReaderPluginBridge output → Kototoro's Content/ContentChapter/ContentPage models.
 * Each content operation creates a fresh QuickJS context (matches IReader pattern of
 * one engine per operation for isolation/memory safety).
 */
class LNReaderContentRepository(
	override val source: ContentSource,
	private val appContext: Context,
	private val httpClient: OkHttpClient
) : ContentRepository {
	
	companion object {
		private const val TAG = "LNReaderContentRepo"
		private const val CHAPTER_SEPARATOR = "|||"
	}
	
	private val jsContent: String
		get() = (source as? JsonContentSource)?.entity?.config ?: ""
	
	private val pluginId: String
		get() = (source as? JsonContentSource)?.entity?.id ?: source.name
	
	override val sortOrders: Set<SortOrder> = EnumSet.of(SortOrder.POPULARITY, SortOrder.NEWEST)
	override var defaultSortOrder: SortOrder = SortOrder.POPULARITY
	
	override val filterCapabilities: ContentListFilterCapabilities = ContentListFilterCapabilities(
		isMultipleTagsSupported = false,
	)
	
	override val listPagingMode: ContentRepository.ListPagingMode
		get() = ContentRepository.ListPagingMode.PAGE_INDEX
	
	// ==================== Content Operations ====================
	
	override suspend fun getList(offset: Int, order: SortOrder?, filter: ContentListFilter?): List<Content> {
		val page = offset + 1 // LNReader uses 1-based pages
		
		return try {
			val query = filter?.query
			if (!query.isNullOrBlank()) {
				// Search mode
				executeInPluginContext { bridge ->
					bridge.searchNovels(query, page).map { it.toContent() }
				}
			} else {
				// Browse mode
				executeInPluginContext { bridge ->
					bridge.popularNovels(page).map { it.toContent() }
				}
			}
		} catch (e: Exception) {
			Log.e(TAG, "getList failed for ${source.name}", e)
			emptyList()
		}
	}
	
	override suspend fun getDetails(manga: Content): Content {
		val novelPath = manga.url ?: manga.publicUrl ?: return manga
		
		return try {
			executeInPluginContext { bridge ->
				val details = bridge.parseNovel(novelPath)
				manga.copy(
					title = details.name.ifBlank { manga.title },
					coverUrl = details.cover.ifBlank { manga.coverUrl },
					largeCoverUrl = details.cover.ifBlank { manga.largeCoverUrl },
					description = details.summary.ifBlank { manga.description },
					tags = if (details.genres.isNotEmpty()) {
						details.genres.map { org.skepsun.kototoro.parsers.model.ContentTag(it, it, source) }.toSet()
					} else manga.tags,
					authors = if (details.author.isNotBlank()) setOf(details.author) else manga.authors,
					publicUrl = details.path.ifBlank { manga.publicUrl },
					chapters = details.chapters.mapIndexed { index, ch ->
						ContentChapter(
							id = (ch.path.hashCode().toLong() and 0x7FFFFFFF) + index,
							title = ch.name,
							number = (index + 1).toFloat(),
							volume = 0,
							url = "${novelPath}${CHAPTER_SEPARATOR}${ch.path}",
							scanlator = ch.releaseTime,
							uploadDate = 0,
							branch = null,
							source = source,
						)
					}
				)
			}
		} catch (e: Exception) {
			Log.e(TAG, "getDetails failed for ${source.name}", e)
			manga
		}
	}
	
	override suspend fun getPages(chapter: ContentChapter, nextChapterUrl: String?): List<ContentPage> {
		// LNReader chapters return HTML text, not page images
		// We create a single "page" containing the HTML content
		val parts = chapter.url.split(CHAPTER_SEPARATOR, limit = 2)
		val chapterPath = if (parts.size == 2) parts[1] else chapter.url
		
		return try {
			executeInPluginContext { bridge ->
				val htmlContent = bridge.parseChapter(chapterPath)
				if (htmlContent.isNotBlank()) {
					listOf(
						ContentPage(
							id = chapter.id,
							url = "lnreader://chapter/${chapterPath}",
							preview = null,
							source = source
						)
					)
				} else {
					emptyList()
				}
			}
		} catch (e: Exception) {
			Log.e(TAG, "getPages failed for ${source.name}", e)
			emptyList()
		}
	}
	
	/**
	 * Get chapter content as novel HTML.
	 * This is the key method for novel reading — returns HTML text.
	 */
	override suspend fun getChapterContent(chapter: ContentChapter, nextChapterUrl: String?): NovelChapterContent? {
		val parts = chapter.url.split(CHAPTER_SEPARATOR, limit = 2)
		val chapterPath = if (parts.size == 2) parts[1] else chapter.url
		
		return try {
			executeInPluginContext { bridge ->
				val htmlContent = bridge.parseChapter(chapterPath)
				if (htmlContent.isNotBlank()) {
					NovelChapterContent(
						html = htmlContent,
						images = emptyList(),
					)
				} else {
					null
				}
			}
		} catch (e: Exception) {
			Log.e(TAG, "getChapterContent failed for ${source.name}", e)
			null
		}
	}
	
	override suspend fun getPageUrl(page: ContentPage): String = page.url
	
	override suspend fun getFilterOptions(): ContentListFilterOptions = ContentListFilterOptions()
	
	override suspend fun getRelated(seed: Content): List<Content> = emptyList()
	
	// ==================== Internal ====================
	
	/**
	 * Execute a block within a fresh plugin context.
	 * Creates QuickJS engine → loads plugin → runs block → disposes.
	 */
	private suspend fun <T> executeInPluginContext(block: suspend (LNReaderPluginBridge) -> T): T {
		val fetchBridge = LNReaderFetchBridge(httpClient, pluginId)
		val engine = LNReaderEngine(appContext, fetchBridge)
		val qjs = engine.createPluginContext(jsContent, pluginId)
		
		return try {
			val bridge = LNReaderPluginBridge(qjs, pluginId)
			block(bridge)
		} finally {
			qjs.close()
		}
	}
	
	/**
	 * Convert LNReader novel item to Kototoro Content model.
	 */
	private fun LNReaderNovelItem.toContent(): Content {
		return Content(
			id = path.hashCode().toLong() and 0x7FFFFFFF,
			title = name,
			altTitles = emptySet(),
			url = path,
			publicUrl = path,
			rating = 0f,
			contentRating = null,
			coverUrl = cover.ifBlank { null },
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			description = null,
			source = source
		)
	}
}
