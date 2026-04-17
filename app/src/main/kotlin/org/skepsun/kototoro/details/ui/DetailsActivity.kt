package org.skepsun.kototoro.details.ui

import android.app.Activity
import android.app.assist.AssistContent
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.map
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.os.AppShortcutManager
import org.skepsun.kototoro.core.ui.BaseActivity
import org.skepsun.kototoro.core.ui.dialog.buildAlertDialog
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.toUriOrNull
import org.skepsun.kototoro.databinding.ActivityDetailsBinding
import org.skepsun.kototoro.details.service.ContentPrefetchService
import org.skepsun.kototoro.details.ui.compose.DetailsScreen
import org.skepsun.kototoro.details.ui.model.ChapterListItem
import org.skepsun.kototoro.main.ui.owners.BottomSheetOwner
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.details.ui.pager.pages.PagesViewModel
import org.skepsun.kototoro.details.ui.pager.bookmarks.BookmarksViewModel
import org.skepsun.kototoro.parsers.model.ContentRating
import org.skepsun.kototoro.parsers.model.ContentType
import javax.inject.Inject
import androidx.compose.ui.geometry.Rect
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import org.skepsun.kototoro.core.ui.sheet.BottomSheetCollapseCallback
import org.skepsun.kototoro.details.ui.compose.DetailsAction

