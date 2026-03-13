package org.skepsun.kototoro.details.ui.pager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.res.Configuration
import androidx.appcompat.view.ActionMode
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
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChaptersPagesSheet : BaseAdaptiveSheet<SheetChaptersPagesBinding>(),
	TabLayout.OnTabSelectedListener,
	ActionModeListener,
	AdaptiveSheetCallback {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var jsonSourceManager: org.skepsun.kototoro.core.jsonsource.JsonSourceManager

	private val viewModel by ChaptersPagesViewModel.ActivityVMLazy(this)

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
		// 优先从ViewModel获取，如果为null则从Activity的Intent中获取
		val source = when (viewModel) {
			is DetailsViewModel -> (viewModel as DetailsViewModel).manga.value?.source
			is org.skepsun.kototoro.video.ui.VideoChaptersViewModel -> 
				(viewModel as org.skepsun.kototoro.video.ui.VideoChaptersViewModel).mangaDetails.value?.toManga()?.source
			else -> null
		} ?: run {
			// 从Activity的Intent中获取Manga，这在Fragment Arguments为空时作为备用方案
			val intent = activity?.intent
			intent?.getParcelableExtra<org.skepsun.kototoro.core.model.parcelable.ParcelableManga>(AppRouter.KEY_MANGA)?.manga?.source
		}
		
		android.util.Log.d("ChaptersPagesSheet", "Source: $source, type: ${source?.javaClass?.simpleName}")
		val contentType = source?.let { getContentType(it) }
		android.util.Log.d("ChaptersPagesSheet", "ContentType: $contentType")
		val isNovel = contentType == org.skepsun.kototoro.parsers.model.ContentType.NOVEL || contentType == org.skepsun.kototoro.parsers.model.ContentType.HENTAI_NOVEL
		val isVideo = contentType == org.skepsun.kototoro.parsers.model.ContentType.VIDEO || contentType == org.skepsun.kototoro.parsers.model.ContentType.HENTAI_VIDEO
		android.util.Log.d("ChaptersPagesSheet", "isNovel: $isNovel, isVideo: $isVideo")
		val isPagesTabEnabled = settings.isPagesTabEnabled && !isNovel && !isVideo
		val isBookmarksTabEnabled = !isVideo // 视频不需要书签功能

		
		val adapter = ChaptersPagesAdapter(this, isPagesTabEnabled, isBookmarksTabEnabled)
		// 调整默认标签，确保不超出可用标签范围
		if (!isPagesTabEnabled && defaultTab > TAB_CHAPTERS) {
			defaultTab = (defaultTab - 1).coerceAtLeast(TAB_CHAPTERS)
		}
		defaultTab = defaultTab.coerceIn(0, adapter.itemCount - 1)
		(viewModel as? DetailsViewModel)?.let { dvm ->
			ReadButtonDelegate(binding.splitButtonRead, dvm, router).attach(viewLifecycleOwner)
		}
		binding.pager.offscreenPageLimit = adapter.itemCount
		binding.pager.recyclerView?.isNestedScrollingEnabled = false
		binding.pager.adapter = adapter
		binding.pager.doOnPageChanged(::onPageChanged)
		TabLayoutMediator(binding.tabs, binding.pager, adapter).attach()
		binding.tabs.addOnTabSelectedListener(this)
		binding.pager.setCurrentItem(defaultTab, false)
			binding.tabs.isVisible = adapter.itemCount > 1

		val menuProvider = ChapterPagesMenuProvider(viewModel, this, binding.pager, settings)
		onBackPressedDispatcher.addCallback(viewLifecycleOwner, menuProvider)
		binding.toolbar.addMenuProvider(menuProvider)

		val menuInvalidator = MenuInvalidator(binding.toolbar)
		viewModel.isChaptersReversed.observe(viewLifecycleOwner, menuInvalidator)
		viewModel.isChaptersInGridView.observe(viewLifecycleOwner, menuInvalidator)
		viewModel.isDownloadedOnly.observe(viewLifecycleOwner, menuInvalidator)

		actionModeDelegate?.addListener(this, viewLifecycleOwner)
		addSheetCallback(this, viewLifecycleOwner)

		viewModel.newChaptersCount.observe(viewLifecycleOwner, ::onNewChaptersChanged)
		if (dialog != null) {
			viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(binding.pager, this))
			viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(binding.pager))
			viewModel.onDownloadStarted.observeEvent(viewLifecycleOwner, DownloadStartedObserver(binding.pager))
		} else {
			PeekHeightController(arrayOf(binding.headerBar, binding.toolbar)).attach()
		}

		// 观察折叠屏状态并在竖屏展开时缩放分裂阅读按钮至 80%
		observeFoldableStateForReadButton()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onStateChanged(sheet: View, newState: Int) {
        val binding = viewBinding ?: return
        binding.layoutTouchBlock.isTouchEventsAllowed = dialog != null || newState != STATE_COLLAPSED
        if (newState == STATE_DRAGGING || newState == STATE_SETTLING) {
            return
        }
		val isActionModeStarted = actionModeDelegate?.isActionModeStarted == true
		binding.toolbar.menuView?.isVisible = newState == STATE_EXPANDED && !isActionModeStarted
		binding.splitButtonRead.isVisible = newState != STATE_EXPANDED && !isActionModeStarted
			&& viewModel is DetailsViewModel
	}

	override fun onActionModeStarted(mode: ActionMode) {
		viewBinding?.toolbar?.menuView?.isVisible = false
		view?.post(::expandAndLock)
	}

	override fun onActionModeFinished(mode: ActionMode) {
		unlock()
		val state = behavior?.state ?: STATE_EXPANDED
		viewBinding?.toolbar?.menuView?.isVisible = state != STATE_COLLAPSED
	}

	override fun onTabSelected(tab: TabLayout.Tab?) = Unit

	override fun onTabUnselected(tab: TabLayout.Tab?) = Unit

	override fun onTabReselected(tab: TabLayout.Tab?) {
		val f = childFragmentManager.findCurrentPagerFragment(
			viewBinding?.pager ?: return,
		) as? RecyclerViewOwner ?: return
		f.recyclerView?.smoothScrollToTop()
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
			pager.isUserInputEnabled = !isLocked
			tabs.visibility = when {
				(pager.adapter?.itemCount ?: 0) <= 1 -> View.GONE
				isLocked -> View.INVISIBLE
				else -> View.VISIBLE
			}
		}
	}

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
        // 统一所有场景下为 80% 缩放，避免割裂感
        binding.splitButtonRead.scaleX = 0.8f
        binding.splitButtonRead.scaleY = 0.8f
    }

	private fun getContentType(source: org.skepsun.kototoro.parsers.model.MangaSource): org.skepsun.kototoro.parsers.model.ContentType {
		return source.getContentType()
	}

	companion object {

		const val TAB_CHAPTERS = 0
		const val TAB_PAGES = 1
		const val TAB_BOOKMARKS = 2
	}
}
