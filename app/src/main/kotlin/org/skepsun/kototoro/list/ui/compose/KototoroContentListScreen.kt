package org.skepsun.kototoro.list.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.widgets.ChipsView
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.core.ui.compose.KototoroLoadingIndicator
import org.skepsun.kototoro.core.ui.compose.KototoroPullToRefreshBox
import org.skepsun.kototoro.core.ui.compose.VerticalRailAnimatedVisibility
import org.skepsun.kototoro.core.ui.compose.compactPosterCardStyle
import org.skepsun.kototoro.core.ui.compose.rememberVerticalRailScrollIntensity
import org.skepsun.kototoro.list.ui.model.ContentCompactListModel
import org.skepsun.kototoro.list.ui.model.ContentDetailedListModel
import org.skepsun.kototoro.list.ui.model.ContentGridModel
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.ErrorState
import org.skepsun.kototoro.list.ui.model.InfoModel
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.list.ui.model.QuickFilter

private data class ContentListScreenPrefs(
    val showSourceOnCards: Boolean,
    val isVerticalCardListAnimationEnabled: Boolean,
    val cardUiPrefs: ContentCardUiPrefs,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KototoroContentListScreen(
    items: List<ListModel>,
    listMode: ListMode,
    isRefreshing: Boolean,
    showRemoveOption: Boolean = false,
    sharedTransitionEnabled: Boolean = true,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    gridScale: Float,
    selectedItemsIds: Set<Long>,
    onPrepareItemTransition: (ContentListModel, Rect?) -> Unit = { _, _ -> },
    onItemClick: (ContentListModel) -> Unit,
    onItemLongClick: (ContentListModel) -> Unit,
    onClearSelection: () -> Unit,
    onSelectionAction: (SelectionAction) -> Unit,
    onQuickFilterOptionClick: (ListFilterOption) -> Unit = {},
    onEmptyActionClick: () -> Unit = {},
    onRetry: () -> Unit = {},
    showInlineSelectionTopBar: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listHeader: (@Composable () -> Unit)? = null
) {
    val lastContentItem = items.lastOrNull { it is ContentListModel }
    val context = LocalContext.current
    val settings = androidx.compose.runtime.remember(context.applicationContext) { AppSettings(context.applicationContext) }
    val screenPrefs = settings.observeAsState(
        AppSettings.KEY_SHOW_SOURCE_ON_CARDS,
        AppSettings.KEY_VERTICAL_LIST_RAIL_ANIMATION,
        AppSettings.KEY_BADGES_TOP_LEFT,
        AppSettings.KEY_BADGES_TOP_RIGHT,
        AppSettings.KEY_BADGES_BOTTOM_LEFT,
        AppSettings.KEY_BADGES_BOTTOM_RIGHT,
    ) {
        ContentListScreenPrefs(
            showSourceOnCards = isShowSourceOnCards,
            isVerticalCardListAnimationEnabled = isVerticalListRailAnimationEnabled,
            cardUiPrefs = ContentCardUiPrefs(
                badgesTopLeft = badgesTopLeft,
                badgesTopRight = badgesTopRight,
                badgesBottomLeft = badgesBottomLeft,
                badgesBottomRight = badgesBottomRight,
            ),
        )
    }.value
    val showSourceOnCards = screenPrefs.showSourceOnCards
    val isVerticalCardListAnimationEnabled = screenPrefs.isVerticalCardListAnimationEnabled
    val cardUiPrefs = screenPrefs.cardUiPrefs

    val topBarInset = contentPadding.calculateTopPadding()
    val innerPadding = remember(contentPadding, topBarInset) {
        PaddingValues(
            top = 0.dp,
            bottom = contentPadding.calculateBottomPadding(),
            start = contentPadding.calculateLeftPadding(LayoutDirection.Ltr),
            end = contentPadding.calculateRightPadding(LayoutDirection.Ltr),
        )
    }
    Box(modifier = modifier.fillMaxSize().padding(top = topBarInset)) {
        KototoroPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicatorTopInset = innerPadding,
        ) {
            if (items.isEmpty() && !isRefreshing) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.nothing_found),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                when (listMode) {
                    ListMode.GRID -> {
                        val posterStyle = compactPosterCardStyle(gridScale)
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = posterStyle.itemWidth + 12.dp),
                            contentPadding = innerPadding,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (listHeader != null) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    listHeader()
                                }
                            }
                            items(
                                count = items.size,
                                key = { index -> listModelComposeKey(items[index], index) },
                                span = { index ->
                                    val listModel = items[index]
                                    if (listModel is ContentGridModel) {
                                        GridItemSpan(1)
                                    } else {
                                        GridItemSpan(maxLineSpan)
                                    }
                                },
                                contentType = { index ->
                                    if (items[index] is ContentGridModel) "grid_card" else "supplementary"
                                },
                            ) { index ->
                                val listModel = items[index]
                                if (listModel is ContentGridModel) {
                                    KototoroContentCardGrid(
                                        item = listModel,
                                        isSelected = listModel.id in selectedItemsIds,
                                        onClick = { coverBounds ->
                                            onPrepareItemTransition(listModel, coverBounds)
                                            onItemClick(listModel)
                                        },
                                        onLongClick = { onItemLongClick(listModel) },
                                        sharedTransitionEnabled = sharedTransitionEnabled,
                                        showSourceInfo = showSourceOnCards,
                                        gridScale = gridScale,
                                        uiPrefs = cardUiPrefs,
                                    )
                                } else {
                                    SupplementaryListItem(
                                        item = listModel,
                                        listMode = listMode,
                                        gridScale = gridScale,
                                        onQuickFilterOptionClick = onQuickFilterOptionClick,
                                        onEmptyActionClick = onEmptyActionClick,
                                        onRetry = onRetry,
                                    )
                                }

                                if (listModel == lastContentItem) {
                                    LaunchedEffect(listModel) {
                                        onLoadMore()
                                    }
                                }
                            }
                        }
                    }
                    ListMode.LIST -> {
                        val listState = rememberLazyListState()
                        val scrollIntensity = if (isVerticalCardListAnimationEnabled) {
                            rememberVerticalRailScrollIntensity(listState)
                        } else {
                            0f
                        }
                        LazyColumn(
                            state = listState,
                            contentPadding = innerPadding,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (listHeader != null) {
                                item {
                                    listHeader()
                                }
                            }
                            items(
                                count = items.size,
                                key = { index -> listModelComposeKey(items[index], index) },
                                contentType = { index ->
                                    if (items[index] is ContentCompactListModel) "list_card" else "supplementary"
                                },
                            ) { index ->
                                val listModel = items[index]
                                VerticalRailAnimatedVisibility(
                                    animationKey = listModelComposeKey(listModel, index),
                                    index = index,
                                    listState = listState,
                                    isAnimationEnabled = isVerticalCardListAnimationEnabled,
                                    scrollIntensity = scrollIntensity,
                                ) { animatedModifier ->
                                    if (listModel is ContentCompactListModel) {
                                        KototoroContentCardList(
                                            item = listModel,
                                            isSelected = listModel.id in selectedItemsIds,
                                            sharedTransitionEnabled = sharedTransitionEnabled,
                                            uiPrefs = cardUiPrefs,
                                            onClick = { coverBounds ->
                                                onPrepareItemTransition(listModel, coverBounds)
                                                onItemClick(listModel)
                                            },
                                            onLongClick = { onItemLongClick(listModel) },
                                            modifier = animatedModifier,
                                        )
                                    } else {
                                        Box(modifier = animatedModifier) {
                                            SupplementaryListItem(
                                                item = listModel,
                                                listMode = listMode,
                                                gridScale = gridScale,
                                                onQuickFilterOptionClick = onQuickFilterOptionClick,
                                                onEmptyActionClick = onEmptyActionClick,
                                                onRetry = onRetry,
                                            )
                                        }
                                    }
                                }
                                if (listModel == lastContentItem) {
                                    LaunchedEffect(listModel) {
                                        onLoadMore()
                                    }
                                }
                            }
                        }
                    }
                    ListMode.DETAILED_LIST -> {
                        val listState = rememberLazyListState()
                        val scrollIntensity = if (isVerticalCardListAnimationEnabled) {
                            rememberVerticalRailScrollIntensity(listState)
                        } else {
                            0f
                        }
                        LazyColumn(
                            state = listState,
                            contentPadding = innerPadding,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (listHeader != null) {
                                item {
                                    listHeader()
                                }
                            }
                            items(
                                count = items.size,
                                key = { index -> listModelComposeKey(items[index], index) },
                                contentType = { index ->
                                    if (items[index] is ContentDetailedListModel) "detailed_card" else "supplementary"
                                },
                            ) { index ->
                                val listModel = items[index]
                                VerticalRailAnimatedVisibility(
                                    animationKey = listModelComposeKey(listModel, index),
                                    index = index,
                                    listState = listState,
                                    isAnimationEnabled = isVerticalCardListAnimationEnabled,
                                    scrollIntensity = scrollIntensity,
                                ) { animatedModifier ->
                                    if (listModel is ContentDetailedListModel) {
                                        KototoroContentCardDetailedList(
                                            item = listModel,
                                            isSelected = listModel.id in selectedItemsIds,
                                            sharedTransitionEnabled = sharedTransitionEnabled,
                                            uiPrefs = cardUiPrefs,
                                            onClick = { coverBounds ->
                                                onPrepareItemTransition(listModel, coverBounds)
                                                onItemClick(listModel)
                                            },
                                            onLongClick = { onItemLongClick(listModel) },
                                            modifier = animatedModifier,
                                        )
                                    } else {
                                        Box(modifier = animatedModifier) {
                                            SupplementaryListItem(
                                                item = listModel,
                                                listMode = listMode,
                                                gridScale = gridScale,
                                                onQuickFilterOptionClick = onQuickFilterOptionClick,
                                                onEmptyActionClick = onEmptyActionClick,
                                                onRetry = onRetry,
                                            )
                                        }
                                    }
                                }
                                if (listModel == lastContentItem) {
                                    LaunchedEffect(listModel) {
                                        onLoadMore()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selection Contextual TopBar overlay
        AnimatedVisibility(
            visible = showInlineSelectionTopBar && selectedItemsIds.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val selectedModels = items.mapNotNull { it as? ContentListModel }.filter { it.id in selectedItemsIds }
            val isAllNonLocal = selectedModels.none { it.manga.isLocal }

            KototoroSelectionTopBar(
                selectedCount = selectedItemsIds.size,
                isAllNonLocal = isAllNonLocal,
                isSingleSelection = selectedItemsIds.size == 1,
                showRemoveOption = showRemoveOption,
                onClearSelection = onClearSelection,
                onActionClick = onSelectionAction
            )
        }
    }
}

@Composable
private fun SupplementaryListItem(
    item: ListModel,
    listMode: ListMode,
    gridScale: Float,
    onQuickFilterOptionClick: (ListFilterOption) -> Unit,
    onEmptyActionClick: () -> Unit,
    onRetry: () -> Unit,
) {
    when (item) {
        is ListHeader -> ListHeaderItem(item)
        is QuickFilter -> QuickFilterSection(
            quickFilter = item,
            onQuickFilterOptionClick = onQuickFilterOptionClick,
        )
        is InfoModel -> InfoCard(item)
        is EmptyState -> EmptyStateCard(item, onEmptyActionClick)
        is ErrorState -> ErrorStateCard(item, onRetry)
        LoadingState -> LoadingStateItem(listMode = listMode, gridScale = gridScale)
    }
}

@Composable
private fun ListHeaderItem(item: ListHeader) {
    val context = LocalContext.current
    val title = item.getText(context)?.toString().orEmpty()
    if (title.isBlank()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.buttonTextRes != 0) {
            TextButton(onClick = {}) {
                Text(stringResource(item.buttonTextRes))
            }
        }
    }
}

@Composable
private fun QuickFilterSection(
    quickFilter: QuickFilter,
    onQuickFilterOptionClick: (ListFilterOption) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(quickFilter.items, contentType = { "filter_chip" }) { chip ->
            val option = chip.data as? ListFilterOption
            FilterChip(
                selected = chip.isChecked,
                onClick = {
                    if (option != null) {
                        onQuickFilterOptionClick(option)
                    }
                },
                enabled = option != null,
                leadingIcon = chipIcon(chip),
                label = {
                    Text(
                        text = buildChipLabel(chip),
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun InfoCard(item: InfoModel) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(item.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(item.title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(item.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    item: EmptyState,
    onEmptyActionClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val titleText = item.textPrimaryText?.toString()
        Text(
            text = titleText ?: stringResource(item.textPrimary),
            style = MaterialTheme.typography.titleMedium,
        )
        val subtitleText = item.textSecondaryText?.toString()
        if (item.textSecondary != 0 || !subtitleText.isNullOrBlank()) {
            Text(
                text = subtitleText ?: stringResource(item.textSecondary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.actionStringRes != 0) {
            Button(onClick = onEmptyActionClick) {
                Text(stringResource(item.actionStringRes))
            }
        }
    }
}

@Composable
private fun ErrorStateCard(
    item: ErrorState,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(if (item.icon != 0) item.icon else R.drawable.ic_error_large),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.error_occurred),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = item.exception.localizedMessage ?: item.exception.javaClass.simpleName.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.canRetry) {
            Button(onClick = onRetry) {
                Text(stringResource(item.buttonText.takeIf { it != 0 } ?: R.string.retry))
            }
        }
    }
}

@Composable
private fun LoadingStateItem(
    listMode: ListMode,
    gridScale: Float,
) {
    when (listMode) {
        ListMode.GRID -> GridLoadingSkeleton(gridScale = gridScale)
        ListMode.LIST,
        ListMode.DETAILED_LIST -> LinearLoadingSkeleton(isDetailed = listMode == ListMode.DETAILED_LIST)
    }
}

@Composable
private fun GridLoadingSkeleton(
    gridScale: Float,
) {
    val posterStyle = compactPosterCardStyle(gridScale)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(3) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(posterStyle.posterHeight)
                )
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(12.dp)
                )
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .height(12.dp)
                )
            }
        }
    }
}

@Composable
private fun LinearLoadingSkeleton(
    isDetailed: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(if (isDetailed) 96.dp else 84.dp)
                        .height(if (isDetailed) 132.dp else 116.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(14.dp)
                    )
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(12.dp)
                    )
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.84f)
                            .height(12.dp)
                    )
                    if (isDetailed) {
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.58f)
                                .height(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                shape = MaterialTheme.shapes.medium,
            ),
    )
}

