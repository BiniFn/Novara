package org.skepsun.kototoro.core.parser

import kotlinx.coroutines.Dispatchers
import okhttp3.Interceptor
import okhttp3.Response
import org.skepsun.kototoro.core.cache.MemoryContentCache
import org.skepsun.kototoro.core.exceptions.CloudFlareProtectedException
import org.skepsun.kototoro.core.exceptions.InteractiveActionRequiredException
import org.skepsun.kototoro.core.exceptions.ProxyConfigException
import org.skepsun.kototoro.core.prefs.SourceSettings
import org.skepsun.kototoro.parsers.MangaParser
import org.skepsun.kototoro.parsers.CategorizedFavoritesProvider
import org.skepsun.kototoro.parsers.FavoritesProvider
import org.skepsun.kototoro.parsers.FavoritesSyncProvider
import org.skepsun.kototoro.parsers.MangaParserAuthProvider
import org.skepsun.kototoro.parsers.config.ConfigKey
import org.skepsun.kototoro.parsers.exception.AuthRequiredException
import org.skepsun.kototoro.parsers.model.Favicons
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaListFilter
import org.skepsun.kototoro.parsers.model.MangaListFilterCapabilities
import org.skepsun.kototoro.parsers.model.MangaListFilterOptions
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.MangaParserSource
import org.skepsun.kototoro.parsers.model.SortOrder
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import org.skepsun.kototoro.parsers.util.suspendlazy.suspendLazy

