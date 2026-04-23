package org.skepsun.kototoro.home.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.core.jsonsource.OriginGroup
import org.skepsun.kototoro.core.jsonsource.SourceGroup
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.core.prefs.observeAsFlow
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.skepsun.kototoro.backups.data.BackupRepository
import org.skepsun.kototoro.backups.domain.BackupWebDavRestoreCoordinator
import org.skepsun.kototoro.backups.domain.BackupWebDavUploadCoordinator
import org.skepsun.kototoro.backups.domain.ExternalBackupStorage
import org.skepsun.kototoro.backups.ui.periodical.WebDavBackupUploader
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.explore.data.ContentSourcesRepository
import org.skepsun.kototoro.entitygraph.data.EntityGraphRepository
import org.skepsun.kototoro.favourites.domain.FavouritesRepository
import org.skepsun.kototoro.history.data.HistoryRepository
import org.skepsun.kototoro.list.domain.ContentListMapper
import org.skepsun.kototoro.list.ui.model.ContentGridModel
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.suggestions.domain.SuggestionRepository
import org.skepsun.kototoro.tracker.domain.TrackingRepository
import org.skepsun.kototoro.tracker.domain.model.ContentTracking
import org.skepsun.kototoro.search.domain.ContentSearchRepository
import javax.inject.Inject

data class HomeRecentItem(
	val content: Content,
	val cardModel: ContentGridModel? = null,
	val groupKey: Long = content.id,
) {
	val title: String
		get() = content.title

	@get:StringRes
	val typeLabelResId: Int?
		get() = content.source.getContentType().toHomeTab()?.titleResId
}

data class HomeUpdateItem(
	val content: Content,
	val newChapters: Int,
	val cardModel: ContentGridModel? = null,
	val groupKey: Long = content.id,
) {
	val title: String
		get() = content.title
}

data class HomeRecommendationItem(
	val content: Content,
	val cardModel: ContentGridModel? = null,
	val groupKey: Long = content.id,
) {
	val title: String
		get() = content.title
}

data class HomeRecentSearchItem(
	val query: String,
)

data class HomeResumeState(
	val content: Content? = null,
	val progressPercent: Int? = null,
	val groupKey: Long? = content?.id,
) {
	val isAvailable: Boolean
		get() = content != null
}

enum class HomeContentTab(@StringRes val titleResId: Int) {
	MANGA(R.string.manga),
	NOVEL(R.string.novel),
	VIDEO(R.string.video),
}

enum class HomeSourceOrigin {
	BUILT_IN,
	MIHON,
	ANIYOMI,
	LEGADO,
	TVBOX,

	EXTERNAL,
	IREADER,
}

data class HomeSourceBreakdown(
	val origin: HomeSourceOrigin,
	val count: Int,
)

data class HomeSyncState(
	val isWebDavEnabled: Boolean = false,
	val isAutoSyncEnabled: Boolean = false,
	val lastUploadTime: Long = 0L,
	val lastUploadKind: String? = null,
)


