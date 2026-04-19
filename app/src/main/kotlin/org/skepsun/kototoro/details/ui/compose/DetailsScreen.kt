package org.skepsun.kototoro.details.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
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
import org.skepsun.kototoro.core.ui.compose.rememberResolvedSourceTitle
import org.skepsun.kototoro.core.util.ext.isHttpUrl
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.details.ui.DetailsViewModel
import org.skepsun.kototoro.details.ui.model.ContentBranch
import org.skepsun.kototoro.details.ui.model.HistoryInfo
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
    val isStatsAvailable by viewModel.isStatsAvailable.collectAsState()
    val trackingSuggestion by viewModel.trackingMatchSuggestion.collectAsState()
    val linkedTrackingItems by viewModel.linkedTrackingItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val context = LocalContext.current
    val panoramaBlur by settings.observeAsState(AppSettings.KEY_PANORAMA_BLUR) { panoramaCoverBlur }
    val panoramaExtraHeight by settings.observeAsState(AppSettings.KEY_PANORAMA_EXTRA_HEIGHT) { panoramaCoverExtraHeight }
    val panoramaBottomAlpha by settings.observeAsState(AppSettings.KEY_PANORAMA_BOTTOM_GRADIENT_ALPHA) {
        panoramaBottomGradientAlpha
    }
    val content = mangaDetails?.toContent()
    val contentType = content?.source?.getContentType()
    val isShortcutSupported = remember(context) { ShortcutManagerCompat.isRequestPinShortcutSupported(context) }
    val scrollState = rememberScrollState()
    var showDeleteLocalDialog by remember { mutableStateOf(false) }
    var showShareOptions by remember { mutableStateOf(false) }
    var pendingAuthorSearch by remember { mutableStateOf<PendingAuthorSearch?>(null) }
    var pendingTagSearch by remember { mutableStateOf<ContentTag?>(null) }
    var showFavoriteDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showScrobblingDialog by remember { mutableStateOf(false) }
    val availableTabIds = remember(contentType, settings.isPagesTabEnabled) {
        resolveAvailableDetailsTabIds(contentType, settings)
    }
    var showPaneSheet by remember { mutableStateOf(false) }
    var selectedPaneTabId by remember {
        mutableStateOf(resolveDetailsTabSelection(settings.defaultDetailsTab, availableTabIds))
    }
    val sheetTabSelection = remember(selectedPaneTabId, availableTabIds) {
        resolveDetailsTabSelection(selectedPaneTabId, availableTabIds)
    }
    val density = LocalDensity.current
    val snackbarHostState = remember { SnackbarHostState() }
    val toolbarGapPx = with(density) { 12.dp.toPx() }
    var toolbarBottomPx by remember { mutableFloatStateOf(Float.NaN) }
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
    val collapseProgress by remember(
        scrollState,
        toolbarBottomPx,
        infoCardTopPx,
        initialInfoCardTopPx,
        toolbarGapPx,
    ) {
        derivedStateOf {
            val targetTop = toolbarBottomPx + toolbarGapPx
            if (toolbarBottomPx.isFinite() && infoCardTopPx.isFinite() && initialInfoCardTopPx.isFinite()) {
                val travelDistance = (initialInfoCardTopPx - targetTop).coerceAtLeast(1f)
                ((initialInfoCardTopPx - infoCardTopPx) / travelDistance).coerceIn(0f, 1f)
            } else {
                (scrollState.value / 360f).coerceIn(0f, 1f)
            }
        }
    }
    val toolbarTitle = translatedTitle ?: content?.title.orEmpty()
    val handleActionClick: (DetailsAction) -> Unit = remember(onActionClick, availableTabIds) {
        { action ->
            when (action) {
                DetailsAction.ToggleList -> {
                    selectedPaneTabId = DETAILS_TAB_CHAPTERS
                    showPaneSheet = true
                }
                DetailsAction.ToggleGrid -> {
                    selectedPaneTabId = resolveDetailsTabSelection(DETAILS_TAB_PAGES, availableTabIds)
                    showPaneSheet = true
                }
                DetailsAction.ToggleBookmarkView -> {
                    selectedPaneTabId = resolveDetailsTabSelection(DETAILS_TAB_BOOKMARKS, availableTabIds)
                    showPaneSheet = true
                }
                DetailsAction.Download -> {
                    showDownloadDialog = true
                }
                else -> onActionClick(action)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (settings.isPanoramaCoverEnabled) {
            val request = remember(mangaDetails) {
                ImageRequest.Builder(context)
                    .data(mangaDetails?.toContent()?.coverUrl)
                    .apply { mangaDetails?.toContent()?.let { mangaExtra(it) } }
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = (((panoramaBlur ?: 35) / 100f) * 20f).dp)
                    .alpha(0.6f * (1f - collapseProgress)),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(
                                    alpha = ((panoramaBottomAlpha ?: 100) / 100f).coerceIn(0f, 1f),
                                ),
                            ),
                        ),
                    ),
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                DetailsBottomBar(
                    contentType = contentType,
                    historyInfo = historyInfo,
                    branches = branches,
                    isLoading = isLoading,
                    onActionClick = handleActionClick,
                )
            },
            topBar = {
                TopAppBar(
                    title = {
                        if (collapseProgress > 0.92f) {
                            Text(
                                text = toolbarTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        DetailsChromeButton(onClick = onBackClick) {
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
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = stringResource(R.string.download),
                            )
                        }
                        DetailsOverflowMenu(
                            contentTitle = content?.title,
                            hasTranslationCache = hasTranslationCache,
                            isShowingTranslation = isShowingTranslation,
                            isTranslating = isTranslating,
                            isScrobblingAvailable = viewModel.isScrobblingAvailable,
                            isStatsAvailable = isStatsAvailable,
                            isBrowserAvailable = content?.publicUrl?.isHttpUrl() == true,
                            isAlternativesAvailable = content?.isLocal == false,
                            hasOnlineVariant = remoteContent != null,
                            isDeleteLocalAvailable = content?.source == LocalMangaSource,
                            isEditOverrideAvailable = content != null,
                            isShortcutSupported = isShortcutSupported && content != null,
                            isNsfw = content?.isNsfw() == true,
                            onDeleteLocalRequest = { handleActionClick(DetailsAction.DeleteLocal) },
                            onActionClick = { action ->
                                when (action) {
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
                        toolbarBottomPx = coordinates.boundsInRoot().bottom
                    },
                )
            },
        ) { paddingValues ->
            val source = content?.source

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState),
            ) {
                DetailsHeader(
                    mangaDetails = mangaDetails,
                    favouriteCategories = favouriteCategories,
                    historyInfo = historyInfo,
                    linkedTrackingItems = linkedTrackingItems,
                    trackingSuggestion = trackingSuggestion,
                    translatedTitle = translatedTitle,
                    translatedDescription = translatedDescription,
                    isShowingTranslation = isShowingTranslation,
                    hasTranslationCache = hasTranslationCache,
                    isTranslating = isTranslating,
                    collapseProgress = collapseProgress,
                    onCoverBoundsSync = onCoverBoundsSync,
                    onInfoCardTopSync = { top -> infoCardTopPx = top },
                    onCoverClick = { handleActionClick(DetailsAction.OpenCover) },
                    onFavoriteClick = { showFavoriteDialog = true },
                    onSourceClick = { handleActionClick(DetailsAction.OpenSource(it)) },
                    onAuthorClick = { author ->
                        source?.let { currentSource ->
                            pendingAuthorSearch = PendingAuthorSearch(
                                author = author,
                                source = currentSource,
                            )
                        }
                    },
                    onTagClick = { pendingTagSearch = it },
                    onTranslateClick = { handleActionClick(DetailsAction.Translate) },
                    onToggleTranslationClick = { handleActionClick(DetailsAction.ToggleTranslation) },
                    onOpenLinkedTracking = { linked -> handleActionClick(DetailsAction.OpenTrackingDetails(linked.service, linked.remoteId, linked.url)) },
                    onManageLinkedTracking = { linked -> handleActionClick(DetailsAction.ManageTrackingBinding(linked.service, linked.remoteId, linked.title, linked.url)) },
                    onRemoveLinkedTracking = { match -> handleActionClick(DetailsAction.RemoveTrackingMatch(match)) },
                    onBindTrackingSuggestion = { match -> handleActionClick(DetailsAction.BindTrackingMatch(match)) },
                    onManageTrackingSuggestion = { match -> handleActionClick(DetailsAction.ManageTrackingBinding(match.service, match.remoteId, match.title, match.url)) },
                )
                Spacer(modifier = Modifier.height(240.dp))
            }
        }

        if (showPaneSheet) {
            val paneSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showPaneSheet = false },
                sheetState = paneSheetState,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = null,
                modifier = Modifier.fillMaxHeight(0.92f),
            ) {
                ChaptersPagesTabsContent(
                    viewModel = viewModel,
                    pagesViewModel = pagesViewModel,
                    bookmarksViewModel = bookmarksViewModel,
                    settings = settings,
                    appRouter = appRouter,
                    pageSaveHelper = pageSaveHelper,
                    selectedTabId = sheetTabSelection,
                    onSelectedTabIdChange = { tabId ->
                        val resolvedTab = resolveDetailsTabSelection(tabId, availableTabIds)
                        selectedPaneTabId = resolvedTab
                        settings.lastDetailsTab = resolvedTab
                    },
                )
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
                onDismiss = { showDownloadDialog = false },
            )
        }

        if (showStatsDialog && content != null) {
            val statsViewModel: ContentStatsViewModel = hiltViewModel()
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
            ScrobblingSelectorDialog(
                viewModel = scrobblingViewModel,
                onDismissRequest = { showScrobblingDialog = false },
            )
        }
    }
}