@AndroidEntryPoint
class DetailsActivity :
	BaseActivity<ActivityDetailsBinding>(),
	BottomSheetOwner {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var pageSaveHelperFactory: org.skepsun.kototoro.reader.ui.PageSaveHelper.Factory

	@Inject
	lateinit var appShortcutManager: AppShortcutManager

	private val viewModel: DetailsViewModel by viewModels()
	private val pagesViewModel: PagesViewModel by viewModels()
	private val bookmarksViewModel: BookmarksViewModel by viewModels()

	private lateinit var pageSaveHelper: org.skepsun.kototoro.reader.ui.PageSaveHelper
	private var shouldRenderTransitionCover = true
	private val overrideEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			viewModel.reload()
		}
	}

	override val bottomSheet: View?
		get() = viewBinding.containerBottomSheet

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		
		pageSaveHelper = pageSaveHelperFactory.create(this)
		
		if (settings.isSharedElementTransitionsEnabled) {
			window.requestFeature(android.view.Window.FEATURE_ACTIVITY_TRANSITIONS)
			val interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
			
			val slideEnter = android.transition.Slide(android.view.Gravity.END)
			slideEnter.duration = 350L
			slideEnter.interpolator = interpolator
			slideEnter.excludeTarget(android.R.id.statusBarBackground, true)
			slideEnter.excludeTarget(android.R.id.navigationBarBackground, true)
			window.enterTransition = slideEnter

			val slideReturn = android.transition.Slide(android.view.Gravity.END)
			slideReturn.duration = 275L
			slideReturn.interpolator = interpolator
			slideReturn.excludeTarget(android.R.id.statusBarBackground, true)
			slideReturn.excludeTarget(android.R.id.navigationBarBackground, true)
			window.returnTransition = slideReturn
			
			val sharedTransition = android.transition.TransitionInflater.from(this).inflateTransition(android.R.transition.move)
			sharedTransition.duration = 350L
			sharedTransition.interpolator = interpolator
			window.sharedElementEnterTransition = sharedTransition
			window.sharedElementReturnTransition = sharedTransition
			
			window.allowEnterTransitionOverlap = true
			window.allowReturnTransitionOverlap = true
		}
		
		setContentView(ActivityDetailsBinding.inflate(layoutInflater))

		viewBinding.containerBottomSheet?.let { sheet ->
			onBackPressedDispatcher.addCallback(BottomSheetCollapseCallback(sheet))
			// BottomSheetBehavior.from(sheet) can be used to add callbacks etc if needed later!
		}

		if (settings.isSharedElementTransitionsEnabled) {
			val manga = viewModel.getContentOrNull()
			if (manga != null) {
				androidx.core.view.ViewCompat.setTransitionName(viewBinding.imageViewCover, "cover_${manga.source.name}_${manga.url}")
				supportPostponeEnterTransition()
				// Fallback to prevent indefinite hang on broken content or failed image loads
				window.decorView.postDelayed({ supportStartPostponedEnterTransition() }, 350)
			}
		}

		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		supportActionBar?.setDisplayShowTitleEnabled(false)

		viewBinding.composeView?.setContent {
			KototoroTheme {
				DetailsScreen(
					viewModel = viewModel,
					pagesViewModel = pagesViewModel,
					bookmarksViewModel = bookmarksViewModel,
					settings = settings,
					pageSaveHelper = pageSaveHelper,
					onBackClick = { onBackPressedDispatcher.onBackPressed() },
					onCoverBoundsSync = { rect, alpha ->
						syncCoverBounds(rect, alpha)
					},
					onActionClick = { action ->
						when (action) {
							DetailsAction.OpenCover -> {
								viewModel.getContentOrNull()?.let { content ->
									content.coverUrl?.let { url ->
										router.openImage(
											url = url,
											source = content.source,
											anchor = viewBinding.imageViewCover,
										)
									}
								}
							}

							DetailsAction.Resume -> {
								openReader()
							}

							DetailsAction.ResumeIncognito -> {
								openReader(isIncognitoMode = true)
							}

							DetailsAction.Favorite -> {
								viewModel.getContentOrNull()?.let(this@DetailsActivity.router::showFavoriteDialog)
							}

							DetailsAction.Download -> {
								viewModel.getContentOrNull()?.let {
									this@DetailsActivity.router.showDownloadDialog(it, viewBinding.root)
								}
							}

							DetailsAction.Share -> {
								viewModel.getContentOrNull()?.let(this@DetailsActivity.router::showShareDialog)
							}

							is DetailsAction.OpenSource -> {
								this@DetailsActivity.router.openList(action.source, null, null)
							}

							is DetailsAction.AuthorClick -> {
								this@DetailsActivity.router.showAuthorDialog(action.author, action.source)
							}

							is DetailsAction.TagClick -> {
								this@DetailsActivity.router.showTagDialog(action.tag)
							}

							is DetailsAction.SelectBranch -> {
								viewModel.setSelectedBranch(action.branch)
							}

							DetailsAction.ForgetHistory -> {
								viewModel.removeFromHistory()
							}

							DetailsAction.Translate -> {
								val hasCache = viewModel.hasTranslationCache.value
								viewModel.translateTitleAndDescription(forceRefresh = hasCache)
								Snackbar.make(
									viewBinding.root,
									if (hasCache) R.string.reader_translation_retranslate_started else R.string.translating,
									Snackbar.LENGTH_SHORT,
								).show()
							}

							DetailsAction.ToggleTranslation -> {
								viewModel.toggleTranslationDisplay()
							}

							DetailsAction.FindSimilar -> {
								viewModel.getContentOrNull()?.let {
									this@DetailsActivity.router.openSearch(it.title)
								}
							}

							DetailsAction.OpenAlternatives -> {
								viewModel.getContentOrNull()?.let(this@DetailsActivity.router::openAlternatives)
							}

							DetailsAction.OpenOnlineVariant -> {
								viewModel.remoteContent.value?.let(this@DetailsActivity.router::openDetails)
							}

							DetailsAction.OpenInBrowser -> {
								viewModel.getContentOrNull()?.let(this@DetailsActivity.router::openBrowser)
							}

							DetailsAction.OpenTracking -> {
								viewModel.getContentOrNull()?.let {
									this@DetailsActivity.router.showScrobblingSelectorSheet(it, null)
								}
							}

							DetailsAction.OpenStatistics -> {
								viewModel.getContentOrNull()?.let(this@DetailsActivity.router::showStatisticSheet)
							}

							DetailsAction.ToggleSafe -> {
								viewModel.toggleMarkSafe()
							}

							DetailsAction.DeleteLocal -> {
								confirmDeleteLocal()
							}

							DetailsAction.EditOverride -> {
								viewModel.getContentOrNull()?.let {
									overrideEditLauncher.launch(AppRouter.overrideEditIntent(this@DetailsActivity, it))
								}
							}

							DetailsAction.CreateShortcut -> {
								viewModel.getContentOrNull()?.let { manga ->
									lifecycleScope.launch {
										if (!appShortcutManager.requestPinShortcut(manga)) {
											Snackbar.make(
												viewBinding.root,
												R.string.operation_not_supported,
												Snackbar.LENGTH_SHORT,
											).show()
										}
									}
								}
							}

							DetailsAction.ToggleList -> {
								showDetailsBottomSheetTab(org.skepsun.kototoro.details.ui.pager.ChaptersPagesSheet.TAB_CHAPTERS)
							}

							DetailsAction.ToggleGrid -> {
								showDetailsBottomSheetTab(org.skepsun.kototoro.details.ui.pager.ChaptersPagesSheet.TAB_PAGES)
							}

							DetailsAction.ToggleBookmarkView -> {
								showDetailsBottomSheetTab(org.skepsun.kototoro.details.ui.pager.ChaptersPagesSheet.TAB_BOOKMARKS)
							}
						}
					}
				)
			}
		}
        
		viewBinding.imageViewCover.addImageRequestListener(object : coil3.request.ImageRequest.Listener {
			override fun onSuccess(request: coil3.request.ImageRequest, result: coil3.request.SuccessResult) {
				supportStartPostponedEnterTransition()
				scheduleTransitionCoverFadeOut()
			}
			override fun onError(request: coil3.request.ImageRequest, result: coil3.request.ErrorResult) {
				supportStartPostponedEnterTransition()
				scheduleTransitionCoverFadeOut()
			}
		})

		lifecycleScope.launch {
			viewModel.coverUrl.collect { url ->
				viewBinding.imageViewCover.setImageAsync(url, viewModel.getContentOrNull())
			}
		}
		viewBinding.imageViewCover.setOnClickListener {
			viewModel.getContentOrNull()?.let { content ->
				content.coverUrl?.let { url ->
					router.openImage(
						url = url,
						source = content.source,
						anchor = viewBinding.imageViewCover,
					)
				}
			}
		}
		viewModel.onContentRemoved.observeEvent(this, ::onContentRemoved)
		lifecycleScope.launch {
			viewModel.chapters.collect(PrefetchObserver(this@DetailsActivity))
		}
	}

	private fun openReader(isIncognitoMode: Boolean = false) {
		if (viewModel.historyInfo.value.isChapterMissing) {
			Snackbar.make(viewBinding.root, R.string.chapter_is_missing, Snackbar.LENGTH_SHORT).show()
			return
		}
		val content = viewModel.getContentOrNull() ?: return
		val intent = org.skepsun.kototoro.core.nav.ReaderIntent.Builder(this)
			.manga(content)
			.branch(viewModel.selectedBranchValue)
			.apply {
				if (isIncognitoMode) {
					incognito()
				}
			}
			.build()
		this.router.openReader(intent)
	}

	private fun confirmDeleteLocal() {
		val manga = viewModel.getContentOrNull() ?: return
		buildAlertDialog(this) {
			setTitle(R.string.delete_manga)
			setMessage(getString(R.string.text_delete_local_manga, manga.title))
			setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteLocal() }
			setNegativeButton(android.R.string.cancel, null)
		}.show()
	}

	private fun showDetailsBottomSheetTab(targetTab: Int) {
		viewBinding.containerBottomSheet?.let { sheet ->
			supportFragmentManager.executePendingTransactions()
			BottomSheetBehavior.from(sheet).state = BottomSheetBehavior.STATE_EXPANDED
			val fragment = supportFragmentManager.findFragmentById(R.id.container_bottom_sheet)
				as? org.skepsun.kototoro.details.ui.pager.ChaptersPagesSheet
			fragment?.selectTab(resolveDetailsBottomSheetTabIndex(targetTab))
			return
		}
		this.router.showChapterPagesSheet(targetTab)
	}

	private fun resolveDetailsBottomSheetTabIndex(targetTab: Int): Int {
		val contentType = viewModel.getContentOrNull()?.source?.getContentType()
		val isNovel = contentType == ContentType.NOVEL || contentType == ContentType.HENTAI_NOVEL
		val isVideo = contentType == ContentType.VIDEO || contentType == ContentType.HENTAI_VIDEO
		val isPagesTabEnabled = settings.isPagesTabEnabled && !isNovel && !isVideo
		val isBookmarksTabEnabled = !isVideo
		return when (targetTab) {
			org.skepsun.kototoro.details.ui.pager.ChaptersPagesSheet.TAB_CHAPTERS -> 0
			org.skepsun.kototoro.details.ui.pager.ChaptersPagesSheet.TAB_PAGES -> {
				if (isPagesTabEnabled) 1 else 0
			}

			org.skepsun.kototoro.details.ui.pager.ChaptersPagesSheet.TAB_BOOKMARKS -> when {
				isPagesTabEnabled && isBookmarksTabEnabled -> 2
				isBookmarksTabEnabled -> 1
				else -> 0
			}

			else -> 0
		}
	}

	private fun syncCoverBounds(rect: Rect, alpha: Float) {
		if (rect.width > 0 && rect.height > 0) {
			viewBinding.imageViewCover.updateLayoutParams<ViewGroup.MarginLayoutParams> {
				width = rect.width.toInt()
				height = rect.height.toInt()
				topMargin = rect.top.toInt()
				leftMargin = rect.left.toInt()
			}
		}
		viewBinding.imageViewCover.alpha = if (shouldRenderTransitionCover) {
			alpha.coerceIn(0f, 1f)
		} else {
			0f
		}
		viewBinding.imageViewCover.visibility = View.VISIBLE
	}

	private fun scheduleTransitionCoverFadeOut() {
		if (!shouldRenderTransitionCover) {
			return
		}
		val delayMs = if (settings.isSharedElementTransitionsEnabled) 380L else 0L
		viewBinding.imageViewCover.postDelayed({
			shouldRenderTransitionCover = false
			viewBinding.imageViewCover.alpha = 0f
		}, delayMs)
	}

	override fun onProvideAssistContent(outContent: AssistContent) {
		super.onProvideAssistContent(outContent)
		viewModel.getContentOrNull()?.publicUrl?.toUriOrNull()?.let { outContent.webUri = it }
	}

	override fun isNsfwContent(): Flow<Boolean> = viewModel.manga.map { it?.contentRating == ContentRating.ADULT }


	private fun onContentRemoved(manga: Content) {
		Toast.makeText(
			this,
			getString(R.string._s_deleted_from_local_storage, manga.title),
			Toast.LENGTH_SHORT,
		).show()
		finishAfterTransition()
	}

	private class PrefetchObserver(
		private val context: Context,
	) : FlowCollector<List<ChapterListItem>?> {

		private var isCalled = false

		override suspend fun emit(value: List<ChapterListItem>?) {
			if (value.isNullOrEmpty()) {
				return
			}
			if (!isCalled) {
				isCalled = true
				val item = value.find { it.isCurrent } ?: value.first()
				ContentPrefetchService.prefetchPages(context, item.chapter)
			}
		}
	}

	override fun onApplyWindowInsets(v: View, insets: androidx.core.view.WindowInsetsCompat): androidx.core.view.WindowInsetsCompat {
		return insets
	}
}
