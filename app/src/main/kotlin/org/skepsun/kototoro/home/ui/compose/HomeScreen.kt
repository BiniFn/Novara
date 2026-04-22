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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.compose.compactPosterRailCardStyle
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.home.ui.HomeSummaryState
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val hasHighlights = historyItems.isNotEmpty() || updateItems.isNotEmpty() || recommendationItems.isNotEmpty()
        if (hasHighlights) {
            HomeHighlightsSections(
                historyItems = historyItems,
                recentHistoryCount = state.recentHistoryCount,
                updateItems = updateItems,
                unreadUpdatesCount = state.unreadUpdatesCount,
                recommendationItems = recommendationItems,
                recommendationsCount = state.recommendationsCount,
                posterStyle = posterStyle,
                onItemClick = onContentClick,
                onViewAllRecentClick = onViewAllRecentClick,
                onViewAllUpdatesClick = onViewAllUpdatesClick,
                onViewAllRecommendationsClick = onViewAllRecommendationsClick,
            )
        }
        if (!hasHighlights && !state.isInitialized) {
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


@Composable
private fun HomeHighlightsSections(
    historyItems: List<Content>,
    recentHistoryCount: Int,
    updateItems: List<Content>,
    unreadUpdatesCount: Int,
    recommendationItems: List<Content>,
    recommendationsCount: Int,
    posterStyle: org.skepsun.kototoro.core.ui.compose.CompactPosterCardStyle,
    onItemClick: (Content, Rect?) -> Unit,
    onViewAllRecentClick: () -> Unit,
    onViewAllUpdatesClick: () -> Unit,
    onViewAllRecommendationsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    org.skepsun.kototoro.core.ui.glass.GlassSurface(
        modifier = modifier.fillMaxWidth(),
        style = org.skepsun.kototoro.core.ui.glass.GlassDefaults.subtleStyle(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        allowRuntimeHaze = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            var firstSection = true

            if (historyItems.isNotEmpty()) {
                HomeContentRowSection(
                    title = stringResource(R.string.recent_history),
                    iconRes = R.drawable.ic_history,
                    items = historyItems,
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
                    items = updateItems,
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
                    items = recommendationItems,
                    count = recommendationsCount,
                    posterStyle = posterStyle,
                    onItemClick = onItemClick,
                    onMoreClick = onViewAllRecommendationsClick,
                    addTopSpacing = false,
                )
            }
        }
    }
}

@Composable
private fun HomeContentRowSection(
    title: String,
    iconRes: Int,
    items: List<Content>,
    count: Int,
    posterStyle: org.skepsun.kototoro.core.ui.compose.CompactPosterCardStyle,
    onItemClick: (Content, Rect?) -> Unit,
    onMoreClick: () -> Unit,
    addTopSpacing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(
                items = items.take(12),
                key = { it.id },
            ) { item ->
                HomeCoverRowItem(
                    content = item,
                    posterStyle = posterStyle,
                    onClick = { coverBounds -> onItemClick(item, coverBounds) },
                )
            }
        }
    }
}

@Composable
private fun HomeCoverRowItem(
    content: Content,
    posterStyle: org.skepsun.kototoro.core.ui.compose.CompactPosterCardStyle,
    onClick: (Rect?) -> Unit,
) {
    val context = LocalContext.current
    val imageRequest = remember(content.coverUrl, content.id) {
        ImageRequest.Builder(context)
            .data(content.coverUrl)
            .crossfade(true)
            .apply { mangaExtra(content) }
            .build()
    }
    var coverBounds by remember(content.id) { mutableStateOf<Rect?>(null) }

    Column(
        modifier = Modifier
            .width(posterStyle.itemWidth)
            .clickable { onClick(coverBounds) },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(posterStyle.posterHeight)
                .onGloballyPositioned { coordinates ->
                    coverBounds = coordinates.boundsInRoot()
                }
                .clip(RoundedCornerShape(posterStyle.cornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = content.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
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
