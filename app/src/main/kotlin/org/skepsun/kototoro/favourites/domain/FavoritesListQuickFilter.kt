package org.skepsun.kototoro.favourites.domain

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import org.skepsun.kototoro.core.os.NetworkState
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.list.domain.MangaListQuickFilter
import org.skepsun.kototoro.core.model.isNsfw

class FavoritesListQuickFilter @AssistedInject constructor(
	@Assisted private val categoryId: Long,
	private val settings: AppSettings,
	private val repository: FavouritesRepository,
	networkState: NetworkState,
	private val globalFilterState: GlobalFavoritesState,
) : MangaListQuickFilter(settings) {

	init {
		// Sync initial state if needed, or rely on global state.
		// Note: MangaListQuickFilter sets 'Downloaded' based on network in init.
		// We might want to apply that to global state ONLY if it's the first init?
		// Or just let user control.
		// For now, let's keep the network logic but apply it to global state
		globalFilterState.setFilterOption(ListFilterOption.Downloaded, !networkState.value)
	}

	override val appliedOptions = globalFilterState.appliedFilter

	override fun setFilterOption(option: ListFilterOption, isApplied: Boolean) {
		globalFilterState.setFilterOption(option, isApplied)
	}

	override fun toggleFilterOption(option: ListFilterOption) {
		globalFilterState.toggleFilterOption(option)
	}

	override fun clearFilter() {
		globalFilterState.clearFilter()
	}

	override suspend fun getAvailableFilterOptions(): List<ListFilterOption> = buildList {
		add(ListFilterOption.Downloaded)
		if (!settings.isNsfwContentDisabled) {
			add(ListFilterOption.SFW)          // 全年龄
			add(ListFilterOption.Macro.NSFW)   // R18
		}
		if (settings.isTrackerEnabled) {
			add(ListFilterOption.Macro.NEW_CHAPTERS)
		}
		add(ListFilterOption.Macro.COMPLETED)
		val hideNsfw = settings.isNsfwContentDisabled
		repository.findPopularSources(categoryId, 3)
			.filterNot { hideNsfw && it.isNsfw() }
			.mapTo(this) { ListFilterOption.Source(it) }
	}

	@AssistedFactory
	interface Factory {

		fun create(categoryId: Long): FavoritesListQuickFilter
	}
}
