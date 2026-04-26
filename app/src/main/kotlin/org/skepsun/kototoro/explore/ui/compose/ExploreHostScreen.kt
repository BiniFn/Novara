package org.skepsun.kototoro.explore.ui.compose

import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest.Builder
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.flow.distinctUntilChanged
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.ContentSourceInfo
import org.skepsun.kototoro.core.model.getLocale
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.model.unwrap
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.compose.ContentSourceIcon
import org.skepsun.kototoro.core.ui.compose.HorizontalRailAnimatedVisibility
import org.skepsun.kototoro.core.ui.compose.rememberRailAnimationFactor
import org.skepsun.kototoro.core.ui.compose.KototoroPullToRefreshBox
import org.skepsun.kototoro.core.ui.compose.LocalNavAnimatedVisibilityScope
import org.skepsun.kototoro.core.ui.compose.LocalSharedTransitionScope
import org.skepsun.kototoro.core.ui.compose.VerticalRailAnimatedVisibility
import org.skepsun.kototoro.core.ui.compose.compactPosterRailCardStyle
import org.skepsun.kototoro.core.ui.compose.contentCoverSharedKey
import org.skepsun.kototoro.core.ui.compose.rememberHorizontalRailScrollIntensity
import org.skepsun.kototoro.core.ui.compose.rememberVerticalRailScrollIntensity
import org.skepsun.kototoro.core.ui.compose.rememberSafePainter
import org.skepsun.kototoro.core.ui.compose.unclippedBoundsInWindow
import org.skepsun.kototoro.core.ui.image.panoramaBlur
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.details.ui.model.DetailsOrigin
import org.skepsun.kototoro.discover.ui.DiscoverViewModel
import org.skepsun.kototoro.discover.ui.compose.DiscoverHeroCarousel
import org.skepsun.kototoro.discover.ui.model.DiscoverCarouselRow
import org.skepsun.kototoro.explore.ui.ExploreViewModel
import org.skepsun.kototoro.explore.ui.model.ContentSourceItem
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.core.parser.external.ExternalContentSource
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.parsers.model.ContentType
import java.util.Locale

private const val BrowseLoadMoreBuffer = 4
private val BrowseHeroContentOverlap = 56.dp

private data class SourceQuickAccessMetrics(
    val columns: Int,
    val cardHeight: androidx.compose.ui.unit.Dp,
    val gridSpacing: androidx.compose.ui.unit.Dp,
    val iconContainerSize: androidx.compose.ui.unit.Dp,
    val iconSize: androidx.compose.ui.unit.Dp,
)

@Immutable
private data class ExploreScreenPrefs(
    val gridScale: Float,
    val isSourcesGroupedByLanguage: Boolean,
)

private data class SourceOriginBadgeInfo(
    @DrawableRes val iconRes: Int,
)

private data class SourceQuickAccessGroup(
    val title: String?,
    val sources: List<ContentSourceItem>,
)

private data class BrowseSourceItems(
    val sources: List<ContentSourceItem>,
    val selectedSources: List<ContentSourceInfo>,
)

private fun prepareBrowseSourceItems(
    items: List<ListModel>,
    selectedSourceIds: Set<Long>,
): BrowseSourceItems {
    val sources = ArrayList<ContentSourceItem>()
    val selectedSources = ArrayList<ContentSourceInfo>()
    items.forEach { item ->
        if (item is ContentSourceItem) {
            sources += item
            if (item.id in selectedSourceIds) {
                selectedSources += item.source
            }
        }
    }
    return BrowseSourceItems(
        sources = sources,
        selectedSources = selectedSources,
    )
}

private data class BrowseShowcaseRow(
    val row: DiscoverCarouselRow,
    val items: List<ContentListModel>,
)

private data class BrowseDiscoverItems(
    val heroRow: DiscoverCarouselRow?,
    val heroItems: List<ContentListModel>,
    val showcaseRows: List<BrowseShowcaseRow>,
    val popularItems: List<ContentListModel>,
    val isLoadingOnly: Boolean,
)

