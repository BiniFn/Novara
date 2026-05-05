package org.skepsun.kototoro.home.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.ExperimentalSharedTransitionApi
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.util.Locale
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.compose.HeroPagerIndicator
import org.skepsun.kototoro.core.ui.compose.rememberResolvedSourceTitle
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.compose.compactPosterRailCardStyle
import org.skepsun.kototoro.core.ui.compose.HorizontalRailAnimatedVisibility
import org.skepsun.kototoro.core.ui.compose.LocalNavAnimatedVisibilityScope
import org.skepsun.kototoro.core.ui.compose.LocalSharedTransitionScope
import org.skepsun.kototoro.core.ui.compose.contentCoverSharedKey
import org.skepsun.kototoro.core.ui.compose.rememberRailAnimationFactor
import org.skepsun.kototoro.core.ui.compose.rememberHorizontalRailScrollIntensity
import org.skepsun.kototoro.core.ui.compose.unclippedBoundsInWindow
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.discover.ui.compose.DiscoverHeroCarousel
import org.skepsun.kototoro.home.ui.HOME_HERO_SECTION_LIMIT
import org.skepsun.kototoro.home.ui.HOME_HERO_TOTAL_LIMIT
import org.skepsun.kototoro.home.ui.HomeRecentItem
import org.skepsun.kototoro.home.ui.HomeRecommendationItem
import org.skepsun.kototoro.home.ui.HomeSummaryState
import org.skepsun.kototoro.home.ui.HomeUpdateItem
import org.skepsun.kototoro.list.ui.compose.ContentCardCornerBadges
import org.skepsun.kototoro.list.ui.compose.ContentCardNsfwBadge
import org.skepsun.kototoro.list.ui.compose.contentCardBadgeMetricsFor
import org.skepsun.kototoro.list.ui.model.ContentGridModel
import org.skepsun.kototoro.parsers.model.Content

@Immutable
private data class HomeCardBadgePrefs(
    val topLeft: Set<String>,
    val topRight: Set<String>,
    val bottomLeft: Set<String>,
    val bottomRight: Set<String>,
)

@Immutable
private data class HomeScreenPrefs(
    val gridScale: Float,
    val badgePrefs: HomeCardBadgePrefs,
)