data class HomeSummaryState(
	val selectedTab: HomeContentTab? = null,
	val recentHistoryCount: Int = 0,
	val recentHistoryItems: List<HomeRecentItem> = emptyList(),
	val resumeState: HomeResumeState = HomeResumeState(),
	val favoritesCount: Int = 0,
	val favoriteCategoriesCount: Int = 0,
	val unreadUpdatesCount: Int = 0,
	val recentUpdates: List<HomeUpdateItem> = emptyList(),
	val recommendationsCount: Int = 0,
	val recommendations: List<HomeRecommendationItem> = emptyList(),
	val recentSearches: List<HomeRecentSearchItem> = emptyList(),
	val enabledSourcesCount: Int = 0,
	val sourceBreakdown: List<HomeSourceBreakdown> = emptyList(),
	val syncState: HomeSyncState = HomeSyncState(),
	val selectedSourceTags: Set<org.skepsun.kototoro.explore.ui.model.SourceTag> = emptySet(),
	val isInitialized: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
	historyRepository: HistoryRepository,
	favouritesRepository: FavouritesRepository,
	trackingRepository: TrackingRepository,
	suggestionRepository: SuggestionRepository,
	contentSourcesRepository: ContentSourcesRepository,
	private val exploreRepository: org.skepsun.kototoro.explore.domain.ExploreRepository,
	private val settings: AppSettings,
	private val webDavUploader: WebDavBackupUploader,
	private val backupWebDavUploadCoordinator: BackupWebDavUploadCoordinator,
	private val backupWebDavRestoreCoordinator: BackupWebDavRestoreCoordinator,
	private val backupStorage: ExternalBackupStorage,
	private val repository: BackupRepository,
	private val entityGraphRepository: EntityGraphRepository,
	private val sourceGroupManager: org.skepsun.kototoro.core.jsonsource.SourceGroupManager,
	private val globalFavoritesState: org.skepsun.kototoro.favourites.domain.GlobalFavoritesState,
	private val sourcePresetsRepository: org.skepsun.kototoro.explore.data.SourcePresetsRepository,
	private val contentSearchRepository: ContentSearchRepository,
	private val contentListMapper: ContentListMapper,
	@ApplicationContext private val appContext: Context,
) : ViewModel() {

	private val selectedTabFlow = globalFavoritesState.selectedGroupTab.map { tabId ->
		when (tabId) {
			org.skepsun.kototoro.explore.ui.model.BrowseGroupTab.Content -> HomeContentTab.MANGA
			org.skepsun.kototoro.explore.ui.model.BrowseGroupTab.Novel -> HomeContentTab.NOVEL
			org.skepsun.kototoro.explore.ui.model.BrowseGroupTab.Video -> HomeContentTab.VIDEO
			else -> null
		}
	}
	private val selectedSourceTagsFlow = globalFavoritesState.selectedSourceTags
	private val activePresetFlow = settings.observeAsFlow(AppSettings.KEY_ACTIVE_SOURCE_PRESET_ID) { activeSourcePresetId }
		.flatMapLatest { id ->
			if (id == -1L) flowOf(null)
			else sourcePresetsRepository.observe(id)
		}
	private val recentHistoryFlow = historyRepository.observeAll()
	private val isHistoryNsfwDisabledFlow = settings.observeAsFlow(AppSettings.KEY_HISTORY_EXCLUDE_NSFW) { isHistoryExcludeNsfw }
	private val resumeCandidateFlow = combine(
		recentHistoryFlow,
		selectedTabFlow,
		selectedSourceTagsFlow,
		activePresetFlow,
		isHistoryNsfwDisabledFlow,
	) { history, selectedTab, selectedSourceTags, preset, isHistoryNsfwDisabled ->
		history.firstOrNull { item ->
			item.matchesHomeFilters(
				tab = selectedTab,
				sourceTags = selectedSourceTags,
				sourceGroupManager = sourceGroupManager,
				preset = preset,
				excludeNsfw = isHistoryNsfwDisabled,
			)
		}
	}
	private val resumeStateFlow = resumeCandidateFlow
		.flatMapLatest { content ->
			if (content == null) {
				flowOf(HomeResumeState())
			} else {
				historyRepository.observeOne(content.id).map { history ->
					HomeResumeState(
						content = content,
						progressPercent = history.toProgressPercent(),
					)
				}
			}
		}
	private val favoritesCountFlow = favouritesRepository.observeContentCount()
	private val favoriteCategoriesCountFlow = favouritesRepository.observeCategories().map { it.size }
	private val unreadUpdatesCountFlow = trackingRepository.observeUnreadUpdatesCount()
	private val recentUpdatesFlow = trackingRepository.observeUpdatedContent(limit = HOME_COVER_PREVIEW_LIMIT * 12, filterOptions = emptySet())
	private val recommendationsFlow = suggestionRepository.observeAll()
	private val recentSearchesFlow = contentSearchRepository.observeRecentQueries(HOME_COVER_PREVIEW_LIMIT)
	private val isTrackerNsfwDisabledFlow = settings.observeAsFlow(AppSettings.KEY_TRACKER_NO_NSFW) { isTrackerNsfwDisabled }
	private val isSuggestionNsfwDisabledFlow = settings.observeAsFlow(AppSettings.KEY_SUGGESTIONS_EXCLUDE_NSFW) { isSuggestionsExcludeNsfw }
	private val enabledSourcesCountFlow = contentSourcesRepository.observeEnabledSourcesCount()
	private val sourceBreakdownFlow = contentSourcesRepository.observeGroupCounts()
		.map { counts ->
			listOfNotNull(
				HomeSourceOrigin.BUILT_IN.toBreakdown(counts.countOf(OriginGroup.NATIVE)),
				HomeSourceOrigin.MIHON.toBreakdown(counts.countOf(OriginGroup.MIHON)),
				HomeSourceOrigin.ANIYOMI.toBreakdown(counts.countOf(OriginGroup.ANIYOMI)),
				HomeSourceOrigin.LEGADO.toBreakdown(counts.countOf(OriginGroup.LEGADO_JSON)),
				HomeSourceOrigin.TVBOX.toBreakdown(counts.countOf(OriginGroup.TVBOX_JSON)),

				HomeSourceOrigin.EXTERNAL.toBreakdown(counts.countOf(OriginGroup.EXTERNAL)),
				HomeSourceOrigin.IREADER.toBreakdown(counts.countOf(OriginGroup.IREADER)),
			).sortedByDescending { it.count }
				.take(3)
		}
	private val syncStateFlow = settings.observe(
		AppSettings.KEY_BACKUP_WEBDAV_ENABLED,
		AppSettings.KEY_BACKUP_WEBDAV_AUTO_SYNC,
		AppSettings.KEY_BACKUP_WEBDAV_LAST_UPLOAD_TIME,
		AppSettings.KEY_BACKUP_WEBDAV_LAST_UPLOAD_KIND,
	).map {
		HomeSyncState(
			isWebDavEnabled = settings.isBackupWebDavUploadEnabled,
			isAutoSyncEnabled = settings.isBackupWebDavAutoSyncEnabled,
			lastUploadTime = settings.backupWebDavLastUploadTime,
			lastUploadKind = settings.backupWebDavLastUploadKind,
		)
	}

	val summaryState = combine(
		selectedTabFlow,
		combine(selectedSourceTagsFlow, activePresetFlow, ::Pair),
		combine(
			combine(
				resumeStateFlow,
				recentHistoryFlow,
				favoritesCountFlow,
			) { resumeState, recentHistory, favoritesCount ->
				Triple(resumeState, recentHistory, favoritesCount)
			},
			combine(
				favoriteCategoriesCountFlow,
				unreadUpdatesCountFlow,
				recentUpdatesFlow,
				recommendationsFlow,
				recentSearchesFlow,
			) { favoriteCategoriesCount, unreadUpdatesCount, recentUpdates, recommendations, recentSearches ->
				Quintuple(favoriteCategoriesCount, unreadUpdatesCount, recentUpdates, recommendations, recentSearches)
			},
		) { left, right ->
			Octuple(
				left.first,
				left.second,
				left.third,
				right.first,
				right.second,
				right.third,
				right.fourth,
				right.fifth,
			)
		},
		combine(
			enabledSourcesCountFlow,
			sourceBreakdownFlow,
			syncStateFlow,
		) { enabledSourcesCount, sourceBreakdown, syncState ->
			Triple(enabledSourcesCount, sourceBreakdown, syncState)
		},
		combine(
			isTrackerNsfwDisabledFlow,
			isSuggestionNsfwDisabledFlow,
			isHistoryNsfwDisabledFlow,
		) { tracker, suggestion, history ->
			Triple(tracker, suggestion, history)
		},
	) { selectedTab, tagsAndPreset, left, right, nsfwFlags ->
		val selectedSourceTags = tagsAndPreset.first
		val preset = tagsAndPreset.second
		val isTrackerNsfwDisabled = nsfwFlags.first
		val isSuggestionNsfwDisabled = nsfwFlags.second
		val isHistoryNsfwDisabled = nsfwFlags.third

		val allHistory = if (isHistoryNsfwDisabled) left.second.filterNot { it.isNsfw() } else left.second
		val allUpdates = if (isTrackerNsfwDisabled) left.sixth.filterNot { it.manga.isNsfw() } else left.sixth
		val allRecommendations = if (isSuggestionNsfwDisabled) left.seventh.filterNot { it.isNsfw() } else left.seventh
		val entityIdsByMangaId = entityGraphRepository.findEntityIdsByLocalMangaIds(
			buildSet {
				addAll(allHistory.map { it.id })
				addAll(allUpdates.map { it.manga.id })
				addAll(allRecommendations.map { it.id })
			},
		)

		val resumeState = left.first
			.filtered(
				tab = selectedTab,
				sourceTags = selectedSourceTags,
				sourceGroupManager = sourceGroupManager,
				preset = preset,
			)
			.filteredNsfw(isHistoryNsfwDisabled)
			.withGroupKey(entityIdsByMangaId)
		val recentHistory = allHistory
			.aggregateHomeContentByEntity(entityIdsByMangaId)
			.selectHomeHistoryByTab(selectedTab, selectedSourceTags, sourceGroupManager, preset)
		val favoritesCount = left.third
		val favoriteCategoriesCount = left.fourth
		val recentUpdates = allUpdates
			.aggregateHomeUpdatesByEntity(entityIdsByMangaId)
			.selectHomeUpdatesByTab(selectedTab, selectedSourceTags, sourceGroupManager, preset)
		val recommendations = allRecommendations
			.aggregateHomeRecommendationsByEntity(entityIdsByMangaId)
			.selectHomeRecommendationsByTab(selectedTab, selectedSourceTags, sourceGroupManager, preset)
		val recentSearches = left.eighth
		val historyPreview = recentHistory.take(HOME_COVER_PREVIEW_LIMIT).withHomeRecentGridModels(contentListMapper)
		val updatesPreview = recentUpdates.take(HOME_COVER_PREVIEW_LIMIT).withHomeUpdateGridModels(contentListMapper)
		val recommendationsPreview = recommendations.take(HOME_COVER_PREVIEW_LIMIT).withHomeRecommendationGridModels(contentListMapper)

		val actualHistoryCount = recentHistory.size
		val unreadUpdatesCount = recentUpdates.size
		val actualRecommendationsCount = recommendations.size
		val enabledSourcesCount = right.first
		val sourceBreakdown = right.second
		val syncState = right.third

		HomeSummaryState(
			selectedTab = selectedTab,
			recentHistoryCount = actualHistoryCount,
			recentHistoryItems = historyPreview,
			resumeState = resumeState,
			favoritesCount = favoritesCount,
			favoriteCategoriesCount = favoriteCategoriesCount,
			unreadUpdatesCount = unreadUpdatesCount,
			recentUpdates = updatesPreview,
			recommendationsCount = actualRecommendationsCount,
			recommendations = recommendationsPreview,
			recentSearches = recentSearches.map { HomeRecentSearchItem(it) },
			enabledSourcesCount = enabledSourcesCount,
			sourceBreakdown = sourceBreakdown,
			syncState = syncState,
			selectedSourceTags = selectedSourceTags,
			isInitialized = true,
		)
	}.flowOn(Dispatchers.Default).stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = HomeSummaryState(
			selectedTab = when (globalFavoritesState.selectedGroupTab.value) {
				org.skepsun.kototoro.explore.ui.model.BrowseGroupTab.Content -> HomeContentTab.MANGA
				org.skepsun.kototoro.explore.ui.model.BrowseGroupTab.Novel -> HomeContentTab.NOVEL
				org.skepsun.kototoro.explore.ui.model.BrowseGroupTab.Video -> HomeContentTab.VIDEO
				else -> null
			},
			selectedSourceTags = globalFavoritesState.selectedSourceTags.value,
		),
	)

	fun setSelectedTab(tab: HomeContentTab?) {
		val groupTab = when (tab) {
			HomeContentTab.MANGA -> org.skepsun.kototoro.explore.ui.model.BrowseGroupTab.Content
			HomeContentTab.NOVEL -> org.skepsun.kototoro.explore.ui.model.BrowseGroupTab.Novel
			HomeContentTab.VIDEO -> org.skepsun.kototoro.explore.ui.model.BrowseGroupTab.Video
			null -> org.skepsun.kototoro.explore.ui.model.BrowseGroupTab.All
		}
		globalFavoritesState.setSelectedGroupTab(groupTab)
	}

	fun setSelectedSourceTags(tags: Set<org.skepsun.kototoro.explore.ui.model.SourceTag>) {
		globalFavoritesState.setSelectedSourceTags(tags)
	}

	val isRandomLoading = MutableStateFlow(false)
	val onOpenContent = org.skepsun.kototoro.core.util.ext.MutableEventFlow<Content>()

	fun openRandom() {
		if (isRandomLoading.value) return
		viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
			isRandomLoading.value = true
			try {
				val manga = exploreRepository.findRandomContent(tagsLimit = 8)
				onOpenContent.call(manga)
			} finally {
				isRandomLoading.value = false
			}
		}
	}

	fun uploadWebDavNow() {
		viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
			val output = org.skepsun.kototoro.backups.domain.BackupUtils.createTempFile(appContext)
			try {
				val zip = java.util.zip.ZipOutputStream(output.outputStream())
				try {
					repository.createBackup(zip, null)
				} finally {
					zip.close()
				}
				
				if (settings.isBackupWebDavKeepLocalCopyEnabled) {
					backupStorage.put(output)
					backupStorage.trim(settings.periodicalBackupMaxCount)
				}
				backupWebDavUploadCoordinator.uploadAndCommit(
					file = output,
					uploadKind = "manual",
				)
			} catch (e: Exception) {
				e.printStackTraceDebug()
			} finally {
				output.delete()
			}
		}
	}

	fun restoreWebDavNow() {
		viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
			try {
				val latest = webDavUploader.getLatestBackup()
				if (latest == null) {
					return@launch
				}
				val tempFile = java.io.File.createTempFile("webdav_backup_manual", ".bk.zip", appContext.cacheDir)
				try {
					webDavUploader.downloadBackup(latest.name, tempFile)
					val allSections = setOf(
						org.skepsun.kototoro.backups.domain.BackupSection.HISTORY,
						org.skepsun.kototoro.backups.domain.BackupSection.CATEGORIES,
						org.skepsun.kototoro.backups.domain.BackupSection.FAVOURITES,
						org.skepsun.kototoro.backups.domain.BackupSection.BOOKMARKS,
						org.skepsun.kototoro.backups.domain.BackupSection.SOURCES,
						org.skepsun.kototoro.backups.domain.BackupSection.SETTINGS,
					)
					val zis = java.util.zip.ZipInputStream(java.io.FileInputStream(tempFile))
					try {
						repository.restoreBackup(zis, allSections, null)
					} finally {
						zis.close()
					}
					backupWebDavRestoreCoordinator.commitManualRestore()
				} finally {
					if (tempFile.exists()) tempFile.delete()
				}
			} catch (e: Exception) {
				e.printStackTraceDebug()
			}
		}
	}
}

