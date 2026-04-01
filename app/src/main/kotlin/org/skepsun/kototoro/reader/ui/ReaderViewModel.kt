package org.skepsun.kototoro.reader.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import org.skepsun.kototoro.R
import org.skepsun.kototoro.bookmarks.domain.Bookmark
import org.skepsun.kototoro.bookmarks.domain.BookmarksRepository
import org.skepsun.kototoro.core.exceptions.EmptyContentException
import org.skepsun.kototoro.core.model.getPreferredBranch
import org.skepsun.kototoro.core.model.getLocale
import org.skepsun.kototoro.core.nav.ContentIntent
import org.skepsun.kototoro.core.nav.ReaderIntent
import org.skepsun.kototoro.core.os.AppShortcutManager
import org.skepsun.kototoro.core.parser.ContentDataRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ReaderMode
import org.skepsun.kototoro.core.prefs.TriStateOption
import org.skepsun.kototoro.core.prefs.observeAsFlow
import org.skepsun.kototoro.core.prefs.observeAsStateFlow
import org.skepsun.kototoro.core.util.ext.MutableEventFlow
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.core.util.ext.firstNotNull
import org.skepsun.kototoro.core.util.ext.requireValue
import org.skepsun.kototoro.details.data.ContentDetails
import org.skepsun.kototoro.details.domain.DetailsInteractor
import org.skepsun.kototoro.details.domain.DetailsLoadUseCase
import org.skepsun.kototoro.details.ui.pager.ChaptersPagesViewModel
import org.skepsun.kototoro.details.ui.pager.EmptyContentReason
import org.skepsun.kototoro.download.ui.worker.DownloadWorker
import org.skepsun.kototoro.history.data.HistoryRepository
import org.skepsun.kototoro.history.domain.HistoryUpdateUseCase
import org.skepsun.kototoro.list.domain.ReadingProgress.Companion.PROGRESS_NONE
import org.skepsun.kototoro.local.data.LocalStorageChanges
import org.skepsun.kototoro.local.domain.DeleteLocalContentUseCase
import org.skepsun.kototoro.local.domain.model.LocalContent
import org.skepsun.kototoro.parsers.model.ContentRating
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.parsers.util.ifNullOrEmpty
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import org.skepsun.kototoro.parsers.util.sizeOrZero
import org.skepsun.kototoro.reader.domain.ChaptersLoader
import org.skepsun.kototoro.reader.domain.DetectReaderModeUseCase
import org.skepsun.kototoro.reader.domain.PageLoader
import org.skepsun.kototoro.reader.domain.ReaderPageEnhancementController
import org.skepsun.kototoro.reader.domain.TranslationLayerState
import org.skepsun.kototoro.reader.translate.domain.normalizeReaderTranslationLanguageTag
import org.skepsun.kototoro.reader.translate.domain.resolveReaderTranslationSourceLanguage
import org.skepsun.kototoro.reader.ui.config.ReaderSettings
import org.skepsun.kototoro.reader.ui.pager.ReaderUiState
import org.skepsun.kototoro.scrobbling.discord.ui.DiscordRpc
import org.skepsun.kototoro.stats.domain.StatsCollector
import java.util.concurrent.atomic.AtomicBoolean
import java.time.Instant
import javax.inject.Inject

