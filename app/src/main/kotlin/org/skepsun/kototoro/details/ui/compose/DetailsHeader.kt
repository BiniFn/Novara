package org.skepsun.kototoro.details.ui.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.FavouriteCategory
import org.skepsun.kototoro.core.ui.compose.rememberResolvedContentSource
import org.skepsun.kototoro.core.ui.compose.rememberResolvedSourceTitle
import org.skepsun.kototoro.core.ui.glass.GlassDefaults
import org.skepsun.kototoro.core.ui.glass.GlassSurface
import org.skepsun.kototoro.core.model.iconResId
import org.skepsun.kototoro.core.model.titleResId
import org.skepsun.kototoro.details.data.ContentDetails
import org.skepsun.kototoro.details.ui.model.HistoryInfo
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentTag
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsHeader(
    mangaDetails: ContentDetails?,
    historyInfo: HistoryInfo,
    favouriteCategories: Set<FavouriteCategory>,
    translatedTitle: String?,
    translatedDescription: String?,
    isShowingTranslation: Boolean,
    hasTranslationCache: Boolean,
    isTranslating: Boolean,
    collapseProgress: Float,
    onCoverBoundsSync: (Rect, Float) -> Unit,
    onInfoCardTopSync: (Float) -> Unit,
    onCoverClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onSourceClick: (ContentSource) -> Unit,
    onAuthorClick: (String) -> Unit,
    onTagClick: (ContentTag) -> Unit,
    onTranslateClick: () -> Unit,
    onToggleTranslationClick: () -> Unit,
) {
    val content = mangaDetails?.toContent()
    val resolvedSource = content?.source?.let { rememberResolvedContentSource(it) }
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
    val chapterProgressLabel = when {
        historyInfo.totalChapters > 0 && historyInfo.currentChapter >= 0 -> "${historyInfo.currentChapter + 1}/${historyInfo.totalChapters}"
        historyInfo.totalChapters > 0 -> "0/${historyInfo.totalChapters}"
        else -> "-"
    }
    val isFavourite = favouriteCategories.isNotEmpty()
    val contentRating = content?.contentRating
    val alternateTitles = buildList {
        if (isShowingTranslation && originalTitle.isNotBlank() && originalTitle != displayTitle) {
            add(originalTitle)
        }
        addAll(content?.altTitles.orEmpty().filter { it.isNotBlank() && it != displayTitle })
    }.distinct()
    val ratingLabel = content
        ?.takeIf { it.hasRating }
        ?.let { String.format(defaultLocale, "%.1f", it.rating * 10f) }
    val state = content?.state
    val progressLabel = if (historyInfo.history != null) {
        "${(historyInfo.percent * 100f).roundToInt()}%"
    } else {
        "-"
    }
    val sourceTitle = content?.source?.let { rememberResolvedSourceTitle(it) }.orEmpty()
    val coverCollapseProgress = (collapseProgress / 0.48f).coerceIn(0f, 1f)
    val coverAlpha = 1f - coverCollapseProgress
    val textCollapseProgress = ((collapseProgress - 0.08f) / 0.44f).coerceIn(0f, 1f)
    val actionsCollapseProgress = ((collapseProgress - 0.18f) / 0.36f).coerceIn(0f, 1f)
    val infoItems = buildList {
        content?.let {
            add(
                DetailsInfoItem(
                    label = stringResource(R.string.source),
                    value = sourceTitle,
                    iconRes = R.drawable.ic_manga_source,
                    onClick = resolvedSource?.let { source -> { onSourceClick(source) } },
                ),
            )
            add(
                DetailsInfoItem(
                    label = stringResource(R.string.author),
                    value = author,
                    iconRes = R.drawable.ic_info_outline,
                    onClick = if (it.authors.isNotEmpty()) {
                        { onAuthorClick(it.authors.first()) }
                    } else {
                        null
                    },
                ),
            )
        }
        add(
            DetailsInfoItem(
                label = stringResource(R.string.state),
                value = state?.let { stringResource(it.titleResId) } ?: stringResource(R.string.unknown),
                iconRes = state?.iconResId ?: R.drawable.ic_info_outline,
            ),
        )
        add(
            DetailsInfoItem(
                label = stringResource(R.string.progress),
                value = progressLabel,
                iconRes = R.drawable.ic_read,
            ),
        )
        add(
            DetailsInfoItem(
                label = stringResource(R.string.language),
                value = language.ifBlank { stringResource(R.string.unknown) },
                iconRes = R.drawable.ic_language,
            ),
        )
        add(
            DetailsInfoItem(
                label = stringResource(R.string.chapters),
                value = chapterProgressLabel,
                iconRes = R.drawable.ic_book_page,
            ),
        )
        ratingLabel?.let {
            add(
                DetailsInfoItem(
                    label = stringResource(R.string.rating),
                    value = it,
                    iconRes = R.drawable.ic_star_small,
                ),
            )
        }
    }
    val heroBadges = buildList {
        state?.let {
            add(
                DetailsHeroBadgeSpec(
                    text = stringResource(it.titleResId),
                    iconRes = it.iconResId,
                ),
            )
        }
        language.takeIf { it.isNotBlank() }?.let {
            add(
                DetailsHeroBadgeSpec(
                    text = it,
                    iconRes = R.drawable.ic_language,
                ),
            )
        }
        ratingLabel?.let {
            add(
                DetailsHeroBadgeSpec(
                    text = it,
                    iconRes = R.drawable.ic_star_small,
                ),
            )
        }
        if (contentRating != null) {
            add(
                DetailsHeroBadgeSpec(
                    text = stringResource(contentRating.titleResId),
                ),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            DetailsCoverFrame(
                coverUrl = mangaDetails?.coverUrl,
                contentDescription = displayTitle,
                onCoverBoundsSync = onCoverBoundsSync,
                alpha = coverAlpha,
                onClick = onCoverClick,
                modifier = Modifier.graphicsLayer {
                    alpha = coverAlpha
                    translationX = -48f * coverCollapseProgress
                },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        alpha = 1f - textCollapseProgress
                        translationX = -32f * textCollapseProgress
                    },
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
                if (heroBadges.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        heroBadges.forEach { badge ->
                            DetailsHeroBadge(
                                text = badge.text,
                                iconRes = badge.iconRes,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - actionsCollapseProgress
                        translationX = -18f * actionsCollapseProgress
                    },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailsHeaderIconButton(
                        iconRes = if (isFavourite) R.drawable.ic_heart else R.drawable.ic_heart_outline,
                        onClick = onFavoriteClick,
                        filled = isFavourite,
                    )
                    DetailsHeaderIconButton(
                        iconRes = R.drawable.ic_translate,
                        onClick = {
                            if (hasTranslationCache) {
                                onToggleTranslationClick()
                            } else {
                                onTranslateClick()
                            }
                        },
                        enabled = !isTranslating,
                    )
                }
            }
        }

        if (infoItems.isNotEmpty()) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        onInfoCardTopSync(coordinates.boundsInRoot().top)
                    },
                style = GlassDefaults.regularStyle(),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.basic_info),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    infoItems.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowItems.forEach { item ->
                                MetadataItem(
                                    label = item.label,
                                    value = item.value,
                                    iconRes = item.iconRes,
                                    modifier = Modifier.weight(1f),
                                    onClick = item.onClick,
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            style = GlassDefaults.regularStyle(),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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

        if (!content?.tags.isNullOrEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.genres),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
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
        }
    }
}

