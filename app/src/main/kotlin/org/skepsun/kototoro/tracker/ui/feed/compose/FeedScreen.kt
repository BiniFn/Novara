package org.skepsun.kototoro.tracker.ui.feed.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.tracker.ui.feed.model.FeedItem
import org.skepsun.kototoro.tracker.ui.feed.model.UpdatedContentHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
	contentPadding: PaddingValues = PaddingValues(0.dp),
	items: List<ListModel>,
	isRefreshing: Boolean,
	onRefresh: () -> Unit,
	onLoadMore: () -> Unit,
	onFeedItemClick: (FeedItem, Rect?) -> Unit,
	onUpdatedContentItemClick: (ContentListModel, Rect?) -> Unit,
	onUpdatedContentMoreClick: (UpdatedContentHeader) -> Unit,
	modifier: Modifier = Modifier
) {
	val listState = rememberLazyListState()
	
	// Trigger pagination threshold
	val shouldLoadMore by remember {
		derivedStateOf {
			val layoutInfo = listState.layoutInfo
			val totalVisibleItems = layoutInfo.visibleItemsInfo.size
			val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
			lastVisibleItemIndex >= layoutInfo.totalItemsCount - 4 && totalVisibleItems > 0
		}
	}

	LaunchedEffect(shouldLoadMore) {
		if (shouldLoadMore && !isRefreshing) {
			onLoadMore()
		}
	}

	PullToRefreshBox(
		isRefreshing = isRefreshing,
		onRefresh = onRefresh,
		modifier = modifier.fillMaxSize()
	) {
		LazyColumn(
			state = listState,
			contentPadding = PaddingValues(
				top = contentPadding.calculateTopPadding() + 12.dp,
				bottom = contentPadding.calculateBottomPadding(),
				start = 12.dp,
				end = 12.dp,
			),
			modifier = Modifier.fillMaxSize()
		) {
			items(
				items = items,
				key = { item ->
					when (item) {
						is FeedItem -> "feed_${item.id}"
						is UpdatedContentHeader -> "updates_header"
						is LoadingState -> "loading"
						is EmptyState -> "empty"
						else -> item.hashCode().toString()
					}
				}
			) { item ->
				when (item) {
					is FeedItem -> {
						FeedItemCard(
							item = item,
							onClick = { coverBounds -> onFeedItemClick(item, coverBounds) }
						)
					}
					is UpdatedContentHeader -> {
						// Here we render the horizontal carousel of updated contents
						UpdatedContentCarousel(
							header = item,
							onItemClick = onUpdatedContentItemClick,
							onMoreClick = { onUpdatedContentMoreClick(item) }
						)
					}
					// loading and empty states could be mapped to existing Compose components
				}
			}
		}
	}
}
