package org.skepsun.kototoro.explore.ui

import androidx.collection.LongSet
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.jsonsource.SourceGroupManager
import org.skepsun.kototoro.core.model.MangaSourceInfo
import org.skepsun.kototoro.core.os.AppShortcutManager
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsFlow
import org.skepsun.kototoro.core.prefs.observeAsStateFlow
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.ui.util.ReversibleAction
import org.skepsun.kototoro.core.util.ext.MutableEventFlow
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.core.util.ext.combine
import org.skepsun.kototoro.explore.data.MangaSourcesRepository
import org.skepsun.kototoro.explore.domain.ExploreRepository
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.ExploreButtons
import org.skepsun.kototoro.explore.ui.model.SourceFilter
import org.skepsun.kototoro.explore.ui.model.MangaSourceItem
import org.skepsun.kototoro.explore.ui.model.RecommendationsItem
import org.skepsun.kototoro.list.ui.model.EmptyHint
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.list.ui.model.MangaCompactListModel
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import org.skepsun.kototoro.suggestions.domain.SuggestionRepository
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
	private val settings: AppSettings,
	private val suggestionRepository: SuggestionRepository,
	private val exploreRepository: ExploreRepository,
	private val sourcesRepository: MangaSourcesRepository,
	private val shortcutManager: AppShortcutManager,
	private val sourceGroupManager: SourceGroupManager,
) : BaseViewModel() {

	val isGrid = settings.observeAsStateFlow(
		key = AppSettings.KEY_SOURCES_GRID,
		scope = viewModelScope + Dispatchers.IO,
		valueProducer = { isSourcesGridMode },
	)

	val isAllSourcesEnabled = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_SOURCES_ENABLED_ALL,
		valueProducer = { isAllSourcesEnabled },
	)

	private val isSuggestionsEnabled = settings.observeAsFlow(
		key = AppSettings.KEY_SUGGESTIONS,
		valueProducer = { isSuggestionsEnabled },
	)

	val onOpenManga = MutableEventFlow<Manga>()
	val onActionDone = MutableEventFlow<ReversibleAction>()
	val onShowSuggestionsTip = MutableEventFlow<Unit>()
	private val isRandomLoading = MutableStateFlow(false)
	
	/**
	 * Currently selected browse group tab
	 */
	private val selectedGroupTab = MutableStateFlow<BrowseGroupTab>(BrowseGroupTab.All)
	
	/**
	 * Observable selected group tab for UI
	 */
	val currentGroupTab: StateFlow<BrowseGroupTab> = selectedGroupTab

	val content: StateFlow<List<ListModel>> = isLoading.flatMapLatest { loading: Boolean ->
		if (loading) {
			flowOf<List<ListModel>>(getLoadingStateList())
		} else {
			createContentFlow()
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, getLoadingStateList())
	
	/**
	 * Set the selected group tab and filter sources accordingly
	 */
	fun setSelectedGroupTab(tab: BrowseGroupTab) {
		selectedGroupTab.value = tab
		// Save preference
		settings.setSelectedGroupTab(tab.id)
	}
	
	/**
	 * Get the currently selected group tab
	 */
	fun getSelectedGroupTab(): BrowseGroupTab = selectedGroupTab.value

	init {
		launchJob(Dispatchers.Default) {
			if (!settings.isSuggestionsEnabled && settings.isTipEnabled(TIP_SUGGESTIONS)) {
				onShowSuggestionsTip.call(Unit)
			}
		}
		
		// Restore saved group tab preference
		val savedTabId = settings.getSelectedGroupTab()
		selectedGroupTab.value = BrowseGroupTab.fromId(savedTabId ?: BrowseGroupTab.All.id)
	}

	fun openRandom() {
		if (isRandomLoading.value) {
			return
		}
		launchJob(Dispatchers.Default) {
			isRandomLoading.value = true
			try {
				val manga = exploreRepository.findRandomManga(tagsLimit = 8)
				onOpenManga.call(manga)
			} finally {
				isRandomLoading.value = false
			}
		}
	}

	fun disableSources(sources: Collection<MangaSource>) {
		launchJob(Dispatchers.Default) {
			val rollback = sourcesRepository.setSourcesEnabled(sources, isEnabled = false)
			val message = if (sources.size == 1) R.string.source_disabled else R.string.sources_disabled
			onActionDone.call(ReversibleAction(message, rollback))
		}
	}

	fun requestPinShortcut(source: MangaSource) {
		launchLoadingJob(Dispatchers.Default) {
			shortcutManager.requestPinShortcut(source)
		}
	}

	fun setSourcesPinned(sources: Collection<MangaSource>, isPinned: Boolean) {
		launchJob(Dispatchers.Default) {
			sourcesRepository.setIsPinned(sources, isPinned)
			val message = if (sources.size == 1) {
				if (isPinned) R.string.source_pinned else R.string.source_unpinned
			} else {
				if (isPinned) R.string.sources_pinned else R.string.sources_unpinned
			}
			onActionDone.call(ReversibleAction(message, null))
		}
	}

	fun respondSuggestionTip(isAccepted: Boolean) {
		settings.isSuggestionsEnabled = isAccepted
		settings.closeTip(TIP_SUGGESTIONS)
	}

	fun sourcesSnapshot(ids: LongSet): List<MangaSourceInfo> {
		return content.value.mapNotNull {
			(it as? MangaSourceItem)?.takeIf { x -> x.id in ids }?.source
		}
	}

	private fun createContentFlow() = combine(
			sourcesRepository.observeEnabledSources(),
			getSuggestionFlow(),
			isGrid,
			isRandomLoading,
			isAllSourcesEnabled,
			sourcesRepository.observeHasNewSourcesForBadge(),
			selectedGroupTab,
		) { content, suggestions, grid, randomLoading, allSourcesEnabled, newSources, groupTab ->
			buildList(
				content,
				suggestions,
				grid,
				randomLoading,
				allSourcesEnabled,
				newSources,
				groupTab,
			)
		}.withErrorHandling()

	private fun buildList(
		sources: List<MangaSourceInfo>,
		recommendation: List<Manga>,
		isGrid: Boolean,
		randomLoading: Boolean,
		allSourcesEnabled: Boolean,
		hasNewSources: Boolean,
		groupTab: BrowseGroupTab,
	): List<ListModel> {
		// Apply group tab filtering
		val filteredSources = applyGroupTabFilter(sources, groupTab)
		
		val result = ArrayList<ListModel>(filteredSources.size + 3)
		result += ExploreButtons(randomLoading)
		if (recommendation.isNotEmpty()) {
			result += ListHeader(R.string.suggestions, R.string.more, R.id.nav_suggestions)
			result += RecommendationsItem(recommendation.toRecommendationList())
		}
		if (filteredSources.isNotEmpty()) {
			result += ListHeader(
				textRes = R.string.remote_sources,
				buttonTextRes = if (allSourcesEnabled) R.string.manage else R.string.catalog,
				badge = if (!allSourcesEnabled && hasNewSources) "" else null,
			)
			filteredSources.mapTo(result) { MangaSourceItem(it, isGrid) }
		} else {
			result += EmptyHint(
				icon = R.drawable.ic_empty_common,
				textPrimary = R.string.no_manga_sources,
				textSecondary = R.string.no_manga_sources_text,
				actionStringRes = R.string.catalog,
			)
		}
		return result
	}
	
	/**
	 * Apply group tab filtering to sources
	 * 
	 * Filters sources based on the selected browse group tab:
	 * - All: Show all sources
	 * - Manga/Novel/Video: Filter by content type
	 * - JsonSources: Filter by origin (Legado/TVBox JSON sources)
	 * 
	 * @param sources The complete list of sources to filter
	 * @param groupTab The selected browse group tab
	 * @return Filtered list of sources that match the tab criteria
	 */
	private fun applyGroupTabFilter(
		sources: List<MangaSourceInfo>,
		groupTab: BrowseGroupTab,
	): List<MangaSourceInfo> {
		android.util.Log.d("ExploreViewModel", "applyGroupTabFilter: total sources=${sources.size}, groupTab=$groupTab")
		
		val filtered = sources.filter { sourceInfo ->
			val source = sourceInfo.mangaSource
			val contentGroup = sourceGroupManager.getContentGroup(source)
			val originGroup = sourceGroupManager.getOriginGroup(source)
			
			// Apply group tab filter
			val passes = groupTab.matchesContentGroup(contentGroup) && 
				groupTab.matchesOriginGroup(originGroup)
			
			if (!passes) {
				android.util.Log.v("ExploreViewModel", "  Filtered out: ${source.name} (contentGroup=$contentGroup, originGroup=$originGroup)")
			}
			
			passes
		}
		
		android.util.Log.d("ExploreViewModel", "applyGroupTabFilter: filtered sources=${filtered.size}")
		if (filtered.isEmpty() && sources.isNotEmpty()) {
			android.util.Log.w("ExploreViewModel", "All sources were filtered out by tab! tab=$groupTab")
		}
		return filtered
	}

	private fun getLoadingStateList() = listOf(
		ExploreButtons(isRandomLoading.value),
		LoadingState,
	)

	private fun getSuggestionFlow() = isSuggestionsEnabled.mapLatest { isEnabled ->
		if (isEnabled) {
			runCatchingCancellable {
				suggestionRepository.getRandomList(SUGGESTIONS_COUNT)
			}.getOrDefault(emptyList())
		} else {
			emptyList()
		}
	}

	private fun List<Manga>.toRecommendationList() = map { manga ->
		MangaCompactListModel(
			manga = manga,
			override = null,
			subtitle = manga.tags.joinToString { it.title },
			counter = 0,
		)
	}

	companion object {

		private const val TIP_SUGGESTIONS = "suggestions"
		private const val SUGGESTIONS_COUNT = 8
	}
}