private data class Quadruple<A, B, C, D>(
	val first: A,
	val second: B,
	val third: C,
	val fourth: D,
)

private data class Quintuple<A, B, C, D, E>(
	val first: A,
	val second: B,
	val third: C,
	val fourth: D,
	val fifth: E,
)

private data class Sextuple<A, B, C, D, E, F>(
	val first: A,
	val second: B,
	val third: C,
	val fourth: D,
	val fifth: E,
	val sixth: F,
)

private data class Septuple<A, B, C, D, E, F, G>(
	val first: A,
	val second: B,
	val third: C,
	val fourth: D,
	val fifth: E,
	val sixth: F,
	val seventh: G,
)

private data class Octuple<A, B, C, D, E, F, G, H>(
	val first: A,
	val second: B,
	val third: C,
	val fourth: D,
	val fifth: E,
	val sixth: F,
	val seventh: G,
	val eighth: H,
)

private fun HomeSourceOrigin.toBreakdown(count: Int?): HomeSourceBreakdown? {
	if (count == null || count <= 0) return null
	return HomeSourceBreakdown(
		origin = this,
		count = count,
	)
}

private fun Map<SourceGroup, Int>.countOf(key: OriginGroup): Int? {
	return this[SourceGroup.Origin(key)]
}

private fun Content.contentTab(): HomeContentTab? = source.getContentType().toHomeTab()

