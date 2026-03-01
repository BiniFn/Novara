package org.skepsun.kototoro.explore.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import org.skepsun.kototoro.BuildConfig
import org.skepsun.kototoro.core.LocalizedAppContext
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.dao.MangaSourcesDao
import org.skepsun.kototoro.core.db.entity.MangaSourceEntity
import org.skepsun.kototoro.core.model.MangaSourceInfo
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.core.parser.external.ExternalMangaSource
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsFlow
import org.skepsun.kototoro.core.ui.util.ReversibleHandle
import org.skepsun.kototoro.core.util.ext.flattenLatest
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.model.getLocale
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.MangaParserSource
import org.skepsun.kototoro.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.network.CloudFlareHelper
import org.skepsun.kototoro.parsers.util.mapNotNullToSet
import org.skepsun.kototoro.parsers.util.mapToSet
import org.skepsun.kototoro.core.parser.kotatsu.KotatsuParsersProvider
import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource

@Singleton
class MangaSourcesRepository @Inject constructor(
	@LocalizedAppContext private val context: Context,
	private val db: MangaDatabase,
	private val settings: AppSettings,
	private val jsonSourceManager: org.skepsun.kototoro.core.jsonsource.JsonSourceManager,
	private val sourceTypeIdentifier: org.skepsun.kototoro.core.jsonsource.SourceTypeIdentifier,
	private val sourceGroupManager: org.skepsun.kototoro.core.jsonsource.SourceGroupManager,
	private val mihonExtensionManager: org.skepsun.kototoro.mihon.MihonExtensionManager,
	private val aniyomiExtensionManager: org.skepsun.kototoro.aniyomi.AniyomiExtensionManager,
) {

	private val isNewSourcesAssimilated = AtomicBoolean(false)
	private val legadoJson = Json {
		ignoreUnknownKeys = true
		isLenient = true
		allowTrailingComma = true
	}

	private val kotatsuSourceNames: Set<String> by lazy {
		KotatsuParsersProvider.sources.mapToSet { it.name }
	}

	private val dao: MangaSourcesDao
		get() = db.getSourcesDao()

val allMangaSources: Set<MangaSource> = Collections.unmodifiableSet(
	LinkedHashSet<MangaSource>().also {
		MangaParserSource.entries.filterTo(it) { src -> !src.isBroken }
		KotatsuParsersProvider.sources.filterTo(it) { src -> !src.isBroken }
	}
)

	suspend fun getEnabledSources(): List<MangaSource> {
		assimilateNewSources()
		val order = settings.sourcesSortOrder
		val isKotatsuEnabled = settings.isKotatsuSourcesEnabled
		return dao.findAll(!settings.isAllSourcesEnabled, order).toSources(settings.isNsfwContentDisabled, order)
			.let { enabledSources ->
				val enabled = if (isKotatsuEnabled) enabledSources else enabledSources.filterTo(ArrayList()) { it.mangaSource.name !in kotatsuSourceNames }
				val external = getExternalSources()
				val jsonSources = getEnabledJsonSources()
				val mihonSources = getEnabledMihonSources()
				val aniyomiSources = getEnabledAniyomiSources()
				android.util.Log.d("MangaSourcesRepository", "getEnabledSources: native=${enabled.size}, external=${external.size}, json=${jsonSources.size}, mihon=${mihonSources.size}, aniyomi=${aniyomiSources.size}")
				val list = ArrayList<MangaSourceInfo>(enabled.size + external.size + jsonSources.size + mihonSources.size + aniyomiSources.size)
				external.mapTo(list) { MangaSourceInfo(it, isEnabled = true, isPinned = true) }
				jsonSources.mapTo(list) { 
					android.util.Log.d("MangaSourcesRepository", "  Wrapping JSON source: ${it.name} (${it.javaClass.simpleName})")
					MangaSourceInfo(it, isEnabled = true, isPinned = it.isPinned) 
				}
				mihonSources.mapTo(list) {
					android.util.Log.d("MangaSourcesRepository", "  Wrapping Mihon source: ${it.displayName}")
					MangaSourceInfo(it, isEnabled = true, isPinned = false)
				}
				aniyomiSources.mapTo(list) {
					android.util.Log.d("MangaSourcesRepository", "  Wrapping Aniyomi source: ${it.displayName}")
					MangaSourceInfo(it, isEnabled = true, isPinned = false)
				}
				list.addAll(enabled)
				android.util.Log.d("MangaSourcesRepository", "getEnabledSources: total=${list.size} sources")
				list
			}
	}
	
	/**
	 * Gets all enabled Mihon sources as MihonMangaSource instances.
	 * Filters sources based on user's app locale - only sources matching user's language are shown.
	 * 
	 * @return List of enabled Mihon sources
	 */
	private fun getEnabledMihonSources(): List<org.skepsun.kototoro.mihon.model.MihonMangaSource> {
		val allSources = mihonExtensionManager.getMihonMangaSources()
		
		// Get user's preferred content languages (from onboarding)
		val userLanguages = settings.contentLanguages
		val isNsfwDisabled = settings.isNsfwContentDisabled
		
		android.util.Log.d("MangaSourcesRepository", "User content languages for Mihon sources: $userLanguages, NSFW disabled: $isNsfwDisabled")
		
		// Map empty string (Various Languages in Kototoro native) to "all" (Mihon)
		val isMultiLangEnabled = userLanguages.contains("")
		
		// Filter sources:
		// - "all" language sources are shown only if user enabled multi-language
		// - Other sources must match user's language preference (handles variants like zh-Hans)
		// - NSFW sources are hidden if isNsfwDisabled is true
		return allSources.filter { source ->
			// Check NSFW first
			if (isNsfwDisabled && source.isNsfw) {
				return@filter false
			}

			// If language filter is disabled, show all sources
			if (!settings.isExtensionsFilterLangEnabled) {
				return@filter true
			}

			val mihonLang = source.language.lowercase()
			if (mihonLang == "all") {
				isMultiLangEnabled
			} else {
				userLanguages.any { userLang ->
					userLang.isNotEmpty() && (mihonLang == userLang || mihonLang.startsWith("$userLang-"))
				}
			}
		}.also { filtered ->
			android.util.Log.d("MangaSourcesRepository", "Mihon sources: ${allSources.size} total, ${filtered.size} after filters. userLanguages=$userLanguages, isNsfwDisabled=$isNsfwDisabled")
			if (filtered.size < allSources.size) {
				val filteredOut = allSources.filter { it !in filtered }
				android.util.Log.d("MangaSourcesRepository", "Filtered out Mihon sources (example): ${filteredOut.take(5).joinToString { "${it.displayName} (${it.language}, NSFW=${it.isNsfw})" }}")
			}
		}
	}
	
	/**
	 * Gets all enabled Aniyomi sources as AniyomiAnimeSource instances.
	 */
	private fun getEnabledAniyomiSources(): List<org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource> {
		val allSources = aniyomiExtensionManager.installedExtensions.value.flatMap { ext ->
			ext.catalogueSources.map { catalogueSource ->
				org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource(
					animeCatalogueSource = catalogueSource,
					pkgName = ext.pkgName,
					isNsfw = ext.isNsfw
				)
			}
		}
		
		val userLanguages = settings.contentLanguages
		val isNsfwDisabled = settings.isNsfwContentDisabled
		val isMultiLangEnabled = userLanguages.contains("")
		
		return allSources.filter { source ->
			if (isNsfwDisabled && source.isNsfw) return@filter false
			
			// If language filter is disabled, show all sources
			if (!settings.isExtensionsFilterLangEnabled) {
				return@filter true
			}

			val lang = source.language.lowercase()
			if (lang == "all") {
				isMultiLangEnabled
			} else {
				userLanguages.any { userLang ->
					userLang.isNotEmpty() && (lang == userLang || lang.startsWith("$userLang-"))
				}
			}
		}
	}
	
	/**
	 * Gets all enabled JSON sources as MangaSource instances.
	 * 
	 * @return List of enabled JSON sources wrapped as MangaSource
	 */
	private suspend fun getEnabledJsonSources(): List<org.skepsun.kototoro.core.jsonsource.JsonMangaSource> {
		val jsonSources = jsonSourceManager.observeEnabledJsonSources()
			.map { entities ->
				android.util.Log.d("MangaSourcesRepository", "getEnabledJsonSources: found ${entities.size} enabled JSON sources")
				entities.forEach { entity ->
					android.util.Log.d("MangaSourcesRepository", "  JSON source: id=${entity.id}, name=${entity.name}, enabled=${entity.enabled}")
				}
				entities.map { org.skepsun.kototoro.core.jsonsource.JsonMangaSource(it) }
			}
			.first()
		android.util.Log.d("MangaSourcesRepository", "getEnabledJsonSources: returning ${jsonSources.size} JsonMangaSource instances")
		return jsonSources
	}

	suspend fun getPinnedSources(): Set<MangaSource> {
		assimilateNewSources()
		val skipNsfw = settings.isNsfwContentDisabled
		return dao.findAllPinned().mapNotNullToSet {
			it.source.toMangaSourceOrNull()?.takeUnless { x -> skipNsfw && x.isNsfw() }
		}
	}

	suspend fun getTopSources(limit: Int): List<MangaSource> {
		assimilateNewSources()
		return dao.findLastUsed(limit).toSources(settings.isNsfwContentDisabled, null)
	}

	suspend fun getDisabledSources(): Set<MangaSource> {
		assimilateNewSources()
		if (settings.isAllSourcesEnabled) {
			return emptySet()
		}
		val result = allMangaSources.toMutableSet()
		val enabled = dao.findAllEnabledNames()
		for (name in enabled) {
			val source = name.toMangaSourceOrNull() ?: continue
			result.remove(source)
		}
		return result
	}

	suspend fun queryParserSources(
		isDisabledOnly: Boolean,
		isNewOnly: Boolean,
		excludeBroken: Boolean,
		types: Set<ContentType>,
		query: String?,
		locale: String?,
		sortOrder: SourcesSortOrder?,
		sourceTypes: Set<org.skepsun.kototoro.core.jsonsource.SourceType>? = null,
	): List<MangaSource> {
		assimilateNewSources()
		
		// Filter by source type if specified
		val shouldIncludeNative = sourceTypes == null || 
			org.skepsun.kototoro.core.jsonsource.SourceType.NATIVE in sourceTypes
		val shouldIncludeJson = sourceTypes == null || 
			sourceTypes.any { it != org.skepsun.kototoro.core.jsonsource.SourceType.NATIVE }
		
		// Get native sources
		val sources = if (shouldIncludeNative) {
			val entities = dao.findAll().toMutableList()
			if (isDisabledOnly && !settings.isAllSourcesEnabled) {
				entities.removeAll { it.isEnabled }
			}
			if (isNewOnly) {
				entities.retainAll { it.addedIn == BuildConfig.VERSION_CODE }
			}
			entities.toSources(
				skipNsfwSources = settings.isNsfwContentDisabled,
				sortOrder = sortOrder,
			).run {
				mapTo(ArrayList<MangaSource>(size)) { it.mangaSource }
			}
		} else {
			ArrayList()
		}
		

		// Apply filters to all collected sources
		if (locale != null) {
			sources.retainAll { it.getLocale()?.language == locale }
		}
		if (excludeBroken) {
			sources.removeAll {
				(it as? MangaParserSource)?.isBroken == true || (it is org.skepsun.kototoro.core.parser.kotatsu.KotatsuParserSource && it.isBroken)
			}
		}
		if (types.isNotEmpty()) {
			sources.retainAll { it.getContentType() in types }
		}
		if (!query.isNullOrEmpty()) {
			sources.retainAll {
				it.getTitle(context).contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
			}
		}
		
		return sources
	}

	/**
	 * Queries all sources (native and JSON) with filtering options.
	 * 
	 * @param isDisabledOnly If true, only return disabled sources
	 * @param isNewOnly If true, only return newly added sources
	 * @param excludeBroken If true, exclude broken sources
	 * @param types Filter by content types (manga, novel, video)
	 * @param query Search query to filter by name
	 * @param locale Filter by locale
	 * @param sortOrder Sort order for results
	 * @param sourceTypes Filter by source types (NATIVE, JSON_LEGADO, JSON_TVBOX)
	 * @return List of sources matching the filters
	 */
	suspend fun queryAllSources(
		isDisabledOnly: Boolean = false,
		isNewOnly: Boolean = false,
		excludeBroken: Boolean = false,
		types: Set<ContentType> = emptySet(),
		query: String? = null,
		locale: String? = null,
		sortOrder: SourcesSortOrder? = null,
		sourceTypes: Set<org.skepsun.kototoro.core.jsonsource.SourceType>? = null,
	): List<MangaSource> {
		val result = mutableListOf<MangaSource>()
		
		// Add native sources if requested
		val shouldIncludeNative = sourceTypes == null || 
			org.skepsun.kototoro.core.jsonsource.SourceType.NATIVE in sourceTypes
		
		if (shouldIncludeNative) {
			val nativeSources = queryParserSources(
				isDisabledOnly = isDisabledOnly,
				isNewOnly = isNewOnly,
				excludeBroken = excludeBroken,
				types = types,
				query = query,
				locale = locale,
				sortOrder = sortOrder,
				sourceTypes = sourceTypes,
			)
			result.addAll(nativeSources)
		}
		
		// Add JSON sources if requested
		val shouldIncludeJson = sourceTypes == null || 
			sourceTypes.any { it != org.skepsun.kototoro.core.jsonsource.SourceType.NATIVE }
		
		if (shouldIncludeJson) {
			val jsonSources = queryJsonSources(
				isDisabledOnly = isDisabledOnly,
				query = query,
				sourceTypes = sourceTypes,
			)
			result.addAll(jsonSources)
		}
		
		// Add Mihon sources if requested
		val shouldIncludeMihon = sourceTypes == null || 
			org.skepsun.kototoro.core.jsonsource.SourceType.MIHON in sourceTypes
		
		if (shouldIncludeMihon) {
			val mihonSources = getEnabledMihonSources().filter { source ->
				query.isNullOrEmpty() || source.displayName.contains(query, ignoreCase = true)
			}
			result.addAll(mihonSources)
		}

		// Add Aniyomi sources if requested
		val shouldIncludeAniyomi = sourceTypes == null || 
			org.skepsun.kototoro.core.jsonsource.SourceType.ANIYOMI in sourceTypes
		
		if (shouldIncludeAniyomi) {
			val aniyomiSources = getEnabledAniyomiSources().filter { source ->
				query.isNullOrEmpty() || source.displayName.contains(query, ignoreCase = true)
			}
			result.addAll(aniyomiSources)
		}
		
		return result
	}
	
	/**
	 * Queries JSON sources with filtering options.
	 * 
	 * @param isDisabledOnly If true, only return disabled sources
	 * @param query Search query to filter by name
	 * @param sourceTypes Filter by JSON source types (JSON_LEGADO, JSON_TVBOX)
	 * @return List of JSON sources matching the filters
	 */
	private suspend fun queryJsonSources(
		isDisabledOnly: Boolean,
		query: String?,
		sourceTypes: Set<org.skepsun.kototoro.core.jsonsource.SourceType>?,
	): List<org.skepsun.kototoro.core.jsonsource.JsonMangaSource> {
		// Get all JSON sources
		val allJsonSources = jsonSourceManager.observeAllJsonSources().first()
		
		// Filter by enabled/disabled
		var filtered = if (isDisabledOnly) {
			allJsonSources.filter { !it.enabled }
		} else {
			allJsonSources.filter { it.enabled }
		}
		
		// Filter by source type
		if (sourceTypes != null) {
			filtered = filtered.filter { entity ->
				val sourceType = sourceTypeIdentifier.getSourceType(entity.id)
				sourceType in sourceTypes
			}
		}
		
		// Filter by query
		if (!query.isNullOrEmpty()) {
			filtered = filtered.filter { entity ->
				entity.name.contains(query, ignoreCase = true) ||
				entity.id.contains(query, ignoreCase = true)
			}
		}
		
		return filtered.map { org.skepsun.kototoro.core.jsonsource.JsonMangaSource(it) }
	}
	
	fun observeIsEnabled(source: MangaSource): Flow<Boolean> {
		// Check if it's a JSON source
		if (sourceTypeIdentifier.isJsonSource(source.name)) {
			return jsonSourceManager.observeAllJsonSources().map { entities ->
				entities.find { it.id == source.name }?.enabled ?: false
			}
		}
		return dao.observeIsEnabled(source.name).onStart { assimilateNewSources() }
	}

	fun observeEnabledSourcesCount(): Flow<Int> {
		return combine(
			observeIsNsfwDisabled(),
			observeAllEnabled().flatMapLatest { isAllSourcesEnabled ->
				dao.observeAll(!isAllSourcesEnabled, SourcesSortOrder.MANUAL)
			},
			settings.observeAsFlow(AppSettings.KEY_ENABLE_KOTATSU_SOURCES) { isKotatsuSourcesEnabled }
		) { skipNsfw, sources, isKotatsuEnabled ->
			sources.count {
				it.source.toMangaSourceOrNull()?.let { s -> 
					(!skipNsfw || !s.isNsfw()) && (isKotatsuEnabled || s.name !in kotatsuSourceNames)
				} == true
			}
		}.distinctUntilChanged().onStart { assimilateNewSources() }
	}

	fun observeAvailableSourcesCount(): Flow<Int> {
		return combine(
			observeIsNsfwDisabled(),
			observeAllEnabled().flatMapLatest { isAllSourcesEnabled ->
				dao.observeAll(!isAllSourcesEnabled, SourcesSortOrder.MANUAL)
			},
		) { skipNsfw, enabledSources ->
			val enabled = enabledSources.mapToSet { it.source }
			allMangaSources.count { x ->
				x.name !in enabled && (!skipNsfw || !x.isNsfw())
			}
		}.distinctUntilChanged().onStart { assimilateNewSources() }
	}

	fun observeBuiltInSourcesCount(): Flow<Int> {
		return observeIsNsfwDisabled().map { skipNsfw ->
			allMangaSources.count { !skipNsfw || !it.isNsfw() }
		}.distinctUntilChanged()
	}

	fun observeJsonSourcesCount(): Flow<Int> {
		return observeJsonSources().map { it.count() }.distinctUntilChanged()
	}

	fun observeMihonSourcesCount(): Flow<Int> {
		// getEnabledMihonSources already respects content language filter if enabled
		return observeMihonSources().map { it.size }.distinctUntilChanged()
	}

	fun observeAniyomiSourcesCount(): Flow<Int> {
		return observeAniyomiSources().map { it.size }.distinctUntilChanged()
	}

	fun observeEnabledSources(): Flow<List<MangaSourceInfo>> = combine(
		observeIsNsfwDisabled(),
		observeAllEnabled(),
		observeSortOrder(),
		settings.observeAsFlow(AppSettings.KEY_CONTENT_LANGUAGES) { contentLanguages },
		settings.observeAsFlow(AppSettings.KEY_EXTENSIONS_FILTER_LANG) { isExtensionsFilterLangEnabled },
		settings.observeAsFlow(AppSettings.KEY_ENABLE_KOTATSU_SOURCES) { isKotatsuSourcesEnabled }
	) { args ->
		val skipNsfw = args[0] as Boolean
		val allEnabled = args[1] as Boolean
		val order = args[2] as SourcesSortOrder
		@Suppress("UNCHECKED_CAST")
		val contentLanguages = args[3] as Set<String>
		val isExtFilterEnabled = args[4] as Boolean
		val isKotatsuEnabled = args[5] as Boolean

		combine(
			dao.observeAll(!allEnabled, order),
			mihonExtensionManager.installedExtensions,
			aniyomiExtensionManager.installedExtensions,
			jsonSourceManager.observeEnabledJsonSources()
		) { entities, _, _, _ ->
			// Map entities to sources and filter by NSFW and language
			entities.toSources(skipNsfw, order).filter { info ->
				val source = info.mangaSource
				if (!isKotatsuEnabled && source.name in kotatsuSourceNames) return@filter false
				
				if (source is org.skepsun.kototoro.mihon.model.MihonMangaSource) {
					// Apply language filter to Mihon sources in the list if enabled
					if (!isExtFilterEnabled) return@filter true
					
					val isMultiLangEnabled = contentLanguages.contains("")
					val mihonLang = source.language.lowercase()
					if (mihonLang == "all") {
						isMultiLangEnabled
					} else {
						contentLanguages.any { userLang ->
							userLang.isNotEmpty() && (mihonLang == userLang || mihonLang.startsWith("$userLang-"))
						}
					}
				} else {
					true // Native sources are already enabled/disabled based on language
				}
			}
		}
	}.flattenLatest()
		.onStart { assimilateNewSources() }
		.combine(observeExternalSources()) { enabled, external ->
			val list = ArrayList<MangaSourceInfo>(enabled.size + external.size)
			external.mapTo(list) { MangaSourceInfo(it, isEnabled = true, isPinned = true) }
			list.addAll(enabled)
			list
		}
		.combine(observeJsonSources()) { sources, jsonSources ->
			val list = ArrayList<MangaSourceInfo>(sources.size + jsonSources.size)
			list.addAll(sources)
			
			// Only add JSON sources that aren't already in the list
			val existingNames = sources.mapToSet { it.mangaSource.name }
			jsonSources.forEach { jsonSource ->
				if (jsonSource.name !in existingNames) {
					list.add(MangaSourceInfo(jsonSource, isEnabled = jsonSource.isEnabled, isPinned = jsonSource.isPinned))
				}
			}
			list
		}
		.combine(observeMihonSources()) { sources, mihonSources ->
			val list = ArrayList<MangaSourceInfo>(sources.size + mihonSources.size)
			list.addAll(sources)
			
			// Only add Mihon sources that aren't already in the list (already pinned/recently used)
			val existingNames = sources.mapToSet { it.mangaSource.name }
			mihonSources.forEach { mihonSource ->
				if (mihonSource.name !in existingNames) {
					list.add(MangaSourceInfo(mihonSource, isEnabled = true, isPinned = false))
				}
			}
			list
		}
			.combine(observeAniyomiSources()) { sources, aniyomiSources ->
				val list = ArrayList<MangaSourceInfo>(sources.size + aniyomiSources.size)
				list.addAll(sources)
			
			val existingNames = sources.mapToSet { it.mangaSource.name }
			aniyomiSources.forEach { aniyomiSource ->
				if (aniyomiSource.name !in existingNames) {
					list.add(MangaSourceInfo(aniyomiSource, isEnabled = true, isPinned = false))
				}
			}
				list
			}

	/**
	 * 对齐 legado-with-MD3：浏览(发现)仅展示具备 exploreUrl 的源；仅提供 searchUrl 的源不应出现在浏览页。
	 *
	 * 说明：
	 * - 仅针对 JSON_LEGADO 源做该过滤（避免误伤 JS/TVBox 等其它 JSON 类型）。
	 * - 搜索仍使用 `getEnabledSources()`，不受影响。
	 */
	fun observeEnabledBrowseSources(): Flow<List<MangaSourceInfo>> {
		return observeEnabledSources().mapLatest { sources ->
			sources.filterNot { info ->
				val src = info.mangaSource
				if (src !is org.skepsun.kototoro.core.jsonsource.JsonMangaSource) return@filterNot false
				if (sourceTypeIdentifier.getSourceType(src.name) != org.skepsun.kototoro.core.jsonsource.SourceType.JSON_LEGADO) {
					return@filterNot false
				}
				val config = runCatching { legadoJson.decodeFromString<LegadoBookSource>(src.entity.config) }.getOrNull()
					?: return@filterNot false
				config.exploreUrl.isNullOrBlank()
			}
		}
	}
	
	/**
	 * Observes all Aniyomi sources.
	 */
	private fun observeAniyomiSources(): Flow<List<org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource>> {
		return combine(
			aniyomiExtensionManager.installedExtensions,
			observeIsNsfwDisabled(),
			settings.observeAsFlow(AppSettings.KEY_CONTENT_LANGUAGES) { contentLanguages },
			settings.observeAsFlow(AppSettings.KEY_EXTENSIONS_FILTER_LANG) { isExtensionsFilterLangEnabled }
		) { _, _, _, _ ->
			getEnabledAniyomiSources()
		}
	}
	
	/**
	 * Observes all enabled JSON sources as MangaSource instances.
	 * 
	 * @return Flow emitting list of JSON sources wrapped as MangaSource
	 */
	private fun observeJsonSources(): Flow<List<org.skepsun.kototoro.core.jsonsource.JsonMangaSource>> {
		return combine(
			jsonSourceManager.observeEnabledJsonSources(),
			observeIsNsfwDisabled()
		) { entities, skipNsfw ->
			entities.map { org.skepsun.kototoro.core.jsonsource.JsonMangaSource(it) }
				.filter { source -> !skipNsfw || !source.isNsfw() }
		}
	}
	
	/**
	 * Observes all Mihon sources.
	 * 
	 * @return Flow emitting list of Mihon sources
	 */
	private fun observeMihonSources(): Flow<List<org.skepsun.kototoro.mihon.model.MihonMangaSource>> {
		return combine(
			mihonExtensionManager.installedExtensions,
			observeIsNsfwDisabled(),
			settings.observeAsFlow(AppSettings.KEY_CONTENT_LANGUAGES) { contentLanguages },
			settings.observeAsFlow(AppSettings.KEY_EXTENSIONS_FILTER_LANG) { isExtensionsFilterLangEnabled }
		) { _, _, _, _ ->
			getEnabledMihonSources()
		}
	}

	fun observeAll(): Flow<List<Pair<MangaSource, Boolean>>> = dao.observeAll().map { entities ->
		val result = ArrayList<Pair<MangaSource, Boolean>>(entities.size)
		for (entity in entities) {
			val source = entity.source.toMangaSourceOrNull() ?: continue
			if (source in allMangaSources) {
				result.add(source to entity.isEnabled)
			}
		}
		result
	}.onStart { assimilateNewSources() }

	suspend fun setSourcesEnabled(sources: Collection<MangaSource>, isEnabled: Boolean): ReversibleHandle {
		setSourcesEnabledImpl(sources, isEnabled)
		return ReversibleHandle {
			setSourcesEnabledImpl(sources, !isEnabled)
		}
	}

	suspend fun setSourcesEnabledExclusive(sources: Set<MangaSource>) {
		db.withTransaction {
			assimilateNewSources()
			for (s in allMangaSources) {
				dao.setEnabled(s.name, s in sources)
			}
		}
	}

	suspend fun disableAllSources() {
		db.withTransaction {
			assimilateNewSources()
			dao.disableAllSources()
		}
	}

	suspend fun setPositions(sources: List<MangaSource>) {
		db.withTransaction {
			for ((index, item) in sources.withIndex()) {
				dao.setSortKey(item.name, index)
			}
		}
	}

	fun observeHasNewSources(): Flow<Boolean> = observeIsNsfwDisabled().map { skipNsfw ->
		val sources = dao.findAllFromVersion(BuildConfig.VERSION_CODE).toSources(skipNsfw, null)
		sources.isNotEmpty() && sources.size != allMangaSources.size
	}.onStart { assimilateNewSources() }

	fun observeHasNewSourcesForBadge(): Flow<Boolean> = combine(
		settings.observeAsFlow(AppSettings.KEY_SOURCES_VERSION) { sourcesVersion },
		observeIsNsfwDisabled(),
	) { version, skipNsfw ->
		if (version < BuildConfig.VERSION_CODE) {
			val sources = dao.findAllFromVersion(version).toSources(skipNsfw, null)
			sources.isNotEmpty()
		} else {
			false
		}
	}.onStart { assimilateNewSources() }

	fun clearNewSourcesBadge() {
		settings.sourcesVersion = BuildConfig.VERSION_CODE
	}

	private suspend fun assimilateNewSources(): Boolean {
		if (isNewSourcesAssimilated.getAndSet(true)) {
			return false
		}
		val new = getNewSources()
		if (new.isEmpty()) {
			return false
		}
		var maxSortKey = dao.getMaxSortKey()
		val isAllEnabled = settings.isAllSourcesEnabled
		val entities = new.map { x ->
			MangaSourceEntity(
				source = x.name,
				isEnabled = isAllEnabled,
				sortKey = ++maxSortKey,
				addedIn = BuildConfig.VERSION_CODE,
				lastUsedAt = 0,
				isPinned = false,
				cfState = CloudFlareHelper.PROTECTION_NOT_DETECTED,
			)
		}
		dao.insertIfAbsent(entities)
		return true
	}

	suspend fun isSetupRequired(): Boolean {
		return settings.sourcesVersion == 0 && dao.findAllEnabledNames().isEmpty()
	}

	suspend fun setIsPinned(sources: Collection<MangaSource>, isPinned: Boolean): ReversibleHandle {
		setSourcesPinnedImpl(sources, isPinned)
		return ReversibleHandle {
			setSourcesEnabledImpl(sources, !isPinned)
		}
	}

	suspend fun trackUsage(source: MangaSource) {
		if (!settings.isIncognitoModeEnabled(source.isNsfw())) {
			dao.setLastUsed(source.name, System.currentTimeMillis())
		}
	}

	private suspend fun setSourcesEnabledImpl(sources: Collection<MangaSource>, isEnabled: Boolean) {
		if (sources.size == 1) { // fast path
			dao.setEnabled(sources.first().name, isEnabled)
			return
		}
		db.withTransaction {
			for (source in sources) {
				dao.setEnabled(source.name, isEnabled)
			}
		}
	}

	private suspend fun getNewSources(): MutableSet<out MangaSource> {
		val entities = dao.findAll()
		val result = allMangaSources.toMutableSet()
		for (e in entities) {
			result.remove(e.source.toMangaSourceOrNull() ?: continue)
		}
		return result
	}

	private suspend fun setSourcesPinnedImpl(sources: Collection<MangaSource>, isPinned: Boolean) {
		if (sources.size == 1) { // fast path
			dao.setPinned(sources.first().name, isPinned)
			return
		}
		db.withTransaction {
			for (source in sources) {
				dao.setPinned(source.name, isPinned)
			}
		}
	}

	/**
	 * Gets the source type label for a given source.
	 * This is useful for displaying the source type in the UI.
	 * 
	 * @param source The manga source
	 * @return A human-readable label for the source type
	 */
	fun getSourceTypeLabel(source: MangaSource): String {
		return sourceTypeIdentifier.getSourceTypeLabel(source.name)
	}
	
	/**
	 * Observes sources grouped by content type.
	 * 
	 * @param contentGroup The content group to filter by
	 * @return Flow emitting list of sources in the specified content group
	 */
	fun observeSourcesByContentGroup(
		contentGroup: org.skepsun.kototoro.core.jsonsource.ContentGroup
	): Flow<List<MangaSourceInfo>> {
		return observeEnabledSources().map { sources ->
			sources.filter { sourceInfo ->
				sourceGroupManager.getContentGroup(sourceInfo.mangaSource) == contentGroup
			}
		}
	}
	
	/**
	 * Observes sources grouped by origin type.
	 * 
	 * @param originGroup The origin group to filter by
	 * @return Flow emitting list of sources in the specified origin group
	 */
	fun observeSourcesByOriginGroup(
		originGroup: org.skepsun.kototoro.core.jsonsource.OriginGroup
	): Flow<List<MangaSourceInfo>> {
		return observeEnabledSources().map { sources ->
			sources.filter { sourceInfo ->
				sourceGroupManager.getOriginGroup(sourceInfo.mangaSource) == originGroup
			}
		}
	}
	
	/**
	 * Observes counts of sources in each group.
	 * 
	 * @return Flow emitting map of SourceGroup to count
	 */
	fun observeGroupCounts(): Flow<Map<org.skepsun.kototoro.core.jsonsource.SourceGroup, Int>> {
		return observeEnabledSources().map { sources ->
			val mangaSources = sources.map { it.mangaSource }
			sourceGroupManager.getGroupCounts(mangaSources)
		}
	}
	
	/**
	 * Checks if a source is a JSON source.
	 * 
	 * @param source The manga source
	 * @return true if the source is a JSON source
	 */
	fun isJsonSource(source: MangaSource): Boolean {
		return sourceTypeIdentifier.isJsonSource(source.name)
	}
	
	/**
	 * Gets the source type for a given source.
	 * 
	 * @param source The manga source
	 * @return The SourceType enum value
	 */
	fun getSourceType(source: MangaSource): org.skepsun.kototoro.core.jsonsource.SourceType {
		return sourceTypeIdentifier.getSourceType(source.name)
	}
	
	private fun observeExternalSources(): Flow<List<ExternalMangaSource>> {
		return callbackFlow {
			val receiver = object : BroadcastReceiver() {
				override fun onReceive(context: Context?, intent: Intent?) {
					trySendBlocking(intent)
				}
			}
			ContextCompat.registerReceiver(
				context,
				receiver,
				IntentFilter().apply {
					addAction(Intent.ACTION_PACKAGE_ADDED)
					addAction(Intent.ACTION_PACKAGE_VERIFIED)
					addAction(Intent.ACTION_PACKAGE_REPLACED)
					addAction(Intent.ACTION_PACKAGE_REMOVED)
					addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
					addDataScheme("package")
				},
				ContextCompat.RECEIVER_EXPORTED,
			)
			awaitClose { context.unregisterReceiver(receiver) }
		}.onStart {
			emit(null)
		}.map {
			getExternalSources()
		}.distinctUntilChanged()
			.conflate()
	}

	fun getExternalSources(): List<ExternalMangaSource> = context.packageManager.queryIntentContentProviders(
		Intent("app.kototoro.parser.PROVIDE_MANGA"), 0,
	).map { resolveInfo ->
		ExternalMangaSource(
			packageName = resolveInfo.providerInfo.packageName,
			authority = resolveInfo.providerInfo.authority,
		)
	}

	private fun List<MangaSourceEntity>.toSources(
		skipNsfwSources: Boolean,
		sortOrder: SourcesSortOrder?,
	): MutableList<MangaSourceInfo> {
		val isAllEnabled = settings.isAllSourcesEnabled
		val result = ArrayList<MangaSourceInfo>(size)
		for (entity in this) {
			val source = entity.source.toMangaSourceOrNull() ?: continue
			if (skipNsfwSources && source.isNsfw()) {
				continue
			}
			// Allow native sources, Mihon sources, and JSON sources
			val isKnownSource = source in allMangaSources || 
								source is org.skepsun.kototoro.mihon.model.MihonMangaSource ||
								source is org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource ||
								source is org.skepsun.kototoro.core.jsonsource.JsonMangaSource
								
			if (isKnownSource) {
				val isKotatsu = source.name in kotatsuSourceNames
				val isKotatsuEnabled = settings.isKotatsuSourcesEnabled
				result.add(
					MangaSourceInfo(
						mangaSource = source,
						isEnabled = (entity.isEnabled || isAllEnabled) && (!isKotatsu || isKotatsuEnabled),
						isPinned = entity.isPinned,
					),
				)
			}
		}
		if (sortOrder == SourcesSortOrder.ALPHABETIC) {
			result.sortWith(compareBy<MangaSourceInfo> { !it.isPinned }.thenBy { it.getTitle(context) })
		}
		return result
	}

	private fun observeIsNsfwDisabled() = settings.observeAsFlow(AppSettings.KEY_DISABLE_NSFW) {
		isNsfwContentDisabled
	}

	private fun observeSortOrder() = settings.observeAsFlow(AppSettings.KEY_SOURCES_ORDER) {
		sourcesSortOrder
	}

	private fun observeAllEnabled() = settings.observeAsFlow(AppSettings.KEY_SOURCES_ENABLED_ALL) {
		isAllSourcesEnabled
	}

	private fun String.toMangaSourceOrNull(): MangaSource? {
		// Try native sources first
		MangaParserSource.entries.find { it.name == this }?.let { return it }
		
		// Try Mihon sources
		if (startsWith("MIHON_")) {
			mihonExtensionManager.getMihonMangaSources().find { it.name == this }?.let { return it }
		}
		
		// Try Aniyomi sources
		if (startsWith("ANIYOMI_")) {
			aniyomiExtensionManager.installedExtensions.value.flatMap { ext ->
				ext.catalogueSources.map { catalogueSource ->
					org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource(
						animeCatalogueSource = catalogueSource,
						pkgName = ext.pkgName,
						isNsfw = ext.isNsfw
					)
				}
			}.find { it.name == this }?.let { return it }
		}
		
		// Try JSON sources
		if (startsWith("JSON_")) {
			// This is a bit expensive but necessary for pinning/top sources to work correctly
			val jsonSources = kotlinx.coroutines.runBlocking { 
				jsonSourceManager.observeEnabledJsonSources().first().map {
					org.skepsun.kototoro.core.jsonsource.JsonMangaSource(it)
				}
			}
			jsonSources.find { it.name == this }?.let { return it }
		}

		// Kotatsu sources
		KotatsuParsersProvider.findByName(this)?.let { return it }
		
		// Fallback to anonymous wrapper
		return org.skepsun.kototoro.core.model.MangaSource(this)
	}
}
