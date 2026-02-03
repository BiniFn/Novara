package org.skepsun.kototoro.search.ui.multi

import androidx.collection.ArraySet
import androidx.collection.LongSet
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.LocalMangaSource
import org.skepsun.kototoro.core.model.UnknownMangaSource
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.util.ext.append
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.ext.toLocale
import org.skepsun.kototoro.core.jsonsource.SourceType
import org.skepsun.kototoro.core.jsonsource.SourceTypeIdentifier
import org.skepsun.kototoro.explore.data.MangaSourcesRepository
import org.skepsun.kototoro.favourites.domain.GlobalFavoritesState
import org.skepsun.kototoro.favourites.domain.FavouritesRepository
import org.skepsun.kototoro.history.data.HistoryRepository
import org.skepsun.kototoro.list.domain.MangaListMapper
import org.skepsun.kototoro.list.ui.model.ButtonFooter
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingFooter
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaParserSource
import org.skepsun.kototoro.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import org.skepsun.kototoro.search.domain.ALL_SOURCE_TYPES
import org.skepsun.kototoro.search.domain.SearchKind
import org.skepsun.kototoro.search.domain.SearchV2Helper
import org.skepsun.kototoro.search.domain.sourceTypesFromNames
import org.skepsun.kototoro.search.domain.sourceTypesFromTags
import java.util.Locale
import javax.inject.Inject

private const val MAX_PARALLELISM = 4

