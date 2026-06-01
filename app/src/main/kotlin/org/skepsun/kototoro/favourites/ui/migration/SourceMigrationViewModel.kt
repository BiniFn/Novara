package org.skepsun.kototoro.favourites.ui.migration

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.skepsun.kototoro.core.jsonsource.SourceGroupManager
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.explore.data.ContentSourcesRepository
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.favourites.data.FavouriteSourcesRepository
import org.skepsun.kototoro.favourites.domain.MigrationItem
import org.skepsun.kototoro.favourites.domain.MigrationProgress
import org.skepsun.kototoro.favourites.work.SourceMigrationWorker
import org.skepsun.kototoro.parsers.model.ContentSource
import javax.inject.Inject

private const val TAG = "SourceMigrationVM"

data class MigrationUiState(
    val favouriteSources: List<ContentSource> = emptyList(),
    val availableSources: List<ContentSource> = emptyList(),
    val selectedFromSource: ContentSource? = null,
    val selectedToSource: ContentSource? = null,
    val fromContentTypeFilter: Set<BrowseGroupTab> = emptySet(),
    val fromSourceTagFilter: Set<SourceTag> = emptySet(),
    val toContentTypeFilter: Set<BrowseGroupTab> = emptySet(),
    val toSourceTagFilter: Set<SourceTag> = emptySet(),
    val concurrency: Int = 3,
    val migrationProgress: MigrationProgress? = null,
    val isExecuting: Boolean = false,
    val workId: String? = null,
    val isFinished: Boolean = false,
    val fromFilteredSources: List<ContentSource> = emptyList(),
    val toFilteredSources: List<ContentSource> = emptyList(),
)