@Composable
private fun DetailsCoverFrame(
    coverUrl: String?,
    contentDescription: String,
    onCoverBoundsSync: (Rect, Float) -> Unit,
    alpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var coverBounds by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(coverBounds, alpha) {
        if (coverBounds.width > 0f && coverBounds.height > 0f) {
            onCoverBoundsSync(coverBounds, alpha)
        }
    }

    Box(
        modifier = modifier
            .width(132.dp)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = Color.Black.copy(alpha = 0.22f),
                spotColor = Color.Black.copy(alpha = 0.28f),
            )
            .clickable(onClick = onClick)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.54f),
                shape = RoundedCornerShape(26.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(26.dp),
            )
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(13f / 18f)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                        shape = RoundedCornerShape(22.dp),
                    )
                    .onGloballyPositioned { coordinates ->
                        coverBounds = coordinates.boundsInRoot()
                    },
            )
            AsyncImage(
                model = coverUrl,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(13f / 18f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(22.dp),
                    ),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun DetailsHeaderIconButton(
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (filled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
        },
        contentColor = if (filled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MetadataItem(
    label: String,
    value: String,
    @DrawableRes iconRes: Int? = null,
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
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconRes?.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailsHeroBadge(
    text: String,
    @DrawableRes iconRes: Int? = null,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconRes?.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class DetailsInfoItem(
    val label: String,
    val value: String,
    @DrawableRes val iconRes: Int? = null,
    val onClick: (() -> Unit)? = null,
)

private data class DetailsHeroBadgeSpec(
    val text: String,
    @DrawableRes val iconRes: Int? = null,
)