@Stable
data class HomeScreenActions(
    val onSettingsClick: () -> Unit,
    val onReaderSettingsClick: () -> Unit,
    val onSyncSettingsClick: () -> Unit,
    val onViewAllRecentClick: () -> Unit,
    val onViewAllUpdatesClick: () -> Unit,
    val onViewAllRecommendationsClick: () -> Unit,
    val onRecentSearchClick: (String) -> Unit,
    val onSourceSettingsClick: () -> Unit,
    val onLibraryOpenClick: () -> Unit,
    val onBookmarksClick: () -> Unit,
    val onLocalClick: () -> Unit,
    val onDownloadsClick: () -> Unit,
    val onRandomClick: () -> Unit,
    val onAutoTranslateClick: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    state: HomeSummaryState,
    onContentClick: (Content, Rect?, String?) -> Unit,
    actions: HomeScreenActions,
    isRandomLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val layoutDirection = LocalLayoutDirection.current
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
    val context = LocalContext.current
    val density = LocalDensity.current
    val settings = remember(context.applicationContext) { AppSettings(context.applicationContext) }
    val screenPrefs by settings.observeAsState(
        AppSettings.KEY_GRID_SIZE,
        AppSettings.KEY_BADGES_TOP_LEFT,
        AppSettings.KEY_BADGES_TOP_RIGHT,
        AppSettings.KEY_BADGES_BOTTOM_LEFT,
        AppSettings.KEY_BADGES_BOTTOM_RIGHT,
    ) {
        HomeScreenPrefs(
            gridScale = gridSize / 100f,
            badgePrefs = HomeCardBadgePrefs(
                topLeft = badgesTopLeft,
                topRight = badgesTopRight,
                bottomLeft = badgesBottomLeft,
                bottomRight = badgesBottomRight,
            ),
        )
    }
    val gridScale = screenPrefs.gridScale
    val badgePrefs = screenPrefs.badgePrefs
    val posterStyle = remember(gridScale) { compactPosterRailCardStyle(gridScale) }
    val recentSearches = remember(state.recentSearches) { state.recentSearches.map { it.query } }
    val heroEntries = remember(
        state.resumeState.content,
        state.resumeState.groupKey,
        state.resumeState.progressPercent,
        state.recentHistoryItems,
        state.recentUpdates,
        state.recommendations,
    ) {
        buildHomeHeroEntries(
            resumeContent = state.resumeState.content,
            resumeGroupKey = state.resumeState.groupKey,
            resumeProgressPercent = state.resumeState.progressPercent,
            historyItems = state.recentHistoryItems,
            updateItems = state.recentUpdates,
            recommendationItems = state.recommendations,
        )
    }
    val quickActions = listOf(
        HomeQuickAction(stringResource(R.string.favourites), R.drawable.ic_heart, actions.onLibraryOpenClick),
        HomeQuickAction(stringResource(R.string.bookmarks), R.drawable.ic_bookmark, actions.onBookmarksClick),
        HomeQuickAction(stringResource(R.string.local_storage), R.drawable.ic_storage, actions.onLocalClick),
        HomeQuickAction(stringResource(R.string.downloads), R.drawable.ic_download, actions.onDownloadsClick),
        HomeQuickAction(stringResource(R.string.random), R.drawable.ic_dice, actions.onRandomClick, !isRandomLoading),
        HomeQuickAction(stringResource(R.string.sync_status), R.drawable.ic_sync, actions.onSyncSettingsClick),
        HomeQuickAction(stringResource(R.string.home_sources_overview), R.drawable.ic_extension, actions.onSourceSettingsClick),
        HomeQuickAction(stringResource(R.string.translation_settings), R.drawable.ic_language, actions.onAutoTranslateClick),
        HomeQuickAction(stringResource(R.string.reader_settings), R.drawable.ic_read, actions.onReaderSettingsClick),
        HomeQuickAction(stringResource(R.string.settings), R.drawable.ic_settings, actions.onSettingsClick),
    )
    val topInset = contentPadding.calculateTopPadding()
    val estimatedHeroPx = with(density) { (340.dp + topInset).roundToPx() }
    var heroPx by rememberSaveable { mutableIntStateOf(estimatedHeroPx) }
    val heroHeightDp by remember(heroPx, density) {
        derivedStateOf { with(density) { heroPx.toDp() } }
    }
    val heroScrollOffsetPx by remember(scrollState, heroPx) {
        derivedStateOf {
            (-scrollState.value.toFloat()).coerceIn(-heroPx.toFloat(), 0f)
        }
    }


    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(rememberNestedScrollInteropConnection())
                .verticalScroll(scrollState)
                .padding(
                    start = systemBarsPadding.calculateLeftPadding(layoutDirection) + 8.dp,
                    end = systemBarsPadding.calculateRightPadding(layoutDirection) + 8.dp,
                    bottom = contentPadding.calculateBottomPadding(),
                )
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val hasHighlights = heroEntries.isNotEmpty() ||
                state.recentHistoryItems.isNotEmpty() ||
                state.recentUpdates.isNotEmpty() ||
                state.recommendations.isNotEmpty() ||
                recentSearches.isNotEmpty()
            if (hasHighlights) {
                if (heroEntries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(heroHeightDp))
                }
                HomeHighlightsSections(
                    historyItems = state.recentHistoryItems,
                    recentHistoryCount = state.recentHistoryCount,
                    updateItems = state.recentUpdates,
                    unreadUpdatesCount = state.unreadUpdatesCount,
                    recommendationItems = state.recommendations,
                    recommendationsCount = state.recommendationsCount,
                    recentSearches = recentSearches,
                    posterStyle = posterStyle,
                    badgePrefs = badgePrefs,
                    onItemClick = onContentClick,
                    onViewAllRecentClick = actions.onViewAllRecentClick,
                    onViewAllUpdatesClick = actions.onViewAllUpdatesClick,
                    onViewAllRecommendationsClick = actions.onViewAllRecommendationsClick,
                    onRecentSearchClick = actions.onRecentSearchClick,
                )
            }
            if (!hasHighlights && !state.isInitialized) {
                HomeLoadingSkeleton(posterStyle = posterStyle)
            }

            QuickActionsSection(actions = quickActions)
        }

        if (heroEntries.isNotEmpty()) {
            HomeHeroSection(
                entries = heroEntries,
                onClick = onContentClick,
                topContentInset = topInset,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer { translationY = heroScrollOffsetPx }
                    .onGloballyPositioned { coordinates ->
                        val newHeight = coordinates.size.height
                        if (heroPx != newHeight) heroPx = newHeight
                    },
            )
        }
    }
}


