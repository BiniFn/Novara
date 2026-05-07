package org.skepsun.kototoro.history.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.list.ui.compose.KototoroContentListScreen
import org.skepsun.kototoro.list.ui.compose.SelectionAction
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.ListModel

@Composable
fun HistoryScreen(
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    items: List<ListModel>,
    listMode: ListMode,
    isRefreshing: Boolean,
    isStatsEnabled: Boolean,
    gridScale: Float,
    selectedItemsIds: Set<Long>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onPrepareItemTransition: (ContentListModel, Rect?) -> Unit,
    onItemClick: (ContentListModel) -> Unit,
    onItemLongClick: (ContentListModel) -> Unit,
    onClearSelection: () -> Unit,
    onSelectionAction: (SelectionAction) -> Unit,
    onClearHistoryClick: () -> Unit,
    onStatsClick: () -> Unit,
    onContinueReadingClick: () -> Unit,
    showContinueReadingButton: Boolean,
    bottomBarOffsetPx: Float = 0f,
    bottomBarHeightPx: Int = 0,
    showInlineSelectionTopBar: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val detailedListState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val fabCollapseProgress = remember(bottomBarOffsetPx, bottomBarHeightPx) {
        if (bottomBarHeightPx <= 0) {
            0f
        } else {
            (bottomBarOffsetPx / bottomBarHeightPx.toFloat()).coerceIn(0f, 1f)
        }
    }
    val isFabExpanded = fabCollapseProgress < 0.5f
    val listContentPadding = remember(contentPadding, showContinueReadingButton) {
        PaddingValues(
            start = contentPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            bottom = contentPadding.calculateBottomPadding() + if (showContinueReadingButton) 88.dp else 0.dp,
        )
    }
    val fabBottomPadding = remember(contentPadding) {
        contentPadding.calculateBottomPadding() + 28.dp
    }
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        KototoroContentListScreen(
            contentPadding = listContentPadding,
            items = items,
            listMode = listMode,
            isRefreshing = isRefreshing,
            showRemoveOption = true,
            onRefresh = onRefresh,
            onLoadMore = onLoadMore,
            gridScale = gridScale,
            selectedItemsIds = selectedItemsIds,
            onPrepareItemTransition = onPrepareItemTransition,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onClearSelection = onClearSelection,
            onSelectionAction = onSelectionAction,
            showInlineSelectionTopBar = showInlineSelectionTopBar,
            gridState = gridState,
            listState = listState,
            detailedListState = detailedListState,
            listHeader = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    AssistChip(
                        onClick = onClearHistoryClick,
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete_all),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        },
                        label = { Text(stringResource(R.string.clear_history)) }
                    )
                    
                    if (isStatsEnabled) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AssistChip(
                            onClick = onStatsClick,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_bar_chart),
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            },
                            label = { Text(stringResource(R.string.statistics)) }
                        )
                    }
                }
            }
        )

        if (showContinueReadingButton && selectedItemsIds.isEmpty()) {
            ExtendedFloatingActionButton(
                onClick = onContinueReadingClick,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_read),
                        contentDescription = null,
                    )
                },
                text = {
                    Text(stringResource(R.string._continue))
                },
                expanded = isFabExpanded,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = fabBottomPadding),
            )
        }
    }
}
