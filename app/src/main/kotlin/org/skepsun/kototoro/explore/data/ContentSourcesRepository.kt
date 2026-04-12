package org.skepsun.kototoro.explore.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
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
import org.skepsun.kototoro.core.model.ContentSourceInfo
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.core.model.unwrap
import org.skepsun.kototoro.core.model.isBroken
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.core.parser.external.ExternalContentSource
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsFlow
import org.skepsun.kototoro.core.ui.util.ReversibleHandle
import org.skepsun.kototoro.core.util.ext.flattenLatest
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.model.getLocale
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.util.mapNotNullToSet
import org.skepsun.kototoro.parsers.util.mapToSet
import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.parsers.network.CloudFlareHelper

@Singleton
class ContentSourcesRepository @Inject constructor(
	@LocalizedAppContext private val context: Context,
	private val db: MangaDatabase,
	private val settings: AppSettings,
	private val jsonSourceManager: org.skepsun.kototoro.core.jsonsource.JsonSourceManager,
	private val sourceTypeIdentifier: org.skepsun.kototoro.core.jsonsource.SourceTypeIdentifier,
	private val sourceGroupManager: org.skepsun.kototoro.core.jsonsource.SourceGroupManager,
	private val mihonExtensionManager: org.skepsun.kototoro.mihon.MihonExtensionManager,
	private val aniyomiExtensionManager: org.skepsun.kototoro.aniyomi.AniyomiExtensionManager,
	private val ireaderExtensionManager: org.skepsun.kototoro.ireader.IReaderExtensionManager,
) {

	private val dao get() = db.getSourcesDao()
	private val isNewSourcesAssimilated = AtomicBoolean(false)
	private val cachedKotatsuSources = java.util.concurrent.ConcurrentHashMap<String, org.skepsun.kototoro.core.parser.kotatsu.KotatsuParserSource>()
	private val legadoJson = Json {
		ignoreUnknownKeys = true
		isLenient = true
		allowTrailingComma = true
	}

	init {
		org.skepsun.kototoro.core.util.ext.processLifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
			org.skepsun.kototoro.core.extensions.GlobalExtensionManager.contentSources.collect {
				assimilateNewSources(force = true)
			}
		}
		org.skepsun.kototoro.core.util.ext.processLifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
			org.skepsun.kototoro.core.extensions.GlobalExtensionManager.mangaSources.collect {
				cachedKotatsuSources.clear()
				assimilateNewSources(force = true)
			}
		}
	}

	val allContentSources: Set<ContentSource>
		get() {
			val set = LinkedHashSet<ContentSource>()
			org.skepsun.kototoro.core.extensions.GlobalExtensionManager.contentSources.value.forEach { set.add(it) }
			org.skepsun.kototoro.core.extensions.GlobalExtensionManager.mangaSources.value.forEach { 
				set.add(cachedKotatsuSources.getOrPut(it.name) { org.skepsun.kototoro.core.parser.kotatsu.KotatsuParserSource(it) }) 
			}
			return set
		}

	suspend fun getEnabledSources(): List<ContentSource> {
		assimilateNewSources()
		val order = settings.sourcesSortOrder
		val disabledNames = if (!settings.isAllSourcesEnabled) dao.findAll().filter { !it.isEnabled }.mapToSet { it.source } else emptySet<String>()
		
		return dao.findAll(!settings.isAllSourcesEnabled, order).toSources(settings.isNsfwContentDisabled, order)
			.let { enabledSources ->
				val external = getExternalSources()
				val jsonSources = getEnabledJsonSources()
				val mihonSources = getEnabledMihonSources()
				val aniyomiSources = getEnabledAniyomiSources()
				val ireaderSources = getEnabledIReaderSources()
				
				val list = ArrayList<ContentSource>()
				enabledSources.mapTo(list) { it.mangaSource }
				
				val existingNames = list.mapToSet { it.name }
				
				external.forEach { if ((settings.isAllSourcesEnabled || it.name !in disabledNames) && it.name !in existingNames) list.add(it) }
				jsonSources.forEach { 
					if (it.name !in existingNames) list.add(it) 
				}
				mihonSources.forEach {
					if ((settings.isAllSourcesEnabled || it.name !in disabledNames) && it.name !in existingNames) list.add(it)
				}
				aniyomiSources.forEach {
					if ((settings.isAllSourcesEnabled || it.name !in disabledNames) && it.name !in existingNames) list.add(it)
				}
				ireaderSources.forEach {
					if ((settings.isAllSourcesEnabled || it.name !in disabledNames) && it.name !in existingNames) list.add(it)
				}
				
				if (!settings.isShowBrokenSources) {
					list.retainAll { !it.isBroken }
				}
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
		
		android.util.Log.d("ContentSourcesRepository", "User content languages for Mihon sources: $userLanguages, NSFW disabled: $isNsfwDisabled")
		
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
			android.util.Log.d("ContentSourcesRepository", "Mihon sources: ${allSources.size} total, ${filtered.size} after filters. userLanguages=$userLanguages, isNsfwDisabled=$isNsfwDisabled")
			if (filtered.size < allSources.size) {
				val filteredOut = allSources.filter { it !in filtered }
				android.util.Log.d("ContentSourcesRepository", "Filtered out Mihon sources (example): ${filteredOut.take(5).joinToString { "${it.displayName} (${it.language}, NSFW=${it.isNsfw})" }}")
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
		
		val isNsfwDisabled = settings.isNsfwContentDisabled
		
		// Note: We bypass `isExtensionsFilterLangEnabled` for Aniyomi because anime viewers
		// often watch subbed content regardless of the extension's declared language, 
		// and Aniyomi extensions frequently mislabel locales or only offer 'en'/'pt-BR' etc.
		return allSources.filter { source ->
			if (isNsfwDisabled && source.isNsfw) return@filter false
			true
		}
	}
	
	/**
	 * Gets all enabled IReader sources as IReaderMangaSource instances.
	 */
	private fun getEnabledIReaderSources(): List<org.skepsun.kototoro.ireader.model.IReaderMangaSource> {
		val allSources = ireaderExtensionManager.getIReaderMangaSources()
		val isNsfwDisabled = settings.isNsfwContentDisabled

		return allSources.filter { source ->
			if (isNsfwDisabled && source.isNsfw) return@filter false

			if (!settings.isExtensionsFilterLangEnabled) return@filter true

			val userLanguages = settings.contentLanguages
			// Map IReader country code to ISO 639-1 language code
			val mappedLang = org.skepsun.kototoro.core.model.mapIReaderLangToLocale(source.language) ?: source.language.lowercase()
			val isMultiLangEnabled = userLanguages.contains("")
			if (mappedLang == "" || mappedLang == "all") {
				isMultiLangEnabled
			} else {
				userLanguages.any { userLang ->
					userLang.isNotEmpty() && (mappedLang == userLang || mappedLang.startsWith("$userLang-") || userLang.startsWith("$mappedLang-"))
				}
			}
		}.also { filtered ->
			android.util.Log.d("ContentSourcesRepository", "IReader sources: ${allSources.size} total, ${filtered.size} after filters. langs=${allSources.map { it.language }}")
		}
	}
	
	/**
	 * Observes all IReader sources.
	 */
	private fun observeIReaderSources(): Flow<List<org.skepsun.kototoro.ireader.model.IReaderMangaSource>> {
		return combine(
			ireaderExtensionManager.installedExtensions,
			observeIsNsfwDisabled(),
			settings.observeAsFlow(AppSettings.KEY_CONTENT_LANGUAGES) { contentLanguages },
			settings.observeAsFlow(AppSettings.KEY_EXTENSIONS_FILTER_LANG) { isExtensionsFilterLangEnabled }
		) { _, _, _, _ ->
			getEnabledIReaderSources()
		}
	}
	
	/**
	 * Gets all enabled JSON sources as ContentSource instances.
	 * 
	 * @return List of enabled JSON sources wrapped as ContentSource
	 */
	private suspend fun getEnabledJsonSources(): List<org.skepsun.kototoro.core.jsonsource.JsonContentSource> {
		val jsonSources = jsonSourceManager.observeEnabledJsonSources()
			.map { entities ->
				val filteredEntities = filterActiveTvBoxEntities(entities, settings.activeTvBoxRepositoryLocator)
				android.util.Log.d("ContentSourcesRepository", "getEnabledJsonSources: found ${filteredEntities.size} enabled JSON sources after TVBox repository filter")
				filteredEntities.forEach { entity ->
					android.util.Log.d("ContentSourcesRepository", "  JSON source: id=${entity.id}, name=${entity.name}, enabled=${entity.enabled}")
				}
				filteredEntities.map { org.skepsun.kototoro.core.jsonsource.JsonContentSource(it) }
			}
			.first()
		android.util.Log.d("ContentSourcesRepository", "getEnabledJsonSources: returning ${jsonSources.size} JsonContentSource instances")
		return jsonSources
	}

	suspend fun getPinnedSources(): Set<ContentSource> {
		assimilateNewSources()
		val skipNsfw = settings.isNsfwContentDisabled
		return dao.findAllPinned().mapNotNullToSet {
			it.source.toContentSourceOrNull()?.takeUnless { x -> skipNsfw && x.isNsfw() }
		}
	}

	suspend fun getTopSources(limit: Int): List<ContentSource> {
		assimilateNewSources()
		return dao.findLastUsed(limit).toSources(settings.isNsfwContentDisabled, null)
	}

	suspend fun getDisabledSources(): Set<ContentSource> {
		assimilateNewSources()
		if (settings.isAllSourcesEnabled) {
			return emptySet()
		}
		val result = allContentSources.toMutableSet()
		val enabled = dao.findAllEnabledNames()
		for (name in enabled) {
			val source = name.toContentSourceOrNull() ?: continue
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
	): List<ContentSource> {
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
				mapTo(ArrayList<ContentSource>(size)) { it.mangaSource }
			}
		} else {
			ArrayList()
		}
		

		// Apply filters to all collected sources
		if (locale != null) {
			sources.retainAll { it.getLocale()?.language == locale }
		}
		if (excludeBroken) {
			sources.retainAll { !it.isBroken }
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
	): List<ContentSource> {
		val result = mutableListOf<ContentSource>()
		
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
			val allMihon = mihonExtensionManager.getMihonMangaSources()
			val enabledNames = if (!settings.isAllSourcesEnabled) dao.findAllEnabledNames() else allMihon.map { it.name }
			val filteredMihon = allMihon.filter { source ->
				val isMatch = if (isDisabledOnly) source.name !in enabledNames else source.name in enabledNames
				isMatch && (query.isNullOrEmpty() || source.displayName.contains(query, ignoreCase = true))
			}
			result.addAll(filteredMihon)
		}

		// Add Aniyomi sources if requested
		val shouldIncludeAniyomi = sourceTypes == null || 
			org.skepsun.kototoro.core.jsonsource.SourceType.ANIYOMI in sourceTypes
		
		if (shouldIncludeAniyomi) {
			val allAniyomi = aniyomiExtensionManager.installedExtensions.value.flatMap { ext ->
				ext.catalogueSources.map { catalogueSource ->
					org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource(
						animeCatalogueSource = catalogueSource,
						pkgName = ext.pkgName,
						isNsfw = ext.isNsfw
					)
				}
			}
			val enabledNames = if (!settings.isAllSourcesEnabled) dao.findAllEnabledNames() else allAniyomi.map { it.name }
			val filteredAniyomi = allAniyomi.filter { source ->
				val isMatch = if (isDisabledOnly) source.name !in enabledNames else source.name in enabledNames
				isMatch && (query.isNullOrEmpty() || source.displayName.contains(query, ignoreCase = true))
			}
			result.addAll(filteredAniyomi)
		}

		// Add IReader sources if requested
		val shouldIncludeIReader = sourceTypes == null || 
			org.skepsun.kototoro.core.jsonsource.SourceType.IREADER in sourceTypes
		
		if (shouldIncludeIReader) {
			val allIReader = ireaderExtensionManager.getIReaderMangaSources()
			val enabledNames = if (!settings.isAllSourcesEnabled) dao.findAllEnabledNames() else allIReader.map { it.name }
			val filteredIReader = allIReader.filter { source ->
				val isMatch = if (isDisabledOnly) source.name !in enabledNames else source.name in enabledNames
				isMatch && (query.isNullOrEmpty() || source.displayName.contains(query, ignoreCase = true))
			}
			result.addAll(filteredIReader)
		}
		
		if (locale != null) {
			result.retainAll { it.getLocale()?.language == locale }
		}
		if (types.isNotEmpty()) {
			result.retainAll { it.getContentType() in types }
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
	): List<org.skepsun.kototoro.core.jsonsource.JsonContentSource> {
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
		
		return filtered.map { org.skepsun.kototoro.core.jsonsource.JsonContentSource(it) }
	}
	
	fun observeIsEnabled(source: ContentSource): Flow<Boolean> {
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
			}
		) { skipNsfw, sources ->
			sources.count {
				it.source.toContentSourceOrNull()?.let { s -> 
					(!skipNsfw || !s.isNsfw())
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
			allContentSources.count { x ->
				x.name !in enabled && (!skipNsfw || !x.isNsfw())
			}
		}.distinctUntilChanged().onStart { assimilateNewSources() }
	}

	fun observeBuiltInSourcesCount(): Flow<Int> {
		return combine(
			observeIsNsfwDisabled(),
			org.skepsun.kototoro.core.extensions.GlobalExtensionManager.contentSources,
			org.skepsun.kototoro.core.extensions.GlobalExtensionManager.mangaSources
		) { skipNsfw, _, _ ->
			allContentSources.count { !skipNsfw || !it.isNsfw() }
		}.distinctUntilChanged()
	}

	fun observeJsonSourcesCount(): Flow<Int> {
		return jsonSourceManager.observeAllJsonSources().map { it.count() }.distinctUntilChanged()
	}

	fun observeMihonSourcesCount(): Flow<Int> {
		// getEnabledMihonSources already respects content language filter if enabled
		return observeMihonSources().map { it.size }.distinctUntilChanged()
	}

	fun observeAniyomiSourcesCount(): Flow<Int> {
		return observeAniyomiSources().map { it.size }.distinctUntilChanged()
	}

	fun observeIReaderSourcesCount(): Flow<Int> {
		return observeIReaderSources().map { it.size }.distinctUntilChanged()
	}

	fun observeEnabledSources(): Flow<List<ContentSourceInfo>> = combine(
		observeIsNsfwDisabled(),
		observeAllEnabled(),
		observeSortOrder(),
		settings.observeAsFlow(AppSettings.KEY_CONTENT_LANGUAGES) { contentLanguages },
		settings.observeAsFlow(AppSettings.KEY_EXTENSIONS_FILTER_LANG) { isExtensionsFilterLangEnabled },
		settings.observeAsFlow(AppSettings.KEY_SHOW_BROKEN_SOURCES) { isShowBrokenSources }
	) { args ->
		val skipNsfw = args[0] as Boolean
		val allEnabled = args[1] as Boolean
		val order = args[2] as SourcesSortOrder
		@Suppress("UNCHECKED_CAST")
		val contentLanguages = args[3] as Set<String>
		val isExtFilterEnabled = args[4] as Boolean
		val showBroken = args[5] as Boolean

		combine(
			dao.observeAll(false, order),
			mihonExtensionManager.installedExtensions,
			aniyomiExtensionManager.installedExtensions,
			jsonSourceManager.observeEnabledJsonSources()
		) { entities, _, _, _ ->
			val disabledNames = if (!allEnabled) entities.filter { !it.isEnabled }.mapToSet { it.source } else emptySet<String>()
			val enabledEntities = if (!allEnabled) entities.filter { it.isEnabled } else entities
			val sources = enabledEntities.toSources(skipNsfw, order).filter { info ->
				val source = info.mangaSource
				if (!showBroken && source.isBroken) return@filter false
				
				if (source is org.skepsun.kototoro.mihon.model.MihonMangaSource) {
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
					true 
				}
			}
			Pair(sources, disabledNames)
		}
	}.flattenLatest()
		.onStart { assimilateNewSources() }
		.combine(observeExternalSources()) { (sources, disabledNames), external ->
			val list = ArrayList<ContentSourceInfo>()
			external.forEach { if (it.name !in disabledNames) list.add(ContentSourceInfo(it, isEnabled = true, isPinned = true)) }
			list.addAll(sources)
			Pair(list, disabledNames)
		}
		.combine(observeJsonSources()) { (sources, disabledNames), jsonSources ->
			val list = ArrayList<ContentSourceInfo>()
			list.addAll(sources)
			
			val existingNames = sources.mapToSet { it.mangaSource.name }
			jsonSources.forEach { jsonSource ->
				if (jsonSource.name !in existingNames) {
					list.add(ContentSourceInfo(jsonSource, isEnabled = jsonSource.isEnabled, isPinned = jsonSource.isPinned))
				}
			}
			Pair(list, disabledNames)
		}
		.combine(observeMihonSources()) { (sources, disabledNames), mihonSources ->
			val list = ArrayList<ContentSourceInfo>()
			list.addAll(sources)
			
			val existingNames = sources.mapToSet { it.mangaSource.name }
			mihonSources.forEach { mihonSource ->
				if (mihonSource.name !in existingNames && mihonSource.name !in disabledNames) {
					list.add(ContentSourceInfo(mihonSource, isEnabled = true, isPinned = false))
				}
			}
			Pair(list, disabledNames)
		}
		.combine(observeAniyomiSources()) { (sources, disabledNames), aniyomiSources ->
			val list = ArrayList<ContentSourceInfo>()
			list.addAll(sources)
			
			val existingNames = sources.mapToSet { it.mangaSource.name }
			aniyomiSources.forEach { aniyomiSource ->
				if (aniyomiSource.name !in existingNames && aniyomiSource.name !in disabledNames) {
					list.add(ContentSourceInfo(aniyomiSource, isEnabled = true, isPinned = false))
				}
			}
			Pair(list, disabledNames)
		}
		.combine(observeIReaderSources()) { (sources, disabledNames), ireaderSources ->
			val list = ArrayList<ContentSourceInfo>()
			list.addAll(sources)

			val existingNames = sources.mapToSet { it.mangaSource.name }
			ireaderSources.forEach { ireaderSource ->
				if (ireaderSource.name !in existingNames && ireaderSource.name !in disabledNames) {
					list.add(ContentSourceInfo(ireaderSource, isEnabled = true, isPinned = false))
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
	fun observeEnabledBrowseSources(): Flow<List<ContentSourceInfo>> {
		return observeEnabledSources().mapLatest { sources ->
			sources.filterNot { info ->
				val src = info.mangaSource
				if (src !is org.skepsun.kototoro.core.jsonsource.JsonContentSource) return@filterNot false
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
	 * Observes all enabled JSON sources as ContentSource instances.
	 * 
	 * @return Flow emitting list of JSON sources wrapped as ContentSource
	 */
	private fun observeJsonSources(): Flow<List<org.skepsun.kototoro.core.jsonsource.JsonContentSource>> {
		return combine(
			jsonSourceManager.observeEnabledJsonSources(),
			observeIsNsfwDisabled(),
			settings.observeAsFlow(AppSettings.KEY_TVBOX_ACTIVE_REPOSITORY) { activeTvBoxRepositoryLocator }
		) { entities, skipNsfw, activeTvBoxRepositoryLocator ->
			filterActiveTvBoxEntities(entities, activeTvBoxRepositoryLocator)
				.map { org.skepsun.kototoro.core.jsonsource.JsonContentSource(it) }
				.filter { source -> !skipNsfw || !source.isNsfw() }
		}
	}

	private fun filterActiveTvBoxEntities(
		entities: List<org.skepsun.kototoro.core.db.entity.JsonSourceEntity>,
		activeLocator: String?,
	): List<org.skepsun.kototoro.core.db.entity.JsonSourceEntity> {
		val normalizedActiveLocator = activeLocator?.trim().orEmpty()
		val effectiveLocator = if (normalizedActiveLocator.isNotBlank()) {
			normalizedActiveLocator
		} else {
			entities.asSequence()
				.filter { it.type == org.skepsun.kototoro.core.db.entity.JsonSourceType.TVBOX }
				.mapNotNull { extractTvBoxSourceLocator(it.config) }
				.distinct()
				.singleOrNull()
				.orEmpty()
		}
		if (effectiveLocator.isBlank()) {
			return entities
		}
		return entities.filter { entity ->
			entity.type != org.skepsun.kototoro.core.db.entity.JsonSourceType.TVBOX ||
				extractTvBoxSourceLocator(entity.config) == effectiveLocator
		}
	}

	private fun extractTvBoxSourceLocator(rawConfig: String): String? {
		return runCatching {
			org.json.JSONObject(rawConfig)
				.optJSONObject("meta")
				?.optString("sourceLocator")
				?.trim()
				?.ifBlank { null }
		}.getOrNull()
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

	fun observeAll(): Flow<List<Pair<ContentSource, Boolean>>> = dao.observeAll().map { entities ->
		val result = ArrayList<Pair<ContentSource, Boolean>>(entities.size)
		for (entity in entities) {
			val source = entity.source.toContentSourceOrNull() ?: continue
			if (source in allContentSources) {
				result.add(source to entity.isEnabled)
			}
		}
		result
	}.onStart { assimilateNewSources() }

	suspend fun setSourcesEnabled(sources: Collection<ContentSource>, isEnabled: Boolean): ReversibleHandle {
		setSourcesEnabledImpl(sources, isEnabled)
		return ReversibleHandle {
			setSourcesEnabledImpl(sources, !isEnabled)
		}
	}

	suspend fun setSourcesEnabledExclusive(sources: Set<ContentSource>) {
		db.withTransaction {
			assimilateNewSources()
			for (s in allContentSources) {
				dao.setEnabled(s.name, s in sources)
			}
		}
	}

	suspend fun disableAllSources() {
		val currentEnabled = getEnabledSources()
		setSourcesEnabledImpl(currentEnabled, false)
	}

	suspend fun setPositions(sources: List<ContentSource>) {
		db.withTransaction {
			for ((index, item) in sources.withIndex()) {
				dao.setSortKey(item.name, index)
			}
		}
	}

	fun observeHasNewSources(): Flow<Boolean> = observeIsNsfwDisabled().map { skipNsfw ->
		val sources = dao.findAllFromVersion(BuildConfig.VERSION_CODE).toSources(skipNsfw, null)
		sources.isNotEmpty() && sources.size != allContentSources.size
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

	private suspend fun assimilateNewSources(force: Boolean = false): Boolean {
		if (!force && isNewSourcesAssimilated.getAndSet(true)) {
			return false
		}
		isNewSourcesAssimilated.set(true)
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
		return !settings.hasSeenPluginWelcome || (settings.sourcesVersion == 0 && dao.findAllEnabledNames().isEmpty())
	}

	suspend fun setIsPinned(sources: Collection<ContentSource>, isPinned: Boolean): ReversibleHandle {
		setSourcesPinnedImpl(sources, isPinned)
		return ReversibleHandle {
			setSourcesEnabledImpl(sources, !isPinned)
		}
	}

	suspend fun trackUsage(source: ContentSource) {
		if (!settings.isIncognitoModeEnabled(source.isNsfw())) {
			dao.setLastUsed(source.name, System.currentTimeMillis())
		}
	}

	private suspend fun setSourcesEnabledImpl(sources: Collection<ContentSource>, isEnabled: Boolean) {
		val nativeSources = mutableListOf<String>()
		val jsonSources = mutableListOf<String>()
		for (source in sources) {
			if (source.name.startsWith("JSON_")) {
				jsonSources.add(source.name)
			} else {
				nativeSources.add(source.name)
			}
		}

		if (jsonSources.isNotEmpty()) {
			jsonSourceManager.toggleSourcesBatch(jsonSources, isEnabled)
		}

		if (nativeSources.isNotEmpty()) {
			db.withTransaction {
				for (name in nativeSources) {
					dao.setEnabled(name, isEnabled)
				}
			}
		}
	}

	private suspend fun getNewSources(): MutableSet<out ContentSource> {
		val entities = dao.findAll()
		val result = allContentSources.toMutableSet()
		for (e in entities) {
			result.remove(e.source.toContentSourceOrNull() ?: continue)
		}
		return result
	}

	private suspend fun setSourcesPinnedImpl(sources: Collection<ContentSource>, isPinned: Boolean) {
		val nativeSources = mutableListOf<String>()
		val jsonSources = mutableListOf<String>()
		for (source in sources) {
			if (source.name.startsWith("JSON_")) {
				jsonSources.add(source.name)
			} else {
				nativeSources.add(source.name)
			}
		}

		if (jsonSources.isNotEmpty()) {
			jsonSourceManager.setSourcesPinnedBatch(jsonSources, isPinned)
		}

		if (nativeSources.isNotEmpty()) {
			db.withTransaction {
				for (name in nativeSources) {
					dao.setPinned(name, isPinned)
				}
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
	fun getSourceTypeLabel(source: ContentSource): String {
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
	): Flow<List<ContentSourceInfo>> {
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
	): Flow<List<ContentSourceInfo>> {
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
	fun isJsonSource(source: ContentSource): Boolean {
		return sourceTypeIdentifier.isJsonSource(source.name)
	}
	
	/**
	 * Gets the source type for a given source.
	 * 
	 * @param source The manga source
	 * @return The SourceType enum value
	 */
	fun getSourceType(source: ContentSource): org.skepsun.kototoro.core.jsonsource.SourceType {
		return sourceTypeIdentifier.getSourceType(source.name)
	}
	
	private fun observeExternalSources(): Flow<List<ExternalContentSource>> {
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

	fun getExternalSources(): List<ExternalContentSource> = context.packageManager.queryIntentContentProviders(
		Intent("app.kototoro.parser.PROVIDE_MANGA"), 0,
	).map { resolveInfo ->
		ExternalContentSource(
			packageName = resolveInfo.providerInfo.packageName,
			authority = resolveInfo.providerInfo.authority,
		)
	}

	private fun List<MangaSourceEntity>.toSources(
		skipNsfwSources: Boolean,
		sortOrder: SourcesSortOrder?,
	): MutableList<ContentSourceInfo> {
		val isAllEnabled = settings.isAllSourcesEnabled
		val result = ArrayList<ContentSourceInfo>(size)
		for (entity in this) {
			val source = entity.source.toContentSourceOrNull() ?: continue
			if (skipNsfwSources && source.isNsfw()) {
				continue
			}
			// Allow native sources, Mihon sources, and JSON sources
			val isKnownSource = source in allContentSources || 
								source is org.skepsun.kototoro.mihon.model.MihonMangaSource ||
								source is org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource ||
								source is org.skepsun.kototoro.ireader.model.IReaderMangaSource ||
								source is org.skepsun.kototoro.core.jsonsource.JsonContentSource
								
			if (isKnownSource) {
				result.add(
					ContentSourceInfo(
						mangaSource = source,
						isEnabled = (entity.isEnabled || isAllEnabled),
						isPinned = entity.isPinned,
					),
				)
			}
		}
		if (sortOrder == SourcesSortOrder.ALPHABETIC) {
			result.sortWith(compareBy<ContentSourceInfo> { !it.isPinned }.thenBy { it.getTitle(context) })
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

	private fun String.toContentSourceOrNull(): ContentSource? {
		// Try Global Registry for PluginContentSources first
		org.skepsun.kototoro.core.extensions.GlobalExtensionManager.contentSources.value.find { it.name == this }?.let { return it }
		org.skepsun.kototoro.core.extensions.GlobalExtensionManager.mangaSources.value.find { it.name == this }?.let { 
			return cachedKotatsuSources.getOrPut(it.name) { org.skepsun.kototoro.core.parser.kotatsu.KotatsuParserSource(it) } 
		}

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
		
		// Try IReader sources
		if (startsWith("IREADER_")) {
			ireaderExtensionManager.getIReaderMangaSources().find { it.name == this }?.let { return it }
		}
		
		// Try JSON sources
		if (startsWith("JSON_")) {
			// This is a bit expensive but necessary for pinning/top sources to work correctly
			val jsonSources = kotlinx.coroutines.runBlocking { 
				jsonSourceManager.observeAllJsonSources().first().map {
					org.skepsun.kototoro.core.jsonsource.JsonContentSource(it)
				}
			}
			jsonSources.find { it.name == this }?.let { return it }
		}

		// Fallback to anonymous/static wrapper
		val fallback = org.skepsun.kototoro.core.model.ContentSource(this)
		return if (fallback == org.skepsun.kototoro.core.model.UnknownContentSource) null else fallback
	}
}