/**
 * Group items by content type tab, take [HOME_COVER_PREVIEW_LIMIT] from each group,
 * then select based on the chosen tab. Filtering by [sourceTags] occurs first.
 */
private fun List<HomeRecentItem>.selectHomeHistoryByTab(
	tab: HomeContentTab?,
	sourceTags: Set<org.skepsun.kototoro.explore.ui.model.SourceTag>,
	sourceGroupManager: org.skepsun.kototoro.core.jsonsource.SourceGroupManager,
	preset: org.skepsun.kototoro.explore.data.SourcePreset?,
): List<HomeRecentItem> {
	val filtered = filter { item ->
		item.content.matchesHomeFilters(
			tab = null,
			sourceTags = sourceTags,
			sourceGroupManager = sourceGroupManager,
			preset = preset,
		)
	}

	val limit = HOME_COVER_PREVIEW_LIMIT
	if (tab != null) {
		return filtered.filter { it.content.contentTab() == tab }.take(limit)
	}
	val taken = mutableSetOf<Long>()
	val countPerTab = mutableMapOf<HomeContentTab?, Int>()
	for (item in filtered) {
		val itemTab = item.content.contentTab()
		val current = countPerTab.getOrDefault(itemTab, 0)
		if (current < limit) {
			taken.add(item.groupKey)
			countPerTab[itemTab] = current + 1
		}
	}
	return filtered.filter { it.groupKey in taken }
}


