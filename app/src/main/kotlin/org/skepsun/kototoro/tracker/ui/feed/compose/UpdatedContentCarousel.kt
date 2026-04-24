package org.skepsun.kototoro.tracker.ui.feed.compose

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
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.compose.HorizontalRailAnimatedVisibility
import org.skepsun.kototoro.core.ui.compose.compactPosterCardStyle
import org.skepsun.kototoro.list.ui.compose.KototoroContentCard
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.tracker.ui.feed.model.UpdatedContentHeader

@Composable
fun UpdatedContentCarousel(
	header: UpdatedContentHeader,
	onItemClick: (ContentListModel, Rect?) -> Unit,
	onMoreClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	val context = LocalContext.current
	val settings = remember(context.applicationContext) { AppSettings(context.applicationContext) }
	val gridScale = settings.observeAsState(AppSettings.KEY_GRID_SIZE) { gridSize / 100f }.value
	val posterStyle = remember(gridScale) { compactPosterCardStyle(gridScale) }
	val listState = rememberLazyListState()

	Column(modifier = modifier.fillMaxWidth()) {
		// Header row
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			Text(
				text = stringResource(R.string.updates),
				style = MaterialTheme.typography.titleMedium
			)
			Text(
				text = stringResource(R.string.more),
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.primary,
				modifier = Modifier.clickable(onClick = onMoreClick)
			)
		}

		// Horizontal items list
		LazyRow(
			state = listState,
			contentPadding = PaddingValues(horizontal = 16.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			itemsIndexed(
				items = header.list,
				key = { _, item -> "updated_${item.manga.id}" }
			) { index, contentModel ->
				HorizontalRailAnimatedVisibility(
					animationKey = "updated_${contentModel.id}",
					index = index,
					listState = listState,
				) { animatedModifier ->
					KototoroContentCard(
						model = contentModel,
						isListLayout = false,
						onClick = { coverBounds -> onItemClick(contentModel, coverBounds) },
						onLongClick = { },
						isSelected = false,
						selectionModeActive = false,
						modifier = animatedModifier.width(posterStyle.itemWidth)
					)
				}
			}
		}
		
		Spacer(modifier = Modifier.height(8.dp))
	}
}
