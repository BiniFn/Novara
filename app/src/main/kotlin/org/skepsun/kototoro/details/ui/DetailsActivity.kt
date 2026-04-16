package org.skepsun.kototoro.details.ui

import android.app.assist.AssistContent
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.map
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseActivity
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
import javax.inject.Inject
import androidx.compose.ui.geometry.Rect
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.skepsun.kototoro.core.ui.sheet.BottomSheetCollapseCallback

@AndroidEntryPoint
class DetailsActivity :
	BaseActivity<ActivityDetailsBinding>(),
	BottomSheetOwner {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var pageSaveHelperFactory: org.skepsun.kototoro.reader.ui.PageSaveHelper.Factory

	private val viewModel: DetailsViewModel by viewModels()
	private val pagesViewModel: PagesViewModel by viewModels()
	private val bookmarksViewModel: BookmarksViewModel by viewModels()

	private lateinit var pageSaveHelper: org.skepsun.kototoro.reader.ui.PageSaveHelper

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
					onCoverBoundsSync = { rect ->
						syncCoverBounds(rect)
					},
					onActionClick = { action ->
                        when (action) {
                            is org.skepsun.kototoro.details.ui.compose.DetailsAction.Resume -> {
                                val content = viewModel.mangaDetails.value?.toContent()
                                if (content != null) {
                                    val intent = org.skepsun.kototoro.core.nav.ReaderIntent.Builder(this@DetailsActivity)
                                        .manga(content)
                                        .build()
                                    this@DetailsActivity.router.openReader(intent)
                                }
                            }
                            is org.skepsun.kototoro.details.ui.compose.DetailsAction.Download -> {
                                // TODO: Map to actual download logic
                            }
                            is org.skepsun.kototoro.details.ui.compose.DetailsAction.Share -> {
                                // TODO: Map to actual share logic
                            }
                            is org.skepsun.kototoro.details.ui.compose.DetailsAction.OpenSource -> {
                                // TODO: Handle source routing
                            }
                            is org.skepsun.kototoro.details.ui.compose.DetailsAction.ToggleList -> {
                                viewBinding.containerBottomSheet?.let { sheet ->
                                    val behavior = BottomSheetBehavior.from(sheet)
                                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                                    val fragment = supportFragmentManager.findFragmentById(R.id.container_bottom_sheet) as? org.skepsun.kototoro.details.ui.pager.ChaptersPagesSheet
                                    fragment?.selectTab(org.skepsun.kototoro.details.ui.pager.ChaptersPagesSheet.TAB_CHAPTERS)
                                } ?: run {
                                    this@DetailsActivity.router.showChapterPagesSheet(org.skepsun.kototoro.details.ui.pager.ChaptersPagesSheet.TAB_CHAPTERS)
                                }
                            }
                            is org.skepsun.kototoro.details.ui.compose.DetailsAction.ToggleGrid -> {
                                viewBinding.containerBottomSheet?.let { sheet ->
                                    val behavior = BottomSheetBehavior.from(sheet)
                                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                                    val fragment = supportFragmentManager.findFragmentById(R.id.container_bottom_sheet) as? org.skepsun.kototoro.details.ui.pager.ChaptersPagesSheet
                                    fragment?.selectTab(org.skepsun.kototoro.details.ui.pager.ChaptersPagesSheet.TAB_PAGES)
                                } ?: run {
                                    this@DetailsActivity.router.showChapterPagesSheet(org.skepsun.kototoro.details.ui.pager.ChaptersPagesSheet.TAB_PAGES)
                                }
                            }
                            is org.skepsun.kototoro.details.ui.compose.DetailsAction.ToggleBookmarkView -> {
                                this@DetailsActivity.router.showChapterPagesSheet(org.skepsun.kototoro.details.ui.pager.ChaptersPagesSheet.TAB_BOOKMARKS)
                            }
                            else -> {}
                        }
					}
				)
			}
		}
        
		viewBinding.imageViewCover.addImageRequestListener(object : coil3.request.ImageRequest.Listener {
			override fun onSuccess(request: coil3.request.ImageRequest, result: coil3.request.SuccessResult) {
				supportStartPostponedEnterTransition()
			}
			override fun onError(request: coil3.request.ImageRequest, result: coil3.request.ErrorResult) {
				supportStartPostponedEnterTransition()
			}
		})

		lifecycleScope.launch {
			viewModel.coverUrl.collect { url ->
				viewBinding.imageViewCover.setImageAsync(url, viewModel.getContentOrNull())
			}
		}
		viewModel.onContentRemoved.observeEvent(this, ::onContentRemoved)
		lifecycleScope.launch {
			viewModel.chapters.collect(PrefetchObserver(this@DetailsActivity))
		}
	}

	private fun syncCoverBounds(rect: Rect) {
		if (rect.width > 0 && rect.height > 0) {
			viewBinding.imageViewCover.updateLayoutParams<ViewGroup.MarginLayoutParams> {
				width = rect.width.toInt()
				height = rect.height.toInt()
				topMargin = rect.top.toInt()
				leftMargin = rect.left.toInt()
			}
		}

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
