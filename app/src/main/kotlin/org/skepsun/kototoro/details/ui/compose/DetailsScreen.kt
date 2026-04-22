package org.skepsun.kototoro.details.ui.compose

import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.LocalMangaSource
import org.skepsun.kototoro.core.model.appUrl
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.model.isBroken
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.compose.rememberSafePainter
import org.skepsun.kototoro.core.ui.compose.rememberResolvedSourceTitle
import org.skepsun.kototoro.core.ui.glass.GlassBottomBarContainer
import org.skepsun.kototoro.core.ui.glass.GlassDefaults
import org.skepsun.kototoro.core.ui.glass.GlassSurface
import org.skepsun.kototoro.core.ui.glass.LocalHazeState
import org.skepsun.kototoro.core.util.ext.isHttpUrl
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.details.ui.DetailsViewModel
import org.skepsun.kototoro.details.ui.model.ActiveLocalSourceOption
import org.skepsun.kototoro.details.ui.model.ContentBranch
import org.skepsun.kototoro.details.ui.model.DetailsChapterSourceTab
import org.skepsun.kototoro.details.ui.model.DetailsSourceOption
import org.skepsun.kototoro.details.ui.model.EntityChapterSourceInfo
import org.skepsun.kototoro.details.ui.model.HistoryInfo
import org.skepsun.kototoro.details.ui.model.TrackingDetailsSection
import org.skepsun.kototoro.details.ui.model.TrackingDetailsAction
import org.skepsun.kototoro.details.ui.model.TrackingDetailsSectionItem
import org.skepsun.kototoro.entitygraph.ui.details.EntityRelationSection
import org.skepsun.kototoro.entitygraph.ui.details.EntityRelationItem
import org.skepsun.kototoro.details.ui.pager.bookmarks.BookmarksViewModel
import org.skepsun.kototoro.details.ui.pager.pages.PagesViewModel
import org.skepsun.kototoro.download.ui.dialog.DownloadDialogViewModel
import org.skepsun.kototoro.download.ui.compose.DownloadDialog
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.reader.ui.PageSaveHelper
import org.skepsun.kototoro.favourites.ui.categories.select.compose.FavoriteCategoryDialog
import org.skepsun.kototoro.stats.ui.sheet.compose.ContentStatsDialog
import org.skepsun.kototoro.stats.ui.sheet.ContentStatsViewModel
import org.skepsun.kototoro.scrobbling.common.ui.selector.compose.ScrobblingSelectorDialog
import org.skepsun.kototoro.scrobbling.common.ui.selector.ScrobblingSelectorViewModel
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    pagesViewModel: PagesViewModel,
    bookmarksViewModel: BookmarksViewModel,
    settings: AppSettings,
    appRouter: AppRouter,
    pageSaveHelper: PageSaveHelper,
    onBackClick: () -> Unit,
    onCoverBoundsSync: (Rect, Float) -> Unit,
    isHeroOverlayVisible: Boolean = false,
    onActionClick: (DetailsAction) -> Unit = {},
) {
    val mangaDetails by viewModel.mangaDetails.collectAsState()
    val remoteContent by viewModel.remoteContent.collectAsState()
    val favouriteCategories by viewModel.favouriteCategories.collectAsState()
    val historyInfo by viewModel.historyInfo.collectAsState()
    val branches by viewModel.branches.collectAsState()
    val translatedTitle by viewModel.translatedTitle.collectAsState()
    val translatedDescription by viewModel.translatedDescription.collectAsState()
    val isShowingTranslation by viewModel.isShowingTranslation.collectAsState()
    val hasTranslationCache by viewModel.hasTranslationCache.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()
    val showTranslateAction by viewModel.showTranslateAction.collectAsState()
    val isStatsAvailable by viewModel.isStatsAvailable.collectAsState()
    val isChaptersReversed by viewModel.isChaptersReversed.collectAsState(initial = false)
    val isChaptersInGridView by viewModel.isChaptersInGridView.collectAsState(initial = false)
    val isDownloadedOnly by viewModel.isDownloadedOnly.collectAsState(initial = false)
    val chapterEmptyReason by viewModel.emptyReason.collectAsState(initial = null)
    val trackingSuggestion by viewModel.trackingMatchSuggestion.collectAsState()
    val linkedTrackingItems by viewModel.linkedTrackingItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val entityRelationSections by viewModel.entityRelationSections.collectAsState()
    val activeLocalSourceOptions by viewModel.activeLocalSourceOptions.collectAsState()
    val entityChapterSourceInfo by viewModel.entityChapterSourceInfo.collectAsState()
    val metadataSourceOptions by viewModel.metadataSourceOptions.collectAsState()
    val readingSourceOptions by viewModel.readingSourceOptions.collectAsState()
    val metadataChapterTabs by viewModel.metadataChapterTabs.collectAsState()
    val readingChapterTabs by viewModel.readingChapterTabs.collectAsState()
    val trackingMetadataProperties by viewModel.trackingMetadataProperties.collectAsState()
    val trackingDetailsSections by viewModel.trackingDetailsSections.collectAsState()
    val trackingDetailsActions by viewModel.trackingDetailsActions.collectAsState()
    val trackingCommentThreads by viewModel.trackingCommentThreads.collectAsState()
    val trackingCommentsUrl by viewModel.trackingCommentsUrl.collectAsState()
    val trackingReviews by viewModel.trackingReviews.collectAsState()
    val trackingReviewsUrl by viewModel.trackingReviewsUrl.collectAsState()
    val metadataSearchServices by viewModel.metadataSearchServices.collectAsState()
    val authorizedTrackingServices by viewModel.authorizedTrackingServices.collectAsState()
    val selectedMetadataSearchService by viewModel.selectedMetadataSearchService.collectAsState()
    val metadataSearchQuery by viewModel.metadataSearchQuery.collectAsState()
    val metadataSearchResults by viewModel.metadataSearchResults.collectAsState()
    val metadataSearchLoading by viewModel.metadataSearchLoading.collectAsState()
    val metadataSearchError by viewModel.metadataSearchError.collectAsState()
    val readingSearchSources by viewModel.readingSearchSources.collectAsState()
    val selectedReadingSearchSource by viewModel.selectedReadingSearchSource.collectAsState()
    val readingSearchQuery by viewModel.readingSearchQuery.collectAsState()
    val readingSearchState by viewModel.readingSearchState.collectAsState()
    val resolvedMetadataContentType by viewModel.resolvedMetadataContentType.collectAsState()
    val resolvedMetadataLanguage by viewModel.resolvedMetadataLanguage.collectAsState()
    val resolvedReadingLanguage by viewModel.resolvedReadingLanguage.collectAsState()
    val activeLocalBrowserContent by viewModel.activeLocalBrowserContent.collectAsState()

    val context = LocalContext.current
    val panoramaExtraHeight by settings.observeAsState(AppSettings.KEY_PANORAMA_EXTRA_HEIGHT) { panoramaCoverExtraHeight }
    val downloadDialogViewModel: DownloadDialogViewModel = hiltViewModel()
    val content = mangaDetails?.toContent()
    val contentType = resolvedMetadataContentType
    val metadataBrowserTarget = remember(content) {
        content?.takeIf { it.publicUrl.isHttpUrl() }
    }
    val localBrowserTarget = remember(activeLocalBrowserContent, metadataBrowserTarget) {
        activeLocalBrowserContent?.takeIf { it.publicUrl.isHttpUrl() }?.takeUnless { local ->
            local.publicUrl == metadataBrowserTarget?.publicUrl &&
                local.source == metadataBrowserTarget.source
        }
    }
    val readingSourceLabelRes = remember(contentType) {
        when (contentType) {
            ContentType.VIDEO,
            ContentType.HENTAI_VIDEO -> R.string.details_playback_source
            else -> R.string.details_reading_source
        }
    }
    val isShortcutSupported = remember(context) { ShortcutManagerCompat.isRequestPinShortcutSupported(context) }
    val configuration = LocalConfiguration.current
    val scrollState = rememberScrollState()
    val landscapeLeftScrollState = rememberScrollState()
    var showDeleteLocalDialog by remember { mutableStateOf(false) }
    var showShareOptions by remember { mutableStateOf(false) }
    var pendingAuthorSearch by remember { mutableStateOf<PendingAuthorSearch?>(null) }
    var pendingTagSearch by remember { mutableStateOf<ContentTag?>(null) }
    var showFavoriteDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showScrobblingDialog by remember { mutableStateOf(false) }
    var showCommentsDialog by remember { mutableStateOf(false) }
    var showReviewsDialog by remember { mutableStateOf(false) }
    var showMetadataSourceDialog by remember { mutableStateOf(false) }
    var showReadingSourceDialog by remember { mutableStateOf(false) }
    var chapterQuery by rememberSaveable { mutableStateOf("") }
    var isChapterSearchVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(showMetadataSourceDialog) {
        if (showMetadataSourceDialog && metadataSearchResults.isEmpty() && !metadataSearchLoading) {
            viewModel.searchMetadataBindings()
        }
    }
    LaunchedEffect(showReadingSourceDialog) {
        if (showReadingSourceDialog && readingSearchState == null) {
            viewModel.searchReadingBindings()
        }
    }
    val availableTabIds = remember(contentType, settings.isPagesTabEnabled) {
        resolveAvailableDetailsTabIds(contentType, settings)
    }
    var selectedPaneTabId by rememberSaveable {
        mutableStateOf(resolveDetailsTabSelection(settings.defaultDetailsTab, availableTabIds))
    }
    val sheetTabSelection = remember(selectedPaneTabId, availableTabIds) {
        resolveDetailsTabSelection(selectedPaneTabId, availableTabIds)
    }
    val isWideAdaptiveLayout = remember(configuration.orientation, configuration.screenWidthDp) {
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 720
    }
    val compactPaneCollapsedHeight = 88.dp
    val compactPaneHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp.dp + 32.dp).coerceAtLeast(520.dp)
    }
    val compactPaneHoveredHeight = remember(configuration.screenHeightDp, compactPaneHeight) {
        (configuration.screenHeightDp.dp * 0.52f)
            .coerceAtLeast(360.dp)
            .coerceAtMost(compactPaneHeight - 96.dp)
    }
    val density = LocalDensity.current
    var compactPaneAnchor by rememberSaveable {
        mutableStateOf(CompactDetailsPaneAnchor.Collapsed)
    }
    var compactPaneOffsetPx by remember { mutableFloatStateOf(Float.NaN) }
    val compactPaneOffsetAnimator = remember { Animatable(0f) }
    var compactPaneHostHeightPx by remember { mutableFloatStateOf(0f) }
    var isCompactPaneDragging by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val compactPaneHeightPx = with(density) { compactPaneHeight.toPx() }
    val compactPaneCollapsedHeightPx = with(density) { compactPaneCollapsedHeight.toPx() }
    val compactPaneHoveredHeightPx = with(density) { compactPaneHoveredHeight.toPx() }
    val compactPaneFullOffsetPx = remember(compactPaneHostHeightPx, compactPaneHeightPx) {
        compactPaneFullOffsetPx(
            hostHeightPx = compactPaneHostHeightPx,
            paneHeightPx = compactPaneHeightPx,
        )
    }
    val compactPaneCollapsedOffsetPx = remember(compactPaneHostHeightPx, compactPaneCollapsedHeightPx) {
        compactPaneCollapsedOffsetPx(
            hostHeightPx = compactPaneHostHeightPx,
            collapsedHeightPx = compactPaneCollapsedHeightPx,
        )
    }
    val compactPaneHoveredOffsetPx = remember(
        compactPaneHostHeightPx,
        compactPaneHoveredHeightPx,
        compactPaneFullOffsetPx,
        compactPaneCollapsedOffsetPx,
    ) {
        compactPaneHoveredOffsetPx(
            hostHeightPx = compactPaneHostHeightPx,
            hoveredHeightPx = compactPaneHoveredHeightPx,
            fullOffset = compactPaneFullOffsetPx,
            collapsedOffset = compactPaneCollapsedOffsetPx,
        )
    }
    val compactPaneDragState = rememberDraggableState { delta ->
        val fallbackOffset = compactPaneOffsetForAnchor(
            anchor = compactPaneAnchor,
            fullOffset = compactPaneFullOffsetPx,
            hoveredOffset = compactPaneHoveredOffsetPx,
            collapsedOffset = compactPaneCollapsedOffsetPx,
        )
        val fullOffset = compactPaneFullOffsetPx
        val collapsedOffset = compactPaneCollapsedOffsetPx
        val nextOffset = ((compactPaneOffsetPx.takeIf { it.isFinite() } ?: fallbackOffset) + delta)
            .coerceIn(fullOffset, collapsedOffset)
        compactPaneOffsetPx = nextOffset
        coroutineScope.launch {
            compactPaneOffsetAnimator.stop()
            compactPaneOffsetAnimator.snapTo(nextOffset)
        }
        isCompactPaneDragging = true
    }
    val compactPaneDragModifier = Modifier.draggable(
        state = compactPaneDragState,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity ->
            compactPaneAnchor = resolveCompactPaneAnchor(
                currentOffset = compactPaneOffsetPx.takeIf { it.isFinite() } ?: compactPaneCollapsedOffsetPx,
                velocity = velocity,
                fullOffset = compactPaneFullOffsetPx,
                hoveredOffset = compactPaneHoveredOffsetPx,
                collapsedOffset = compactPaneCollapsedOffsetPx,
            )
            isCompactPaneDragging = false
        },
    )
    val paneNestedScrollConnection = remember(compactPaneCollapsedOffsetPx, compactPaneFullOffsetPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val offset = compactPaneOffsetPx.takeIf { it.isFinite() } ?: compactPaneCollapsedOffsetPx
                if (available.y < 0 && offset > compactPaneFullOffsetPx) {
                    val consumedY = available.y.coerceAtLeast(compactPaneFullOffsetPx - offset)
                    val nextOffset = offset + consumedY
                    compactPaneOffsetPx = nextOffset
                    coroutineScope.launch {
                        compactPaneOffsetAnimator.stop()
                        compactPaneOffsetAnimator.snapTo(nextOffset)
                    }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val offset = compactPaneOffsetPx.takeIf { it.isFinite() } ?: compactPaneCollapsedOffsetPx
                if (available.y > 0 && offset < compactPaneCollapsedOffsetPx) {
                    val consumedY = available.y.coerceAtMost(compactPaneCollapsedOffsetPx - offset)
                    val nextOffset = offset + consumedY
                    compactPaneOffsetPx = nextOffset
                    coroutineScope.launch {
                        compactPaneOffsetAnimator.stop()
                        compactPaneOffsetAnimator.snapTo(nextOffset)
                    }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                val offset = compactPaneOffsetPx.takeIf { it.isFinite() } ?: compactPaneCollapsedOffsetPx
                compactPaneAnchor = resolveCompactPaneAnchor(
                    currentOffset = offset,
                    velocity = available.y,
                    fullOffset = compactPaneFullOffsetPx,
                    hoveredOffset = compactPaneHoveredOffsetPx,
                    collapsedOffset = compactPaneCollapsedOffsetPx,
                )
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val toolbarGapPx = with(density) { 12.dp.toPx() }
    var toolbarBottomPx by remember { mutableFloatStateOf(Float.NaN) }
    var lastToolbarBottomPx by remember { mutableFloatStateOf(Float.NaN) }
    var infoCardTopPx by remember { mutableFloatStateOf(Float.NaN) }
    var initialInfoCardTopPx by remember { mutableFloatStateOf(Float.NaN) }

    LaunchedEffect(availableTabIds) {
        val resolvedDefaultTab = resolveDetailsTabSelection(settings.defaultDetailsTab, availableTabIds)
        selectedPaneTabId = resolveDetailsTabSelection(selectedPaneTabId, availableTabIds)
        if (settings.defaultDetailsTab != resolvedDefaultTab) {
            settings.defaultDetailsTab = resolvedDefaultTab
        }
    }

    LaunchedEffect(infoCardTopPx) {
        if (infoCardTopPx.isFinite() && (!initialInfoCardTopPx.isFinite() || infoCardTopPx > initialInfoCardTopPx)) {
            initialInfoCardTopPx = infoCardTopPx
        }
    }
    LaunchedEffect(isWideAdaptiveLayout) {
        if (isWideAdaptiveLayout) {
            landscapeLeftScrollState.scrollTo(0)
        }
    }
    LaunchedEffect(
        isWideAdaptiveLayout,
        compactPaneAnchor,
        compactPaneHostHeightPx,
        compactPaneFullOffsetPx,
        compactPaneHoveredOffsetPx,
        compactPaneCollapsedOffsetPx,
        isCompactPaneDragging,
    ) {
        if (isWideAdaptiveLayout || compactPaneHostHeightPx <= 0f || isCompactPaneDragging) {
            return@LaunchedEffect
        }
        val targetOffset = compactPaneOffsetForAnchor(
            anchor = compactPaneAnchor,
            fullOffset = compactPaneFullOffsetPx,
            hoveredOffset = compactPaneHoveredOffsetPx,
            collapsedOffset = compactPaneCollapsedOffsetPx,
        )
        if (!compactPaneOffsetPx.isFinite()) {
            compactPaneOffsetPx = targetOffset
            compactPaneOffsetAnimator.snapTo(targetOffset)
            return@LaunchedEffect
        }
        compactPaneOffsetAnimator.stop()
        compactPaneOffsetAnimator.animateTo(
            targetValue = targetOffset,
            animationSpec = tween(
                durationMillis = compactPaneAnimationDurationMillis(compactPaneAnchor),
                easing = FastOutSlowInEasing,
            ),
        ) {
            compactPaneOffsetPx = value
        }
    }
    val collapseProgress by remember(
        scrollState,
        landscapeLeftScrollState,
        toolbarBottomPx,
        infoCardTopPx,
        initialInfoCardTopPx,
        toolbarGapPx,
        isWideAdaptiveLayout,
    ) {
        derivedStateOf {
            if (isWideAdaptiveLayout) {
                return@derivedStateOf 0f
            }
            val targetTop = toolbarBottomPx + toolbarGapPx
            if (toolbarBottomPx.isFinite() && infoCardTopPx.isFinite() && initialInfoCardTopPx.isFinite()) {
                val travelDistance = (initialInfoCardTopPx - targetTop).coerceAtLeast(1f)
                ((initialInfoCardTopPx - infoCardTopPx) / travelDistance).coerceIn(0f, 1f)
            } else {
                (scrollState.value / 360f).coerceIn(0f, 1f)
            }
        }
    }
    val toolbarTitleProgress by remember(
        scrollState,
        landscapeLeftScrollState,
        toolbarBottomPx,
        infoCardTopPx,
        initialInfoCardTopPx,
        toolbarGapPx,
        isWideAdaptiveLayout,
    ) {
        derivedStateOf {
            val targetTop = toolbarBottomPx + toolbarGapPx
            if (toolbarBottomPx.isFinite() && infoCardTopPx.isFinite() && initialInfoCardTopPx.isFinite()) {
                val travelDistance = (initialInfoCardTopPx - targetTop).coerceAtLeast(1f)
                ((initialInfoCardTopPx - infoCardTopPx) / travelDistance).coerceIn(0f, 1f)
            } else {
                val fallbackScroll = if (isWideAdaptiveLayout) {
                    landscapeLeftScrollState.value
                } else {
                    scrollState.value
                }
                (fallbackScroll / 360f).coerceIn(0f, 1f)
            }
        }
    }
    val compactSheetExpansionProgress by remember(
        compactPaneOffsetPx,
        compactPaneFullOffsetPx,
        compactPaneCollapsedOffsetPx,
    ) {
        derivedStateOf {
            val currentOffset = compactPaneOffsetPx
                .takeIf { it.isFinite() }
                ?: compactPaneOffsetForAnchor(
                    anchor = compactPaneAnchor,
                    fullOffset = compactPaneFullOffsetPx,
                    hoveredOffset = compactPaneHoveredOffsetPx,
                    collapsedOffset = compactPaneCollapsedOffsetPx,
                )
            val travelDistance = (compactPaneCollapsedOffsetPx - compactPaneFullOffsetPx).coerceAtLeast(1f)
            ((compactPaneCollapsedOffsetPx - currentOffset) / travelDistance).coerceIn(0f, 1f)
        }
    }
    val toolbarTitle = translatedTitle ?: content?.title.orEmpty()
    val isCompactPaneFullyExpanded = !isWideAdaptiveLayout && compactPaneAnchor == CompactDetailsPaneAnchor.Full
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val overlayTopBarInset = remember(isWideAdaptiveLayout, toolbarBottomPx, lastToolbarBottomPx, density, statusBarTopPadding) {
        if (isWideAdaptiveLayout) {
            0.dp
        } else {
            with(density) {
                when {
                    toolbarBottomPx.isFinite() && toolbarBottomPx > 0f -> toolbarBottomPx.toDp()
                    lastToolbarBottomPx.isFinite() && lastToolbarBottomPx > 0f -> lastToolbarBottomPx.toDp()
                    else -> statusBarTopPadding + 64.dp
                }
            }
        }
    }
    val panoramaExtraHeightDp = ((panoramaExtraHeight ?: 0).coerceAtLeast(0)).dp
    val detailsHeaderTopSpacing = overlayTopBarInset + if (settings.isPanoramaCoverEnabled) panoramaExtraHeightDp else 0.dp
    val compactTopBarAlpha by animateFloatAsState(
        targetValue = if (isWideAdaptiveLayout) 1f else (1f - compactSheetExpansionProgress).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 180),
        label = "details_compact_top_bar_alpha",
    )
    val animatedHeaderCoverVisualAlpha by animateFloatAsState(
        targetValue = if (isWideAdaptiveLayout) {
            1f
        } else {
            (1f - compactSheetExpansionProgress).coerceIn(0f, 1f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "details_header_cover_visual_alpha",
    )
    val headerCoverVisualAlpha = if (isHeroOverlayVisible) {
        0f
    } else {
        animatedHeaderCoverVisualAlpha
    }

    val handleBackPress = remember(isWideAdaptiveLayout, compactPaneAnchor, onBackClick) {
        {
            if (isWideAdaptiveLayout) {
                onBackClick()
            } else {
                when (compactPaneAnchor) {
                    CompactDetailsPaneAnchor.Full -> compactPaneAnchor = CompactDetailsPaneAnchor.Hovered
                    CompactDetailsPaneAnchor.Hovered -> compactPaneAnchor = CompactDetailsPaneAnchor.Collapsed
                    CompactDetailsPaneAnchor.Collapsed -> onBackClick()
                }
            }
        }
    }

    BackHandler(enabled = !isWideAdaptiveLayout && compactPaneAnchor != CompactDetailsPaneAnchor.Collapsed) {
        handleBackPress()
    }

    LaunchedEffect(sheetTabSelection, isCompactPaneFullyExpanded) {
        if (sheetTabSelection != DETAILS_TAB_CHAPTERS || !isCompactPaneFullyExpanded) {
            if (chapterQuery.isNotEmpty()) {
                viewModel.performChapterSearch(null)
                chapterQuery = ""
            }
            isChapterSearchVisible = false
        }
    }

    val updateChapterQuery: (String) -> Unit = remember(viewModel) {
        { query ->
            chapterQuery = query
            viewModel.performChapterSearch(query.ifBlank { null })
        }
    }

    val openPaneTab: (Int) -> Unit = remember(
        availableTabIds,
        compactPaneAnchor,
        isWideAdaptiveLayout,
        settings,
    ) {
        { requestedTabId ->
            val resolvedTab = resolveDetailsTabSelection(requestedTabId, availableTabIds)
            selectedPaneTabId = resolvedTab
            settings.lastDetailsTab = resolvedTab
            if (!isWideAdaptiveLayout) {
                compactPaneAnchor = if (compactPaneAnchor == CompactDetailsPaneAnchor.Full) {
                    CompactDetailsPaneAnchor.Full
                } else {
                    CompactDetailsPaneAnchor.Hovered
                }
            }
        }
    }
    val handleActionClick: (DetailsAction) -> Unit = { action ->
        when (action) {
            DetailsAction.ToggleList -> {
                openPaneTab(DETAILS_TAB_CHAPTERS)
            }
            DetailsAction.ToggleGrid -> {
                openPaneTab(DETAILS_TAB_PAGES)
            }
            DetailsAction.ToggleBookmarkView -> {
                openPaneTab(DETAILS_TAB_BOOKMARKS)
            }
            DetailsAction.Download -> {
                showDownloadDialog = true
            }
            DetailsAction.OpenAlternatives -> {
                showReadingSourceDialog = true
            }
            else -> onActionClick(action)
        }
    }

    val detailsHazeState = remember { HazeState() }
    val useBackgroundHaze = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }

    CompositionLocalProvider(LocalHazeState provides detailsHazeState) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (useBackgroundHaze) Modifier.haze(detailsHazeState) else Modifier),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface),
                )
                if (settings.isPanoramaCoverEnabled) {
                    val panoramaCoverUrl = content?.coverUrl?.takeIf { it.isNotBlank() }
                    val request = remember(content?.source?.name, content?.url, panoramaCoverUrl) {
                        ImageRequest.Builder(context)
                            .data(panoramaCoverUrl)
                            .apply { content?.let { mangaExtra(it) } }
                            .build()
                    }
                    AnimatedPanoramaBackdrop(
                        settings = settings,
                        model = request,
                        contentAlpha = 0.6f * (1f - collapseProgress),
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            val commonTopBar: @Composable () -> Unit = {
            TopAppBar(
                title = {
                    if (toolbarTitleProgress > 0.92f) {
                        Text(
                            text = toolbarTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                    navigationIcon = {
                    DetailsChromeButton(onClick = handleBackPress) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    DetailsChromeButton(
                        onClick = {
                            showShareOptions = true
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.share),
                        )
                    }
                    DetailsChromeButton(onClick = {
                        handleActionClick(DetailsAction.Download)
                        showDownloadDialog = true
                    }) {
                        Icon(
                            painter = rememberSafePainter(R.drawable.ic_download),
                            contentDescription = stringResource(R.string.download),
                        )
                    }
                    DetailsOverflowMenu(
                        contentTitle = content?.title,
                        showTranslateAction = showTranslateAction,
                        hasTranslationCache = hasTranslationCache,
                        isShowingTranslation = isShowingTranslation,
                        isTranslating = isTranslating,
                        isScrobblingAvailable = viewModel.isScrobblingAvailable,
                        isStatsAvailable = isStatsAvailable,
                        hasMetadataBrowserTarget = metadataBrowserTarget != null,
                        hasLocalBrowserTarget = localBrowserTarget != null,
                        isAlternativesAvailable = content?.isLocal == false,
                        hasOnlineVariant = remoteContent != null,
                        isDeleteLocalAvailable = content?.source == LocalMangaSource,
                        isEditOverrideAvailable = content != null,
                        isShortcutSupported = isShortcutSupported && content != null,
                        isNsfw = content?.isNsfw() == true,
                        onDeleteLocalRequest = { handleActionClick(DetailsAction.DeleteLocal) },
                        onActionClick = { action ->
                            when (action) {
                                is DetailsAction.OpenMetadataInBrowser -> {
                                    metadataBrowserTarget?.let {
                                        handleActionClick(DetailsAction.OpenBrowserPage(it.publicUrl, it.source, it.title))
                                    }
                                }
                                is DetailsAction.OpenLocalSourceInBrowser -> {
                                    localBrowserTarget?.let {
                                        handleActionClick(DetailsAction.OpenBrowserPage(it.publicUrl, it.source, it.title))
                                    }
                                }
                                DetailsAction.OpenTracking -> {
                                    showScrobblingDialog = true
                                }
                                DetailsAction.OpenStatistics -> {
                                    showStatsDialog = true
                                }
                                else -> handleActionClick(action)
                            }
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f * collapseProgress),
                ),
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    val bottom = coordinates.boundsInRoot().bottom
                    toolbarBottomPx = bottom
                    if (bottom.isFinite() && bottom > 0f) {
                        lastToolbarBottomPx = bottom
                    }
                },
            )
            }

            if (isWideAdaptiveLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Scaffold(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = commonTopBar,
                ) { paddingValues ->
                    PullToRefreshBox(
                        isRefreshing = isLoading,
                        onRefresh = { viewModel.reload() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        DetailsScrollableContent(
                            modifier = Modifier.fillMaxSize(),
                            scrollState = landscapeLeftScrollState,
                            contentPadding = paddingValues,
                            headerTopSpacing = if (settings.isPanoramaCoverEnabled) panoramaExtraHeightDp else 0.dp,
                            bottomSpacerHeight = 40.dp,
                            mangaDetails = mangaDetails,
                            favouriteCategories = favouriteCategories,
                            historyInfo = historyInfo,
                            linkedTrackingItems = linkedTrackingItems,
                            trackingSuggestion = trackingSuggestion,
                            metadataSourceOptions = metadataSourceOptions,
                            readingSourceOptions = readingSourceOptions,
                            activeLocalSourceOptions = activeLocalSourceOptions,
                            entityChapterSourceInfo = entityChapterSourceInfo,
                            trackingMetadataProperties = trackingMetadataProperties,
                            trackingDetailsSections = trackingDetailsSections,
                            trackingDetailsActions = trackingDetailsActions,
                            resolvedContentType = contentType,
                            resolvedMetadataLanguage = resolvedMetadataLanguage,
                            resolvedReadingLanguage = resolvedReadingLanguage,
                            entityRelationSections = entityRelationSections,
                            translatedTitle = translatedTitle,
                            translatedDescription = translatedDescription,
                            isShowingTranslation = isShowingTranslation,
                            hasTranslationCache = hasTranslationCache,
                            isTranslating = isTranslating,
                            showTranslateAction = showTranslateAction,
                            settings = settings,
                            collapseProgress = 0f,
                            coverVisualAlpha = if (isHeroOverlayVisible) 0f else 1f,
                            coverUrl = mangaDetails?.coverUrl?.takeIf { it.isNotBlank() } ?: content?.coverUrl,
                            fallbackCoverUrl = content?.coverUrl,
                            showCommentsAction = trackingCommentThreads.isNotEmpty() || !trackingCommentsUrl.isNullOrBlank(),
                            showReviewsAction = trackingReviews.isNotEmpty() || !trackingReviewsUrl.isNullOrBlank(),
                            content = content,
                            pendingTagSearch = { pendingTagSearch = it },
                            pendingAuthorSearch = { author, source ->
                                pendingAuthorSearch = PendingAuthorSearch(author = author, source = source)
                            },
							onCoverBoundsSync = onCoverBoundsSync,
							onInfoCardTopSync = { infoCardTopPx = it },
							onFavoriteClick = { showFavoriteDialog = true },
							onCommentsClick = { showCommentsDialog = true },
							onReviewsClick = { showReviewsDialog = true },
							onSelectActiveLocalSource = viewModel::selectActiveLocalSource,
                            onSelectMetadataSource = viewModel::selectMetadataSource,
							onOpenMetadataSourceSheet = { showMetadataSourceDialog = true },
							onOpenReadingSourceSheet = { showReadingSourceDialog = true },
							onEntityClick = appRouter::openEntityDetails,
							onActionClick = handleActionClick,
						)
                    }

                }
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(min = 360.dp, max = 440.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 4.dp,
                ) {
                    DetailsPaneContent(
                        contentType = contentType,
                        historyInfo = historyInfo,
                        branches = branches,
                        isLoading = isLoading,
                        viewModel = viewModel,
                        pagesViewModel = pagesViewModel,
                        bookmarksViewModel = bookmarksViewModel,
                        settings = settings,
                        appRouter = appRouter,
                        pageSaveHelper = pageSaveHelper,
                        metadataChapterTabs = metadataChapterTabs,
                        readingChapterTabs = readingChapterTabs,
                        onSelectMetadataChapterTab = { tab ->
                            val matchingOption = metadataSourceOptions.firstOrNull { option -> option.key == tab.key } ?: return@DetailsPaneContent
                            viewModel.selectMetadataSource(matchingOption)
                        },
                        onSelectReadingChapterTab = { tab ->
                            tab.targetMangaId?.let(viewModel::selectActiveLocalSource)
                        },
                        selectedTabId = sheetTabSelection,
                        availableTabIds = availableTabIds,
                        isSheetFullyExpanded = false,
                        sheetExpansionProgress = 0f,
                        chapterQuery = chapterQuery,
                        isChapterSearchVisible = isChapterSearchVisible,
                        isChapterSearchAvailable = chapterEmptyReason == null,
                        isChaptersReversed = isChaptersReversed,
                        isChaptersInGridView = isChaptersInGridView,
                        isDownloadedOnly = isDownloadedOnly,
                        isDownloadedFilterVisible = mangaDetails?.local != null,
                        onChapterQueryChange = updateChapterQuery,
                        onChapterSearchToggle = {
                            val nextVisible = !isChapterSearchVisible
                            isChapterSearchVisible = nextVisible
                            if (!nextVisible && chapterQuery.isNotEmpty()) {
                                updateChapterQuery("")
                            }
                        },
                        onToggleChaptersReversed = { viewModel.setChaptersReversed(!isChaptersReversed) },
                        onToggleChaptersGrid = { viewModel.setChaptersInGridView(!isChaptersInGridView) },
                        onToggleDownloadedOnly = { viewModel.isDownloadedOnly.value = !isDownloadedOnly },
                        showCollapsedHandle = false,
                        onSelectedTabIdChange = { resolvedTab ->
                            selectedPaneTabId = resolvedTab
                            settings.lastDetailsTab = resolvedTab
                        },
                        onActionClick = handleActionClick,
                    )
                }
            }
            } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        compactPaneHostHeightPx = size.height.toFloat()
                    },
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { paddingValues ->
                    PullToRefreshBox(
                        isRefreshing = isLoading,
                        onRefresh = { viewModel.reload() },
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                val offset = compactPaneOffsetPx
                                if (offset.isFinite() && offset >= 0f) {
                                    clipRect(bottom = offset) {
                                        this@drawWithContent.drawContent()
                                    }
                                } else {
                                    this@drawWithContent.drawContent()
                                }
                            },
                    ) {
                        DetailsScrollableContent(
                            modifier = Modifier.fillMaxSize(),
                            scrollState = scrollState,
                            contentPadding = paddingValues,
                            headerTopSpacing = detailsHeaderTopSpacing,
                            bottomSpacerHeight = compactPaneCollapsedHeight + 28.dp,
                            mangaDetails = mangaDetails,
                            favouriteCategories = favouriteCategories,
                            historyInfo = historyInfo,
                            linkedTrackingItems = linkedTrackingItems,
                            trackingSuggestion = trackingSuggestion,
                            metadataSourceOptions = metadataSourceOptions,
                            readingSourceOptions = readingSourceOptions,
                            activeLocalSourceOptions = activeLocalSourceOptions,
                            entityChapterSourceInfo = entityChapterSourceInfo,
                            trackingMetadataProperties = trackingMetadataProperties,
                            trackingDetailsSections = trackingDetailsSections,
                            trackingDetailsActions = trackingDetailsActions,
                            resolvedContentType = contentType,
                            resolvedMetadataLanguage = resolvedMetadataLanguage,
                            resolvedReadingLanguage = resolvedReadingLanguage,
                            entityRelationSections = entityRelationSections,
                            translatedTitle = translatedTitle,
                            translatedDescription = translatedDescription,
                            isShowingTranslation = isShowingTranslation,
                            hasTranslationCache = hasTranslationCache,
                            isTranslating = isTranslating,
                            showTranslateAction = showTranslateAction,
                            settings = settings,
                            collapseProgress = collapseProgress,
                            coverVisualAlpha = headerCoverVisualAlpha,
                            coverUrl = mangaDetails?.coverUrl?.takeIf { it.isNotBlank() } ?: content?.coverUrl,
                            fallbackCoverUrl = content?.coverUrl,
                            showCommentsAction = trackingCommentThreads.isNotEmpty() || !trackingCommentsUrl.isNullOrBlank(),
                            showReviewsAction = trackingReviews.isNotEmpty() || !trackingReviewsUrl.isNullOrBlank(),
                            content = content,
                            pendingTagSearch = { pendingTagSearch = it },
                            pendingAuthorSearch = { author, source ->
                                pendingAuthorSearch = PendingAuthorSearch(author = author, source = source)
                            },
							onCoverBoundsSync = onCoverBoundsSync,
							onInfoCardTopSync = { top -> infoCardTopPx = top },
							onFavoriteClick = { showFavoriteDialog = true },
							onCommentsClick = { showCommentsDialog = true },
							onReviewsClick = { showReviewsDialog = true },
							onSelectActiveLocalSource = viewModel::selectActiveLocalSource,
                            onSelectMetadataSource = viewModel::selectMetadataSource,
							onOpenMetadataSourceSheet = { showMetadataSourceDialog = true },
							onOpenReadingSourceSheet = { showReadingSourceDialog = true },
							onEntityClick = appRouter::openEntityDetails,
							onActionClick = handleActionClick,
						)
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .offset {
                            IntOffset(
                                x = 0,
                                y = compactPaneOffsetPx
                                    .takeIf { it.isFinite() }
                                    ?.roundToInt()
                                    ?: compactPaneCollapsedOffsetPx.roundToInt(),
                            )
                        }
                        .then(compactPaneDragModifier)
                        .nestedScroll(paneNestedScrollConnection),
                ) {
                    DetailsPaneContent(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(compactPaneHeight),
                        contentType = contentType,
                        historyInfo = historyInfo,
                        branches = branches,
                        isLoading = isLoading,
                        viewModel = viewModel,
                        pagesViewModel = pagesViewModel,
                        bookmarksViewModel = bookmarksViewModel,
                        settings = settings,
                        appRouter = appRouter,
                        pageSaveHelper = pageSaveHelper,
                        metadataChapterTabs = metadataChapterTabs,
                        readingChapterTabs = readingChapterTabs,
                        onSelectMetadataChapterTab = { tab ->
                            val matchingOption = metadataSourceOptions.firstOrNull { option -> option.key == tab.key } ?: return@DetailsPaneContent
                            viewModel.selectMetadataSource(matchingOption)
                        },
                        onSelectReadingChapterTab = { tab ->
                            tab.targetMangaId?.let(viewModel::selectActiveLocalSource)
                        },
                        selectedTabId = sheetTabSelection,
                        availableTabIds = availableTabIds,
                        isSheetFullyExpanded = isCompactPaneFullyExpanded,
                        sheetExpansionProgress = compactSheetExpansionProgress,
                        chapterQuery = chapterQuery,
                        isChapterSearchVisible = isChapterSearchVisible,
                        isChapterSearchAvailable = chapterEmptyReason == null,
                        isChaptersReversed = isChaptersReversed,
                        isChaptersInGridView = isChaptersInGridView,
                        isDownloadedOnly = isDownloadedOnly,
                        isDownloadedFilterVisible = mangaDetails?.local != null,
                        onChapterQueryChange = updateChapterQuery,
                        onChapterSearchToggle = {
                            val nextVisible = !isChapterSearchVisible
                            isChapterSearchVisible = nextVisible
                            if (!nextVisible && chapterQuery.isNotEmpty()) {
                                updateChapterQuery("")
                            }
                        },
                        onToggleChaptersReversed = { viewModel.setChaptersReversed(!isChaptersReversed) },
                        onToggleChaptersGrid = { viewModel.setChaptersInGridView(!isChaptersInGridView) },
                        onToggleDownloadedOnly = { viewModel.isDownloadedOnly.value = !isDownloadedOnly },
                        showCollapsedHandle = true,
                        onSelectedTabIdChange = { resolvedTab ->
                            selectedPaneTabId = resolvedTab
                            settings.lastDetailsTab = resolvedTab
                        },
                        onActionClick = handleActionClick,
                        paneDragModifier = compactPaneDragModifier,
                    )
                }
            }
                if (compactTopBarAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .alpha(compactTopBarAlpha),
                ) {
                    commonTopBar()
                }
                }
            }

            pendingAuthorSearch?.let { pending ->
            SearchTargetDialog(
                iconRes = R.drawable.ic_user,
                title = pending.author,
                sourceTitle = rememberResolvedSourceTitle(pending.source),
                onDismissRequest = { pendingAuthorSearch = null },
                onSearchOnSource = {
                    pendingAuthorSearch = null
                    handleActionClick(DetailsAction.SearchAuthorOnSource(pending.author, pending.source))
                },
                onSearchEverywhere = {
                    pendingAuthorSearch = null
                    handleActionClick(DetailsAction.SearchAuthorEverywhere(pending.author))
                },
            )
            }

            pendingTagSearch?.let { tag ->
            SearchTargetDialog(
                iconRes = R.drawable.ic_tag,
                title = tag.title,
                sourceTitle = rememberResolvedSourceTitle(tag.source),
                onDismissRequest = { pendingTagSearch = null },
                onSearchOnSource = {
                    pendingTagSearch = null
                    handleActionClick(DetailsAction.SearchTagOnSource(tag))
                },
                onSearchEverywhere = {
                    pendingTagSearch = null
                    handleActionClick(DetailsAction.SearchTagEverywhere(tag.title))
                },
            )
            }

            if (showShareOptions && content != null) {
            ShareOptionsDialog(
                title = content.title,
                sourceTitle = rememberResolvedSourceTitle(content.source),
                onDismissRequest = { showShareOptions = false },
                onShareAppLink = {
                    showShareOptions = false
                    handleActionClick(
                        DetailsAction.ShareLink(
                            title = content.title,
                            link = content.appUrl.toString(),
                        ),
                    )
                },
                onShareSourceLink = {
                    showShareOptions = false
                    handleActionClick(
                        DetailsAction.ShareLink(
                            title = content.title,
                            link = content.publicUrl,
                        ),
                    )
                },
            )
            }

            if (showDeleteLocalDialog && content != null) {
            DeleteLocalDialog(
                title = content.title,
                onDismissRequest = { showDeleteLocalDialog = false },
                onConfirm = {
                    showDeleteLocalDialog = false
                    handleActionClick(DetailsAction.DeleteLocal)
                },
            )
            }

            if (showFavoriteDialog && content != null) {
            val allCategories by viewModel.allCategories.collectAsState()
            val memberCategoryIds = remember(favouriteCategories) {
                favouriteCategories.mapTo(mutableSetOf()) { it.id }
            }
            FavoriteCategoryDialog(
                contentTitle = content.title,
                allCategories = allCategories,
                memberCategoryIds = memberCategoryIds,
                onCategoryToggle = { categoryId, isChecked ->
                    viewModel.setFavouriteCategory(categoryId, isChecked)
                },
                onManageCategories = {
                    showFavoriteDialog = false
                    handleActionClick(DetailsAction.ManageCategories)
                },
                onDismiss = { showFavoriteDialog = false },
            )
            }

            if (showDownloadDialog && content != null) {
            DownloadDialog(
                mangaList = listOf(content),
                snackbarHostState = snackbarHostState,
                viewModel = downloadDialogViewModel,
                onDismiss = { showDownloadDialog = false },
            )
            }

            if (showStatsDialog && content != null) {
                val statsViewModel: ContentStatsViewModel = hiltViewModel()
                statsViewModel.initialize(content)
                ContentStatsDialog(
                    viewModel = statsViewModel,
                    onDismissRequest = { showStatsDialog = false },
                    onOpenDetails = {
                        showStatsDialog = false
                    },
                )
            }

            if (showScrobblingDialog && content != null) {
                val scrobblingViewModel: ScrobblingSelectorViewModel = hiltViewModel()
                scrobblingViewModel.initialize(content)
                ScrobblingSelectorDialog(
                    viewModel = scrobblingViewModel,
                    onDismissRequest = { showScrobblingDialog = false },
                )
            }

            if (showMetadataSourceDialog) {
                MetadataSourceSheet(
                    currentOptions = metadataSourceOptions,
                    selectedOption = metadataSourceOptions.firstOrNull { it.isSelected },
                    searchServices = metadataSearchServices,
                    authorizedServices = authorizedTrackingServices,
                    selectedService = selectedMetadataSearchService,
                    searchQuery = metadataSearchQuery,
                    searchResults = metadataSearchResults,
                    isLoading = metadataSearchLoading,
                    errorMessage = metadataSearchError,
                    unavailableText = stringResource(R.string.details_reading_source_unavailable),
                    onDismissRequest = { showMetadataSourceDialog = false },
                    onSelectOption = viewModel::selectMetadataSource,
                    onSearchQueryChange = viewModel::updateMetadataSearchQuery,
                    onSelectService = {
                        viewModel.setMetadataSearchService(it)
                        viewModel.searchMetadataBindings()
                    },
                    onSearch = viewModel::searchMetadataBindings,
                    onBindResult = viewModel::bindMetadataSource,
                )
            }

            if (showReadingSourceDialog) {
                ReadingSourceSheet(
                    currentOptions = readingSourceOptions,
                    selectedOption = readingSourceOptions.firstOrNull { it.isSelected },
                    label = stringResource(readingSourceLabelRes),
                    searchSources = readingSearchSources,
                    selectedSourceName = selectedReadingSearchSource,
                    searchQuery = readingSearchQuery,
                    searchState = readingSearchState,
                    unavailableText = stringResource(R.string.details_reading_source_unavailable),
                    onSelectOption = { option -> option.targetMangaId?.let(viewModel::selectActiveLocalSource) },
                    onSearchQueryChange = viewModel::updateReadingSearchQuery,
                    onSelectSearchSource = {
                        viewModel.setReadingSearchSource(it)
                        viewModel.searchReadingBindings()
                    },
                    onSearch = viewModel::searchReadingBindings,
                    onResultClick = { candidate ->
                        viewModel.bindReadingCandidateToTracking(candidate)
                        appRouter.openDetails(candidate)
                    },
                    onDismissRequest = { showReadingSourceDialog = false },
                )
            }

            if (showCommentsDialog) {
                TrackingCommentsSheet(
                    threads = trackingCommentThreads,
                    externalUrl = trackingCommentsUrl,
                    onDismissRequest = { showCommentsDialog = false },
                    onOpenExternal = { url ->
                        showCommentsDialog = false
                        handleActionClick(DetailsAction.OpenWebUrl(url))
                    },
                )
            }

            if (showReviewsDialog) {
                TrackingReviewsSheet(
                    reviews = trackingReviews,
                    externalUrl = trackingReviewsUrl,
                    onDismissRequest = { showReviewsDialog = false },
                    onOpenExternal = { url ->
                        showReviewsDialog = false
                        handleActionClick(DetailsAction.OpenWebUrl(url))
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackingReviewsSheet(
    reviews: List<org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails.ReviewEntry>,
    externalUrl: String?,
    onDismissRequest: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.details_reviews),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!externalUrl.isNullOrBlank()) {
                    TextButton(onClick = { onOpenExternal(externalUrl) }) {
                        Text(stringResource(R.string.details_more_reviews))
                    }
                }
            }
            if (reviews.isEmpty()) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    style = GlassDefaults.subtleStyle(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(
                        text = stringResource(R.string.details_no_reviews),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    )
                }
            } else {
                reviews.forEach { review ->
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        style = GlassDefaults.subtleStyle(),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                AsyncImage(
                                    model = review.avatarUrl,
                                    contentDescription = review.authorName,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                    contentScale = ContentScale.Crop,
                                    error = rememberSafePainter(R.drawable.ic_placeholder),
                                    placeholder = rememberSafePainter(R.drawable.ic_placeholder),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = review.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    val metaLine = buildList {
                                        add(review.authorName)
                                        review.postedAt?.takeIf { it.isNotBlank() }?.let(::add)
                                        review.repliesCount?.let { replies ->
                                            add(stringResource(R.string.details_review_reply_count, replies))
                                        }
                                    }.joinToString(" · ")
                                    Text(
                                        text = metaLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                text = review.excerpt,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            TextButton(
                                modifier = Modifier.align(Alignment.End),
                                onClick = { onOpenExternal(review.url) },
                            ) {
                                Text(stringResource(R.string.details_open_review))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackingCommentsSheet(
    threads: List<org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails.CommentThread>,
    externalUrl: String?,
    onDismissRequest: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.details_comments),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!externalUrl.isNullOrBlank()) {
                    TextButton(onClick = { onOpenExternal(externalUrl) }) {
                        Text(stringResource(R.string.details_more_comments))
                    }
                }
            }
            if (threads.isEmpty()) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    style = GlassDefaults.subtleStyle(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(
                        text = stringResource(R.string.details_no_comments),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    )
                }
            } else {
                threads.forEach { thread ->
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        style = GlassDefaults.subtleStyle(),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                AsyncImage(
                                    model = thread.avatarUrl,
                                    contentDescription = thread.userName,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                    contentScale = ContentScale.Crop,
                                    error = rememberSafePainter(R.drawable.ic_placeholder),
                                    placeholder = rememberSafePainter(R.drawable.ic_placeholder),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = thread.userName,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    val metaLine = buildList {
                                        thread.rating?.let { add(String.format(Locale.ROOT, "%.1f", it)) }
                                        thread.status?.takeIf { it.isNotBlank() }?.let(::add)
                                        thread.postedAt?.takeIf { it.isNotBlank() }?.let(::add)
                                    }.joinToString(" · ")
                                    if (metaLine.isNotBlank()) {
                                        Text(
                                            text = metaLine,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            Text(
                                text = thread.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (thread.replies.isNotEmpty()) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
                                    thread.replies.forEach { reply ->
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.26f),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Text(
                                                    text = buildString {
                                                        append(reply.userName)
                                                        reply.postedAt?.takeIf { it.isNotBlank() }?.let {
                                                            append(" · ")
                                                            append(it)
                                                        }
                                                    },
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                Text(
                                                    text = reply.content,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsScrollableContent(
    mangaDetails: org.skepsun.kototoro.details.data.ContentDetails?,
    historyInfo: HistoryInfo,
    favouriteCategories: Set<org.skepsun.kototoro.core.model.FavouriteCategory>,
    linkedTrackingItems: List<org.skepsun.kototoro.details.ui.model.LinkedTrackingItemUiModel>,
    trackingSuggestion: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult?,
    metadataSourceOptions: List<DetailsSourceOption>,
    readingSourceOptions: List<DetailsSourceOption>,
    activeLocalSourceOptions: List<ActiveLocalSourceOption>,
    entityChapterSourceInfo: EntityChapterSourceInfo?,
    trackingMetadataProperties: List<Pair<String, String>>,
    trackingDetailsSections: List<TrackingDetailsSection>,
    trackingDetailsActions: List<TrackingDetailsAction>,
    resolvedContentType: ContentType?,
    resolvedMetadataLanguage: String?,
    resolvedReadingLanguage: String?,
    entityRelationSections: List<EntityRelationSection>,
    translatedTitle: String?,
    translatedDescription: String?,
    isShowingTranslation: Boolean,
    hasTranslationCache: Boolean,
    isTranslating: Boolean,
    showTranslateAction: Boolean,
    settings: org.skepsun.kototoro.core.prefs.AppSettings,
    collapseProgress: Float,
    coverVisualAlpha: Float,
    coverUrl: String?,
    fallbackCoverUrl: String?,
    showCommentsAction: Boolean,
    showReviewsAction: Boolean,
    content: org.skepsun.kototoro.parsers.model.Content?,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    headerTopSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    bottomSpacerHeight: androidx.compose.ui.unit.Dp,
    pendingTagSearch: (ContentTag) -> Unit,
    pendingAuthorSearch: (String, ContentSource) -> Unit,
    onCoverBoundsSync: (Rect, Float) -> Unit,
    onInfoCardTopSync: (Float) -> Unit,
    onFavoriteClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onReviewsClick: () -> Unit,
    onSelectActiveLocalSource: (Long) -> Unit,
    onSelectMetadataSource: (DetailsSourceOption) -> Unit,
    onOpenMetadataSourceSheet: () -> Unit,
    onOpenReadingSourceSheet: () -> Unit,
    onEntityClick: (Long) -> Unit,
    onActionClick: (DetailsAction) -> Unit,
) {
    val context = LocalContext.current
    val source = content?.source
    Column(
        modifier = modifier
            .padding(contentPadding)
            .verticalScroll(scrollState),
    ) {
        if (headerTopSpacing > 0.dp) {
            Spacer(modifier = Modifier.height(headerTopSpacing))
        }
        DetailsHeader(
            mangaDetails = mangaDetails,
            favouriteCategories = favouriteCategories,
            historyInfo = historyInfo,
            linkedTrackingItems = linkedTrackingItems,
            trackingSuggestion = trackingSuggestion,
            metadataSourceOptions = metadataSourceOptions,
            readingSourceOptions = readingSourceOptions,
            trackingDetailsActions = trackingDetailsActions,
            resolvedContentType = resolvedContentType,
            metadataLanguageCode = resolvedMetadataLanguage,
            readingLanguageCode = resolvedReadingLanguage,
            translatedTitle = translatedTitle,
            translatedDescription = translatedDescription,
            isShowingTranslation = isShowingTranslation,
            hasTranslationCache = hasTranslationCache,
            isTranslating = isTranslating,
            showTranslateAction = showTranslateAction,
            settings = settings,
            collapseProgress = collapseProgress,
            coverVisualAlpha = coverVisualAlpha,
            coverUrl = coverUrl,
            fallbackCoverUrl = fallbackCoverUrl,
            showCommentsAction = showCommentsAction,
            showReviewsAction = showReviewsAction,

            onCoverBoundsSync = onCoverBoundsSync,
            onInfoCardTopSync = onInfoCardTopSync,
            onCoverClick = { onActionClick(DetailsAction.OpenCover) },
            onFavoriteClick = onFavoriteClick,
            onCommentsClick = onCommentsClick,
            onReviewsClick = onReviewsClick,
            onSelectActiveLocalSource = onSelectActiveLocalSource,
            onSelectMetadataSource = onSelectMetadataSource,
            onSourceClick = { onActionClick(DetailsAction.OpenSource(it)) },
            onOpenTrackingDiscover = { service ->
                onActionClick(DetailsAction.OpenTrackingDiscover(service))
            },
            onOpenMetadataSourceSheet = onOpenMetadataSourceSheet,
            onOpenReadingSourceSheet = onOpenReadingSourceSheet,
            onOpenTrackingAction = { action ->
                onActionClick(DetailsAction.OpenWebUrl(action.url))
            },
            onAuthorClick = { author ->
                source?.let { currentSource ->
                    pendingAuthorSearch(author, currentSource)
                }
            },
            onTagClick = pendingTagSearch,
            onTranslateClick = { onActionClick(DetailsAction.Translate) },
            onTranslateLongClick = {
                Toast.makeText(
                    context,
                    R.string.details_translate_title_and_description_hint,
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onToggleTranslationClick = { onActionClick(DetailsAction.ToggleTranslation) },
            onOpenLinkedTracking = { linked ->
                onActionClick(DetailsAction.OpenTrackingDetails(linked.service, linked.remoteId, linked.url))
            },
            onManageLinkedTracking = { linked ->
                onActionClick(DetailsAction.ManageTrackingBinding(linked.service, linked.remoteId, linked.title, linked.url))
            },
            onRemoveLinkedTracking = { match -> onActionClick(DetailsAction.RemoveTrackingMatch(match)) },
            onBindTrackingSuggestion = { match -> onActionClick(DetailsAction.BindTrackingMatch(match)) },
            onOpenTrackingSuggestion = { match ->
                onActionClick(DetailsAction.OpenTrackingDetails(match.service, match.remoteId, match.url))
            },
            onIgnoreTrackingSuggestion = { match -> onActionClick(DetailsAction.IgnoreTrackingSuggestion(match)) },
            onManageTrackingSuggestion = { match ->
                onActionClick(DetailsAction.ManageTrackingBinding(match.service, match.remoteId, match.title, match.url))
            },
        )
        if (trackingMetadataProperties.isNotEmpty()) {
            TrackingMetadataCard(properties = trackingMetadataProperties)
        }
        if (trackingDetailsSections.isNotEmpty()) {
            TrackingDetailsSections(
                sections = trackingDetailsSections,
                onItemClick = { item ->
                    onActionClick(DetailsAction.OpenTrackingDetails(item.service, item.remoteId, item.url))
                },
            )
        }
        if (entityRelationSections.isNotEmpty()) {
            EntityRelationCarousels(
                sections = entityRelationSections,
                onEntityClick = onEntityClick,
            )
        }
        Spacer(modifier = Modifier.height(bottomSpacerHeight))
    }
}

@Composable
private fun DetailsPaneContent(
    contentType: ContentType?,
    historyInfo: HistoryInfo,
    branches: List<ContentBranch>,
    isLoading: Boolean,
    viewModel: DetailsViewModel,
    pagesViewModel: PagesViewModel,
    bookmarksViewModel: BookmarksViewModel,
    settings: AppSettings,
    appRouter: AppRouter,
    pageSaveHelper: PageSaveHelper,
    metadataChapterTabs: List<DetailsChapterSourceTab>,
    readingChapterTabs: List<DetailsChapterSourceTab>,
    onSelectMetadataChapterTab: (DetailsChapterSourceTab) -> Unit,
    onSelectReadingChapterTab: (DetailsChapterSourceTab) -> Unit,
    selectedTabId: Int,
    availableTabIds: List<Int>,
    isSheetFullyExpanded: Boolean,
    sheetExpansionProgress: Float,
    chapterQuery: String,
    isChapterSearchVisible: Boolean,
    isChapterSearchAvailable: Boolean,
    isChaptersReversed: Boolean,
    isChaptersInGridView: Boolean,
    isDownloadedOnly: Boolean,
    isDownloadedFilterVisible: Boolean,
    onChapterQueryChange: (String) -> Unit,
    onChapterSearchToggle: () -> Unit,
    onToggleChaptersReversed: () -> Unit,
    onToggleChaptersGrid: () -> Unit,
    onToggleDownloadedOnly: () -> Unit,
    showCollapsedHandle: Boolean,
    onSelectedTabIdChange: (Int) -> Unit,
    onActionClick: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
    paneDragModifier: Modifier = Modifier,
) {
    val paneOpacityProgress = easedOpacityProgress(sheetExpansionProgress)
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val isStatusBarHandleVisible = showCollapsedHandle && isSheetFullyExpanded
    val opaquePaneOverlayAlpha = if (showCollapsedHandle) {
        lerpFloat(0f, 0.14f, paneOpacityProgress)
    } else {
        0f
    }
    val paneShape = if (showCollapsedHandle && isSheetFullyExpanded && paneOpacityProgress >= 0.96f) {
        RoundedCornerShape(0.dp)
    } else {
        RoundedCornerShape(28.dp)
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(modifier),
            color = MaterialTheme.colorScheme.surface.copy(
                alpha = if (showCollapsedHandle) lerpFloat(0.12f, 0.85f, paneOpacityProgress) else 0.85f
            ),
            shape = paneShape,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                if (opaquePaneOverlayAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = opaquePaneOverlayAlpha)),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (isStatusBarHandleVisible) statusBarTopPadding + 8.dp else 0.dp),
                ) {
                    DetailsPaneActionsRow(
                        modifier = paneDragModifier,
                        selectedTabId = resolveDetailsTabSelection(selectedTabId, availableTabIds),
                        isSheetFullyExpanded = isSheetFullyExpanded,
                        sheetExpansionProgress = sheetExpansionProgress,
                        isChapterSearchVisible = isChapterSearchVisible,
                        isChapterSearchAvailable = isChapterSearchAvailable,
                        isChaptersReversed = isChaptersReversed,
                        isChaptersInGridView = isChaptersInGridView,
                        isDownloadedOnly = isDownloadedOnly,
                        isDownloadedFilterVisible = isDownloadedFilterVisible,
                        onChapterSearchToggle = onChapterSearchToggle,
                        onToggleChaptersReversed = onToggleChaptersReversed,
                        onToggleChaptersGrid = onToggleChaptersGrid,
                        onToggleDownloadedOnly = onToggleDownloadedOnly,
                        showCollapsedHandle = showCollapsedHandle && !isStatusBarHandleVisible,
                        contentType = contentType,
                        historyInfo = historyInfo,
                        branches = branches,
                        isLoading = isLoading,
                        onActionClick = onActionClick,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        ChaptersPagesTabsContent(
                            viewModel = viewModel,
                            pagesViewModel = pagesViewModel,
                            bookmarksViewModel = bookmarksViewModel,
                            settings = settings,
                            appRouter = appRouter,
                            pageSaveHelper = pageSaveHelper,
                            metadataChapterTabs = metadataChapterTabs,
                            readingChapterTabs = readingChapterTabs,
                            onSelectMetadataChapterTab = onSelectMetadataChapterTab,
                            onSelectReadingChapterTab = onSelectReadingChapterTab,
                            selectedTabId = resolveDetailsTabSelection(selectedTabId, availableTabIds),
                            showTabStrip = false,
                            isSheetFullyExpanded = isSheetFullyExpanded,
                            isChapterListScrollEnabled = if (showCollapsedHandle) isSheetFullyExpanded else true,
                            chapterQuery = chapterQuery,
                            isChapterSearchVisible = isChapterSearchVisible,
                            onChapterQueryChange = onChapterQueryChange,
                            onSelectedTabIdChange = { tabId ->
                                val resolvedTab = resolveDetailsTabSelection(tabId, availableTabIds)
                                onSelectedTabIdChange(resolvedTab)
                            },
                        )
                    }
                }
                if (isStatusBarHandleVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = (statusBarTopPadding * 0.5f).coerceAtLeast(8.dp))
                            .then(paneDragModifier),
                    ) {
                        DetailsPaneDragHandle(
                            modifier = Modifier.alpha(lerpFloat(0.68f, 1f, paneOpacityProgress)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsPaneActionsRow(
    modifier: Modifier = Modifier,
    selectedTabId: Int,
    isSheetFullyExpanded: Boolean,
    sheetExpansionProgress: Float,
    isChapterSearchVisible: Boolean,
    isChapterSearchAvailable: Boolean,
    isChaptersReversed: Boolean,
    isChaptersInGridView: Boolean,
    isDownloadedOnly: Boolean,
    isDownloadedFilterVisible: Boolean,
    onChapterSearchToggle: () -> Unit,
    onToggleChaptersReversed: () -> Unit,
    onToggleChaptersGrid: () -> Unit,
    onToggleDownloadedOnly: () -> Unit,
    showCollapsedHandle: Boolean,
    contentType: ContentType?,
    historyInfo: HistoryInfo,
    branches: List<ContentBranch>,
    isLoading: Boolean,
    onActionClick: (DetailsAction) -> Unit,
) {
    val paneOpacityProgress = easedOpacityProgress(sheetExpansionProgress)
    val showPagesTab = contentType != ContentType.VIDEO &&
        contentType != ContentType.HENTAI_VIDEO &&
        contentType != ContentType.NOVEL &&
        contentType != ContentType.HENTAI_NOVEL
    val showBookmarksTab = contentType != ContentType.VIDEO &&
        contentType != ContentType.HENTAI_VIDEO
    Column(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        if (showCollapsedHandle) {
            DetailsPaneDragHandle()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                DetailsDockActionButton(
                    iconRes = R.drawable.ic_list,
                    contentDescription = stringResource(R.string.chapters),
                    isSelected = selectedTabId == DETAILS_TAB_CHAPTERS,
                    onClick = { onActionClick(DetailsAction.ToggleList) },
                )
                if (showPagesTab) {
                    DetailsDockActionButton(
                        iconRes = R.drawable.ic_grid,
                        contentDescription = stringResource(R.string.pages),
                        isSelected = selectedTabId == DETAILS_TAB_PAGES,
                        onClick = { onActionClick(DetailsAction.ToggleGrid) },
                    )
                }
                if (showBookmarksTab) {
                    DetailsDockActionButton(
                        iconRes = R.drawable.ic_bookmark,
                        contentDescription = stringResource(R.string.bookmarks),
                        isSelected = selectedTabId == DETAILS_TAB_BOOKMARKS,
                        onClick = { onActionClick(DetailsAction.ToggleBookmarkView) },
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                if (isSheetFullyExpanded && selectedTabId == DETAILS_TAB_CHAPTERS) {
                    ExpandedPaneUtilityDock(
                        modifier = Modifier.weight(1f),
                        sheetExpansionProgress = paneOpacityProgress,
                        isSearchEnabled = isChapterSearchAvailable,
                        isSearchActive = isChapterSearchVisible,
                        isChaptersReversed = isChaptersReversed,
                        isChaptersInGridView = isChaptersInGridView,
                        isDownloadedOnly = isDownloadedOnly,
                        isDownloadedFilterVisible = isDownloadedFilterVisible,
                        onSearchClick = onChapterSearchToggle,
                        onToggleChaptersReversed = onToggleChaptersReversed,
                        onToggleChaptersGrid = onToggleChaptersGrid,
                        onToggleDownloadedOnly = onToggleDownloadedOnly,
                    )
                } else {
                    ReadDock(
                        modifier = Modifier.weight(1f),
                        sheetExpansionProgress = paneOpacityProgress,
                        readLabel = resolveReadActionLabel(
                            contentType = contentType,
                            historyInfo = historyInfo,
                            isLoading = isLoading,
                        ),
                        branches = branches,
                        historyInfo = historyInfo,
                        isDownloadAvailable = historyInfo.canDownload,
                        isEnabled = !isLoading && historyInfo.isValid,
                        onReadClick = { onActionClick(DetailsAction.Resume) },
                        onIncognitoClick = { onActionClick(DetailsAction.ResumeIncognito) },
                        onForgetClick = { onActionClick(DetailsAction.ForgetHistory) },
                        onDownloadClick = { onActionClick(DetailsAction.Download) },
                        onBranchSelected = { onActionClick(DetailsAction.SelectBranch(it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsPaneDragHandle(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(28.dp)
            .height(4.dp)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                shape = RoundedCornerShape(999.dp),
            ),
    )
}

@Composable
private fun ExpandedPaneUtilityDock(
    modifier: Modifier = Modifier,
    sheetExpansionProgress: Float,
    isSearchEnabled: Boolean,
    isSearchActive: Boolean,
    isChaptersReversed: Boolean,
    isChaptersInGridView: Boolean,
    isDownloadedOnly: Boolean,
    isDownloadedFilterVisible: Boolean,
    onSearchClick: () -> Unit,
    onToggleChaptersReversed: () -> Unit,
    onToggleChaptersGrid: () -> Unit,
    onToggleDownloadedOnly: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.height(52.dp).padding(horizontal = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onSearchClick,
                enabled = isSearchEnabled,
                modifier = Modifier
                    .width(42.dp)
                    .height(42.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search_chapters),
                    tint = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .width(42.dp)
                    .height(42.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.options),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reverse)) },
                leadingIcon = {
                    MenuSelectionIndicator(selected = isChaptersReversed)
                },
                onClick = {
                    expanded = false
                    onToggleChaptersReversed()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chapters_grid_view)) },
                leadingIcon = {
                    MenuSelectionIndicator(selected = isChaptersInGridView)
                },
                onClick = {
                    expanded = false
                    onToggleChaptersGrid()
                },
            )
            if (isDownloadedFilterVisible) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.downloaded)) },
                    leadingIcon = {
                        MenuSelectionIndicator(selected = isDownloadedOnly)
                    },
                    onClick = {
                        expanded = false
                        onToggleDownloadedOnly()
                    },
                )
            }
        }
    }
}

@Composable
private fun MenuSelectionIndicator(
    selected: Boolean,
) {
    val strokeColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
    }
    Box(
        modifier = Modifier
            .size(18.dp)
            .border(
                width = 1.5.dp,
                color = strokeColor,
                shape = RoundedCornerShape(5.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = strokeColor,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
internal fun DetailsDockActionButton(
    iconRes: Int,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.padding(end = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .width(42.dp)
                .height(42.dp),
        ) {
            Icon(
                painter = rememberSafePainter(iconRes),
                contentDescription = contentDescription,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

sealed interface DetailsAction {
    data object OpenCover : DetailsAction
    data class OpenSource(val source: ContentSource) : DetailsAction
    data class OpenTrackingDiscover(val service: ScrobblerService) : DetailsAction
    data class SearchAuthorOnSource(val author: String, val source: ContentSource) : DetailsAction
    data class SearchAuthorEverywhere(val author: String) : DetailsAction
    data class SearchTagOnSource(val tag: ContentTag) : DetailsAction
    data class SearchTagEverywhere(val tagTitle: String) : DetailsAction
    data class OpenWebUrl(val url: String) : DetailsAction
    data class SelectBranch(val branch: String?) : DetailsAction
    data object ManageCategories : DetailsAction
    data object ManageDownloads : DetailsAction
    data object Favorite : DetailsAction
    data object Share : DetailsAction
    data class ShareLink(val title: String, val link: String) : DetailsAction
    data object Download : DetailsAction
    data object DeleteLocal : DetailsAction
    data object EditOverride : DetailsAction
    data object CreateShortcut : DetailsAction
    data object Translate : DetailsAction
    data object ToggleTranslation : DetailsAction
    data object FindSimilar : DetailsAction
    data object OpenAlternatives : DetailsAction
    data object OpenOnlineVariant : DetailsAction
    data class OpenBrowserPage(
        val url: String,
        val source: ContentSource?,
        val title: String?,
    ) : DetailsAction
    data object OpenMetadataInBrowser : DetailsAction
    data object OpenLocalSourceInBrowser : DetailsAction
    data object OpenTracking : DetailsAction
    data object OpenStatistics : DetailsAction
    data object ToggleSafe : DetailsAction
    data object ToggleList : DetailsAction
    data object ToggleGrid : DetailsAction
    data object ToggleBookmarkView : DetailsAction
    data object Resume : DetailsAction
    data object ResumeIncognito : DetailsAction
    data object ForgetHistory : DetailsAction
    data class OpenTrackingDetails(
        val service: org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService,
        val remoteId: Long,
        val url: String?,
    ) : DetailsAction
    data class ManageTrackingBinding(
        val service: org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService,
        val remoteId: Long,
        val title: String,
        val url: String?,
    ) : DetailsAction
    data class BindTrackingMatch(
        val match: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult,
    ) : DetailsAction
    data class IgnoreTrackingSuggestion(
        val match: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult,
    ) : DetailsAction
    data class RemoveTrackingMatch(
        val match: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult,
    ) : DetailsAction
}

@Composable
private fun ReadDock(
    modifier: Modifier = Modifier,
    sheetExpansionProgress: Float,
    readLabel: String,
    branches: List<ContentBranch>,
    historyInfo: HistoryInfo,
    isDownloadAvailable: Boolean,
    isEnabled: Boolean,
    onReadClick: () -> Unit,
    onIncognitoClick: () -> Unit,
    onForgetClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onBranchSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasBranchOptions = branches.size > 1
    val canOpenIncognito = !historyInfo.isIncognitoMode
    val canForgetHistory = historyInfo.history != null
    val hasQuickActions = canOpenIncognito || canForgetHistory || isDownloadAvailable
    val hasMenuActions = hasQuickActions || hasBranchOptions

    val shapeRadiusPercent by androidx.compose.animation.core.animateIntAsState(targetValue = if (expanded) 50 else 0)
    val optionGap by androidx.compose.animation.core.animateDpAsState(targetValue = if (expanded) 8.dp else 2.dp)

    Row(
        modifier = modifier.height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(optionGap)
    ) {
        androidx.compose.material3.Button(
            onClick = onReadClick,
            enabled = isEnabled,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                topStartPercent = 50,
                bottomStartPercent = 50,
                topEndPercent = shapeRadiusPercent,
                bottomEndPercent = shapeRadiusPercent
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = lerpFloat(0.9f, 1f, sheetExpansionProgress)),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Text(
                text = readLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box {
            androidx.compose.material3.Button(
                onClick = { expanded = true },
                enabled = hasMenuActions,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    topEndPercent = 50,
                    bottomEndPercent = 50,
                    topStartPercent = shapeRadiusPercent,
                    bottomStartPercent = shapeRadiusPercent
                ),
                modifier = Modifier
                    .width(52.dp)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(0.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = lerpFloat(0.9f, 1f, sheetExpansionProgress)),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (hasBranchOptions) {
                        stringResource(R.string.system_default)
                    } else {
                        stringResource(R.string.options)
                    },
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                if (canOpenIncognito) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.incognito_mode)) },
                        onClick = {
                            expanded = false
                            onIncognitoClick()
                        },
                    )
                }
                if (canForgetHistory) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_from_history)) },
                        onClick = {
                            expanded = false
                            onForgetClick()
                        },
                    )
                }
                if (isDownloadAvailable) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.download)) },
                        onClick = {
                            expanded = false
                            onDownloadClick()
                        },
                    )
                }
                if (hasQuickActions && hasBranchOptions) {
                    HorizontalDivider()
                }
                branches.forEach { branch ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = buildString {
                                    append(branch.name ?: stringResource(R.string.system_default))
                                    append(" / ")
                                    append(branch.count)
                                },
                            )
                        },
                        leadingIcon = {
                            if (branch.isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onBranchSelected(branch.name)
                        },
                    )
                }
            }
        }
    }
}

