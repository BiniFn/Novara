package org.skepsun.kototoro.core.parser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

import android.content.Context
import androidx.annotation.AnyThread
import androidx.collection.ArrayMap
import dagger.hilt.android.qualifiers.ApplicationContext
import org.skepsun.kototoro.core.cache.MemoryContentCache
import org.skepsun.kototoro.core.model.LocalMangaSource
import org.skepsun.kototoro.core.model.MangaSourceInfo
import org.skepsun.kototoro.core.model.TestMangaSource
import org.skepsun.kototoro.core.model.UnknownMangaSource
import org.skepsun.kototoro.core.parser.external.ExternalMangaRepository
import org.skepsun.kototoro.core.parser.external.ExternalMangaSource
import org.skepsun.kototoro.local.data.LocalMangaRepository
import org.skepsun.kototoro.mihon.MihonExtensionManager
import org.skepsun.kototoro.mihon.MihonMangaRepository
import org.skepsun.kototoro.mihon.model.MihonMangaSource
import org.skepsun.kototoro.parsers.MangaLoaderContext
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaListFilter
import org.skepsun.kototoro.parsers.model.MangaListFilterCapabilities
import org.skepsun.kototoro.parsers.model.MangaListFilterOptions
import org.skepsun.kototoro.parsers.model.NovelChapterContent
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.MangaParserSource
import org.skepsun.kototoro.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.model.SortOrder
import org.skepsun.kototoro.core.parser.kotatsu.KotatsuParsersProvider
import org.skepsun.kototoro.core.parser.kotatsu.KotatsuParserSource
import org.skepsun.kototoro.core.parser.kotatsu.KotatsuParserRepository
import org.skepsun.kototoro.core.network.jsonsource.PersistentCookieJar
import org.skepsun.kototoro.video.data.VideoLocalCacheProxy
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

interface MangaRepository {

	enum class ListPagingMode {
		OFFSET,
		PAGE_INDEX,
	}

	val source: MangaSource

	val sortOrders: Set<SortOrder>

	var defaultSortOrder: SortOrder

	val filterCapabilities: MangaListFilterCapabilities

	/**
	 * 列表分页模式：
	 * - OFFSET：`getList(offset)` 的 offset 为“已加载条数”（Kototoro 原语义）。
	 * - PAGE_INDEX：`getList(offset)` 的 offset 视为“页索引(0-based)”，最终请求页号通常为 offset+1（对齐 legado/MD3 的 {{page}}）。
	 *
	 * 默认 OFFSET，避免影响现有解析器/本地库实现。
	 */
	val listPagingMode: ListPagingMode
		get() = ListPagingMode.OFFSET

	suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga>

	suspend fun getDetails(manga: Manga): Manga

	suspend fun getPages(chapter: MangaChapter, nextChapterUrl: String? = null): List<MangaPage>

	/**
	 * 获取章节页面的流，支持增量加载（如 Legado 多页小说章节）。
	 * 每次 emit 都是目前已获取到的所有页面列表。
	 * @param nextChapterUrl 下一章的 URL。如果加载过程中遇到此 URL，应停止加载，防止“下一章”规则误触导致无限连读。
	 */
	fun getPagesFlow(chapter: MangaChapter, nextChapterUrl: String? = null): Flow<List<MangaPage>> = flow {
		emit(getPages(chapter, nextChapterUrl))
	}

	suspend fun getPageUrl(page: MangaPage): String

	suspend fun getFilterOptions(): MangaListFilterOptions

	/**
	 * 可选：返回小说章节的完整 HTML 与图片资源信息，用于离线下载。
	 * 默认实现返回 null（未实现）。
	 */
	suspend fun getChapterContent(chapter: MangaChapter, nextChapterUrl: String? = null): NovelChapterContent? = null

	/**
	 * Create an OkHttp Request for a specific page.
	 * Default implementation uses PageLoader.createPageRequest with global settings.
	 */
	fun createPageRequest(pageUrl: String, page: MangaPage): okhttp3.Request {
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
		return source != LocalMangaSource && source != TestMangaSource && source != org.skepsun.kototoro.core.model.LocalNovelSource
	}

	suspend fun getRelated(seed: Manga): List<Manga>

