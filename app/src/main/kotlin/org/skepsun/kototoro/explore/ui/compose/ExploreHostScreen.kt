package org.skepsun.kototoro.explore.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.compose.HeroAutoAdvanceEffect
import org.skepsun.kototoro.core.ui.compose.HeroBackdropCard
import org.skepsun.kototoro.core.ui.compose.HeroBackdropScrim
import org.skepsun.kototoro.core.ui.compose.HeroPagerIndicator
import org.skepsun.kototoro.discover.ui.DiscoverViewModel
import org.skepsun.kototoro.discover.ui.compose.DiscoverHeroCarousel
import org.skepsun.kototoro.discover.ui.model.DiscoverCarouselRow
import org.skepsun.kototoro.explore.ui.ExploreViewModel
import org.skepsun.kototoro.explore.ui.model.ContentSourceItem
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.list.ui.model.LoadingState
import kotlin.math.absoluteValue

/**
 * Unified Discover page: combines content sources and tracking site discovery
 * into a single vertically-scrolling card-based layout.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun KototoroExploreHostRoute(
    appRouter: AppRouter,
    contentPadding: PaddingValues,
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    discoverViewModel: DiscoverViewModel = hiltViewModel()
) {
    val sourceItems by exploreViewModel.content.collectAsStateWithLifecycle(emptyList())
    val discoverItems by discoverViewModel.content.collectAsStateWithLifecycle(emptyList())
    val isDiscoverLoading by discoverViewModel.isLoading.collectAsStateWithLifecycle(initialValue = false)
    val context = LocalContext.current

    val sources = remember(sourceItems) {
        sourceItems.filterIsInstance<ContentSourceItem>()
    }
    val carouselRows = remember(discoverItems) {
        discoverItems.filterIsInstance<DiscoverCarouselRow>()
    }
    val heroRow = remember(carouselRows) {
        carouselRows.firstOrNull { row -> row.items.any { it is ContentListModel } }
    }
    val heroItems = remember(heroRow) {
        heroRow?.items?.filterIsInstance<ContentListModel>()?.take(6).orEmpty()
    }
    val isLoadingOnly = discoverItems.size <= 1 && discoverItems.any { it is LoadingState }

    if (isLoadingOnly && sources.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    PullToRefreshBox(
        isRefreshing = isDiscoverLoading && !isLoadingOnly,
        onRefresh = { discoverViewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 8.dp,
                start = 12.dp,
                end = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Sources quick-access card
            if (sources.isNotEmpty()) {
                item(key = "sources_card") {
                    SourcesQuickAccessCard(
                        sources = sources,
                        onSourceClick = { source -> appRouter.openList(source.source, null, null) },
                        onManageClick = {
                            if (exploreViewModel.isAllSourcesEnabled.value) {
                                appRouter.openManageSources()
                            } else {
                                appRouter.openSourcesCatalog()
                            }
                        },
                    )
                }
            }

            // Hero carousel (daily picks)
            if (heroItems.isNotEmpty() && heroRow != null) {
                item(key = "discover_hero") {
                    DiscoverHeroCarousel(
                        title = stringResource(heroRow.category.nameResId),
                        items = heroItems,
                        onItemClick = { item ->
                            val serviceName = item.manga.source.name.removePrefix("TRACKING_")
                            val trackingService = discoverViewModel.availableServices.value.find { it.name == serviceName }
                                ?: return@DiscoverHeroCarousel
                            if (discoverViewModel.supportsDetails(trackingService)) {
                                appRouter.openTrackingSiteDetails(trackingService, item.manga.id, item.manga.publicUrl)
                            } else {
                                val url = item.manga.url ?: item.manga.publicUrl
                                if (!url.isNullOrBlank()) {
                                    appRouter.openExternalBrowser(url)
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            // Tracking category cards — each as a HeroBackdropCard carousel
            items(
                items = carouselRows,
                key = { it.category.id }
            ) { row ->
                val rowContentItems = remember(row) {
                    row.items.filterIsInstance<ContentListModel>().take(8)
                }
                if (rowContentItems.isNotEmpty()) {
                    TrackingCategoryCarouselCard(
                        title = stringResource(row.category.nameResId),
                        items = rowContentItems,
                        onItemClick = { item ->
                            val serviceName = item.manga.source.name.removePrefix("TRACKING_")
                            val trackingService = discoverViewModel.availableServices.value.find { it.name == serviceName }
                                ?: return@TrackingCategoryCarouselCard
                            if (discoverViewModel.supportsDetails(trackingService)) {
                                appRouter.openTrackingSiteDetails(trackingService, item.manga.id, item.manga.publicUrl)
                            } else {
                                val url = item.manga.url ?: item.manga.publicUrl
                                if (!url.isNullOrBlank()) {
                                    appRouter.openExternalBrowser(url)
                                }
                            }
                        },
                        onMoreClick = {
                            val service = discoverViewModel.activeService.value
                            if (service != null) {
                                appRouter.openTrackingDiscoveryCategory(service, row.category.id, row.category.nameResId)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * A horizontal-scrolling card showing available content sources as compact chips.
 */