private fun prepareBrowseDiscoverItems(items: List<ListModel>): BrowseDiscoverItems {
    val carouselRows = ArrayList<DiscoverCarouselRow>()
    var isLoadingOnly = items.size <= 1
    if (isLoadingOnly) {
        isLoadingOnly = items.any { it is LoadingState }
    }
    items.forEach { item ->
        if (item is DiscoverCarouselRow) {
            carouselRows += item
        }
    }

    var heroRow: DiscoverCarouselRow? = null
    var heroItems: List<ContentListModel> = emptyList()
    val showcaseRows = ArrayList<BrowseShowcaseRow>()
    val popularItems = ArrayList<ContentListModel>()
    val popularIds = HashSet<Long>()

    carouselRows.forEach { row ->
        val rowItems = row.items
            .asSequence()
            .filterIsInstance<ContentListModel>()
            .toList()
        if (heroRow == null && rowItems.isNotEmpty()) {
            heroRow = row
            heroItems = rowItems.take(6)
        } else {
            if (showcaseRows.size < 4 && rowItems.isNotEmpty()) {
                showcaseRows += BrowseShowcaseRow(row = row, items = rowItems.take(12))
            }
            rowItems.forEach { item ->
                if (popularIds.add(item.id)) {
                    popularItems += item
                }
            }
        }
    }

    return BrowseDiscoverItems(
        heroRow = heroRow,
        heroItems = heroItems,
        showcaseRows = showcaseRows,
        popularItems = popularItems,
        isLoadingOnly = isLoadingOnly,
    )
}

