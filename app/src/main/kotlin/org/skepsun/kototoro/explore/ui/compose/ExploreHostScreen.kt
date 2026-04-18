package org.skepsun.kototoro.explore.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.compose.HeroBackdropCard
import org.skepsun.kototoro.core.ui.compose.HeroBackdropScrim
import org.skepsun.kototoro.discover.ui.DiscoverViewModel
import org.skepsun.kototoro.discover.ui.compose.DiscoverHeroCarousel
import org.skepsun.kototoro.discover.ui.model.DiscoverCarouselRow
import org.skepsun.kototoro.explore.ui.ExploreViewModel
import org.skepsun.kototoro.explore.ui.model.ContentSourceItem
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.LoadingState

/**
 * Unified Discover page: combines content sources and tracking site discovery
 * into a single vertically-scrolling card-based layout.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
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
                    )
                }
            }

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
                        modifier = Modifier.padding(horizontal = 12.dp),
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
                        modifier = Modifier.padding(horizontal = 12.dp),
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
            FlowRow(
                modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                sources.take(20).forEach { source ->
                    val title = source.source.getTitle(context)
                    Surface(
                        modifier = Modifier.clickable { onSourceClick(source) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 2.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_storage),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
@Composable
private fun TrackingCategoryCarouselCard(
    title: String,
    items: List<ContentListModel>,
    onItemClick: (ContentListModel) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val context = LocalContext.current
    val backgroundItem = items.first()
    val backgroundRequest = remember(backgroundItem.coverUrl, backgroundItem.id) {
        ImageRequest.Builder(context)
            .data(backgroundItem.coverUrl)
            .crossfade(true)
            .build()
    }

    HeroBackdropCard(
        modifier = modifier,
        minHeight = 0.dp,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
        elevation = 2.dp,
        background = {
            AsyncImage(
                model = backgroundRequest,
                contentDescription = null,
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
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onMoreClick) {
                    Text(stringResource(R.string.more))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(
                    items = items,
                    key = { it.id },
                ) { item ->
                    TrackingCompactPoster(
                        item = item,
                        onClick = { onItemClick(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackingCompactPoster(
    item: ContentListModel,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val imageRequest = remember(item.coverUrl, item.id) {
        ImageRequest.Builder(context)
            .data(item.coverUrl)
            .crossfade(true)
            .build()
    }

    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 100.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
