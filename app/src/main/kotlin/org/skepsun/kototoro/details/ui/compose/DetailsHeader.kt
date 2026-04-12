package org.skepsun.kototoro.details.ui.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.details.data.ContentDetails

@Composable
fun DetailsHeader(
	mangaDetails: ContentDetails?,
	onCoverBoundsSync: (Rect) -> Unit,
) {
	val content = mangaDetails?.toContent()

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.statusBarsPadding()
			.padding(horizontal = 16.dp, vertical = 24.dp)
	) {
		// 1. Cover Box & Title Area
		Row(
			modifier = Modifier.fillMaxWidth()
		) {
			// Invisible spacer for CoverImageView native bound syncing
			Box(
				modifier = Modifier
					.fillMaxWidth(0.3f)
					.aspectRatio(13f / 18f)
					.onGloballyPositioned { coordinates ->
						onCoverBoundsSync(coordinates.boundsInRoot())
					}
			)

			Spacer(modifier = Modifier.width(16.dp))

			Column(
				modifier = Modifier.weight(1f)
			) {
				Text(
					text = content?.title ?: "Loading...",
					style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
					maxLines = 4,
					overflow = TextOverflow.Ellipsis
				)
					Spacer(modifier = Modifier.height(12.dp))
					
					// Favorite Action Button
					Button(
						onClick = { /* TODO: Hook favorite toggle action */ },
						modifier = Modifier.fillMaxWidth(),
						shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
					) {
						Icon(
							painter = androidx.compose.ui.res.painterResource(
								id = if (mangaDetails?.local != null) org.skepsun.kototoro.R.drawable.ic_heart else org.skepsun.kototoro.R.drawable.ic_heart_outline
							),
							contentDescription = "Favorite",
							modifier = Modifier.size(18.dp)
						)
						Spacer(modifier = Modifier.width(8.dp))
						Text(
							text = if (mangaDetails?.local != null) "In Library" else "Add to Library",
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
					}
				content?.altTitles?.let { altTitles ->
					if (altTitles.isNotEmpty()) {
						Text(
							text = altTitles.joinToString(" • "),
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 2,
							overflow = TextOverflow.Ellipsis
						)
					}
				}
			}
		}

		Spacer(modifier = Modifier.height(24.dp))

		// 2. Statistics Row
		Card(
			modifier = Modifier.fillMaxWidth(),
			shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
			colors = CardDefaults.cardColors(
				containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
			)
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				if (content != null) {
					VerticalStatItem("来源", content.source.name)
					VerticalStatItem("作者", content.authors.firstOrNull() ?: "-")
					VerticalStatItem("翻译", "🇨🇳 中文") // Dummy translation logic
					val chapterCount = mangaDetails.allChapters.size
					VerticalStatItem("章节", "$chapterCount 个章节")
				}
			}
		}

		Spacer(modifier = Modifier.height(16.dp))

		// 3. Tags / Genres
		content?.tags?.let { tags ->
			if (tags.isNotEmpty()) {
				@OptIn(ExperimentalLayoutApi::class)
				FlowRow(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalArrangement = Arrangement.spacedBy(4.dp)
				) {
					tags.forEach { tag ->
						SuggestionChip(
							onClick = { },
							label = { Text(tag.title) },
							colors = SuggestionChipDefaults.suggestionChipColors(
								containerColor = androidx.compose.ui.graphics.Color.Transparent
							),
							border = SuggestionChipDefaults.suggestionChipBorder(
								enabled = true,
								borderColor = MaterialTheme.colorScheme.outline
							)
						)
					}
				}
				Spacer(modifier = Modifier.height(16.dp))
			}
		}

		// 4. Description Section
		mangaDetails?.description?.let { desc ->
			if (desc.isNotBlank()) {
				Text(
					text = "Description",
					style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
					color = MaterialTheme.colorScheme.onSurface,
					modifier = Modifier.padding(bottom = 8.dp)
				)
				SelectionContainer {
					Text(
						text = desc.toString(),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.fillMaxWidth()
					)
				}
			}
		}
	}
}

@Composable
private fun VerticalStatItem(label: String, value: String) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.width(60.dp)
		)
		Text(
			text = value,
			style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
			color = MaterialTheme.colorScheme.onSurface
		)
	}
}
