package org.skepsun.kototoro.list.ui.config

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.util.ext.sortedByOrdinal
import org.skepsun.kototoro.favourites.domain.FavouritesRepository
import org.skepsun.kototoro.core.model.FavouriteCategory.Companion.NO_ID
import org.skepsun.kototoro.list.domain.ListSortOrder
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import javax.inject.Inject

@HiltViewModel
class ListConfigViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val settings: AppSettings,
	private val favouritesRepository: FavouritesRepository,
) : BaseViewModel() {

	private val sectionState = MutableStateFlow<ListConfigSection?>(
		savedStateHandle[AppRouter.KEY_LIST_SECTION],
	)

	val section: ListConfigSection?
		get() = sectionState.value

	fun initialize(section: ListConfigSection) {
		if (sectionState.value == null) {
			sectionState.value = section
		}
	}

	var listMode: ListMode
		get() = settings.listMode
		set(value) {
			settings.listMode = value
		}

	var gridSize: Int
		get() = settings.gridSize
		set(value) {
			settings.gridSize = value
		}

	val isGroupingSupported: Boolean
		get() = section == ListConfigSection.History || section == ListConfigSection.Updated

	val isGroupingAvailable: Boolean
		get() = when (section) {
			ListConfigSection.History -> settings.historySortOrder.isGroupingSupported()
			ListConfigSection.Updated -> true
			else -> false
		}

	var isGroupingEnabled: Boolean
		get() = when (section) {
			ListConfigSection.History -> settings.isHistoryGroupingEnabled
			ListConfigSection.Updated -> settings.isUpdatedGroupingEnabled
			else -> false
		}
		set(value) = when (section) {
			ListConfigSection.History -> settings.isHistoryGroupingEnabled = value
			ListConfigSection.Updated -> settings.isUpdatedGroupingEnabled = value
			else -> Unit
		}

	fun getSortOrders(): List<ListSortOrder>? = when (sectionState.value) {
		is ListConfigSection.Favorites -> ListSortOrder.FAVORITES
		ListConfigSection.General -> null
		ListConfigSection.History -> ListSortOrder.HISTORY
		ListConfigSection.Suggestions -> ListSortOrder.SUGGESTIONS
		ListConfigSection.Updated -> null
		null -> null
	}?.sortedByOrdinal()

	fun getSelectedSortOrder(): ListSortOrder? = when (val currentSection = sectionState.value) {
		is ListConfigSection.Favorites -> getCategorySortOrder(currentSection.categoryId)
		ListConfigSection.General -> null
		ListConfigSection.Updated -> null
		ListConfigSection.History -> settings.historySortOrder
		ListConfigSection.Suggestions -> ListSortOrder.RELEVANCE
		null -> null
	}

	fun setSortOrder(position: Int) {
		val value = getSortOrders()?.getOrNull(position) ?: return
		setSortOrder(value)
	}

	fun setSortOrder(value: ListSortOrder) {
		when (val currentSection = sectionState.value) {
			is ListConfigSection.Favorites -> launchJob {
				if (currentSection.categoryId == NO_ID) {
					settings.allFavoritesSortOrder = value
				} else {
					favouritesRepository.setCategoryOrder(currentSection.categoryId, value)
				}
			}

			ListConfigSection.General -> Unit
			ListConfigSection.History -> settings.historySortOrder = value

			ListConfigSection.Suggestions -> Unit
			ListConfigSection.Updated -> Unit
			null -> Unit
		}
	}

	private fun getCategorySortOrder(id: Long): ListSortOrder = if (id == NO_ID) {
		settings.allFavoritesSortOrder
	} else runBlocking {
		runCatchingCancellable {
			favouritesRepository.getCategory(id).order
		}.getOrElse {
			settings.allFavoritesSortOrder
		}
	}
}
