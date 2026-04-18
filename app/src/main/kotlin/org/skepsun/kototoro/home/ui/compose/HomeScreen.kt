package org.skepsun.kototoro.home.ui.compose

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import org.jsoup.Jsoup
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getLocale
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.compose.HeroAutoAdvanceEffect
import org.skepsun.kototoro.core.ui.compose.ContentSourceChipMeta
import org.skepsun.kototoro.core.ui.compose.HeroBackdropCard
import org.skepsun.kototoro.core.ui.compose.HeroBackdropScrim
import org.skepsun.kototoro.core.ui.compose.HeroPagerIndicator
import org.skepsun.kototoro.core.ui.compose.rememberResolvedSourceTitle
import org.skepsun.kototoro.core.ui.compose.rememberSourceChipMeta
import org.skepsun.kototoro.core.ui.glass.GlassDefaults
import org.skepsun.kototoro.core.ui.glass.GlassSurface
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.home.ui.HomeSourceOrigin
import org.skepsun.kototoro.home.ui.HomeTrackingSection
import org.skepsun.kototoro.home.ui.HomeTrackingSpotlightItem
import org.skepsun.kototoro.home.ui.HomeSummaryState
import org.skepsun.kototoro.parsers.model.Content
import kotlin.math.absoluteValue

private enum class HomeHeroTab(
    val titleResId: Int,
    val iconRes: Int,
) {
    HISTORY(R.string.recent_history, R.drawable.ic_history),
    UPDATES(R.string.home_recent_updates, R.drawable.ic_updated),
    SUGGESTIONS(R.string.suggestions, R.drawable.ic_feed),
}