@Composable
private fun SourcesQuickAccessCard(
    sources: List<ContentSourceItem>,
    onSourceClick: (ContentSourceItem) -> Unit,
    onManageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(onClick = onManageClick) {
                    Text(stringResource(R.string.more))
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(
                    items = sources.take(20),
                    key = { it.id },
                ) { source ->
                    val title = source.source.getTitle(context)
                    Surface(
                        modifier = Modifier.clickable { onSourceClick(source) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .width(80.dp)
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_storage),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A tracking category rendered as a HeroBackdropCard with HorizontalPager,
 * matching the visual style of HomeTrackingHeroSection.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackingCategoryCarouselCard(
    title: String,
    items: List<ContentListModel>,
    onItemClick: (ContentListModel) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    val selectedIndex by remember(items, pagerState) {
        derivedStateOf { pagerState.currentPage.coerceIn(0, items.lastIndex) }
    }
    val selectedItem = items[selectedIndex]
    val context = LocalContext.current
    val backgroundRequest = remember(selectedItem.coverUrl, selectedItem.id) {
        ImageRequest.Builder(context)
            .data(selectedItem.coverUrl)
            .crossfade(true)
            .build()
    }

    HeroAutoAdvanceEffect(
        pagerState = pagerState,
        pageCount = items.size,
    )

    HeroBackdropCard(
        modifier = modifier.height(184.dp),
        minHeight = 184.dp,
        shape = RoundedCornerShape(26.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
        elevation = 4.dp,
        background = {
            AsyncImage(
                model = backgroundRequest,
                contentDescription = selectedItem.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(22.dp)
                    .alpha(0.72f),
            )
            HeroBackdropScrim(
                verticalColors = listOf(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                ),
                horizontalColors = listOf(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
                    Color.Transparent,
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.44f),
                ),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onMoreClick) {
                    Text(stringResource(R.string.more))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
            ) {
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 8.dp,
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    TrackingCategoryPoster(
                        item = items[page],
                        pageOffset = pageOffset,
                        onClick = { onItemClick(items[page]) },
                    )
                }
            }
            HeroPagerIndicator(
                pageCount = items.size,
                currentPage = selectedIndex,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun TrackingCategoryPoster(
    item: ContentListModel,
    pageOffset: Float,
    onClick: () -> Unit,
) {
    val offsetFraction = pageOffset.absoluteValue.coerceIn(0f, 1f)
    val posterWidth = lerp(72.dp, 66.dp, offsetFraction)
    val posterHeight = lerp(100.dp, 92.dp, offsetFraction)
    val context = LocalContext.current
    val imageRequest = remember(item.coverUrl, item.id) {
        ImageRequest.Builder(context)
            .data(item.coverUrl)
            .crossfade(true)
            .build()
    }
    val sourceLabel = item.source.getTitle(context)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val scale = 0.94f + ((1f - offsetFraction) * 0.06f)
                scaleX = scale
                scaleY = scale
                alpha = 0.74f + ((1f - offsetFraction) * 0.26f)
                translationX = pageOffset * -18f
            }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(posterWidth)
                    .height(posterHeight)
                    .clip(RoundedCornerShape(22.dp)),
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f)),
                            ),
                        ),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
