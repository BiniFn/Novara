package org.skepsun.kototoro.core.parser

import android.content.Context
import androidx.annotation.AnyThread
import androidx.collection.ArrayMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.skepsun.kototoro.core.model.LocalMangaSource
import org.skepsun.kototoro.core.model.TestContentSource
import org.skepsun.kototoro.core.model.UnknownContentSource
import org.skepsun.kototoro.local.data.LocalMangaRepository
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentListFilter
import org.skepsun.kototoro.parsers.model.ContentListFilterCapabilities
import org.skepsun.kototoro.parsers.model.ContentListFilterOptions
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.NovelChapterContent
import org.skepsun.kototoro.parsers.model.SortOrder
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

interface ContentRepository {

	enum class ListPagingMode {
		OFFSET,
		PAGE_INDEX,
	}

	val source: ContentSource

	val sortOrders: Set<SortOrder>

	var defaultSortOrder: SortOrder

	val filterCapabilities: ContentListFilterCapabilities

	/**
	 * 列表分页模式：
	 * - OFFSET：`getList(offset)` 的 offset 为“已加载条数”（Kototoro 原语义）。
	 * - PAGE_INDEX：`getList(offset)` 的 offset 视为“页索引(0-based)”，最终请求页号通常为 offset+1（对齐 legado/MD3 的 {{page}}）。
	 *
	 * 默认 OFFSET，避免影响现有解析器/本地库实现。
	 */
	val listPagingMode: ListPagingMode
		get() = ListPagingMode.OFFSET

	suspend fun getList(offset: Int, order: SortOrder?, filter: ContentListFilter?): List<Content>

	suspend fun getDetails(manga: Content): Content

	suspend fun getPages(chapter: ContentChapter, nextChapterUrl: String? = null): List<ContentPage>

	/**
	 * 获取章节页面的流，支持增量加载（如 Legado 多页小说章节）。
	 * 每次 emit 都是目前已获取到的所有页面列表。
	 * @param nextChapterUrl 下一章的 URL。如果加载过程中遇到此 URL，应停止加载，防止“下一章”规则误触导致无限连读。
	 */
	fun getPagesFlow(chapter: ContentChapter, nextChapterUrl: String? = null): Flow<List<ContentPage>> = flow {
		emit(getPages(chapter, nextChapterUrl))
	}

	suspend fun getPageUrl(page: ContentPage): String

	suspend fun getFilterOptions(): ContentListFilterOptions

	/**
	 * 可选：返回小说章节的完整 HTML 与图片资源信息，用于离线下载。
	 * 默认实现返回 null（未实现）。
	 */
	suspend fun getChapterContent(chapter: ContentChapter, nextChapterUrl: String? = null): NovelChapterContent? = null

	/**
	 * Create an OkHttp Request for a specific page.
	 * Default implementation uses PageLoader.createPageRequest with global settings.
	 */
	fun createPageRequest(pageUrl: String, page: ContentPage): okhttp3.Request {
		return org.skepsun.kototoro.reader.domain.PageLoader.createPageRequest(pageUrl, page)
	}

	/**
	 * Create an OkHttp Request for a cover image.
	 */
	fun createCoverRequest(imageUrl: String): okhttp3.Request {
		return org.skepsun.kototoro.reader.domain.PageLoader.createPageRequest(imageUrl, source)
	}

	/**
	 * Returns the request headers for this source (generic headers like UA/Referer).
	 */
	fun getRequestHeaders(): Map<String, String> = emptyMap()

	fun isSlowdownEnabled(): Boolean {
		return source != LocalMangaSource && source != TestContentSource && source != org.skepsun.kototoro.core.model.LocalNovelSource
	}

	suspend fun getRelated(seed: Content): List<Content>

	suspend fun getConfigKeys(): List<org.skepsun.kototoro.parsers.config.ConfigKey<*>> = emptyList()

