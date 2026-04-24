package org.skepsun.kototoro.tracker.ui.feed.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.compose.HorizontalRailAnimatedVisibility
import org.skepsun.kototoro.core.ui.compose.compactPosterRailCardStyle
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.list.ui.compose.ContentCardNsfwBadge
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
	val posterStyle = remember(gridScale) { compactPosterRailCardStyle(gridScale) }
	val listState = rememberLazyListState()

	Column(modifier = modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 12.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			Text(
				text = stringResource(R.string.updates),
				style = MaterialTheme.typography.labelLarge,
				fontWeight = FontWeight.SemiBold,
				color = MaterialTheme.colorScheme.onSurface,
			)
			TextButton(
				onClick = onMoreClick,
				contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
			) {
				Text(
					text = stringResource(R.string.more),
					style = MaterialTheme.typography.labelMedium,
				)
			}
		}

		LazyRow(
			state = listState,
			modifier = Modifier.fillMaxWidth(),
			contentPadding = PaddingValues(horizontal = 2.dp),
			horizontalArrangement = Arrangement.spacedBy(10.dp),
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
					FeedUpdatedPosterCard(
						model = contentModel,
						posterStyle = posterStyle,
						onClick = { coverBounds -> onItemClick(contentModel, coverBounds) },
						modifier = animatedModifier,
					)
				}
			}
		}
		
		Spacer(modifier = Modifier.height(8.dp))
	}
}

@Composable
private fun FeedUpdatedPosterCard(
	model: ContentListModel,
	posterStyle: org.skepsun.kototoro.core.ui.compose.CompactPosterCardStyle,
	onClick: (Rect?) -> Unit,
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	val imageRequest = remember(model.id, model.coverUrl) {
		ImageRequest.Builder(context)
			.data(model.coverUrl)
			.crossfade(true)
			.build()
	}
	var coverBounds by remember(model.id) { mutableStateOf<Rect?>(null) }

	Column(
		modifier = modifier
			.width(posterStyle.itemWidth)
			.clickable { onClick(coverBounds) },
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(posterStyle.posterHeight)
				.onGloballyPositioned { coordinates ->
					coverBounds = coordinates.boundsInRoot()
				}
				.clip(MaterialTheme.shapes.medium)
				.background(MaterialTheme.colorScheme.surfaceVariant),
		) {
			AsyncImage(
				model = imageRequest,
				contentDescription = model.title,
				modifier = Modifier.fillMaxSize(),
				contentScale = ContentScale.Crop,
			)
            if (model.manga.isNsfw()) {
                ContentCardNsfwBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }
		}
		Text(
			text = model.title,
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurface,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
		)
	}
}
