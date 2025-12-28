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
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.parser.MangaDataRepository
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
import org.skepsun.kototoro.list.domain.MangaListMapper
import org.skepsun.kototoro.list.domain.QuickFilterListener
import org.skepsun.kototoro.list.ui.MangaListViewModel
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.list.ui.model.toErrorState
import org.skepsun.kototoro.parsers.model.Manga
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import org.skepsun.kototoro.local.data.LocalStorageChanges
import org.skepsun.kototoro.local.domain.model.LocalManga
import kotlinx.coroutines.flow.SharedFlow

private const val PAGE_SIZE = 16

@HiltViewModel
class FavouritesListViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val repository: FavouritesRepository,
	private val mangaListMapper: MangaListMapper,
	private val markAsReadUseCase: MarkAsReadUseCase,
	quickFilterFactory: FavoritesListQuickFilter.Factory,
	settings: AppSettings,
	mangaDataRepository: MangaDataRepository,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalManga?>,
) : MangaListViewModel(settings, mangaDataRepository, localStorageChanges), QuickFilterListener {

	val categoryId: Long = savedStateHandle[AppRouter.KEY_ID] ?: NO_ID
	private val quickFilter = quickFilterFactory.create(categoryId)
	private val refreshTrigger = MutableStateFlow(Any())
	private val limit = MutableStateFlow(if (categoryId == NO_ID) Int.MAX_VALUE else PAGE_SIZE)
	private val isPaginationReady = AtomicBoolean(false)

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
	) { list, filters, mode, _ ->
		list.mapList(mode, filters)
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

	fun markAsRead(items: Set<Manga>) {
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

	private suspend fun List<Manga>.mapList(mode: ListMode, filters: Set<ListFilterOption>): List<ListModel> {
		val hideAdult = settings.isNsfwContentDisabled
		val adultItems = filter { it.isNsfw }
		val visibleItems = if (hideAdult) filterNot { it.isNsfw } else this

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
				if (filters.isEmpty()) {
					getEmptyState(hasFilters = false)
				} else {
					getEmptyState(hasFilters = true)
				}
			)
			return models
		}

		val result = ArrayList<ListModel>(visibleItems.size + 1)
		quickFilter.filterItem(filters)?.let(result::add)
		mangaListMapper.toListModelList(result, visibleItems, mode, MangaListMapper.NO_FAVORITE)
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
