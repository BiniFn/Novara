package org.skepsun.kototoro.list.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.list.ui.model.ContentCompactListModel
import org.skepsun.kototoro.list.ui.model.ContentDetailedListModel
import org.skepsun.kototoro.list.ui.model.ContentGridModel
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.ListModel
import kotlin.math.roundToInt

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
    onItemClick: (ContentListModel) -> Unit,
    onItemLongClick: (ContentListModel) -> Unit,
    onClearSelection: () -> Unit,
    onSelectionAction: (SelectionAction) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val pullRefreshState = rememberPullToRefreshState()

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            if (items.isEmpty() && !isRefreshing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No content available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                when (listMode) {
                    ListMode.GRID -> {
                        // Compute adaptive span count using screen width and gridScale multiplier
                        val configuration = LocalConfiguration.current
                        val screenWidthDp = configuration.screenWidthDp
                        val baseGridWidthDp = 110f // Matches typical preferred_grid_width
                        val scaledGridWidthDp = baseGridWidthDp * (1f / gridScale.coerceAtLeast(0.1f))
                        val computedColumns = (screenWidthDp / scaledGridWidthDp).roundToInt().coerceAtLeast(1)

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(computedColumns),
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(items) { listModel ->
                                if (listModel is ContentGridModel) {
                                    KototoroContentCardGrid(
                                        item = listModel,
                                        isSelected = listModel.manga.id in selectedItemsIds,
                                        onClick = { onItemClick(listModel) },
                                        onLongClick = { onItemLongClick(listModel) }
                                    )
                                }
                                
                                // Trigger load more when reaching the end
                                if (listModel == items.lastOrNull()) {
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
                            items(items) { listModel ->
                                if (listModel is ContentCompactListModel) {
                                    KototoroContentCardList(
                                        item = listModel,
                                        isSelected = listModel.manga.id in selectedItemsIds,
                                        onClick = { onItemClick(listModel) },
                                        onLongClick = { onItemLongClick(listModel) }
                                    )
                                }
                                if (listModel == items.lastOrNull()) {
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
                            items(items) { listModel ->
                                if (listModel is ContentDetailedListModel) {
                                    KototoroContentCardDetailedList(
                                        item = listModel,
                                        isSelected = listModel.manga.id in selectedItemsIds,
                                        onClick = { onItemClick(listModel) },
                                        onLongClick = { onItemLongClick(listModel) }
                                    )
                                }
                                if (listModel == items.lastOrNull()) {
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
