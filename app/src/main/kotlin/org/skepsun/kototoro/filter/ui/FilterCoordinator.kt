package org.skepsun.kototoro.filter.ui

import androidx.fragment.app.Fragment
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.ViewModelLifecycle
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.skepsun.kototoro.core.model.MangaSource
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.core.util.LocaleComparator
import org.skepsun.kototoro.core.util.ext.lifecycleScope
import org.skepsun.kototoro.core.util.ext.sortedByOrdinal
import org.skepsun.kototoro.core.util.ext.sortedWithSafe
import org.skepsun.kototoro.filter.data.PersistableFilter
import org.skepsun.kototoro.filter.data.SavedFiltersRepository
import org.skepsun.kototoro.filter.ui.model.FilterProperty
import org.skepsun.kototoro.filter.ui.tags.TagTitleComparator
import org.skepsun.kototoro.parsers.model.ContentRating
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.Demographic
import org.skepsun.kototoro.parsers.model.MangaListFilter
import org.skepsun.kototoro.parsers.model.MangaListFilterOptions
import org.skepsun.kototoro.parsers.model.MangaParserSource
import org.skepsun.kototoro.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.model.MangaState
import org.skepsun.kototoro.filter.ui.model.UiTagGroup
import org.skepsun.kototoro.parsers.model.MangaTag
import org.skepsun.kototoro.parsers.model.SortOrder
import org.skepsun.kototoro.parsers.model.YEAR_MIN
import org.skepsun.kototoro.parsers.util.ifZero
import org.skepsun.kototoro.parsers.util.nullIfEmpty
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import org.skepsun.kototoro.remotelist.ui.RemoteListFragment
import org.skepsun.kototoro.search.domain.MangaSearchRepository
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@ViewModelScoped
class FilterCoordinator @Inject constructor(
    savedStateHandle: SavedStateHandle,
    mangaRepositoryFactory: MangaRepository.Factory,
    private val searchRepository: MangaSearchRepository,
    private val savedFiltersRepository: SavedFiltersRepository,
    lifecycle: ViewModelLifecycle,
) {

    private val coroutineScope = lifecycle.lifecycleScope + Dispatchers.Default
    private val repository = mangaRepositoryFactory.create(MangaSource(savedStateHandle[RemoteListFragment.ARG_SOURCE]))
    private val sourceLocale = (repository.source as? MangaParserSource)?.locale

    private val currentListFilter = MutableStateFlow(MangaListFilter.EMPTY)
    private val currentSortOrder = MutableStateFlow(repository.defaultSortOrder)

    private val availableSortOrders = repository.sortOrders
    private val filterRefreshTrigger = MutableStateFlow(0)
    private val filterOptions: Flow<Result<MangaListFilterOptions>> = filterRefreshTrigger.flatMapLatest {
        flow {
            emit(runCatchingCancellable { repository.getFilterOptions() })
        }
    }.shareIn(coroutineScope, SharingStarted.Lazily, replay = 1)

    val capabilities = repository.filterCapabilities

    val mangaSource: MangaSource
        get() = repository.source

    val isFilterApplied: Boolean
        get() = currentListFilter.value.isNotEmpty()

    val query: StateFlow<String?> = currentListFilter.map { it.query }
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    val sortOrder: StateFlow<FilterProperty<SortOrder>> = currentSortOrder.map { selected ->
        FilterProperty(
            availableItems = availableSortOrders.sortedByOrdinal(),
            selectedItem = selected,
        )
    }.stateIn(coroutineScope, SharingStarted.Lazily, FilterProperty.LOADING)

    val tags: StateFlow<FilterProperty<UiTagGroup>> = combine(
        filterOptions,
        currentListFilter.distinctUntilChangedBy { it.tags },
    ) { optionsRes, selected ->
        optionsRes.fold(
            onSuccess = { opts ->
                val groups = opts.effectiveTagGroups.map { group ->
                    val selectedInGroup = group.tags.intersect(selected.tags)
                    UiTagGroup(group.title, group.tags, selectedInGroup)
                }
                FilterProperty(
                    availableItems = groups,
                    selectedItems = groups.filter { it.selected.isNotEmpty() }.toSet(),
                )
            },
            onFailure = { FilterProperty.error(it) },
        )
    }.stateIn(coroutineScope, SharingStarted.Lazily, FilterProperty.LOADING)

    val tagsExcluded: StateFlow<FilterProperty<UiTagGroup>> =
        if (capabilities.isTagsExclusionSupported) {
            combine(
                filterOptions,
                currentListFilter.distinctUntilChangedBy { it.tagsExclude },
            ) { optionsRes, selected ->
                optionsRes.fold(
                    onSuccess = { opts ->
                        val groups = opts.effectiveTagGroups.map { group ->
                            val selectedInGroup = group.tags.intersect(selected.tagsExclude)
                            UiTagGroup(group.title, group.tags, selectedInGroup)
                        }
                        FilterProperty(
                            availableItems = groups,
                            selectedItems = groups.filter { it.selected.isNotEmpty() }.toSet(),
                        )
                    },
                    onFailure = { FilterProperty.error(it) },
                )
            }.stateIn(coroutineScope, SharingStarted.Lazily, FilterProperty.LOADING)
        } else {
            MutableStateFlow(FilterProperty.EMPTY)
        }

    val authors: StateFlow<FilterProperty<String>> = if (capabilities.isAuthorSearchSupported) {
        combine(
            flow { emit(searchRepository.getAuthors(repository.source, TAGS_LIMIT)) },
            currentListFilter.distinctUntilChangedBy { it.author },
        ) { available, selected ->
            FilterProperty(
                availableItems = available,
                selectedItems = setOfNotNull(selected.author),
            )
        }.stateIn(coroutineScope, SharingStarted.Lazily, FilterProperty.LOADING)
    } else {
        MutableStateFlow(FilterProperty.EMPTY)
    }

    val states: StateFlow<FilterProperty<MangaState>> = combine(
        filterOptions,
        currentListFilter.distinctUntilChangedBy { it.states },
    ) { available, selected ->
        available.fold(
            onSuccess = {
                FilterProperty(
                    availableItems = it.availableStates.sortedByOrdinal(),
                    selectedItems = selected.states,
                )
            },
            onFailure = {
                FilterProperty.error(it)
            },
        )
    }.stateIn(coroutineScope, SharingStarted.Lazily, FilterProperty.LOADING)

    val contentRating: StateFlow<FilterProperty<ContentRating>> = combine(
        filterOptions,
        currentListFilter.distinctUntilChangedBy { it.contentRating },
    ) { available, selected ->
        available.fold(
            onSuccess = {
                FilterProperty(
                    availableItems = it.availableContentRating.sortedByOrdinal(),
                    selectedItems = selected.contentRating,
                )
            },
            onFailure = {
                FilterProperty.error(it)
            },
        )
    }.stateIn(coroutineScope, SharingStarted.Lazily, FilterProperty.LOADING)

    val contentTypes: StateFlow<FilterProperty<ContentType>> = combine(
        filterOptions,
        currentListFilter.distinctUntilChangedBy { it.types },
    ) { available, selected ->
        available.fold(
            onSuccess = {
                FilterProperty(
                    availableItems = it.availableContentTypes.sortedByOrdinal(),
                    selectedItems = selected.types,
                )
            },
            onFailure = {
                FilterProperty.error(it)
            },
        )
    }.stateIn(coroutineScope, SharingStarted.Lazily, FilterProperty.LOADING)

    val demographics: StateFlow<FilterProperty<Demographic>> = combine(
        filterOptions,
        currentListFilter.distinctUntilChangedBy { it.demographics },
    ) { available, selected ->
        available.fold(
            onSuccess = {
                FilterProperty(
                    availableItems = it.availableDemographics.sortedByOrdinal(),
                    selectedItems = selected.demographics,
                )
            },
            onFailure = {
                FilterProperty.error(it)
            },
        )
    }.stateIn(coroutineScope, SharingStarted.Lazily, FilterProperty.LOADING)

    val locale: StateFlow<FilterProperty<Locale?>> = combine(
        filterOptions,
        currentListFilter.distinctUntilChangedBy { it.locale },
    ) { available, selected ->
        available.fold(
            onSuccess = {
                FilterProperty(
                    availableItems = it.availableLocales.sortedWithSafe(LocaleComparator()).addFirstDistinct(null),
                    selectedItems = setOfNotNull(selected.locale),
                )
            },
            onFailure = {
                FilterProperty.error(it)
            },
        )
    }.stateIn(coroutineScope, SharingStarted.Lazily, FilterProperty.LOADING)

    val originalLocale: StateFlow<FilterProperty<Locale?>> = if (capabilities.isOriginalLocaleSupported) {
        combine(
            filterOptions,
            currentListFilter.distinctUntilChangedBy { it.originalLocale },
        ) { available, selected ->
            available.fold(
                onSuccess = {
                    FilterProperty(
                        availableItems = it.availableLocales.sortedWithSafe(LocaleComparator()).addFirstDistinct(null),
                        selectedItems = setOfNotNull(selected.originalLocale),
                    )
                },
                onFailure = {
                    FilterProperty.error(it)
                },
            )
        }.stateIn(coroutineScope, SharingStarted.Lazily, FilterProperty.LOADING)
    } else {
        MutableStateFlow(FilterProperty.EMPTY)
    }

    val year: StateFlow<FilterProperty<Int>> = if (capabilities.isYearSupported) {
        currentListFilter.distinctUntilChangedBy { it.year }.map { selected ->
            FilterProperty(
                availableItems = listOf(YEAR_MIN, MAX_YEAR),
                selectedItems = setOf(selected.year),
            )
        }.stateIn(coroutineScope, SharingStarted.Lazily, FilterProperty.LOADING)
    } else {
        MutableStateFlow(FilterProperty.EMPTY)
    }

    val yearRange: StateFlow<FilterProperty<Int>> = if (capabilities.isYearRangeSupported) {
        currentListFilter.distinctUntilChanged { old, new ->
            old.yearTo == new.yearTo && old.yearFrom == new.yearFrom
        }.map { selected ->
            FilterProperty(
                availableItems = listOf(YEAR_MIN, MAX_YEAR),
                selectedItems = setOf(selected.yearFrom.ifZero { YEAR_MIN }, selected.yearTo.ifZero { MAX_YEAR }),
            )
        }.stateIn(coroutineScope, SharingStarted.Lazily, FilterProperty.LOADING)
    } else {
        MutableStateFlow(FilterProperty.EMPTY)
    }

    val savedFilters: StateFlow<FilterProperty<PersistableFilter>> = combine(
        savedFiltersRepository.observeAll(repository.source),
        currentListFilter,
    ) { available, applied ->
        FilterProperty(
            availableItems = available,
            selectedItems = setOfNotNull(available.find { it.filter == applied }),
        )
    }.stateIn(coroutineScope, SharingStarted.Lazily, FilterProperty.EMPTY)

    fun reset() {
        currentListFilter.value = MangaListFilter.EMPTY
        refreshFilters()
    }

    fun refreshFilters() {
        filterRefreshTrigger.value++
    }

    fun snapshot() = Snapshot(
        sortOrder = currentSortOrder.value,
        listFilter = currentListFilter.value,
    )

    fun observe(): Flow<Snapshot> = combine(currentSortOrder, currentListFilter, ::Snapshot)

    fun setSortOrder(newSortOrder: SortOrder) {
        currentSortOrder.value = newSortOrder
        repository.defaultSortOrder = newSortOrder
    }

    fun set(value: MangaListFilter) {
        currentListFilter.value = value
    }

    fun setAdjusted(value: MangaListFilter) {
        var newFilter = value
        if (!newFilter.author.isNullOrEmpty() && !capabilities.isAuthorSearchSupported) {
            newFilter = newFilter.copy(
                query = newFilter.author,
                author = null,
            )
        }
        if (!newFilter.query.isNullOrEmpty() && !newFilter.hasNonSearchOptions() && !capabilities.isSearchWithFiltersSupported) {
            newFilter = MangaListFilter(query = newFilter.query)
        }
        set(newFilter)
    }

    fun saveCurrentFilter(name: String) = coroutineScope.launch {
        savedFiltersRepository.save(repository.source, name, currentListFilter.value)
    }

    fun renameSavedFilter(id: Int, newName: String) = coroutineScope.launch {
        savedFiltersRepository.rename(repository.source, id, newName)
    }

    fun deleteSavedFilter(id: Int) = coroutineScope.launch {
        savedFiltersRepository.delete(repository.source, id)
    }

    fun setQuery(value: String?) {
        val newQuery = value?.trim()?.nullIfEmpty()
        currentListFilter.update { oldValue ->
            if (capabilities.isSearchWithFiltersSupported || newQuery == null) {
                oldValue.copy(query = newQuery)
            } else {
                MangaListFilter(query = newQuery)
            }
        }
    }

    fun setLocale(value: Locale?) {
        currentListFilter.update { oldValue ->
            oldValue.copy(
                locale = value,
                query = oldValue.takeQueryIfSupported(),
            )
        }
    }

    fun setAuthor(value: String?) {
        currentListFilter.update { oldValue ->
            oldValue.copy(
                author = value,
                query = oldValue.takeQueryIfSupported(),
            )
        }
    }

    fun setOriginalLocale(value: Locale?) {
        currentListFilter.update { oldValue ->
            oldValue.copy(
                originalLocale = value,
                query = oldValue.takeQueryIfSupported(),
            )
        }
    }

    fun setYear(value: Int) {
        currentListFilter.update { oldValue ->
            oldValue.copy(
                year = value,
                query = oldValue.takeQueryIfSupported(),
            )
        }
    }

    fun setYearRange(valueFrom: Int, valueTo: Int) {
        currentListFilter.update { oldValue ->
            oldValue.copy(
                yearFrom = valueFrom,
                yearTo = valueTo,
                query = oldValue.takeQueryIfSupported(),
            )
        }
    }

    fun toggleState(value: MangaState, isSelected: Boolean) {
        currentListFilter.update { oldValue ->
            oldValue.copy(
                states = if (isSelected) oldValue.states + value else oldValue.states - value,
                query = oldValue.takeQueryIfSupported(),
            )
        }
    }

    fun toggleContentRating(value: ContentRating, isSelected: Boolean) {
        currentListFilter.update { oldValue ->
            oldValue.copy(
                contentRating = if (isSelected) oldValue.contentRating + value else oldValue.contentRating - value,
                query = oldValue.takeQueryIfSupported(),
            )
        }
    }

    fun toggleDemographic(value: Demographic, isSelected: Boolean) {
        currentListFilter.update { oldValue ->
            oldValue.copy(
                demographics = if (isSelected) oldValue.demographics + value else oldValue.demographics - value,
                query = oldValue.takeQueryIfSupported(),
            )
        }
    }

    fun toggleContentType(value: ContentType, isSelected: Boolean) {
        currentListFilter.update { oldValue ->
            oldValue.copy(
                types = if (isSelected) oldValue.types + value else oldValue.types - value,
                query = oldValue.takeQueryIfSupported(),
            )
        }
    }

    /**
     * Check if a tag represents a text input field (Mihon Filter.Text).
     * Text input tags have keys starting with "text:" prefix.
     */
    fun isTextInputTag(tag: MangaTag): Boolean {
        return tag.key.startsWith("text:")
    }
    
    /**
     * Get the display name for a text input tag (without the emoji prefix).
     */
    fun getTextInputLabel(tag: MangaTag): String {
        return tag.title.removePrefix("📝 ")
    }
    
    /**
     * Get the current value for a text input tag, if any.
     */
    fun getTextInputValue(tag: MangaTag): String? {
        val baseKey = tag.key
        return currentListFilter.value.tags
            .find { it.key.startsWith(baseKey) && it.key.contains("=") }
            ?.key?.substringAfter("=")
    }
    
    /**
     * Set the value for a text input filter.
     * Creates a new tag with the value appended to the key (format: key=value).
     */
    fun setTextInputValue(originalTag: MangaTag, value: String) {
        currentListFilter.update { oldValue ->
            // Remove any existing tag with the same base key
            val baseKey = originalTag.key
            val filteredTags = oldValue.tags.filter { !it.key.startsWith(baseKey) }.toSet()
            
            // Add new tag with value if not empty
            val newTags = if (value.isNotBlank()) {
                val tagWithValue = MangaTag(
                    title = "${originalTag.title.removePrefix("📝 ")}: $value",
                    key = "$baseKey=$value",
                    source = originalTag.source
                )
                filteredTags + tagWithValue
            } else {
                filteredTags
            }
            
            oldValue.copy(
                tags = newTags,
                query = oldValue.takeQueryIfSupported(),
            )
        }
    }

    fun toggleTag(value: MangaTag, isSelected: Boolean) {
        currentListFilter.update { oldValue ->
            val newTags = if (capabilities.isMultipleTagsSupported) {
                if (isSelected) oldValue.tags + value else oldValue.tags - value
            } else {
                if (isSelected) setOf(value) else emptySet()
            }
            oldValue.copy(
                tags = newTags,
                tagsExclude = oldValue.tagsExclude - newTags,
                query = oldValue.takeQueryIfSupported(),
            )
        }
    }

    fun toggleTagExclude(value: MangaTag, isSelected: Boolean) {
        currentListFilter.update { oldValue ->
            val newTagsExclude = if (capabilities.isMultipleTagsSupported) {
                if (isSelected) oldValue.tagsExclude + value else oldValue.tagsExclude - value
            } else {
                if (isSelected) setOf(value) else emptySet()
            }
            oldValue.copy(
                tags = oldValue.tags - newTagsExclude,
                tagsExclude = newTagsExclude,
                query = oldValue.takeQueryIfSupported(),
            )
        }
    }

    fun getAllTagGroups(): Flow<Result<List<UiTagGroup>>> = filterOptions.map { opts ->
        opts.map { x: MangaListFilterOptions ->
            x.effectiveTagGroups.map { group ->
                UiTagGroup(
                    title = group.title,
                    tags = group.tags.sortedWithSafe(TagTitleComparator(sourceLocale)).toSet(),
                )
            }
        }
    }

    private fun MangaListFilter.takeQueryIfSupported() = when {
        capabilities.isSearchWithFiltersSupported -> query
        query.isNullOrEmpty() -> query
        hasNonSearchOptions() -> null
        else -> query
    }

    private fun getTopTags(limit: Int): Flow<Result<List<MangaTag>>> = combine(
        flow { emit(searchRepository.getTopTags(repository.source, limit)) },
        filterOptions,
    ) { suggested: List<MangaTag>, options: Result<MangaListFilterOptions> ->
        val all = options.getOrNull()?.availableTags.orEmpty()
        val result = ArrayList<MangaTag>(limit)
        result.addAll(suggested.take(limit))
        if (result.size < limit) {
            result.addAll(all.shuffled().take(limit - result.size))
        }
        if (result.isNotEmpty()) {
            Result.success(result.toList())
        } else {
            options.map { emptyList<MangaTag>() }
        }
    }.catch {
        emit(Result.failure(it))
    }

    private fun getBottomTags(limit: Int): Flow<Result<List<MangaTag>>> = combine(
        flow { emit(searchRepository.getRareTags(repository.source, limit)) },
        filterOptions,
    ) { suggested: List<MangaTag>, options: Result<MangaListFilterOptions> ->
        val all = options.getOrNull()?.availableTags.orEmpty()
        val result = ArrayList<MangaTag>(limit)
        result.addAll(suggested.take(limit))
        if (result.size < limit) {
            result.addAll(all.shuffled().take(limit - result.size))
        }
        if (result.isNotEmpty()) {
            Result.success(result.toList())
        } else {
            options.map { emptyList<MangaTag>() }
        }
    }.catch {
        emit(Result.failure(it))
    }

    private fun <T> List<T>.addFirstDistinct(other: Collection<T>): List<T> {
        val result = ArrayDeque<T>(this.size + other.size)
        result.addAll(this)
        for (item in other) {
            if (item !in result) {
                result.addFirst(item)
            }
        }
        return result
    }

    private fun <T> List<T>.addFirstDistinct(item: T): List<T> {
        val result = ArrayDeque<T>(this.size + 1)
        result.addAll(this)
        if (item !in result) {
            result.addFirst(item)
        }
        return result
    }

    data class Snapshot(
        val sortOrder: SortOrder,
        val listFilter: MangaListFilter,
    )

    interface Owner {

        val filterCoordinator: FilterCoordinator
    }

    companion object {

        private const val TAGS_LIMIT = 12
        private val MAX_YEAR = Calendar.getInstance()[Calendar.YEAR] + 1

        fun find(fragment: Fragment): FilterCoordinator? {
            (fragment.activity as? Owner)?.let {
                return it.filterCoordinator
            }
            var f = fragment
            while (true) {
                (f as? Owner)?.let {
                    return it.filterCoordinator
                }
                f = f.parentFragment ?: break
            }
            return null
        }

        fun require(fragment: Fragment): FilterCoordinator {
            return find(fragment) ?: throw IllegalStateException("FilterCoordinator cannot be found")
        }
    }
}
