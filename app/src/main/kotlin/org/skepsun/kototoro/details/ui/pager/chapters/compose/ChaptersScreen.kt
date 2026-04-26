package org.skepsun.kototoro.details.ui.pager.chapters.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.details.ui.compose.state.DetailsPaneState
import org.skepsun.kototoro.details.ui.compose.state.rememberDetailsPaneNestedScrollConnection
import org.skepsun.kototoro.core.ui.widgets.ChipsView.ChipModel
import org.skepsun.kototoro.details.ui.model.ChapterListItem
import org.skepsun.kototoro.list.ui.model.CollapsibleListHeader
import org.skepsun.kototoro.list.ui.model.ListModel

@Composable
fun ChaptersScreen(
    items: List<ListModel>,
    isGridView: Boolean,
    isScrollEnabled: Boolean = true,
    detailsPaneState: DetailsPaneState? = null,
    gridSpanCount: Int,
    selectedItemIds: Set<Long>,
    filterChips: List<ChipModel>,
    isLoading: Boolean,
    emptyMessageResId: Int?,
    onItemClick: (ChapterListItem) -> Unit,
    onItemLongClick: (ChapterListItem) -> Unit,
    onHeaderClick: (CollapsibleListHeader) -> Unit,
    onFilterChipClick: (ChipModel) -> Unit,
    onSelectionActionClick: (Int) -> Unit,
    onClearSelection: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val paneNestedScrollConnection = rememberDetailsPaneNestedScrollConnection(
        state = detailsPaneState,
        canChildScrollBackward = {
            if (isGridView) {
                gridState.canScrollBackward
            } else {
                listState.canScrollBackward
            }
        },
    )
    val paneNestedScrollModifier = remember(paneNestedScrollConnection) {
        if (paneNestedScrollConnection != null) {
            Modifier.nestedScroll(paneNestedScrollConnection)
        } else {
            Modifier
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (filterChips.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filterChips.forEach { chip ->
                        FilterChip(
                            selected = false,
                            onClick = { onFilterChipClick(chip) },
                            label = {
                                Text(
                                    chip.title?.toString()
                                        ?: if (chip.titleResId != 0) stringResource(chip.titleResId) else "",
                                )
                            },
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (items.isEmpty() && emptyMessageResId != null && emptyMessageResId != 0) {
                    Text(
                        text = stringResource(emptyMessageResId),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                    )
                } else if (isGridView) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(gridSpanCount),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        userScrollEnabled = isScrollEnabled,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(paneNestedScrollModifier),
                    ) {
                        items(
                            count = items.size,
                            key = { index ->
                                when (val item = items[index]) {
                                    is ChapterListItem -> "chapter_${item.chapter.id}_${item.chapter.url}_${index}"
                                    is CollapsibleListHeader -> "header_${item.groupId}_${index}"
                                    else -> "item_${item::class.java.simpleName}_${index}"
                                }
                            },
                            span = { index ->
                                if (items[index] is CollapsibleListHeader) {
                                    GridItemSpan(maxLineSpan)
                                } else {
                                    GridItemSpan(1)
                                }
                            },
                        ) { index ->
                            when (val item = items[index]) {
                                is ChapterListItem -> {
                                    ChapterGridCard(
                                        item = item,
                                        isSelected = selectedItemIds.contains(item.chapter.id),
                                        onClick = { onItemClick(item) },
                                        onLongClick = { onItemLongClick(item) },
                                    )
                                }

                                is CollapsibleListHeader -> {
                                    CollapsibleHeaderUI(header = item, onClick = { onHeaderClick(item) })
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        userScrollEnabled = isScrollEnabled,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(paneNestedScrollModifier),
                    ) {
                        items(
                            count = items.size,
                            key = { index ->
                                when (val item = items[index]) {
                                    is ChapterListItem -> "chapter_${item.chapter.id}_${item.chapter.url}_${index}"
                                    is CollapsibleListHeader -> "header_${item.groupId}_${index}"
                                    else -> "item_${item::class.java.simpleName}_${index}"
                                }
                            },
                        ) { index ->
                            when (val item = items[index]) {
                                is ChapterListItem -> {
                                    ChapterListCard(
                                        item = item,
                                        isSelected = selectedItemIds.contains(item.chapter.id),
                                        onClick = { onItemClick(item) },
                                        onLongClick = { onItemLongClick(item) },
                                    )
                                }

                                is CollapsibleListHeader -> {
                                    CollapsibleHeaderUI(header = item, onClick = { onHeaderClick(item) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollapsibleHeaderUI(header: CollapsibleListHeader, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = header.isCollapsible, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = header.text.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (header.isCollapsible) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = if (!header.isExpanded) "Expand" else "Collapse",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (!header.isExpanded) -90f else 0f),
            )
        }
    }
}
