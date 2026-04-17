package org.skepsun.kototoro.details.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.FavouriteCategory
import org.skepsun.kototoro.details.data.ContentDetails
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentTag
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsHeader(
    mangaDetails: ContentDetails?,
    favouriteCategories: Set<FavouriteCategory>,
    translatedTitle: String?,
    translatedDescription: String?,
    isShowingTranslation: Boolean,
    hasTranslationCache: Boolean,
    isTranslating: Boolean,
    onCoverBoundsSync: (Rect) -> Unit,
    onFavoriteClick: () -> Unit,
    onSourceClick: (ContentSource) -> Unit,
    onAuthorClick: (String) -> Unit,
    onTagClick: (ContentTag) -> Unit,
    onTranslateClick: () -> Unit,
    onToggleTranslationClick: () -> Unit,
) {
    val content = mangaDetails?.toContent()
    val originalTitle = content?.title.orEmpty()
    val displayTitle = translatedTitle ?: originalTitle
    val displayDescription = translatedDescription ?: mangaDetails?.description?.toString().orEmpty()
    val fallbackDescription = stringResource(R.string.no_description)
    val defaultLocale = Locale.getDefault()
    val author = content?.authors
        ?.firstOrNull()
        ?.takeUnless { it.isBlank() }
        ?: stringResource(R.string.unknown_author)
    val language = mangaDetails?.getLocale()
        ?.getDisplayName(defaultLocale)
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(defaultLocale) else it.toString() }
        .orEmpty()
    val chapterCount = mangaDetails?.allChapters?.size?.toString().orEmpty()
    val isFavourite = favouriteCategories.isNotEmpty()
    val favouriteLabel = if (isFavourite) {
        favouriteCategories.joinToString(" / ") { it.title }
    } else {
        stringResource(R.string.add_to_favourites)
    }
    val translationLabel = when {
        isTranslating -> stringResource(R.string.translating)
        hasTranslationCache -> stringResource(
            if (isShowingTranslation) R.string.details_show_original else R.string.details_show_translation,
        )
        else -> stringResource(R.string.translate_title)
    }
    val alternateTitles = buildList {
        if (isShowingTranslation && originalTitle.isNotBlank() && originalTitle != displayTitle) {
            add(originalTitle)
        }
        addAll(content?.altTitles.orEmpty().filter { it.isNotBlank() && it != displayTitle })
    }.distinct()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .aspectRatio(13f / 18f)
                    .onGloballyPositioned { coordinates ->
                        onCoverBoundsSync(coordinates.boundsInRoot())
                    },
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (alternateTitles.isNotEmpty()) {
                    Text(
                        text = alternateTitles.joinToString(" / "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onFavoriteClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = if (isFavourite) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        },
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isFavourite) R.drawable.ic_heart else R.drawable.ic_heart_outline,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = favouriteLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (hasTranslationCache) {
                                onToggleTranslationClick()
                            } else {
                                onTranslateClick()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isTranslating,
                    ) {
                        Text(
                            text = translationLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (content != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MetadataItem(
                            label = stringResource(R.string.source),
                            value = content.source.name,
                            modifier = Modifier.weight(1f),
                            onClick = { onSourceClick(content.source) },
                        )
                        MetadataItem(
                            label = stringResource(R.string.author),
                            value = author,
                            modifier = Modifier.weight(1f),
                            onClick = if (content.authors.isNotEmpty()) {
                                { onAuthorClick(content.authors.first()) }
                            } else {
                                null
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MetadataItem(
                            label = stringResource(R.string.language),
                            value = language.ifBlank { "-" },
                            modifier = Modifier.weight(1f),
                        )
                        MetadataItem(
                            label = stringResource(R.string.chapters),
                            value = chapterCount.ifBlank { "-" },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (!content?.tags.isNullOrEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content?.tags.orEmpty().forEach { tag ->
                    SuggestionChip(
                        onClick = { onTagClick(tag) },
                        label = { Text(tag.title) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        ),
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.description),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            SelectionContainer {
                Text(
                    text = displayDescription.ifBlank { fallbackDescription },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MetadataItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