@Composable
private fun DetailsBottomBar(
    contentType: ContentType?,
    historyInfo: HistoryInfo,
    branches: List<ContentBranch>,
    isLoading: Boolean,
    onActionClick: (DetailsAction) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            shadowElevation = 18.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                DetailsDockActionButton(
                    iconRes = R.drawable.ic_list,
                    contentDescription = stringResource(R.string.chapters),
                    onClick = { onActionClick(DetailsAction.ToggleList) },
                )
                DetailsDockActionButton(
                    iconRes = R.drawable.ic_grid,
                    contentDescription = stringResource(R.string.pages),
                    onClick = { onActionClick(DetailsAction.ToggleGrid) },
                )
                DetailsDockActionButton(
                    iconRes = R.drawable.ic_bookmark,
                    contentDescription = stringResource(R.string.bookmarks),
                    onClick = { onActionClick(DetailsAction.ToggleBookmarkView) },
                )
                Spacer(modifier = Modifier.weight(1f))
                ReadDock(
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

@Composable
private fun DetailsDockActionButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
        modifier = Modifier.padding(end = 8.dp),
    ) {
        IconButton(onClick = onClick) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
            )
        }
    }
}

sealed interface DetailsAction {
    data object OpenCover : DetailsAction
    data class OpenSource(val source: ContentSource) : DetailsAction
    data class SearchAuthorOnSource(val author: String, val source: ContentSource) : DetailsAction
    data class SearchAuthorEverywhere(val author: String) : DetailsAction
    data class SearchTagOnSource(val tag: ContentTag) : DetailsAction
    data class SearchTagEverywhere(val tagTitle: String) : DetailsAction
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
    data object OpenInBrowser : DetailsAction
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
    data class RemoveTrackingMatch(
        val match: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult,
    ) : DetailsAction
}