@HiltViewModel
class SourceMigrationViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val favouriteSourcesRepository: FavouriteSourcesRepository,
    private val sourcesRepository: ContentSourcesRepository,
    private val sourceGroupManager: SourceGroupManager,
) : AndroidViewModel(appContext as Application) {

    private val _uiState = MutableStateFlow(MigrationUiState())
    val uiState: StateFlow<MigrationUiState> = _uiState.asStateFlow()

    private var migrationObserver: Observer<WorkInfo?>? = null

    init {
        loadSources()
    }

    override fun onCleared() {
        super.onCleared()
        removeMigrationObserver()
    }

    fun loadSources() {
        viewModelScope.launch(Dispatchers.IO) {
            val favSources = favouriteSourcesRepository.getFavouriteSources()
            val sourceCounts = favSources.associateWith { source ->
                favouriteSourcesRepository.getFavouriteContentsBySource(source.name).size
            }
            val sortedFavSources = favSources.sortedByDescending { sourceCounts[it] ?: 0 }
            val allSources = sourcesRepository.getAllAvailableSourcesForListing()
            val state = _uiState.value
            _uiState.value = state.copy(
                favouriteSources = sortedFavSources,
                availableSources = allSources,
                fromFilteredSources = filterSources(sortedFavSources, state.fromContentTypeFilter, state.fromSourceTagFilter),
                toFilteredSources = filterSources(allSources, state.toContentTypeFilter, state.toSourceTagFilter),
            )
        }
    }

    fun selectFromSource(source: ContentSource?) {
        _uiState.value = _uiState.value.copy(selectedFromSource = source)
    }

    fun selectToSource(source: ContentSource?) {
        _uiState.value = _uiState.value.copy(selectedToSource = source)
    }

    fun setConcurrency(value: Int) {
        _uiState.value = _uiState.value.copy(concurrency = value.coerceIn(1, 10))
    }

    fun toggleFromContentType(tab: BrowseGroupTab) {
        val state = _uiState.value
        val tabs = if (tab in state.fromContentTypeFilter) {
            if (state.fromContentTypeFilter.size == 1) state.fromContentTypeFilter
            else state.fromContentTypeFilter - tab
        } else {
            state.fromContentTypeFilter + tab
        }
        _uiState.value = state.copy(
            fromContentTypeFilter = tabs,
            fromFilteredSources = filterSources(state.favouriteSources, tabs, state.fromSourceTagFilter),
        )
    }

    fun toggleFromSourceTag(tag: SourceTag) {
        val state = _uiState.value
        val tags = if (tag in state.fromSourceTagFilter) {
            state.fromSourceTagFilter - tag
        } else {
            state.fromSourceTagFilter + tag
        }
        _uiState.value = state.copy(
            fromSourceTagFilter = tags,
            fromFilteredSources = filterSources(state.favouriteSources, state.fromContentTypeFilter, tags),
        )
    }

    fun toggleToContentType(tab: BrowseGroupTab) {
        val state = _uiState.value
        val tabs = if (tab in state.toContentTypeFilter) {
            if (state.toContentTypeFilter.size == 1) state.toContentTypeFilter
            else state.toContentTypeFilter - tab
        } else {
            state.toContentTypeFilter + tab
        }
        _uiState.value = state.copy(
            toContentTypeFilter = tabs,
            toFilteredSources = filterSources(state.availableSources, tabs, state.toSourceTagFilter),
        )
    }

    fun toggleToSourceTag(tag: SourceTag) {
        val state = _uiState.value
        val tags = if (tag in state.toSourceTagFilter) {
            state.toSourceTagFilter - tag
        } else {
            state.toSourceTagFilter + tag
        }
        _uiState.value = state.copy(
            toSourceTagFilter = tags,
            toFilteredSources = filterSources(state.availableSources, state.toContentTypeFilter, tags),
        )
    }

    fun startMigration() {
        val state = _uiState.value
        val fromSource = state.selectedFromSource ?: return
        val toSource = state.selectedToSource ?: return
        if (fromSource.name == toSource.name) return

        Log.d(TAG, "startMigration: from=${fromSource.name}, to=${toSource.name}, concurrency=${state.concurrency}")

        val workManager = WorkManager.getInstance(appContext)
        val request = OneTimeWorkRequestBuilder<SourceMigrationWorker>()
            .setInputData(
                workDataOf(
                    Pair(SourceMigrationWorker.KEY_FROM_SOURCE, fromSource.name),
                    Pair(SourceMigrationWorker.KEY_TO_SOURCE, toSource.name),
                    Pair(SourceMigrationWorker.KEY_CONCURRENCY, state.concurrency),
                ),
            )
            .addTag(SourceMigrationWorker.WORK_TAG)
            .build()

        removeMigrationObserver()
        workManager.enqueue(request)
        val workId = request.id.toString()
        Log.d(TAG, "Work enqueued: id=$workId")

        _uiState.value = state.copy(
            isExecuting = true,
            workId = workId,
            isFinished = false,
            migrationProgress = MigrationProgress(
                total = 0,
                completed = 0,
                failed = 0,
                notFound = 0,
                currentItem = null,
                items = emptyList(),
            ),
        )

        migrationObserver = Observer { workInfo ->
            workInfo ?: return@Observer
            val currentState = _uiState.value
            val progressData = workInfo.progress
            val prevProgress = currentState.migrationProgress
            val newTotal = progressData.getInt(SourceMigrationWorker.KEY_TOTAL, 0)
            val newCompleted = progressData.getInt(SourceMigrationWorker.KEY_COMPLETED, 0)
            val newFailed = progressData.getInt(SourceMigrationWorker.KEY_FAILED, 0)
            val newNotFound = progressData.getInt(SourceMigrationWorker.KEY_NOT_FOUND, 0)
            val finished = progressData.getBoolean(SourceMigrationWorker.KEY_FINISHED, false)
            val workDone = finished || workInfo.state.isFinished

            Log.d(TAG, "Progress: total=$newTotal completed=$newCompleted failed=$newFailed notFound=$newNotFound finished=$finished state=${workInfo.state}")

            // Never regress: always keep the max of current and new values
            val total = maxOf(newTotal, prevProgress?.total ?: 0)
            val completed = maxOf(newCompleted, prevProgress?.completed ?: 0)
            val failed = maxOf(newFailed, prevProgress?.failed ?: 0)
            val notFound = maxOf(newNotFound, prevProgress?.notFound ?: 0)

            val progress = MigrationProgress(
                total = total,
                completed = completed,
                failed = failed,
                notFound = notFound,
                currentItem = prevProgress?.currentItem,
                items = prevProgress?.items ?: emptyList(),
                isFinished = workDone,
            )

            _uiState.value = currentState.copy(
                migrationProgress = progress,
                isExecuting = !workInfo.state.isFinished,
                isFinished = workInfo.state == WorkInfo.State.SUCCEEDED,
            )
        }

        workManager.getWorkInfoByIdLiveData(request.id).observeForever(migrationObserver!!)
    }

    fun cancelMigration() {
        val workId = _uiState.value.workId ?: return
        WorkManager.getInstance(appContext).cancelWorkById(java.util.UUID.fromString(workId))
        removeMigrationObserver()
        _uiState.value = _uiState.value.copy(isExecuting = false, isFinished = true)
    }

    fun checkExistingMigration() {
        val workManager = WorkManager.getInstance(appContext)
        viewModelScope.launch(Dispatchers.IO) {
            val workInfos = workManager.getWorkInfosByTag(SourceMigrationWorker.WORK_TAG).get()
            val activeWork = workInfos.firstOrNull { !it.state.isFinished }
            if (activeWork != null) {
                val state = _uiState.value
                _uiState.value = state.copy(isExecuting = true, workId = activeWork.id.toString())
            }
        }
    }

    private fun removeMigrationObserver() {
        val observer = migrationObserver ?: return
        val workId = _uiState.value.workId ?: return
        kotlin.runCatching {
            WorkManager.getInstance(appContext)
                .getWorkInfoByIdLiveData(java.util.UUID.fromString(workId))
                .removeObserver(observer)
        }
        migrationObserver = null
    }

    private fun filterSources(
        sources: List<ContentSource>,
        contentTypeFilter: Set<BrowseGroupTab>,
        sourceTagFilter: Set<SourceTag>,
    ): List<ContentSource> {
        if (contentTypeFilter.isEmpty() && sourceTagFilter.isEmpty()) return sources
        return sources.filter { source ->
            val sourceName = source.name
            val contentGroup = sourceGroupManager.getContentGroupByName(sourceName, source.isNsfw())
            val originGroup = sourceGroupManager.getOriginGroupByName(sourceName)

            val contentTypeMatch = contentTypeFilter.isEmpty() ||
                contentTypeFilter.any { it.matchesContentGroup(contentGroup) }

            val sourceTagMatch = sourceTagFilter.isEmpty() ||
                sourceTagFilter.any { it.matches(contentGroup, originGroup) }

            contentTypeMatch && sourceTagMatch
        }
    }
}