	suspend fun getConfigKeys(): List<org.skepsun.kototoro.parsers.config.ConfigKey<*>> = emptyList()

	suspend fun find(manga: Manga): Manga? {
		val list = getList(0, SortOrder.RELEVANCE, MangaListFilter(query = manga.title))
		return list.find { x -> x.id == manga.id }
	}

	@Singleton
	class Factory @Inject constructor(
		@ApplicationContext private val context: Context,
		private val localMangaRepository: LocalMangaRepository,
		private val localNovelRepository: org.skepsun.kototoro.local.novel.LocalNovelRepository,
		private val loaderContext: MangaLoaderContext,
		private val contentCache: MemoryContentCache,
		private val mirrorSwitcher: MirrorSwitcher,
		private val jsonSourceManager: org.skepsun.kototoro.core.jsonsource.JsonSourceManager,
		private val ruleEngine: org.skepsun.kototoro.core.parser.rule.EnhancedRuleEngine,
		private val legadoHttpClient: org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient,
		private val jsEngine: org.skepsun.kototoro.core.javascript.JavaScriptEngine,
		private val mihonExtensionManager: MihonExtensionManager,
		private val aniyomiExtensionManager: org.skepsun.kototoro.aniyomi.AniyomiExtensionManager,
		private val videoLocalCacheProxy: VideoLocalCacheProxy,
	) {

		private val cache = ArrayMap<MangaSource, WeakReference<MangaRepository>>()

		@AnyThread
		fun create(source: MangaSource): MangaRepository {
			android.util.Log.d("MangaRepository", "create() called with source: ${source.javaClass.simpleName} - ${source.name}")
			
			// Check if this is a JSON source (by name prefix) that needs to be resolved
			if (source.name.startsWith("JSON_") && source !is org.skepsun.kototoro.core.jsonsource.JsonMangaSource) {
				android.util.Log.d("MangaRepository", "Detected JSON source by name: ${source.name}, attempting to resolve from database")
				// Try to load the JSON source from database
				val jsonSource = kotlinx.coroutines.runBlocking {
					jsonSourceManager.getById(source.name)
				}
				if (jsonSource != null) {
					android.util.Log.d("MangaRepository", "Successfully resolved JSON source from database: ${jsonSource.name}")
					// Create JsonMangaSource and recursively call create
					val resolvedSource = org.skepsun.kototoro.core.jsonsource.JsonMangaSource(jsonSource)
					return create(resolvedSource)
				} else {
					android.util.Log.w("MangaRepository", "JSON source not found in database: ${source.name}")
					return EmptyMangaRepository(source)
				}
			}
			
			// Check if this is a Mihon source (by name prefix) that needs to be resolved
			if (source.name.startsWith("MIHON_") && source !is org.skepsun.kototoro.mihon.model.MihonMangaSource) {
				// Don't log on every request to avoid flickering and log spam
				val mihonSource = mihonExtensionManager.getMihonMangaSourceByName(source.name)
				if (mihonSource != null) {
					return create(mihonSource)
				}
			}

			// Check if this is an Aniyomi source (by name prefix) that needs to be resolved
			if (source.name.startsWith("ANIYOMI_") && source !is org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource) {
				val aniyomiSource = aniyomiExtensionManager.getAniyomiAnimeSourceByName(source.name)
				if (aniyomiSource != null) {
					return create(aniyomiSource)
				}
			}
			
			when (source) {
				is MangaSourceInfo -> {
					android.util.Log.d("MangaRepository", "Source is MangaSourceInfo, unwrapping to: ${source.mangaSource.javaClass.simpleName}")
					return create(source.mangaSource)
				}
				LocalMangaSource -> return localMangaRepository
				org.skepsun.kototoro.core.model.LocalNovelSource -> return localNovelRepository
				UnknownMangaSource -> return EmptyMangaRepository(source)
			}
			cache[source]?.get()?.let { return it }
			return synchronized(cache) {
				cache[source]?.get()?.let { return it }
				val repository = createRepository(source)
				if (repository != null) {
					android.util.Log.d("MangaRepository", "Created repository: ${repository.javaClass.simpleName} for source: ${source.name}")
					cache[source] = WeakReference(repository)
					repository
				} else {
					android.util.Log.w("MangaRepository", "createRepository returned null for source: ${source.javaClass.simpleName} - ${source.name}, using EmptyMangaRepository")
					EmptyMangaRepository(source)
				}
			}
		}

		private fun createRepository(source: MangaSource): MangaRepository? {
			android.util.Log.d("MangaRepository", "createRepository() called with source: ${source.javaClass.simpleName} - ${source.name}")
			return when (source) {
				is MangaParserSource -> {
					android.util.Log.d("MangaRepository", "Creating ParserMangaRepository for: ${source.name}")
					ParserMangaRepository(
						parser = loaderContext.newParserInstance(source),
						cache = contentCache,
						mirrorSwitcher = mirrorSwitcher,
					)
				}
				is KotatsuParserSource -> {
					android.util.Log.d("MangaRepository", "Creating Kotatsu Parser repository for: ${source.name}")
					KotatsuParserRepository(
						parser = KotatsuParsersProvider.newParserInstance(loaderContext, source),
						kotatsuSource = source,
						loaderContext = loaderContext,
						cache = contentCache,
					)
				}

				TestMangaSource -> {
					android.util.Log.d("MangaRepository", "Creating TestMangaRepository")
					TestMangaRepository(
						loaderContext = loaderContext,
						cache = contentCache,
					)
				}

				is ExternalMangaSource -> if (source.isAvailable(context)) {
					android.util.Log.d("MangaRepository", "Creating ExternalMangaRepository for: ${source.name}")
					ExternalMangaRepository(
						contentResolver = context.contentResolver,
						source = source,
						cache = contentCache,
					)
				} else {
					android.util.Log.w("MangaRepository", "ExternalMangaSource not available: ${source.name}")
					EmptyMangaRepository(source)
				}
				
				is MihonMangaSource -> {
					android.util.Log.d("MangaRepository", "Creating MihonMangaRepository for: ${source.displayName}")
					MihonMangaRepository(
						source = source,
						cache = contentCache,
					)
				}

				is org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource -> {
					android.util.Log.d("MangaRepository", "Creating AniyomiAnimeRepository for: ${source.displayName}")
					org.skepsun.kototoro.aniyomi.AniyomiAnimeRepository(
						source = source,
						cache = contentCache,
					)
				}
				
				is org.skepsun.kototoro.core.jsonsource.JsonMangaSource -> {
					when (source.entity.type) {
						org.skepsun.kototoro.core.db.entity.JsonSourceType.LEGADO -> {
							android.util.Log.d("MangaRepository", "Creating LegadoRepository for JSON source: ${source.name}")
							// Create BrowserLauncher for Cloudflare handling
							// Wrap the shared cookie jar so BrowserLauncher can sync WebView cookies
							val browserLauncher = org.skepsun.kototoro.core.javascript.BrowserLauncher(
								context = context,
								cookieJar = PersistentCookieJar(legadoHttpClient.getCookieJar())
							)
							org.skepsun.kototoro.core.parser.legado.LegadoRepository(
								source = source,
								httpClient = legadoHttpClient,
								jsEngine = jsEngine,
								memoryCache = contentCache,
								browserLauncher = browserLauncher
							)
					}
					org.skepsun.kototoro.core.db.entity.JsonSourceType.TVBOX -> {
						android.util.Log.d("MangaRepository", "Creating TVBoxRepository for JSON source: ${source.name}")
						org.skepsun.kototoro.core.parser.tvbox.TVBoxRepository(
							source = source,
							context = context,
							httpClient = legadoHttpClient,
							videoLocalCacheProxy = videoLocalCacheProxy,
						)
					}
					org.skepsun.kototoro.core.db.entity.JsonSourceType.JS -> {
						android.util.Log.d("MangaRepository", "Creating JsMangaRepository for JS source: ${source.name}")
						JsMangaRepository(source, loaderContext as MangaLoaderContextImpl)
					}
				}
			}

				else -> {
					android.util.Log.w("MangaRepository", "No repository type matched for source: ${source.javaClass.simpleName} - ${source.name}")
					null
				}
			}
		}
	}
}
