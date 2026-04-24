package org.skepsun.kototoro.details.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.iconResId
import org.skepsun.kototoro.core.model.titleResId
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.core.model.FavouriteCategory
import org.skepsun.kototoro.core.model.ContentSourceInfo
import org.skepsun.kototoro.core.ui.compose.ContentSourceIcon
import org.skepsun.kototoro.core.ui.compose.KototoroLoadingIndicator
import org.skepsun.kototoro.core.ui.compose.rememberResolvedSourceTitle
import org.skepsun.kototoro.core.ui.glass.GlassDefaults
import org.skepsun.kototoro.core.ui.glass.GlassSurface
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.core.util.ext.toLocaleOrNull
import org.skepsun.kototoro.details.data.ContentDetails
import org.skepsun.kototoro.discover.ui.details.LocalSearchState
import org.skepsun.kototoro.details.ui.model.DetailsSourceOption
import org.skepsun.kototoro.details.ui.model.EntityChapterSourceInfo
import org.skepsun.kototoro.details.ui.model.HistoryInfo
import org.skepsun.kototoro.details.ui.model.LinkedTrackingItemUiModel
import org.skepsun.kototoro.details.ui.model.TrackingDetailsAction
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
    trackingDetailsActions: List<TrackingDetailsAction>,
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
    collapseProgress: Float,
    coverVisualAlpha: Float,
    coverUrl: String?,
    fallbackCoverUrl: String?,
    onCoverBoundsSync: (Rect, Float) -> Unit,
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
    onOpenTrackingAction: (TrackingDetailsAction) -> Unit,
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
    val metadataSourceOption = metadataSourceOptions.firstOrNull { it.isSelected } ?: metadataSourceOptions.firstOrNull()
    val readingSourceOption = readingSourceOptions.firstOrNull { it.isSelected } ?: readingSourceOptions.firstOrNull()
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
                onCoverBoundsSync = onCoverBoundsSync,
                syncAlpha = coverSyncAlpha,
                showNsfwBadge = isNsfw,
                topBadgeText = ratingLabel,
                topBadgeIconRes = R.drawable.ic_star_small,
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
                if (alternateTitles.isNotEmpty()) {
                    Text(
                        text = alternateTitles.joinToString(" / "),
                        style = MaterialTheme.typography.labelMedium.copy(lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (trackingDetailsActions.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(trackingDetailsActions, key = { it.title + it.url }) { action ->
                            SuggestionChip(
                                onClick = { onOpenTrackingAction(action) },
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
                        .alpha(1f - actionsCollapseProgress)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailsHeaderIconButton(
                        iconRes = if (isFavourite) R.drawable.ic_heart else R.drawable.ic_heart_outline,
                        onClick = onFavoriteClick,
                        filled = isFavourite,
                    )
                    if (showCommentsAction) {
                        DetailsHeaderActionButton(
                            iconRes = R.drawable.ic_comment,
                            label = stringResource(R.string.details_comments),
                            onClick = onCommentsClick,
                        )
                    }
                    if (showReviewsAction) {
                        DetailsHeaderActionButton(
                            iconRes = R.drawable.ic_book_page,
                            label = stringResource(R.string.details_reviews),
                            onClick = onReviewsClick,
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        currentOption?.source != null -> {
                            ContentSourceIcon(source = currentOption.source)
                        }
                        currentOption?.trackingService != null -> {
                            Icon(
                                painter = painterResource(currentOption.trackingService.iconResId),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        else -> {
                            Icon(
                                painter = painterResource(R.drawable.ic_manga_source),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(
                        text = currentTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
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
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                )
                Box(
                    modifier = Modifier
                        .clickable(enabled = isMenuEnabled, onClick = onMenuClick)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
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
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
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
    selectedService: ScrobblerService?,
    searchQuery: String,
    searchResults: List<TrackingSiteItem>,
    isLoading: Boolean,
    errorMessage: String?,
    unavailableText: String,
    onDismissRequest: () -> Unit,
    onSelectOption: (DetailsSourceOption) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectService: (ScrobblerService) -> Unit,
    onSearch: () -> Unit,
    onBindResult: (TrackingSiteItem) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.details_metadata_source),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(
                modifier = Modifier.heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                currentOptions.forEach { option ->
                    SourceOptionSheetRow(
                        title = detailsSourceOptionTitle(option, unavailableText),
                        subtitle = when {
                            option.trackingService != null -> stringResource(R.string.tracking_source)
                            option.source != null -> stringResource(R.string.source)
                            else -> unavailableText
                        },
                        isSelected = option == selectedOption || option.isSelected,
                        onClick = {
                            onDismissRequest()
                            onSelectOption(option)
                        },
                    )
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
            if (searchServices.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(searchServices, key = { it.id }) { service ->
                        FilterChip(
                            selected = service == selectedService,
                            onClick = { onSelectService(service) },
                            label = { Text(stringResource(service.titleResId)) },
                            leadingIcon = if (service == selectedService) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                    )
                                }
                            } else {
                                null
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                            ),
                        )
                    }
                }
            }
            selectedService?.takeIf { it !in authorizedServices }?.let { service ->
                Text(
                    text = stringResource(R.string.details_metadata_source_login_hint, stringResource(service.titleResId)),
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
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            KototoroLoadingIndicator()
                        }
                    }
                    errorMessage != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    searchResults.isEmpty() -> {
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
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(
                                items = searchResults,
                                key = { "${it.service.id}:${it.remoteId}" },
                            ) { item ->
                                TrackingSearchResultRow(
                                    item = item,
                                    onClick = {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingSourceSheet(
    currentOptions: List<DetailsSourceOption>,
    selectedOption: DetailsSourceOption?,
    searchSources: List<ContentSourceInfo>,
    selectedSourceName: String?,
    searchQuery: String,
    searchState: LocalSearchState?,
    unavailableText: String,
    label: String,
    onDismissRequest: () -> Unit,
    onSelectOption: (DetailsSourceOption) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectSearchSource: (String) -> Unit,
    onSearch: () -> Unit,
    onResultClick: (Content) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
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
            if (searchSources.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(searchSources, key = { it.mangaSource.name }) { sourceInfo ->
                        val source = sourceInfo.mangaSource
                        val title = rememberResolvedSourceTitle(source)
                        FilterChip(
                            selected = source.name == selectedSourceName,
                            onClick = { onSelectSearchSource(source.name) },
                            label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = if (source.name == selectedSourceName) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                    )
                                }
                            } else {
                                null
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                            ),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
            ) {
                when (val state = searchState) {
                    null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    LocalSearchState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            KototoroLoadingIndicator()
                        }
                    }
                    is LocalSearchState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = state.throwable.localizedMessage ?: stringResource(R.string.error_occurred),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    is LocalSearchState.Loaded -> {
                        if (state.items.isEmpty()) {
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
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(
                                    items = state.items,
                                    key = { it.id },
                                ) { item ->
                                    ReadingSearchResultRow(
                                        item = item,
                                        onClick = {
                                            onDismissRequest()
                                            onResultClick(item)
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
private fun ReadingSearchResultRow(
    item: Content,
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
                Text(
                    text = rememberResolvedSourceTitle(item.source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
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