private fun sourceQuickAccessMetrics(gridScale: Float): SourceQuickAccessMetrics {
    val normalized = ((gridScale.coerceIn(0.75f, 1.4f) - 0.75f) / (1.4f - 0.75f)).coerceIn(0f, 1f)
    val interpolatedColumns = 5f + ((3f - 5f) * normalized)
    return SourceQuickAccessMetrics(
        columns = interpolatedColumns.toInt().coerceIn(3, 5),
        cardHeight = lerp(96.dp, 84.dp, normalized),
        gridSpacing = lerp(8.dp, 6.dp, normalized),
        iconContainerSize = lerp(44.dp, 36.dp, normalized),
        iconSize = lerp(34.dp, 28.dp, normalized),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun KototoroExploreHostRoute(
    appRouter: AppRouter,
    contentPadding: PaddingValues,
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    discoverViewModel: DiscoverViewModel = hiltViewModel(),
    onSourceSelectionTopBarChanged: (ExploreSourceSelectionTopBarState?) -> Unit = {},
    onNavigateToDetails: ((DetailsOrigin, String?) -> Unit)? = null,
) {
    val sourceItems by exploreViewModel.content.collectAsStateWithLifecycle(emptyList())
    val discoverItems by discoverViewModel.content.collectAsStateWithLifecycle(emptyList())
    val isDiscoverLoading by discoverViewModel.isLoading.collectAsStateWithLifecycle(initialValue = false)
    val availableServices by discoverViewModel.availableServices.collectAsStateWithLifecycle()
    val activeService by discoverViewModel.activeService.collectAsStateWithLifecycle()
    val query by discoverViewModel.query.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val verticalScrollIntensity = rememberVerticalRailScrollIntensity(listState)
    var heroPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    val settings = remember(context.applicationContext) { AppSettings(context.applicationContext) }
    val screenPrefs by settings.observeAsState(
        AppSettings.KEY_GRID_SIZE,
        AppSettings.KEY_SOURCES_GROUPED_BY_LANGUAGE,
    ) {
        ExploreScreenPrefs(
            gridScale = gridSize / 100f,
            isSourcesGroupedByLanguage = isSourcesGroupedByLanguage,
        )
    }
    val gridScale = screenPrefs.gridScale
    val isSourcesGroupedByLanguage = screenPrefs.isSourcesGroupedByLanguage
    val posterStyle = remember(gridScale) { compactPosterRailCardStyle(gridScale) }
    // 实时读取 LazyColumn 第一个 item 的滚动偏移，驱动 Hero 跟随滚动
    val heroScrollOffsetPx by remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                -listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                -heroPx.toFloat()
            }
        }
    }

    var selectedSourceIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    val browseSourceItems = remember(sourceItems, selectedSourceIds) {
        prepareBrowseSourceItems(sourceItems, selectedSourceIds)
    }
    val sources = browseSourceItems.sources
    val browseDiscoverItems = remember(discoverItems) { prepareBrowseDiscoverItems(discoverItems) }
    val heroRow = browseDiscoverItems.heroRow
    val heroItems = browseDiscoverItems.heroItems
    val showcaseRows = browseDiscoverItems.showcaseRows
    val popularItems = browseDiscoverItems.popularItems
    val isLoadingOnly = browseDiscoverItems.isLoadingOnly
    val heroOverlapDp = if (sources.isNotEmpty() || isLoadingOnly) BrowseHeroContentOverlap else 0.dp
    val heroHeightDp by remember(heroPx, density, heroOverlapDp) {
        derivedStateOf {
            with(density) {
                (heroPx - heroOverlapDp.roundToPx()).coerceAtLeast(0).toDp()
            }
        }
    }
    val selectedSources = browseSourceItems.selectedSources

    BackHandler(enabled = selectedSourceIds.isNotEmpty()) {
        selectedSourceIds = emptySet()
    }

    SideEffect {
        if (selectedSourceIds.isNotEmpty()) {
            val isSingleSelection = selectedSources.size == 1
            val canPin = selectedSources.isNotEmpty() && selectedSources.all { !it.isPinned }
            val canUnpin = selectedSources.isNotEmpty() && selectedSources.all { it.isPinned }
            val canDisable = selectedSources.isNotEmpty() && !exploreViewModel.isAllSourcesEnabled.value && selectedSources.all {
                val unwrapped = it.mangaSource.unwrap()
                !unwrapped.isLocal && unwrapped !is ExternalContentSource
            }
            val canDelete = selectedSources.isNotEmpty() && selectedSources.all { it.mangaSource is ExternalContentSource }

            onSourceSelectionTopBarChanged(
                ExploreSourceSelectionTopBarState(
                    selectedCount = selectedSourceIds.size,
                    isSingleSelection = isSingleSelection,
                    canPin = canPin,
                    canUnpin = canUnpin,
                    canDisable = canDisable,
                    canDelete = canDelete,
                    onClearSelection = { selectedSourceIds = emptySet() },
                    onSettings = {
                        selectedSources.singleOrNull()?.let { appRouter.openSourceSettings(it) }
                        selectedSourceIds = emptySet()
                    },
                    onDisable = {
                        exploreViewModel.disableSources(selectedSources)
                        selectedSourceIds = emptySet()
                    },
                    onDelete = {
                        selectedSources.forEach { item ->
                            (item.mangaSource as? ExternalContentSource)?.let { source ->
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_DELETE,
                                    android.net.Uri.parse("package:${source.packageName}"),
                                )
                                activity?.startActivity(intent)
                            }
                        }
                        selectedSourceIds = emptySet()
                    },
                    onShortcut = {
                        selectedSources.singleOrNull()?.let { exploreViewModel.requestPinShortcut(it) }
                        selectedSourceIds = emptySet()
                    },
                    onPin = {
                        exploreViewModel.setSourcesPinned(selectedSources, isPinned = true)
                        selectedSourceIds = emptySet()
                    },
                    onUnpin = {
                        exploreViewModel.setSourcesPinned(selectedSources, isPinned = false)
                        selectedSourceIds = emptySet()
                    },
                ),
            )
        } else {
            onSourceSelectionTopBarChanged(null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onSourceSelectionTopBarChanged(null)
        }
    }

    val currentDiscoverLoading = androidx.compose.runtime.rememberUpdatedState(isDiscoverLoading)
    LaunchedEffect(listState, query, popularItems.size) {
        if (query.isNotBlank() || popularItems.isEmpty()) {
            return@LaunchedEffect
        }
        listState.maybeTriggerBrowseLoadMore(
            itemCount = popularItems.size,
            isLoading = { currentDiscoverLoading.value },
            onLoadMore = discoverViewModel::loadNextPage,
        )
    }

    KototoroPullToRefreshBox(
        isRefreshing = isDiscoverLoading && !isLoadingOnly,
        onRefresh = { discoverViewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ===== 内容流 =====
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = 0.dp,
                    bottom = contentPadding.calculateBottomPadding() + 120.dp,
                ),
                verticalArrangement = Arrangement.Top,
                modifier = Modifier.fillMaxSize(),
            ) {
                // 占位 Spacer，给 Hero Overlay 留空间
                item(key = "discover_hero_spacer") {
                    Spacer(modifier = Modifier.height(heroHeightDp))
                }

                if (sources.isNotEmpty() || isLoadingOnly) {
                    item(key = "discover_sources") {
                        DetachedBottomContent(
                            sources = sources,
                            isLoadingOnly = isLoadingOnly,
                            metrics = sourceQuickAccessMetrics(gridScale),
                            isGroupedByLanguage = isSourcesGroupedByLanguage,
                            selectedSourceIds = selectedSourceIds,
                            onSourceClick = { source ->
                                if (selectedSourceIds.isNotEmpty()) {
                                    selectedSourceIds = selectedSourceIds.toggle(source.id)
                                } else {
                                    appRouter.openList(source.source, null, null)
                                }
                            },
                            onSourceLongClick = { source ->
                                selectedSourceIds = selectedSourceIds.toggle(source.id)
                            },
                            onManageSourcesClick = appRouter::openManageSources,
                            topBackgroundOverlap = heroOverlapDp,
                        )
                    }
                }

                items(
                    items = showcaseRows,
                    key = { "showcase_${it.row.category.id}" },
                    contentType = { "showcase_row" },
                ) { showcaseRow ->
                    val row = showcaseRow.row
                    if (showcaseRow.items.isNotEmpty()) {
                        TrackingCategoryRow(
                            title = stringResource(row.category.nameResId),
                            items = showcaseRow.items,
                            posterStyle = posterStyle,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                            onItemClick = { item ->
                                openTrackingItem(
                                    appRouter = appRouter,
                                    discoverViewModel = discoverViewModel,
                                    availableServices = availableServices,
                                    item = item,
                                    sharedElementKey = contentCoverSharedKey(
                                        item.manga.source.name,
                                        item.manga.coverUrl.orEmpty(),
                                        instanceKey = "explore_showcase_${row.category.id}_${item.id}",
                                    ),
                                    onNavigateToDetails = onNavigateToDetails,
                                )
                            },
                            onMoreClick = {
                                activeService?.let { service ->
                                    appRouter.openTrackingDiscoveryCategory(service, row.category.id, row.category.nameResId)
                                }
                            },
                        )
                    }
                }

                if (popularItems.isNotEmpty()) {
                    item(key = "popular_header") {
                        BrowsePopularHeader(
                            title = stringResource(R.string.popular),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                        )
                    }
                    itemsIndexed(
                        items = popularItems,
                        key = { _, item -> "popular_${item.id}" },
                        contentType = { _, _ -> "popular_item" },
                    ) { index, item ->
                        VerticalRailAnimatedVisibility(
                            animationKey = "explore_popular_${item.id}",
                            index = index + showcaseRows.size + 1,
                            listState = listState,
                            scaleFactor = 0f,
                            scrollIntensity = verticalScrollIntensity,
                        ) { animatedModifier ->
                            val sharedElementKey = contentCoverSharedKey(
                                item.manga.source.name,
                                item.manga.coverUrl.orEmpty(),
                                instanceKey = "explore_popular_${item.id}",
                            )
                            BrowsePopularListItem(
                                item = item,
                                posterStyle = posterStyle,
                                sharedElementKey = sharedElementKey,
                                modifier = animatedModifier.padding(horizontal = 16.dp, vertical = 5.dp),
                                onClick = {
                                    openTrackingItem(
                                        appRouter = appRouter,
                                        discoverViewModel = discoverViewModel,
                                        availableServices = availableServices,
                                        item = item,
                                        sharedElementKey = sharedElementKey,
                                        onNavigateToDetails = onNavigateToDetails,
                                    )
                                },
                            )
                        }
                    }
                }

                if (isDiscoverLoading && popularItems.isNotEmpty()) {
                    item(key = "popular_loading") {
                        BrowsePopularLoadingSection(
                            posterStyle = posterStyle,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            // ===== Hero Overlay（跟随滚动，无 spacing 污染）=====
            BrowseHeroBlock(
                title = heroRow?.category?.let { stringResource(it.nameResId) }
                    ?: stringResource(R.string.discover),
                heroItems = heroItems,
                activeService = activeService,
                availableServices = availableServices,
                isLoadingOnly = isLoadingOnly,
                topContentInset = contentPadding.calculateTopPadding(),
                settings = settings,
                onSelectService = discoverViewModel::selectService,
                onHeroItemClick = { item, sharedElementKey ->
                    openTrackingItem(
                        appRouter = appRouter,
                        discoverViewModel = discoverViewModel,
                        availableServices = availableServices,
                        item = item,
                        sharedElementKey = sharedElementKey,
                        onNavigateToDetails = onNavigateToDetails,
                    )
                },
                sharedElementKeyForItem = { item, _ ->
                    contentCoverSharedKey(
                        item.manga.source.name,
                        item.manga.coverUrl.orEmpty(),
                        instanceKey = "explore_hero_${item.id}",
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .onSizeChanged { heroPx = it.height }
                    .graphicsLayer { translationY = heroScrollOffsetPx },
            )

        }
    }
}

private suspend fun LazyListState.maybeTriggerBrowseLoadMore(
    itemCount: Int,
    isLoading: () -> Boolean,
    onLoadMore: () -> Unit,
) {
    snapshotFlow { layoutInfo.visibleItemsInfo.lastOrNull()?.index }
        .distinctUntilChanged()
        .collect { lastVisibleIndex: Int? ->
            if (lastVisibleIndex != null && !isLoading() && lastVisibleIndex >= itemCount - BrowseLoadMoreBuffer) {
                onLoadMore()
            }
        }
}

private fun openTrackingItem(
    appRouter: AppRouter,
    discoverViewModel: DiscoverViewModel,
    availableServices: List<ScrobblerService>,
    item: ContentListModel,
    sharedElementKey: String? = null,
    onNavigateToDetails: ((DetailsOrigin, String?) -> Unit)? = null,
) {
    val serviceName = item.manga.source.name.removePrefix("TRACKING_")
    val trackingService = availableServices.find { it.name == serviceName } ?: return
    if (discoverViewModel.supportsDetails(trackingService)) {
        if (onNavigateToDetails != null) {
            onNavigateToDetails(
                DetailsOrigin.TrackingItem(
                    serviceId = trackingService.id.toString(),
                    remoteId = item.manga.id,
                    url = item.manga.publicUrl,
                ),
                sharedElementKey,
            )
        } else {
            appRouter.openTrackingSiteDetails(trackingService, item.manga.id, item.manga.publicUrl)
        }
    } else {
        val url = item.manga.url ?: item.manga.publicUrl
        if (!url.isNullOrBlank()) {
            appRouter.openExternalBrowser(url)
        }
    }
}

@Composable
private fun BrowseHeroBlock(
    title: String,
    heroItems: List<ContentListModel>,
    activeService: ScrobblerService?,
    availableServices: List<ScrobblerService>,
    isLoadingOnly: Boolean,
    topContentInset: androidx.compose.ui.unit.Dp,
    settings: AppSettings,
    onSelectService: (ScrobblerService) -> Unit,
    onHeroItemClick: (ContentListModel, String) -> Unit,
    sharedElementKeyForItem: (ContentListModel, Int) -> String,
    modifier: Modifier = Modifier,
) {
    if (heroItems.isNotEmpty()) {
        DiscoverHeroCarousel(
            title = title,
            items = heroItems,
            activeService = activeService,
            availableServices = availableServices,
            onSelectService = onSelectService,
            onItemClick = { item, _, sharedElementKey -> onHeroItemClick(item, sharedElementKey) },
            topContentInset = topContentInset,
            detachedBottomContent = true,
            settings = settings,
            sharedElementKeyForItem = sharedElementKeyForItem,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(topContentInset + 220.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                )
                .padding(top = topContentInset + 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoadingOnly) {
                BrowseHeroSkeleton()
            }
        }
    }
}

@Composable
private fun DetachedBottomContent(
    sources: List<ContentSourceItem>,
    isLoadingOnly: Boolean,
    metrics: SourceQuickAccessMetrics,
    isGroupedByLanguage: Boolean,
    selectedSourceIds: Set<Long>,
    onSourceClick: (ContentSourceItem) -> Unit,
    onSourceLongClick: (ContentSourceItem) -> Unit,
    onManageSourcesClick: () -> Unit,
    topBackgroundOverlap: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            sources.isNotEmpty() -> {
                SourcesQuickAccessSection(
                    sources = sources,
                    metrics = metrics,
                    isGroupedByLanguage = isGroupedByLanguage,
                    selectedSourceIds = selectedSourceIds,
                    onSourceClick = onSourceClick,
                    onSourceLongClick = onSourceLongClick,
                    onManageClick = onManageSourcesClick,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = topBackgroundOverlap,
                        bottom = 8.dp,
                    ),
                )
            }
            isLoadingOnly -> {
                BrowseSourcesSkeleton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = topBackgroundOverlap + 36.dp,
                            bottom = 36.dp,
                        ),
                    metrics = metrics,
                )
            }
        }
    }
}

