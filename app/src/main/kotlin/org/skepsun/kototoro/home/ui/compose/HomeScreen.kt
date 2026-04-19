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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.compose.HeroAutoAdvanceEffect
import org.skepsun.kototoro.core.ui.compose.HeroBackdropCard
import org.skepsun.kototoro.core.ui.compose.HeroBackdropScrim
import org.skepsun.kototoro.core.ui.compose.HeroPagerIndicator
import org.skepsun.kototoro.core.ui.compose.rememberResolvedSourceTitle
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.home.ui.HomeSummaryState
import org.skepsun.kototoro.parsers.model.Content
import kotlin.math.absoluteValue

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    state: HomeSummaryState,
    onContentClick: (Content) -> Unit,
    onSettingsClick: () -> Unit,
    onReaderSettingsClick: () -> Unit,
    onSyncSettingsClick: () -> Unit,
    onViewAllRecentClick: () -> Unit,
    onViewAllUpdatesClick: () -> Unit,
    onViewAllRecommendationsClick: () -> Unit,
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
    val recentItems = remember(state.recentHistoryItems) { state.recentHistoryItems.map { it.content } }
    val updateItems = remember(state.recentUpdates) { state.recentUpdates.map { it.content } }
    val recommendationItems = remember(state.recommendations) { state.recommendations.map { it.content } }
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
    val historyItems = remember(state.resumeState.content, recentItems) {
        buildList {
            state.resumeState.content?.let(::add)
            addAll(recentItems)
        }.distinctBy(Content::id)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(rememberNestedScrollInteropConnection())
            .verticalScroll(scrollState)
            .padding(
                start = systemBarsPadding.calculateLeftPadding(layoutDirection) + 12.dp,
                end = systemBarsPadding.calculateRightPadding(layoutDirection) + 12.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (historyItems.isNotEmpty()) {
            HomeContentCarouselCard(
                title = stringResource(R.string.recent_history),
                iconRes = R.drawable.ic_history,
                items = historyItems,
                count = state.recentHistoryCount,
                onItemClick = onContentClick,
                onMoreClick = onViewAllRecentClick,
            )
        }
        if (updateItems.isNotEmpty()) {
            HomeContentCarouselCard(
                title = stringResource(R.string.home_recent_updates),
                iconRes = R.drawable.ic_updated,
                items = updateItems,
                count = state.unreadUpdatesCount,
                onItemClick = onContentClick,
                onMoreClick = onViewAllUpdatesClick,
            )
        }
        if (recommendationItems.isNotEmpty()) {
            HomeContentCarouselCard(
                title = stringResource(R.string.suggestions),
                iconRes = R.drawable.ic_feed,
                items = recommendationItems,
                count = state.recommendationsCount,
                onItemClick = onContentClick,
                onMoreClick = onViewAllRecommendationsClick,
            )
        }
        val hasNoCarousel = historyItems.isEmpty() && updateItems.isEmpty() &&
            recommendationItems.isEmpty()
        if (hasNoCarousel) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        QuickActionsCard(actions = quickActions)
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContentCarouselCard(
    title: String,
    iconRes: Int,
    items: List<Content>,
    count: Int,
    onItemClick: (Content) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val pageCount = items.size.coerceAtMost(8)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val selectedIndex by remember(items, pagerState) {
        derivedStateOf { pagerState.currentPage.coerceIn(0, pageCount - 1) }
    }
    val selectedItem = items.getOrNull(selectedIndex) ?: items.first()
    val context = LocalContext.current
    val backgroundRequest = remember(selectedItem.coverUrl, selectedItem.id) {
        ImageRequest.Builder(context)
            .data(selectedItem.coverUrl)
            .crossfade(true)
            .apply { mangaExtra(selectedItem) }
            .build()
    }

    HeroAutoAdvanceEffect(
        pagerState = pagerState,
        pageCount = pageCount,
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
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    HomeBadge(
                        text = count.toHeroCountLabel(),
                        iconRes = iconRes,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                    ContentCarouselPoster(
                        content = items[page],
                        pageOffset = pageOffset,
                        onClick = { onItemClick(items[page]) },
                    )
                }
            }
            HeroPagerIndicator(
                pageCount = pageCount,
                currentPage = selectedIndex,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun ContentCarouselPoster(
    content: Content,
    pageOffset: Float,
    onClick: () -> Unit,
) {
    val offsetFraction = pageOffset.absoluteValue.coerceIn(0f, 1f)
    val posterWidth = lerp(72.dp, 66.dp, offsetFraction)
    val posterHeight = lerp(100.dp, 92.dp, offsetFraction)
    val context = LocalContext.current
    val imageRequest = remember(content.coverUrl, content.id) {
        ImageRequest.Builder(context)
            .data(content.coverUrl)
            .crossfade(true)
            .apply { mangaExtra(content) }
            .build()
    }
    val sourceTitle = rememberResolvedSourceTitle(content.source)

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
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = content.title,
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
                    text = content.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sourceTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
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
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
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
    this >= 100 -> "999+"
    else -> toString()
}
