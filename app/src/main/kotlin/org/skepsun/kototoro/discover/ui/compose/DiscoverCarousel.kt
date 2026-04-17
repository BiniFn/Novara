package org.skepsun.kototoro.discover.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.discover.ui.model.DiscoverCarouselRow
import org.skepsun.kototoro.list.ui.compose.KototoroContentCard
import org.skepsun.kototoro.list.ui.model.ContentListModel
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscoverCarousel(
	row: DiscoverCarouselRow,
	onItemClick: (ContentListModel) -> Unit,
	onMoreClick: (org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteCategory) -> Unit,
	modifier: Modifier = Modifier
) {
	val listState = rememberLazyListState()

	Column(modifier = modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			Text(
				text = stringResource(row.category.nameResId),
				style = MaterialTheme.typography.titleMedium
			)
			Text(
				text = stringResource(R.string.more),
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.primary,
				modifier = Modifier.clickable { onMoreClick(row.category) }
			)
		}

		LazyRow(
			state = listState,
			contentPadding = PaddingValues(horizontal = 16.dp),
			horizontalArrangement = Arrangement.spacedBy(12.dp)
		) {
			itemsIndexed(
				items = row.items,
				key = { _, item -> "carousel_${row.category.id}_${(item as? ContentListModel)?.manga?.id ?: item.hashCode()}" }
			) { index, contentModel ->
				(contentModel as? ContentListModel)?.let { model ->
					val itemOffsetFraction by remember(listState, index) {
						derivedStateOf {
							resolveItemOffsetFraction(listState, index)
						}
					}
					val absoluteOffset = itemOffsetFraction.absoluteValue
					KototoroContentCard(
						model = model,
						isListLayout = false,
						onClick = { onItemClick(model) },
						onLongClick = { },
						isSelected = false,
						selectionModeActive = false,
						modifier = Modifier
							.width(124.dp)
							.graphicsLayer {
								translationX = itemOffsetFraction * -14f
								translationY = absoluteOffset * 20f
								val scale = 1f - (absoluteOffset * 0.08f)
								scaleX = scale
								scaleY = scale
								alpha = 1f - (absoluteOffset * 0.18f)
							}
					)
				}
			}
		}

		Spacer(modifier = Modifier.height(16.dp))
	}
}

private fun resolveItemOffsetFraction(
	listState: androidx.compose.foundation.lazy.LazyListState,
	index: Int,
): Float {
	val layoutInfo = listState.layoutInfo
	val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0f
	val viewportWidth = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
	if (viewportWidth <= 0f) {
		return 0f
	}
	val viewportCenter = layoutInfo.viewportStartOffset + (viewportWidth / 2f)
	val itemCenter = itemInfo.offset + (itemInfo.size / 2f)
	return ((itemCenter - viewportCenter) / viewportWidth).coerceIn(-1f, 1f)
}