private const val BOUNDS_PAGE_OFFSET = 2
private const val PREFETCH_LIMIT = 10
private const val LOG_TAG = "ReaderViewModel"

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val dataRepository: ContentDataRepository,
    private val historyRepository: HistoryRepository,
    private val bookmarksRepository: BookmarksRepository,
    settings: AppSettings,
    private val pageLoader: PageLoader,
    private val enhancementController: ReaderPageEnhancementController,
    private val chaptersLoader: ChaptersLoader,
    private val appShortcutManager: AppShortcutManager,
    private val detailsLoadUseCase: DetailsLoadUseCase,
    private val historyUpdateUseCase: HistoryUpdateUseCase,
    private val detectReaderModeUseCase: DetectReaderModeUseCase,
    private val statsCollector: StatsCollector,
    private val discordRpc: DiscordRpc,
    @LocalStorageChanges localStorageChanges: SharedFlow<LocalContent?>,
    interactor: DetailsInteractor,
    deleteLocalContentUseCase: DeleteLocalContentUseCase,
    downloadScheduler: DownloadWorker.Scheduler,
    mangaRepositoryFactory: org.skepsun.kototoro.core.parser.ContentRepository.Factory,
    readerSettingsProducerFactory: ReaderSettings.Producer.Factory,
) : ChaptersPagesViewModel(
    settings = settings,
    interactor = interactor,
    bookmarksRepository = bookmarksRepository,
    historyRepository = historyRepository,
    downloadScheduler = downloadScheduler,
    deleteLocalContentUseCase = deleteLocalContentUseCase,
    mangaRepositoryFactory = mangaRepositoryFactory,
    localStorageChanges = localStorageChanges,
) {
    data class TranslationPageTaskSnapshot(
        val pageId: Long,
        val pageIndex: Int,
        val state: TranslationLayerState,
        val updatedAtMs: Long?,
        val log: String,
        val failCode: String?,
    )

    private val intent = ContentIntent(savedStateHandle)

    private var loadingJob: Job? = null
    private var pageSaveJob: Job? = null
    private var bookmarkJob: Job? = null
    private var stateChangeJob: Job? = null

    init {
        mangaDetails.value = intent.manga?.let { ContentDetails(it) }
    }

    val readerMode = MutableStateFlow<ReaderMode?>(null)
    val onPageSaved = MutableEventFlow<Collection<Uri>>()
    val onLoadingError = MutableEventFlow<Throwable>()
    val onShowToast = MutableEventFlow<Int>()
    val onAskNsfwIncognito = MutableEventFlow<Unit>()
    val uiState = MutableStateFlow<ReaderUiState?>(null)
    val targetPagePosition = MutableStateFlow<Int?>(null)
    val translationLayerState = MutableStateFlow(TranslationLayerState.IDLE)
    val translationTaskPanelVersion = MutableStateFlow(0L)
    private val translationStateByPageId = linkedMapOf<Long, TranslationLayerState>()
    private val translationStateUpdatedAtByPageId = linkedMapOf<Long, Long>()

    val isIncognitoMode = MutableStateFlow(savedStateHandle.get<Boolean>(ReaderIntent.EXTRA_INCOGNITO))

    val isMenuVisible = MutableStateFlow(false)

    val content = MutableStateFlow(ReaderContent(emptyList(), null))

    // 避免切换章节/模式后首次 onCurrentPageChanged 触发边界加载，将其忽略一次
    private val skipBoundaryLoadOnce = AtomicBoolean(false)

    val pageAnimation = settings.observeAsStateFlow(
        scope = viewModelScope + Dispatchers.Default,
        key = AppSettings.KEY_READER_ANIMATION,
        valueProducer = { readerAnimation },
    )

    val isInfoBarEnabled = settings.observeAsStateFlow(
        scope = viewModelScope + Dispatchers.Default,
        key = AppSettings.KEY_READER_BAR,
        valueProducer = { isReaderBarEnabled },
    )

    val isInfoBarTransparent = settings.observeAsStateFlow(
        scope = viewModelScope + Dispatchers.Default,
        key = AppSettings.KEY_READER_BAR_TRANSPARENT,
        valueProducer = { isReaderBarTransparent },
    )

    val isKeepScreenOnEnabled = settings.observeAsStateFlow(
        scope = viewModelScope + Dispatchers.Default,
        key = AppSettings.KEY_READER_SCREEN_ON,
        valueProducer = { isReaderKeepScreenOn },
    )

    val isWebtoonZooEnabled = observeIsWebtoonZoomEnabled()
        .stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Lazily, false)

    val isWebtoonGapsEnabled = settings.observeAsStateFlow(
        scope = viewModelScope + Dispatchers.Default,
        key = AppSettings.KEY_WEBTOON_GAPS,
        valueProducer = { isWebtoonGapsEnabled },
    )

    val isWebtoonPullGestureEnabled = settings.observeAsStateFlow(
        scope = viewModelScope + Dispatchers.Default,
        key = AppSettings.KEY_WEBTOON_PULL_GESTURE,
        valueProducer = { isWebtoonPullGestureEnabled },
    )


    val defaultWebtoonZoomOut = observeIsWebtoonZoomEnabled().flatMapLatest {
        if (it) {
            observeWebtoonZoomOut()
        } else {
            flowOf(0f)
        }
    }.flowOn(Dispatchers.Default)

    val isZoomControlsEnabled = getObserveIsZoomControlEnabled().flatMapLatest { zoom ->
        if (zoom) {
            combine(readerMode, isWebtoonZooEnabled) { mode, ze -> ze || mode != ReaderMode.WEBTOON }
        } else {
            flowOf(false)
        }
    }.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Lazily, false)

    val readerSettingsProducer = readerSettingsProducerFactory.create(
        manga.mapNotNull { it?.id },
    )

    val isContentNsfw = manga.map { it?.contentRating == ContentRating.ADULT }

    val isBookmarkAdded = readingState.flatMapLatest { state ->
        val manga = mangaDetails.value?.toContent()
        if (state == null || manga == null) {
            flowOf(false)
        } else {
            bookmarksRepository.observeBookmark(manga, state.chapterId, state.page)
                .map {
                    it != null && it.chapterId == state.chapterId && it.page == state.page
                }
        }
    }.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, false)

    init {
        initIncognitoMode()
        observeTranslationLayerState()
        observeTranslationDebugLogs()
        listenToDoublePageEvents()
        loadImpl()
        launchJob(Dispatchers.Default) {
            val mangaId = manga.filterNotNull().first().id
            if (!isIncognitoMode.firstNotNull()) {
                appShortcutManager.notifyContentOpened(mangaId)
            }
        }
    }


    private val widePageIds = mutableSetOf<Long>()

    private fun listenToDoublePageEvents() {
        launchJob(Dispatchers.Default) {
            pageLoader.widePageDetectedEvent.collect { pageId ->
                if (!settings.isReaderSplitPagesEnabled) return@collect
                if (widePageIds.add(pageId)) {
                    rebuildPages()
                }
            }
        }
    }

    private fun rebuildPages() {
        val currentContent = content.value
        val newPages = getSplitPagesSnapshot()
        if (newPages != currentContent.pages) {
            content.value = currentContent.copy(pages = newPages)
        }
    }

    private fun getSplitPagesSnapshot(): List<org.skepsun.kototoro.reader.ui.pager.ReaderPage> {
        val originalPages = chaptersLoader.snapshot()
        if (!settings.isReaderSplitPagesEnabled) return originalPages
        val newPages = mutableListOf<org.skepsun.kototoro.reader.ui.pager.ReaderPage>()
        val mode = readerMode.value
        val isRtl = mode == org.skepsun.kototoro.core.prefs.ReaderMode.REVERSED

        for (page in originalPages) {
            if (widePageIds.contains(page.id)) {
                if (isRtl) {
                    newPages.add(page.copy(split = org.skepsun.kototoro.reader.ui.pager.ReaderPageSplit.RIGHT))
                    newPages.add(page.copy(split = org.skepsun.kototoro.reader.ui.pager.ReaderPageSplit.LEFT))
                } else {
                    newPages.add(page.copy(split = org.skepsun.kototoro.reader.ui.pager.ReaderPageSplit.LEFT))
                    newPages.add(page.copy(split = org.skepsun.kototoro.reader.ui.pager.ReaderPageSplit.RIGHT))
                }
            } else {
                newPages.add(page)
            }
        }
        return newPages
    }

    fun reload() {
        loadingJob?.cancel()
        loadImpl()
    }

    fun retranslateCurrent() {
        launchJob(Dispatchers.Default) {
            val page = getCurrentPage() ?: return@launchJob
            pageLoader.invalidateTask(page.id)
            enhancementController.invalidateTranslationTask(page.id)
            enhancementController.invalidateTranslationCacheForPage(page.id)
            reload()
            onShowToast.call(R.string.reader_translation_retranslate_started)
        }
    }

    fun retranslateFailedInCurrentChapter() {
        launchJob(Dispatchers.Default) {
            val pages = getCurrentChapterPages().orEmpty()
            if (pages.isEmpty()) return@launchJob
            var retries = 0
            pages.forEach { page ->
                if (translationStateByPageId[page.id] == TranslationLayerState.FAILED) {
                    pageLoader.invalidateTask(page.id)
                    enhancementController.invalidateTranslationTask(page.id)
                    enhancementController.invalidateTranslationCacheForPage(page.id)
                    retries++
                }
            }
            if (retries == 0) {
                onShowToast.call(R.string.reader_translation_retry_failed_none)
                return@launchJob
            }
            reload()
            onShowToast.call(R.string.reader_translation_retry_failed_started)
        }
    }

    fun retranslateCurrentChapter() {
        launchJob(Dispatchers.Default) {
            val chapterPages = getCurrentChapterPages().orEmpty()
            if (chapterPages.isEmpty()) return@launchJob
            chapterPages.forEach { page ->
                pageLoader.invalidateTask(page.id)
                enhancementController.invalidateTranslationTask(page.id)
                enhancementController.invalidateTranslationCacheForPage(page.id)
            }
            reload()
            onShowToast.call(R.string.reader_translation_retranslate_chapter_started)
        }
    }

    fun retryTranslationForPage(pageId: Long) {
        launchJob(Dispatchers.Default) {
            pageLoader.invalidateTask(pageId)
            enhancementController.invalidateTranslationTask(pageId)
            enhancementController.invalidateTranslationCacheForPage(pageId)
            val currentPageId = getCurrentPage()?.id
            if (currentPageId == pageId) {
                reload()
            }
        }
    }

    fun getCurrentChapterTranslationTaskSnapshots(): List<TranslationPageTaskSnapshot> {
        val pages = getCurrentChapterPages().orEmpty()
        return pages.mapIndexed { index, page ->
            val log = enhancementController.getTranslationDebugLog(page.id)
            TranslationPageTaskSnapshot(
                pageId = page.id,
                pageIndex = index,
                state = translationStateByPageId[page.id] ?: TranslationLayerState.IDLE,
                updatedAtMs = translationStateUpdatedAtByPageId[page.id],
                log = log,
                failCode = Regex("""fail_code=([A-Z_]+)""")
                    .findAll(log)
                    .lastOrNull()
                    ?.groupValues
                    ?.getOrNull(1),
            )
        }
    }

    fun isTranslationBypassedForCurrentContent(): Boolean {
        if (!settings.isReaderTranslationEnabled) return false
        val sourceLang = resolveCurrentTranslationSourceLanguage()
        if (sourceLang.isBlank()) return false
        val targetLang = settings.readerTranslationTargetLanguage
            .normalizeReaderTranslationLanguageTag()
            .orEmpty()
        return sourceLang == targetLang
    }

    fun getTranslationBypassHint(context: Context): String? {
        if (!isTranslationBypassedForCurrentContent()) return null
        val targetLang = settings.readerTranslationTargetLanguage
        return context.getString(R.string.reader_translation_bypass_hint, targetLang)
    }

    fun shouldShowTranslationToggle(): Boolean {
        return settings.isReaderTranslationEnabled && !isTranslationBypassedForCurrentContent()
    }

    private fun resolveCurrentTranslationSourceLanguage(): String {
        return resolveReaderTranslationSourceLanguage(
            preferredLanguage = settings.readerTranslationSourceLanguage,
            contentLanguage = getContentOrNull()?.source?.getLocale()?.language,
        )
    }

    fun onPause() {
        getContentOrNull()?.let {
            statsCollector.onPause(it.id)
        }
    }

    fun onStop() {
        discordRpc.clearRpc()
    }

    fun onIdle() {
        discordRpc.setIdle()
    }

    fun switchMode(newMode: ReaderMode) {
        launchJob {
            val manga = checkNotNull(getContentOrNull())
            dataRepository.saveReaderMode(
                manga = manga,
                mode = newMode,
            )
            readerMode.value = newMode
            content.update {
                it.copy(state = getCurrentState())
            }
        }
    }

    fun saveCurrentState(state: ReaderState? = null) {
        if (state != null) {
            readingState.value = state
            savedStateHandle[ReaderIntent.EXTRA_STATE] = state
        }
        if (isIncognitoMode.value != false) {
            return
        }
        val readerState = state ?: readingState.value ?: return
        historyUpdateUseCase.invokeAsync(
            manga = getContentOrNull() ?: return,
            readerState = readerState,
            percent = computePercent(readerState.chapterId, readerState.page),
        )
    }

    fun getCurrentState() = readingState.value

    fun getCurrentChapterPages(): List<ContentPage>? {
        val chapterId = readingState.value?.chapterId ?: return null
        return chaptersLoader.getPages(chapterId)
    }

    fun skipBoundaryLoadNext() {
        skipBoundaryLoadOnce.set(true)
    }

    fun saveCurrentPage(
        pageSaveHelper: PageSaveHelper
    ) {
        val prevJob = pageSaveJob
        pageSaveJob = launchLoadingJob(Dispatchers.Default) {
            prevJob?.cancelAndJoin()
            val state = checkNotNull(getCurrentState())
            val targetPage = targetPagePosition.value ?: state.page
            val currentContent = manga.requireValue()
            val pages = content.value.pages
            val page = pages.find { it.chapterId == state.chapterId && it.index == targetPage }
                ?: pages.find { it.chapterId == state.chapterId && it.index == state.page }
                ?: throw IllegalStateException("Cannot find current page")

            val task = PageSaveHelper.Task(
                manga = currentContent,
                chapterId = state.chapterId,
                pageNumber = targetPage + 1,
                page = page.toContentPage(),
            )
            val dest = pageSaveHelper.save(setOf(task))
            onPageSaved.call(dest)
        }
    }

    fun getCurrentPage(): ContentPage? {
        val state = readingState.value ?: return null
        return content.value.pages.find {
            it.chapterId == state.chapterId && it.index == state.page
        }?.toContentPage()
    }

    fun switchChapter(id: Long, page: Int) {
        val prevJob = loadingJob
        loadingJob = launchLoadingJob(Dispatchers.Default) {
            prevJob?.cancelAndJoin()
            content.value = ReaderContent(emptyList(), null)
            chaptersLoader.loadSingleChapter(id)
            val newState = ReaderState(id, page, 0)
            content.value = ReaderContent(getSplitPagesSnapshot(), newState)
            saveCurrentState(newState)
        }
    }

    fun switchChapterBy(delta: Int) {
        val prevJob = loadingJob
        loadingJob = launchLoadingJob(Dispatchers.Default) {
            prevJob?.cancelAndJoin()
            val prevState = readingState.requireValue()
            val newChapterId = if (delta != 0) {
                val allChapters = mangaDetails.requireValue().allChapters
                var index = allChapters.indexOfFirst { x -> x.id == prevState.chapterId }
                if (index < 0) {
                    return@launchLoadingJob
                }
                index += delta
                (allChapters.getOrNull(index) ?: return@launchLoadingJob).id
            } else {
                prevState.chapterId
            }
            content.value = ReaderContent(emptyList(), null)
            chaptersLoader.loadSingleChapter(newChapterId)
            val newState = ReaderState(
                chapterId = newChapterId,
                page = if (delta == 0) prevState.page else 0,
                scroll = if (delta == 0) prevState.scroll else 0,
            )
            skipBoundaryLoadOnce.set(true)
            content.value = ReaderContent(getSplitPagesSnapshot(), newState)
            saveCurrentState(newState)
        }
    }

    @MainThread
    fun onCurrentPageChanged(lowerPos: Int, upperPos: Int) {
        val prevJob = stateChangeJob
        val pages = content.value.pages // capture immediately
        targetPagePosition.value = null
        stateChangeJob = launchJob(Dispatchers.Default) {
            prevJob?.cancelAndJoin()
            val centerPos = (lowerPos + upperPos) / 2
            val selectedPos = if (lowerPos >= 0 && upperPos >= 0 && pages.isNotEmpty()) {
                val lastIndex = pages.lastIndex
                val safeLower = lowerPos.coerceIn(0, lastIndex)
                val safeUpper = upperPos.coerceIn(0, lastIndex)
                when {
                    safeUpper >= lastIndex - BOUNDS_PAGE_OFFSET -> safeUpper
                    safeLower <= BOUNDS_PAGE_OFFSET -> safeLower
                    else -> (safeLower + safeUpper) / 2
                }
            } else {
                centerPos
            }
            Log.d(
                LOG_TAG,
                "onCurrentPageChanged: lower=$lowerPos, upper=$upperPos, selected=$selectedPos, " +
                    "pages=${pages.size}, skipBoundary=${skipBoundaryLoadOnce.get()}",
            )
            pages.getOrNull(selectedPos)?.let { page ->
                readingState.update { cs ->
                    cs?.copy(chapterId = page.chapterId, page = page.index)
                }
                updateTranslationStateForCurrentPage(page.id)
            }
            notifyStateChanged()
            val currentLoadingJob = loadingJob
            if (currentLoadingJob?.isActive == true) {
                Log.d(LOG_TAG, "onCurrentPageChanged: loading active, skip boundary check")
                return@launchJob
            }
            currentLoadingJob?.join()
            if (pages.size != content.value.pages.size) {
                return@launchJob // TODO
            }
            if (pages.isEmpty() || loadingJob?.isActive == true) {
                return@launchJob
            }
            if (skipBoundaryLoadOnce.getAndSet(false)) {
                return@launchJob
            }
            ensureActive()
            val autoLoadAllowed = readerMode.value != ReaderMode.WEBTOON || !isWebtoonPullGestureEnabled.value
            if (autoLoadAllowed) {
                if (upperPos >= pages.lastIndex - BOUNDS_PAGE_OFFSET) {
                    Log.d(
                        LOG_TAG,
                        "preload: trigger next, chapterId=${pages.last().chapterId}, " +
                            "upper=$upperPos, lastIndex=${pages.lastIndex}",
                    )
                    loadPrevNextChapter(pages.last().chapterId, isNext = true)
                }
                if (lowerPos <= BOUNDS_PAGE_OFFSET) {
                    Log.d(
                        LOG_TAG,
                        "preload: trigger prev, chapterId=${pages.first().chapterId}, " +
                            "lower=$lowerPos",
                    )
                    loadPrevNextChapter(pages.first().chapterId, isNext = false)
                }
            }
            if (pageLoader.isPrefetchApplicable()) {
                pageLoader.prefetch(pages.trySublist(upperPos + 1, upperPos + PREFETCH_LIMIT))
            }
        }
    }

    fun toggleBookmark() {
        if (bookmarkJob?.isActive == true) {
            return
        }
        bookmarkJob = launchJob(Dispatchers.Default) {
            loadingJob?.join()
            val state = checkNotNull(getCurrentState())
            if (isBookmarkAdded.value) {
                val manga = requireContent()
                bookmarksRepository.removeBookmark(manga.id, state.chapterId, state.page)
                onShowToast.call(R.string.bookmark_removed)
            } else {
                val page = checkNotNull(getCurrentPage()) { "Page not found" }
                val bookmark = Bookmark(
                    manga = requireContent(),
                    pageId = page.id,
                    chapterId = state.chapterId,
                    page = state.page,
                    scroll = state.scroll,
                    imageUrl = page.preview.ifNullOrEmpty { page.url },
                    createdAt = Instant.now(),
                    percent = computePercent(state.chapterId, state.page),
                )
                bookmarksRepository.addBookmark(bookmark)
                onShowToast.call(R.string.bookmark_added)
            }
        }
    }

    fun setTargetPageBySide(rawX: Float, width: Int, isDoublePage: Boolean) {
        val mode = readerMode.value ?: return
        if (isDoublePage && width > 0) {
            val state = readingState.value ?: return
            val isRtl = mode == ReaderMode.REVERSED
            val isRightSide = rawX > width / 2f

            // In LTR: left is page, right is page + 1
            // In RTL: right is page, left is page + 1
            val isSecondPage = if (isRtl) !isRightSide else isRightSide
            targetPagePosition.value = if (isSecondPage) state.page + 1 else state.page
        } else {
            targetPagePosition.value = null
        }
    }

    fun setIncognitoMode(value: Boolean, dontAskAgain: Boolean) {
        isIncognitoMode.value = value
        if (dontAskAgain) {
            settings.incognitoModeForNsfw = if (value) TriStateOption.ENABLED else TriStateOption.DISABLED
        }
    }

    private fun loadImpl() {
        loadingJob = launchLoadingJob(Dispatchers.Default + EventExceptionHandler(onLoadingError)) {
            var exception: Exception? = null
            var loadedDetails: ContentDetails? = null
            try {
                detailsLoadUseCase(intent, force = false)
                    .collect { details ->
                        loadedDetails = details
                        if (mangaDetails.value == null) {
                            mangaDetails.value = details
                        }
                        chaptersLoader.init(details)
                        val manga = details.toContent()
                        // obtain state
                        if (readingState.value == null) {
                            val newState = getStateFromIntent(manga)
                            if (newState == null) {
                                return@collect // manga not loaded yet if cannot get state
                            }
                            readingState.value = newState
                            val mode = runCatchingCancellable {
                                detectReaderModeUseCase(manga, newState)
                            }.getOrDefault(settings.defaultReaderMode)
                            val branch = chaptersLoader.peekChapter(newState.chapterId)?.branch
                            selectedBranch.value = branch
                            readerMode.value = mode
                            try {
                                chaptersLoader.loadSingleChapter(newState.chapterId)
                            } catch (e: Exception) {
                                readingState.value = null // try next time
                                exception = e.mergeWith(exception)
                                return@collect
                            }
                        }
                        mangaDetails.value = details.filterChapters(selectedBranch.value)

                        // save state
                        if (!isIncognitoMode.firstNotNull()) {
                            readingState.value?.let {
                                val percent = computePercent(it.chapterId, it.page)
                                historyUpdateUseCase(manga, it, percent)
                            }
                        }
                        notifyStateChanged()
                        skipBoundaryLoadOnce.set(true)
                        content.value = ReaderContent(getSplitPagesSnapshot(), readingState.value)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                exception = e.mergeWith(exception)
            }
            if (readingState.value == null) {
                val loadedContent = loadedDetails // for smart cast
                if (loadedContent != null) {
                    mangaDetails.value = loadedContent.filterChapters(selectedBranch.value)
                }
                val loadingError = when {
                    exception != null -> exception
                    loadedContent == null || !loadedContent.isLoaded -> null
                    loadedContent.isRestricted -> EmptyContentException(
                        EmptyContentReason.RESTRICTED,
                        loadedContent.toContent(),
                        null,
                    )

                    loadedContent.allChapters.isEmpty() -> EmptyContentException(
                        EmptyContentReason.NO_CHAPTERS,
                        loadedContent.toContent(),
                        null,
                    )

                    else -> null
                } ?: IllegalStateException("Unable to load manga. This should never happen. Please report")
                onLoadingError.call(loadingError)
            } else exception?.let { e ->
                // manga has been loaded but error occurred
                errorEvent.call(e)
            }
        }
    }

    @AnyThread
    private fun loadPrevNextChapter(currentId: Long, isNext: Boolean) {
        val prevJob = loadingJob
        loadingJob = launchLoadingJob(Dispatchers.Default) {
            prevJob?.join()
            Log.d(LOG_TAG, "loadPrevNextChapter: currentId=$currentId, isNext=$isNext")
            chaptersLoader.loadPrevNextChapter(mangaDetails.requireValue(), currentId, isNext)
            content.value = ReaderContent(getSplitPagesSnapshot(), null)
        }
    }

    private fun <T> List<T>.trySublist(fromIndex: Int, toIndex: Int): List<T> {
        val fromIndexBounded = fromIndex.coerceAtMost(lastIndex)
        val toIndexBounded = toIndex.coerceIn(fromIndexBounded, lastIndex)
        return if (fromIndexBounded == toIndexBounded) {
            emptyList()
        } else {
            subList(fromIndexBounded, toIndexBounded)
        }
    }

    private fun observeTranslationLayerState() {
        launchJob(Dispatchers.Default) {
            enhancementController.observeTranslationStatusUpdates().collect { event ->
                translationStateByPageId[event.pageId] = event.state
                translationStateUpdatedAtByPageId[event.pageId] = System.currentTimeMillis()
                translationTaskPanelVersion.update { it + 1 }
                val currentPageId = getCurrentPage()?.id
                if (currentPageId == event.pageId) {
                    translationLayerState.value = event.state
                }
            }
        }
    }

    private fun observeTranslationDebugLogs() {
        launchJob(Dispatchers.Default) {
            enhancementController.observeTranslationDebugLogUpdates().collect {
                translationTaskPanelVersion.update { it + 1 }
            }
        }
    }

    private fun updateTranslationStateForCurrentPage(pageId: Long) {
        translationLayerState.value = translationStateByPageId[pageId] ?: TranslationLayerState.IDLE
    }

    @WorkerThread
    private fun notifyStateChanged() {
        val state = getCurrentState() ?: return
        val chapter = chaptersLoader.peekChapter(state.chapterId) ?: return
        val m = mangaDetails.value ?: return
        val chapterIndex = m.chapters[chapter.branch]?.indexOfFirst { it.id == chapter.id } ?: -1
        val newState = ReaderUiState(
            mangaName = m.toContent().title,
            chapter = chapter,
            chapterIndex = chapterIndex,
            chaptersTotal = m.chapters[chapter.branch].sizeOrZero(),
            totalPages = chaptersLoader.getPagesCount(chapter.id),
            currentPage = state.page,
            percent = computePercent(state.chapterId, state.page),
            incognito = isIncognitoMode.value == true,
        )
        uiState.value = newState
        if (isIncognitoMode.value == false) {
            statsCollector.onStateChanged(m.id, state)
            discordRpc.updateRpc(m.toContent(), newState)
        }
    }

    private fun computePercent(chapterId: Long, pageIndex: Int): Float {
        val branch = chaptersLoader.peekChapter(chapterId)?.branch
        val chapters = mangaDetails.value?.chapters?.get(branch) ?: return PROGRESS_NONE
        val chaptersCount = chapters.size
        val chapterIndex = chapters.indexOfFirst { x -> x.id == chapterId }
        val pagesCount = chaptersLoader.getPagesCount(chapterId)
        if (chaptersCount == 0 || pagesCount == 0) {
            return PROGRESS_NONE
        }
        val pagePercent = (pageIndex + 1) / pagesCount.toFloat()
        val ppc = 1f / chaptersCount
        return ppc * chapterIndex + ppc * pagePercent
    }

    private fun observeIsWebtoonZoomEnabled() = settings.observeAsFlow(
        key = AppSettings.KEY_WEBTOON_ZOOM,
        valueProducer = { isWebtoonZoomEnabled },
    )

    private fun observeWebtoonZoomOut() = settings.observeAsFlow(
        key = AppSettings.KEY_WEBTOON_ZOOM_OUT,
        valueProducer = { defaultWebtoonZoomOut },
    )

    private fun getObserveIsZoomControlEnabled() = settings.observeAsFlow(
        key = AppSettings.KEY_READER_ZOOM_BUTTONS,
        valueProducer = { isReaderZoomButtonsEnabled },
    )

    private fun initIncognitoMode() {
        if (isIncognitoMode.value != null) {
            return
        }
        launchJob(Dispatchers.Default) {
            interactor.observeIncognitoMode(manga)
                .collect {
                    when (it) {
                        TriStateOption.ENABLED -> isIncognitoMode.value = true
                        TriStateOption.ASK -> {
                            onAskNsfwIncognito.call(Unit)
                            return@collect
                        }

                        TriStateOption.DISABLED -> isIncognitoMode.value = false
                    }
                }
        }
    }

    private suspend fun getStateFromIntent(manga: Content): ReaderState? {
        // check if we have at least some chapters loaded
        if (manga.chapters.isNullOrEmpty()) {
            return null
        }
        // specific state is requested
        val requestedState: ReaderState? = savedStateHandle[ReaderIntent.EXTRA_STATE]
        if (requestedState != null) {
            return if (manga.findChapterById(requestedState.chapterId) != null) {
                requestedState
            } else {
                null
            }
        }

        val requestedBranch: String? = savedStateHandle[ReaderIntent.EXTRA_BRANCH]
        // continue reading
        val history = historyRepository.getOne(manga)
        if (history != null) {
            val chapter = manga.findChapterById(history.chapterId) ?: return null
            // specified branch is requested
            return if (ReaderIntent.EXTRA_BRANCH in savedStateHandle) {
                if (chapter.branch == requestedBranch) {
                    ReaderState(history)
                } else {
                    ReaderState(manga, requestedBranch)
                }
            } else {
                ReaderState(history)
            }
        }

        // start from beginning
        val preferredBranch = requestedBranch ?: manga.getPreferredBranch(null)
        return ReaderState(manga, preferredBranch)
    }

    private fun Exception.mergeWith(other: Exception?): Exception = if (other == null) {
        this
    } else {
        other.addSuppressed(this)
        other
    }
}
