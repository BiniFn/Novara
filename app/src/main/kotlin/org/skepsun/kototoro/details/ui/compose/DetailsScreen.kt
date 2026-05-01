package org.skepsun.kototoro.details.ui.compose

import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState as createAnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Slider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import org.skepsun.kototoro.core.ui.compose.KototoroPullToRefreshBox
import org.skepsun.kototoro.core.ui.compose.rememberSafePainter
import org.skepsun.kototoro.core.ui.compose.rememberResolvedSourceTitle
import org.skepsun.kototoro.core.ui.glass.GlassDefaults
import org.skepsun.kototoro.core.ui.glass.GlassStyle
import org.skepsun.kototoro.core.ui.glass.GlassSurface
import org.skepsun.kototoro.core.ui.glass.LocalHazeState
import org.skepsun.kototoro.core.util.ext.isHttpUrl
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.details.ui.DetailsViewModel
import org.skepsun.kototoro.details.ui.model.ActiveLocalSourceOption
import org.skepsun.kototoro.details.ui.model.ContentBranch
import org.skepsun.kototoro.details.ui.model.DetailsChapterSourceTab
import org.skepsun.kototoro.details.ui.model.DetailsSourceOption
import org.skepsun.kototoro.details.ui.model.DetailsSupplementAction
import org.skepsun.kototoro.details.ui.model.EntityChapterSourceInfo
import org.skepsun.kototoro.details.ui.model.HistoryInfo
import org.skepsun.kototoro.details.ui.compose.pane.DetailsPaneHost
import org.skepsun.kototoro.details.ui.compose.state.CompactDetailsPaneAnchor
import org.skepsun.kototoro.details.ui.compose.state.rememberDetailsPaneState
import org.skepsun.kototoro.details.ui.compose.state.DetailsPaneTopBarMode
import org.skepsun.kototoro.details.ui.compose.state.DetailsPaneState
import org.skepsun.kototoro.entitygraph.ui.details.EntityRelationSection
import org.skepsun.kototoro.entitygraph.ui.details.EntityRelationItem
import org.skepsun.kototoro.details.ui.pager.bookmarks.BookmarksViewModel
import org.skepsun.kototoro.details.ui.pager.chapters.compose.ChapterSelectionUiState
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
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    pagesViewModel: PagesViewModel,
    bookmarksViewModel: BookmarksViewModel,
    settings: AppSettings,
    appRouter: AppRouter,
    pageSaveHelper: PageSaveHelper,
    onBackClick: () -> Unit,
    sharedElementKey: String? = null,
    onActionClick: (DetailsAction) -> Unit = {},
) {
    val detailsPrimaryUiState by viewModel.detailsPrimaryUiState.collectAsStateWithLifecycle()
    val translationUiState by viewModel.translationUiState.collectAsStateWithLifecycle()
    val chaptersPaneControlsUiState by viewModel.chaptersPaneControlsUiState.collectAsStateWithLifecycle()
    val pagesGridScale by pagesViewModel.gridScale.collectAsStateWithLifecycle(initialValue = settings.gridSizePages / 100f)
    val sourceBindingUiState by viewModel.sourceBindingUiState.collectAsStateWithLifecycle()
    val detailsSupplementUiState by viewModel.detailsSupplementUiState.collectAsStateWithLifecycle()
    val metadataSearchUiState by viewModel.metadataSearchUiState.collectAsStateWithLifecycle()
    val readingSearchUiState by viewModel.readingSearchUiState.collectAsStateWithLifecycle()
    val mangaDetails = detailsPrimaryUiState.mangaDetails
    val remoteContent = detailsPrimaryUiState.remoteContent
    val favouriteCategories = detailsPrimaryUiState.favouriteCategories
    val historyInfo = detailsPrimaryUiState.historyInfo
    val branches = detailsPrimaryUiState.branches
    val isStatsAvailable = detailsPrimaryUiState.isStatsAvailable
    val trackingSuggestion = detailsPrimaryUiState.trackingSuggestion
    val linkedTrackingItems = detailsPrimaryUiState.linkedTrackingItems
    val isLoading = detailsPrimaryUiState.isLoading
    val entityRelationSections = detailsPrimaryUiState.entityRelationSections
    val activeLocalBrowserContent = detailsPrimaryUiState.activeLocalBrowserContent
    val isChaptersReversed = chaptersPaneControlsUiState.isChaptersReversed
    val isChaptersInGridView = chaptersPaneControlsUiState.isChaptersInGridView
    val isDownloadedOnly = chaptersPaneControlsUiState.isDownloadedOnly
    val chapterEmptyReason = chaptersPaneControlsUiState.emptyReason
    val activeLocalSourceOptions = sourceBindingUiState.activeLocalSourceOptions
    val entityChapterSourceInfo = sourceBindingUiState.entityChapterSourceInfo
    val metadataSourceOptions = sourceBindingUiState.metadataSourceOptions
    val readingSourceOptions = sourceBindingUiState.readingSourceOptions
    val metadataChapterTabs = sourceBindingUiState.metadataChapterTabs
    val readingChapterTabs = sourceBindingUiState.readingChapterTabs
    val resolvedMetadataContentType = sourceBindingUiState.resolvedMetadataContentType
    val resolvedMetadataLanguage = sourceBindingUiState.resolvedMetadataLanguage
    val resolvedReadingLanguage = sourceBindingUiState.resolvedReadingLanguage
    val translatedTitle = translationUiState.translatedTitle
    val translatedDescription = translationUiState.translatedDescription
    val isShowingTranslation = translationUiState.isShowingTranslation
    val hasTranslationCache = translationUiState.hasTranslationCache
    val isTranslating = translationUiState.isTranslating
    val showTranslateAction = translationUiState.showTranslateAction
    val supplementalMetadataProperties = detailsSupplementUiState.metadataProperties
    val supplementalSections = detailsSupplementUiState.sections
    val supplementalActions = detailsSupplementUiState.actions
    val supplementalCommentThreads = detailsSupplementUiState.commentThreads
    val supplementalCommentsUrl = detailsSupplementUiState.commentsUrl
    val supplementalReviews = detailsSupplementUiState.reviews
    val supplementalReviewsUrl = detailsSupplementUiState.reviewsUrl
    val metadataSearchServices = metadataSearchUiState.services
    val authorizedTrackingServices = metadataSearchUiState.authorizedServices
    val selectedMetadataSearchService = metadataSearchUiState.selectedService
    val metadataSearchQuery = metadataSearchUiState.query
    val metadataSearchResults = metadataSearchUiState.results
    val metadataSearchSections = metadataSearchUiState.sections
    val metadataSearchLoading = metadataSearchUiState.isLoading
    val metadataSearchHasSearched = metadataSearchUiState.hasSearched
    val metadataSearchError = metadataSearchUiState.errorMessage
    val readingSearchSources = readingSearchUiState.sources
    val selectedReadingSearchSource = readingSearchUiState.selectedSource
    val readingSearchQuery = readingSearchUiState.query
    val readingSearchSections = readingSearchUiState.sections
    val readingSearchLoading = readingSearchUiState.isLoading
    val readingSearchHasSearched = readingSearchUiState.hasSearched
    val readingSearchState = readingSearchUiState.state

    val context = LocalContext.current
    val panoramaPrefs = rememberPanoramaBackdropPrefs(settings)
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
    var showCommentsDialog by remember { mutableStateOf(false) }
    var showReviewsDialog by remember { mutableStateOf(false) }
    var selectedSupplementalRelationItem by remember { mutableStateOf<EntityRelationItem?>(null) }
    var showMetadataSourceDialog by remember { mutableStateOf(false) }
    var showReadingSourceDialog by remember { mutableStateOf(false) }
    LaunchedEffect(showMetadataSourceDialog) {
        if (showMetadataSourceDialog && !metadataSearchHasSearched && !metadataSearchLoading) {
            viewModel.searchMetadataBindings()
        }
    }
    LaunchedEffect(showReadingSourceDialog) {
        if (showReadingSourceDialog && !readingSearchHasSearched && !readingSearchLoading) {
            viewModel.searchReadingBindings()
        }
    }
    val availableTabIds = remember(contentType, settings.isPagesTabEnabled) {
        resolveAvailableDetailsTabIds(contentType, settings)
    }
    val isWideAdaptiveLayout = remember(configuration.orientation, configuration.screenWidthDp) {
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 720
    }
    val density = LocalDensity.current
    val navigationBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val compactPaneCollapsedHeight = remember(navigationBarBottomPadding) {
        (68.dp + navigationBarBottomPadding).coerceIn(88.dp, 120.dp)
    }
    val detailsPaneState = rememberDetailsPaneState(
        screenHeightDp = configuration.screenHeightDp,
        collapsedHeight = compactPaneCollapsedHeight,
        initialPageGridSizeValue = settings.gridSizePages.toFloat(),
        initialSelectedTabId = settings.defaultDetailsTab,
        initialChapterQuery = "",
    )
    val compactPaneHeight = detailsPaneState.paneHeight
    val compactPaneAnchor = detailsPaneState.anchor
    val pageGridSizeValue = detailsPaneState.pageGridSizeValue
    val sheetTabSelection = remember(detailsPaneState.selectedTabId, availableTabIds) {
        detailsPaneState.resolvedSelectedTabId(availableTabIds)
    }
    LaunchedEffect(isWideAdaptiveLayout, detailsPaneState.chapterSelectionState) {
        if (!isWideAdaptiveLayout && detailsPaneState.chapterSelectionState != null) {
            detailsPaneState.onChapterSelectionActivated()
        }
    }
    LaunchedEffect(pagesGridScale) {
        detailsPaneState.syncPageGridSizeValue((pagesGridScale * 100f).coerceIn(50f, 150f))
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val toolbarGapPx = with(density) { 12.dp.toPx() }
    var toolbarBottomPx by remember { mutableFloatStateOf(Float.NaN) }
    var lastToolbarBottomPx by remember { mutableFloatStateOf(Float.NaN) }
    var infoCardTopPx by remember { mutableFloatStateOf(Float.NaN) }
    var initialInfoCardTopPx by remember { mutableFloatStateOf(Float.NaN) }

    LaunchedEffect(availableTabIds) {
        detailsPaneState.syncSelectedTabs(
            availableTabIds = availableTabIds,
            defaultTabId = settings.defaultDetailsTab,
            onDefaultResolved = { resolvedDefaultTab ->
                settings.defaultDetailsTab = resolvedDefaultTab
            },
        )
    }

    LaunchedEffect(isWideAdaptiveLayout) {
        if (isWideAdaptiveLayout) {
            landscapeLeftScrollState.scrollTo(0)
        }
    }
    val compactCollapseProgressProvider = remember(
        scrollState,
        landscapeLeftScrollState,
        toolbarGapPx,
        isWideAdaptiveLayout,
    ) {
        {
            calculateDetailsScrollProgress(
                scrollValue = scrollState.value,
                landscapeScrollValue = landscapeLeftScrollState.value,
                toolbarBottomPx = toolbarBottomPx,
                infoCardTopPx = infoCardTopPx,
                initialInfoCardTopPx = initialInfoCardTopPx,
                toolbarGapPx = toolbarGapPx,
                isWideAdaptiveLayout = isWideAdaptiveLayout,
                disableInWideLayout = true,
            )
        }
    }
    val toolbarTitleProgressProvider = remember(
        scrollState,
        landscapeLeftScrollState,
        toolbarGapPx,
        isWideAdaptiveLayout,
    ) {
        {
            calculateDetailsScrollProgress(
                scrollValue = scrollState.value,
                landscapeScrollValue = landscapeLeftScrollState.value,
                toolbarBottomPx = toolbarBottomPx,
                infoCardTopPx = infoCardTopPx,
                initialInfoCardTopPx = initialInfoCardTopPx,
                toolbarGapPx = toolbarGapPx,
                isWideAdaptiveLayout = isWideAdaptiveLayout,
                disableInWideLayout = false,
            )
        }
    }
    val syncInfoCardTop: (Float) -> Unit = remember {
        { top ->
            infoCardTopPx = top
            if (top.isFinite() && (!initialInfoCardTopPx.isFinite() || top > initialInfoCardTopPx)) {
                initialInfoCardTopPx = top
            }
        }
    }
    val compactSheetExpansionProgress = detailsPaneState.expansionProgress
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
    val panoramaExtraHeightDp = panoramaPrefs.extraHeight.coerceAtLeast(0).dp
    val detailsHeaderTopSpacing = overlayTopBarInset + if (panoramaPrefs.isEnabled) panoramaExtraHeightDp else 0.dp
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
    val headerCoverVisualAlpha = animatedHeaderCoverVisualAlpha

    val clearChapterSearch: () -> Unit = remember(detailsPaneState, viewModel) {
        {
            detailsPaneState.clearChapterQuery {
                viewModel.performChapterSearch(null)
            }
        }
    }
    val handleBackPress = remember(isWideAdaptiveLayout, compactPaneAnchor, detailsPaneState, clearChapterSearch, onBackClick) {
        {
            if (isWideAdaptiveLayout) {
                onBackClick()
            } else {
                detailsPaneState.handleBack(
                    onBackClick = onBackClick,
                    onChapterSearchClosed = clearChapterSearch,
                )
            }
        }
    }

    val shouldInterceptPaneBack = !isWideAdaptiveLayout && detailsPaneState.shouldHandleBack
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PredictiveBackHandler(enabled = shouldInterceptPaneBack) { progress ->
            try {
                progress.collect { }
                handleBackPress()
            } catch (_: CancellationException) {
                Unit
            }
        }
    } else {
        BackHandler(enabled = shouldInterceptPaneBack) {
            handleBackPress()
        }
    }

    LaunchedEffect(sheetTabSelection, isCompactPaneFullyExpanded) {
        detailsPaneState.syncChapterSearchContext(
            selectedTabId = sheetTabSelection,
            chaptersTabId = DETAILS_TAB_CHAPTERS,
            isSheetFullyExpanded = isCompactPaneFullyExpanded,
            onClosed = clearChapterSearch,
        )
    }

    val updateChapterQuery: (String) -> Unit = remember(detailsPaneState, viewModel) {
        { query ->
            detailsPaneState.updateChapterQuery(query) { searchQuery ->
                viewModel.performChapterSearch(searchQuery)
            }
        }
    }
    val updatePageGridSize: (Float) -> Unit = remember(detailsPaneState, settings) {
        { value ->
            detailsPaneState.updatePageGridSizeValue(value) { updatedValue ->
                settings.gridSizePages = updatedValue.toInt()
            }
        }
    }
    val toggleChapterSearch: () -> Unit = remember(detailsPaneState, clearChapterSearch) {
        {
            detailsPaneState.toggleChapterSearch(onClosed = clearChapterSearch)
        }
    }
    val persistSelectedPaneTab: (Int) -> Unit = remember(detailsPaneState, availableTabIds, settings) {
        { requestedTabId ->
            detailsPaneState.selectTab(
                requestedTabId = requestedTabId,
                availableTabIds = availableTabIds,
                onPersist = { resolvedTab ->
                    settings.lastDetailsTab = resolvedTab
                },
            )
        }
    }

    val openPaneTab: (Int) -> Unit = remember(
        isWideAdaptiveLayout,
        compactPaneAnchor,
        persistSelectedPaneTab,
    ) {
        { requestedTabId ->
            persistSelectedPaneTab(requestedTabId)
            if (!isWideAdaptiveLayout) {
                detailsPaneState.onOpenPaneRequested()
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
                if (panoramaPrefs.isEnabled) {
                    val panoramaCoverUrl = mangaDetails?.coverUrl?.takeIf { it.isNotBlank() }
                        ?: content?.largeCoverUrl?.takeIf { it.isNotBlank() }
                        ?: content?.coverUrl?.takeIf { it.isNotBlank() }
                    val request = remember(content?.source?.name, content?.url, panoramaCoverUrl) {
                        ImageRequest.Builder(context)
                            .data(panoramaCoverUrl)
                            .apply { content?.let { mangaExtra(it) } }
                            .build()
                    }
                    AnimatedPanoramaBackdrop(
                        prefs = panoramaPrefs,
                        model = request,
                        contentAlpha = 0.6f,
                        contentAlphaProvider = {
                            0.6f * (1f - compactCollapseProgressProvider())
                        },
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        crossfadeEnabled = sharedElementKey == null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            val commonTopBar: @Composable () -> Unit = {
            val topBarContainerColor = MaterialTheme.colorScheme.surface
            TopAppBar(
                title = {
                    Text(
                        text = toolbarTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer {
                            alpha = if (toolbarTitleProgressProvider() > 0.92f) 1f else 0f
                        },
                    )
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
                        isStatsAvailable = isStatsAvailable,
                        hasMetadataBrowserTarget = metadataBrowserTarget != null,
                        hasLocalBrowserTarget = localBrowserTarget != null,
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
                                DetailsAction.OpenStatistics -> {
                                    showStatsDialog = true
                                }
                                else -> handleActionClick(action)
                            }
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                modifier = Modifier
                    .drawBehind {
                        drawRect(
                            color = topBarContainerColor.copy(
                                alpha = 0.94f * compactCollapseProgressProvider(),
                            ),
                        )
                    }
                    .onGloballyPositioned { coordinates ->
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
                    KototoroPullToRefreshBox(
                        isRefreshing = isLoading,
                        onRefresh = { viewModel.reload() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        DetailsScrollableContent(
                            modifier = Modifier.fillMaxSize(),
                            scrollState = landscapeLeftScrollState,
                            contentPadding = paddingValues,
                            headerTopSpacing = if (panoramaPrefs.isEnabled) panoramaExtraHeightDp else 0.dp,
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
                            supplementalMetadataProperties = supplementalMetadataProperties,
                            supplementalSections = supplementalSections,
                            supplementalActions = supplementalActions,
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
                            collapseProgressProvider = remember { { 0f } },
                            coverVisualAlpha = 1f,
                            coverUrl = mangaDetails?.coverUrl?.takeIf { it.isNotBlank() } ?: content?.coverUrl,
                            fallbackCoverUrl = content?.coverUrl,
                            showCommentsAction = supplementalCommentThreads.isNotEmpty(),
                            showReviewsAction = supplementalReviews.isNotEmpty(),
                            content = content,
                            sharedElementKey = sharedElementKey,
                            pendingTagSearch = { pendingTagSearch = it },
                            pendingAuthorSearch = { author, source ->
                                pendingAuthorSearch = PendingAuthorSearch(author = author, source = source)
                            },
							onInfoCardTopSync = syncInfoCardTop,
							onFavoriteClick = { showFavoriteDialog = true },
							onCommentsClick = { showCommentsDialog = true },
							onReviewsClick = { showReviewsDialog = true },
                            onSupplementalRelationClick = { item ->
                                when {
                                    shouldOpenTrackingRelationSheet(item) -> {
                                        selectedSupplementalRelationItem = item
                                    }
                                    !item.url.isNullOrBlank() -> {
                                        handleActionClick(DetailsAction.OpenWebUrl(item.url))
                                    }
                                }
                            },
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
                        detailsPaneState = detailsPaneState,
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
                        isChapterSearchAvailable = chapterEmptyReason == null,
                        isChaptersReversed = isChaptersReversed,
                        isChaptersInGridView = isChaptersInGridView,
                        isDownloadedOnly = isDownloadedOnly,
                        isDownloadedFilterVisible = mangaDetails?.local != null,
                        pageGridSizeValue = pageGridSizeValue,
                        onChapterQueryChange = updateChapterQuery,
                        onChapterSearchToggle = toggleChapterSearch,
                        onToggleChaptersReversed = { viewModel.setChaptersReversed(!isChaptersReversed) },
                        onToggleChaptersGrid = { viewModel.setChaptersInGridView(!isChaptersInGridView) },
                        onToggleDownloadedOnly = { viewModel.isDownloadedOnly.value = !isDownloadedOnly },
                        onPageGridSizeChange = updatePageGridSize,
                        showCollapsedHandle = false,
                        onSelectedTabIdChange = persistSelectedPaneTab,
                        onActionClick = handleActionClick,
                    )
                }
            }
            } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        detailsPaneState.onHostHeightChanged(size.height.toFloat())
                    },
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { paddingValues ->
                    KototoroPullToRefreshBox(
                        isRefreshing = isLoading,
                        onRefresh = { viewModel.reload() },
                        modifier = Modifier
                            .fillMaxSize(),
                    ) {
                        DetailsScrollableContent(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = (1f - compactSheetExpansionProgress).coerceIn(0f, 1f)
                                },
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
                            supplementalMetadataProperties = supplementalMetadataProperties,
                            supplementalSections = supplementalSections,
                            supplementalActions = supplementalActions,
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
                            collapseProgressProvider = compactCollapseProgressProvider,
                            coverVisualAlpha = headerCoverVisualAlpha,
                            coverUrl = mangaDetails?.coverUrl?.takeIf { it.isNotBlank() } ?: content?.coverUrl,
                            fallbackCoverUrl = content?.coverUrl,
                            showCommentsAction = supplementalCommentThreads.isNotEmpty(),
                            showReviewsAction = supplementalReviews.isNotEmpty(),
                            content = content,
                            sharedElementKey = sharedElementKey,
                            pendingTagSearch = { pendingTagSearch = it },
                            pendingAuthorSearch = { author, source ->
                                pendingAuthorSearch = PendingAuthorSearch(author = author, source = source)
                            },
							onInfoCardTopSync = syncInfoCardTop,
							onFavoriteClick = { showFavoriteDialog = true },
							onCommentsClick = { showCommentsDialog = true },
							onReviewsClick = { showReviewsDialog = true },
                            onSupplementalRelationClick = { item ->
                                when {
                                    shouldOpenTrackingRelationSheet(item) -> {
                                        selectedSupplementalRelationItem = item
                                    }
                                    !item.url.isNullOrBlank() -> {
                                        handleActionClick(DetailsAction.OpenWebUrl(item.url))
                                    }
                                }
                            },
							onSelectActiveLocalSource = viewModel::selectActiveLocalSource,
                            onSelectMetadataSource = viewModel::selectMetadataSource,
                            onOpenMetadataSourceSheet = { showMetadataSourceDialog = true },
							onOpenReadingSourceSheet = { showReadingSourceDialog = true },
							onEntityClick = appRouter::openEntityDetails,
							onActionClick = handleActionClick,
						)
                    }
                }
                DetailsPaneHost(
                    state = detailsPaneState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    DetailsPaneContent(
                        detailsPaneState = detailsPaneState,
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
                        isChapterSearchAvailable = chapterEmptyReason == null,
                        isChaptersReversed = isChaptersReversed,
                        isChaptersInGridView = isChaptersInGridView,
                        isDownloadedOnly = isDownloadedOnly,
                        isDownloadedFilterVisible = mangaDetails?.local != null,
                        pageGridSizeValue = pageGridSizeValue,
                        onChapterQueryChange = updateChapterQuery,
                        onChapterSearchToggle = toggleChapterSearch,
                        onToggleChaptersReversed = { viewModel.setChaptersReversed(!isChaptersReversed) },
                        onToggleChaptersGrid = { viewModel.setChaptersInGridView(!isChaptersInGridView) },
                        onToggleDownloadedOnly = { viewModel.isDownloadedOnly.value = !isDownloadedOnly },
                        onPageGridSizeChange = updatePageGridSize,
                        showCollapsedHandle = true,
                        onSelectedTabIdChange = persistSelectedPaneTab,
                        onActionClick = handleActionClick,
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
            val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
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
                onOpenDownloads = appRouter::openDownloads,
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

            if (showMetadataSourceDialog) {
                MetadataSourceSheet(
                    currentOptions = metadataSourceOptions,
                    selectedOption = metadataSourceOptions.firstOrNull { it.isSelected },
                    searchServices = metadataSearchServices,
                    authorizedServices = authorizedTrackingServices,
                    searchQuery = metadataSearchQuery,
                    searchSections = metadataSearchSections,
                    isLoading = metadataSearchLoading,
                    hasSearched = metadataSearchHasSearched,
                    unavailableText = stringResource(R.string.details_reading_source_unavailable),
                    onDismissRequest = { showMetadataSourceDialog = false },
                    onSelectOption = viewModel::selectMetadataSource,
                    onSearchQueryChange = viewModel::updateMetadataSearchQuery,
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
                    searchQuery = readingSearchQuery,
                    searchSections = readingSearchSections,
                    isLoading = readingSearchLoading,
                    hasSearched = readingSearchHasSearched,
                    unavailableText = stringResource(R.string.details_reading_source_unavailable),
                    onSelectOption = { option -> option.targetMangaId?.let(viewModel::selectActiveLocalSource) },
                    onSearchQueryChange = viewModel::updateReadingSearchQuery,
                    onSearch = viewModel::searchReadingBindings,
                    onResultClick = { candidate ->
                        viewModel.bindReadingCandidateToTracking(candidate) {
                            appRouter.openDetails(candidate)
                        }
                    },
                    onDismissRequest = { showReadingSourceDialog = false },
                )
            }

            if (showCommentsDialog) {
                TrackingCommentsSheet(
                    threads = supplementalCommentThreads,
                    externalUrl = supplementalCommentsUrl,
                    onDismissRequest = { showCommentsDialog = false },
                    onOpenExternal = { url ->
                        showCommentsDialog = false
                        handleActionClick(DetailsAction.OpenWebUrl(url))
                    },
                )
            }

            if (showReviewsDialog) {
                TrackingReviewsSheet(
                    reviews = supplementalReviews,
                    externalUrl = supplementalReviewsUrl,
                    onDismissRequest = { showReviewsDialog = false },
                    onOpenExternal = { url ->
                        showReviewsDialog = false
                        handleActionClick(DetailsAction.OpenWebUrl(url))
                    },
                )
            }

            selectedSupplementalRelationItem?.let { item ->
                TrackingRelationItemSheet(
                    item = item,
                    onDismissRequest = { selectedSupplementalRelationItem = null },
                    onOpenExternal = { url ->
                        selectedSupplementalRelationItem = null
                        handleActionClick(DetailsAction.OpenWebUrl(url))
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackingRelationItemSheet(
    item: EntityRelationItem,
    onDismissRequest: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val context = LocalContext.current
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    modifier = Modifier
                        .width(112.dp)
                        .aspectRatio(0.72f),
                    shape = RoundedCornerShape(20.dp),
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
                                    painter = rememberSafePainter(R.drawable.ic_placeholder),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(item.coverUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = rememberSafePainter(R.drawable.ic_placeholder),
                                placeholder = rememberSafePainter(R.drawable.ic_placeholder),
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    item.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    item.supportingText?.takeIf { it.isNotBlank() }?.let { supportingText ->
                        Text(
                            text = supportingText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item.subtitle?.takeIf { it.isNotBlank() }?.let { role ->
                TrackingRelationMetaBlock(
                    label = stringResource(R.string.details_character_role_label),
                    value = role,
                )
            }

            item.detailLines
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = "\n")
                ?.let { voiceActors ->
                    TrackingRelationMetaBlock(
                        label = stringResource(R.string.details_character_voice_actors_label),
                        value = voiceActors,
                    )
                }

            item.url?.takeIf { it.isNotBlank() }?.let { url ->
                Button(
                    onClick = { onOpenExternal(url) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = rememberSafePainter(R.drawable.ic_open_external),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(text = stringResource(R.string.details_open_character_site))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackingRelationMetaBlock(
    label: String,
    value: String,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        style = GlassDefaults.subtleStyle(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
    supplementalMetadataProperties: List<Pair<String, String>>,
    supplementalSections: List<EntityRelationSection>,
    supplementalActions: List<DetailsSupplementAction>,
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
    collapseProgressProvider: () -> Float,
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
    onInfoCardTopSync: (Float) -> Unit,
    onFavoriteClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onReviewsClick: () -> Unit,
    onSupplementalRelationClick: (EntityRelationItem) -> Unit,
    onSelectActiveLocalSource: (Long) -> Unit,
    onSelectMetadataSource: (DetailsSourceOption) -> Unit,
    onOpenMetadataSourceSheet: () -> Unit,
    onOpenReadingSourceSheet: () -> Unit,
    onEntityClick: (Long) -> Unit,
    onActionClick: (DetailsAction) -> Unit,
    sharedElementKey: String? = null,
) {
    val context = LocalContext.current
    val source = content?.source
    val visibleSupplementalSections = remember(supplementalSections, entityRelationSections) {
        val hasEntityCharacterSection = entityRelationSections.any { it.titleRes == R.string.entity_graph_section_characters }
        if (hasEntityCharacterSection) {
            supplementalSections.filterNot { it.titleRes == R.string.entity_graph_section_characters }
        } else {
            supplementalSections
        }
    }
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
            supplementalActions = supplementalActions,
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
            collapseProgressProvider = collapseProgressProvider,
            coverVisualAlpha = coverVisualAlpha,
            coverUrl = coverUrl,
            fallbackCoverUrl = fallbackCoverUrl,
            sharedElementKey = sharedElementKey,
            showCommentsAction = showCommentsAction,
            showReviewsAction = showReviewsAction,

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
            onOpenSupplementalAction = { action ->
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
        if (supplementalMetadataProperties.isNotEmpty()) {
            DetailsSupplementMetadataCard(properties = supplementalMetadataProperties)
        }
        if (visibleSupplementalSections.isNotEmpty()) {
            DetailsRelationSections(
                sections = visibleSupplementalSections,
                onItemClick = { item ->
                    val service = item.trackingService
                    val remoteId = item.remoteId
                    when {
                        service != null && remoteId != null -> {
                            onActionClick(DetailsAction.OpenTrackingDetails(service, remoteId, item.url))
                        }
                        shouldOpenTrackingRelationSheet(item) -> {
                            onSupplementalRelationClick(item)
                        }
                        !item.url.isNullOrBlank() -> {
                            onSupplementalRelationClick(item)
                        }
                    }
                },
            )
        }
        if (entityRelationSections.isNotEmpty()) {
            DetailsRelationSections(
                sections = entityRelationSections,
                onItemClick = { item ->
                    item.entityId?.let(onEntityClick)
                },
            )
        }
        Spacer(modifier = Modifier.height(bottomSpacerHeight))
    }
}

private fun shouldOpenTrackingRelationSheet(item: EntityRelationItem): Boolean {
    return item.trackingService == null &&
        item.remoteId == null &&
        !item.url.isNullOrBlank() &&
        (!item.subtitle.isNullOrBlank() || !item.supportingText.isNullOrBlank() || item.detailLines.isNotEmpty())
}

@Composable
private fun DetailsPaneContent(
    detailsPaneState: DetailsPaneState,
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
    isChapterSearchAvailable: Boolean,
    isChaptersReversed: Boolean,
    isChaptersInGridView: Boolean,
    isDownloadedOnly: Boolean,
    isDownloadedFilterVisible: Boolean,
    pageGridSizeValue: Float,
    onChapterQueryChange: (String) -> Unit,
    onChapterSearchToggle: () -> Unit,
    onToggleChaptersReversed: () -> Unit,
    onToggleChaptersGrid: () -> Unit,
    onToggleDownloadedOnly: () -> Unit,
    onPageGridSizeChange: (Float) -> Unit,
    showCollapsedHandle: Boolean,
    onSelectedTabIdChange: (Int) -> Unit,
    onActionClick: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chapterQuery = detailsPaneState.chapterQuery
    val isChapterSearchVisible = detailsPaneState.isChapterSearchVisible
    val paneOpacityProgress = easedOpacityProgress(sheetExpansionProgress)
    val actionsExpansionProgress by animateFloatAsState(
        targetValue = if (isSheetFullyExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "detailsPaneActionsExpansion",
    )
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val colorScheme = MaterialTheme.colorScheme
    val isCollapsedPane = showCollapsedHandle && detailsPaneState.anchor == CompactDetailsPaneAnchor.Collapsed
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val useCompactPaneSurfaceTint = showCollapsedHandle
    val paneShadowElevation = if (useCompactPaneSurfaceTint) 0.dp else 2.dp
    val paneBorderColor = colorScheme.outlineVariant.copy(
        alpha = if (useCompactPaneSurfaceTint) {
            if (isDarkTheme) 0.10f else 0.08f
        } else if (isDarkTheme) {
            0.12f
        } else {
            0.08f
        },
    )
    val paneTopBaseColor = if (isDarkTheme) {
        colorScheme.surfaceContainerHigh
    } else {
        colorScheme.surface
    }
    val paneBottomBaseColor = if (isDarkTheme) {
        colorScheme.surfaceContainer
    } else {
        colorScheme.surfaceContainerLow
    }
    val paneTopAlpha = when {
        !showCollapsedHandle -> if (isDarkTheme) 0.76f else 0.16f
        useCompactPaneSurfaceTint && isCollapsedPane -> if (isDarkTheme) 0.92f else 0.90f
        useCompactPaneSurfaceTint && isSheetFullyExpanded -> if (isDarkTheme) 0.88f else 0.84f
        useCompactPaneSurfaceTint -> if (isDarkTheme) 0.84f else 0.80f
        isCollapsedPane -> if (isDarkTheme) 0.82f else 0.20f
        isSheetFullyExpanded -> if (isDarkTheme) {
            lerpFloat(0.68f, 0.74f, paneOpacityProgress)
        } else {
            lerpFloat(0.12f, 0.18f, paneOpacityProgress)
        }
        else -> if (isDarkTheme) {
            lerpFloat(0.56f, 0.68f, paneOpacityProgress)
        } else {
            lerpFloat(0.08f, 0.16f, paneOpacityProgress)
        }
    }
    val paneTopGradientColor = paneTopBaseColor.copy(alpha = paneTopAlpha)
    val paneMiddleGradientColor = paneTopBaseColor.copy(
        alpha = paneTopAlpha * if (useCompactPaneSurfaceTint) {
            if (isDarkTheme) 0.97f else 0.95f
        } else if (isDarkTheme) {
            0.92f
        } else {
            0.82f
        },
    )
    val paneBottomGradientColor = paneBottomBaseColor.copy(
        alpha = paneTopAlpha * if (useCompactPaneSurfaceTint) {
            if (isDarkTheme) 0.94f else 0.90f
        } else if (isDarkTheme) {
            0.80f
        } else {
            0.58f
        },
    )
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
            shape = paneShape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = paneShadowElevation,
            border = if (paneBorderColor.alpha > 0f) {
                BorderStroke(width = 1.dp, color = paneBorderColor)
            } else {
                null
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                paneTopGradientColor,
                                paneMiddleGradientColor,
                                paneBottomGradientColor,
                            ),
                        ),
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    DetailsPaneActionsRow(
                        detailsPaneState = detailsPaneState,
                        selectedTabId = resolveDetailsTabSelection(selectedTabId, availableTabIds),
                        isSheetFullyExpanded = isSheetFullyExpanded,
                        sheetExpansionProgress = actionsExpansionProgress,
                        isChapterSearchAvailable = isChapterSearchAvailable,
                        isChaptersReversed = isChaptersReversed,
                        isChaptersInGridView = isChaptersInGridView,
                        isDownloadedOnly = isDownloadedOnly,
                        isDownloadedFilterVisible = isDownloadedFilterVisible,
                        pageGridSizeValue = pageGridSizeValue,
                        onChapterSearchToggle = onChapterSearchToggle,
                        onToggleChaptersReversed = onToggleChaptersReversed,
                        onToggleChaptersGrid = onToggleChaptersGrid,
                        onToggleDownloadedOnly = onToggleDownloadedOnly,
                        onPageGridSizeChange = onPageGridSizeChange,
                        showCollapsedHandle = showCollapsedHandle,
                        handleTopInset = statusBarTopPadding,
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
                            handleSelectionBackPressInternally = !showCollapsedHandle,
                            detailsPaneState = if (showCollapsedHandle) detailsPaneState else null,
                            chapterQuery = chapterQuery,
                            isChapterSearchVisible = isChapterSearchVisible,
                            onChapterQueryChange = onChapterQueryChange,
                            onChapterSelectionStateChange = detailsPaneState::onChapterSelectionStateChanged,
                            onSelectedTabIdChange = { tabId ->
                                val resolvedTab = resolveDetailsTabSelection(tabId, availableTabIds)
                                onSelectedTabIdChange(resolvedTab)
                            },
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
    detailsPaneState: DetailsPaneState,
    selectedTabId: Int,
    isSheetFullyExpanded: Boolean,
    sheetExpansionProgress: Float,
    isChapterSearchAvailable: Boolean,
    isChaptersReversed: Boolean,
    isChaptersInGridView: Boolean,
    isDownloadedOnly: Boolean,
    isDownloadedFilterVisible: Boolean,
    pageGridSizeValue: Float,
    onChapterSearchToggle: () -> Unit,
    onToggleChaptersReversed: () -> Unit,
    onToggleChaptersGrid: () -> Unit,
    onToggleDownloadedOnly: () -> Unit,
    onPageGridSizeChange: (Float) -> Unit,
    showCollapsedHandle: Boolean,
    handleTopInset: androidx.compose.ui.unit.Dp,
    contentType: ContentType?,
    historyInfo: HistoryInfo,
    branches: List<ContentBranch>,
    isLoading: Boolean,
    onActionClick: (DetailsAction) -> Unit,
) {
    val isChapterSearchVisible = detailsPaneState.isChapterSearchVisible
    val chapterSelectionState = detailsPaneState.chapterSelectionState
    val paneOpacityProgress = easedOpacityProgress(sheetExpansionProgress)
    val showPagesTab = contentType != ContentType.VIDEO &&
        contentType != ContentType.HENTAI_VIDEO &&
        contentType != ContentType.NOVEL &&
        contentType != ContentType.HENTAI_NOVEL
    val showBookmarksTab = contentType != ContentType.VIDEO &&
        contentType != ContentType.HENTAI_VIDEO

    LaunchedEffect(selectedTabId, isSheetFullyExpanded) {
        detailsPaneState.syncTopBarContext(
            selectedTabId = selectedTabId,
            chaptersTabId = DETAILS_TAB_CHAPTERS,
            isSheetFullyExpanded = isSheetFullyExpanded,
        )
    }
    val topBarMode = detailsPaneState.topBarMode(
        selectedTabId = selectedTabId,
        chaptersTabId = DETAILS_TAB_CHAPTERS,
    )

    Column(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .anchoredDraggable(
                state = detailsPaneState.anchoredState,
                orientation = Orientation.Vertical,
                enabled = detailsPaneState.anchor == CompactDetailsPaneAnchor.Full &&
                    !detailsPaneState.isGridSizeControlsVisible,
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        if (showCollapsedHandle) {
            val collapsedHandleHeight = 18.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(collapsedHandleHeight),
                contentAlignment = Alignment.Center,
            ) {
                DetailsPaneDragHandle(
                    modifier = Modifier
                        .alpha(lerpFloat(0.68f, 1f, paneOpacityProgress)),
                )
            }
        }
        when (topBarMode) {
            DetailsPaneTopBarMode.ChapterSelection -> {
                ChapterSelectionTopBar(
                    state = chapterSelectionState ?: return@Column,
                    modifier = Modifier.fillMaxWidth(),
                )
                return@Column
            }

            DetailsPaneTopBarMode.GridSizeControls -> {
                PageGridSizeControlsRow(
                    value = pageGridSizeValue,
                    onValueChange = onPageGridSizeChange,
                    onBackClick = detailsPaneState::hideGridSizeControls,
                    modifier = Modifier.fillMaxWidth(),
                )
                return@Column
            }

            else -> Unit
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
                    .padding(horizontal = 2.dp),
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
                when (topBarMode) {
                    DetailsPaneTopBarMode.ExpandedChapterTools -> {
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
                    }

                    DetailsPaneTopBarMode.ExpandedGridTools -> {
                        Spacer(modifier = Modifier.weight(1f))
                        DetailsChromeButton(
                            onClick = detailsPaneState::showGridSizeControls,
                        ) {
                            Icon(
                                painter = rememberSafePainter(R.drawable.ic_size_large),
                                contentDescription = stringResource(R.string.grid_size),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    else -> {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterSelectionTopBar(
    state: ChapterSelectionUiState,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            Text(text = state.selectedCount.toString())
        },
        navigationIcon = {
            IconButton(onClick = state.onClearSelection) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                )
            }
        },
        actions = {
            if (state.canSelectAll) {
                IconButton(onClick = state.onSelectAll) {
                    Icon(
                        painter = rememberSafePainter(R.drawable.ic_select_all),
                        contentDescription = stringResource(android.R.string.selectAll),
                    )
                }
            }
            if (state.canDownload) {
                IconButton(onClick = state.onDownload) {
                    Icon(
                        painter = rememberSafePainter(R.drawable.ic_download),
                        contentDescription = stringResource(R.string.download),
                    )
                }
            }
            if (state.canDelete) {
                IconButton(onClick = state.onDelete) {
                    Icon(
                        painter = rememberSafePainter(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.delete),
                    )
                }
            }
            if (state.canBookmark) {
                IconButton(onClick = state.onBookmark) {
                    Icon(
                        painter = rememberSafePainter(R.drawable.ic_bookmark),
                        contentDescription = stringResource(R.string.bookmarks),
                    )
                }
            }
            if (state.canMarkCurrent) {
                IconButton(onClick = state.onMarkCurrent) {
                    Icon(
                        painter = rememberSafePainter(R.drawable.ic_current_chapter),
                        contentDescription = stringResource(R.string.mark_as_current),
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    )
}

@Composable
private fun PageGridSizeControlsRow(
    value: Float,
    onValueChange: (Float) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 50f..150f,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
    }
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
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.extraSmall,
        )
 {
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
        modifier = modifier.height(50.dp),
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
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            Text(
                text = readLabel,
                style = MaterialTheme.typography.labelLarge,
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
                    .width(50.dp)
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
                shape = MaterialTheme.shapes.extraSmall,
            )
 {
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

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction.coerceIn(0f, 1f)
}

private fun calculateDetailsScrollProgress(
    scrollValue: Int,
    landscapeScrollValue: Int,
    toolbarBottomPx: Float,
    infoCardTopPx: Float,
    initialInfoCardTopPx: Float,
    toolbarGapPx: Float,
    isWideAdaptiveLayout: Boolean,
    disableInWideLayout: Boolean,
): Float {
    if (disableInWideLayout && isWideAdaptiveLayout) {
        return 0f
    }
    val targetTop = toolbarBottomPx + toolbarGapPx
    return if (toolbarBottomPx.isFinite() && infoCardTopPx.isFinite() && initialInfoCardTopPx.isFinite()) {
        val travelDistance = (initialInfoCardTopPx - targetTop).coerceAtLeast(1f)
        ((initialInfoCardTopPx - infoCardTopPx) / travelDistance).coerceIn(0f, 1f)
    } else {
        val fallbackScroll = if (isWideAdaptiveLayout) landscapeScrollValue else scrollValue
        (fallbackScroll / 360f).coerceIn(0f, 1f)
    }
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
    isStatsAvailable: Boolean,
    hasMetadataBrowserTarget: Boolean,
    hasLocalBrowserTarget: Boolean,
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
            shape = MaterialTheme.shapes.extraSmall,
        )
 {
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
fun DetailsRelationSections(
    sections: List<EntityRelationSection>,
    onItemClick: (EntityRelationItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        sections.forEach { section ->
            DetailsRelationSectionContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                EntityRelationSectionHeader(section = section)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(
                        items = section.items,
                        key = { it.stableKey },
                    ) { item ->
                        EntityRelationCard(item = item, onClick = { onItemClick(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsSupplementMetadataCard(
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
        section.titleRes?.let { titleRes ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            ) {
                Icon(
                    painter = rememberSafePainter(entityRelationSectionIconRes(titleRes)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(18.dp),
                )
            }
        }
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
}

@Composable
fun EntityRelationCard(
    item: EntityRelationItem,
    onClick: () -> Unit,
) {
    val type = item.type
    val typeLabel = type?.let { stringResource(entityRelationTypeLabelRes(it)) }
    val typeIconRes = type?.let { entityRelationTypeIconRes(it) }
    val opensExternalPage = type == null && item.trackingService == null && item.remoteId == null && !item.url.isNullOrBlank()
    DetailsRelationItemCard(
        width = if (type != null) 148.dp else 132.dp,
        title = item.name,
        subtitle = item.subtitle,
        supportingText = item.supportingText,
        onClick = onClick,
        footer = if (typeLabel != null && typeIconRes != null) {
            {
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
        } else if (opensExternalPage) {
            {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = rememberSafePainter(R.drawable.ic_open_external),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.open_website),
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
        } else {
            null
        },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (type != null) 0.76f else 0.72f),
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
                            painter = rememberSafePainter(typeIconRes ?: R.drawable.ic_placeholder),
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
                if (typeLabel != null && typeIconRes != null) {
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
        }
    }
}

@Composable
private fun DetailsRelationSectionContainer(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        style = GlassDefaults.subtleStyle(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun DetailsRelationItemCard(
    width: androidx.compose.ui.unit.Dp,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    supportingText: String? = null,
    footer: (@Composable () -> Unit)? = null,
    cover: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .width(width)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = GlassDefaults.nestedCardColor(),
        border = BorderStroke(1.dp, GlassDefaults.nestedCardBorderColor()),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            cover()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                supportingText?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                footer?.invoke()
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