	suspend fun find(manga: Content): Content? {
		val list = getList(0, SortOrder.RELEVANCE, ContentListFilter(query = manga.title))
		return list.find { x -> x.id == manga.id }
	}

	@Singleton
	class Factory @Inject constructor(
		@ApplicationContext @Suppress("unused") private val context: Context,
		private val localContentRepository: LocalMangaRepository,
		private val localNovelRepository: org.skepsun.kototoro.local.novel.LocalNovelRepository,
		private val contentSourceInfoResolver: ContentSourceInfoResolver,
		private val jsonContentSourceResolver: JsonContentSourceResolver,
		private val mihonContentSourceResolver: MihonContentSourceResolver,
		private val aniyomiContentSourceResolver: AniyomiContentSourceResolver,
		private val parserContentRepositoryProvider: ParserContentRepositoryProvider,
		private val kotatsuContentRepositoryProvider: KotatsuContentRepositoryProvider,
		private val testContentRepositoryProvider: TestContentRepositoryProvider,
		private val externalContentRepositoryProvider: ExternalContentRepositoryProvider,
		private val mihonContentRepositoryProvider: MihonContentRepositoryProvider,
		private val aniyomiContentRepositoryProvider: AniyomiContentRepositoryProvider,
		private val jsonContentRepositoryProvider: JsonContentRepositoryProvider,
	) {

		private val cache = ArrayMap<ContentSource, WeakReference<ContentRepository>>()
		private val contentSourceResolvers: List<ContentSourceResolver> by lazy(LazyThreadSafetyMode.NONE) {
			listOf(
				contentSourceInfoResolver,
				jsonContentSourceResolver,
				mihonContentSourceResolver,
				aniyomiContentSourceResolver,
			)
		}
		private val contentRepositoryProviders: List<ContentRepositoryProvider> by lazy(LazyThreadSafetyMode.NONE) {
			listOf(
				parserContentRepositoryProvider,
				kotatsuContentRepositoryProvider,
				testContentRepositoryProvider,
				externalContentRepositoryProvider,
				mihonContentRepositoryProvider,
				aniyomiContentRepositoryProvider,
				jsonContentRepositoryProvider,
			)
		}

		@AnyThread
		fun create(source: ContentSource): ContentRepository {
			android.util.Log.d("ContentRepoFactory", "create() called with source: ${source.name}, type: ${source::class.simpleName}")
			val resolvedSource = resolveContentSource(source)
			android.util.Log.d("ContentRepoFactory", "Resolved source: ${resolvedSource.name}, type: ${resolvedSource::class.simpleName}")
			when (resolvedSource) {
				LocalMangaSource -> return localContentRepository
				org.skepsun.kototoro.core.model.LocalNovelSource -> return localNovelRepository
				UnknownContentSource -> return EmptyContentRepository(resolvedSource)
			}
			cache[resolvedSource]?.get()?.let { return it }
			return synchronized(cache) {
				cache[resolvedSource]?.get()?.let { return it }
				android.util.Log.d("ContentRepoFactory", "Trying providers for source: ${resolvedSource.name}")
				val repository = contentRepositoryProviders.firstNotNullOfOrNull {
					android.util.Log.d("ContentRepoFactory", "Trying provider: ${it::class.simpleName}")
					it.create(resolvedSource)
				}
				if (repository != null) {
					android.util.Log.d("ContentRepoFactory", "Repository created: ${repository::class.simpleName}")
					cache[resolvedSource] = WeakReference(repository)
					repository
				} else {
					android.util.Log.w("ContentRepoFactory", "No provider could create repository, returning EmptyContentRepository")
					EmptyContentRepository(resolvedSource)
				}
			}
		}

		private fun resolveContentSource(source: ContentSource): ContentSource {
			var current = source
			while (true) {
				val next = contentSourceResolvers.firstNotNullOfOrNull { it.resolve(current) } ?: return current
				if (next === current) return current
				current = next
			}
		}
	}
}
