package org.skepsun.kototoro.details.ui.pager.chapters.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.widgets.ChipsView.ChipModel
import org.skepsun.kototoro.details.ui.model.ChapterListItem
import org.skepsun.kototoro.list.ui.model.CollapsibleListHeader
import org.skepsun.kototoro.list.ui.model.ListModel

@Composable
fun ChaptersScreen(
	items: List<ListModel>,
	isGridView: Boolean,
	gridSpanCount: Int,
	selectedItemIds: Set<Long>,
	filterChips: List<ChipModel>,
	isLoading: Boolean,
	emptyMessageResId: Int?,
	onItemClick: (ChapterListItem) -> Unit,
	onItemLongClick: (ChapterListItem) -> Unit,
	onHeaderClick: (CollapsibleListHeader) -> Unit,
	onFilterChipClick: (ChipModel) -> Unit,
	onSelectionActionClick: (Int) -> Unit, // R.id.action_save, action_delete, etc.
	onClearSelection: () -> Unit
) {
	Box(modifier = Modifier.fillMaxSize()) {
		Column(modifier = Modifier.fillMaxSize()) {
		// Filters
		if (filterChips.isNotEmpty()) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 8.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp)
			) {
				filterChips.forEach { chip ->
					FilterChip(
						selected = false,
						onClick = { onFilterChipClick(chip) },
						label = { Text(chip.title?.toString() ?: if (chip.titleResId != 0) androidx.compose.ui.res.stringResource(chip.titleResId) else "") }
					)
				}
			}
		}

		// Content
		Box(modifier = Modifier.fillMaxSize().weight(1f)) {
			if (isLoading) {
				CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
			} else if (items.isEmpty() && emptyMessageResId != null && emptyMessageResId != 0) {
				Text(
					text = androidx.compose.ui.res.stringResource(emptyMessageResId),
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textAlign = androidx.compose.ui.text.style.TextAlign.Center,
					modifier = Modifier
						.align(Alignment.Center)
						.padding(16.dp)
				)
			} else {
				if (isGridView) {
					LazyVerticalGrid(
						columns = GridCells.Fixed(gridSpanCount),
						contentPadding = PaddingValues(16.dp),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalArrangement = Arrangement.spacedBy(8.dp),
						modifier = Modifier.fillMaxSize()
					) {
						items(
							items = items,
							key = { item ->
								when (item) {
									is ChapterListItem -> item.chapter.id
									is CollapsibleListHeader -> "header_${item.groupId}"
									else -> item.hashCode()
								}
							},
							span = { item ->
								if (item is CollapsibleListHeader) GridItemSpan(maxLineSpan) else GridItemSpan(1)
							}
						) { item ->
							when (item) {
								is ChapterListItem -> {
									ChapterGridCard(
										item = item,
										isSelected = selectedItemIds.contains(item.chapter.id),
										onClick = { onItemClick(item) },
										onLongClick = { onItemLongClick(item) }
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
						contentPadding = PaddingValues(bottom = 16.dp),
						modifier = Modifier.fillMaxSize()
					) {
						items(
							items = items,
							key = { item ->
								when (item) {
									is ChapterListItem -> item.chapter.id
									is CollapsibleListHeader -> "header_${item.groupId}"
									else -> item.hashCode()
								}
							}
						) { item ->
							when (item) {
								is ChapterListItem -> {
									ChapterListCard(
										item = item,
										isSelected = selectedItemIds.contains(item.chapter.id),
										onClick = { onItemClick(item) },
										onLongClick = { onItemLongClick(item) }
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

		// Floating Action Bar for Selection
		Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
			androidx.compose.animation.AnimatedVisibility(
				visible = selectedItemIds.isNotEmpty(),
				enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
				exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
				modifier = Modifier.padding(bottom = 16.dp)
			) {
			Surface(
				shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
				color = MaterialTheme.colorScheme.inverseSurface,
				contentColor = MaterialTheme.colorScheme.inverseOnSurface,
				modifier = Modifier.padding(16.dp).windowInsetsPadding(WindowInsets.safeDrawing)
			) {
				Row(
					modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						IconButton(onClick = onClearSelection) {
							Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
						}
						Text(
							text = "${selectedItemIds.size}",
							style = MaterialTheme.typography.titleMedium,
							modifier = Modifier.padding(start = 8.dp)
						)
					}
					Row {
						IconButton(onClick = { onSelectionActionClick(R.id.action_save) }) {
							Icon(painter = painterResource(id = R.drawable.ic_save_ok), contentDescription = "Download")
						}
						IconButton(onClick = { onSelectionActionClick(R.id.action_delete) }) {
							Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
						}
						IconButton(onClick = { onSelectionActionClick(R.id.action_mark_current) }) {
							Icon(painter = painterResource(id = R.drawable.ic_current_chapter), contentDescription = "Mark Current")
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
			.clickable(enabled = header.isCollapsible, onClick = { onClick() })
			.padding(horizontal = 16.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = header.text.toString(),
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.primary,
			modifier = Modifier.weight(1f)
		)
		if (header.isCollapsible) {
			Icon(
				imageVector = Icons.Default.ArrowDropDown,
				contentDescription = if (!header.isExpanded) "Expand" else "Collapse",
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.rotate(if (!header.isExpanded) -90f else 0f)
			)
		}
	}
}
