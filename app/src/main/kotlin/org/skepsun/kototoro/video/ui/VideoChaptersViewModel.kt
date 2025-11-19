package org.skepsun.kototoro.video.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.skepsun.kototoro.bookmarks.domain.BookmarksRepository
import org.skepsun.kototoro.core.model.getPreferredBranch
import org.skepsun.kototoro.core.nav.MangaIntent
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.details.data.MangaDetails
import org.skepsun.kototoro.details.domain.DetailsInteractor
import org.skepsun.kototoro.details.domain.DetailsLoadUseCase
import org.skepsun.kototoro.details.ui.pager.ChaptersPagesViewModel
import org.skepsun.kototoro.download.ui.worker.DownloadWorker
import org.skepsun.kototoro.history.data.HistoryRepository
import org.skepsun.kototoro.local.data.LocalStorageChanges
import org.skepsun.kototoro.local.domain.DeleteLocalMangaUseCase
import org.skepsun.kototoro.local.domain.model.LocalManga
import org.skepsun.kototoro.reader.ui.ReaderState

@HiltViewModel
class VideoChaptersViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    bookmarksRepository: BookmarksRepository,
    settings: AppSettings,
    @LocalStorageChanges
    localStorageChanges: SharedFlow<LocalManga?>,
    downloadScheduler: DownloadWorker.Scheduler,
    interactor: DetailsInteractor,
    savedStateHandle: SavedStateHandle,
    deleteLocalMangaUseCase: DeleteLocalMangaUseCase,
    private val detailsLoadUseCase: DetailsLoadUseCase,
) : ChaptersPagesViewModel(
    settings = settings,
    interactor = interactor,
    bookmarksRepository = bookmarksRepository,
    historyRepository = historyRepository,
    downloadScheduler = downloadScheduler,
    deleteLocalMangaUseCase = deleteLocalMangaUseCase,
    localStorageChanges = localStorageChanges,
) {

    private val intent = MangaIntent(savedStateHandle)
    private var loadingJob: Job? = null
    private val mangaId = intent.mangaId

    init {
        mangaDetails.value = intent.manga?.let { MangaDetails(it) }

        historyRepository.observeOne(mangaId)
            .onEach { h -> readingState.value = h?.let(::ReaderState) }
            .withErrorHandling()
            .stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

        loadingJob = doLoad(false)
    }

    fun reload(force: Boolean = true) {
        loadingJob = doLoad(force)
    }

    private fun doLoad(force: Boolean): Job = launchLoadingJob(Dispatchers.Default) {
        detailsLoadUseCase.invoke(intent, force).collect { details ->
            mangaDetails.value = details
            if (details.allChapters.isNotEmpty()) {
                val manga = details.toManga()
                val hist = historyRepository.getOne(manga)
                selectedBranch.value = manga.getPreferredBranch(hist)
            }
        }
    }
}