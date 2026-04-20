package org.skepsun.kototoro.explore.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import coil3.request.ImageRequest.Builder
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.parser.favicon.faviconUri
import org.skepsun.kototoro.core.ui.image.sourceFallbackImage
import org.skepsun.kototoro.core.util.ext.mangaSourceExtra
import org.skepsun.kototoro.discover.ui.DiscoverViewModel
import org.skepsun.kototoro.discover.ui.compose.DiscoverHeroCarousel
import org.skepsun.kototoro.discover.ui.model.DiscoverCarouselRow
import org.skepsun.kototoro.explore.ui.ExploreViewModel
import org.skepsun.kototoro.explore.ui.model.ContentSourceItem
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService

private const val BrowseLoadMoreBuffer = 4

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun KototoroExploreHostRoute(
    appRouter: AppRouter,
    contentPadding: PaddingValues,
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    discoverViewModel: DiscoverViewModel = hiltViewModel(),
) {
    val sourceItems by exploreViewModel.content.collectAsStateWithLifecycle(emptyList())
    val discoverItems by discoverViewModel.content.collectAsStateWithLifecycle(emptyList())
    val isDiscoverLoading by discoverViewModel.isLoading.collectAsStateWithLifecycle(initialValue = false)
    val availableServices by discoverViewModel.availableServices.collectAsStateWithLifecycle()
    val activeService by discoverViewModel.activeService.collectAsStateWithLifecycle()
    val query by discoverViewModel.query.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

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
    val showcaseRows = remember(carouselRows, heroRow) {
        carouselRows
            .filterNot { row -> row.category.id == heroRow?.category?.id }
            .take(4)
    }
    val popularItems = remember(carouselRows, heroRow) {
        carouselRows
            .filterNot { row -> row.category.id == heroRow?.category?.id }
            .flatMap { row -> row.items.filterIsInstance<ContentListModel>() }
            .distinctBy { it.id }
    }
    val isLoadingOnly = discoverItems.size <= 1 && discoverItems.any { it is LoadingState }

    LaunchedEffect(listState, query, popularItems.size, isDiscoverLoading) {
        if (query.isNotBlank() || popularItems.isEmpty()) {
            return@LaunchedEffect
        }
        listState.maybeTriggerBrowseLoadMore(
            itemCount = popularItems.size,
            isLoading = isDiscoverLoading,
            onLoadMore = discoverViewModel::loadNextPage,
        )
    }

    PullToRefreshBox(
        isRefreshing = isDiscoverLoading && !isLoadingOnly,
        onRefresh = { discoverViewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = contentPadding.calculateBottomPadding() + 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "discover_hero_block") {
                BrowseHeroBlock(
                    title = heroRow?.category?.let { stringResource(it.nameResId) }
                        ?: stringResource(R.string.discover),
                    heroItems = heroItems,
                    sources = sources,
                    activeService = activeService,
                    availableServices = availableServices,
                    isLoadingOnly = isLoadingOnly,
                    topContentInset = contentPadding.calculateTopPadding(),
                    onSelectService = discoverViewModel::selectService,
                    onHeroItemClick = { item ->
                        openTrackingItem(appRouter, discoverViewModel, availableServices, item)
                    },
                    onSourceClick = { source -> appRouter.openList(source.source, null, null) },
                    onManageSourcesClick = appRouter::openManageSources,
                )
            }

            items(
                items = showcaseRows,
                key = { "showcase_${it.category.id}" },
            ) { row ->
                val rowContentItems = remember(row) {
                    row.items.filterIsInstance<ContentListModel>().take(12)
                }
                if (rowContentItems.isNotEmpty()) {
                    TrackingCategoryRow(
                        title = stringResource(row.category.nameResId),
                        items = rowContentItems,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onItemClick = { item ->
                            openTrackingItem(appRouter, discoverViewModel, availableServices, item)
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
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                items(
                    items = popularItems,
                    key = { "popular_${it.id}" },
                ) { item ->
                    BrowsePopularListItem(
                        item = item,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onClick = {
                            openTrackingItem(appRouter, discoverViewModel, availableServices, item)
                        },
                    )
                }
            }

            if (isDiscoverLoading && popularItems.isNotEmpty()) {
                item(key = "popular_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

private suspend fun LazyListState.maybeTriggerBrowseLoadMore(
    itemCount: Int,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
) {
    snapshotFlow { layoutInfo.visibleItemsInfo.lastOrNull()?.index }
        .collect { lastVisibleIndex: Int? ->
            if (lastVisibleIndex != null && !isLoading && lastVisibleIndex >= itemCount - BrowseLoadMoreBuffer) {
                onLoadMore()
            }
        }
}

private fun openTrackingItem(
    appRouter: AppRouter,
    discoverViewModel: DiscoverViewModel,
    availableServices: List<ScrobblerService>,
    item: ContentListModel,
) {
    val serviceName = item.manga.source.name.removePrefix("TRACKING_")
    val trackingService = availableServices.find { it.name == serviceName } ?: return
    if (discoverViewModel.supportsDetails(trackingService)) {
        appRouter.openTrackingSiteDetails(trackingService, item.manga.id, item.manga.publicUrl)
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
    sources: List<ContentSourceItem>,
    activeService: ScrobblerService?,
    availableServices: List<ScrobblerService>,
    isLoadingOnly: Boolean,
    topContentInset: androidx.compose.ui.unit.Dp,
    onSelectService: (ScrobblerService) -> Unit,
    onHeroItemClick: (ContentListModel) -> Unit,
    onSourceClick: (ContentSourceItem) -> Unit,
    onManageSourcesClick: () -> Unit,
) {
    val sourcesContent: (@Composable () -> Unit)? = when {
        sources.isNotEmpty() -> ({
            SourcesQuickAccessSection(
                sources = sources,
                onSourceClick = onSourceClick,
                onManageClick = onManageSourcesClick,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 22.dp),
            )
        })
        isLoadingOnly -> ({
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        })
        else -> null
    }

    if (heroItems.isNotEmpty()) {
        DiscoverHeroCarousel(
            title = title,
            items = heroItems,
            activeService = activeService,
            availableServices = availableServices,
            onSelectService = onSelectService,
            onItemClick = onHeroItemClick,
            topContentInset = topContentInset,
            bottomContent = sourcesContent,
        )
    } else {
        Box(
            modifier = Modifier
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
                CircularProgressIndicator()
            }
        }
        if (sources.isNotEmpty()) {
            SourcesQuickAccessSection(
                sources = sources,
                onSourceClick = onSourceClick,
                onManageClick = onManageSourcesClick,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 22.dp),
            )
        } else {
            Spacer(modifier = Modifier.fillMaxWidth().height(12.dp))
        }
    }
}

@Composable
private fun SourcesQuickAccessSection(
    sources: List<ContentSourceItem>,
    onSourceClick: (ContentSourceItem) -> Unit,
    onManageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                )
            }
            TextButton(onClick = onManageClick) {
                Text(stringResource(R.string.manage))
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(
                items = sources.take(16),
                key = { it.id },
            ) { source ->
                SourceQuickAccessCard(
                    source = source,
                    onClick = { onSourceClick(source) },
                )
            }
        }
    }
}

@Composable
private fun SourceQuickAccessCard(
    source: ContentSourceItem,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val actualSource = source.source.mangaSource
    val title = actualSource.getTitle(context)
    val faviconRequest = remember(source.id, actualSource.name) {
        val fallback = sourceFallbackImage(
            context = context,
            styleResId = R.style.FaviconDrawable,
            source = actualSource,
            animated = false,
        )
        Builder(context)
            .data(actualSource.faviconUri())
            .crossfade(true)
            .mangaSourceExtra(actualSource)
            .placeholder(fallback)
            .fallback(fallback)
            .error(fallback)
            .build()
    }

    Surface(
        modifier = Modifier
            .width(68.dp)
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            ) {
                AsyncImage(
                    model = faviconRequest,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(34.dp)
                        .padding(4.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TrackingCategoryRow(
    title: String,
    items: List<ContentListModel>,
    onItemClick: (ContentListModel) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onMoreClick) {
                Text(stringResource(R.string.more))
            }
        }
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

@Composable
private fun BrowsePopularHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun BrowsePopularListItem(
    item: ContentListModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backgroundRequest = remember(item.coverUrl, item.id) {
        ImageRequest.Builder(context)
            .data(item.coverUrl)
            .crossfade(true)
            .build()
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
                .height(158.dp),
        ) {
            AsyncImage(
                model = backgroundRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp)
                    .alpha(0.72f),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.38f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.32f),
                            ),
                        ),
                    ),
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 88.dp, height = 124.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    AsyncImage(
                        model = backgroundRequest,
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
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
            .width(76.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 106.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
