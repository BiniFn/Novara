package org.skepsun.kototoro.details.ui.pager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.sheet.AdaptiveSheetBehavior.Companion.STATE_COLLAPSED
import org.skepsun.kototoro.core.ui.sheet.AdaptiveSheetBehavior.Companion.STATE_DRAGGING
import org.skepsun.kototoro.core.ui.sheet.AdaptiveSheetBehavior.Companion.STATE_EXPANDED
import org.skepsun.kototoro.core.ui.sheet.AdaptiveSheetBehavior.Companion.STATE_SETTLING
import org.skepsun.kototoro.core.ui.sheet.AdaptiveSheetCallback
import org.skepsun.kototoro.core.ui.sheet.BaseAdaptiveSheet
import org.skepsun.kototoro.core.ui.util.MenuInvalidator
import org.skepsun.kototoro.core.ui.util.ReversibleActionObserver
import org.skepsun.kototoro.core.util.FoldableUtils
import org.skepsun.kototoro.core.util.ext.menuView
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.databinding.SheetChaptersPagesBinding
import org.skepsun.kototoro.details.ui.DetailsViewModel
import org.skepsun.kototoro.details.ui.ReadButtonDelegate
import org.skepsun.kototoro.details.ui.pager.bookmarks.BookmarksViewModel
import org.skepsun.kototoro.details.ui.pager.bookmarks.compose.BookmarksScreenRoot
import org.skepsun.kototoro.details.ui.pager.chapters.compose.ChaptersScreenRoot
import org.skepsun.kototoro.details.ui.pager.chapters.compose.ChapterSelectionBar
import org.skepsun.kototoro.details.ui.pager.chapters.compose.ChapterSelectionUiState
import org.skepsun.kototoro.details.ui.pager.pages.PagesViewModel
import org.skepsun.kototoro.details.ui.pager.pages.compose.PagesScreenRoot
import org.skepsun.kototoro.download.ui.worker.DownloadStartedObserver
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentType
import javax.inject.Inject

