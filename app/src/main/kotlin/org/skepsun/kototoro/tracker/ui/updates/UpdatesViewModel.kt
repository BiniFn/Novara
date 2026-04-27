package org.skepsun.kototoro.tracker.ui.updates

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.parser.ContentDataRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.prefs.observeAsFlow
import org.skepsun.kototoro.core.ui.model.DateTimeAgo
import org.skepsun.kototoro.core.util.ext.calculateTimeAgo
import org.skepsun.kototoro.core.util.ext.onFirst
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.list.domain.ContentListMapper
import org.skepsun.kototoro.list.domain.QuickFilterListener
import org.skepsun.kototoro.list.ui.ContentListViewModel
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.list.ui.model.toErrorState
import org.skepsun.kototoro.tracker.domain.TrackingRepository
import org.skepsun.kototoro.tracker.domain.UpdatesListQuickFilter
import org.skepsun.kototoro.tracker.domain.model.ContentTracking
import javax.inject.Inject
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.entitygraph.data.EntityGraphRepository
import org.skepsun.kototoro.local.data.LocalStorageChanges
import org.skepsun.kototoro.local.domain.model.LocalContent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.skepsun.kototoro.core.jsonsource.SourceGroupManager
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.list.ui.model.ContentCompactListModel
import org.skepsun.kototoro.list.ui.model.ContentDetailedListModel
import org.skepsun.kototoro.list.ui.model.ContentGridModel
import java.time.Instant