@Composable
private fun SourcesQuickAccessSection(
    sources: List<ContentSourceItem>,
    metrics: SourceQuickAccessMetrics,
    isGroupedByLanguage: Boolean,
    selectedSourceIds: Set<Long>,
    onSourceClick: (ContentSourceItem) -> Unit,
    onSourceLongClick: (ContentSourceItem) -> Unit,
    onManageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_storage),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.explore_tab_sources),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onManageClick, modifier = Modifier.size(34.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.manage),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            var isExpanded by rememberSaveable(sources.size) { mutableStateOf(false) }
            val columns = remember(metrics) {
                metrics.columns
            }
            val collapsedRowCount = if (maxWidth < 520.dp) 5 else 4
            val collapsedVisibleCount = columns * collapsedRowCount
            val groupedSources = remember(sources, isGroupedByLanguage, context) {
                sources.toQuickAccessGroups(
                    isGroupedByLanguage = isGroupedByLanguage,
                    context = context,
                )
            }
            val visibleGroups = remember(groupedSources, collapsedVisibleCount, isExpanded) {
                groupedSources.takeVisibleSourceGroups(
                    maxSources = if (isExpanded) Int.MAX_VALUE else collapsedVisibleCount,
                )
            }
            val hasMoreSources = sources.size > collapsedVisibleCount

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                visibleGroups.forEach { group ->
                    group.title?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 2.dp),
                        )
                    }
                    SourceQuickAccessGrid(
                        metrics = metrics,
                        columns = columns,
                        sources = group.sources,
                        selectedSourceIds = selectedSourceIds,
                        onSourceClick = onSourceClick,
                        onSourceLongClick = onSourceLongClick,
                    )
                }
                if (hasMoreSources) {
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = if (isExpanded) {
                                stringResource(R.string.show_less)
                            } else {
                                "${stringResource(R.string.show_more)} (${sources.size - collapsedVisibleCount})"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceQuickAccessGrid(
    metrics: SourceQuickAccessMetrics,
    columns: Int,
    sources: List<ContentSourceItem>,
    selectedSourceIds: Set<Long>,
    onSourceClick: (ContentSourceItem) -> Unit,
    onSourceLongClick: (ContentSourceItem) -> Unit,
) {
    val rows = remember(sources, columns) { sources.chunked(columns) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(metrics.gridSpacing),
    ) {
        rows.forEach { rowSources ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.gridSpacing),
            ) {
                rowSources.forEach { source ->
                    Box(modifier = Modifier.weight(1f)) {
                        SourceQuickAccessCard(
                            metrics = metrics,
                            source = source,
                            isSelected = source.id in selectedSourceIds,
                            onClick = { onSourceClick(source) },
                            onLongClick = { onSourceLongClick(source) },
                        )
                    }
                }
                repeat(columns - rowSources.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SourceQuickAccessCard(
    metrics: SourceQuickAccessMetrics,
    source: ContentSourceItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val actualSource = source.source.mangaSource
    val title = actualSource.getTitle(context)
    val originBadge = remember(actualSource.name) {
        sourceOriginBadgeInfo(actualSource.name)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.cardHeight)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.background
        },
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(metrics.iconContainerSize)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .background(
                        sourceTypeAccent(actualSource.contentType).copy(
                            alpha = if (isSelected) 0.32f else 0.18f,
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                ContentSourceIcon(
                    source = source.source,
                    modifier = Modifier.size(metrics.iconSize),
                    contentDescription = title,
                )
                if (originBadge != null) {
                    SourceOriginBadge(
                        badge = originBadge,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 13.sp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SourceOriginBadge(
    badge: SourceOriginBadgeInfo,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                shape = androidx.compose.foundation.shape.CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = rememberSafePainter(badge.iconRes),
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> {
    return if (id in this) this - id else this + id
}

@Composable
private fun sourceTypeAccent(contentType: ContentType): Color = when (contentType) {
    ContentType.VIDEO -> MaterialTheme.colorScheme.tertiary
    ContentType.NOVEL -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

private fun sourceOriginBadgeInfo(sourceName: String): SourceOriginBadgeInfo? = when {
    sourceName.startsWith("MIHON_") -> SourceOriginBadgeInfo(R.drawable.ic_source_mihon)
    sourceName.startsWith("ANIYOMI_") -> SourceOriginBadgeInfo(R.drawable.ic_source_aniyomi)
    sourceName.startsWith("JSON_LEGADO_") -> SourceOriginBadgeInfo(R.drawable.ic_source_legado)
    sourceName.startsWith("JSON_TVBOX_") -> SourceOriginBadgeInfo(R.drawable.ic_source_tvbox)
    sourceName.startsWith("IREADER_") -> SourceOriginBadgeInfo(R.drawable.ic_source_ireader)
    sourceName.startsWith("JSON_LNREADER_") -> SourceOriginBadgeInfo(R.drawable.ic_source_lnreader)
    else -> null
}

private fun List<ContentSourceItem>.toQuickAccessGroups(
    isGroupedByLanguage: Boolean,
    context: android.content.Context,
): List<SourceQuickAccessGroup> {
    if (isEmpty()) {
        return emptyList()
    }
    if (!isGroupedByLanguage) {
        return listOf(SourceQuickAccessGroup(title = null, sources = this))
    }
    val result = ArrayList<SourceQuickAccessGroup>()
    val (pinned, unpinned) = partition { it.source.isPinned }
    if (pinned.isNotEmpty()) {
        result += SourceQuickAccessGroup(
            title = context.getString(R.string.source_pinned),
            sources = pinned,
        )
    }
    val grouped = unpinned
        .groupBy { sourceItem ->
            sourceItem.source.mangaSource.getLocale()
                ?.getDisplayName(Locale.getDefault())
                ?.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
                }
                ?: context.getString(R.string.other)
        }
        .toSortedMap()
    grouped.forEach { (language, sourcesInLanguage) ->
        if (sourcesInLanguage.isNotEmpty()) {
            result += SourceQuickAccessGroup(
                title = language,
                sources = sourcesInLanguage,
            )
        }
    }
    return result
}

private fun List<SourceQuickAccessGroup>.takeVisibleSourceGroups(
    maxSources: Int,
): List<SourceQuickAccessGroup> {
    if (maxSources == Int.MAX_VALUE) {
        return this
    }
    var remaining = maxSources
    val result = ArrayList<SourceQuickAccessGroup>(size)
    for (group in this) {
        if (remaining <= 0) break
        val visibleSources = group.sources.take(remaining)
        if (visibleSources.isNotEmpty()) {
            result += group.copy(sources = visibleSources)
            remaining -= visibleSources.size
        }
    }
    return result
}

@Composable
private fun TrackingCategoryRow(
    title: String,
    items: List<ContentListModel>,
    posterStyle: org.skepsun.kototoro.core.ui.compose.CompactPosterCardStyle,
    onItemClick: (ContentListModel) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val rowState = rememberLazyListState()
    val scrollIntensity = rememberHorizontalRailScrollIntensity(rowState)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onMoreClick) {
                Text(stringResource(R.string.more))
            }
        }
        val railAnimationFactor = rememberRailAnimationFactor()
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.id },
            ) { index, item ->
                HorizontalRailAnimatedVisibility(
                    animationKey = "explore_${title}_${item.id}",
                    index = index,
                    listState = rowState,
                    scrollIntensity = scrollIntensity,
                    animationFactor = railAnimationFactor,
                    enableScrollLinkedAnimation = false,
                ) { animatedModifier ->
                    TrackingCompactPoster(
                        item = item,
                        posterStyle = posterStyle,
                        sharedElementKey = contentCoverSharedKey(
                            item.manga.source.name,
                            item.manga.coverUrl.orEmpty(),
                            instanceKey = "explore_row_${title}_${item.id}_$index",
                        ),
                        onClick = { onItemClick(item) },
                        modifier = animatedModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowsePopularHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun BrowsePopularListItem(
    item: ContentListModel,
    posterStyle: org.skepsun.kototoro.core.ui.compose.CompactPosterCardStyle,
    sharedElementKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val backgroundRequest = remember(item.coverUrl, item.id) {
        buildExploreCoverRequest(
            context = context,
            coverUrl = item.coverUrl,
            content = item.manga,
            size = 150,
            blurPercent = 58,
        )
    }
    val posterRequest = remember(item.coverUrl, item.id) {
        buildExploreCoverRequest(
            context = context,
            coverUrl = item.coverUrl,
            content = item.manga,
            size = 320,
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
        tonalElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp),
        ) {
            AsyncImage(
                model = backgroundRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.58f),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.14f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.90f),
                            ),
                        ),
                    )
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
                            ),
                        ),
                    ),
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(posterStyle.itemWidth)
                        .height(posterStyle.posterHeight)
                        .then(
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedElement(
                                        rememberSharedContentState(key = sharedElementKey),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    )
                                }
                            } else Modifier
                        )
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(posterStyle.cornerRadius))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    AsyncImage(
                        model = posterRequest,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
                    ) {
                        Text(
                            text = item.source.getTitle(context),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun TrackingCompactPoster(
    item: ContentListModel,
    posterStyle: org.skepsun.kototoro.core.ui.compose.CompactPosterCardStyle,
    sharedElementKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val imageRequest = remember(item.coverUrl, item.id) {
        buildExploreCoverRequest(
            context = context,
            coverUrl = item.coverUrl,
            content = item.manga,
            size = 320,
        )
    }

    Column(
        modifier = modifier
            .width(posterStyle.itemWidth)
            .height(posterStyle.posterHeight + 32.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(posterStyle.itemWidth)
                .height(posterStyle.posterHeight)
                .then(
                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                rememberSharedContentState(key = sharedElementKey),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        }
                    } else Modifier
                )
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(posterStyle.cornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BrowseHeroSkeleton(
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExploreSkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                ExploreSkeletonBlock(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp),
                )
            }
        }
    }
}

@Composable
private fun BrowseSourcesSkeleton(
    metrics: SourceQuickAccessMetrics,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(metrics.gridSpacing),
    ) {
        ExploreSkeletonBlock(
            modifier = Modifier
                .width(148.dp)
                .height(18.dp),
        )
        repeat(2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.gridSpacing),
            ) {
                repeat(metrics.columns.coerceAtMost(4)) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ExploreSkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(metrics.cardHeight),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowsePopularLoadingSection(
    posterStyle: org.skepsun.kototoro.core.ui.compose.CompactPosterCardStyle,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(2) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExploreSkeletonBlock(
                        modifier = Modifier
                            .width(posterStyle.itemWidth)
                            .height(posterStyle.posterHeight),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ExploreSkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .height(16.dp),
                        )
                        ExploreSkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.52f)
                                .height(14.dp),
                        )
                        ExploreSkeletonBlock(
                            modifier = Modifier
                                .width(88.dp)
                                .height(28.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreSkeletonBlock(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
    )
}

private fun buildExploreCoverRequest(
    context: android.content.Context,
    coverUrl: String?,
    content: org.skepsun.kototoro.parsers.model.Content,
    size: Int? = null,
    blurPercent: Int = 0,
): ImageRequest {
    val builder = ImageRequest.Builder(context)
        .data(normalizeExploreCoverUrl(coverUrl))
        .mangaExtra(content)
        .crossfade(true)
        .panoramaBlur(blurPercent)
    if (size != null) {
        builder.size(size)
    }
    return builder.build()
}

private fun normalizeExploreCoverUrl(url: String?): String? = when {
    url == null -> null
    url.startsWith("//") -> "https:$url"
    else -> url
}
