package org.skepsun.kototoro.details.ui.pager

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import okio.FileNotFoundException
import org.skepsun.kototoro.bookmarks.domain.BookmarksRepository
import org.skepsun.kototoro.core.model.toChipModel
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsStateFlow
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.ui.util.ReversibleAction
import org.skepsun.kototoro.core.util.LocaleStringComparator
import org.skepsun.kototoro.core.util.ext.MutableEventFlow
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.core.util.ext.combine
import org.skepsun.kototoro.core.util.ext.requireValue
import org.skepsun.kototoro.core.util.ext.sortedWithSafe
import org.skepsun.kototoro.details.data.ContentDetails
import org.skepsun.kototoro.details.domain.DetailsInteractor
import org.skepsun.kototoro.details.ui.DetailsActivity
import org.skepsun.kototoro.details.ui.DetailsViewModel
import org.skepsun.kototoro.details.ui.mapChapters
import org.skepsun.kototoro.details.ui.model.ChapterListItem
import org.skepsun.kototoro.download.ui.worker.DownloadTask
import org.skepsun.kototoro.download.ui.worker.DownloadWorker
import org.skepsun.kototoro.history.data.HistoryRepository
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.local.domain.DeleteLocalContentUseCase
import org.skepsun.kototoro.local.domain.model.LocalContent
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentState
import org.skepsun.kototoro.reader.ui.ReaderActivity
import org.skepsun.kototoro.reader.ui.ReaderState
import org.skepsun.kototoro.reader.ui.ReaderViewModel
import org.skepsun.kototoro.video.ui.VideoPlayerActivity
import org.skepsun.kototoro.video.ui.VideoChaptersViewModel

