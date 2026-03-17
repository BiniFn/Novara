package org.skepsun.kototoro.favourites.ui.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.FavouriteCategory
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.parser.ContentDataRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.prefs.observeAsFlow
import org.skepsun.kototoro.core.ui.util.ReversibleAction
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.core.util.ext.flattenLatest
import org.skepsun.kototoro.favourites.domain.FavoritesListQuickFilter
import org.skepsun.kototoro.favourites.domain.FavouritesRepository
import org.skepsun.kototoro.favourites.ui.list.FavouritesListFragment.Companion.NO_ID
import org.skepsun.kototoro.history.domain.MarkAsReadUseCase
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.list.domain.ListSortOrder
import org.skepsun.kototoro.list.domain.ContentListMapper
import org.skepsun.kototoro.list.domain.QuickFilterListener
import org.skepsun.kototoro.list.ui.ContentListViewModel
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.list.ui.model.toErrorState
import org.skepsun.kototoro.parsers.model.Content
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import org.skepsun.kototoro.local.data.LocalStorageChanges
import org.skepsun.kototoro.local.domain.model.LocalContent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf
import org.skepsun.kototoro.core.jsonsource.SourceGroupManager
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag

private const val PAGE_SIZE = 16

@HiltViewModel
class FavouritesListViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val repository: FavouritesRepository,
	private val mangaListMapper: ContentListMapper,
	private val markAsReadUseCase: MarkAsReadUseCase,
	quickFilterFactory: FavoritesListQuickFilter.Factory,
	private val sourceGroupManager: SourceGroupManager,
	settings: AppSettings,
	mangaDataRepository: ContentDataRepository,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalContent?>,
	private val globalFavoritesState: org.skepsun.kototoro.favourites.domain.GlobalFavoritesState,
) : ContentListViewModel(settings, mangaDataRepository, localStorageChanges), QuickFilterListener {

	val categoryId: Long = savedStateHandle[AppRouter.KEY_ID] ?: NO_ID
	private val quickFilter = quickFilterFactory.create(categoryId)
	private val refreshTrigger = MutableStateFlow(Any())
	private val limit = MutableStateFlow(if (categoryId == NO_ID) Int.MAX_VALUE else PAGE_SIZE)
	private val isPaginationReady = AtomicBoolean(false)

	override val isFilterBarVisible = MutableStateFlow(false)

	override val currentSourceTags = globalFavoritesState.selectedSourceTags

	override fun setSelectedSourceTags(tags: Set<org.skepsun.kototoro.explore.ui.model.SourceTag>) {
		globalFavoritesState.setSelectedSourceTags(tags)
	}

	override val currentGroupTab = globalFavoritesState.selectedGroupTab

	override fun setSelectedGroupTab(tab: org.skepsun.kototoro.explore.ui.model.BrowseGroupTab) {
		globalFavoritesState.setSelectedGroupTab(tab)
	}

	override val availableCategories = flowOf(emptyList<FavouriteCategory>())
		.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

	override val listMode = settings.observeAsFlow(AppSettings.KEY_LIST_MODE_FAVORITES) { favoritesListMode }
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, settings.favoritesListMode)

	val sortOrder: StateFlow<ListSortOrder?> = if (categoryId == NO_ID) {
		settings.observeAsFlow(AppSettings.KEY_FAVORITES_ORDER) {
			allFavoritesSortOrder
		}
	} else {
		repository.observeCategory(categoryId)
			.withErrorHandling()
			.map { it?.order }
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	override val content = combine(
		observeFavorites(),
		quickFilter.appliedOptions,
		observeListModeWithTriggers(),
		refreshTrigger,
		currentGroupTab,
		currentSourceTags,
		selectedCategoryIds,
	) { values: Array<Any?> ->
		val list = values[0] as List<org.skepsun.kototoro.parsers.model.Content>
		val filters = values[1] as Set<ListFilterOption>
		val mode = values[2] as ListMode
		// val refreshTrigger = values[3]
		val groupTab = values[4] as BrowseGroupTab
		val sourceTags = values[5] as Set<SourceTag>
		val categoryIds = values[6] as Set<Long>
		mapList(list, filters, mode, groupTab, sourceTags, categoryIds)
	}.distinctUntilChanged().onEach {
		isPaginationReady.set(true)
	}.catch {
		emit(listOf(it.toErrorState(canRetry = false)))
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState))

	override fun onRefresh() {
		refreshTrigger.value = Any()
	}

	override fun onRetry() = Unit

	override fun setFilterOption(option: ListFilterOption, isApplied: Boolean) =
		quickFilter.setFilterOption(option, isApplied)

	override fun toggleFilterOption(option: ListFilterOption) = quickFilter.toggleFilterOption(option)

	override fun clearFilter() = quickFilter.clearFilter()

	fun markAsRead(items: Set<Content>) {
		launchLoadingJob(Dispatchers.Default) {
			markAsReadUseCase(items)
			onRefresh()
		}
	}

	fun removeFromFavourites(ids: Set<Long>) {
		if (ids.isEmpty()) {
			return
		}
		launchJob(Dispatchers.Default) {
			val handle = if (categoryId == NO_ID) {
				repository.removeFromFavourites(ids)
			} else {
				repository.removeFromCategory(categoryId, ids)
			}
			onActionDone.call(ReversibleAction(R.string.removed_from_favourites, handle))
		}
	}

	fun setSortOrder(order: ListSortOrder) {
		if (categoryId == NO_ID) {
			return
		}
		launchJob {
			repository.setCategoryOrder(categoryId, order)
		}
	}

	fun requestMoreItems() {
		if (isPaginationReady.compareAndSet(true, false)) {
			limit.value += PAGE_SIZE
		}
	}

	private suspend fun mapList(
		list: List<Content>,
		filters: Set<ListFilterOption>,
		mode: ListMode,
		groupTab: BrowseGroupTab,
		sourceTags: Set<SourceTag>,
		categoryIds: Set<Long>,
	): List<ListModel> {
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
			
			val categoryMatches = if (categoryIds.isEmpty()) {
				true
			} else {
				val mangaCategories = repository.getCategoriesIds(manga.id).toSet()
				categoryIds.any { it in mangaCategories }
			}

			groupMatches && originMatches && categoryMatches
		}

		val hideAdult = settings.isNsfwContentDisabled
		val adultItems = filteredList.filter { it.isNsfw }
		val visibleItems = if (hideAdult) filteredList.filterNot { it.isNsfw } else filteredList

		if (visibleItems.isEmpty()) {
			val models = mutableListOf<ListModel>()
			quickFilter.filterItem(filters)?.let(models::add)
			if (hideAdult && adultItems.isNotEmpty()) {
				models.add(
					org.skepsun.kototoro.list.ui.model.InfoModel(
						key = "hidden_nsfw_favourites",
						title = R.string.favourites_hidden_adult_title,
						text = R.string.favourites_hidden_adult_subtitle,
						icon = org.skepsun.kototoro.R.drawable.ic_eye_off,
					)
				)
			}
			models.add(
				if (filters.isEmpty() && groupTab == BrowseGroupTab.All && sourceTags.isEmpty() && categoryIds.isEmpty()) {
					getEmptyState(hasFilters = false)
				} else {
					getEmptyState(hasFilters = true)
				}
			)
			return models
		}

		val result = ArrayList<ListModel>(visibleItems.size + 1)
		quickFilter.filterItem(filters)?.let(result::add)
		mangaListMapper.toListModelList(result, visibleItems, mode, ContentListMapper.NO_FAVORITE)
		return result
	}

	private fun observeFavorites() = if (categoryId == NO_ID) {
		combine(
			sortOrder.filterNotNull(),
			quickFilter.appliedOptions.combineWithSettings(),
			limit,
		) { order, filters, limit ->
			isPaginationReady.set(false)
			repository.observeAll(order, filters, limit)
		}.flattenLatest()
	} else {
		combine(quickFilter.appliedOptions.combineWithSettings(), limit) { filters, limit ->
			repository.observeAll(categoryId, filters, limit)
		}.flattenLatest()
	}

	private fun getEmptyState(hasFilters: Boolean) = if (hasFilters) {
		EmptyState(
			icon = R.drawable.ic_empty_favourites,
			textPrimary = R.string.nothing_found,
			textSecondary = R.string.text_empty_holder_secondary_filtered,
			actionStringRes = R.string.reset_filter,
		)
	} else {
		EmptyState(
			icon = R.drawable.ic_empty_favourites,
			textPrimary = R.string.text_empty_holder_primary,
			textSecondary = if (categoryId == NO_ID) {
				R.string.you_have_not_favourites_yet
			} else {
				R.string.favourites_category_empty
			},
			actionStringRes = 0,
		)
	}
}