private enum class CompactDetailsPaneAnchor {
    Collapsed,
    Hovered,
    Full,
}

private fun compactPaneFullOffsetPx(
    hostHeightPx: Float,
    paneHeightPx: Float,
): Float {
    if (hostHeightPx <= 0f) return 0f
    return (hostHeightPx - paneHeightPx).coerceAtLeast(0f)
}

private fun compactPaneCollapsedOffsetPx(
    hostHeightPx: Float,
    collapsedHeightPx: Float,
): Float {
    if (hostHeightPx <= 0f) return 0f
    return (hostHeightPx - collapsedHeightPx).coerceAtLeast(0f)
}

private fun compactPaneHoveredOffsetPx(
    hostHeightPx: Float,
    hoveredHeightPx: Float,
    fullOffset: Float,
    collapsedOffset: Float,
): Float {
    if (hostHeightPx <= 0f || collapsedOffset <= fullOffset) {
        return fullOffset
    }
    val rawOffset = (hostHeightPx - hoveredHeightPx).coerceAtLeast(0f)
    val edgePadding = minOf(28f, (collapsedOffset - fullOffset) / 3f)
    return rawOffset.coerceIn(fullOffset + edgePadding, collapsedOffset - edgePadding)
}

private fun compactPaneOffsetForAnchor(
    anchor: CompactDetailsPaneAnchor,
    fullOffset: Float,
    hoveredOffset: Float,
    collapsedOffset: Float,
): Float {
    return when (anchor) {
        CompactDetailsPaneAnchor.Collapsed -> collapsedOffset
        CompactDetailsPaneAnchor.Hovered -> hoveredOffset
        CompactDetailsPaneAnchor.Full -> fullOffset
    }
}