@Composable
private fun ReadDock(
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
    val selectedBranch = branches.firstOrNull { it.isSelected } ?: branches.firstOrNull()
    val hasBranchOptions = branches.size > 1
    val canOpenIncognito = !historyInfo.isIncognitoMode
    val canForgetHistory = historyInfo.history != null
    val hasQuickActions = canOpenIncognito || canForgetHistory || isDownloadAvailable
    val hasMenuActions = hasQuickActions || hasBranchOptions
    val menuLabel = if (hasBranchOptions) {
        selectedBranch?.name ?: stringResource(R.string.system_default)
    } else {
        stringResource(R.string.options)
    }

    Surface(
        modifier = Modifier.height(56.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Box {
            Row {
                TextButton(
                    onClick = onReadClick,
                    enabled = isEnabled,
                    modifier = Modifier.height(56.dp),
                ) {
                    Text(
                        text = readLabel,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(1.dp)
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)),
                )
                TextButton(
                    onClick = { expanded = true },
                    enabled = hasMenuActions,
                    modifier = Modifier.height(56.dp),
                ) {
                    Text(
                        text = menuLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.options),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
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
    hasTranslationCache: Boolean,
    isShowingTranslation: Boolean,
    isTranslating: Boolean,
    isScrobblingAvailable: Boolean,
    isStatsAvailable: Boolean,
    isBrowserAvailable: Boolean,
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
            if (isBrowserAvailable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_in_browser)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.OpenInBrowser)
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
                painter = painterResource(iconRes),
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
    Surface(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.18f),
                spotColor = Color.Black.copy(alpha = 0.24f),
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        IconButton(onClick = onClick) {
            content()
        }
    }
}
