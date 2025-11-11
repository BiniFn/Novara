package org.skepsun.kototoro.suggestions.ui

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
import org.skepsun.kototoro.core.parser.MangaDataRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsFlow
import org.skepsun.kototoro.core.util.ext.onFirst
import org.skepsun.kototoro.list.domain.MangaListMapper
import org.skepsun.kototoro.list.domain.QuickFilterListener
import org.skepsun.kototoro.list.ui.MangaListViewModel
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.list.ui.model.toErrorState
import org.skepsun.kototoro.suggestions.domain.SuggestionRepository
import org.skepsun.kototoro.suggestions.domain.SuggestionsListQuickFilter
import javax.inject.Inject
import org.skepsun.kototoro.local.data.LocalStorageChanges
import org.skepsun.kototoro.local.domain.model.LocalManga
import kotlinx.coroutines.flow.SharedFlow

@HiltViewModel
class SuggestionsViewModel @Inject constructor(
	repository: SuggestionRepository,
	settings: AppSettings,
	private val mangaListMapper: MangaListMapper,
	private val quickFilter: SuggestionsListQuickFilter,
	private val suggestionsScheduler: SuggestionsWorker.Scheduler,
	mangaDataRepository: MangaDataRepository,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalManga?>,
) : MangaListViewModel(settings, mangaDataRepository, localStorageChanges), QuickFilterListener by quickFilter {

	override val listMode = settings.observeAsFlow(AppSettings.KEY_LIST_MODE_SUGGESTIONS) { suggestionsListMode }
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, settings.suggestionsListMode)

	override val content = combine(
		quickFilter.appliedOptions.combineWithSettings().flatMapLatest { repository.observeAll(0, it) },
		quickFilter.appliedOptions,
		observeListModeWithTriggers(),
	) { list, filters, mode ->
		when {
			list.isEmpty() -> if (filters.isEmpty()) {
				listOf(
					EmptyState(
						icon = R.drawable.ic_empty_common,
						textPrimary = R.string.nothing_found,
						textSecondary = R.string.text_suggestion_holder,
						actionStringRes = 0,
					),
				)
			} else {
				listOfNotNull(
					quickFilter.filterItem(filters),
					EmptyState(
						icon = R.drawable.ic_empty_common,
						textPrimary = R.string.nothing_found,
						textSecondary = R.string.text_empty_holder_secondary_filtered,
						actionStringRes = 0,
					),
				)
			}

			else -> buildList(list.size + 1) {
				quickFilter.filterItem(filters)?.let(::add)
				mangaListMapper.toListModelList(this, list, mode)
			}
		}
	}.onStart {
		loadingCounter.increment()
	}.onFirst {
		loadingCounter.decrement()
	}.catch {
		emit(listOf(it.toErrorState(canRetry = false)))
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState))

	override fun onRefresh() = Unit

	override fun onRetry() = Unit

	fun updateSuggestions() {
		launchJob(Dispatchers.Default) {
			suggestionsScheduler.startNow()
		}
	}
}
