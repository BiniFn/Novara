package org.skepsun.kototoro.discover.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.discover.ui.model.DiscoverCarouselRow
import org.skepsun.kototoro.list.ui.compose.KototoroContentCard
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
	items: List<ListModel>,
	isRefreshing: Boolean,
	isCarousel: Boolean,
	isLoadingOnly: Boolean,
	gridSpanCount: Int,
	onRefresh: () -> Unit,
	onLoadMore: () -> Unit,
	onItemClick: (ContentListModel) -> Unit,
	onCategoryMoreClick: (TrackingSiteCategory) -> Unit,
	modifier: Modifier = Modifier
) {
	if (isLoadingOnly) {
		Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			CircularProgressIndicator()
		}
		return
	}

	PullToRefreshBox(
		isRefreshing = isRefreshing,
		onRefresh = onRefresh,
		modifier = modifier.fillMaxSize()
	) {
		if (isCarousel) {
			val listState = rememberLazyListState()
			LazyColumn(
				state = listState,
				contentPadding = PaddingValues(vertical = 8.dp),
				modifier = Modifier.fillMaxSize()
			) {
				items(
					items = items.filterIsInstance<DiscoverCarouselRow>(),
					key = { it.category.id }
				) { row ->
					DiscoverCarousel(
						row = row,
						onItemClick = onItemClick,
						onMoreClick = onCategoryMoreClick
					)
				}
			}
		} else {
			val gridState = rememberLazyGridState()

			// Trigger pagination threshold for grid
			val shouldLoadMore by remember {
				derivedStateOf {
					val layoutInfo = gridState.layoutInfo
					val totalVisibleItems = layoutInfo.visibleItemsInfo.size
					val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
					lastVisibleItemIndex >= layoutInfo.totalItemsCount - 4 * gridSpanCount && totalVisibleItems > 0
				}
			}

			LaunchedEffect(shouldLoadMore) {
				if (shouldLoadMore && !isRefreshing) {
					onLoadMore()
				}
			}

			LazyVerticalGrid(
				columns = GridCells.Fixed(gridSpanCount),
				state = gridState,
				contentPadding = PaddingValues(16.dp),
				modifier = Modifier.fillMaxSize()
			) {
				// Only render content list models in search grid mode
				val gridItems = items.filterIsInstance<ContentListModel>()
				items(
					items = gridItems,
					key = { it.manga.id }
				) { item ->
					KototoroContentCard(
						model = item,
						isListLayout = false,
						onClick = { onItemClick(item) },
						onLongClick = { },
						isSelected = false,
						selectionModeActive = false
					)
				}
			}
		}
	}
}
