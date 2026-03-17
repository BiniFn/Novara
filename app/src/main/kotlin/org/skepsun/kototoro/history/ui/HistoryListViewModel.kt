package org.skepsun.kototoro.history.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.ContentHistory
import org.skepsun.kototoro.core.parser.ContentDataRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.prefs.observeAsFlow
import org.skepsun.kototoro.core.prefs.observeAsStateFlow
import org.skepsun.kototoro.core.ui.util.ReversibleAction
import org.skepsun.kototoro.core.util.ext.calculateTimeAgo
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.core.util.ext.flattenLatest
import org.skepsun.kototoro.history.data.HistoryRepository
import org.skepsun.kototoro.history.domain.HistoryListQuickFilter
import org.skepsun.kototoro.history.domain.MarkAsReadUseCase
import org.skepsun.kototoro.history.domain.model.ContentWithHistory
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.list.domain.ListSortOrder
import org.skepsun.kototoro.list.domain.ContentListMapper
import org.skepsun.kototoro.list.domain.QuickFilterListener
import org.skepsun.kototoro.list.domain.ReadingProgress
import org.skepsun.kototoro.list.ui.ContentListViewModel
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.InfoModel
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.list.ui.model.toErrorState
import org.skepsun.kototoro.parsers.model.Content
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import org.skepsun.kototoro.local.data.LocalStorageChanges
import org.skepsun.kototoro.local.domain.model.LocalContent
import kotlinx.coroutines.flow.SharedFlow
import org.skepsun.kototoro.core.jsonsource.SourceGroupManager
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag

private const val PAGE_SIZE = 16

