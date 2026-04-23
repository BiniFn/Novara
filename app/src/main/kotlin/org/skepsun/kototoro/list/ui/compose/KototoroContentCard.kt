package org.skepsun.kototoro.list.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.compose.ContentSourceIcon
import org.skepsun.kototoro.core.ui.compose.rememberResolvedContentSource
import org.skepsun.kototoro.core.model.getLocale
import org.skepsun.kototoro.core.ui.compose.compactPosterCardStyle
import org.skepsun.kototoro.list.domain.ReadingProgress
import org.skepsun.kototoro.list.ui.model.ContentGridModel
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.ContentDetailedListModel
import org.skepsun.kototoro.list.ui.model.ContentCompactListModel
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.compose.KototoroLoadingIndicator

@Composable
fun KototoroContentCard(
    model: ContentListModel,
    isListLayout: Boolean = false,
    isSelected: Boolean = false,
    selectionModeActive: Boolean = false,
    onClick: (Rect?) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isListLayout) {
        if (model is ContentDetailedListModel) {
            KototoroContentCardDetailedList(
                item = model,
                isSelected = isSelected,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier
            )
        } else if (model is ContentCompactListModel) {
            KototoroContentCardList(
                item = model,
                isSelected = isSelected,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier
            )
        }
    } else {
        if (model is ContentGridModel) {
            KototoroContentCardGrid(
                item = model,
                isSelected = isSelected,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KototoroContentCardGrid(
    item: ContentGridModel,
    isSelected: Boolean = false,
    showSourceInfo: Boolean = false, // Ignored in favor of new badge settings
    gridScale: Float = 1f,
    onClick: (Rect?) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = remember(context.applicationContext) { AppSettings(context.applicationContext) }
    
    val badgesTopLeft by settings.observeAsState(AppSettings.KEY_BADGES_TOP_LEFT) { badgesTopLeft }
    val badgesTopRight by settings.observeAsState(AppSettings.KEY_BADGES_TOP_RIGHT) { badgesTopRight }
    val badgesBottomLeft by settings.observeAsState(AppSettings.KEY_BADGES_BOTTOM_LEFT) { badgesBottomLeft }

    val manga = item.manga
    val coverUrl = manga.coverUrl
    val posterStyle = compactPosterCardStyle(gridScale)
    var coverBounds by remember { mutableStateOf<Rect?>(null) }
    
    val cardShape = MaterialTheme.shapes.medium
    val cardRadius = (cardShape as? RoundedCornerShape)?.topStart?.toPx(
        androidx.compose.ui.geometry.Size.Unspecified,
        androidx.compose.ui.platform.LocalDensity.current
    )?.let { with(androidx.compose.ui.platform.LocalDensity.current) { it.toDp() } } ?: 12.dp

    val loadingStyle by settings.observeAsState(AppSettings.KEY_LOADING_CIRCLE_STYLE) { loadingCircleStyle }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
            .combinedClickable(
                onClick = { onClick(coverBounds) },
                onLongClick = onLongClick,
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = posterStyle.itemWidth)
                .fillMaxWidth()
                .height(posterStyle.posterHeight)
                .onGloballyPositioned { coordinates ->
                    coverBounds = coordinates.boundsInRoot()
                }
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = manga.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )

            if (isSelected) {
                Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.Center).size(32.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)).padding(4.dp)
                )
            }

            // Top Left Badges
            ContentCardCornerBadges(
                badges = badgesTopLeft,
                item = item,
                corner = Alignment.TopStart,
                cardRadius = cardRadius,
                modifier = Modifier.align(Alignment.TopStart),
            )

            // Top Right Badges (includes counter if not handled by badges)
            val effectiveTopRightBadges = remember(badgesTopRight, item.counter) {
                if (item.counter > 0 && "counter" !in badgesTopRight) {
                    badgesTopRight + "counter"
                } else badgesTopRight
            }
            ContentCardCornerBadges(
                badges = effectiveTopRightBadges,
                item = item,
                corner = Alignment.TopEnd,
                cardRadius = cardRadius,
                modifier = Modifier.align(Alignment.TopEnd),
            )

            // Bottom Left Badges
            ContentCardCornerBadges(
                badges = badgesBottomLeft,
                item = item,
                corner = Alignment.BottomStart,
                cardRadius = cardRadius,
                modifier = Modifier.align(Alignment.BottomStart),
            )

            // Bottom Right Progress (Always shown if progress exists)
            if (item.progress != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    KototoroLoadingIndicator(
                        progress = { item.progress.percent },
                        modifier = Modifier.fillMaxSize(),
                        style = loadingStyle,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    )
                    Text(
                        text = "${(item.progress.percent * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        
        // Title... (rest of the code)

        Text(
            text = manga.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = posterStyle.itemWidth)
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KototoroContentCardList(
    item: org.skepsun.kototoro.list.ui.model.ContentCompactListModel,
    isSelected: Boolean = false,
    onClick: (Rect?) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var coverBounds by remember { mutableStateOf<Rect?>(null) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
            .combinedClickable(onClick = { onClick(coverBounds) }, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp, 72.dp)
                .onGloballyPositioned { coordinates ->
                    coverBounds = coordinates.boundsInRoot()
                }
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            if (item.counter > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                ) {
                    Text(text = item.counter.toString(), color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.subtitle.isNullOrBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ContentCardCornerBadges(
    badges: Set<String>,
    item: ContentGridModel,
    corner: Alignment,
    cardRadius: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    if (badges.isEmpty()) return

    val resolvedSource = rememberResolvedContentSource(item.manga.source)
    val langText = remember(resolvedSource.name, resolvedSource.locale) {
        resolvedSource.getLocale()
            ?.language
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
    }
    val showTracker = "tracker" in badges && item.metadataTrackingService != null
    val showFavorite = "favorite" in badges && item.isFavorite
    val showSaved = "saved" in badges && item.isSaved
    val showSource = "source" in badges
    val showLanguage = "language" in badges && !langText.isNullOrBlank()
    val showCounter = "counter" in badges && item.counter > 0

    if (!showTracker && !showFavorite && !showSaved && !showSource && !showLanguage && !showCounter) {
        return
    }

    val shape = when (corner) {
        Alignment.TopStart -> RoundedCornerShape(topStart = cardRadius, bottomEnd = 9.dp)
        Alignment.TopEnd -> RoundedCornerShape(topEnd = cardRadius, bottomStart = 9.dp)
        Alignment.BottomStart -> RoundedCornerShape(bottomStart = cardRadius, topEnd = 9.dp)
        Alignment.BottomEnd -> RoundedCornerShape(bottomEnd = cardRadius, topStart = 9.dp)
        else -> RoundedCornerShape(9.dp)
    }

    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = shape,
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        badges.forEach { badge ->
            when (badge) {
                "tracker" -> {
                    item.metadataTrackingService?.let { service ->
                        Icon(
                            painter = painterResource(id = service.iconResId),
                            contentDescription = service.name,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                "favorite" -> {
                    if (item.isFavorite) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_heart_outline),
                            contentDescription = "Favourite",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                "saved" -> {
                    if (item.isSaved) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_storage),
                            contentDescription = "Local/Saved",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                "source" -> {
                    ContentSourceIcon(
                        source = resolvedSource,
                        contentDescription = resolvedSource.name,
                        modifier = Modifier.size(13.dp),
                    )
                }
                "language" -> {
                    if (!langText.isNullOrBlank()) {
                        Text(
                            text = langText,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                lineHeight = 9.sp,
                            ),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                "counter" -> {
                    if (item.counter > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text(text = item.counter.toString(), color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KototoroContentCardDetailedList(
    item: org.skepsun.kototoro.list.ui.model.ContentDetailedListModel,
    isSelected: Boolean = false,
    onClick: (Rect?) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var coverBounds by remember { mutableStateOf<Rect?>(null) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
            .combinedClickable(onClick = { onClick(coverBounds) }, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp, 120.dp)
                .onGloballyPositioned { coordinates ->
                    coverBounds = coordinates.boundsInRoot()
                }
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            if (item.counter > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                ) {
                    Text(text = item.counter.toString(), color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            val authorText = item.manga.authors.joinToString(", ")
            if (authorText.isNotBlank()) {
                Text(
                    text = authorText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            val tagsText = item.tags.joinToString(", ") { it.title ?: "" }
            if (tagsText.isNotBlank()) {
                Text(
                    text = tagsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
