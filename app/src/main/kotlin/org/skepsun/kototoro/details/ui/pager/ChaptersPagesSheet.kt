package org.skepsun.kototoro.details.ui.pager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.res.Configuration
import org.skepsun.kototoro.R
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.sheet.AdaptiveSheetBehavior.Companion.STATE_COLLAPSED
import org.skepsun.kototoro.core.ui.sheet.AdaptiveSheetBehavior.Companion.STATE_DRAGGING
import org.skepsun.kototoro.core.ui.sheet.AdaptiveSheetBehavior.Companion.STATE_EXPANDED
import org.skepsun.kototoro.core.ui.sheet.AdaptiveSheetBehavior.Companion.STATE_SETTLING
import org.skepsun.kototoro.core.ui.sheet.AdaptiveSheetCallback
import org.skepsun.kototoro.core.ui.sheet.BaseAdaptiveSheet
import org.skepsun.kototoro.core.ui.util.ActionModeListener
import org.skepsun.kototoro.core.ui.util.MenuInvalidator
import org.skepsun.kototoro.core.ui.util.RecyclerViewOwner
import org.skepsun.kototoro.core.ui.util.ReversibleActionObserver
import org.skepsun.kototoro.core.util.FoldableUtils
import org.skepsun.kototoro.core.util.ext.doOnPageChanged
import org.skepsun.kototoro.core.util.ext.findCurrentPagerFragment
import org.skepsun.kototoro.core.util.ext.menuView
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.recyclerView
import org.skepsun.kototoro.core.util.ext.smoothScrollToTop
import org.skepsun.kototoro.core.model.unwrap
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.databinding.SheetChaptersPagesBinding
import org.skepsun.kototoro.details.ui.DetailsViewModel
import org.skepsun.kototoro.details.ui.ReadButtonDelegate
import org.skepsun.kototoro.download.ui.worker.DownloadStartedObserver
import org.skepsun.kototoro.core.model.unwrap
import javax.inject.Inject
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.launch
import org.skepsun.kototoro.details.ui.pager.chapters.compose.ChaptersScreenRoot
import org.skepsun.kototoro.details.ui.pager.pages.compose.PagesScreenRoot
import org.skepsun.kototoro.details.ui.pager.bookmarks.compose.BookmarksScreenRoot
import androidx.fragment.app.viewModels
import org.skepsun.kototoro.details.ui.pager.pages.PagesViewModel
import org.skepsun.kototoro.details.ui.pager.bookmarks.BookmarksViewModel