@Composable
private fun HomeHighlightsSections(
    historyItems: List<HomeRecentItem>,
    recentHistoryCount: Int,
    updateItems: List<HomeUpdateItem>,
    unreadUpdatesCount: Int,
    recommendationItems: List<HomeRecommendationItem>,
    recommendationsCount: Int,
    recentSearches: List<String>,
    posterStyle: org.skepsun.kototoro.core.ui.compose.CompactPosterCardStyle,
    badgePrefs: HomeCardBadgePrefs,
    onItemClick: (Content, Rect?, String?) -> Unit,
    onViewAllRecentClick: () -> Unit,
    onViewAllUpdatesClick: () -> Unit,
    onViewAllRecommendationsClick: () -> Unit,
    onRecentSearchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val historyDisplayItems = remember(historyItems) {
        historyItems.map {
            HomeCoverDisplayItem(
                content = it.content,
                cardModel = it.cardModel,
                sectionKey = "recent_history",
            )
        }
    }
    val updateDisplayItems = remember(updateItems) {
        updateItems.map {
            HomeCoverDisplayItem(
                content = it.content,
                cardModel = it.cardModel,
                sectionKey = "recent_updates",
            )
        }
    }
    val recommendationDisplayItems = remember(recommendationItems) {
        recommendationItems.map {
            HomeCoverDisplayItem(
                content = it.content,
                cardModel = it.cardModel,
                sectionKey = "recommendations",
            )
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (historyItems.isNotEmpty()) {
            HomeContentRowSection(
                title = stringResource(R.string.recent_history),
                sectionKey = "recent_history",
                iconRes = R.drawable.ic_history,
                items = historyDisplayItems,
                count = recentHistoryCount,
                posterStyle = posterStyle,
                badgePrefs = badgePrefs,
                onItemClick = onItemClick,
                onMoreClick = onViewAllRecentClick,
                addTopSpacing = false,
            )
        }
        if (updateItems.isNotEmpty()) {
            HomeContentRowSection(
                title = stringResource(R.string.home_recent_updates),
                sectionKey = "recent_updates",
                iconRes = R.drawable.ic_updated,
                items = updateDisplayItems,
                count = unreadUpdatesCount,
                posterStyle = posterStyle,
                badgePrefs = badgePrefs,
                onItemClick = onItemClick,
                onMoreClick = onViewAllUpdatesClick,
                addTopSpacing = false,
            )
        }
        if (recommendationItems.isNotEmpty()) {
            HomeContentRowSection(
                title = stringResource(R.string.suggestions),
                sectionKey = "recommendations",
                iconRes = R.drawable.ic_feed,
                items = recommendationDisplayItems,
                count = recommendationsCount,
                posterStyle = posterStyle,
                badgePrefs = badgePrefs,
                onItemClick = onItemClick,
                onMoreClick = onViewAllRecommendationsClick,
                addTopSpacing = false,
            )
        }
        if (recentSearches.isNotEmpty()) {
            HomeRecentSearchSection(
                queries = recentSearches,
                onQueryClick = onRecentSearchClick,
            )
        }
    }
}

@Composable
private fun HomeHeroSection(
    entries: List<HomeHeroEntry>,
    onClick: (Content, Rect?, String?) -> Unit,
    topContentInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val heroItems = remember(entries) { entries.map(HomeHeroEntry::toListModel) }
    val pagerState = rememberPagerState(pageCount = { entries.size })
    val selectedIndex by remember(entries, pagerState) {
        derivedStateOf { pagerState.currentPage.coerceIn(0, entries.lastIndex) }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        DiscoverHeroCarousel(
            title = stringResource(R.string.home),
            items = heroItems,
            activeService = null,
            availableServices = emptyList(),
            onItemClick = { item, rect, sharedKey ->
                entries.getOrNull(heroItems.indexOfFirst { it.id == item.id })
                    ?.content
                    ?.let { content -> onClick(content, rect, sharedKey) }
            },
            onSelectService = {},
            topContentInset = topContentInset,
            settings = null,
            detachedBottomContent = true,
            bottomContent = {
                HomeHeroOverlay(
                    entry = entries[selectedIndex],
                    currentPage = selectedIndex,
                    pageCount = entries.size,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            },
            sharedElementKeyForItem = { _, index ->
                entries[index].sharedElementKey
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HomeHeroOverlay(
    entry: HomeHeroEntry,
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeBadge(
                text = stringResource(entry.kind.labelRes),
                iconRes = entry.kind.iconRes,
            )
            if (pageCount > 1) {
                HomeBadge(
                    text = "${currentPage + 1}/$pageCount",
                    iconRes = entry.kind.iconRes,
                )
            }
        }
        Text(
            text = entry.content.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = rememberResolvedSourceTitle(entry.content.source),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.86f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        entry.supportingText()?.let { supportingText ->
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (pageCount > 1) {
            HeroPagerIndicator(
                pageCount = pageCount,
                currentPage = currentPage,
            )
        }
    }
}

@Composable
private fun HomeRecentSearchSection(
    queries: List<String>,
    onQueryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.home_recent_searches),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            HomeBadge(
                text = queries.size.toHeroCountLabel(),
                iconRes = R.drawable.ic_history,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            queries.forEach { query ->
                AssistChip(
                    onClick = { onQueryClick(query) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_history),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    label = {
                        Text(
                            text = query,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeContentRowSection(
    title: String,
    sectionKey: String,
    iconRes: Int,
    items: List<HomeCoverDisplayItem>,
    count: Int,
    posterStyle: org.skepsun.kototoro.core.ui.compose.CompactPosterCardStyle,
    badgePrefs: HomeCardBadgePrefs,
    onItemClick: (Content, Rect?, String?) -> Unit,
    onMoreClick: () -> Unit,
    addTopSpacing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val rowState = rememberLazyListState()
    val scrollIntensity = rememberHorizontalRailScrollIntensity(rowState)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (addTopSpacing) 6.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                HomeBadge(
                    text = count.toHeroCountLabel(),
                    iconRes = iconRes,
                )
            }
            TextButton(
                onClick = onMoreClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.more),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        val railAnimationFactor = rememberRailAnimationFactor()
        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            itemsIndexed(
                items = items.take(12),
                key = { _, item -> "${item.sectionKey}:${item.content.id}" },
                contentType = { _, _ -> "home_content_card" },
            ) { index, item ->
                HorizontalRailAnimatedVisibility(
                    animationKey = "home_row_${title}_${item.content.id}",
                    index = index,
                    listState = rowState,
                    scrollIntensity = scrollIntensity,
                    animationFactor = railAnimationFactor,
                    enableScrollLinkedAnimation = false,
                ) { animatedModifier ->
                    HomeCoverRowItem(
                        item = item,
                        posterStyle = posterStyle,
                        badgePrefs = badgePrefs,
                        onClick = { coverBounds, sharedElementKey ->
                            onItemClick(item.content, coverBounds, sharedElementKey)
                        },
                        modifier = animatedModifier,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeCoverRowItem(
    item: HomeCoverDisplayItem,
    posterStyle: org.skepsun.kototoro.core.ui.compose.CompactPosterCardStyle,
    badgePrefs: HomeCardBadgePrefs,
    onClick: (Rect?, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val cardShape = MaterialTheme.shapes.medium
    val cardRadius = remember(cardShape, density) {
        (cardShape as? RoundedCornerShape)?.topStart?.toPx(Size.Unspecified, density)?.let {
            with(density) { it.toDp() }
        } ?: 12.dp
    }
    val content = item.content
    var coverBounds by remember(content.id) { mutableStateOf<Rect?>(null) }
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val shouldCrossfadeCover = sharedTransitionScope == null || animatedVisibilityScope == null
    val imageRequest = remember(content.coverUrl, content.id, shouldCrossfadeCover) {
        ImageRequest.Builder(context)
            .data(content.coverUrl)
            .crossfade(shouldCrossfadeCover)
            .apply { mangaExtra(content) }
            .build()
    }
    val badgeMetrics = remember(posterStyle.itemWidth) { contentCardBadgeMetricsFor(posterStyle.itemWidth) }
    val sharedElementKey = remember(item.sectionKey, content.id, content.coverUrl, content.source.name) {
        contentCoverSharedKey(
            content.source.name,
            content.coverUrl.orEmpty(),
            instanceKey = "home_row_${item.sectionKey}_${content.id}",
        )
    }

    Column(
        modifier = modifier
            .width(posterStyle.itemWidth)
            .clickable { onClick(coverBounds, sharedElementKey) },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(posterStyle.posterHeight)
                .onGloballyPositioned { coordinates ->
                    coverBounds = coordinates.unclippedBoundsInWindow()
                }
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
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = content.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            item.cardModel?.let { cardModel ->
                ContentCardCornerBadges(
                    badges = badgePrefs.topLeft,
                    item = cardModel,
                    corner = Alignment.TopStart,
                    cardRadius = cardRadius,
                    metrics = badgeMetrics,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                ContentCardCornerBadges(
                    badges = remember(badgePrefs.topRight, cardModel.counter) {
                        if (cardModel.counter > 0 && "counter" !in badgePrefs.topRight) {
                            badgePrefs.topRight + "counter"
                        } else {
                            badgePrefs.topRight
                        }
                    },
                    item = cardModel,
                    corner = Alignment.TopEnd,
                    cardRadius = cardRadius,
                    metrics = badgeMetrics,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
                ContentCardCornerBadges(
                    badges = badgePrefs.bottomLeft,
                    item = cardModel,
                    corner = Alignment.BottomStart,
                    cardRadius = cardRadius,
                    metrics = badgeMetrics,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
                ContentCardCornerBadges(
                    badges = badgePrefs.bottomRight,
                    item = cardModel,
                    corner = Alignment.BottomEnd,
                    cardRadius = cardRadius,
                    metrics = badgeMetrics,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            } ?: run {
                if (content.isNsfw()) {
                    ContentCardNsfwBadge(
                        metrics = badgeMetrics,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(badgeMetrics.outerPadding),
                    )
                }
            }
        }
        Text(
            text = content.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Immutable
private data class HomeCoverDisplayItem(
    val content: Content,
    val cardModel: ContentGridModel?,
    val sectionKey: String,
)

@Composable
private fun QuickActionsSection(
    actions: List<HomeQuickAction>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.quick_access),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        androidx.compose.foundation.layout.BoxWithConstraints {
            val compact = maxWidth < 680.dp
            val itemsPerRow = when {
                maxWidth >= 800.dp -> 4
                maxWidth >= 620.dp -> 3
                maxWidth >= 360.dp -> 4
                else -> 3
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = itemsPerRow,
            ) {
                actions.forEach { action ->
                    QuickAccessButton(
                        action = action,
                        compact = compact,
                        modifier = Modifier.weight(1f, fill = true),
                    )
                }
            }
        }
    }
}

private data class HomeQuickAction(
    val label: String,
    val iconRes: Int,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@Composable
private fun QuickAccessButton(
    action: HomeQuickAction,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = action.enabled, onClick = action.onClick)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(action.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (action.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                )
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (action.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = action.enabled, onClick = action.onClick)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(action.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (action.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                )
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (action.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeBadge(
    text: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeLoadingSkeleton(
    posterStyle: org.skepsun.kototoro.core.ui.compose.CompactPosterCardStyle,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HomeSkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(232.dp),
        )
        repeat(2) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeSkeletonBlock(
                        modifier = Modifier
                            .width(124.dp)
                            .height(16.dp),
                    )
                    HomeSkeletonBlock(
                        modifier = Modifier
                            .width(52.dp)
                            .height(14.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    repeat(3) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            HomeSkeletonBlock(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(posterStyle.posterHeight),
                            )
                            HomeSkeletonBlock(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(12.dp),
                            )
                            HomeSkeletonBlock(
                                modifier = Modifier
                                    .fillMaxWidth(0.64f)
                                    .height(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSkeletonBlock(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)),
    )
}

@Composable
private fun HomeStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Int.toHeroCountLabel(): String = when {
    this >= 10_000 -> "${this / 1000}k+"
    this >= 1_000 -> "${this / 1000}k"
    else -> toString()
}

private enum class HomeHeroKind(val labelRes: Int, val iconRes: Int) {
    RESUME(R.string.home_resume_title, R.drawable.ic_read),
    HISTORY(R.string.recent_history, R.drawable.ic_history),
    UPDATE(R.string.home_recent_updates, R.drawable.ic_updated),
    RECOMMENDATION(R.string.suggestions, R.drawable.ic_feed),
}

private data class HomeHeroEntry(
    val kind: HomeHeroKind,
    val content: Content,
    val groupKey: Long,
    val progressPercent: Int? = null,
    val newChapters: Int = 0,
)

private val HomeHeroEntry.sharedElementKey: String
    get() = contentCoverSharedKey(
        sourceName = content.source.name,
        url = content.coverUrl.orEmpty(),
        instanceKey = "home_hero_${kind.name.lowercase(Locale.ROOT)}_${content.id}",
    )

@Composable
private fun HomeHeroEntry.supportingText(): String? = when (kind) {
    HomeHeroKind.RESUME -> progressPercent
        ?.takeIf { it > 0 }
        ?.let { value -> stringResource(R.string.home_resume_progress, value) }
    HomeHeroKind.UPDATE -> newChapters
        .takeIf { it > 0 }
        ?.let { value ->
            stringResource(
                R.string.new_chapters_pattern,
                stringResource(R.string.new_chapters),
                value,
            )
        }
    HomeHeroKind.HISTORY,
    HomeHeroKind.RECOMMENDATION -> null
}

private fun HomeHeroEntry.toListModel(): ContentGridModel = ContentGridModel(
    manga = content,
    override = null,
    subtitle = null,
    counter = 0,
    id = content.id,
    progress = null,
    isFavorite = false,
    isSaved = false,
)

private fun buildHomeHeroEntries(
    resumeContent: Content?,
    resumeGroupKey: Long?,
    resumeProgressPercent: Int?,
    historyItems: List<org.skepsun.kototoro.home.ui.HomeRecentItem>,
    updateItems: List<org.skepsun.kototoro.home.ui.HomeUpdateItem>,
    recommendationItems: List<org.skepsun.kototoro.home.ui.HomeRecommendationItem>,
): List<HomeHeroEntry> {
    val entries = ArrayList<HomeHeroEntry>(HOME_HERO_TOTAL_LIMIT)

    fun addEntry(entry: HomeHeroEntry) {
        if (entries.size >= HOME_HERO_TOTAL_LIMIT) return
        entries += entry
    }

    resumeContent?.let { content ->
        addEntry(
                HomeHeroEntry(
                    kind = HomeHeroKind.RESUME,
                    content = content,
                    groupKey = resumeGroupKey ?: content.id,
                    progressPercent = resumeProgressPercent,
                ),
            )
        }

    historyItems
        .asSequence()
        .filterNot { it.groupKey == resumeGroupKey }
        .take(HOME_HERO_SECTION_LIMIT)
        .forEach { item ->
            addEntry(
                HomeHeroEntry(
                    kind = HomeHeroKind.HISTORY,
                    content = item.content,
                    groupKey = item.groupKey,
                ),
            )
        }

    updateItems
        .asSequence()
        .filterNot { it.groupKey == resumeGroupKey }
        .take(HOME_HERO_SECTION_LIMIT)
        .forEach { item ->
            addEntry(
                HomeHeroEntry(
                    kind = HomeHeroKind.UPDATE,
                    content = item.content,
                    groupKey = item.groupKey,
                    newChapters = item.newChapters,
                ),
            )
        }

    recommendationItems
        .asSequence()
        .filterNot { it.groupKey == resumeGroupKey }
        .take(HOME_HERO_SECTION_LIMIT)
        .forEach { item ->
            addEntry(
                HomeHeroEntry(
                    kind = HomeHeroKind.RECOMMENDATION,
                    content = item.content,
                    groupKey = item.groupKey,
                ),
            )
        }

    return entries
}