private data class HomeHeroTabSpec(
    val tab: HomeHeroTab,
    val items: List<Content>,
    val count: Int,
    val emptySubtitleResId: Int,
    val onMoreClick: () -> Unit,
)

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
    onTrackingItemClick: (HomeTrackingSpotlightItem) -> Unit,
    onTrackingSectionMoreClick: (HomeTrackingSection) -> Unit,
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
    val heroTrackingSection = state.trackingSections.firstOrNull()

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
        val historyItems = remember(state.resumeState.content, recentItems) {
            buildList {
                state.resumeState.content?.let(::add)
                addAll(recentItems)
            }.distinctBy(Content::id)
        }
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

        heroTrackingSection?.let { section ->
            HomeTrackingHeroSection(
                section = section,
                onItemClick = onTrackingItemClick,
                onMoreClick = { onTrackingSectionMoreClick(section) },
            )
        }

        QuickActionsCard(actions = quickActions)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeTrackingHeroSection(
    section: HomeTrackingSection,
    onItemClick: (HomeTrackingSpotlightItem) -> Unit,
    onMoreClick: () -> Unit,
) {
    val items = section.items
    if (items.isEmpty()) {
        return
    }

    val pagerState = rememberPagerState(pageCount = { items.size })
    val selectedIndex by remember(items, pagerState) {
        derivedStateOf { pagerState.currentPage.coerceIn(0, items.lastIndex) }
    }
    val selectedItem = items[selectedIndex]
    val context = LocalContext.current
    val backgroundRequest = remember(selectedItem.coverUrl, selectedItem.remoteId, selectedItem.service) {
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
        modifier = Modifier.height(184.dp),
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
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HomeBadge(
                        text = stringResource(section.service.titleResId),
                        iconRes = section.service.iconResId,
                    )
                    Text(
                        text = stringResource(section.titleResId),
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
                    TrackingHeroPoster(
                        item = items[page],
                        pageOffset = pageOffset,
                        onClick = { onItemClick(items[page]) },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroPagerIndicator(
                    pageCount = items.size,
                    currentPage = selectedIndex,
                )
                selectedItem.score?.takeIf { it > 0f }?.let { score ->
                    HomeStatPill(
                        label = stringResource(R.string.rating),
                        value = String.format("%.1f", score * 10f),
                    )
                } ?: Spacer(modifier = Modifier.width(1.dp))
            }
        }
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

    val pagerState = rememberPagerState(pageCount = { items.size.coerceAtMost(8) })
    val selectedIndex by remember(items, pagerState) {
        derivedStateOf { pagerState.currentPage.coerceIn(0, items.lastIndex.coerceAtMost(7)) }
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
        pageCount = items.size.coerceAtMost(8),
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
                pageCount = items.size.coerceAtMost(8),
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
                    .clip(RoundedCornerShape(22.dp)),
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
private fun HomeTrackingSpotlight(
    sections: List<HomeTrackingSection>,
    onItemClick: (HomeTrackingSpotlightItem) -> Unit,
    onMoreClick: (HomeTrackingSection) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sections.forEach { section ->
            DashboardCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(section.titleResId),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(section.service.titleResId),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onMoreClick(section) }) {
                        Text(stringResource(R.string.more))
                    }
                }
                TrackingSpotlightRow(
                    items = section.items,
                    onItemClick = onItemClick,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun TrackingHeroPoster(
    item: HomeTrackingSpotlightItem,
    pageOffset: Float,
    onClick: () -> Unit,
) {
    val offsetFraction = pageOffset.absoluteValue.coerceIn(0f, 1f)
    val posterWidth = lerp(72.dp, 66.dp, offsetFraction)
    val posterHeight = lerp(100.dp, 92.dp, offsetFraction)
    val context = LocalContext.current
    val imageRequest = remember(item.coverUrl, item.remoteId, item.service) {
        ImageRequest.Builder(context)
            .data(item.coverUrl)
            .crossfade(true)
            .build()
    }

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
                    text = item.subtitle ?: item.altTitle ?: stringResource(item.service.titleResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeBadge(
                        text = stringResource(item.service.titleResId),
                        iconRes = item.service.iconResId,
                    )
                    item.score?.takeIf { it > 0f }?.let { score ->
                        Text(
                            text = String.format("%.1f", score * 10f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeroSection(
    historyItems: List<Content>,
    updateItems: List<Content>,
    recommendationItems: List<Content>,
    recentHistoryCount: Int,
    favoritesCount: Int,
    recommendationCount: Int,
    unreadUpdatesCount: Int,
    resumeContentId: Long?,
    resumeProgress: Int?,
    onContentClick: (Content) -> Unit,
    onViewAllRecentClick: () -> Unit,
    onViewAllUpdatesClick: () -> Unit,
    onViewAllRecommendationsClick: () -> Unit,
) {
    val tabs = listOf(
        HomeHeroTabSpec(
            tab = HomeHeroTab.HISTORY,
            items = historyItems,
            count = recentHistoryCount,
            emptySubtitleResId = R.string.home_recent_empty_subtitle,
            onMoreClick = onViewAllRecentClick,
        ),
        HomeHeroTabSpec(
            tab = HomeHeroTab.UPDATES,
            items = updateItems,
            count = unreadUpdatesCount,
            emptySubtitleResId = R.string.text_feed_holder,
            onMoreClick = onViewAllUpdatesClick,
        ),
        HomeHeroTabSpec(
            tab = HomeHeroTab.SUGGESTIONS,
            items = recommendationItems,
            count = recommendationCount,
            emptySubtitleResId = R.string.text_suggestion_holder,
            onMoreClick = onViewAllRecommendationsClick,
        ),
    )
    val hasAnyItems = tabs.any { it.items.isNotEmpty() }
    if (!hasAnyItems) {
        DashboardCard {
            Text(
                text = stringResource(R.string.home_placeholder_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.home_placeholder_subtitle),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = onViewAllRecommendationsClick) {
                    Text(stringResource(R.string.suggestions))
                }
            }
        }
        return
    }

    val defaultTab = tabs.firstOrNull { it.items.isNotEmpty() }?.tab ?: HomeHeroTab.HISTORY
    val selectedTabState = remember(defaultTab) { mutableStateOf(defaultTab) }
    val selectedTabSpec = tabs.first { it.tab == selectedTabState.value }
    val selectedItemIdState = remember(selectedTabSpec.tab, selectedTabSpec.items) {
        mutableStateOf(selectedTabSpec.items.firstOrNull()?.id)
    }
    val selectedItem = selectedTabSpec.items.firstOrNull { it.id == selectedItemIdState.value }
        ?: selectedTabSpec.items.firstOrNull()
    val context = LocalContext.current
    val imageRequest = remember(selectedItem?.coverUrl, selectedItem?.id) {
        selectedItem?.let {
            ImageRequest.Builder(context)
                .data(it.coverUrl)
                .crossfade(true)
                .apply { mangaExtra(it) }
                .build()
        }
    }

    HeroBackdropCard(
        modifier = Modifier.fillMaxWidth(),
        minHeight = 344.dp,
        shape = RoundedCornerShape(30.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        elevation = 8.dp,
        background = {
            if (imageRequest != null && selectedItem != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = selectedItem.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(24.dp)
                        .alpha(0.8f),
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.24f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                            ),
                        ),
                    ),
            )
        },
        content = {

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                val isCompactLayout = maxWidth < 620.dp
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isCompactLayout) Arrangement.Start else Arrangement.End,
                    ) {
                        HomeHeroTabRow(
                            tabs = tabs,
                            selectedTab = selectedTabSpec.tab,
                            onTabClick = { selectedTabState.value = it },
                        )
                    }

                    if (selectedItem == null) {
                        EmptyCollectionCard(
                            title = stringResource(selectedTabSpec.tab.titleResId),
                            subtitle = stringResource(selectedTabSpec.emptySubtitleResId),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = selectedTabSpec.onMoreClick) {
                                Text(stringResource(R.string.more))
                            }
                        }
                    } else if (isCompactLayout) {
                        Box {
                            HeroFeatureArtwork(
                                content = selectedItem,
                                onClick = { onContentClick(selectedItem) },
                            )
                            HomeHeroActionRow(
                                activeTab = selectedTabSpec.tab,
                                isResumeContent = selectedTabSpec.tab == HomeHeroTab.HISTORY && selectedItem.id == resumeContentId,
                                resumeProgress = resumeProgress,
                                onPrimaryClick = { onContentClick(selectedItem) },
                                onSecondaryClick = selectedTabSpec.onMoreClick,
                                compact = true,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp),
                            )
                        }
                        HomeHeroDetails(
                            content = selectedItem,
                            activeTab = selectedTabSpec.tab,
                            resumeContentId = resumeContentId,
                            showActions = false,
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            HeroPoster(
                                content = selectedItem,
                                onClick = { onContentClick(selectedItem) },
                            )
                            HomeHeroDetails(
                                content = selectedItem,
                                activeTab = selectedTabSpec.tab,
                                resumeContentId = resumeContentId,
                                showActions = true,
                                modifier = Modifier.weight(1f),
                                onPrimaryClick = { onContentClick(selectedItem) },
                                onSecondaryClick = selectedTabSpec.onMoreClick,
                                resumeProgress = resumeProgress,
                            )
                        }
                    }

                    if (selectedTabSpec.items.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(
                                items = selectedTabSpec.items.take(8),
                                key = { it.id },
                            ) { item ->
                                HeroThumbnail(
                                    content = item,
                                    isSelected = item.id == selectedItem?.id,
                                    compact = isCompactLayout,
                                    onClick = { selectedItemIdState.value = item.id },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun HomeHeroDetails(
    content: Content,
    activeTab: HomeHeroTab,
    resumeContentId: Long?,
    showActions: Boolean,
    onPrimaryClick: () -> Unit = {},
    onSecondaryClick: () -> Unit = {},
    resumeProgress: Int? = null,
    modifier: Modifier = Modifier,
) {
    val isResumeContent = activeTab == HomeHeroTab.HISTORY && content.id == resumeContentId
    val sourceTitle = rememberResolvedSourceTitle(content.source)
    val descriptionText = remember(content.description) {
        content.description
            ?.takeIf { it.isNotBlank() }
            ?.let { Jsoup.parse(it).text().replace(Regex("\\s+"), " ").trim() }
            ?.takeIf { it.isNotBlank() }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = content.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = sourceTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        descriptionText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showActions) {
            HomeHeroActionRow(
                activeTab = activeTab,
                isResumeContent = isResumeContent,
                resumeProgress = resumeProgress,
                onPrimaryClick = onPrimaryClick,
                onSecondaryClick = onSecondaryClick,
                compact = false,
            )
        }
    }
}

@Composable
private fun HomeHeroActionRow(
    activeTab: HomeHeroTab,
    isResumeContent: Boolean,
    resumeProgress: Int?,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val primaryLabel = when {
        isResumeContent && resumeProgress != null -> "${stringResource(R.string._continue)} $resumeProgress%"
        isResumeContent -> stringResource(R.string._continue)
        else -> stringResource(R.string.home_recent_open_details)
    }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeHeroActionButton(
            text = primaryLabel,
            iconRes = if (isResumeContent) R.drawable.ic_read else R.drawable.ic_info_outline,
            onClick = onPrimaryClick,
            filled = true,
            compact = compact,
        )
        HomeHeroActionButton(
            text = stringResource(R.string.more),
            iconRes = activeTab.iconRes,
            onClick = onSecondaryClick,
            filled = false,
            compact = compact,
        )
    }
}

@Composable
private fun HomeHeroActionButton(
    text: String,
    iconRes: Int,
    onClick: () -> Unit,
    filled: Boolean,
    compact: Boolean,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(if (compact) 14.dp else 16.dp),
        color = if (filled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
        },
        border = if (filled) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f))
        },
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 8.dp else 9.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = text,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HomeHeroTabRow(
    tabs: List<HomeHeroTabSpec>,
    selectedTab: HomeHeroTab,
    onTabClick: (HomeHeroTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { spec ->
            val isSelected = spec.tab == selectedTab
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabClick(spec.tab) },
                shape = RoundedCornerShape(999.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)
                },
                border = BorderStroke(
                    1.dp,
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(spec.tab.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(spec.tab.titleResId),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    ) {
                        Text(
                            text = spec.count.toHeroCountLabel(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeroLinkRow(
    unreadUpdatesCount: Int,
    recentHistoryCount: Int,
    onViewAllUpdatesClick: () -> Unit,
    onViewAllRecentClick: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeActionChip(
            text = stringResource(R.string.home_recent_updates),
            count = unreadUpdatesCount,
            onClick = onViewAllUpdatesClick,
        )
        HomeActionChip(
            text = stringResource(R.string.recent_history),
            count = recentHistoryCount,
            onClick = onViewAllRecentClick,
        )
    }
}

@Composable
private fun HomeActionChip(
    text: String,
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f),
            ) {
                Text(
                    text = count.toString(),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
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
        Text(
            text = stringResource(R.string.home_quick_access_subtitle),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(modifier = Modifier.padding(top = 16.dp)) {
            val compact = maxWidth < 680.dp
            val itemsPerRow = when {
                maxWidth >= 800.dp -> 4
                maxWidth >= 620.dp -> 3
                maxWidth >= 360.dp -> 4
                else -> 3
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
private fun HomeUtilityHubCard(
    actions: List<HomeQuickAction>,
    state: HomeSummaryState,
    preferredTrackingSiteLabel: String,
    onDiscoverClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier) {
        Text(
            text = stringResource(R.string.quick_access),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.home_overview_title),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(modifier = Modifier.padding(top = 14.dp)) {
            val itemsPerRow = if (maxWidth >= 360.dp) 4 else 3
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = itemsPerRow,
            ) {
                actions.forEach { action ->
                    QuickAccessButton(
                        action = action,
                        compact = true,
                        modifier = Modifier.weight(1f, fill = true),
                    )
                }
            }
        }
        BoxWithConstraints(modifier = Modifier.padding(top = 14.dp)) {
            val itemsPerRow = if (maxWidth >= 360.dp) 2 else 1
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = itemsPerRow,
            ) {
                OverviewMetricCard(
                    label = stringResource(R.string.favorite_count),
                    value = state.favoritesCount.toString(),
                    iconRes = R.drawable.ic_heart,
                    compact = true,
                    modifier = Modifier.weight(1f, fill = true),
                )
                OverviewMetricCard(
                    label = stringResource(R.string.favourites_categories),
                    value = state.favoriteCategoriesCount.toString(),
                    iconRes = R.drawable.ic_bookmark,
                    compact = true,
                    modifier = Modifier.weight(1f, fill = true),
                )
                OverviewMetricCard(
                    label = stringResource(R.string.enabled_sources),
                    value = state.enabledSourcesCount.toString(),
                    iconRes = R.drawable.ic_storage,
                    compact = true,
                    modifier = Modifier.weight(1f, fill = true),
                )
                OverviewMetricCard(
                    label = stringResource(R.string.preferred_tracking_site),
                    value = preferredTrackingSiteLabel,
                    iconRes = R.drawable.ic_bangumi,
                    compact = true,
                    modifier = Modifier.weight(1f, fill = true),
                )
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clickable(onClick = onDiscoverClick),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = CircleShape,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bangumi),
                        contentDescription = null,
                        modifier = Modifier.padding(9.dp).size(16.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.discover),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = preferredTrackingSiteLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(R.string.more),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun HomePulseCard(
    state: HomeSummaryState,
    preferredTrackingSiteLabel: String,
    onDiscoverClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier) {
        Text(
            text = stringResource(R.string.home_overview_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = preferredTrackingSiteLabel,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(modifier = Modifier.padding(top = 14.dp)) {
            val itemsPerRow = if (maxWidth >= 360.dp) 2 else 1
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = itemsPerRow,
            ) {
                OverviewMetricCard(
                    label = stringResource(R.string.favorite_count),
                    value = state.favoritesCount.toString(),
                    iconRes = R.drawable.ic_heart,
                    modifier = Modifier.weight(1f, fill = true),
                )
                OverviewMetricCard(
                    label = stringResource(R.string.favourites_categories),
                    value = state.favoriteCategoriesCount.toString(),
                    iconRes = R.drawable.ic_bookmark,
                    modifier = Modifier.weight(1f, fill = true),
                )
                OverviewMetricCard(
                    label = stringResource(R.string.enabled_sources),
                    value = state.enabledSourcesCount.toString(),
                    iconRes = R.drawable.ic_storage,
                    modifier = Modifier.weight(1f, fill = true),
                )
                OverviewMetricCard(
                    label = stringResource(R.string.preferred_tracking_site),
                    value = preferredTrackingSiteLabel,
                    iconRes = R.drawable.ic_bangumi,
                    modifier = Modifier.weight(1f, fill = true),
                )
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .clickable(onClick = onDiscoverClick),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = CircleShape,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bangumi),
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.discover),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = preferredTrackingSiteLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.more),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun HomeCollectionSection(
    title: String,
    subtitle: String,
    count: Int,
    items: List<Content>,
    showAsList: Boolean,
    emptyTitle: String,
    emptySubtitle: String,
    appSettings: AppSettings,
    onContentClick: (Content) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DashboardCard(modifier) {
        CollectionSectionHeader(
            title = title,
            subtitle = subtitle,
            count = count,
            onMoreClick = onMoreClick,
        )
        if (items.isEmpty()) {
            EmptyCollectionCard(
                title = emptyTitle,
                subtitle = emptySubtitle,
                modifier = Modifier.padding(top = 14.dp),
            )
        } else if (showAsList) {
            ContentColumn(
                items = items,
                appSettings = appSettings,
                onContentClick = onContentClick,
                modifier = Modifier.padding(top = 14.dp),
            )
        } else {
            ContentLazyRow(
                items = items,
                appSettings = appSettings,
                onContentClick = onContentClick,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun CollectionSectionHeader(
    title: String,
    subtitle: String,
    count: Int,
    onMoreClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f),
            ) {
                Text(
                    text = count.toString(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(onClick = onMoreClick) {
                Text(stringResource(R.string.more))
            }
        }
    }
}

@Composable
private fun UtilitySection(
    state: HomeSummaryState,
    preferredTrackingSiteLabel: String,
    onSyncSettingsClick: () -> Unit,
    onSyncBackupClick: () -> Unit,
    onSyncRestoreClick: () -> Unit,
    onSourceSettingsClick: () -> Unit,
) {
    BoxWithConstraints {
        val isWide = maxWidth >= 760.dp
        val syncCard: @Composable (Modifier) -> Unit = { modifier ->
            DashboardCard(modifier) {
                val context = LocalContext.current
                val syncText = when {
                    state.syncState.isWebDavEnabled && state.syncState.isAutoSyncEnabled -> stringResource(R.string.home_sync_status_auto)
                    state.syncState.isWebDavEnabled -> stringResource(R.string.home_sync_status_ready)
                    else -> stringResource(R.string.home_sync_status_not_configured)
                }
                val subtitleText = when {
                    state.syncState.lastUploadTime > 0L -> stringResource(
                        R.string.home_sync_last_upload,
                        DateUtils.formatDateTime(
                            context,
                            state.syncState.lastUploadTime,
                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
                        ),
                    )
                    state.syncState.isWebDavEnabled -> stringResource(R.string.home_sync_subtitle_ready)
                    else -> stringResource(R.string.home_sync_subtitle_configure)
                }
                UtilityHeader(
                    title = stringResource(R.string.sync_status),
                    badge = syncText,
                    iconRes = R.drawable.ic_sync,
                )
                Text(
                    text = subtitleText,
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (state.syncState.isWebDavEnabled) {
                        TextButton(onClick = onSyncBackupClick) { Text(stringResource(R.string.create_backup)) }
                        TextButton(onClick = onSyncRestoreClick) { Text(stringResource(R.string.restore)) }
                    }
                    TextButton(onClick = onSyncSettingsClick) { Text(stringResource(R.string.settings)) }
                }
            }
        }
        val sourceCard: @Composable (Modifier) -> Unit = { modifier ->
            DashboardCard(modifier) {
                UtilityHeader(
                    title = stringResource(R.string.home_sources_overview),
                    badge = preferredTrackingSiteLabel,
                    iconRes = R.drawable.ic_storage,
                )
                repeat(3) { index ->
                    val item = state.sourceBreakdown.getOrNull(index)
                    Text(
                        text = if (item != null) {
                            stringResource(R.string.home_source_breakdown_item, getSourceOriginLabel(item.origin), item.count)
                        } else {
                            stringResource(R.string.home_source_breakdown_empty)
                        },
                        modifier = Modifier
                            .padding(top = if (index == 0) 12.dp else 6.dp)
                            .alpha(if (item != null) 1f else 0.55f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onSourceSettingsClick) { Text(stringResource(R.string.manage)) }
                }
            }
        }

        if (isWide) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                syncCard(Modifier.weight(1f))
                sourceCard(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                syncCard(Modifier)
                sourceCard(Modifier)
            }
        }
    }
}

@Composable
private fun TrackingSpotlightRow(
    items: List<HomeTrackingSpotlightItem>,
    onItemClick: (HomeTrackingSpotlightItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { "${it.service.name}_${it.remoteId}" }) { item ->
            TrackingSpotlightCard(
                item = item,
                onClick = { onItemClick(item) },
            )
        }
    }
}

@Composable
private fun TrackingSpotlightCard(
    item: HomeTrackingSpotlightItem,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val imageRequest = remember(item.coverUrl, item.remoteId, item.service) {
        ImageRequest.Builder(context)
            .data(item.coverUrl)
            .crossfade(true)
            .build()
    }
    Surface(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
    ) {
        Column(modifier = Modifier.padding(9.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(14.dp)),
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
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
                            ),
                        ),
                )
                HomeBadge(
                    text = stringResource(item.service.titleResId),
                    iconRes = item.service.iconResId,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
                item.score?.takeIf { it > 0f }?.let { score ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_star_small),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = String.format("%.1f", score * 10f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            Text(
                text = item.title,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle ?: item.altTitle ?: stringResource(item.service.titleResId),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UtilityHeader(
    title: String,
    badge: String,
    iconRes: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.64f),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.padding(10.dp).size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = badge,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HeroFeatureArtwork(
    content: Content,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sourceMeta = rememberSourceChipMeta(content.source)
    val imageRequest = remember(content.coverUrl, content.id) {
        ImageRequest.Builder(context)
            .data(content.coverUrl)
            .crossfade(true)
            .apply { mangaExtra(content) }
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.92f)
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
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
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.24f),
                            Color.Black.copy(alpha = 0.62f),
                        ),
                    ),
                ),
        )
        sourceMeta?.let { meta ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(meta.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = meta.text,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPoster(
    content: Content,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val sourceMeta = rememberSourceChipMeta(content.source)
    val imageRequest = remember(content.coverUrl, content.id) {
        ImageRequest.Builder(context)
            .data(content.coverUrl)
            .crossfade(true)
            .apply { mangaExtra(content) }
            .build()
    }

    Box(
        modifier = Modifier
            .width(144.dp)
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
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
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.48f)),
                    ),
                ),
        )
        sourceMeta?.let { meta ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(meta.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = meta.text,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroThumbnail(
    content: Content,
    isSelected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val sourceTitle = rememberResolvedSourceTitle(content.source)
    val context = LocalContext.current
    val imageRequest = remember(content.coverUrl, content.id) {
        ImageRequest.Builder(context)
            .data(content.coverUrl)
            .crossfade(true)
            .apply { mangaExtra(content) }
            .build()
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.56f)
        },
        border = BorderStroke(
            width = if (compact) 0.5.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            },
        ),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 7.dp,
                vertical = if (compact) 6.dp else 7.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = content.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(if (compact) 32.dp else 36.dp)
                    .height(if (compact) 46.dp else 52.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Column(modifier = Modifier.width(if (compact) 64.dp else 72.dp)) {
                Text(
                    text = content.title,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sourceTitle,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

private fun Int.toHeroCountLabel(): String = when {
    this >= 10_000 -> "${this / 1000}k+"
    this >= 1_000 -> "${this / 1000}k"
    this >= 100 -> "999+"
    else -> toString()
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

@Composable
private fun ResumePanel(
    state: HomeSummaryState,
    onContentClick: (Content) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resumeContent = state.resumeState.content
    val progressPercent = state.resumeState.progressPercent
    val sourceTitle = if (resumeContent != null) rememberResolvedSourceTitle(resumeContent.source) else ""
    val context = LocalContext.current
    val imageRequest = remember(resumeContent?.coverUrl, resumeContent?.id) {
        resumeContent?.let {
            ImageRequest.Builder(context)
                .data(it.coverUrl)
                .crossfade(true)
                .apply { mangaExtra(it) }
                .build()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .then(
                if (resumeContent != null) {
                    Modifier.clickable { onContentClick(resumeContent) }
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        if (resumeContent == null || imageRequest == null) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.home_resume_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.home_resume_empty_subtitle),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = resumeContent.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(60.dp)
                        .height(84.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_resume_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = resumeContent.title,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            progressPercent != null -> stringResource(R.string.home_resume_progress, progressPercent)
                            else -> sourceTitle
                        },
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewMetricCard(
    label: String,
    value: String,
    iconRes: Int,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = GlassDefaults.nestedCardColor(),
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, GlassDefaults.nestedCardBorderColor()),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 10.dp else 12.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.padding(if (compact) 7.dp else 8.dp).size(if (compact) 12.dp else 14.dp),
                )
            }
            Column {
                Text(
                    text = value,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = label,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyCollectionCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = GlassDefaults.nestedCardColor(),
        border = BorderStroke(1.dp, GlassDefaults.nestedCardBorderColor()),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        style = GlassDefaults.regularStyle(),
        shape = RoundedCornerShape(24.dp),
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
        color = GlassDefaults.nestedCardColor(),
        border = BorderStroke(1.dp, GlassDefaults.nestedCardBorderColor()),
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
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f),
                ) {
                    Icon(
                        painter = painterResource(action.iconRes),
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(14.dp),
                    )
                }
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
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f),
                ) {
                    Icon(
                        painter = painterResource(action.iconRes),
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(16.dp),
                    )
                }
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
private fun getSourceOriginLabel(origin: HomeSourceOrigin): String = when (origin) {
    HomeSourceOrigin.BUILT_IN -> stringResource(R.string.source_type_native)
    HomeSourceOrigin.MIHON -> stringResource(R.string.source_type_mihon)
    HomeSourceOrigin.ANIYOMI -> stringResource(R.string.source_type_aniyomi)
    HomeSourceOrigin.LEGADO -> stringResource(R.string.source_type_legado)
    HomeSourceOrigin.TVBOX -> stringResource(R.string.source_type_tvbox)
    HomeSourceOrigin.EXTERNAL -> stringResource(R.string.external_source)
    HomeSourceOrigin.IREADER -> stringResource(R.string.source_type_ireader)
}

@Composable
private fun ContentLazyRow(
    items: List<Content>,
    appSettings: AppSettings,
    onContentClick: (Content) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showSourceInfo = remember { appSettings.isShowSourceOnCards }
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.id }) { content ->
            ContentCoverItem(
                content = content,
                showSourceInfo = showSourceInfo,
                onClick = { onContentClick(content) },
            )
        }
    }
}

@Composable
private fun ContentColumn(
    items: List<Content>,
    appSettings: AppSettings,
    onContentClick: (Content) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showSourceInfo = remember { appSettings.isShowSourceOnCards }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { content ->
            ContentListItem(
                content = content,
                showSourceInfo = showSourceInfo,
                onClick = { onContentClick(content) },
            )
        }
    }
}

@Composable
private fun ContentListItem(
    content: Content,
    showSourceInfo: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val sourceTitle = rememberResolvedSourceTitle(content.source)
    val imageRequest = remember(content.coverUrl, content.id) {
        ImageRequest.Builder(context)
            .data(content.coverUrl)
            .crossfade(true)
            .apply { mangaExtra(content) }
            .build()
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = content.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(54.dp)
                    .height(78.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showSourceInfo) {
                    Text(
                        text = sourceTitle,
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentCoverItem(
    content: Content,
    showSourceInfo: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val sourceMeta = rememberSourceChipMeta(content.source)
    val sourceTitle = rememberResolvedSourceTitle(content.source)
    val imageRequest = remember(content.coverUrl, content.id) {
        ImageRequest.Builder(context)
            .data(content.coverUrl)
            .crossfade(true)
            .apply { mangaExtra(content) }
            .build()
    }
    Column(
        modifier = Modifier
            .width(108.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(18.dp)),
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = content.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
                        ),
                    ),
            )
            if (showSourceInfo) {
                sourceMeta?.let { meta ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(meta.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = meta.text,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = content.title,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (showSourceInfo) {
            Text(
                text = sourceTitle,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun <T> AppSettings.observeAsState(
    key: String,
    selector: AppSettings.() -> T,
): State<T> {
    val state = remember { mutableStateOf(selector()) }
    DisposableEffect(key) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                state.value = selector()
            }
        }
        subscribe(listener)
        onDispose { unsubscribe(listener) }
    }
    return state
}