@HiltViewModel
class UpdatesViewModel @Inject constructor(
	private val repository: TrackingRepository,
	settings: AppSettings,
	private val mangaListMapper: ContentListMapper,
	private val quickFilter: UpdatesListQuickFilter,
	private val sourceGroupManager: SourceGroupManager,
	private val entityGraphRepository: EntityGraphRepository,
	mangaDataRepository: ContentDataRepository,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalContent?>,
	private val globalFavoritesState: org.skepsun.kototoro.favourites.domain.GlobalFavoritesState,
) : ContentListViewModel(settings, mangaDataRepository, localStorageChanges), QuickFilterListener by quickFilter {

	@Volatile
	private var groupedRemovalIds: Map<Long, Set<Long>> = emptyMap()

	override val isFilterBarVisible = MutableStateFlow(true)

	override val currentSourceTags = globalFavoritesState.selectedSourceTags

	override fun setSelectedSourceTags(tags: Set<org.skepsun.kototoro.explore.ui.model.SourceTag>) {
		globalFavoritesState.setSelectedSourceTags(tags)
	}

	override val currentGroupTab = globalFavoritesState.selectedGroupTab

	override fun setSelectedGroupTab(tab: org.skepsun.kototoro.explore.ui.model.BrowseGroupTab) {
		globalFavoritesState.setSelectedGroupTab(tab)
	}

	override val content = combine(
		quickFilter.appliedOptions.flatMapLatest { filterOptions ->
			repository.observeUpdatedContent(
				limit = 0,
				filterOptions = filterOptions,
			)
		},
		quickFilter.appliedOptions,
		settings.observeAsFlow(AppSettings.KEY_UPDATED_GROUPING) { isUpdatedGroupingEnabled },
		observeListModeWithTriggers(),
		selectedGroupTab,
		selectedSourceTags,
		mangaListMapper.observeDisplayChanges().onStart { emit(Unit) },
	) { values: Array<Any?> ->
		val mangaList = values[0] as List<ContentTracking>
		val filters = values[1] as Set<ListFilterOption>
		val grouping = values[2] as Boolean
		val mode = values[3] as ListMode
		val groupTab = values[4] as BrowseGroupTab
		val sourceTags = values[5] as Set<SourceTag>
		when {
			mangaList.isEmpty() -> if (filters.isEmpty() && groupTab == BrowseGroupTab.All && sourceTags.isEmpty()) {
				listOfNotNull(
					quickFilter.filterItem(filters),
					EmptyState(
						icon = R.drawable.ic_empty_feed,
						textPrimary = R.string.text_empty_holder_primary,
						textSecondary = R.string.text_feed_holder,
						actionStringRes = 0,
					),
				)
			} else {
				listOfNotNull(
					quickFilter.filterItem(filters),
					EmptyState(
						icon = R.drawable.ic_empty_history,
						textPrimary = R.string.nothing_found,
						textSecondary = R.string.text_empty_holder_secondary_filtered,
						actionStringRes = 0,
					),
				)
			}

			else -> mangaList.toUi(mode, filters, grouping, groupTab, sourceTags)
		}
	}.onStart {
		loadingCounter.increment()
	}.onFirst {
		loadingCounter.decrement()
	}.catch {
		emit(listOf(it.toErrorState(canRetry = false)))
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState))

	init {
		launchJob(Dispatchers.Default) {
			repository.gc()
		}
	}

	override fun onRefresh() = Unit

	override fun onRetry() = Unit

	fun remove(ids: Set<Long>) {
		launchJob(Dispatchers.Default) {
			repository.clearUpdates(
				ids.flatMapTo(LinkedHashSet()) { groupId ->
					groupedRemovalIds[groupId].orEmpty().ifEmpty { setOf(groupId) }
				},
			)
		}
	}

	private suspend fun List<ContentTracking>.toUi(
		mode: ListMode,
		filters: Set<ListFilterOption>,
		grouped: Boolean,
		groupTab: BrowseGroupTab,
		sourceTags: Set<SourceTag>,
	): List<ListModel> {
		val filteredList = filter { item ->
			val source = item.manga.source
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

		val hideAdult = settings.isTrackerNsfwDisabled
		val visibleList = if (hideAdult) filteredList.filterNot { it.manga.isNsfw() } else filteredList

		if (visibleList.isEmpty()) {
			groupedRemovalIds = emptyMap()
			return listOfNotNull(
				quickFilter.filterItem(filters),
				EmptyState(
					icon = R.drawable.ic_empty_history,
					textPrimary = R.string.nothing_found,
					textSecondary = R.string.text_empty_holder_secondary_filtered,
					actionStringRes = 0,
				),
			)
		}

		val groupedList = visibleList.aggregateByEntity()
		groupedRemovalIds = groupedList.associate { it.uiId to it.mangaIds }

		val result = ArrayList<ListModel>(if (grouped) (groupedList.size * 1.4).toInt() else groupedList.size + 1)
		quickFilter.filterItem(filters)?.let(result::add)
		var prevHeader: DateTimeAgo? = null
		for (item in groupedList) {
			if (grouped) {
				val header = item.lastChapterDate?.let { calculateTimeAgo(it) }
				if (header != prevHeader) {
					if (header != null) {
						result += ListHeader(header)
					}
					prevHeader = header
				}
			}
			result += mangaListMapper.toListModel(item.representative.manga, mode).toGroupedListModel(item)
		}
		return result
	}

	private suspend fun List<ContentTracking>.aggregateByEntity(): List<UpdateGroup> {
		if (isEmpty()) {
			return emptyList()
		}
		val entityIdsByMangaId = entityGraphRepository.findEntityIdsByLocalMangaIds(map { it.manga.id })
		val grouped = LinkedHashMap<Long, MutableList<ContentTracking>>(size)
		for (item in this) {
			val key = entityIdsByMangaId[item.manga.id]?.toUiGroupId() ?: item.manga.id
			grouped.getOrPut(key) { ArrayList(1) }.add(item)
		}
		return grouped.map { (uiId, items) ->
			items.toUpdateGroup(uiId)
		}
	}

	private fun List<ContentTracking>.toUpdateGroup(uiId: Long): UpdateGroup {
		val representative = maxWithOrNull(
			compareBy<ContentTracking>(
				{ it.lastChapterDate ?: Instant.EPOCH },
				{ it.lastCheck ?: Instant.EPOCH },
				{ it.newChapters },
			),
		) ?: first()
		return UpdateGroup(
			uiId = uiId,
			representative = representative,
			mangaIds = mapTo(LinkedHashSet(size)) { it.manga.id },
			sourceCount = map { it.manga.source.name }.distinct().size,
			lastChapterDate = mapNotNull { it.lastChapterDate }.maxOrNull(),
			totalNewChapters = sumOf { it.newChapters },
		)
	}

	private fun org.skepsun.kototoro.list.ui.model.ContentListModel.toGroupedListModel(group: UpdateGroup): ListModel {
		val groupSuffix = group.groupSuffix()
		return when (this) {
			is ContentCompactListModel -> copy(
				counter = group.totalNewChapters,
				id = group.uiId,
				subtitle = listOfNotNull(subtitle?.takeIf { it.isNotBlank() }, groupSuffix).joinToString(" · "),
			)
			is ContentDetailedListModel -> copy(
				counter = group.totalNewChapters,
				id = group.uiId,
				subtitle = listOfNotNull(subtitle.takeIf { !it.isNullOrBlank() }, groupSuffix).joinToString(" · "),
			)
			is ContentGridModel -> copy(
				counter = group.totalNewChapters,
				id = group.uiId,
			)
		}
	}

	private fun UpdateGroup.groupSuffix(): String? {
		return sourceCount.takeIf { it > 1 }?.let { "$it 个来源" }
	}

	private fun Long.toUiGroupId(): Long = -this

	private data class UpdateGroup(
		val uiId: Long,
		val representative: ContentTracking,
		val mangaIds: Set<Long>,
		val sourceCount: Int,
		val lastChapterDate: Instant?,
		val totalNewChapters: Int,
	)
}