@AndroidEntryPoint
class ChaptersPagesSheet : BaseAdaptiveSheet<SheetChaptersPagesBinding>(),
	TabLayout.OnTabSelectedListener,
	AdaptiveSheetCallback {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var jsonSourceManager: org.skepsun.kototoro.core.jsonsource.JsonSourceManager

	@Inject
	lateinit var pageSaveHelperFactory: org.skepsun.kototoro.reader.ui.PageSaveHelper.Factory

	private val viewModel by ChaptersPagesViewModel.ActivityVMLazy(this)
	private val pagesViewModel by viewModels<PagesViewModel>()
	private val bookmarksViewModel by viewModels<BookmarksViewModel>()

	private var isFoldUnfolded: Boolean = false

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): SheetChaptersPagesBinding {
		return SheetChaptersPagesBinding.inflate(inflater, container, false)
	}

		override fun onViewBindingCreated(binding: SheetChaptersPagesBinding, savedInstanceState: Bundle?) {
			super.onViewBindingCreated(binding, savedInstanceState)
			disableFitToContents()

		val args = arguments ?: Bundle.EMPTY
		var defaultTab = args.getInt(AppRouter.KEY_TAB, settings.defaultDetailsTab)
		
		// 对于小说和视频类型，禁用页面（缩略图）和书签标签
		// 支持从DetailsViewModel或VideoChaptersViewModel获取内容类型
		// 优先从ViewModel获取，如果为null则从Activity的Intent中获�?
		val source = when (viewModel) {
			is DetailsViewModel -> (viewModel as DetailsViewModel).manga.value?.source
			is org.skepsun.kototoro.video.ui.VideoChaptersViewModel -> 
				(viewModel as org.skepsun.kototoro.video.ui.VideoChaptersViewModel).mangaDetails.value?.toContent()?.source
			else -> null
		} ?: run {
			// 从Activity的Intent中获取Content，这在Fragment Arguments为空时作为备用方�?
			val intent = activity?.intent
			intent?.getParcelableExtra<org.skepsun.kototoro.core.model.parcelable.ParcelableContent>(AppRouter.KEY_MANGA)?.manga?.source
		}
		
		android.util.Log.d("ChaptersPagesSheet", "Source: $source, type: ${source?.javaClass?.simpleName}")
		val contentType = source?.let { getContentType(it) }
		android.util.Log.d("ChaptersPagesSheet", "ContentType: $contentType")
		val isNovel = contentType == org.skepsun.kototoro.parsers.model.ContentType.NOVEL || contentType == org.skepsun.kototoro.parsers.model.ContentType.HENTAI_NOVEL
		val isVideo = contentType == org.skepsun.kototoro.parsers.model.ContentType.VIDEO || contentType == org.skepsun.kototoro.parsers.model.ContentType.HENTAI_VIDEO
		android.util.Log.d("ChaptersPagesSheet", "isNovel: $isNovel, isVideo: $isVideo")
		val isPagesTabEnabled = settings.isPagesTabEnabled && !isNovel && !isVideo
		val isBookmarksTabEnabled = !isVideo // 视频不需要书签功�?

		
		val tabsList = mutableListOf<Int>()
		tabsList.add(R.string.chapters)
		if (isPagesTabEnabled) tabsList.add(R.string.pages)
		if (isBookmarksTabEnabled) tabsList.add(R.string.bookmarks)

		for (titleRes in tabsList) {
			binding.tabs.addTab(binding.tabs.newTab().setText(titleRes))
		}

		if (!isPagesTabEnabled && defaultTab > TAB_CHAPTERS) {
			defaultTab = (defaultTab - 1).coerceAtLeast(TAB_CHAPTERS)
		}
		defaultTab = defaultTab.coerceIn(0, tabsList.size - 1)

		(viewModel as? DetailsViewModel)?.let { dvm ->
			ReadButtonDelegate(binding.splitButtonRead, dvm, router).attach(viewLifecycleOwner)
		}

		val context = requireContext()
		val pageSaveHelper = pageSaveHelperFactory.create(this)
		val viewForSnackbar = binding.composePager

		binding.composePager.apply {
			setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
			setContent {
				val pagerState = androidx.compose.foundation.pager.rememberPagerState(
					initialPage = defaultTab,
					pageCount = { tabsList.size }
				)

				androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
					if (binding.tabs.selectedTabPosition != pagerState.currentPage) {
						binding.tabs.selectTab(binding.tabs.getTabAt(pagerState.currentPage))
					}
					this@ChaptersPagesSheet.onPageChanged(pagerState.currentPage)
				}

				androidx.compose.runtime.DisposableEffect(binding.tabs) {
					val listener = object : TabLayout.OnTabSelectedListener {
						override fun onTabSelected(tab: TabLayout.Tab) {
							lifecycleScope.launch {
								pagerState.animateScrollToPage(tab.position)
							}
						}
						override fun onTabUnselected(tab: TabLayout.Tab?) {}
						override fun onTabReselected(tab: TabLayout.Tab?) {}
					}
					binding.tabs.addOnTabSelectedListener(listener)
					onDispose {
						binding.tabs.removeOnTabSelectedListener(listener)
					}
				}

				org.skepsun.kototoro.core.ui.theme.KototoroTheme {
					androidx.compose.foundation.pager.HorizontalPager(
						state = pagerState,
						modifier = androidx.compose.ui.Modifier.fillMaxSize()
					) { page ->
						when (tabsList[page]) {
							R.string.chapters -> org.skepsun.kototoro.details.ui.pager.chapters.compose.ChaptersScreenRoot(
								viewModel = viewModel,
								router = router,
								context = context,
								viewForSnackbar = viewForSnackbar,
								lifecycleOwner = viewLifecycleOwner
							)
							R.string.pages -> PagesScreenRoot(
								activityViewModel = viewModel,
								router = router,
								context = context,
								pageSaveHelper = pageSaveHelper,
								viewForSnackbar = viewForSnackbar,
								lifecycleOwner = viewLifecycleOwner,
								viewModel = pagesViewModel
							)
							R.string.bookmarks -> BookmarksScreenRoot(
								activityViewModel = viewModel,
								router = router,
								context = context,
								viewModel = bookmarksViewModel
							)
						}
					}
				}
			}
		}
		binding.tabs.isVisible = tabsList.size > 1

		val menuProvider = ChapterPagesMenuProvider(
			viewModel,
			this,
			{ 
				var page = binding.tabs.selectedTabPosition
				if (page > 0 && binding.tabs.tabCount == 2) page++
				page
			},
			{ binding.tabs.tabCount },
			settings
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

		// 观察折叠屏状态并在竖屏展开时缩放分裂阅读按钮至 80%
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
		binding.splitButtonRead.isVisible = newState != STATE_EXPANDED
			&& viewModel is DetailsViewModel
	}

	override fun onSlide(bottomSheet: View, slideOffset: Float) {}

	override fun onTabSelected(tab: TabLayout.Tab?) = Unit

	override fun onTabUnselected(tab: TabLayout.Tab?) = Unit

	override fun onTabReselected(tab: TabLayout.Tab?) {
		// Handled via smoothScrollToTop manually locally inside Compose lists if needed
		// Historically passed to child fragment
	}

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
			// pager.isUserInputEnabled is handled via PagerState in Compose

			tabs.visibility = when {
				tabs.tabCount <= 1 -> View.GONE
				isLocked -> View.INVISIBLE
				else -> View.VISIBLE
			}
		}
	}

	fun selectTab(tab: Int) { viewBinding?.tabs?.selectTab(viewBinding?.tabs?.getTabAt(tab)) }
	private fun onPageChanged(position: Int) {
		viewBinding?.toolbar?.invalidateMenu()
		settings.lastDetailsTab = position
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
        // 统一所有场景下�?80% 缩放，避免割裂感
        binding.splitButtonRead.scaleX = 0.8f
        binding.splitButtonRead.scaleY = 0.8f
    }

	private fun getContentType(source: org.skepsun.kototoro.parsers.model.ContentSource): org.skepsun.kototoro.parsers.model.ContentType {
		return source.getContentType()
	}

	companion object {

		const val TAB_CHAPTERS = 0
		const val TAB_PAGES = 1
		const val TAB_BOOKMARKS = 2
	}
}
