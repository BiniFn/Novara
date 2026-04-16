package org.skepsun.kototoro.tracker.ui.feed

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.jsonsource.SourceGroupManager
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.prefs.observeAsFlow
import org.skepsun.kototoro.core.prefs.observeAsStateFlow
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.ui.model.DateTimeAgo
import org.skepsun.kototoro.core.ui.util.ReversibleAction
import org.skepsun.kototoro.core.util.ext.MutableEventFlow
import org.skepsun.kototoro.core.util.ext.calculateTimeAgo
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.list.domain.ContentListMapper
import org.skepsun.kototoro.list.domain.QuickFilterListener
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.list.ui.model.toErrorState
import org.skepsun.kototoro.tracker.domain.TrackingRepository
import org.skepsun.kototoro.tracker.domain.UpdatesListQuickFilter
import org.skepsun.kototoro.tracker.domain.model.TrackingLogItem
import org.skepsun.kototoro.tracker.ui.feed.model.FeedItem
import org.skepsun.kototoro.tracker.ui.feed.model.UpdatedContentHeader
import org.skepsun.kototoro.tracker.work.TrackWorker
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

private const val PAGE_SIZE = 20

@HiltViewModel
class FeedViewModel @Inject constructor(
	private val settings: AppSettings,
	private val repository: TrackingRepository,
	private val scheduler: TrackWorker.Scheduler,
	private val mangaListMapper: ContentListMapper,
	private val quickFilter: UpdatesListQuickFilter,
	private val sourceGroupManager: SourceGroupManager,
	private val globalFavoritesState: org.skepsun.kototoro.favourites.domain.GlobalFavoritesState,
	private val sourcePresetsRepository: org.skepsun.kototoro.explore.data.SourcePresetsRepository,
) : BaseViewModel(), QuickFilterListener by quickFilter {

	private val limit = MutableStateFlow(PAGE_SIZE)
	private val isReady = AtomicBoolean(false)

	val currentGroupTab = globalFavoritesState.selectedGroupTab
	val currentSourceTags = globalFavoritesState.selectedSourceTags

	val isRunning = scheduler.observeIsRunning()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Lazily, false)

	val isHeaderEnabled = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.Default,
		key = AppSettings.KEY_FEED_HEADER,
		valueProducer = { isFeedHeaderVisible },
	)

	val onActionDone = MutableEventFlow<ReversibleAction>()

	@Suppress("USELESS_CAST")
	val content = combine(
		observeHeader(),
		quickFilter.appliedOptions,
		combine(limit, quickFilter.appliedOptions.combineWithSettings(), ::Pair)
			.flatMapLatest { repository.observeTrackingLog(it.first, it.second) },
		currentGroupTab,
		currentSourceTags,
		settings.observeAsFlow(AppSettings.KEY_ACTIVE_SOURCE_PRESET_ID) { activeSourcePresetId }
			.flatMapLatest { id ->
				if (id == -1L) flowOf(null)
				else sourcePresetsRepository.observe(id)
			}
	) { values: Array<Any?> ->
		val header = values[0] as UpdatedContentHeader?
		val filters = values[1] as Set<ListFilterOption>
		val list = values[2] as List<TrackingLogItem>
		val groupTab = values[3] as BrowseGroupTab
		val sourceTags = values[4] as Set<SourceTag>
		val preset = values[5] as? org.skepsun.kototoro.explore.data.SourcePreset

		val filteredList = list.filter { item ->
			val source = item.manga.source
			if (preset != null && source.name !in preset.sources) {
				return@filter false
			}

			val contentGroup = sourceGroupManager.getContentGroup(source)
			val originGroup = sourceGroupManager.getOriginGroup(item.manga.source)
			
			groupTab.matchesContentGroup(contentGroup) &&
				(sourceTags.isEmpty() || sourceTags.any { it.matches(contentGroup, originGroup) })
		}

		val result = ArrayList<ListModel>((filteredList.size * 1.4).toInt().coerceAtLeast(3))
		quickFilter.filterItem(filters)?.let(result::add)
		if (header != null) {
			result += header
		}
		isReady.set(true)
		if (filteredList.isEmpty()) {
			result += EmptyState(
				icon = R.drawable.ic_empty_feed,
				textPrimary = R.string.text_empty_holder_primary,
				textSecondary = R.string.text_feed_holder,
				actionStringRes = 0,
			)
		} else {
			filteredList.mapListTo(result)
		}
		result as List<ListModel>
	}.catch { e ->
		emit(listOf(e.toErrorState(canRetry = false)))
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState))

	init {
		launchJob(Dispatchers.Default) {
			repository.gc()
		}
	}

	fun clearFeed(clearCounters: Boolean) {
		launchLoadingJob(Dispatchers.Default) {
			repository.clearLogs()
			if (clearCounters) {
				repository.clearCounters()
			}
			onActionDone.call(ReversibleAction(R.string.updates_feed_cleared, null))
		}
	}

	fun requestMoreItems() {
		if (isReady.compareAndSet(true, false)) {
			limit.value += PAGE_SIZE
		}
	}

	fun update() {
		scheduler.startNow()
	}

	fun setHeaderEnabled(value: Boolean) {
		settings.isFeedHeaderVisible = value
	}

	fun onItemClick(item: FeedItem) {
		launchJob(Dispatchers.Default, CoroutineStart.ATOMIC) {
			repository.markAsRead(item.id)
		}
	}

	fun setSelectedGroupTab(tab: BrowseGroupTab) {
		globalFavoritesState.setSelectedGroupTab(tab)
	}

	fun toggleSourceTag(tag: SourceTag) {
		globalFavoritesState.toggleSourceTag(tag)
	}

	private suspend fun List<TrackingLogItem>.mapListTo(destination: MutableList<ListModel>) {
		var prevDate: DateTimeAgo? = null
		for (item in this) {
			val date = calculateTimeAgo(item.createdAt)
			if (prevDate != date) {
				destination += if (date != null) {
					ListHeader(date)
				} else {
					ListHeader(R.string.unknown)
				}
			}
			prevDate = date
			destination += mangaListMapper.toFeedItem(item)
		}
	}

	private fun observeHeader() = combine(
		isHeaderEnabled,
		currentGroupTab,
		currentSourceTags,
		settings.observeAsFlow(AppSettings.KEY_ACTIVE_SOURCE_PRESET_ID) { activeSourcePresetId }
			.flatMapLatest { id ->
				if (id == -1L) flowOf(null)
				else sourcePresetsRepository.observe(id)
			}
	) { hasHeader, groupTab, sourceTags, preset ->
		data class HeaderParams(val hasHeader: Boolean, val groupTab: BrowseGroupTab, val sourceTags: Set<SourceTag>, val preset: org.skepsun.kototoro.explore.data.SourcePreset?)
		HeaderParams(hasHeader, groupTab, sourceTags, preset)
	}.flatMapLatest { args ->
		if (args.hasHeader) {
			quickFilter.appliedOptions.combineWithSettings().flatMapLatest {
				repository.observeUpdatedContent(10, it)
			}.map { mangaList ->
				val filteredContentList = mangaList.filter { item ->
					val source = item.manga.source
					if (args.preset != null && source.name !in args.preset.sources) {
						return@filter false
					}

					val contentGroup = sourceGroupManager.getContentGroup(source)
					val originGroup = sourceGroupManager.getOriginGroup(source)
					
					args.groupTab.matchesContentGroup(contentGroup) &&
						(args.sourceTags.isEmpty() || args.sourceTags.any { it.matches(contentGroup, originGroup) })
				}
				if (filteredContentList.isEmpty()) {
					null
				} else {
					UpdatedContentHeader(
						filteredContentList.map { mangaListMapper.toListModel(it.manga, ListMode.GRID) },
					)
				}
			}
		} else {
			flowOf(null)
		}
	}

	private fun Flow<Set<ListFilterOption>>.combineWithSettings(): Flow<Set<ListFilterOption>> = combine(
		settings.observeAsFlow(AppSettings.KEY_DISABLE_NSFW) { isNsfwContentDisabled },
	) { filters, skipNsfw ->
		if (skipNsfw) {
			filters + ListFilterOption.SFW
		} else {
			filters
		}
	}
}