/**
 * Same grouping logic for [HomeUpdateItem] items.
 */
private fun List<HomeUpdateItem>.selectHomeUpdatesByTab(
	tab: HomeContentTab?,
	sourceTags: Set<org.skepsun.kototoro.explore.ui.model.SourceTag>,
	sourceGroupManager: org.skepsun.kototoro.core.jsonsource.SourceGroupManager,
	preset: org.skepsun.kototoro.explore.data.SourcePreset?,
): List<HomeUpdateItem> {
	val filtered = filter { item ->
		item.content.matchesHomeFilters(
			tab = null,
			sourceTags = sourceTags,
			sourceGroupManager = sourceGroupManager,
			preset = preset,
		)
	}

	val limit = HOME_COVER_PREVIEW_LIMIT
	if (tab != null) {
		return filtered.filter { it.content.contentTab() == tab }.take(limit)
	}
	val taken = mutableSetOf<Long>()
	val countPerTab = mutableMapOf<HomeContentTab?, Int>()
	for (item in filtered) {
		val itemTab = item.content.contentTab()
		val current = countPerTab.getOrDefault(itemTab, 0)
		if (current < limit) {
			taken.add(item.groupKey)
			countPerTab[itemTab] = current + 1
		}
	}
	return filtered.filter { it.groupKey in taken }
}

