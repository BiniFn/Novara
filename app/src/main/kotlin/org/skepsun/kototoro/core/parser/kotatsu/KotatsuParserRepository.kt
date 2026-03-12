package org.skepsun.kototoro.core.parser.kotatsu

import okhttp3.Interceptor
import okhttp3.Response
import org.koitharu.kotatsu.parsers.MangaParser as KTMangaParser
import org.skepsun.kototoro.core.cache.MemoryContentCache
import org.skepsun.kototoro.core.parser.CachingMangaRepository
import org.skepsun.kototoro.parsers.config.ConfigKey
import org.skepsun.kototoro.parsers.model.Favicons
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaListFilter
import org.skepsun.kototoro.parsers.model.MangaListFilterCapabilities
import org.skepsun.kototoro.parsers.model.MangaListFilterOptions
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.SortOrder

class KotatsuParserRepository(
	private val parser: KTMangaParser,
	private val kotatsuSource: KotatsuParserSource,
	private val loaderContext: org.skepsun.kototoro.parsers.MangaLoaderContext,
	cache: MemoryContentCache,
) : CachingMangaRepository(cache), Interceptor {

	override val source: org.skepsun.kototoro.parsers.model.MangaSource
		get() = kotatsuSource

	override val sortOrders: Set<SortOrder> =
		parser.availableSortOrders.map { it.toKototoro() }.toSet()

	override val filterCapabilities: MangaListFilterCapabilities =
		parser.filterCapabilities.toKototoro()

	override var defaultSortOrder: SortOrder
		get() = sortOrders.first()
		set(@Suppress("UNUSED_PARAMETER") value) {}

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> =
		parser.getList(offset, (order ?: sortOrders.first()).toKotatsu(), (filter ?: MangaListFilter.EMPTY).toKotatsu(kotatsuSource))
			.map { it.toKototoro(kotatsuSource) }

	override suspend fun getDetailsImpl(manga: Manga): Manga =
		parser.getDetails(manga.toKotatsu(kotatsuSource)).toKototoro(kotatsuSource)

	override suspend fun getPagesImpl(chapter: MangaChapter, nextChapterUrl: String?): List<MangaPage> =
		parser.getPages(chapter.toKotatsu(kotatsuSource)).map { it.toKototoro(kotatsuSource) }

	override suspend fun getPageUrl(page: MangaPage): String =
		parser.getPageUrl(page.toKotatsu(kotatsuSource))

	override suspend fun getFilterOptions(): MangaListFilterOptions =
		parser.getFilterOptions().toKototoro(kotatsuSource)

	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> =
		parser.getRelatedManga(seed.toKotatsu(kotatsuSource)).map { it.toKototoro(kotatsuSource) }

	suspend fun getFavicons(): Favicons = parser.getFavicons().toKototoro()

	override fun getRequestHeaders(): Map<String, String> {
		val headers = parser.getRequestHeaders()
		val map = mutableMapOf<String, String>()
		for (i in 0 until headers.size) {
			map[headers.name(i)] = headers.value(i)
		}
		return map
	}

	override fun intercept(chain: Interceptor.Chain): Response = parser.intercept(chain)

	fun getConfig() = loaderContext.getConfig(source) as org.skepsun.kototoro.core.prefs.SourceSettings

	var domain: String
		get() = parser.domain
		set(value) {
			getConfig()[parser.configKeyDomain.toKototoro()] = value
		}

	override suspend fun getConfigKeys(): List<ConfigKey<*>> =
		ArrayList<org.koitharu.kotatsu.parsers.config.ConfigKey<*>>().also {
			parser.onCreateConfig(it)
		}.map { it.toKototoro() }
}