abstract class ChaptersPagesViewModel(
	@JvmField protected val settings: AppSettings,
	@JvmField protected val interactor: DetailsInteractor,
	private val bookmarksRepository: BookmarksRepository,
	private val historyRepository: HistoryRepository,
	private val downloadScheduler: DownloadWorker.Scheduler,
	private val deleteLocalContentUseCase: DeleteLocalContentUseCase,
	private val localStorageChanges: SharedFlow<LocalContent?>,
) : BaseViewModel() {

	val mangaDetails = MutableStateFlow<ContentDetails?>(null)
	val readingState = MutableStateFlow<ReaderState?>(null)

	val onActionDone = MutableEventFlow<ReversibleAction>()
	val onDownloadStarted = MutableEventFlow<Unit>()
	val onContentRemoved = MutableEventFlow<Content>()

	private val chaptersQuery = MutableStateFlow("")
	val selectedBranch = MutableStateFlow<String?>(null)

	val manga = mangaDetails.map { x -> x?.toContent() }
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	val coverUrl = mangaDetails.map { x -> x?.coverUrl }
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	val isChaptersReversed = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.Default,
		key = AppSettings.KEY_REVERSE_CHAPTERS,
		valueProducer = { isChaptersReverse },
	)

	val isChaptersInGridView = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.Default,
		key = AppSettings.KEY_GRID_VIEW_CHAPTERS,
		valueProducer = { isChaptersGridView },
	)

	val isDownloadedOnly = MutableStateFlow(false)

	val newChaptersCount = mangaDetails.flatMapLatest { d ->
		if (d?.isLocal == false) {
			interactor.observeNewChapters(d.id)
		} else {
			flowOf(0)
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, 0)

	val emptyReason: StateFlow<EmptyContentReason?> = combine(
		mangaDetails,
		isLoading,
		onError.onStart { emit(null) },
	) { details, loading, error ->
		when {
			details == null || loading -> null
			details.chapters.isNotEmpty() -> null
			details.toContent().state == ContentState.RESTRICTED -> EmptyContentReason.RESTRICTED
			error != null -> EmptyContentReason.LOADING_ERROR
			else -> EmptyContentReason.NO_CHAPTERS
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(), null)

	val bookmarks = mangaDetails.flatMapLatest {
		if (it != null) {
			bookmarksRepository.observeBookmarks(it.toContent()).withErrorHandling()
		} else {
			flowOf(emptyList())
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Lazily, emptyList())

	private val downloadInvalidation = MutableStateFlow(0)

	private val baseChaptersFlow = combine(
		mangaDetails,
		readingState.map { it?.chapterId ?: 0L }.distinctUntilChanged(),
		selectedBranch,
		newChaptersCount,
		bookmarks,
		isChaptersInGridView,
		isDownloadedOnly,
	) { manga, currentChapterId, branch, news, bookmarks, grid, downloadedOnly ->
		val baseChapters = manga?.mapChapters(
			currentChapterId = currentChapterId,
			newCount = news,
			branch = branch,
			bookmarks = bookmarks,
			isGrid = grid,
			isDownloadedOnly = downloadedOnly,
		).orEmpty()
		expandEpubChaptersIfNeeded(baseChapters)
	}

	val chapters = combine(
		combine(baseChaptersFlow, downloadInvalidation) { list, _ -> list },
		isChaptersReversed,
		chaptersQuery,
	) { list, reversed, query ->
		(if (reversed) list.asReversed() else list).filterSearch(query)
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

	protected fun notifyDownloadChanged() {
		downloadInvalidation.value = downloadInvalidation.value + 1
	}
	
	/**
	 * Expand EPUB chapters by loading mappings from database.
	 * This is a hook that can be overridden by subclasses to provide EPUB expansion.
	 * Default implementation returns chapters as-is.
	 */
	protected open suspend fun expandEpubChaptersIfNeeded(chapters: List<ChapterListItem>): List<ChapterListItem> {
		return chapters
	}

	val quickFilter = combine(
		mangaDetails,
		selectedBranch,
	) { details, branch ->
		val branches = details?.chapters?.toList()?.sortedWithSafe(
			compareBy(LocaleStringComparator()) { it.first },
		).orEmpty()
		if (branches.size > 1) {
			branches.map {
				val option = ListFilterOption.Branch(titleText = it.first, chaptersCount = it.second.size)
				option.toChipModel(isChecked = it.first == branch)
			}
		} else {
			emptyList()
		}
	}

	init {
		launchJob(Dispatchers.Default) {
			localStorageChanges
				.collect { onDownloadComplete(it) }
		}
	}

	fun setChaptersReversed(newValue: Boolean) {
		settings.isChaptersReverse = newValue
	}

	fun setChaptersInGridView(newValue: Boolean) {
		settings.isChaptersGridView = newValue
	}

	fun setSelectedBranch(branch: String?) {
		selectedBranch.value = branch
	}

	fun performChapterSearch(query: String?) {
		chaptersQuery.value = query?.trim().orEmpty()
	}

	fun getContentOrNull(): Content? = mangaDetails.value?.toContent()

	fun requireContent() = mangaDetails.requireValue().toContent()

	fun markChapterAsCurrent(chapterId: Long) {
		launchJob(Dispatchers.Default) {
			val manga = mangaDetails.requireValue()
			// Use all chapters for global progress calculation
			val allChapters = manga.allChapters
			val chapterIndex = allChapters.indexOfFirst { it.id == chapterId }
			check(chapterIndex in allChapters.indices) { "Chapter not found" }
			val percent = chapterIndex / allChapters.size.toFloat()
			historyRepository.addOrUpdate(
				manga = manga.toContent(),
				chapterId = chapterId,
				page = 0,
				scroll = 0,
				percent = percent,
				force = true,
			)
		}
	}

	fun download(chaptersIds: Set<Long>?, allowMeteredNetwork: Boolean) {
		launchJob(Dispatchers.Default) {
			val manga = requireContent()
			val task = DownloadTask(
				mangaId = manga.id,
				isPaused = false,
				isSilent = false,
				chaptersIds = chaptersIds?.toLongArray(),
				destination = null,
				format = null,
				allowMeteredNetwork = allowMeteredNetwork,
			)
			downloadScheduler.schedule(setOf(manga to task))
			onDownloadStarted.call(Unit)
		}
	}

	fun deleteLocal() {
		val m = mangaDetails.value?.local?.manga
		if (m == null) {
			errorEvent.call(FileNotFoundException())
			return
		}
		launchLoadingJob(Dispatchers.Default) {
			deleteLocalContentUseCase(m)
			onContentRemoved.call(m)
		}
	}

	private fun List<ChapterListItem>.filterSearch(query: String): List<ChapterListItem> {
		if (query.isEmpty() || this.isEmpty()) {
			return this
		}
		return filter { it.contains(query) }
	}

	private suspend fun onDownloadComplete(downloadedContent: LocalContent?) {
		downloadedContent ?: return
		mangaDetails.update {
			interactor.updateLocal(it, downloadedContent)
		}
	}

	class ActivityVMLazy(
		private val fragment: Fragment,
	) : Lazy<ChaptersPagesViewModel> {
		private var cached: ChaptersPagesViewModel? = null

		override val value: ChaptersPagesViewModel
			get() {
				val viewModel = cached
				return if (viewModel == null) {
					val activity = fragment.requireActivity()
					val vmClass = getViewModelClass(activity)
					ViewModelProvider.create(
						store = activity.viewModelStore,
						factory = activity.defaultViewModelProviderFactory,
						extras = activity.defaultViewModelCreationExtras,
					)[vmClass].also { cached = it }
				} else {
					viewModel
				}
			}

		override fun isInitialized(): Boolean = cached != null

		private fun getViewModelClass(activity: Activity) = when (activity) {
			is ReaderActivity -> ReaderViewModel::class.java
			is DetailsActivity -> DetailsViewModel::class.java
			is VideoPlayerActivity -> VideoChaptersViewModel::class.java
			else -> error("Wrong activity ${activity.javaClass.simpleName} for ${ChaptersPagesViewModel::class.java.simpleName}")
		}
	}
}