@AndroidEntryPoint
class ChaptersPagesSheet : BaseAdaptiveSheet<SheetChaptersPagesBinding>(),
    TabLayout.OnTabSelectedListener,
    AdaptiveSheetCallback {

    @Inject
    lateinit var settings: AppSettings

    @Inject
    lateinit var pageSaveHelperFactory: org.skepsun.kototoro.reader.ui.PageSaveHelper.Factory

    private val viewModel by ChaptersPagesViewModel.ActivityVMLazy(this)
    private val pagesViewModel by viewModels<PagesViewModel>()
    private val bookmarksViewModel by viewModels<BookmarksViewModel>()

    private var isFoldUnfolded: Boolean = false
    private var activeTabs: List<Int> = emptyList()

    override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): SheetChaptersPagesBinding {
        return SheetChaptersPagesBinding.inflate(inflater, container, false)
    }

    override fun onViewBindingCreated(binding: SheetChaptersPagesBinding, savedInstanceState: Bundle?) {
        super.onViewBindingCreated(binding, savedInstanceState)
        disableFitToContents()

        val args = arguments ?: Bundle.EMPTY
        val source = resolveContentSource()
        val contentType = source?.getContentType()
        val tabsList = resolveAvailableTabIds(contentType)
        activeTabs = tabsList

        var selectedTabId = args.getInt(AppRouter.KEY_TAB, settings.defaultDetailsTab)
        selectedTabId = resolveSelectedTabId(selectedTabId, tabsList)

        binding.tabs.removeAllTabs()
        for (tabId in tabsList) {
            binding.tabs.addTab(
                binding.tabs.newTab()
                    .setIcon(tabIconRes(tabId))
                    .setContentDescription(tabTitleRes(tabId)),
            )
        }

        (viewModel as? DetailsViewModel)?.let { dvm ->
            ReadButtonDelegate(binding.splitButtonRead, dvm, router).attach(viewLifecycleOwner)
        }

        val context = requireContext()
        val pageSaveHelper = pageSaveHelperFactory.create(this)
        val viewForSnackbar = binding.composePager

        binding.composePager.apply {
            setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                val composeScope = rememberCoroutineScope()
                var chapterSelectionState by remember { mutableStateOf<ChapterSelectionUiState?>(null) }
                val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                    initialPage = tabsList.indexOf(selectedTabId).coerceAtLeast(0),
                    pageCount = { tabsList.size },
                )

                LaunchedEffect(pagerState.currentPage) {
                    if (binding.tabs.selectedTabPosition != pagerState.currentPage) {
                        binding.tabs.selectTab(binding.tabs.getTabAt(pagerState.currentPage))
                    }
                    this@ChaptersPagesSheet.onPageChanged(tabsList[pagerState.currentPage])
                }

                DisposableEffect(binding.tabs, pagerState) {
                    val listener = object : TabLayout.OnTabSelectedListener {
                        override fun onTabSelected(tab: TabLayout.Tab) {
                            if (pagerState.currentPage == tab.position) {
                                return
                            }
                            composeScope.launch {
                                pagerState.animateScrollToPage(tab.position)
                            }
                        }

                        override fun onTabUnselected(tab: TabLayout.Tab?) = Unit

                        override fun onTabReselected(tab: TabLayout.Tab?) = Unit
                    }
                    binding.tabs.addOnTabSelectedListener(listener)
                    onDispose {
                        binding.tabs.removeOnTabSelectedListener(listener)
                    }
                }

                org.skepsun.kototoro.core.ui.theme.KototoroTheme {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(rememberNestedScrollInteropConnection()),
                    ) {
                        chapterSelectionState?.let { selState ->
                            ChapterSelectionBar(
                                state = selState,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        androidx.compose.foundation.pager.HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            when (tabsList[page]) {
                                TAB_CHAPTERS -> ChaptersScreenRoot(
                                    viewModel = viewModel,
                                    router = router,
                                    context = context,
                                    viewForSnackbar = viewForSnackbar,
                                    lifecycleOwner = viewLifecycleOwner,
                                    handleSelectionBackPressInternally = true,
                                    onSelectionStateChange = { state ->
                                        chapterSelectionState = state
                                        viewBinding?.toolbar?.isVisible = state == null
                                    },
                                )

                                TAB_PAGES -> PagesScreenRoot(
                                    activityViewModel = viewModel,
                                    router = router,
                                    context = context,
                                    pageSaveHelper = pageSaveHelper,
                                    viewForSnackbar = viewForSnackbar,
                                    lifecycleOwner = viewLifecycleOwner,
                                    viewModel = pagesViewModel,
                                )

                                TAB_BOOKMARKS -> BookmarksScreenRoot(
                                    activityViewModel = viewModel,
                                    router = router,
                                    context = context,
                                    viewModel = bookmarksViewModel,
                                )
                            }
                        }
                    }
                }
            }
        }
        binding.tabs.isVisible = tabsList.size > 1

        val menuProvider = ChapterPagesMenuProvider(
            viewModel = viewModel,
            sheet = this,
            getCurrentTab = {
                tabsList.getOrElse(binding.tabs.selectedTabPosition.coerceAtLeast(0)) { tabsList.firstOrNull() ?: TAB_CHAPTERS }
            },
            getTabCount = { binding.tabs.tabCount },
            settings = settings,
        )
        onBackPressedDispatcher.addCallback(viewLifecycleOwner, menuProvider)
        binding.toolbar.addMenuProvider(menuProvider)

        val menuInvalidator = MenuInvalidator(binding.toolbar)
        viewModel.isChaptersReversed.observe(viewLifecycleOwner, menuInvalidator)
        viewModel.isChaptersInGridView.observe(viewLifecycleOwner, menuInvalidator)
        viewModel.isDownloadedOnly.observe(viewLifecycleOwner, menuInvalidator)

        addSheetCallback(this, viewLifecycleOwner)

        viewModel.newChaptersCount.observe(viewLifecycleOwner, ::onNewChaptersChanged)
        if (dialog != null) {
            viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(binding.composePager, this))
            viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(binding.composePager))
            viewModel.onDownloadStarted.observeEvent(viewLifecycleOwner, DownloadStartedObserver(binding.composePager))
        } else {
            PeekHeightController(arrayOf(binding.headerBar, binding.toolbar)).attach()
        }

        observeFoldableStateForReadButton()
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

    override fun onStateChanged(bottomSheet: View, newState: Int) {
        val binding = viewBinding ?: return
        binding.layoutTouchBlock.isTouchEventsAllowed = dialog != null || newState != STATE_COLLAPSED
        if (newState == STATE_DRAGGING || newState == STATE_SETTLING) {
            return
        }
        binding.toolbar.menuView?.isVisible = newState == STATE_EXPANDED
        binding.splitButtonRead.isVisible = newState != STATE_EXPANDED && viewModel is DetailsViewModel
    }

    override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit

    override fun onTabSelected(tab: TabLayout.Tab?) = Unit

    override fun onTabUnselected(tab: TabLayout.Tab?) = Unit

    override fun onTabReselected(tab: TabLayout.Tab?) = Unit

    override fun expandAndLock() {
        super.expandAndLock()
        adjustLockState()
    }

    override fun unlock() {
        super.unlock()
        adjustLockState()
    }

    private fun adjustLockState() {
        viewBinding?.run {
            tabs.visibility = when {
                tabs.tabCount <= 1 -> View.GONE
                isLocked -> View.INVISIBLE
                else -> View.VISIBLE
            }
        }
    }

    fun selectTab(tab: Int) {
        val targetIndex = activeTabs.indexOf(tab)
        if (targetIndex < 0) {
            return
        }
        viewBinding?.tabs?.selectTab(viewBinding?.tabs?.getTabAt(targetIndex))
    }

    private fun onPageChanged(tabId: Int) {
        viewBinding?.toolbar?.invalidateMenu()
        settings.lastDetailsTab = tabId
    }

    private fun onNewChaptersChanged(counter: Int) {
        val tab = viewBinding?.tabs?.getTabAt(0) ?: return
        if (counter == 0) {
            tab.removeBadge()
        } else {
            val badge = tab.orCreateBadge
            badge.number = counter
        }
    }

    private fun observeFoldableStateForReadButton() {
        val owner = viewLifecycleOwner
        val foldableState = FoldableUtils.observeFoldableState(requireActivity(), owner)
        owner.lifecycleScope.launch {
            foldableState.collect { unfolded ->
                isFoldUnfolded = unfolded
                adjustReadSplitButtonScale()
            }
        }
    }

    private fun adjustReadSplitButtonScale() {
        val binding = viewBinding ?: return
        binding.splitButtonRead.scaleX = 0.8f
        binding.splitButtonRead.scaleY = 0.8f
    }

    private fun resolveContentSource(): ContentSource? {
        return when (viewModel) {
            is DetailsViewModel -> (viewModel as DetailsViewModel).manga.value?.source
            is org.skepsun.kototoro.video.ui.VideoChaptersViewModel ->
                (viewModel as org.skepsun.kototoro.video.ui.VideoChaptersViewModel).mangaDetails.value?.toContent()?.source

            else -> null
        } ?: activity?.intent
            ?.getParcelableExtra<org.skepsun.kototoro.core.model.parcelable.ParcelableContent>(AppRouter.KEY_MANGA)
            ?.manga
            ?.source
    }

    private fun resolveAvailableTabIds(contentType: ContentType?): List<Int> = buildList {
        add(TAB_CHAPTERS)
        val isNovel = contentType == ContentType.NOVEL || contentType == ContentType.HENTAI_NOVEL
        val isVideo = contentType == ContentType.VIDEO || contentType == ContentType.HENTAI_VIDEO
        if (settings.isPagesTabEnabled && !isNovel && !isVideo) {
            add(TAB_PAGES)
        }
        if (!isVideo) {
            add(TAB_BOOKMARKS)
        }
    }

    private fun resolveSelectedTabId(requestedTabId: Int, availableTabs: List<Int>): Int {
        return if (requestedTabId in availableTabs) {
            requestedTabId
        } else {
            when {
                requestedTabId > TAB_CHAPTERS -> {
                    availableTabs.getOrElse((requestedTabId - 1).coerceAtLeast(0)) { availableTabs.first() }
                }
                else -> availableTabs.first()
            }
        }
    }

    private fun tabTitleRes(tabId: Int): Int = when (tabId) {
        TAB_CHAPTERS -> R.string.chapters
        TAB_PAGES -> R.string.pages
        TAB_BOOKMARKS -> R.string.bookmarks
        else -> R.string.chapters
    }

    private fun tabIconRes(tabId: Int): Int = when (tabId) {
        TAB_PAGES -> R.drawable.ic_grid
        TAB_BOOKMARKS -> R.drawable.ic_bookmark
        else -> R.drawable.ic_list
    }

    companion object {
        const val TAB_CHAPTERS = 0
        const val TAB_PAGES = 1
        const val TAB_BOOKMARKS = 2
    }
}
