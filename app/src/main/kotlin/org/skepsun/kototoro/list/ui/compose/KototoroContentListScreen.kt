package org.skepsun.kototoro.list.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import org.skepsun.kototoro.core.ui.compose.compactPosterCardStyle
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KototoroContentListScreen(
    items: List<ListModel>,
    listMode: ListMode,
    isRefreshing: Boolean,
    showRemoveOption: Boolean = false,
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
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listHeader: (@Composable () -> Unit)? = null
) {
    val pullRefreshState = rememberPullToRefreshState()
    val lastContentItem = items.lastOrNull { it is ContentListModel }
    val context = LocalContext.current
    val settings = androidx.compose.runtime.remember(context.applicationContext) { AppSettings(context.applicationContext) }
    val showSourceOnCards = settings.observeAsState(AppSettings.KEY_SHOW_SOURCE_ON_CARDS) { isShowSourceOnCards }.value

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
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
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (listHeader != null) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    listHeader()
                                }
                            }
                            items(
                                items = items,
                                span = { listModel ->
                                    if (listModel is ContentGridModel) {
                                        androidx.compose.foundation.lazy.grid.GridItemSpan(1)
                                    } else {
                                        androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan)
                                    }
                                },
                            ) { listModel ->
                                if (listModel is ContentGridModel) {
                                    KototoroContentCardGrid(
                                        item = listModel,
                                        isSelected = listModel.id in selectedItemsIds,
                                        onClick = { coverBounds ->
                                            onPrepareItemTransition(listModel, coverBounds)
                                            onItemClick(listModel)
                                        },
                                        onLongClick = { onItemLongClick(listModel) },
                                        showSourceInfo = showSourceOnCards,
                                        gridScale = gridScale,
                                    )
                                } else {
                                    SupplementaryListItem(
                                        item = listModel,
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
                        LazyColumn(
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (listHeader != null) {
                                item {
                                    listHeader()
                                }
                            }
                            items(items) { listModel ->
                                if (listModel is ContentCompactListModel) {
                                    KototoroContentCardList(
                                        item = listModel,
                                        isSelected = listModel.id in selectedItemsIds,
                                        onClick = { coverBounds ->
                                            onPrepareItemTransition(listModel, coverBounds)
                                            onItemClick(listModel)
                                        },
                                        onLongClick = { onItemLongClick(listModel) }
                                    )
                                } else {
                                    SupplementaryListItem(
                                        item = listModel,
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
                    ListMode.DETAILED_LIST -> {
                        LazyColumn(
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (listHeader != null) {
                                item {
                                    listHeader()
                                }
                            }
                            items(items) { listModel ->
                                if (listModel is ContentDetailedListModel) {
                                    KototoroContentCardDetailedList(
                                        item = listModel,
                                        isSelected = listModel.id in selectedItemsIds,
                                        onClick = { coverBounds ->
                                            onPrepareItemTransition(listModel, coverBounds)
                                            onItemClick(listModel)
                                        },
                                        onLongClick = { onItemLongClick(listModel) }
                                    )
                                } else {
                                    SupplementaryListItem(
                                        item = listModel,
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
                }
            }
        }

        // Selection Contextual TopBar overlay
        AnimatedVisibility(
            visible = selectedItemsIds.isNotEmpty(),
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
        LoadingState -> LoadingStateItem()
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
        items(quickFilter.items) { chip ->
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
        Text(
            text = stringResource(item.textPrimary),
            style = MaterialTheme.typography.titleMedium,
        )
        if (item.textSecondary != 0) {
            Text(
                text = stringResource(item.textSecondary),
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
private fun LoadingStateItem() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        KototoroLoadingIndicator()
    }
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