@HiltViewModel
class SearchViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val mangaListMapper: MangaListMapper,
	private val searchHelperFactory: SearchV2Helper.Factory,
	private val sourcesRepository: MangaSourcesRepository,
	private val sourceTypeIdentifier: SourceTypeIdentifier,
	private val globalFavoritesState: GlobalFavoritesState,
	private val historyRepository: HistoryRepository,
	private val favouritesRepository: FavouritesRepository,
) : BaseViewModel() {

	val query = savedStateHandle.get<String>(AppRouter.KEY_QUERY).orEmpty()
	val kind = savedStateHandle.get<SearchKind>(AppRouter.KEY_KIND) ?: SearchKind.SIMPLE

	private var includeDisabledSources = MutableStateFlow(false)
	private var pinnedOnly = MutableStateFlow(false)
	private var hideEmpty = MutableStateFlow(false)
	private var sourceTypes = MutableStateFlow(
		sourceTypesFromNames(savedStateHandle.get<ArrayList<String>>(AppRouter.KEY_SOURCE_TYPES))
			?: sourceTypesFromTags(globalFavoritesState.selectedSourceTags.value),
	)
	private val results = MutableStateFlow<List<SearchResultsListModel>>(emptyList())

	private var searchJob: Job? = null

	val list: StateFlow<List<ListModel>> = combine(
		results,
		isLoading.dropWhile { !it },
		includeDisabledSources,
		hideEmpty,
	) { list, loading, includeDisabled, hideEmptyVal ->
		val filteredList = if (hideEmptyVal) {
			list.filter { it.list.isNotEmpty() }
		} else {
			list
		}
		when {
			filteredList.isEmpty() -> listOf(
				when {
					loading -> LoadingState
					else -> EmptyState(
						icon = R.drawable.ic_empty_common,
						textPrimary = R.string.nothing_found,
						textSecondary = R.string.text_search_holder_secondary,
						actionStringRes = 0,
					)
				},
			)

			loading -> filteredList + LoadingFooter()
			includeDisabled -> filteredList
			else -> filteredList + ButtonFooter(R.string.search_disabled_sources)
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState))

	init {
		doSearch()
	}

	fun getItems(ids: LongSet): Set<Manga> {
		val snapshot = results.value
		val result = ArraySet<Manga>(ids.size)
		snapshot.forEach { x ->
			for (item in x.list) {
				if (item.id in ids) {
					result.add(item.manga)
				}
			}
		}
		return result
	}

	fun retry() {
		searchJob?.cancel()
		results.value = emptyList()
		includeDisabledSources.value = false
		doSearch()
	}

	fun setPinnedOnly(value: Boolean) {
		if (pinnedOnly.value != value) {
			pinnedOnly.value = value
			retry()
		}
	}

	fun setHideEmpty(value: Boolean) {
		hideEmpty.value = value
	}

	fun isSourceTypeEnabled(type: SourceType): Boolean {
		return type in sourceTypes.value
	}

	fun setSourceTypeEnabled(type: SourceType, enabled: Boolean) {
		val updated = sourceTypes.value.toMutableSet().apply {
			if (enabled) add(type) else remove(type)
		}
		setSourceTypes(updated)
	}

	fun setSourceTypes(types: Set<SourceType>) {
		val resolved = if (types.isEmpty()) ALL_SOURCE_TYPES else types
		if (resolved != sourceTypes.value) {
			sourceTypes.value = resolved
			retry()
		}
	}

	fun getSourceTypes(): Set<SourceType> {
		return sourceTypes.value
	}

	fun continueSearch() {
		if (includeDisabledSources.value) {
			return
		}
		val prevJob = searchJob
		searchJob = launchLoadingJob(Dispatchers.Default) {
			includeDisabledSources.value = true
			prevJob?.join()
			val sources = if (pinnedOnly.value) {
				emptyList()
			} else {
				sourcesRepository.getDisabledSources()
					.sortedByDescending { it.priority() }
			}
			val filteredSources = filterSourcesByType(sources)
			val semaphore = Semaphore(MAX_PARALLELISM)
			filteredSources.map { source ->
				launch {
					semaphore.withPermit {
						appendResult(searchSource(source))
					}
				}
			}.joinAll()
		}
	}

	private fun doSearch() {
		val prevJob = searchJob
		searchJob = launchLoadingJob(Dispatchers.Default) {
			prevJob?.cancelAndJoin()
			appendResult(searchHistory())
			appendResult(searchFavorites())
			appendResult(searchLocal())
			val sources = if (pinnedOnly.value) {
				sourcesRepository.getPinnedSources().toList()
			} else {
				sourcesRepository.getEnabledSources()
			}
			val filteredSources = filterSourcesByType(sources)
			val semaphore = Semaphore(MAX_PARALLELISM)
			filteredSources.map { source ->
				launch {
					semaphore.withPermit {
						appendResult(searchSource(source))
					}
				}
			}.joinAll()
		}
	}

	// impl

	private suspend fun searchSource(source: MangaSource): SearchResultsListModel? = runCatchingCancellable {
		val searchHelper = searchHelperFactory.create(source)
		searchHelper(query, kind)
	}.fold(
		onSuccess = { result ->
			if (result == null || result.manga.isEmpty()) {
				null
			} else {
				val list = mangaListMapper.toListModelList(
					manga = result.manga,
					mode = ListMode.GRID,
				)
				SearchResultsListModel(
					titleResId = 0,
					source = source,
					list = list,
					error = null,
					listFilter = result.listFilter,
					sortOrder = result.sortOrder,
				)
			}
		},
		onFailure = { error ->
			error.printStackTraceDebug()
			if (source is MangaParserSource && source.isBroken) {
				null
			} else {
				SearchResultsListModel(0, source, null, null, emptyList(), error)
			}
		},
	)

	private suspend fun searchHistory(): SearchResultsListModel? = runCatchingCancellable {
		historyRepository.search(query, kind, Int.MAX_VALUE)
	}.fold(
		onSuccess = { result ->
			val filtered = filterMangaBySourceType(result)
			if (filtered.isNotEmpty()) {
				SearchResultsListModel(
					titleResId = R.string.history,
					source = UnknownMangaSource,
					list = mangaListMapper.toListModelList(manga = filtered, mode = ListMode.GRID),
					error = null,
					listFilter = null,
					sortOrder = null,
				)
			} else {
				null
			}
		},
		onFailure = { error ->
			SearchResultsListModel(
				titleResId = R.string.history,
				source = UnknownMangaSource,
				list = emptyList(),
				error = error,
				listFilter = null,
				sortOrder = null,
			)
		},
	)

	private suspend fun searchFavorites(): SearchResultsListModel? = runCatchingCancellable {
		favouritesRepository.search(query, kind, Int.MAX_VALUE)
	}.fold(
		onSuccess = { result ->
			val filtered = filterMangaBySourceType(result)
			if (filtered.isNotEmpty()) {
				SearchResultsListModel(
					titleResId = R.string.favourites,
					source = UnknownMangaSource,
					list = mangaListMapper.toListModelList(
						manga = filtered,
						mode = ListMode.GRID,
						flags = MangaListMapper.NO_FAVORITE,
					),
					error = null,
					listFilter = null,
					sortOrder = null,
				)
			} else {
				null
			}
		},
		onFailure = { error ->
			SearchResultsListModel(
				titleResId = R.string.favourites,
				source = UnknownMangaSource,
				list = emptyList(),
				error = error,
				listFilter = null,
				sortOrder = null,
			)
		},
	)

	private suspend fun searchLocal(): SearchResultsListModel? = runCatchingCancellable {
		if (isSourceTypeAllowed(LocalMangaSource)) {
			searchHelperFactory.create(LocalMangaSource).invoke(query, kind)
		} else {
			null
		}
	}.fold(
		onSuccess = { result ->
			if (!result?.manga.isNullOrEmpty()) {
				SearchResultsListModel(
					titleResId = 0,
					source = LocalMangaSource,
					list = mangaListMapper.toListModelList(
						manga = result.manga,
						mode = ListMode.GRID,
						flags = MangaListMapper.NO_SAVED,
					),
					error = null,
					listFilter = result.listFilter,
					sortOrder = result.sortOrder,
				)
			} else {
				null
			}
		},
		onFailure = { error ->
			SearchResultsListModel(
				titleResId = 0,
				source = LocalMangaSource,
				list = emptyList(),
				error = error,
				listFilter = null,
				sortOrder = null,
			)
		},
	)

	private fun appendResult(item: SearchResultsListModel?) {
		if (item != null) {
			results.append(item)
		}
	}

	private fun filterSourcesByType(sources: Collection<MangaSource>): List<MangaSource> {
		val allowed = sourceTypes.value
		return sources.filter { source ->
			sourceTypeIdentifier.getSourceType(source.name) in allowed
		}
	}

	private fun filterMangaBySourceType(manga: List<Manga>): List<Manga> {
		val allowed = sourceTypes.value
		return manga.filter { item ->
			sourceTypeIdentifier.getSourceType(item.source.name) in allowed
		}
	}

	private fun isSourceTypeAllowed(source: MangaSource): Boolean {
		return sourceTypeIdentifier.getSourceType(source.name) in sourceTypes.value
	}

	private fun MangaSource.priority(): Int {
		var res = 0
		if (this is MangaParserSource) {
			if (locale.toLocale() == Locale.getDefault()) res += 2
		}
		return res
	}
}