private fun List<HomeRecommendationItem>.selectHomeRecommendationsByTab(
	tab: HomeContentTab?,
	sourceTags: Set<org.skepsun.kototoro.explore.ui.model.SourceTag>,
	sourceGroupManager: org.skepsun.kototoro.core.jsonsource.SourceGroupManager,
	preset: org.skepsun.kototoro.explore.data.SourcePreset?,
): List<HomeRecommendationItem> {
	val filtered = filter { item ->
		item.content.matchesHomeFilters(
			tab = null,
			sourceTags = sourceTags,
			sourceGroupManager = sourceGroupManager,
			preset = preset,
		)
	}

	val limit = HOME_COVER_PREVIEW_LIMIT
	if (tab != null) {
		return filtered.filter { it.content.contentTab() == tab }.take(limit)
	}
	val taken = mutableSetOf<Long>()
	val countPerTab = mutableMapOf<HomeContentTab?, Int>()
	for (item in filtered) {
		val itemTab = item.content.contentTab()
		val current = countPerTab.getOrDefault(itemTab, 0)
		if (current < limit) {
			taken.add(item.groupKey)
			countPerTab[itemTab] = current + 1
		}
	}
	return filtered.filter { it.groupKey in taken }
}

private fun HomeResumeState.filtered(
	tab: HomeContentTab?,
	sourceTags: Set<org.skepsun.kototoro.explore.ui.model.SourceTag>,
	sourceGroupManager: org.skepsun.kototoro.core.jsonsource.SourceGroupManager,
	preset: org.skepsun.kototoro.explore.data.SourcePreset?,
): HomeResumeState {
	val current = content ?: return this
	return if (
		current.matchesHomeFilters(
			tab = tab,
			sourceTags = sourceTags,
			sourceGroupManager = sourceGroupManager,
			preset = preset,
		)
	) {
		this
	} else {
		HomeResumeState()
	}
}

