package org.skepsun.kototoro.home.ui.compose

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getLocale
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.home.ui.HomeSourceOrigin
import org.skepsun.kototoro.home.ui.HomeSummaryState
import org.skepsun.kototoro.parsers.model.Content

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    state: HomeSummaryState,
    appSettings: AppSettings,
    onContentClick: (Content) -> Unit,
    onSettingsClick: () -> Unit,
    onReaderSettingsClick: () -> Unit,
    onSyncSettingsClick: () -> Unit,
    onSyncBackupClick: () -> Unit,
    onSyncRestoreClick: () -> Unit,
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
    val listModeState by appSettings.observeAsState(AppSettings.KEY_LIST_MODE) { listMode }
    val isListMode = listModeState == ListMode.LIST || listModeState == ListMode.DETAILED_LIST
    val layoutDirection = LocalLayoutDirection.current
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
    val quickActions = listOf(
        HomeQuickAction(stringResource(R.string.favourites), R.drawable.ic_heart, onLibraryOpenClick),
        HomeQuickAction(stringResource(R.string.bookmarks), R.drawable.ic_bookmark, onBookmarksClick),
        HomeQuickAction(stringResource(R.string.local_storage), R.drawable.ic_storage, onLocalClick),
        HomeQuickAction(stringResource(R.string.downloads), R.drawable.ic_download, onDownloadsClick),
        HomeQuickAction(stringResource(R.string.random), R.drawable.ic_dice, onRandomClick, !isRandomLoading),
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
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OverviewCard(
            isListMode = isListMode,
            recentHistoryCount = state.recentHistoryCount,
            recentItems = state.recentHistoryItems.map { it.content },
            recentUpdatesCount = state.unreadUpdatesCount,
            updateItems = state.recentUpdates.map { it.content },
            recommendationsCount = state.recommendationsCount,
            recommendationItems = state.recommendations.map { it.content },
            appSettings = appSettings,
            onContentClick = onContentClick,
            onViewAllRecentClick = onViewAllRecentClick,
            onViewAllUpdatesClick = onViewAllUpdatesClick,
            onViewAllRecommendationsClick = onViewAllRecommendationsClick,
        )

        DashboardCard {
            Text(
                text = stringResource(R.string.quick_access),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.home_quick_access_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            BoxWithConstraints(modifier = Modifier.padding(top = 14.dp)) {
                val itemsPerRow = when {
                    maxWidth >= 880.dp -> 4
                    maxWidth >= 600.dp -> 3
                    else -> 2
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = itemsPerRow,
                ) {
                    quickActions.forEach { action ->
                        QuickAccessButton(
                            action = action,
                            modifier = Modifier.weight(1f, fill = true),
                        )
                    }
                }
            }
        }

        DashboardCard {
            Text(
                text = stringResource(R.string.home_overview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            ResumePanel(
                state = state,
                onContentClick = onContentClick,
                modifier = Modifier.padding(top = 14.dp),
            )
            BoxWithConstraints(modifier = Modifier.padding(top = 12.dp)) {
                val itemsPerRow = if (maxWidth >= 680.dp) 4 else 2
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = itemsPerRow,
                ) {
                    OverviewMetricCard(
                        label = stringResource(R.string.favorite_count),
                        value = state.favoritesCount.toString(),
                        modifier = Modifier.weight(1f, fill = true),
                    )
                    OverviewMetricCard(
                        label = stringResource(R.string.favourites_categories),
                        value = state.favoriteCategoriesCount.toString(),
                        modifier = Modifier.weight(1f, fill = true),
                    )
                    OverviewMetricCard(
                        label = stringResource(R.string.enabled_sources),
                        value = state.enabledSourcesCount.toString(),
                        modifier = Modifier.weight(1f, fill = true),
                    )
                    OverviewMetricCard(
                        label = stringResource(R.string.preferred_tracking_site),
                        value = stringResource(state.preferredTrackingSite.titleResId),
                        modifier = Modifier.weight(1f, fill = true),
                    )
                }
            }
        }

        BoxWithConstraints {
            val isWide = maxWidth >= 720.dp
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
                    Text(stringResource(R.string.sync_status), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(syncText, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        subtitleText,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
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
                    Text(stringResource(R.string.home_sources_overview), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    repeat(3) { index ->
                        val item = state.sourceBreakdown.getOrNull(index)
                        Text(
                            text = if (item != null) {
                                stringResource(R.string.home_source_breakdown_item, getSourceOriginLabel(item.origin), item.count)
                            } else {
                                stringResource(R.string.home_source_breakdown_empty)
                            },
                            modifier = Modifier.padding(top = if (index == 0) 12.dp else 4.dp).alpha(if (item != null) 1f else 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onSourceSettingsClick) { Text(stringResource(R.string.manage)) }
                    }
                }
            }
            if (isWide) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    syncCard(Modifier.weight(1f))
                    sourceCard(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    syncCard(Modifier)
                    sourceCard(Modifier)
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(
    isListMode: Boolean,
    recentHistoryCount: Int,
    recentItems: List<Content>,
    recentUpdatesCount: Int,
    updateItems: List<Content>,
    recommendationsCount: Int,
    recommendationItems: List<Content>,
    appSettings: AppSettings,
    onContentClick: (Content) -> Unit,
    onViewAllRecentClick: () -> Unit,
    onViewAllUpdatesClick: () -> Unit,
    onViewAllRecommendationsClick: () -> Unit,
) {
    DashboardCard {
        BoxWithConstraints {
            val showThreeColumns = isListMode && maxWidth >= 900.dp
            if (showThreeColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OverviewSection(Modifier.weight(1f), stringResource(R.string.recent_history), recentHistoryCount, recentItems, true, appSettings, onContentClick, onViewAllRecentClick)
                    VerticalDivider(modifier = Modifier.height(220.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    OverviewSection(Modifier.weight(1f), stringResource(R.string.home_recent_updates), recentUpdatesCount, updateItems, true, appSettings, onContentClick, onViewAllUpdatesClick)
                    VerticalDivider(modifier = Modifier.height(220.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    OverviewSection(Modifier.weight(1f), stringResource(R.string.suggestions), recommendationsCount, recommendationItems, true, appSettings, onContentClick, onViewAllRecommendationsClick)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OverviewSection(Modifier, stringResource(R.string.recent_history), recentHistoryCount, recentItems, isListMode, appSettings, onContentClick, onViewAllRecentClick)
                    OverviewSection(Modifier, stringResource(R.string.home_recent_updates), recentUpdatesCount, updateItems, isListMode, appSettings, onContentClick, onViewAllUpdatesClick)
                    OverviewSection(Modifier, stringResource(R.string.suggestions), recommendationsCount, recommendationItems, isListMode, appSettings, onContentClick, onViewAllRecommendationsClick)
                }
            }
        }
    }
}

@Composable
private fun OverviewSection(
    modifier: Modifier,
    title: String,
    count: Int,
    items: List<Content>,
    showAsList: Boolean,
    appSettings: AppSettings,
    onContentClick: (Content) -> Unit,
    onMoreClick: () -> Unit,
) {
    Column(modifier = modifier) {
        SectionHeader(title = title, count = count, onMoreClick = onMoreClick)
        if (showAsList) {
            ContentColumn(items = items, appSettings = appSettings, onContentClick = onContentClick)
        } else {
            ContentLazyRow(items = items, appSettings = appSettings, onContentClick = onContentClick)
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
    val context = LocalContext.current
    val imageRequest = remember(resumeContent?.coverUrl) {
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
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (resumeContent != null) {
                    Modifier.clickable { onContentClick(resumeContent) }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        if (resumeContent == null || imageRequest == null) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.home_resume_empty_title),
                    style = MaterialTheme.typography.titleSmall,
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
                        .width(52.dp)
                        .height(74.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_resume_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = resumeContent.title,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            progressPercent != null -> stringResource(R.string.home_resume_progress, progressPercent)
                            else -> resumeContent.source.name
                        },
                        modifier = Modifier.padding(top = 4.dp),
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
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DashboardCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

private data class HomeQuickAction(val label: String, val iconRes: Int, val onClick: () -> Unit, val enabled: Boolean = true)

@Composable
private fun QuickAccessButton(action: HomeQuickAction, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = action.onClick,
        modifier = modifier,
        enabled = action.enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(painterResource(action.iconRes), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(action.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun SectionHeader(title: String, count: Int, onMoreClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(count.toString(), modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onMoreClick) { Text(stringResource(R.string.more)) }
    }
}

@Composable
private fun ContentLazyRow(items: List<Content>, appSettings: AppSettings, onContentClick: (Content) -> Unit) {
    val showSourceInfo = remember { appSettings.isShowSourceOnCards }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.id }) { content ->
            ContentCoverItem(content = content, showSourceInfo = showSourceInfo, onClick = { onContentClick(content) })
        }
    }
}

@Composable
private fun ContentColumn(items: List<Content>, appSettings: AppSettings, onContentClick: (Content) -> Unit) {
    val showSourceInfo = remember { appSettings.isShowSourceOnCards }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        items.forEach { content ->
            ContentListItem(content = content, showSourceInfo = showSourceInfo, onClick = { onContentClick(content) })
        }
    }
}

@Composable
private fun ContentListItem(content: Content, showSourceInfo: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val imageRequest = remember(content.coverUrl) {
        ImageRequest.Builder(context).data(content.coverUrl).crossfade(true).apply { mangaExtra(content) }.build()
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = imageRequest,
                contentDescription = content.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(56.dp).height(80.dp).clip(RoundedCornerShape(10.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(content.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (showSourceInfo) {
                    Text(
                        content.source.name,
                        modifier = Modifier.padding(top = 4.dp),
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
private fun ContentCoverItem(content: Content, showSourceInfo: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val imageRequest = remember(content.coverUrl) {
        ImageRequest.Builder(context).data(content.coverUrl).crossfade(true).apply { mangaExtra(content) }.build()
    }
    Column(modifier = Modifier.width(110.dp).clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(12.dp))) {
            AsyncImage(model = imageRequest, contentDescription = content.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (showSourceInfo) {
                content.source.getLocale()?.language?.uppercase()?.takeIf { it.isNotBlank() }?.let { langText ->
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(langText, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Text(content.title, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun <T> AppSettings.observeAsState(key: String, selector: AppSettings.() -> T): State<T> {
    val state = remember { mutableStateOf(selector()) }
    DisposableEffect(key) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) state.value = selector()
        }
        subscribe(listener)
        onDispose { unsubscribe(listener) }
    }
    return state
}
