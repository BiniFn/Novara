package org.skepsun.kototoro.home.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.core.model.getLocale
import org.skepsun.kototoro.home.ui.HomeSourceOrigin
import org.skepsun.kototoro.home.ui.HomeSummaryState
import org.skepsun.kototoro.parsers.model.Content

@Composable
fun HomeScreen(
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
    isRandomLoading: Boolean
) {
    val scrollState = rememberScrollState()
    
    // We can handle chrome collapsing by passing scroll listener values to the parent,
    // but for now we'll just allow regular scrolling.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .padding(bottom = 24.dp)
    ) {
        // Since we migrated from GridLayout, we can just use columns and rows
        
        // 1. Combined Recent Content
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            shape = RoundedCornerShape(12.dp) // standard Material 3 shape
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                // History
                SectionHeader(
                    title = stringResource(R.string.recent_history),
                    count = state.recentHistoryCount,
                    onMoreClick = onViewAllRecentClick
                )
                ContentLazyRow(
                    items = state.recentHistoryItems.map { it.content },
                    appSettings = appSettings,
                    onContentClick = onContentClick
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Updates
                SectionHeader(
                    title = stringResource(R.string.home_recent_updates),
                    count = state.unreadUpdatesCount,
                    onMoreClick = onViewAllUpdatesClick
                )
                ContentLazyRow(
                    items = state.recentUpdates.map { it.content },
                    appSettings = appSettings,
                    onContentClick = onContentClick
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Recommendations
                SectionHeader(
                    title = stringResource(R.string.suggestions),
                    count = state.recommendationsCount,
                    onMoreClick = onViewAllRecommendationsClick
                )
                ContentLazyRow(
                    items = state.recommendations.map { it.content },
                    appSettings = appSettings,
                    onContentClick = onContentClick
                )
            }
        }
        
        // 2. Quick Access
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.quick_access),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    QuickAccessButton(
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        text = stringResource(R.string.favourites),
                        iconRes = R.drawable.ic_heart,
                        onClick = onLibraryOpenClick
                    )
                    QuickAccessButton(
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        text = stringResource(R.string.bookmarks),
                        iconRes = R.drawable.ic_bookmark,
                        onClick = onBookmarksClick
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    QuickAccessButton(
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        text = stringResource(R.string.local_storage),
                        iconRes = R.drawable.ic_storage,
                        onClick = onLocalClick
                    )
                    QuickAccessButton(
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        text = stringResource(R.string.downloads),
                        iconRes = R.drawable.ic_download,
                        onClick = onDownloadsClick
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    QuickAccessButton(
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        text = stringResource(R.string.random),
                        iconRes = R.drawable.ic_dice,
                        onClick = onRandomClick,
                        enabled = !isRandomLoading
                    )
                    QuickAccessButton(
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        text = stringResource(R.string.translation_settings),
                        iconRes = R.drawable.ic_language,
                        onClick = onAutoTranslateClick
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    QuickAccessButton(
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        text = stringResource(R.string.reader_settings),
                        iconRes = R.drawable.ic_read,
                        onClick = onReaderSettingsClick
                    )
                    Button( // The settings button has different styling in XML (materialButtonStyle vs Outline)
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        onClick = onSettingsClick,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.settings), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        
        // Two-column layout for remaining items
        Row(modifier = Modifier.fillMaxWidth()) {
            // Sync Status
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(IntrinsicSize.Max)
                    .padding(4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxHeight()) {
                    Text(
                        text = stringResource(R.string.sync_status),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val syncText = when {
                        state.syncState.isWebDavEnabled && state.syncState.isAutoSyncEnabled -> stringResource(R.string.home_sync_status_auto)
                        state.syncState.isWebDavEnabled -> stringResource(R.string.home_sync_status_ready)
                        else -> stringResource(R.string.home_sync_status_not_configured)
                    }
                    Text(
                        text = syncText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    
                    val subtitleText = when {
                        state.syncState.lastUploadTime > 0L -> stringResource(R.string.home_sync_last_upload, state.syncState.lastUploadTime) // Date format simplified
                        state.syncState.isWebDavEnabled -> stringResource(R.string.home_sync_subtitle_ready)
                        else -> stringResource(R.string.home_sync_subtitle_configure)
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    if (state.syncState.isWebDavEnabled) {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onSyncBackupClick) { Text(stringResource(R.string.create_backup)) }
                            TextButton(onClick = onSyncRestoreClick) { Text(stringResource(R.string.restore)) }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onSyncSettingsClick) { Text(stringResource(R.string.settings)) }
                    }
                }
            }
            
            // Sources Overview
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(IntrinsicSize.Max)
                    .padding(4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxHeight()) {
                    Text(
                        text = stringResource(R.string.home_sources_overview),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    for (i in 0..2) {
                        val item = state.sourceBreakdown.getOrNull(i)
                        Text(
                            text = if (item != null) stringResource(R.string.home_source_breakdown_item, getSourceOriginLabel(item.origin), item.count) else stringResource(R.string.home_source_breakdown_empty),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .alpha(if (item != null) 1f else 0.6f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onSourceSettingsClick) { Text(stringResource(R.string.manage)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun getSourceOriginLabel(origin: HomeSourceOrigin): String {
    return when (origin) {
        HomeSourceOrigin.BUILT_IN -> stringResource(R.string.source_type_native)
        HomeSourceOrigin.MIHON -> stringResource(R.string.source_type_mihon)
        HomeSourceOrigin.ANIYOMI -> stringResource(R.string.source_type_aniyomi)
        HomeSourceOrigin.LEGADO -> stringResource(R.string.source_type_legado)
        HomeSourceOrigin.TVBOX -> stringResource(R.string.source_type_tvbox)
        HomeSourceOrigin.EXTERNAL -> stringResource(R.string.external_source)
        HomeSourceOrigin.IREADER -> stringResource(R.string.source_type_ireader)
    }
}

@Composable
private fun QuickAccessButton(
    text: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, onMoreClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onMoreClick) {
            Text(stringResource(R.string.more))
        }
    }
}

@Composable
private fun ContentLazyRow(
    items: List<Content>,
    appSettings: AppSettings,
    onContentClick: (Content) -> Unit
) {
    val showSourceInfo = remember { appSettings.isShowSourceOnCards }
    
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { content ->
            ContentCoverItem(
                content = content,
                showSourceInfo = showSourceInfo,
                onClick = { onContentClick(content) }
            )
        }
    }
}

@Composable
private fun ContentCoverItem(
    content: Content,
    showSourceInfo: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(content.coverUrl) {
        ImageRequest.Builder(context)
            .data(content.coverUrl)
            .crossfade(true)
            .apply {
                mangaExtra(content)
            }
            .build()
    }
    
    // Width and height mapping roughly to @layout/item_home_cover which has width=110dp, height=160dp cover (or wrap content).
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f) // approximate book cover ratio
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = content.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            if (showSourceInfo) {
                val locale = content.source.getLocale()
                val langText = locale?.language?.uppercase()?.takeIf { it.isNotBlank() }
                
                // Add source info overlay if needed
                if (langText != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = langText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        Text(
            text = content.title,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