private fun HomeResumeState.filteredNsfw(isNsfwDisabled: Boolean): HomeResumeState {
	return if (isNsfwDisabled && content?.isNsfw() == true) HomeResumeState() else this
}

private fun HomeResumeState.withGroupKey(entityIdsByMangaId: Map<Long, Long>): HomeResumeState {
	val current = content ?: return this
	return copy(
		groupKey = entityIdsByMangaId[current.id]?.toHomeGroupKey() ?: current.id,
	)
}

private fun Content.matchesHomeFilters(
	tab: HomeContentTab?,
	sourceTags: Set<org.skepsun.kototoro.explore.ui.model.SourceTag>,
	sourceGroupManager: org.skepsun.kototoro.core.jsonsource.SourceGroupManager,
	preset: org.skepsun.kototoro.explore.data.SourcePreset?,
	excludeNsfw: Boolean = false,
): Boolean {
	if (excludeNsfw && isNsfw()) {
		return false
	}
	if (tab != null && contentTab() != tab) {
		return false
	}
	if (preset != null && source.name !in preset.sources) {
		return false
	}
	if (sourceTags.isEmpty()) {
		return true
	}
	val contentGroup = sourceGroupManager.getContentGroup(source)
	val originGroup = sourceGroupManager.getOriginGroup(source)
	return sourceTags.any { it.matches(contentGroup, originGroup) }
}

private fun List<Content>.aggregateHomeContentByEntity(entityIdsByMangaId: Map<Long, Long>): List<HomeRecentItem> {
	if (isEmpty()) {
		return emptyList()
	}
	val result = ArrayList<HomeRecentItem>(size)
	val seen = LinkedHashSet<Long>()
	for (item in this) {
		val groupKey = entityIdsByMangaId[item.id]?.toHomeGroupKey() ?: item.id
		if (seen.add(groupKey)) {
			result += HomeRecentItem(
				content = item,
				groupKey = groupKey,
			)
		}
	}
	return result
}

