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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.discover.ui.model.DiscoverCarouselRow
import org.skepsun.kototoro.list.ui.compose.KototoroContentCard
import org.skepsun.kototoro.list.ui.model.ContentListModel

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
			) { _, contentModel ->
				(contentModel as? ContentListModel)?.let { model ->
					KototoroContentCard(
						model = model,
						isListLayout = false,
						onClick = { onItemClick(model) },
						onLongClick = { },
						isSelected = false,
						selectionModeActive = false,
						modifier = Modifier.width(124.dp)
					)
				}
			}
		}

		Spacer(modifier = Modifier.height(16.dp))
	}
}
