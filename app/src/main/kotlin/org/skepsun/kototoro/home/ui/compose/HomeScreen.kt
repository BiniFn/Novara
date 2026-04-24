package org.skepsun.kototoro.home.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.absoluteValue
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.compose.HeroAutoAdvanceEffect
import org.skepsun.kototoro.core.ui.compose.HeroPagerIndicator
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.compose.KototoroLoadingIndicator
import org.skepsun.kototoro.core.ui.compose.compactPosterRailCardStyle
import org.skepsun.kototoro.core.ui.compose.HorizontalRailAnimatedVisibility
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.details.ui.compose.AnimatedPanoramaBackdrop
import org.skepsun.kototoro.home.ui.HOME_HERO_SECTION_LIMIT
import org.skepsun.kototoro.home.ui.HOME_HERO_TOTAL_LIMIT
import org.skepsun.kototoro.home.ui.HomeRecentItem
import org.skepsun.kototoro.home.ui.HomeRecommendationItem
import org.skepsun.kototoro.home.ui.HomeSummaryState
import org.skepsun.kototoro.home.ui.HomeUpdateItem
import org.skepsun.kototoro.list.ui.compose.ContentCardNsfwBadge
import org.skepsun.kototoro.list.ui.compose.ContentCardCornerBadges
import org.skepsun.kototoro.list.ui.model.ContentGridModel
import org.skepsun.kototoro.parsers.model.Content

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    state: HomeSummaryState,
    onContentClick: (Content, Rect?) -> Unit,
    onSettingsClick: () -> Unit,
    onReaderSettingsClick: () -> Unit,
    onSyncSettingsClick: () -> Unit,
    onViewAllRecentClick: () -> Unit,
    onViewAllUpdatesClick: () -> Unit,
    onViewAllRecommendationsClick: () -> Unit,
    onRecentSearchClick: (String) -> Unit,
    onSourceSettingsClick: () -> Unit,
    onLibraryOpenClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onLocalClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onRandomClick: () -> Unit,
    onAutoTranslateClick: () -> Unit,
    isRandomLoading: Boolean,
) {
    val scrollState = rememberScrollState()
    val layoutDirection = LocalLayoutDirection.current
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
    val context = LocalContext.current
    val settings = remember(context.applicationContext) { AppSettings(context.applicationContext) }
    val gridScale by settings.observeAsState(AppSettings.KEY_GRID_SIZE) { gridSize / 100f }
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
        HomeQuickAction(stringResource(R.string.favourites), R.drawable.ic_heart, onLibraryOpenClick),
        HomeQuickAction(stringResource(R.string.bookmarks), R.drawable.ic_bookmark, onBookmarksClick),
        HomeQuickAction(stringResource(R.string.local_storage), R.drawable.ic_storage, onLocalClick),
        HomeQuickAction(stringResource(R.string.downloads), R.drawable.ic_download, onDownloadsClick),
        HomeQuickAction(stringResource(R.string.random), R.drawable.ic_dice, onRandomClick, !isRandomLoading),
        HomeQuickAction(stringResource(R.string.sync_status), R.drawable.ic_sync, onSyncSettingsClick),
        HomeQuickAction(stringResource(R.string.home_sources_overview), R.drawable.ic_storage, onSourceSettingsClick),
        HomeQuickAction(stringResource(R.string.translation_settings), R.drawable.ic_language, onAutoTranslateClick),
        HomeQuickAction(stringResource(R.string.reader_settings), R.drawable.ic_read, onReaderSettingsClick),
        HomeQuickAction(stringResource(R.string.settings), R.drawable.ic_settings, onSettingsClick),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(rememberNestedScrollInteropConnection())
            .verticalScroll(scrollState)
            .padding(
                start = systemBarsPadding.calculateLeftPadding(layoutDirection) + 8.dp,
                end = systemBarsPadding.calculateRightPadding(layoutDirection) + 8.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val hasHighlights = heroEntries.isNotEmpty() ||
            state.recentHistoryItems.isNotEmpty() ||
            state.recentUpdates.isNotEmpty() ||
            state.recommendations.isNotEmpty() ||
            recentSearches.isNotEmpty()
        if (hasHighlights) {
            HomeHighlightsSections(
                heroEntries = heroEntries,
                historyItems = state.recentHistoryItems,
                recentHistoryCount = state.recentHistoryCount,
                updateItems = state.recentUpdates,
                unreadUpdatesCount = state.unreadUpdatesCount,
                recommendationItems = state.recommendations,
                recommendationsCount = state.recommendationsCount,
                recentSearches = recentSearches,
                posterStyle = posterStyle,
                onItemClick = onContentClick,
                onViewAllRecentClick = onViewAllRecentClick,
                onViewAllUpdatesClick = onViewAllUpdatesClick,
                onViewAllRecommendationsClick = onViewAllRecommendationsClick,
                onRecentSearchClick = onRecentSearchClick,
            )
        }
        if (!hasHighlights && !state.isInitialized) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                KototoroLoadingIndicator()
            }
        }

        QuickActionsCard(actions = quickActions)
    }
}