private fun List<ContentTracking>.aggregateHomeUpdatesByEntity(entityIdsByMangaId: Map<Long, Long>): List<HomeUpdateItem> {
	if (isEmpty()) {
		return emptyList()
	}
	val grouped = LinkedHashMap<Long, HomeUpdateItem>()
	for (item in this) {
		val groupKey = entityIdsByMangaId[item.manga.id]?.toHomeGroupKey() ?: item.manga.id
		val existing = grouped[groupKey]
		grouped[groupKey] = if (existing == null) {
			HomeUpdateItem(
				content = item.manga,
				newChapters = item.newChapters,
				groupKey = groupKey,
			)
		} else {
			existing.copy(
				newChapters = existing.newChapters + item.newChapters,
			)
		}
	}
	return grouped.values.toList()
}

private fun List<Content>.aggregateHomeRecommendationsByEntity(entityIdsByMangaId: Map<Long, Long>): List<HomeRecommendationItem> {
	if (isEmpty()) {
		return emptyList()
	}
	val result = ArrayList<HomeRecommendationItem>(size)
	val seen = LinkedHashSet<Long>()
	for (item in this) {
		val groupKey = entityIdsByMangaId[item.id]?.toHomeGroupKey() ?: item.id
		if (seen.add(groupKey)) {
			result += HomeRecommendationItem(
				content = item,
				groupKey = groupKey,
			)
		}
	}
	return result
}

private suspend fun List<HomeRecentItem>.withHomeRecentGridModels(
	contentListMapper: ContentListMapper,
): List<HomeRecentItem> {
	if (isEmpty()) return this
	val modelsById = contentListMapper
		.toListModelList(map { it.content }, ListMode.GRID)
		.filterIsInstance<ContentGridModel>()
		.associateBy { it.manga.id }
	return map { item -> item.copy(cardModel = modelsById[item.content.id]) }
}

private suspend fun List<HomeUpdateItem>.withHomeUpdateGridModels(
	contentListMapper: ContentListMapper,
): List<HomeUpdateItem> {
	if (isEmpty()) return this
	val modelsById = contentListMapper
		.toListModelList(map { it.content }, ListMode.GRID)
		.filterIsInstance<ContentGridModel>()
		.associateBy { it.manga.id }
	return map { item -> item.copy(cardModel = modelsById[item.content.id]) }
}

private suspend fun List<HomeRecommendationItem>.withHomeRecommendationGridModels(
	contentListMapper: ContentListMapper,
): List<HomeRecommendationItem> {
	if (isEmpty()) return this
	val modelsById = contentListMapper
		.toListModelList(map { it.content }, ListMode.GRID)
		.filterIsInstance<ContentGridModel>()
		.associateBy { it.manga.id }
	return map { item -> item.copy(cardModel = modelsById[item.content.id]) }
}

private fun Long.toHomeGroupKey(): Long = -this

private fun ContentType.toHomeTab(): HomeContentTab? = when (this) {
	ContentType.NOVEL,
	ContentType.HENTAI_NOVEL -> HomeContentTab.NOVEL

	ContentType.VIDEO,
	ContentType.HENTAI_VIDEO -> HomeContentTab.VIDEO

	ContentType.MANGA,
	ContentType.HENTAI_MANGA,
	ContentType.COMICS,
	ContentType.MANHWA,
	ContentType.MANHUA,
	ContentType.ONE_SHOT,
	ContentType.DOUJINSHI,
	ContentType.IMAGE_SET,
	ContentType.ARTIST_CG,
	ContentType.GAME_CG,
	ContentType.OTHER -> HomeContentTab.MANGA
}

private fun org.skepsun.kototoro.core.model.ContentHistory?.toProgressPercent(): Int? {
	val percent = this?.percent ?: return null
	return (percent * 100f).toInt().coerceIn(0, 100)
}
