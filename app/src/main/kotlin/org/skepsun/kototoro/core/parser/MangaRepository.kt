package org.skepsun.kototoro.core.parser

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
import org.skepsun.kototoro.core.network.jsonsource.PersistentCookieJar
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

interface MangaRepository {

	val source: MangaSource

	val sortOrders: Set<SortOrder>

	var defaultSortOrder: SortOrder

	val filterCapabilities: MangaListFilterCapabilities

	suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga>

	suspend fun getDetails(manga: Manga): Manga

	suspend fun getPages(chapter: MangaChapter): List<MangaPage>

	suspend fun getPageUrl(page: MangaPage): String

	suspend fun getFilterOptions(): MangaListFilterOptions

	/**
	 * 可选：返回小说章节的完整 HTML 与图片资源信息，用于离线下载。
	 * 默认实现返回 null（未实现）。
	 */
	suspend fun getChapterContent(chapter: MangaChapter): NovelChapterContent? = null

	suspend fun getRelated(seed: Manga): List<Manga>

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
				
				is org.skepsun.kototoro.core.jsonsource.JsonMangaSource -> {
					when (source.entity.type) {
						org.skepsun.kototoro.core.db.entity.JsonSourceType.LEGADO -> {
							android.util.Log.d("MangaRepository", "Creating BasicJsonRepository for Legado JSON source: ${source.name}")
							// Create BrowserLauncher for Cloudflare handling
							// Wrap the shared cookie jar so BrowserLauncher can sync WebView cookies
							val browserLauncher = org.skepsun.kototoro.core.javascript.BrowserLauncher(
								context = context,
								cookieJar = PersistentCookieJar(legadoHttpClient.getCookieJar())
							)
							org.skepsun.kototoro.core.parser.dynamic.BasicJsonRepository(
								source = source,
								legadoHttpClient = legadoHttpClient,
								ruleEngine = ruleEngine,
								browserLauncher = browserLauncher
							)
					}
					org.skepsun.kototoro.core.db.entity.JsonSourceType.TVBOX -> {
						android.util.Log.w("MangaRepository", "TVBox JSON source is video; no parser available in MangaRepository. Returning EmptyMangaRepository for ${source.name}")
						EmptyMangaRepository(source)
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