@Composable
private fun HomeHighlightsSections(
    heroEntries: List<HomeHeroEntry>,
    historyItems: List<HomeRecentItem>,
    recentHistoryCount: Int,
    updateItems: List<HomeUpdateItem>,
    unreadUpdatesCount: Int,
    recommendationItems: List<HomeRecommendationItem>,
    recommendationsCount: Int,
    recentSearches: List<String>,
    posterStyle: org.skepsun.kototoro.core.ui.compose.CompactPosterCardStyle,
    onItemClick: (Content, Rect?) -> Unit,
    onViewAllRecentClick: () -> Unit,
    onViewAllUpdatesClick: () -> Unit,
    onViewAllRecommendationsClick: () -> Unit,
    onRecentSearchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val historyDisplayItems = remember(historyItems) {
        historyItems.map { HomeCoverDisplayItem(content = it.content, cardModel = it.cardModel) }
    }
    val updateDisplayItems = remember(updateItems) {
        updateItems.map { HomeCoverDisplayItem(content = it.content, cardModel = it.cardModel) }
    }
    val recommendationDisplayItems = remember(recommendationItems) {
        recommendationItems.map { HomeCoverDisplayItem(content = it.content, cardModel = it.cardModel) }
    }
    org.skepsun.kototoro.core.ui.glass.GlassSurface(
        modifier = modifier.fillMaxWidth(),
        style = org.skepsun.kototoro.core.ui.glass.GlassDefaults.subtleStyle(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        allowRuntimeHaze = false,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (heroEntries.isNotEmpty()) {
                HomeHeroCarousel(
                    entries = heroEntries,
                    onClick = onItemClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (historyItems.isNotEmpty() || updateItems.isNotEmpty() || recommendationItems.isNotEmpty() || recentSearches.isNotEmpty()) {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 10.dp,
                        end = 10.dp,
                        top = if (heroEntries.isNotEmpty()) 0.dp else 10.dp,
                        bottom = 10.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                var firstSection = true

                if (historyItems.isNotEmpty()) {
                    HomeContentRowSection(
                        title = stringResource(R.string.recent_history),
                        iconRes = R.drawable.ic_history,
                        items = historyDisplayItems,
                        count = recentHistoryCount,
                        posterStyle = posterStyle,
                        onItemClick = onItemClick,
                        onMoreClick = onViewAllRecentClick,
                        addTopSpacing = false,
                    )
                    firstSection = false
                }
                if (updateItems.isNotEmpty()) {
                    if (!firstSection) {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        )
                    }
                    HomeContentRowSection(
                        title = stringResource(R.string.home_recent_updates),
                        iconRes = R.drawable.ic_updated,
                        items = updateDisplayItems,
                        count = unreadUpdatesCount,
                        posterStyle = posterStyle,
                        onItemClick = onItemClick,
                        onMoreClick = onViewAllUpdatesClick,
                        addTopSpacing = false,
                    )
                    firstSection = false
                }
                if (recommendationItems.isNotEmpty()) {
                    if (!firstSection) {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        )
                    }
                    HomeContentRowSection(
                        title = stringResource(R.string.suggestions),
                        iconRes = R.drawable.ic_feed,
                        items = recommendationDisplayItems,
                        count = recommendationsCount,
                        posterStyle = posterStyle,
                        onItemClick = onItemClick,
                        onMoreClick = onViewAllRecommendationsClick,
                        addTopSpacing = false,
                    )
                    firstSection = false
                }
                if (recentSearches.isNotEmpty()) {
                    if (!firstSection) {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        )
                    }
                    HomeRecentSearchSection(
                        queries = recentSearches,
                        onQueryClick = onRecentSearchClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeroCarousel(
    entries: List<HomeHeroEntry>,
    onClick: (Content, Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { entries.size })
    val hasPagerIndicator = entries.size > 1
    val selectedIndex by remember(entries, pagerState) {
        derivedStateOf { pagerState.currentPage.coerceIn(0, entries.lastIndex) }
    }

    HeroAutoAdvanceEffect(
        pagerState = pagerState,
        pageCount = entries.size,
        intervalMillis = 5200L,
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) {
        val sidePeek = 18.dp
        val pageSpacing = 2.dp
        val horizontalContentPadding = 8.dp
        val cardWidth = (maxWidth - (horizontalContentPadding * 2) - (sidePeek * 2) - pageSpacing)
            .coerceAtLeast(264.dp)

        Box(modifier = Modifier.fillMaxWidth()) {
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(cardWidth),
                pageSpacing = pageSpacing,
                beyondViewportPageCount = 2,
                contentPadding = PaddingValues(horizontal = horizontalContentPadding),
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    .absoluteValue
                    .coerceIn(0f, 1f)
                val focusProgress = 1f - pageOffset
                val pageScale = 0.9f + (0.1f * focusProgress)
                val pageAlpha = 0.72f + (0.28f * focusProgress)
                HomeHeroCard(
                    entry = entries[page],
                    bottomInset = if (hasPagerIndicator) 42.dp else 16.dp,
                    onClick = onClick,
                    modifier = Modifier.graphicsLayer {
                        scaleX = pageScale
                        scaleY = pageScale
                        alpha = pageAlpha
                        translationY = (1f - focusProgress) * 16f
                    },
                )
            }
            if (hasPagerIndicator) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HeroPagerIndicator(
                        pageCount = entries.size,
                        currentPage = selectedIndex,
                    )
                    HomeBadge(
                        text = "${selectedIndex + 1}/${entries.size}",
                        iconRes = entries[selectedIndex].kind.iconRes,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeroCard(
    entry: HomeHeroEntry,
    bottomInset: androidx.compose.ui.unit.Dp,
    onClick: (Content, Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = remember(context.applicationContext) { AppSettings(context.applicationContext) }
    val isPanoramaCoverEnabled by settings.observeAsState(AppSettings.KEY_PANORAMA_ENABLED) { isPanoramaCoverEnabled }
    val content = entry.content
    val imageRequest = remember(content.coverUrl, content.id) {
        ImageRequest.Builder(context)
            .data(content.coverUrl)
            .crossfade(true)
            .apply { mangaExtra(content) }
            .build()
    }
    var coverBounds by remember(entry.kind, content.id) { mutableStateOf<Rect?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick(content, coverBounds) },
    ) {
        if (isPanoramaCoverEnabled) {
            AnimatedPanoramaBackdrop(
                settings = settings,
                model = imageRequest,
                contentAlpha = 0.94f,
                backgroundColor = MaterialTheme.colorScheme.surface,
            )
        } else {
            AsyncImage(
                model = imageRequest,
                contentDescription = content.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.14f),
                            Color.Black.copy(alpha = 0.70f),
                        ),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = bottomInset),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 88.dp, height = 122.dp)
                    .onGloballyPositioned { coordinates ->
                        coverBounds = coordinates.boundsInRoot()
                    }
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)),
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = content.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                if (content.isNsfw()) {
                    ContentCardNsfwBadge(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val supportingText = when (entry.kind) {
                    HomeHeroKind.RESUME -> entry.progressPercent
                        ?.takeIf { it > 0 }
                        ?.let { stringResource(R.string.home_resume_progress, it) }
                    HomeHeroKind.UPDATE -> entry.newChapters
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
                HomeBadge(
                    text = stringResource(entry.kind.labelRes),
                    iconRes = entry.kind.iconRes,
                )
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = content.source.getTitle(context),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                supportingText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
    iconRes: Int,
    items: List<HomeCoverDisplayItem>,
    count: Int,
    posterStyle: org.skepsun.kototoro.core.ui.compose.CompactPosterCardStyle,
    onItemClick: (Content, Rect?) -> Unit,
    onMoreClick: () -> Unit,
    addTopSpacing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val rowState = rememberLazyListState()

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

        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            itemsIndexed(
                items = items.take(12),
                key = { _, item -> item.content.id },
            ) { index, item ->
                HorizontalRailAnimatedVisibility(
                    animationKey = "home_row_${title}_${item.content.id}",
                    index = index,
                    listState = rowState,
                ) { animatedModifier ->
                    HomeCoverRowItem(
                        item = item,
                        posterStyle = posterStyle,
                        onClick = { coverBounds -> onItemClick(item.content, coverBounds) },
                        modifier = animatedModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCoverRowItem(
    item: HomeCoverDisplayItem,
    posterStyle: org.skepsun.kototoro.core.ui.compose.CompactPosterCardStyle,
    onClick: (Rect?) -> Unit,
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
    val imageRequest = remember(content.coverUrl, content.id) {
        ImageRequest.Builder(context)
            .data(content.coverUrl)
            .crossfade(true)
            .apply { mangaExtra(content) }
            .build()
    }
    var coverBounds by remember(content.id) { mutableStateOf<Rect?>(null) }

    Column(
        modifier = modifier
            .width(posterStyle.itemWidth)
            .clickable { onClick(coverBounds) },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(posterStyle.posterHeight)
                .onGloballyPositioned { coordinates ->
                    coverBounds = coordinates.boundsInRoot()
                }
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = content.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (content.isNsfw()) {
                ContentCardNsfwBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }
            item.cardModel?.let { cardModel ->
                ContentCardCornerBadges(
                    badges = rememberHomeBadgesTopLeft(),
                    item = cardModel,
                    corner = Alignment.TopStart,
                    cardRadius = cardRadius,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                ContentCardCornerBadges(
                    badges = rememberHomeBadgesTopRight(cardModel),
                    item = cardModel,
                    corner = Alignment.TopEnd,
                    cardRadius = cardRadius,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
                ContentCardCornerBadges(
                    badges = rememberHomeBadgesBottomLeft(),
                    item = cardModel,
                    corner = Alignment.BottomStart,
                    cardRadius = cardRadius,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
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

private data class HomeCoverDisplayItem(
    val content: Content,
    val cardModel: ContentGridModel?,
)

@Composable
private fun rememberHomeBadgesTopLeft(): Set<String> {
    val context = LocalContext.current
    val settings = remember(context.applicationContext) { AppSettings(context.applicationContext) }
    return settings.observeAsState(AppSettings.KEY_BADGES_TOP_LEFT) { badgesTopLeft }.value
}

@Composable
private fun rememberHomeBadgesBottomLeft(): Set<String> {
    val context = LocalContext.current
    val settings = remember(context.applicationContext) { AppSettings(context.applicationContext) }
    return settings.observeAsState(AppSettings.KEY_BADGES_BOTTOM_LEFT) { badgesBottomLeft }.value
}

@Composable
private fun rememberHomeBadgesTopRight(item: ContentGridModel): Set<String> {
    val context = LocalContext.current
    val settings = remember(context.applicationContext) { AppSettings(context.applicationContext) }
    val badgesTopRight = settings.observeAsState(AppSettings.KEY_BADGES_TOP_RIGHT) { badgesTopRight }.value
    return remember(badgesTopRight, item.counter) {
        if (item.counter > 0 && "counter" !in badgesTopRight) {
            badgesTopRight + "counter"
        } else {
            badgesTopRight
        }
    }
}


@Composable
private fun QuickActionsCard(
    actions: List<HomeQuickAction>,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier) {
        Text(
            text = stringResource(R.string.quick_access),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        BoxWithConstraints(modifier = Modifier.padding(top = 12.dp)) {
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

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    org.skepsun.kototoro.core.ui.glass.GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        style = org.skepsun.kototoro.core.ui.glass.GlassDefaults.subtleStyle(),
        allowRuntimeHaze = false,
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
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
    org.skepsun.kototoro.core.ui.glass.GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        style = org.skepsun.kototoro.core.ui.glass.GlassDefaults.subtleStyle().copy(
            containerAlpha = 0.6f
        ),
        allowRuntimeHaze = false,
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

private fun buildHomeHeroEntries(
    resumeContent: Content?,
    resumeGroupKey: Long?,
    resumeProgressPercent: Int?,
    historyItems: List<org.skepsun.kototoro.home.ui.HomeRecentItem>,
    updateItems: List<org.skepsun.kototoro.home.ui.HomeUpdateItem>,
    recommendationItems: List<org.skepsun.kototoro.home.ui.HomeRecommendationItem>,
): List<HomeHeroEntry> {
    val entries = ArrayList<HomeHeroEntry>(HOME_HERO_TOTAL_LIMIT)
    val seenIds = LinkedHashSet<Long>()

    fun addEntry(entry: HomeHeroEntry) {
        if (entries.size >= HOME_HERO_TOTAL_LIMIT) return
        if (seenIds.add(entry.groupKey)) {
            entries += entry
        }
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
