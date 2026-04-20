package org.skepsun.kototoro.suggestions.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.parser.ContentDataRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsFlow
import org.skepsun.kototoro.core.util.ext.onFirst
import org.skepsun.kototoro.list.domain.ContentListMapper
import org.skepsun.kototoro.list.domain.QuickFilterListener
import org.skepsun.kototoro.list.ui.ContentListViewModel
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.list.ui.model.toErrorState
import org.skepsun.kototoro.suggestions.domain.SuggestionRepository
import org.skepsun.kototoro.suggestions.domain.SuggestionsListQuickFilter
import javax.inject.Inject
import org.skepsun.kototoro.local.data.LocalStorageChanges
import org.skepsun.kototoro.local.domain.model.LocalContent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.skepsun.kototoro.core.jsonsource.SourceGroupManager
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.core.model.isNsfw

@HiltViewModel
class SuggestionsViewModel @Inject constructor(
	repository: SuggestionRepository,
	settings: AppSettings,
	private val mangaListMapper: ContentListMapper,
	private val quickFilter: SuggestionsListQuickFilter,
	private val suggestionsScheduler: SuggestionsWorker.Scheduler,
	private val sourceGroupManager: SourceGroupManager,
	mangaDataRepository: ContentDataRepository,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalContent?>,
	private val globalFavoritesState: org.skepsun.kototoro.favourites.domain.GlobalFavoritesState,
) : ContentListViewModel(settings, mangaDataRepository, localStorageChanges), QuickFilterListener by quickFilter {

	override val isFilterBarVisible = MutableStateFlow(true)

	override val listMode = settings.observeAsFlow(AppSettings.KEY_LIST_MODE) { this.listMode }
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, settings.listMode)

	override val currentGroupTab = globalFavoritesState.selectedGroupTab
	override val currentSourceTags = globalFavoritesState.selectedSourceTags

	override fun setSelectedGroupTab(tab: BrowseGroupTab) {
		globalFavoritesState.setSelectedGroupTab(tab)
	}

	override fun setSelectedSourceTags(tags: Set<SourceTag>) {
		globalFavoritesState.setSelectedSourceTags(tags)
	}

	override val content = combine(
		quickFilter.appliedOptions.combineWithSettings().flatMapLatest { repository.observeAll(0, it) },
		quickFilter.appliedOptions,
		observeListModeWithTriggers(),
		selectedGroupTab,
		selectedSourceTags,
	) { values: Array<Any?> ->
		val list = values[0] as List<Content>
		val filters = values[1] as Set<ListFilterOption>
		val mode = values[2] as ListMode
		val groupTab = values[3] as BrowseGroupTab
		val sourceTags = values[4] as Set<SourceTag>
		val filteredList = list.filter { manga ->
			val source = manga.source
			val contentGroup = sourceGroupManager.getContentGroup(source)
			val originGroup = sourceGroupManager.getOriginGroup(source)

			val groupMatches = groupTab.matchesContentGroup(contentGroup) && groupTab.matchesOriginGroup(originGroup)
			val originMatches = if (sourceTags.isEmpty()) {
				true
			} else {
				sourceTags.any { it.matches(contentGroup, originGroup) }
			}

			groupMatches && originMatches
		}

		val hideAdult = settings.isSuggestionsExcludeNsfw
		val visibleList = if (hideAdult) filteredList.filterNot { it.isNsfw() } else filteredList

		val resultList = ArrayList<ListModel>()

		if (visibleList.isEmpty()) {
			if (filters.isEmpty() && groupTab == BrowseGroupTab.All && sourceTags.isEmpty()) {
				resultList.add(
					EmptyState(
						icon = R.drawable.ic_empty_common,
						textPrimary = R.string.nothing_found,
						textSecondary = R.string.text_suggestion_holder,
						actionStringRes = 0,
					)
				)
			} else {
				quickFilter.filterItem(filters)?.let { resultList.add(it) }
				resultList.add(
					EmptyState(
						icon = R.drawable.ic_empty_common,
						textPrimary = R.string.nothing_found,
						textSecondary = R.string.text_empty_holder_secondary_filtered,
						actionStringRes = 0,
					)
				)
			}
		} else {
			quickFilter.filterItem(filters)?.let { resultList.add(it) }
			mangaListMapper.toListModelList(resultList, visibleList, mode)
		}
		resultList as List<ListModel>
	}.onStart {
		loadingCounter.increment()
	}.onFirst {
		loadingCounter.decrement()
	}.catch {
		emit(listOf<ListModel>(it.toErrorState(canRetry = false)))
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState))

	override fun onRefresh() = Unit

	override fun onRetry() = Unit

	fun updateSuggestions() {
		launchJob(Dispatchers.Default) {
			suggestionsScheduler.startNow()
		}
	}
}
