package org.skepsun.kototoro.details.ui

import android.app.Activity
import android.app.assist.AssistContent
import android.content.Context
import android.graphics.Outline
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.geometry.Rect
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.os.AppShortcutManager
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.BaseActivity
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.toUriOrNull
import org.skepsun.kototoro.databinding.ActivityDetailsBinding
import org.skepsun.kototoro.details.ui.compose.DetailsAction
import org.skepsun.kototoro.details.ui.compose.DetailsScreen
import org.skepsun.kototoro.details.ui.pager.bookmarks.BookmarksViewModel
import org.skepsun.kototoro.details.ui.pager.pages.PagesViewModel
import org.skepsun.kototoro.main.ui.owners.BottomSheetOwner
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentRating
import org.skepsun.kototoro.search.domain.SearchKind
import javax.inject.Inject

@AndroidEntryPoint
class DetailsActivity :
    BaseActivity<ActivityDetailsBinding>(),
    BottomSheetOwner {

    override fun onApplyWindowInsets(v: android.view.View, insets: androidx.core.view.WindowInsetsCompat): androidx.core.view.WindowInsetsCompat {
        return insets
    }

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
        get() = null

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

        if (settings.isSharedElementTransitionsEnabled) {
            val manga = viewModel.getContentOrNull()
            if (manga != null) {
                androidx.core.view.ViewCompat.setTransitionName(viewBinding.imageViewCover, "cover_${manga.source.name}_${manga.url}")
                viewBinding.imageViewCover.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, resources.displayMetrics.density * 22f)
                    }
                }
                viewBinding.imageViewCover.clipToOutline = true
                supportPostponeEnterTransition()
                window.decorView.postDelayed({ supportStartPostponedEnterTransition() }, 1200L)
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
                    appRouter = router,
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

                            DetailsAction.ManageDownloads -> {
                                router.openDownloads()
                            }

                            DetailsAction.Favorite -> {
                                viewModel.getContentOrNull()?.let(this@DetailsActivity.router::showFavoriteDialog)
                            }

                            DetailsAction.Share -> {
                                viewModel.getContentOrNull()?.let(this@DetailsActivity.router::showShareDialog)
                            }

                            DetailsAction.ForgetHistory -> {
                                viewModel.removeFromHistory()
                            }

                            DetailsAction.ManageCategories -> {
                                this@DetailsActivity.router.openFavoriteCategories()
                            }

                            is DetailsAction.OpenSource -> {
                                this@DetailsActivity.router.openList(action.source, null, null)
                            }

                            is DetailsAction.SearchAuthorOnSource -> {
                                this@DetailsActivity.router.openSearch(action.source, action.author)
                            }

                            is DetailsAction.SearchAuthorEverywhere -> {
                                this@DetailsActivity.router.openSearch(action.author, SearchKind.AUTHOR)
                            }

                            is DetailsAction.SearchTagOnSource -> {
                                this@DetailsActivity.router.openSearch(action.tag.source, action.tag.title)
                            }

                            is DetailsAction.SearchTagEverywhere -> {
                                this@DetailsActivity.router.openSearch(action.tagTitle, SearchKind.TAG)
                            }

                            is DetailsAction.SelectBranch -> {
                                viewModel.setSelectedBranch(action.branch)
                            }

                            is DetailsAction.ShareLink -> {
                                router.shareLink(action.link, action.title)
                            }

                            DetailsAction.Translate -> {
                                val hasCache = viewModel.hasTranslationCache.value
                                viewModel.translateTitleAndDescription(forceRefresh = hasCache)
                                com.google.android.material.snackbar.Snackbar.make(
                                    viewBinding.root,
                                    if (hasCache) R.string.reader_translation_retranslate_started else R.string.translating,
                                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
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

                            is DetailsAction.OpenTrackingDetails -> {
                                router.openTrackingSiteDetails(action.service, action.remoteId, action.url)
                            }

                            is DetailsAction.ManageTrackingBinding -> {
                                router.openScrobblerBinding(
                                    scrobbler = action.service,
                                    remoteId = action.remoteId,
                                    title = action.title,
                                    url = action.url,
                                )
                            }

                            is DetailsAction.BindTrackingMatch -> {
                                viewModel.bindTrackingMatch(action.match)
                            }

                            is DetailsAction.RemoveTrackingMatch -> {
                                viewModel.removeTrackingMatch(action.match)
                            }

                            DetailsAction.Download -> {
                            }

                            DetailsAction.OpenTracking,
                            DetailsAction.OpenStatistics,
                            DetailsAction.OpenStatistics -> {
                            }

                            DetailsAction.OpenTracking -> {
                            }

                            DetailsAction.ToggleList,
                            DetailsAction.ToggleGrid,
                            DetailsAction.ToggleBookmarkView -> {
                            }

                            DetailsAction.ToggleSafe -> {
                                viewModel.toggleMarkSafe()
                            }

                            DetailsAction.DeleteLocal -> {
                                viewModel.deleteLocal()
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
                                            com.google.android.material.snackbar.Snackbar.make(
                                                viewBinding.root,
                                                R.string.operation_not_supported,
                                                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                }
                            }
                        }
                    },
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
    }

    private fun openReader(isIncognitoMode: Boolean = false) {
        openDetailsReader(
            context = this,
            viewModel = viewModel,
            router = router,
            isIncognitoMode = isIncognitoMode,
            snackbarHost = viewBinding.root,
        )
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
}
