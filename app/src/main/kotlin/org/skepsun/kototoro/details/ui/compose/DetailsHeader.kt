package org.skepsun.kototoro.details.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.iconResId
import org.skepsun.kototoro.core.model.containsAdultTagKeyword
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.model.titleResId
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.core.model.FavouriteCategory
import org.skepsun.kototoro.core.model.ContentSourceInfo
import org.skepsun.kototoro.core.ui.compose.ContentSourceIcon
import org.skepsun.kototoro.core.ui.compose.KototoroLoadingIndicator
import org.skepsun.kototoro.core.ui.compose.KototoroLinearProgressIndicator
import org.skepsun.kototoro.core.ui.compose.rememberResolvedSourceTitle
import org.skepsun.kototoro.core.ui.glass.GlassDefaults
import org.skepsun.kototoro.core.ui.glass.GlassSurface
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.core.util.ext.toLocaleOrNull
import org.skepsun.kototoro.details.data.ContentDetails
import org.skepsun.kototoro.discover.ui.details.LocalSearchState
import org.skepsun.kototoro.details.ui.model.DetailsSourceOption
import org.skepsun.kototoro.details.ui.model.DetailsSupplementAction
import org.skepsun.kototoro.details.ui.model.EntityChapterSourceInfo
import org.skepsun.kototoro.details.ui.model.HistoryInfo
import org.skepsun.kototoro.details.ui.model.LinkedTrackingItemUiModel
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItem
import org.skepsun.kototoro.parsers.model.ContentType
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DetailsHeader(
    mangaDetails: ContentDetails?,
    historyInfo: HistoryInfo,
    favouriteCategories: Set<FavouriteCategory>,
    linkedTrackingItems: List<LinkedTrackingItemUiModel>,
    trackingSuggestion: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult?,
    metadataSourceOptions: List<DetailsSourceOption>,
    readingSourceOptions: List<DetailsSourceOption>,
    supplementalActions: List<DetailsSupplementAction>,
    resolvedContentType: ContentType?,
    metadataLanguageCode: String?,
    readingLanguageCode: String?,
    translatedTitle: String?,
    translatedDescription: String?,
    isShowingTranslation: Boolean,
    hasTranslationCache: Boolean,
    isTranslating: Boolean,
    showTranslateAction: Boolean,
    settings: AppSettings,
    collapseProgressProvider: () -> Float,
    coverVisualAlpha: Float,
    coverUrl: String?,
    fallbackCoverUrl: String?,
    sharedElementKey: String? = null,
    onInfoCardTopSync: (Float) -> Unit,
    onCoverClick: (String?) -> Unit,
    onFavoriteClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onReviewsClick: () -> Unit,
    onSelectActiveLocalSource: (Long) -> Unit,
    onSelectMetadataSource: (DetailsSourceOption) -> Unit,
    onSourceClick: (ContentSource) -> Unit,
    onOpenTrackingDiscover: (ScrobblerService) -> Unit,
    onOpenMetadataSourceSheet: () -> Unit,
    onOpenReadingSourceSheet: () -> Unit,
    onOpenSupplementalAction: (DetailsSupplementAction) -> Unit,
    onAuthorClick: (String) -> Unit,
    onTagClick: (ContentTag) -> Unit,
    onTranslateClick: () -> Unit,
    onTranslateLongClick: () -> Unit,
    onToggleTranslationClick: () -> Unit,
    showCommentsAction: Boolean,
    showReviewsAction: Boolean,
    onOpenLinkedTracking: (LinkedTrackingItemUiModel) -> Unit,
    onManageLinkedTracking: (LinkedTrackingItemUiModel) -> Unit,
    onRemoveLinkedTracking: (org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult) -> Unit,
    onBindTrackingSuggestion: (org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult) -> Unit,
    onOpenTrackingSuggestion: (org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult) -> Unit,
    onIgnoreTrackingSuggestion: (org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult) -> Unit,
    onManageTrackingSuggestion: (org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult) -> Unit,
) {
    val context = LocalContext.current
    val content = mangaDetails?.toContent()
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
    val originalLanguage = metadataLanguageCode
        ?.toLocaleOrNull()
        ?.getDisplayName(defaultLocale)
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(defaultLocale) else it.toString() }
        .orEmpty()
    val readingLanguage = readingLanguageCode
        ?.toLocaleOrNull()
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
    val alternateTitlesText = remember(isShowingTranslation, originalTitle, displayTitle, content?.altTitles) {
        buildList {
            if (isShowingTranslation && originalTitle.isNotBlank() && originalTitle != displayTitle) {
                add(originalTitle)
            }
            addAll(content?.altTitles.orEmpty().filter { it.isNotBlank() && it != displayTitle })
        }.distinct().joinToString(" / ")
    }
    val ratingLabel = remember(content?.hasRating, content?.rating, defaultLocale) {
        content
            ?.takeIf { it.hasRating }
            ?.let { String.format(defaultLocale, "%.1f", it.rating * 10f) }
    }
    val state = content?.state
    val progressLabel = if (historyInfo.history != null) {
        "${(historyInfo.percent * 100f).roundToInt()}%"
    } else {
        "-"
    }
    val metadataSourceOption = metadataSourceOptions.firstOrNull { it.isSelected } ?: metadataSourceOptions.firstOrNull()
    val readingSourceOption = readingSourceOptions.firstOrNull { it.isSelected } ?: readingSourceOptions.firstOrNull()
    val commentsLabel = stringResource(R.string.details_comments)
    val reviewsLabel = stringResource(R.string.details_reviews)
    val visibleTrackingSuggestion = trackingSuggestion?.takeUnless { suggestion ->
        linkedTrackingItems.any { linked ->
            linked.service == suggestion.service && linked.remoteId == suggestion.remoteId
        }
    }
    val readingSourceLabelRes = when (resolvedContentType) {
        ContentType.VIDEO,
        ContentType.HENTAI_VIDEO -> R.string.details_playback_source
        else -> R.string.details_reading_source
    }
    val readingLanguageLabelRes = when (resolvedContentType) {
        ContentType.VIDEO,
        ContentType.HENTAI_VIDEO -> R.string.details_playback_language_short
        else -> R.string.details_reading_language_short
    }

    val normalizedCoverUrl = coverUrl?.takeIf { it.isNotBlank() }
    val normalizedFallbackCoverUrl = fallbackCoverUrl?.takeIf { it.isNotBlank() }
    var hasCoverLoadFailed by remember(normalizedCoverUrl) { mutableStateOf(false) }
    val currentCoverUrl = if (hasCoverLoadFailed && normalizedFallbackCoverUrl != null) {
        normalizedFallbackCoverUrl
    } else {
        normalizedCoverUrl
    }

    var isDescriptionExpanded by remember(settings.isDescriptionExpanded) { mutableStateOf(settings.isDescriptionExpanded) }
    val description = displayDescription.ifBlank { fallbackDescription }
    val canExpandDescription = description.length > 200

    val coverModel = remember(content?.source?.name, content?.url, currentCoverUrl) {
        ImageRequest.Builder(context)
            .data(currentCoverUrl)
            .crossfade(sharedElementKey == null)
            .apply { content?.let { mangaExtra(it) } }
            .build()
    }
    val isNsfw = content?.isNsfw() == true
    val infoItems = buildList {
        content?.let {
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
                label = stringResource(R.string.details_original_language_short),
                value = originalLanguage.ifBlank { stringResource(R.string.unknown) },
                iconRes = R.drawable.ic_language,
            ),
        )
        add(
            DetailsInfoItem(
                label = stringResource(readingLanguageLabelRes),
                value = readingLanguage.ifBlank { stringResource(R.string.unknown) },
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
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            DetailsCoverFrame(
                coverModel = coverModel,
                contentDescription = displayTitle,
                showNsfwBadge = isNsfw,
                sharedElementKey = sharedElementKey,
                topBadgeText = ratingLabel,
                topBadgeIconRes = R.drawable.ic_star_small,
                onClick = { onCoverClick(currentCoverUrl) },
                onState = { state ->
                    if (state is coil3.compose.AsyncImagePainter.State.Error) {
                        hasCoverLoadFailed = true
                    }
                },
                modifier = Modifier
                    .graphicsLayer {
                        val coverCollapseProgress = (collapseProgressProvider() / 0.48f).coerceIn(0f, 1f)
                        alpha = (1f - coverCollapseProgress) * coverVisualAlpha.coerceIn(0f, 1f)
                    },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        val textCollapseProgress = ((collapseProgressProvider() - 0.08f) / 0.44f).coerceIn(0f, 1f)
                        alpha = 1f - textCollapseProgress
                    },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (alternateTitlesText.isNotEmpty()) {
                    Text(
                        text = alternateTitlesText,
                        style = MaterialTheme.typography.labelMedium.copy(lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (supplementalActions.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(supplementalActions, key = { it.title + it.url }) { action ->
                            SuggestionChip(
                                onClick = { onOpenSupplementalAction(action) },
                                label = { Text(action.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                ),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            val actionsCollapseProgress =
                                ((collapseProgressProvider() - 0.18f) / 0.36f).coerceIn(0f, 1f)
                            alpha = 1f - actionsCollapseProgress
                        }
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailsHeaderIconButton(
                        iconRes = if (isFavourite) R.drawable.ic_heart else R.drawable.ic_heart_outline,
                        onClick = onFavoriteClick,
                        filled = isFavourite,
                    )
                    if (showCommentsAction) {
                        DetailsHeaderIconButton(
                            iconRes = R.drawable.ic_comment,
                            onClick = onCommentsClick,
                            onLongClick = {
                                Toast.makeText(context, commentsLabel, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                    if (showReviewsAction) {
                        DetailsHeaderIconButton(
                            iconRes = R.drawable.ic_book_page,
                            onClick = onReviewsClick,
                            onLongClick = {
                                Toast.makeText(context, reviewsLabel, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
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

        val showInfoCard = metadataSourceOptions.isNotEmpty() || readingSourceOptions.isNotEmpty() || infoItems.isNotEmpty()
        if (showInfoCard) {
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
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (metadataSourceOptions.isNotEmpty() || readingSourceOptions.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (metadataSourceOptions.isNotEmpty()) {
                                DetailsSourceSelectorButton(
                                    label = stringResource(R.string.details_metadata_source),
                                    currentOption = metadataSourceOption,
                                    unavailableText = stringResource(R.string.details_reading_source_unavailable),
                                    onPrimaryClick = {
                                        when {
                                            metadataSourceOption?.source != null -> onSourceClick(metadataSourceOption.source)
                                            metadataSourceOption?.trackingService != null -> onOpenTrackingDiscover(metadataSourceOption.trackingService)
                                        }
                                    },
                                    isMenuEnabled = true,
                                    onMenuClick = onOpenMetadataSourceSheet,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            DetailsSourceSelectorButton(
                                label = stringResource(readingSourceLabelRes),
                                currentOption = readingSourceOption,
                                unavailableText = stringResource(R.string.details_reading_source_unavailable),
                                onPrimaryClick = {},
                                isMenuEnabled = true,
                                onMenuClick = onOpenReadingSourceSheet,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (infoItems.isNotEmpty()) {
                        if (metadataSourceOptions.isNotEmpty() || readingSourceOptions.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                        }
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
                    }
                    // Reading progress bar at bottom of info card
                    if (historyInfo.history != null && historyInfo.percent > 0f) {
                        val progressPercent = (historyInfo.percent * 100f).roundToInt()
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            KototoroLinearProgressIndicator(
                                progress = { historyInfo.percent.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp)),
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

        visibleTrackingSuggestion?.let { suggestion ->
            TrackingSuggestionCard(
                match = suggestion,
                onBindClick = { onBindTrackingSuggestion(suggestion) },
                onOpenClick = { onOpenTrackingSuggestion(suggestion) },
                onIgnoreClick = { onIgnoreTrackingSuggestion(suggestion) },
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.description),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (canExpandDescription) {
                    Text(
                        text = if (isDescriptionExpanded) stringResource(R.string.show_less) else stringResource(R.string.show_more),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { isDescriptionExpanded = !isDescriptionExpanded }
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().clickable { if (canExpandDescription) isDescriptionExpanded = !isDescriptionExpanded },
                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!content?.tags.isNullOrEmpty()) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 24.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        content?.tags.orEmpty().forEach { tag ->
                            val isSensitiveTag = isSensitiveDetailsTag(tag)
                            SuggestionChip(
                                onClick = { onTagClick(tag) },
                                modifier = Modifier.heightIn(min = 24.dp),
                                label = {
                                    Text(
                                        text = tag.title,
                                        style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (isSensitiveTag) {
                                        Color(0xFFE3B341).copy(alpha = 0.22f)
                                    } else {
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.48f)
                                    },
                                    labelColor = if (isSensitiveTag) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = if (isSensitiveTag) {
                                        Color(0xFFE3B341).copy(alpha = 0.68f)
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackingSuggestionCard(
    match: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult,
    onBindClick: () -> Unit,
    onOpenClick: () -> Unit,
    onIgnoreClick: () -> Unit,
) {
    val defaultLocale = Locale.getDefault()
    val confidenceLabel = String.format(defaultLocale, "%.0f%%", match.confidence * 100f)
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        style = GlassDefaults.subtleStyle().copy(
            containerAlpha = 0.78f,
            borderAlpha = 0.22f,
        ),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(match.service.iconResId),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(18.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.details_tracking_suggestion_title),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.details_tracking_suggestion_summary,
                            stringResource(match.service.titleResId),
                            match.title,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = confidenceLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestionChip(
                    onClick = onBindClick,
                    label = { Text(stringResource(R.string.tracking_bind_suggestion_action)) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        labelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                SuggestionChip(
                    onClick = onOpenClick,
                    label = { Text(stringResource(R.string.details_tracking_suggestion_view)) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    ),
                )
                SuggestionChip(
                    onClick = onIgnoreClick,
                    label = { Text(stringResource(R.string.details_tracking_suggestion_ignore)) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsSourceSelectorButton(
    modifier: Modifier = Modifier,
    label: String,
    currentOption: DetailsSourceOption?,
    unavailableText: String,
    onPrimaryClick: () -> Unit,
    isMenuEnabled: Boolean,
    onMenuClick: () -> Unit,
) {
    val currentTitle = detailsSourceOptionTitle(currentOption, unavailableText)
    val isPrimaryEnabled = currentOption?.source != null || currentOption?.trackingService != null

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (isPrimaryEnabled) {
                                Modifier.clickable(onClick = onPrimaryClick)
                            } else {
                                Modifier
                            },
                        )
                        .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when {
                        currentOption?.source != null -> {
                            ContentSourceIcon(
                                source = currentOption.source,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        currentOption?.trackingService != null -> {
                            Icon(
                                painter = painterResource(currentOption.trackingService.iconResId),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        else -> {
                            Icon(
                                painter = painterResource(R.drawable.ic_manga_source),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Text(
                        text = currentTitle,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isPrimaryEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                )
                Box(
                    modifier = Modifier
                        .clickable(enabled = isMenuEnabled, onClick = onMenuClick)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = if (isMenuEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        },
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private fun isSensitiveDetailsTag(tag: ContentTag): Boolean {
    return tag.title.containsAdultTagKeyword()
}

@Composable
private fun detailsSourceOptionTitle(
    option: DetailsSourceOption?,
    unavailableText: String,
): String {
    return when {
        option?.source != null -> rememberResolvedSourceTitle(option.source)
        option?.trackingService != null -> stringResource(option.trackingService.titleResId)
        else -> unavailableText
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataSourceSheet(
    currentOptions: List<DetailsSourceOption>,
    selectedOption: DetailsSourceOption?,
    searchServices: List<ScrobblerService>,
    authorizedServices: Set<ScrobblerService>,
    searchQuery: String,
    searchSections: List<org.skepsun.kototoro.details.ui.MetadataSearchSectionUiState>,
    isLoading: Boolean,
    hasSearched: Boolean,
    unavailableText: String,
    linkedTrackingItems: List<LinkedTrackingItemUiModel> = emptyList(),
    onDismissRequest: () -> Unit,
    onSelectOption: (DetailsSourceOption) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBindResult: (TrackingSiteItem) -> Unit,
    onOpenLinkedTracking: (LinkedTrackingItemUiModel) -> Unit = {},
) {
    val visibleSections = remember(searchServices, searchSections) {
        if (searchSections.isNotEmpty()) {
            searchSections
        } else {
            searchServices.map { service ->
                org.skepsun.kototoro.details.ui.MetadataSearchSectionUiState(service = service)
            }
        }
    }
    DetailsSourceOverlayDialog(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.details_metadata_source),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            val detailContext = LocalContext.current
            if (currentOptions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        items = currentOptions,
                        key = { it.key },
                    ) { option ->
                        val linked = option.trackingService?.let { svc ->
                            linkedTrackingItems.firstOrNull { it.service == svc && it.remoteId == option.remoteId }
                        }
                        val isSelected = option == selectedOption || option.isSelected
                        val label = when {
                            option.trackingService != null -> stringResource(option.trackingService.titleResId)
                            option.source != null -> rememberResolvedSourceTitle(option.source)
                            else -> ""
                        }
                        val title = when {
                            option.source != null -> option.source.getTitle(detailContext)
                            !linked?.title.isNullOrBlank() -> linked?.title.orEmpty()
                            option.trackingService != null -> stringResource(option.trackingService.titleResId)
                            else -> option.key
                        }
                        val subtitle = when {
                            !linked?.title.isNullOrBlank() && linked.title != title -> linked.title
                            option.remoteId != null -> "#${option.remoteId}"
                            else -> label
                        }
                        val coverUrl = linked?.coverUrl
                        Surface(
                            modifier = Modifier
                                .width(112.dp)
                                .clickable {
                                    onDismissRequest()
                                    onSelectOption(option)
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = if (isSelected)
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            else null,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp, 80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!coverUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(coverUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else if (option.trackingService != null) {
                                        Icon(
                                            painter = painterResource(option.trackingService.iconResId),
                                            contentDescription = null,
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_extension),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                    )
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onSearch() },
                ),
            )
            searchServices
                .filter { it !in authorizedServices }
                .takeIf { it.isNotEmpty() }
                ?.let {
                    Text(
                        text = stringResource(R.string.details_metadata_source_login_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
            ) {
                when {
                    visibleSections.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = stringResource(R.string.nothing_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(
                                items = visibleSections,
                                key = { it.service.id },
                            ) { section ->
                                MetadataSearchSection(
                                    section = section,
                                    isAuthorized = section.service in authorizedServices,
                                    hasSearched = hasSearched,
                                    onItemClick = { item ->
                                        onDismissRequest()
                                        onBindResult(item)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReadingSourceSheet(
    currentOptions: List<DetailsSourceOption>,
    selectedOption: DetailsSourceOption?,
    searchSources: List<ContentSourceInfo>,
    searchQuery: String,
    searchSections: List<org.skepsun.kototoro.details.ui.ReadingSearchSectionUiState>,
    isLoading: Boolean,
    hasSearched: Boolean,
    currentContent: Content?,
    unavailableText: String,
    label: String,
    onDismissRequest: () -> Unit,
    onSelectOption: (DetailsSourceOption) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onTemporaryOpenResult: (Content) -> Unit,
    onMigrateResult: (Content) -> Unit,
) {
    val context = LocalContext.current
    var pendingMigrationTarget by remember { mutableStateOf<Content?>(null) }
    val visibleSections = remember(searchSources, searchSections) {
        if (searchSections.isNotEmpty()) {
            searchSections
        } else {
            searchSources.map { source ->
                org.skepsun.kototoro.details.ui.ReadingSearchSectionUiState(source = source)
            }
        }
    }
    DetailsSourceOverlayDialog(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (currentOptions.isNotEmpty()) {
                Column(
                    modifier = Modifier.heightIn(max = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    currentOptions.forEach { option ->
                        SourceOptionSheetRow(
                            title = detailsSourceOptionTitle(option, unavailableText),
                            subtitle = stringResource(R.string.source),
                            isSelected = option == selectedOption || option.isSelected,
                            onClick = {
                                onDismissRequest()
                                onSelectOption(option)
                            },
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                    )
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onSearch() },
                ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
            ) {
                when {
                    visibleSections.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = stringResource(R.string.nothing_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(
                                items = visibleSections,
                                key = { it.source.mangaSource.name },
                            ) { section ->
                                ReadingSearchSection(
                                    section = section,
                                    hasSearched = hasSearched,
                                    onItemClick = { item ->
                                        onTemporaryOpenResult(item)
                                    },
                                    onMigrateClick = { item ->
                                        pendingMigrationTarget = item
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    pendingMigrationTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingMigrationTarget = null },
            title = { Text(stringResource(R.string.manga_migration)) },
            text = {
                Text(
                    stringResource(
                        R.string.migrate_confirmation,
                        currentContent?.title.orEmpty(),
                        currentContent?.source?.getTitle(context).orEmpty(),
                        target.title,
                        target.source.getTitle(context),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingMigrationTarget = null
                        onDismissRequest()
                        onMigrateResult(target)
                    },
                ) {
                    Text(stringResource(R.string.migrate))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMigrationTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun DetailsSourceOverlayDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
            )
        },
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.18f),
        tonalElevation = 0.dp,
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            style = GlassDefaults.prominentStyle().copy(
                containerAlpha = 0.84f,
                borderAlpha = 0.20f,
                shadowElevation = 0.dp,
            ),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun MetadataSearchSection(
    section: org.skepsun.kototoro.details.ui.MetadataSearchSectionUiState,
    isAuthorized: Boolean,
    hasSearched: Boolean,
    onItemClick: (TrackingSiteItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(section.service.titleResId),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (isAuthorized) section.items.size.toString() else stringResource(R.string.sign_in),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        section.errorMessage?.let { errorMessage ->
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        when {
            section.items.isNotEmpty() -> {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(
                    items = section.items,
                    key = { "${it.service.id}:${it.remoteId}" },
                ) { item ->
                    TrackingSearchResultCard(
                        item = item,
                        onClick = { onItemClick(item) },
                    )
                }
            }
            }
            section.isLoading -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.search),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            hasSearched && section.errorMessage == null -> {
                Text(
                    text = stringResource(R.string.nothing_found),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReadingSearchSection(
    section: org.skepsun.kototoro.details.ui.ReadingSearchSectionUiState,
    hasSearched: Boolean,
    onItemClick: (Content) -> Unit,
    onMigrateClick: (Content) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = rememberResolvedSourceTitle(section.source.mangaSource),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        section.errorMessage?.let { errorMessage ->
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        when {
            section.items.isNotEmpty() -> {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(
                    items = section.items,
                    key = { it.id },
                ) { item ->
                    ReadingSearchResultCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onMigrateClick = { onMigrateClick(item) },
                    )
                }
            }
            }
            section.isLoading -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.search),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            hasSearched && section.errorMessage == null -> {
                Text(
                    text = stringResource(R.string.nothing_found),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SourceOptionSheetRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        style = GlassDefaults.subtleStyle(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun TrackingSearchResultRow(
    item: TrackingSiteItem,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        style = GlassDefaults.subtleStyle(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .width(42.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.altTitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(item.service.titleResId),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun TrackingSearchResultCard(
    item: TrackingSiteItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier.width(108.dp),
        style = GlassDefaults.subtleStyle(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(142.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.altTitle?.takeIf { it.isNotBlank() }?.let { altTitle ->
                Text(
                    text = altTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val infoParts = buildList {
                item.score?.let { score ->
                    val max = item.scoreMax ?: 10f
                    add("%.1f".format(score / max * 10))
                }
                item.totalEpisodes?.let { count ->
                    add("$count EP")
                }
            }
            if (infoParts.isNotEmpty()) {
                Text(
                    text = infoParts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ReadingSearchResultRow(
    item: Content,
    onClick: () -> Unit,
) {
    val latestChapterInfo = remember(item) { item.readingSearchLatestChapterInfo() }
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        style = GlassDefaults.subtleStyle(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .width(42.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = rememberResolvedSourceTitle(item.source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                latestChapterInfo?.let { latestInfo ->
                    Text(
                        text = when (latestInfo) {
                            is ReadingSearchLatestChapterInfo.Numbered -> {
                                stringResource(
                                    R.string.details_search_result_latest_chapter,
                                    latestInfo.number,
                                )
                            }
                            is ReadingSearchLatestChapterInfo.Titled -> {
                                stringResource(
                                    R.string.details_search_result_latest_title,
                                    latestInfo.title,
                                )
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ReadingSearchResultCard(
    item: Content,
    onClick: () -> Unit,
    onMigrateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestChapterInfo = remember(item) { item.readingSearchLatestChapterInfo() }
    GlassSurface(
        modifier = modifier.width(108.dp),
        style = GlassDefaults.subtleStyle(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onMigrateClick,
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(142.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val chaptersCount = item.chapters?.size ?: 0
            if (chaptersCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.chapters, chaptersCount, chaptersCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            latestChapterInfo?.let { latestInfo ->
                Text(
                    text = when (latestInfo) {
                        is ReadingSearchLatestChapterInfo.Numbered -> {
                            stringResource(
                                R.string.details_search_result_latest_chapter,
                                latestInfo.number,
                            )
                        }
                        is ReadingSearchLatestChapterInfo.Titled -> {
                            stringResource(
                                R.string.details_search_result_latest_title,
                                latestInfo.title,
                            )
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalButton(
                onClick = onMigrateClick,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_replace),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.migrate),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private sealed interface ReadingSearchLatestChapterInfo {
    data class Numbered(val number: String) : ReadingSearchLatestChapterInfo
    data class Titled(val title: String) : ReadingSearchLatestChapterInfo
}

private fun Content.readingSearchLatestChapterInfo(): ReadingSearchLatestChapterInfo? {
    val chapters = chapters.orEmpty()
    if (chapters.isEmpty()) return null

    val numberedChapter = chapters
        .asSequence()
        .filter { it.number > 0f }
        .maxByOrNull { it.number }
    if (numberedChapter != null) {
        return ReadingSearchLatestChapterInfo.Numbered(
            numberedChapter.numberString().orEmpty(),
        )
    }

    val titledChapter = chapters.firstNotNullOfOrNull { chapter ->
        chapter.title?.takeIf { it.isNotBlank() }
    } ?: return null
    return ReadingSearchLatestChapterInfo.Titled(titledChapter)
}

@Composable
private fun EntityChapterSourceCard(
    info: EntityChapterSourceInfo,
) {
    val chapterSourceTitle = info.source?.let { rememberResolvedSourceTitle(it) }
        ?: stringResource(R.string.entity_graph_chapter_source_unavailable)
    val supportingText = if (info.source != null) {
        stringResource(R.string.entity_graph_chapter_source_selected_hint)
    } else {
        stringResource(R.string.entity_graph_chapter_source_unavailable_hint)
    }
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        style = GlassDefaults.subtleStyle(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_book_page),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.entity_graph_chapter_source),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = chapterSourceTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
