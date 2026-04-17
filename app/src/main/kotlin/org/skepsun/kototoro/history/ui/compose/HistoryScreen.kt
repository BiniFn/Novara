package org.skepsun.kototoro.history.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BarChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.skepsun.kototoro.core.model.ContentSource
import java.time.Instant

@Composable
fun HistoryScreen(
    items: List<ListModel>,
    listMode: ListMode,
    isRefreshing: Boolean,
    isStatsEnabled: Boolean,
    gridScale: Float,
    selectedItemsIds: Set<Long>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (ContentListModel) -> Unit,
    onItemLongClick: (ContentListModel) -> Unit,
    onClearSelection: () -> Unit,
    onSelectionAction: (SelectionAction) -> Unit,
    onClearHistoryClick: () -> Unit,
    onStatsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        KototoroContentListScreen(
            items = items,
            listMode = listMode,
            isRefreshing = isRefreshing,
            showRemoveOption = true,
            onRefresh = onRefresh,
            onLoadMore = onLoadMore,
            gridScale = gridScale,
            selectedItemsIds = selectedItemsIds,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onClearSelection = onClearSelection,
            onSelectionAction = onSelectionAction,
            listHeader = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
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
                                    imageVector = Icons.AutoMirrored.Filled.BarChart,
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
    }
}