@Composable
private fun chipIcon(chip: ChipsView.ChipModel): (@Composable () -> Unit)? {
    if (chip.icon == 0) {
        return null
    }
    return {
        Icon(
            painter = painterResource(chip.icon),
            contentDescription = null,
            tint = if (chip.tint == 0) Color.Unspecified else MaterialTheme.colorScheme.primary,
        )
    }
}

private fun listModelComposeKey(
    listModel: ListModel,
    index: Int,
): String = when (listModel) {
    is ContentListModel -> buildString {
        append(listModel.javaClass.simpleName)
        append(':')
        append(listModel.source.name)
        append(':')
        append(listModel.id)
        append(':')
        append(
            listModel.manga.url
                .ifBlank { listModel.manga.publicUrl }
                .ifBlank { listModel.title },
        )
        append(':')
        append(index)
    }
    is ListHeader -> "header:${listModel.hashCode()}:$index"
    is QuickFilter -> "quick_filter:${listModel.hashCode()}:$index"
    is InfoModel -> "info:${listModel.hashCode()}:$index"
    is EmptyState -> "empty_state:${listModel.hashCode()}:$index"
    is ErrorState -> "error_state:${listModel.hashCode()}:$index"
    LoadingState -> "loading_state:$index"
    else -> "${listModel.javaClass.name}:${listModel.hashCode()}:$index"
}

@Composable
private fun buildChipLabel(chip: ChipsView.ChipModel): String {
    val title = when {
        chip.titleResId != 0 -> stringResource(chip.titleResId)
        chip.title != null -> chip.title.toString()
        else -> ""
    }
    return if (chip.counter > 0) {
        "$title ${chip.counter}"
    } else {
        title
    }
}