private fun compactPaneAnimationDurationMillis(
    anchor: CompactDetailsPaneAnchor,
): Int {
    return when (anchor) {
        CompactDetailsPaneAnchor.Collapsed -> 420
        CompactDetailsPaneAnchor.Hovered -> 440
        CompactDetailsPaneAnchor.Full -> 480
    }
}

private fun resolveCompactPaneAnchor(
    currentOffset: Float,
    velocity: Float,
    fullOffset: Float,
    hoveredOffset: Float,
    collapsedOffset: Float,
): CompactDetailsPaneAnchor {
    val fastSwipeThreshold = 1200f
    return when {
        velocity <= -fastSwipeThreshold -> when {
            currentOffset <= hoveredOffset -> CompactDetailsPaneAnchor.Full
            else -> CompactDetailsPaneAnchor.Hovered
        }

        velocity >= fastSwipeThreshold -> when {
            currentOffset >= hoveredOffset -> CompactDetailsPaneAnchor.Collapsed
            else -> CompactDetailsPaneAnchor.Hovered
        }

        else -> listOf(
            CompactDetailsPaneAnchor.Full to abs(currentOffset - fullOffset),
            CompactDetailsPaneAnchor.Hovered to abs(currentOffset - hoveredOffset),
            CompactDetailsPaneAnchor.Collapsed to abs(currentOffset - collapsedOffset),
        ).minBy { it.second }.first
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction.coerceIn(0f, 1f)
}

private fun easedOpacityProgress(progress: Float): Float {
    val clamped = progress.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

@Composable
private fun resolveReadActionLabel(
    contentType: ContentType?,
    historyInfo: HistoryInfo,
    isLoading: Boolean,
): String {
    val isChaptersLoading = isLoading && (historyInfo.totalChapters <= 0 || historyInfo.isChapterMissing)
    val defaultReadRes = when (contentType) {
        ContentType.VIDEO, ContentType.HENTAI_VIDEO -> R.string.play
        else -> R.string.read
    }
    val continueRes = when (contentType) {
        ContentType.VIDEO, ContentType.HENTAI_VIDEO -> R.string._continue_play
        else -> R.string._continue
    }
    return stringResource(
        when {
            isChaptersLoading -> R.string.loading_
            historyInfo.isIncognitoMode -> R.string.incognito
            historyInfo.canContinue -> continueRes
            else -> defaultReadRes
        },
    )
}

@Composable
private fun DetailsOverflowMenu(
    contentTitle: String?,
    showTranslateAction: Boolean,
    hasTranslationCache: Boolean,
    isShowingTranslation: Boolean,
    isTranslating: Boolean,
    isScrobblingAvailable: Boolean,
    isStatsAvailable: Boolean,
    hasMetadataBrowserTarget: Boolean,
    hasLocalBrowserTarget: Boolean,
    isAlternativesAvailable: Boolean,
    hasOnlineVariant: Boolean,
    isDeleteLocalAvailable: Boolean,
    isEditOverrideAvailable: Boolean,
    isShortcutSupported: Boolean,
    isNsfw: Boolean,
    onDeleteLocalRequest: () -> Unit,
    onActionClick: (DetailsAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        DetailsChromeButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (showTranslateAction) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (hasTranslationCache && isShowingTranslation) {
                                    R.string.details_show_original
                                } else if (hasTranslationCache) {
                                    R.string.details_show_translation
                                } else {
                                    R.string.translate_title
                                },
                            ),
                        )
                    },
                    enabled = !isTranslating,
                    onClick = {
                        expanded = false
                        onActionClick(
                            if (hasTranslationCache) {
                                DetailsAction.ToggleTranslation
                            } else {
                                DetailsAction.Translate
                            },
                        )
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (isNsfw) R.string.mark_as_safe else R.string.mark_as_nsfw)) },
                onClick = {
                    expanded = false
                    onActionClick(DetailsAction.ToggleSafe)
                },
            )
            if (isDeleteLocalAvailable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = {
                        expanded = false
                        if (contentTitle != null) {
                            onDeleteLocalRequest()
                        }
                    },
                )
            }
            if (isEditOverrideAvailable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.EditOverride)
                    },
                )
            }
            if (isShortcutSupported) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.create_shortcut)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.CreateShortcut)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.find_similar)) },
                onClick = {
                    expanded = false
                    onActionClick(DetailsAction.FindSimilar)
                },
            )
            if (isAlternativesAvailable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.alternatives)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.OpenAlternatives)
                    },
                )
            }
            if (hasOnlineVariant) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.online_variant)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.OpenOnlineVariant)
                    },
                )
            }
            if (hasMetadataBrowserTarget) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_metadata_in_browser)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.OpenMetadataInBrowser)
                    },
                )
            }
            if (hasLocalBrowserTarget) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_local_source_in_browser)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.OpenLocalSourceInBrowser)
                    },
                )
            }
            if (isScrobblingAvailable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tracking)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.OpenTracking)
                    },
                )
            }
            if (isStatsAvailable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.statistics)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.OpenStatistics)
                    },
                )
            }
        }
    }
}

