package org.skepsun.kototoro.home.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.core.jsonsource.OriginGroup
import org.skepsun.kototoro.core.jsonsource.SourceGroup
import org.skepsun.kototoro.core.model.getContentType
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
import org.skepsun.kototoro.tracking.discovery.domain.PreferredTrackingSiteProvider
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
	JAVASCRIPT,
	EXTERNAL,
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
	val selectedTab: HomeContentTab = HomeContentTab.MANGA,
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
	val preferredTrackingSite: ScrobblerService = ScrobblerService.BANGUMI,
	val syncState: HomeSyncState = HomeSyncState(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
	historyRepository: HistoryRepository,
	favouritesRepository: FavouritesRepository,
	trackingRepository: TrackingRepository,
	suggestionRepository: SuggestionRepository,
	contentSourcesRepository: ContentSourcesRepository,
	preferredTrackingSiteProvider: PreferredTrackingSiteProvider,
	private val exploreRepository: org.skepsun.kototoro.explore.domain.ExploreRepository,
	private val settings: AppSettings,
	private val webDavUploader: WebDavBackupUploader,
	private val backupWebDavUploadCoordinator: BackupWebDavUploadCoordinator,
	private val backupWebDavRestoreCoordinator: BackupWebDavRestoreCoordinator,
	private val backupStorage: ExternalBackupStorage,
	private val repository: BackupRepository,
	@ApplicationContext private val appContext: Context,
) : ViewModel() {

	private val selectedTabFlow = MutableStateFlow(HomeContentTab.MANGA)
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
	private val recentHistoryFlow = historyRepository.observeAll(limit = HOME_COVER_PREVIEW_LIMIT)
	private val favoritesCountFlow = favouritesRepository.observeContentCount()
	private val favoriteCategoriesCountFlow = favouritesRepository.observeCategories().map { it.size }
	private val unreadUpdatesCountFlow = trackingRepository.observeUnreadUpdatesCount()
	private val recentUpdatesFlow = trackingRepository.observeUpdatedContent(limit = HOME_COVER_PREVIEW_LIMIT, filterOptions = emptySet())
	private val recommendationsFlow = suggestionRepository.observeAll()
	private val enabledSourcesCountFlow = contentSourcesRepository.observeEnabledSourcesCount()
	private val sourceBreakdownFlow = contentSourcesRepository.observeGroupCounts()
		.map { counts ->
			listOfNotNull(
				HomeSourceOrigin.BUILT_IN.toBreakdown(counts.countOf(OriginGroup.NATIVE)),
				HomeSourceOrigin.MIHON.toBreakdown(counts.countOf(OriginGroup.MIHON)),
				HomeSourceOrigin.ANIYOMI.toBreakdown(counts.countOf(OriginGroup.ANIYOMI)),
				HomeSourceOrigin.LEGADO.toBreakdown(counts.countOf(OriginGroup.LEGADO_JSON)),
				HomeSourceOrigin.TVBOX.toBreakdown(counts.countOf(OriginGroup.TVBOX_JSON)),
				HomeSourceOrigin.JAVASCRIPT.toBreakdown(counts.countOf(OriginGroup.JS_JSON)),
				HomeSourceOrigin.EXTERNAL.toBreakdown(counts.countOf(OriginGroup.EXTERNAL)),
			).sortedByDescending { it.count }
				.take(3)
		}
	private val preferredTrackingSiteFlow = preferredTrackingSiteProvider.preferredSite

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
			preferredTrackingSiteFlow,
			syncStateFlow,
		) { enabledSourcesCount, sourceBreakdown, preferredTrackingSite, syncState ->
			Quadruple(enabledSourcesCount, sourceBreakdown, preferredTrackingSite, syncState)
		},
	) { selectedTab, left, right ->
		val resumeState = left.first.filtered(selectedTab)
		val recentHistory = left.second.filterContentsByTab(selectedTab)
		val favoritesCount = left.third
		val favoriteCategoriesCount = left.fourth
		val unreadUpdatesCount = left.fifth
		val recentUpdates = left.sixth.filterTrackingsByTab(selectedTab)
		val recommendations = left.seventh.filterContentsByTab(selectedTab)
		val enabledSourcesCount = right.first
		val sourceBreakdown = right.second
		val preferredTrackingSite = right.third
		val syncState = right.fourth

		HomeSummaryState(
			selectedTab = selectedTab,
			recentHistoryCount = recentHistory.size,
			recentHistoryItems = recentHistory.take(HOME_COVER_PREVIEW_LIMIT).map { HomeRecentItem(it) },
			resumeState = resumeState,
			favoritesCount = favoritesCount,
			favoriteCategoriesCount = favoriteCategoriesCount,
			unreadUpdatesCount = unreadUpdatesCount,
			recentUpdates = recentUpdates.take(HOME_COVER_PREVIEW_LIMIT).map { it.toHomeUpdateItem() },
			recommendationsCount = recommendations.size,
			recommendations = recommendations.take(HOME_COVER_PREVIEW_LIMIT).map { HomeRecommendationItem(it) },
			enabledSourcesCount = enabledSourcesCount,
			sourceBreakdown = sourceBreakdown,
			preferredTrackingSite = preferredTrackingSite,
			syncState = syncState,
		)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = HomeSummaryState(),
	)

	fun setSelectedTab(tab: HomeContentTab) {
		selectedTabFlow.value = tab
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

private fun Content.filterMatches(tab: HomeContentTab): Boolean {
	return source.getContentType().toHomeTab() == tab
}

private fun List<Content>.filterContentsByTab(tab: HomeContentTab): List<Content> {
	return filter { it.filterMatches(tab) }
}

private fun List<ContentTracking>.filterTrackingsByTab(tab: HomeContentTab): List<ContentTracking> {
	return filter { it.manga.filterMatches(tab) }
}

private fun HomeResumeState.filtered(tab: HomeContentTab): HomeResumeState {
	return if (content?.filterMatches(tab) == true) this else HomeResumeState()
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
