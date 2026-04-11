package org.skepsun.kototoro.tracker.ui.feed.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.skepsun.kototoro.R
import org.skepsun.kototoro.tracker.ui.feed.model.FeedItem

@Composable
fun FeedItemCard(
	item: FeedItem,
	onClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(horizontal = 16.dp, vertical = 16.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		AsyncImage(
			model = ImageRequest.Builder(LocalContext.current)
				.data(item.imageUrl)
				.crossfade(true)
				.build(),
			contentDescription = item.title,
			contentScale = ContentScale.Crop,
			modifier = Modifier
				.size(40.dp)
				.clip(MaterialTheme.shapes.medium)
		)

		Spacer(modifier = Modifier.width(16.dp))

		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = item.title,
				style = MaterialTheme.typography.titleSmall,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = if (item.isNew) "New update" else "${item.count} unread chapters",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}