@HiltViewModel
class HistoryListViewModel @Inject constructor(
	private val repository: HistoryRepository,
	settings: AppSettings,
	private val mangaListMapper: ContentListMapper,
	private val markAsReadUseCase: MarkAsReadUseCase,
	private val quickFilter: HistoryListQuickFilter,
	private val sourceGroupManager: SourceGroupManager,
	private val globalFavoritesState: org.skepsun.kototoro.favourites.domain.GlobalFavoritesState,
	mangaDataRepository: ContentDataRepository,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalContent?>,
) : ContentListViewModel(settings, mangaDataRepository, localStorageChanges), QuickFilterListener by quickFilter {

	override val isFilterBarVisible = MutableStateFlow(true)

	override val currentGroupTab = globalFavoritesState.selectedGroupTab
	override val currentSourceTags = globalFavoritesState.selectedSourceTags

	override fun setSelectedGroupTab(tab: BrowseGroupTab) {
		globalFavoritesState.setSelectedGroupTab(tab)
	}

	override fun setSelectedSourceTags(tags: Set<SourceTag>) {
		globalFavoritesState.setSelectedSourceTags(tags)
	}

	private val sortOrder: StateFlow<ListSortOrder> = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_HISTORY_ORDER,
		valueProducer = { historySortOrder },
	)

	override val listMode = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.Default,
		key = AppSettings.KEY_LIST_MODE_HISTORY,
		valueProducer = { historyListMode },
	)

	private val isGroupingEnabled = settings.observeAsFlow(
		key = AppSettings.KEY_HISTORY_GROUPING,
		valueProducer = { isHistoryGroupingEnabled },
	).combine(sortOrder) { g, s ->
		g && s.isGroupingSupported()
	}

	private val limit = MutableStateFlow(PAGE_SIZE)
	private val isPaginationReady = AtomicBoolean(false)

	val isStatsEnabled = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.Default,
		key = AppSettings.KEY_STATS_ENABLED,
		valueProducer = { isStatsEnabled },
	)

	override val content = combine(
		quickFilter.appliedOptions,
		observeHistory(),
		isGroupingEnabled,
		observeListModeWithTriggers(),
		settings.observeAsFlow(AppSettings.KEY_INCOGNITO_MODE) { isIncognitoModeEnabled },
		this.currentGroupTab,
		this.currentSourceTags,
	) { values: Array<Any?> ->
		val filters = values[0] as Set<ListFilterOption>
		val list = values[1] as List<ContentWithHistory>
		val grouped = values[2] as Boolean
		val mode = values[3] as ListMode
		val incognito = values[4] as Boolean
		val groupTab = values[5] as BrowseGroupTab
		val sourceTags = values[6] as Set<SourceTag>
		mapList(list, grouped, mode, filters, incognito, groupTab, sourceTags)
	}.distinctUntilChanged().onEach {
		isPaginationReady.set(true)
	}.catch { e ->
		emit(listOf(e.toErrorState(canRetry = false)))
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState))

	override fun onRefresh() = Unit

	override fun onRetry() = Unit

	fun clearHistory(minDate: Instant?) {
		launchJob(Dispatchers.Default) {
			val stringRes = if (minDate == null) {
				repository.clear()
				R.string.history_cleared
			} else {
				repository.deleteAfter(minDate.toEpochMilli())
				R.string.removed_from_history
			}
			onActionDone.call(ReversibleAction(stringRes, null))
		}
	}

	fun removeNotFavorite() {
		launchJob(Dispatchers.Default) {
			repository.deleteNotFavorite()
			onActionDone.call(ReversibleAction(R.string.removed_from_history, null))
		}
	}

	fun removeFromHistory(ids: Set<Long>) {
		if (ids.isEmpty()) {
			return
		}
		launchJob(Dispatchers.Default) {
			val handle = repository.delete(ids)
			onActionDone.call(ReversibleAction(R.string.removed_from_history, handle))
		}
	}

	fun markAsRead(items: Set<Content>) {
		launchLoadingJob(Dispatchers.Default) {
			markAsReadUseCase(items)
		}
	}

	fun requestMoreItems() {
		if (isPaginationReady.compareAndSet(true, false)) {
			limit.value += PAGE_SIZE
		}
	}

	private fun observeHistory() = combine(
		sortOrder,
		quickFilter.appliedOptions.combineWithSettings(),
		limit,
	) { order, filters, limit ->
		isPaginationReady.set(false)
		repository.observeAllWithHistory(order, filters, limit)
	}.flattenLatest()

	private suspend fun mapList(
		list: List<ContentWithHistory>,
		grouped: Boolean,
		mode: ListMode,
		filters: Set<ListFilterOption>,
		isIncognito: Boolean,
		groupTab: BrowseGroupTab,
		sourceTags: Set<SourceTag>,
	): List<ListModel> {
		val filteredList = list.filter { (manga, _) ->
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

		if (filteredList.isEmpty()) {
			return if (filters.isEmpty() && groupTab == BrowseGroupTab.All && sourceTags.isEmpty()) {
				listOf(getEmptyState(hasFilters = false))
			} else {
				listOfNotNull(quickFilter.filterItem(filters), getEmptyState(hasFilters = true))
			}
		}
		val result = ArrayList<ListModel>((if (grouped) (filteredList.size * 1.4).toInt() else filteredList.size) + 2)
		quickFilter.filterItem(filters)?.let(result::add)
		if (isIncognito) {
			result += InfoModel(
				key = AppSettings.KEY_INCOGNITO_MODE,
				title = R.string.incognito_mode,
				text = R.string.incognito_mode_hint,
				icon = R.drawable.ic_incognito,
			)
		}
		val order = sortOrder.value
		var prevHeader: ListHeader? = null
		var isEmpty = true
		for ((manga, history) in filteredList) {
			isEmpty = false
			if (grouped) {
				val header = history.header(order)
				if (header != prevHeader) {
					if (header != null) {
						result += header
					}
					prevHeader = header
				}
			}
			result += mangaListMapper.toListModel(manga, mode)
		}
		if ((filters.isNotEmpty() || groupTab != BrowseGroupTab.All || sourceTags.isNotEmpty()) && isEmpty) {
			result += getEmptyState(hasFilters = true)
		}
		return result
	}

	private fun ContentHistory.header(order: ListSortOrder): ListHeader? = when (order) {
		ListSortOrder.LAST_READ,
		ListSortOrder.LONG_AGO_READ -> calculateTimeAgo(updatedAt)?.let {
			ListHeader(it)
		} ?: ListHeader(R.string.unknown)

		ListSortOrder.OLDEST,
		ListSortOrder.NEWEST -> calculateTimeAgo(createdAt)?.let {
			ListHeader(it)
		} ?: ListHeader(R.string.unknown)

		ListSortOrder.UNREAD,
		ListSortOrder.PROGRESS -> ListHeader(
			when {
				ReadingProgress.isCompleted(percent) -> R.string.status_completed
				percent in 0f..0.01f -> R.string.status_planned
				percent in 0f..1f -> R.string.status_reading
				else -> R.string.unknown
			},
		)

		ListSortOrder.ALPHABETIC,
		ListSortOrder.ALPHABETIC_REVERSE,
		ListSortOrder.RELEVANCE,
		ListSortOrder.NEW_CHAPTERS,
		ListSortOrder.UPDATED,
		ListSortOrder.RATING -> null
	}

	private fun getEmptyState(hasFilters: Boolean) = if (hasFilters) {
		EmptyState(
			icon = R.drawable.ic_empty_history,
			textPrimary = R.string.nothing_found,
			textSecondary = R.string.text_empty_holder_secondary_filtered,
			actionStringRes = R.string.reset_filter,
		)
	} else {
		EmptyState(
			icon = R.drawable.ic_empty_history,
			textPrimary = R.string.text_history_holder_primary,
			textSecondary = R.string.text_history_holder_secondary,
			actionStringRes = 0,
		)
	}
}