@Composable
private fun DeleteLocalDialog(
    title: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.delete_manga)) },
        text = { Text(stringResource(R.string.text_delete_local_manga, title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun SearchTargetDialog(
    iconRes: Int,
    title: String,
    sourceTitle: String,
    onDismissRequest: () -> Unit,
    onSearchOnSource: () -> Unit,
    onSearchEverywhere: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                painter = rememberSafePainter(iconRes),
                contentDescription = null,
            )
        },
        title = { Text(text = title) },
        text = {
            Column {
                TextButton(
                    onClick = onSearchOnSource,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.search_on_s, sourceTitle),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    onClick = onSearchEverywhere,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.search_everywhere),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.close))
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun ShareOptionsDialog(
    title: String,
    sourceTitle: String,
    onDismissRequest: () -> Unit,
    onShareAppLink: () -> Unit,
    onShareSourceLink: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
            )
        },
        title = { Text(text = stringResource(R.string.share)) },
        text = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
                TextButton(
                    onClick = onShareAppLink,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.link_to_manga_in_app),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    onClick = onShareSourceLink,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.link_to_manga_on_s, sourceTitle),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        confirmButton = {},
    )
}


private data class PendingAuthorSearch(
    val author: String,
    val source: ContentSource,
)

private fun resolveAvailableDetailsTabIds(
    contentType: ContentType?,
    settings: AppSettings,
): List<Int> = buildList {
    add(DETAILS_TAB_CHAPTERS)
    val isNovel = contentType == ContentType.NOVEL || contentType == ContentType.HENTAI_NOVEL
    val isVideo = contentType == ContentType.VIDEO || contentType == ContentType.HENTAI_VIDEO
    if (settings.isPagesTabEnabled && !isNovel && !isVideo) {
        add(DETAILS_TAB_PAGES)
    }
    if (!isVideo) {
        add(DETAILS_TAB_BOOKMARKS)
    }
}

