package org.skepsun.kototoro.tracker.ui.feed.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.ListHeader
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
	selectedGroupTab: BrowseGroupTab,
	selectedSourceTags: Set<SourceTag>,
	onGroupTabSelected: (BrowseGroupTab) -> Unit,
	onSourceTagToggled: (SourceTag) -> Unit,
	modifier: Modifier = Modifier
) {
	val listState = rememberLazyListState()
	val context = LocalContext.current
	val visibleSourceTags = remember(selectedGroupTab) {
		SourceTag.quickFilterEntries.filter(selectedGroupTab::supportsSourceTag)
	}
	
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
				start = 0.dp,
				end = 0.dp,
			),
			modifier = Modifier.fillMaxSize()
		) {
			item(key = "feed_filters") {
				FeedFilterBar(
					selectedGroupTab = selectedGroupTab,
					selectedSourceTags = selectedSourceTags,
					visibleSourceTags = visibleSourceTags,
					onGroupTabSelected = onGroupTabSelected,
					onSourceTagToggled = onSourceTagToggled,
				)
			}
			items(
				items = items,
				key = { item ->
					when (item) {
						is FeedItem -> "feed_${item.id}"
						is UpdatedContentHeader -> "updates_header"
						is ListHeader -> "header_${item.hashCode()}"
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
					is ListHeader -> {
						Text(
							text = item.getText(context)?.toString().orEmpty(),
							style = MaterialTheme.typography.titleMedium,
							color = MaterialTheme.colorScheme.onBackground,
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 16.dp, vertical = 12.dp)
						)
					}
					// loading and empty states could be mapped to existing Compose components
				}
			}
		}
	}
}

@Composable
private fun FeedFilterBar(
	selectedGroupTab: BrowseGroupTab,
	selectedSourceTags: Set<SourceTag>,
	visibleSourceTags: List<SourceTag>,
	onGroupTabSelected: (BrowseGroupTab) -> Unit,
	onSourceTagToggled: (SourceTag) -> Unit,
	modifier: Modifier = Modifier,
) {
	LazyRow(
		modifier = modifier
			.fillMaxWidth()
			.padding(bottom = 8.dp),
		contentPadding = PaddingValues(horizontal = 12.dp),
		horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
	) {
		items(BrowseGroupTab.getAllTabs(), key = { "group_${it.id}" }) { tab ->
			FilterChip(
				selected = selectedGroupTab == tab,
				onClick = { onGroupTabSelected(tab) },
				label = { Text(stringResource(tab.titleRes)) },
				leadingIcon = {
					androidx.compose.material3.Icon(
						painter = painterResource(tab.iconRes),
						contentDescription = null,
					)
				},
			)
		}
		if (visibleSourceTags.isNotEmpty()) {
			item(key = "filters_divider") {
				Text(
					text = "·",
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(horizontal = 2.dp, vertical = 10.dp),
				)
			}
		}
		items(visibleSourceTags, key = { "tag_${it.id}" }) { tag ->
			FilterChip(
				selected = tag in selectedSourceTags,
				onClick = { onSourceTagToggled(tag) },
				label = { Text(stringResource(tag.titleRes)) },
				leadingIcon = {
					androidx.compose.material3.Icon(
						painter = painterResource(tag.iconRes),
						contentDescription = null,
					)
				},
			)
		}
	}
}
