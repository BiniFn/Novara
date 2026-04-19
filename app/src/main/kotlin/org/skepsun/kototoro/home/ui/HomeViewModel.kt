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
import org.skepsun.kototoro.favourites.domain.FavouritesRepository
import org.skepsun.kototoro.history.data.HistoryRepository
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.suggestions.domain.SuggestionRepository
import org.skepsun.kototoro.tracker.domain.TrackingRepository
import org.skepsun.kototoro.tracker.domain.model.ContentTracking
import javax.inject.Inject

data class HomeRecentItem(
	val content: Content,
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
) {
	val title: String
		get() = content.title
}

data class HomeRecommendationItem(
	val content: Content,
) {
	val title: String
		get() = content.title
}

data class HomeResumeState(
	val content: Content? = null,
	val progressPercent: Int? = null,
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
	val enabledSourcesCount: Int = 0,
	val sourceBreakdown: List<HomeSourceBreakdown> = emptyList(),
	val syncState: HomeSyncState = HomeSyncState(),
	val selectedSourceTags: Set<org.skepsun.kototoro.explore.ui.model.SourceTag> = emptySet(),
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
	private val sourceGroupManager: org.skepsun.kototoro.core.jsonsource.SourceGroupManager,
	private val globalFavoritesState: org.skepsun.kototoro.favourites.domain.GlobalFavoritesState,
	private val sourcePresetsRepository: org.skepsun.kototoro.explore.data.SourcePresetsRepository,
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
	private val lastHistoryContentFlow = historyRepository.observeLast()
	private val resumeStateFlow = lastHistoryContentFlow
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
	private val recentHistoryFlow = historyRepository.observeAll()
	private val favoritesCountFlow = favouritesRepository.observeContentCount()
	private val favoriteCategoriesCountFlow = favouritesRepository.observeCategories().map { it.size }
	private val unreadUpdatesCountFlow = trackingRepository.observeUnreadUpdatesCount()
	private val recentUpdatesFlow = trackingRepository.observeUpdatedContent(limit = HOME_COVER_PREVIEW_LIMIT * 12, filterOptions = emptySet())
	private val recommendationsFlow = suggestionRepository.observeAll()
	private val isTrackerNsfwDisabledFlow = settings.observeAsFlow(AppSettings.KEY_TRACKER_NO_NSFW) { isTrackerNsfwDisabled }
	private val isSuggestionNsfwDisabledFlow = settings.observeAsFlow(AppSettings.KEY_SUGGESTIONS_EXCLUDE_NSFW) { isSuggestionsExcludeNsfw }
	private val isHistoryNsfwDisabledFlow = settings.observeAsFlow(AppSettings.KEY_HISTORY_EXCLUDE_NSFW) { isHistoryExcludeNsfw }
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
			) { favoriteCategoriesCount, unreadUpdatesCount, recentUpdates, recommendations ->
				Quadruple(favoriteCategoriesCount, unreadUpdatesCount, recentUpdates, recommendations)
			},
		) { left, right ->
			Septuple(
				left.first,
				left.second,
				left.third,
				right.first,
				right.second,
				right.third,
				right.fourth,
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

		val resumeState = left.first.filtered(selectedTab).filteredNsfw(isHistoryNsfwDisabled)
		val allHistory = if (isHistoryNsfwDisabled) left.second.filterNot { it.isNsfw() } else left.second
		val recentHistory = allHistory.groupByTabThenSelect(selectedTab, selectedSourceTags, sourceGroupManager, preset)
		val favoritesCount = left.third
		val favoriteCategoriesCount = left.fourth
		val unreadUpdatesCount = left.fifth
		val allUpdates = if (isTrackerNsfwDisabled) left.sixth.filterNot { it.manga.isNsfw() } else left.sixth
		val recentUpdates = allUpdates.groupTrackingsByTabThenSelect(selectedTab, selectedSourceTags, sourceGroupManager, preset)
		val allRecommendations = if (isSuggestionNsfwDisabled) left.seventh.filterNot { it.isNsfw() } else left.seventh
		val recommendations = allRecommendations.groupByTabThenSelect(selectedTab, selectedSourceTags, sourceGroupManager, preset)

		val actualHistoryCount = allHistory.count { item ->
			val source = item.source
			if (preset != null && source.name !in preset.sources) return@count false
			if (selectedTab != null && item.contentTab() != selectedTab) return@count false
			if (selectedSourceTags.isNotEmpty()) {
				val contentGroup = sourceGroupManager.getContentGroup(source)
				val originGroup = sourceGroupManager.getOriginGroup(source)
				if (!selectedSourceTags.any { it.matches(contentGroup, originGroup) }) return@count false
			}
			true
		}

		val actualRecommendationsCount = allRecommendations.count { item ->
			val source = item.source
			if (preset != null && source.name !in preset.sources) return@count false
			if (selectedTab != null && item.contentTab() != selectedTab) return@count false
			if (selectedSourceTags.isNotEmpty()) {
				val contentGroup = sourceGroupManager.getContentGroup(source)
				val originGroup = sourceGroupManager.getOriginGroup(source)
				if (!selectedSourceTags.any { it.matches(contentGroup, originGroup) }) return@count false
			}
			true
		}
		val enabledSourcesCount = right.first
		val sourceBreakdown = right.second
		val syncState = right.third

		HomeSummaryState(
			selectedTab = selectedTab,
			recentHistoryCount = actualHistoryCount,
			recentHistoryItems = recentHistory.take(HOME_COVER_PREVIEW_LIMIT).map { HomeRecentItem(it) },
			resumeState = resumeState,
			favoritesCount = favoritesCount,
			favoriteCategoriesCount = favoriteCategoriesCount,
			unreadUpdatesCount = unreadUpdatesCount,
			recentUpdates = recentUpdates.take(HOME_COVER_PREVIEW_LIMIT).map { it.toHomeUpdateItem() },
			recommendationsCount = actualRecommendationsCount,
			recommendations = recommendations.take(HOME_COVER_PREVIEW_LIMIT).map { HomeRecommendationItem(it) },
			enabledSourcesCount = enabledSourcesCount,
			sourceBreakdown = sourceBreakdown,
			syncState = syncState,
			selectedSourceTags = selectedSourceTags,
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

private fun ContentTracking.toHomeUpdateItem(): HomeUpdateItem {
	return HomeUpdateItem(
		content = manga,
		newChapters = newChapters,
	)
}


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
private fun List<Content>.groupByTabThenSelect(
	tab: HomeContentTab?,
	sourceTags: Set<org.skepsun.kototoro.explore.ui.model.SourceTag>,
	sourceGroupManager: org.skepsun.kototoro.core.jsonsource.SourceGroupManager,
	preset: org.skepsun.kototoro.explore.data.SourcePreset?,
): List<Content> {
	val filtered = if (sourceTags.isEmpty() && preset == null) this else filter { item ->
		val source = item.source
		if (preset != null && source.name !in preset.sources) return@filter false
		if (sourceTags.isEmpty()) return@filter true
		val contentGroup = sourceGroupManager.getContentGroup(source)
		val originGroup = sourceGroupManager.getOriginGroup(source)
		sourceTags.any { it.matches(contentGroup, originGroup) }
	}

	val limit = HOME_COVER_PREVIEW_LIMIT
	if (tab != null) {
		// Specific tab: filter and take limit
		return filtered.filter { it.contentTab() == tab }.take(limit)
	}
	// All tabs: take limit from each type, then merge preserving original order
	val taken = mutableSetOf<Content>()
	val countPerTab = mutableMapOf<HomeContentTab?, Int>()
	for (item in filtered) {
		val itemTab = item.contentTab()
		val current = countPerTab.getOrDefault(itemTab, 0)
		if (current < limit) {
			taken.add(item)
			countPerTab[itemTab] = current + 1
		}
	}
	// Return in original order (already sorted by recency from DB)
	return filtered.filter { it in taken }
}


/**
 * Same grouping logic for [ContentTracking] items.
 */
@JvmName("groupTrackingsByTab")
private fun List<ContentTracking>.groupTrackingsByTabThenSelect(
	tab: HomeContentTab?,
	sourceTags: Set<org.skepsun.kototoro.explore.ui.model.SourceTag>,
	sourceGroupManager: org.skepsun.kototoro.core.jsonsource.SourceGroupManager,
	preset: org.skepsun.kototoro.explore.data.SourcePreset?,
): List<ContentTracking> {
	val filtered = if (sourceTags.isEmpty() && preset == null) this else filter { item ->
		val source = item.manga.source
		if (preset != null && source.name !in preset.sources) return@filter false
		if (sourceTags.isEmpty()) return@filter true
		val contentGroup = sourceGroupManager.getContentGroup(source)
		val originGroup = sourceGroupManager.getOriginGroup(source)
		sourceTags.any { it.matches(contentGroup, originGroup) }
	}

	val limit = HOME_COVER_PREVIEW_LIMIT
	if (tab != null) {
		return filtered.filter { it.manga.contentTab() == tab }.take(limit)
	}
	val taken = mutableSetOf<ContentTracking>()
	val countPerTab = mutableMapOf<HomeContentTab?, Int>()
	for (item in filtered) {
		val itemTab = item.manga.contentTab()
		val current = countPerTab.getOrDefault(itemTab, 0)
		if (current < limit) {
			taken.add(item)
			countPerTab[itemTab] = current + 1
		}
	}
	return filtered.filter { it in taken }
}

private fun HomeResumeState.filtered(tab: HomeContentTab?): HomeResumeState {
	return if (tab == null || content?.contentTab() == tab) this else HomeResumeState()
}

private fun HomeResumeState.filteredNsfw(isNsfwDisabled: Boolean): HomeResumeState {
	return if (isNsfwDisabled && content?.isNsfw() == true) HomeResumeState() else this
}

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