private fun resolveDetailsTabSelection(
    requestedTabId: Int,
    availableTabs: List<Int>,
): Int {
    return if (requestedTabId in availableTabs) {
        requestedTabId
    } else {
        when {
            requestedTabId > DETAILS_TAB_CHAPTERS -> {
                availableTabs.getOrElse((requestedTabId - 1).coerceAtLeast(0)) { availableTabs.first() }
            }
            else -> availableTabs.first()
        }
    }
}

@Composable
fun DetailsChromeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 2.dp),
    ) {
        content()
    }
}


@Composable
fun EntityRelationCarousels(
    sections: List<EntityRelationSection>,
    onEntityClick: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        sections.forEach { section ->
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                style = GlassDefaults.regularStyle(),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    EntityRelationSectionHeader(section = section)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(
                            items = section.items,
                            key = { it.entityId },
                        ) { item ->
                            EntityRelationCard(item = item, onClick = { onEntityClick(item.entityId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackingMetadataCard(
    properties: List<Pair<String, String>>,
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        style = GlassDefaults.subtleStyle(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.details_additional_metadata),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            properties.forEach { (label, value) ->
                if (value.isBlank()) {
                    return@forEach
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(96.dp),
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackingDetailsSections(
    sections: List<TrackingDetailsSection>,
    onItemClick: (TrackingDetailsSectionItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        sections.forEach { section ->
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                style = GlassDefaults.regularStyle(),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = section.titleRes?.let { stringResource(it) } ?: section.title.orEmpty(),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        ) {
                            Text(
                                text = section.items.size.toString(),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(
                            items = section.items,
                            key = { "${it.service.id}:${it.remoteId}" },
                        ) { item ->
                            TrackingDetailsCard(
                                item = item,
                                onClick = { onItemClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackingDetailsCard(
    item: TrackingDetailsSectionItem,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.32f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(14.dp)),
                error = rememberSafePainter(R.drawable.ic_placeholder),
                placeholder = rememberSafePainter(R.drawable.ic_placeholder),
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EntityRelationSectionHeader(
    section: EntityRelationSection,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        ) {
            Icon(
                painter = rememberSafePainter(entityRelationSectionIconRes(section.titleRes)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(10.dp)
                    .size(18.dp),
            )
        }
        Text(
            text = stringResource(section.titleRes),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        ) {
            Text(
                text = section.items.size.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun EntityRelationCard(
    item: EntityRelationItem,
    onClick: () -> Unit,
) {
    val typeLabel = stringResource(entityRelationTypeLabelRes(item.type))
    val typeIconRes = entityRelationTypeIconRes(item.type)
    GlassSurface(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick),
        style = GlassDefaults.regularStyle(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.76f),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (item.coverUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = rememberSafePainter(typeIconRes),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    } else {
                        AsyncImage(
                            model = item.coverUrl,
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.22f),
                                    ),
                                ),
                            ),
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = rememberSafePainter(typeIconRes),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = rememberSafePainter(typeIconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        painter = rememberSafePainter(R.drawable.ic_arrow_forward),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

private fun entityRelationSectionIconRes(titleRes: Int): Int = when (titleRes) {
    R.string.entity_graph_section_characters -> R.drawable.ic_user
    R.string.entity_graph_section_creators -> R.drawable.ic_auto_fix
    R.string.entity_graph_section_parent_work -> R.drawable.ic_content_manga
    R.string.entity_graph_section_voice_actors -> R.drawable.ic_voice_input
    R.string.entity_graph_section_created_works -> R.drawable.ic_content_manga
    R.string.entity_graph_section_voiced_characters -> R.drawable.ic_user
    R.string.entity_graph_section_related_entities -> R.drawable.ic_select_group
    else -> R.drawable.ic_select_group
}

private fun entityRelationTypeLabelRes(type: org.skepsun.kototoro.entitygraph.domain.EntityType): Int = when (type) {
    org.skepsun.kototoro.entitygraph.domain.EntityType.WORK -> R.string.entity_graph_type_work
    org.skepsun.kototoro.entitygraph.domain.EntityType.CHARACTER -> R.string.entity_graph_type_character
    org.skepsun.kototoro.entitygraph.domain.EntityType.PERSON -> R.string.entity_graph_type_person
    org.skepsun.kototoro.entitygraph.domain.EntityType.ORGANIZATION -> R.string.entity_graph_type_organization
}

private fun entityRelationTypeIconRes(type: org.skepsun.kototoro.entitygraph.domain.EntityType): Int = when (type) {
    org.skepsun.kototoro.entitygraph.domain.EntityType.WORK -> R.drawable.ic_content_manga
    org.skepsun.kototoro.entitygraph.domain.EntityType.CHARACTER -> R.drawable.ic_user
    org.skepsun.kototoro.entitygraph.domain.EntityType.PERSON -> R.drawable.ic_user
    org.skepsun.kototoro.entitygraph.domain.EntityType.ORGANIZATION -> R.drawable.ic_select_group
}