class ParserMangaRepository(
	private val parser: MangaParser,
	private val mirrorSwitcher: MirrorSwitcher,
	cache: MemoryContentCache,
) : CachingMangaRepository(cache), Interceptor {

	private val filterOptionsLazy = suspendLazy(Dispatchers.Default) {
		withMirrors {
			parser.getFilterOptions()
		}
	}

	override val source: MangaParserSource
		get() = parser.source

	override val sortOrders: Set<SortOrder>
		get() = parser.availableSortOrders

	override val filterCapabilities: MangaListFilterCapabilities
		get() = parser.filterCapabilities

	override var defaultSortOrder: SortOrder
		get() = getConfig().defaultSortOrder ?: sortOrders.first()
		set(value) {
			getConfig().defaultSortOrder = value
		}

	var domain: String
		get() = parser.domain
		set(value) {
			getConfig()[parser.configKeyDomain] = value
		}

	val domains: Array<out String>
		get() = parser.configKeyDomain.presetValues

	override fun intercept(chain: Interceptor.Chain): Response = parser.intercept(chain)

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		return withMirrors {
			parser.getList(offset, order ?: defaultSortOrder, filter ?: MangaListFilter.EMPTY)
		}
	}

	override suspend fun getPagesImpl(
		chapter: MangaChapter,
		nextChapterUrl: String?
	): List<MangaPage> = withMirrors {
		parser.getPages(chapter)
	}

	override suspend fun getPageUrl(page: MangaPage): String = withMirrors {
		parser.getPageUrl(page).also { result ->
			check(result.isNotEmpty()) { "Page url is empty" }
		}
	}

	override suspend fun getChapterContent(chapter: MangaChapter, nextChapterUrl: String?): org.skepsun.kototoro.parsers.model.NovelChapterContent? {
		return runCatching {
			withMirrors { parser.getChapterContent(chapter) ?: throw IllegalStateException("Chapter content is null") }
		}.getOrNull()
	}

	override suspend fun getFilterOptions(): MangaListFilterOptions = filterOptionsLazy.get()

	suspend fun getFavicons(): Favicons = withMirrors {
		parser.getFavicons()
	}

	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = parser.getRelatedManga(seed)

	override suspend fun getDetailsImpl(manga: Manga): Manga = withMirrors {
		parser.getDetails(manga)
	}

	fun getAuthProvider(): MangaParserAuthProvider? = parser.authorizationProvider

	override fun getRequestHeaders(): Map<String, String> {
		val headers = parser.getRequestHeaders()
		val map = mutableMapOf<String, String>()
		for (i in 0 until headers.size) {
			map[headers.name(i)] = headers.value(i)
		}
		if (map["Referer"] == null) {
			val idn = java.net.IDN.toASCII(parser.domain)
			map["Referer"] = "https://$idn/"
		}
		return map
	}

	override fun createPageRequest(pageUrl: String, page: MangaPage): okhttp3.Request {
		return org.skepsun.kototoro.reader.domain.PageLoader.createPageRequest(pageUrl, page)
	}

	fun favoritesProvider(): FavoritesProvider? =
		resolveFavoritesProvider(parser)

	fun favoritesSyncProvider(): FavoritesSyncProvider? =
		resolveFavoritesSyncProvider(parser)

	fun categorizedFavoritesProvider(): CategorizedFavoritesProvider? =
		resolveCategorizedFavoritesProvider(parser)

	private fun allFields(clazz: Class<*>): Sequence<java.lang.reflect.Field> = sequence {
		var current: Class<*>? = clazz
		while (current != null && current != Any::class.java) {
			current.declaredFields.forEach { yield(it) }
			current = current.superclass
		}
	}

	private fun resolveFavoritesProvider(p: MangaParser, visited: MutableSet<Any> = mutableSetOf()): FavoritesProvider? {
		if (p in visited) return null
		visited += p
		if (p is FavoritesProvider) return p
		// 尝试通过反射抓取包装器中的字段
		for (field in allFields(p.javaClass)) {
			field.isAccessible = true
			val value = runCatching { field.get(p) }.getOrNull() ?: continue
			if (value is FavoritesProvider) return value
			if (value is MangaParser) {
				resolveFavoritesProvider(value, visited)?.let { return it }
			}
		}
		return null
	}

	private fun resolveFavoritesSyncProvider(p: MangaParser, visited: MutableSet<Any> = mutableSetOf()): FavoritesSyncProvider? {
		if (p in visited) return null
		visited += p
		if (p is FavoritesSyncProvider) return p
		for (field in allFields(p.javaClass)) {
			field.isAccessible = true
			val value = runCatching { field.get(p) }.getOrNull() ?: continue
			if (value is FavoritesSyncProvider) return value
			if (value is MangaParser) {
				resolveFavoritesSyncProvider(value, visited)?.let { return it }
			}
		}
		return null
	}

	private fun resolveCategorizedFavoritesProvider(p: MangaParser, visited: MutableSet<Any> = mutableSetOf()): CategorizedFavoritesProvider? {
		if (p in visited) return null
		visited += p
		if (p is CategorizedFavoritesProvider) return p
		for (field in allFields(p.javaClass)) {
			field.isAccessible = true
			val value = runCatching { field.get(p) }.getOrNull() ?: continue
			if (value is CategorizedFavoritesProvider) return value
			if (value is MangaParser) {
				resolveCategorizedFavoritesProvider(value, visited)?.let { return it }
			}
		}
		return null
	}

	override suspend fun getConfigKeys(): List<ConfigKey<*>> = ArrayList<ConfigKey<*>>().also {
		parser.onCreateConfig(it)
	}

	fun getAvailableMirrors(): List<String> {
		return parser.configKeyDomain.presetValues.toList()
	}

	override fun isSlowdownEnabled(): Boolean {
		return getConfig().isSlowdownEnabled
	}

	fun getConfig() = parser.config as SourceSettings

	private suspend fun <T : Any> withMirrors(block: suspend () -> T): T {
		if (!mirrorSwitcher.isEnabled) {
			return block()
		}
		val initialResult = runCatchingCancellable { block() }
		if (initialResult.isValidResult()) {
			return initialResult.getOrThrow()
		}
		val newResult = mirrorSwitcher.trySwitchMirror(this, block)
		return newResult ?: initialResult.getOrThrow()
	}

	private fun Result<Any>.isValidResult() = fold(
		onSuccess = {
			when (it) {
				is Collection<*> -> it.isNotEmpty()
				else -> true
			}
		},
		onFailure = {
			when (it.cause) {
				is CloudFlareProtectedException,
				is AuthRequiredException,
				is InteractiveActionRequiredException,
				is ProxyConfigException -> true

				else -> false
			}
		},
	)
}
