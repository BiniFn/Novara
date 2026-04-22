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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import kotlin.math.roundToInt
import org.skepsun.kototoro.core.model.FavouriteCategory
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.core.ui.compose.rememberResolvedContentSource
import org.skepsun.kototoro.core.ui.compose.rememberResolvedSourceTitle
import org.skepsun.kototoro.core.ui.glass.GlassDefaults
import org.skepsun.kototoro.core.ui.glass.GlassSurface
import org.skepsun.kototoro.core.model.iconResId
import org.skepsun.kototoro.core.model.titleResId
import org.skepsun.kototoro.details.data.ContentDetails
import org.skepsun.kototoro.details.ui.model.HistoryInfo
import org.skepsun.kototoro.details.ui.model.LinkedTrackingItemUiModel
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
    linkedTrackingItems: List<LinkedTrackingItemUiModel>,
    trackingSuggestion: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult?,
    translatedTitle: String?,
    translatedDescription: String?,
    isShowingTranslation: Boolean,
    hasTranslationCache: Boolean,
    isTranslating: Boolean,
    showTranslateAction: Boolean,
    settings: AppSettings,
    collapseProgress: Float,
    coverVisualAlpha: Float,
    coverUrl: String?,
    fallbackCoverUrl: String?,
    onCoverBoundsSync: (Rect, Float) -> Unit,
    onInfoCardTopSync: (Float) -> Unit,
    onCoverClick: (String?) -> Unit,
    onFavoriteClick: () -> Unit,
    onSourceClick: (ContentSource) -> Unit,
    onAuthorClick: (String) -> Unit,
    onTagClick: (ContentTag) -> Unit,
    onTranslateClick: () -> Unit,
    onTranslateLongClick: () -> Unit,
    onToggleTranslationClick: () -> Unit,
    onOpenLinkedTracking: (LinkedTrackingItemUiModel) -> Unit,
    onManageLinkedTracking: (LinkedTrackingItemUiModel) -> Unit,
    onRemoveLinkedTracking: (org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult) -> Unit,
    onBindTrackingSuggestion: (org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult) -> Unit,
    onManageTrackingSuggestion: (org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult) -> Unit,
    onOpenTrackerSearch: () -> Unit,
) {
    val context = LocalContext.current
    val content = mangaDetails?.toContent()
    val resolvedSource = content?.source?.let { rememberResolvedContentSource(it) }
    val originalTitle = content?.title.orEmpty()
    val displayTitle = translatedTitle ?: originalTitle
    val displayDescription = translatedDescription ?: mangaDetails?.description?.toString().orEmpty()
    val fallbackDescription = stringResource(R.string.no_description)
    val scrobblingStatuses = stringArrayResource(R.array.scrobbling_statuses)
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
    
    var hasCoverLoadFailed by remember(coverUrl) { mutableStateOf(false) }
    val currentCoverUrl = if (hasCoverLoadFailed && fallbackCoverUrl != null) {
        fallbackCoverUrl
    } else {
        coverUrl
    }
    android.util.Log.e("DetailsCover", "resolving coverModel with currentCoverUrl=$currentCoverUrl (orig=$coverUrl, fallback=$fallbackCoverUrl)")

    var isDescriptionExpanded by remember { mutableStateOf(false) }

    val coverModel = remember(content?.source?.name, content?.url, currentCoverUrl) {
        ImageRequest.Builder(context)
            .data(currentCoverUrl)
            .crossfade(true)
            .apply { content?.let { mangaExtra(it) } }
            .build()
    }
    val isNsfw = content?.isNsfw() == true
    val coverCollapseProgress = (collapseProgress / 0.48f).coerceIn(0f, 1f)
    val coverSyncAlpha = 1f - coverCollapseProgress
    val coverAlpha = coverSyncAlpha * coverVisualAlpha.coerceIn(0f, 1f)
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
        ratingLabel?.let {
            add(
                DetailsHeroBadgeSpec(
                    text = it,
                    iconRes = R.drawable.ic_star_small,
                ),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            DetailsCoverFrame(
                coverModel = coverModel,
                contentDescription = displayTitle,
                onCoverBoundsSync = onCoverBoundsSync,
                syncAlpha = coverSyncAlpha,
                showNsfwBadge = isNsfw,
                onClick = { onCoverClick(currentCoverUrl) },
                onState = { state ->
                    android.util.Log.e("DetailsCover", "Cover State: $state")
                    if (state is coil3.compose.AsyncImagePainter.State.Error) {
                        android.util.Log.e("DetailsCover", "Cover Error: ${state.result.throwable}")
                        hasCoverLoadFailed = true
                    }
                },
                modifier = Modifier
                    .alpha(coverAlpha),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(1f - textCollapseProgress),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
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
                    modifier = Modifier
                        .alpha(1f - actionsCollapseProgress),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailsHeaderIconButton(
                        iconRes = if (isFavourite) R.drawable.ic_heart else R.drawable.ic_heart_outline,
                        onClick = onFavoriteClick,
                        filled = isFavourite,
                    )
                    DetailsHeaderIconButton(
                        iconRes = R.drawable.ic_sync,
                        onClick = onOpenTrackerSearch,
                    )
                    if (showTranslateAction) {
                        DetailsHeaderIconButton(
                            iconRes = R.drawable.ic_translate,
                            onClick = {
                                if (hasTranslationCache) {
                                    onToggleTranslationClick()
                                } else {
                                    onTranslateClick()
                                }
                            },
                            onLongClick = onTranslateLongClick,
                            enabled = !isTranslating,
                        )
                    }
                }
            }
        }

        linkedTrackingItems.firstOrNull()?.let { linked ->
            DetailsBindingCard(
                badge = stringResource(linked.service.titleResId),
                badgeIconRes = linked.service.iconResId,
                title = linked.title,
                subtitle = buildString {
                    append(stringResource(R.string.discover_local_linked))
                    linked.status?.let {
                        append(" · ")
                        append(scrobblingStatuses.getOrNull(it.ordinal).orEmpty())
                    }
                    linked.rating?.takeIf { it > 0f }?.let {
                        append(" · ")
                        append(String.format(defaultLocale, "%.1f", it * 10f))
                    }
                },
                supportingText = linked.summary,
                coverUrl = linked.coverUrl,
                primaryActionLabel = stringResource(R.string.open_in_browser),
                onPrimaryAction = { onOpenLinkedTracking(linked) },
                secondaryActionLabel = stringResource(R.string.discover_manage_binding),
                onSecondaryAction = { onManageLinkedTracking(linked) },
                tertiaryActionLabel = if (trackingSuggestion?.isLinked == true) stringResource(R.string.remove) else null,
                onTertiaryAction = trackingSuggestion?.takeIf { it.isLinked }?.let { match -> { onRemoveLinkedTracking(match) } },
            )
        }

        trackingSuggestion?.takeIf { !it.isLinked }?.let { suggestion ->
            DetailsBindingCard(
                badge = stringResource(suggestion.service.titleResId),
                badgeIconRes = suggestion.service.iconResId,
                title = suggestion.title,
                subtitle = stringResource(R.string.suggestions),
                supportingText = suggestion.reason,
                primaryActionLabel = stringResource(R.string.add),
                onPrimaryAction = { onBindTrackingSuggestion(suggestion) },
                secondaryActionLabel = stringResource(R.string.discover_manage_binding),
                onSecondaryAction = { onManageTrackingSuggestion(suggestion) },
            )
        }

        if (infoItems.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        onInfoCardTopSync(coordinates.boundsInRoot().top)
                    },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    infoItems.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    // Reading progress bar at bottom of info card
                    if (historyInfo.history != null && historyInfo.percent > 0f) {
                        val progressPercent = (historyInfo.percent * 100f).roundToInt()
                        val isThick = settings.loadingCircleStyle == AppSettings.LoadingCircleStyle.THICK_STRAIGHT ||
                            settings.loadingCircleStyle == AppSettings.LoadingCircleStyle.THICK_WAVY
                        val barHeight = if (isThick) 6.dp else 3.dp
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LinearProgressIndicator(
                                progress = { historyInfo.percent.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            )
                            Text(
                                text = "$progressPercent%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth().clickable { isDescriptionExpanded = !isDescriptionExpanded },
                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!content?.tags.isNullOrEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

private fun Modifier.offsetX(
    maxOffset: Dp,
    progress: Float,
): Modifier = this.then(
    Modifier.offset(x = maxOffset * progress),
)
