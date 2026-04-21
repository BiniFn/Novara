package org.skepsun.kototoro.discover.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.ContentSourceInfo
import org.skepsun.kototoro.core.ui.compose.rememberResolvedSourceTitle
import org.skepsun.kototoro.core.ui.glass.GlassDefaults
import org.skepsun.kototoro.core.ui.glass.GlassSurface
import org.skepsun.kototoro.parsers.model.Content

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingLocalSourcesPanel(
    availableSources: List<ContentSourceInfo>,
    selectedSourceName: String?,
    results: Map<String, LocalSearchState>,
    linkedContent: Content?,
    onSourceSelected: (String) -> Unit,
    onCandidateClick: (Content) -> Unit,
    onRetry: (String) -> Unit,
    onOpenLinked: () -> Unit,
    onUnlinkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (availableSources.isEmpty()) {
        GlassSurface(
            modifier = modifier.fillMaxWidth(),
            style = GlassDefaults.subtleStyle(),
            shape = RoundedCornerShape(20.dp),
        ) {
            Text(
                text = stringResource(R.string.nothing_found),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val selectedIndex = availableSources.indexOfFirst { it.mangaSource.name == selectedSourceName }
        .coerceAtLeast(0)

    LaunchedEffect(selectedSourceName, availableSources) {
        if (selectedSourceName == null && availableSources.isNotEmpty()) {
            onSourceSelected(availableSources.first().mangaSource.name)
        }
    }

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        style = GlassDefaults.subtleStyle(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (linkedContent != null) {
                LinkedBanner(
                    content = linkedContent,
                    onOpen = onOpenLinked,
                    onUnlink = onUnlinkClick,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.0f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                edgePadding = 12.dp,
                indicator = { positions ->
                    if (selectedIndex < positions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(positions[selectedIndex]),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                divider = {},
            ) {
                availableSources.forEachIndexed { index, info ->
                    val sourceTitle = rememberResolvedSourceTitle(info.mangaSource)
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { onSourceSelected(info.mangaSource.name) },
                        text = {
                            Text(
                                text = sourceTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }

            val currentName = availableSources.getOrNull(selectedIndex)?.mangaSource?.name
            val state = currentName?.let { results[it] }
            LocalSearchStateContent(
                state = state,
                onCandidateClick = onCandidateClick,
                onRetry = { currentName?.let(onRetry) },
            )
        }
    }
}

@Composable
private fun LinkedBanner(
    content: Content,
    onOpen: () -> Unit,
    onUnlink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.discover_local_linked),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.details), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onUnlink, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.discover_manage_binding), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun LocalSearchStateContent(
    state: LocalSearchState?,
    onCandidateClick: (Content) -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(164.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            null -> Text(
                text = stringResource(R.string.search),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LocalSearchState.Loading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
            is LocalSearchState.Loaded -> {
                if (state.items.isEmpty()) {
                    Text(
                        text = stringResource(R.string.nothing_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.items, key = { it.id }) { c ->
                            CandidateCard(
                                content = c,
                                onClick = { onCandidateClick(c) },
                            )
                        }
                    }
                }
            }
            is LocalSearchState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = state.throwable.localizedMessage ?: stringResource(R.string.error_occurred),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    content: Content,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        ) {
            if (!content.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = content.coverUrl,
                    contentDescription = content.title,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Text(
            text = content.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
