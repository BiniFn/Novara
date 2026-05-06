package org.skepsun.kototoro.discover.ui.compose

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.image.panoramaBlur
import org.skepsun.kototoro.core.ui.image.rememberPanoramaRequestSize
import org.skepsun.kototoro.core.ui.compose.HeroAutoAdvanceEffect
import org.skepsun.kototoro.core.ui.compose.unclippedBoundsInWindow
import org.skepsun.kototoro.core.ui.compose.HeroPagerIndicator
import org.skepsun.kototoro.core.ui.compose.rememberResolvedSourceTitle
import androidx.compose.animation.ExperimentalSharedTransitionApi
import org.skepsun.kototoro.core.ui.compose.LocalSharedTransitionScope
import org.skepsun.kototoro.core.ui.compose.LocalNavAnimatedVisibilityScope
import org.skepsun.kototoro.core.ui.compose.contentCoverSharedKey
import org.skepsun.kototoro.core.ui.compose.rememberSafePainter
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.secondaryTitleText
import org.skepsun.kototoro.list.ui.model.supportingText
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService

private val DiscoverHeroHeight = 340.dp
private val DiscoverHeroHeightDetached = 232.dp
private val DiscoverHeroHeightLandscape = 220.dp
private val DiscoverHeroBottomBlendHeightLandscape = 128.dp
private val DiscoverHeroBottomBlendHeightDetachedLandscape = 64.dp

@Immutable
private data class DiscoverHeroPanoramaPrefs(
    val isEnabled: Boolean,
    val blurPercent: Int,
    val bottomGradientAlphaPercent: Int,
    val animationEnabled: Boolean,
    val animationSpeedPercent: Int,
    val blendHeight: Int,
    val downsampleEnabled: Boolean,
)

@Composable
private fun rememberDiscoverHeroPanoramaPrefs(settings: AppSettings): DiscoverHeroPanoramaPrefs {
    val supportsRealtimeEffects = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val prefs by settings.observeAsState(
        AppSettings.KEY_PANORAMA_ENABLED,
        AppSettings.KEY_PANORAMA_BLUR,
        AppSettings.KEY_BROWSE_PANORAMA_BOTTOM_GRADIENT_ALPHA,
        AppSettings.KEY_PANORAMA_ANIMATION_ENABLED,
        AppSettings.KEY_PANORAMA_ANIMATION_SPEED,
        AppSettings.KEY_BROWSE_PANORAMA_BLEND_HEIGHT,
        AppSettings.KEY_PANORAMA_DOWNSAMPLE,
    ) {
        DiscoverHeroPanoramaPrefs(
            isEnabled = isPanoramaCoverEnabled,
            blurPercent = panoramaCoverBlur,
            bottomGradientAlphaPercent = browsePanoramaBottomGradientAlpha,
            animationEnabled = supportsRealtimeEffects && isPanoramaCoverAnimationEnabled,
            animationSpeedPercent = panoramaAnimationSpeed,
            blendHeight = browsePanoramaBlendHeight,
            downsampleEnabled = isPanoramaDownsampleEnabled,
        )
    }
    return prefs
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun DiscoverHeroCarousel(
    title: String,
    items: List<ContentListModel>,
    activeService: ScrobblerService?,
    availableServices: List<ScrobblerService>,
    onItemClick: (ContentListModel, Rect?, String) -> Unit,
    onSelectService: (ScrobblerService) -> Unit,
    onOpenSchedule: (() -> Unit)? = null,
    topContentInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
    bottomContent: (@Composable () -> Unit)? = null,
    detachedBottomContent: Boolean = false,
    settings: AppSettings? = null,
    sharedElementKeyForItem: (ContentListModel, Int) -> String = { item, _ ->
        contentCoverSharedKey(item.manga.source.name, item.manga.coverUrl.orEmpty())
    },
) {
    if (items.isEmpty()) return

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val heroHeight = when {
        isLandscape -> DiscoverHeroHeightLandscape
        detachedBottomContent -> DiscoverHeroHeightDetached
        else -> DiscoverHeroHeight
    }
    val context = LocalContext.current
    val resolvedSettings = settings ?: remember(context.applicationContext) { AppSettings(context.applicationContext) }
    val panoramaPrefs = rememberDiscoverHeroPanoramaPrefs(resolvedSettings)
    val useRealtimeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && panoramaPrefs.blurPercent > 0
    val realtimeBlurRadius = ((panoramaPrefs.blurPercent.coerceIn(0, 100) / 100f) * 18f).dp
    val panoramaRequestSize = rememberPanoramaRequestSize(
        minWidthPx = 1280,
        minHeightPx = 960,
        maxWidthPx = 2200,
        maxHeightPx = 1600,
        widthOverscan = 1.34f,
        heightOverscan = 0.64f,
        downsample = panoramaPrefs.downsampleEnabled,
    )
    val heroBottomBlendHeight = when {
        detachedBottomContent && isLandscape -> DiscoverHeroBottomBlendHeightDetachedLandscape
        detachedBottomContent -> ((panoramaPrefs.blendHeight * 0.54f).toInt()).dp
        isLandscape -> DiscoverHeroBottomBlendHeightLandscape
        else -> panoramaPrefs.blendHeight.dp
    }
    val pageBackground = MaterialTheme.colorScheme.background

    val panoramaGradientAlphaFactor = (panoramaPrefs.bottomGradientAlphaPercent / 100f).coerceIn(0f, 1f)
    val panoramaAnimationSpeedFactor = (panoramaPrefs.animationSpeedPercent.coerceIn(50, 200)) / 100f
    val scaleAnimationDuration = (14000 / panoramaAnimationSpeedFactor).toInt().coerceAtLeast(4000)
    val horizontalPanAnimationDuration = (16000 / panoramaAnimationSpeedFactor).toInt().coerceAtLeast(4500)
    val verticalPanAnimationDuration = (12000 / panoramaAnimationSpeedFactor).toInt().coerceAtLeast(3500)
    val pagerState = rememberPagerState(pageCount = { items.size })
    val selectedIndex by remember(items, pagerState) {
        derivedStateOf { pagerState.currentPage.coerceIn(0, items.lastIndex) }
    }
    val selectedItem = items[selectedIndex]
    var isServiceMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val infiniteTransition = if (panoramaPrefs.animationEnabled) {
        rememberInfiniteTransition(label = "discover_hero_background")
    } else {
        null
    }
    val backgroundScaleState = if (infiniteTransition != null) {
        infiniteTransition.animateFloat(
            initialValue = 1.15f,
            targetValue = 1.22f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = scaleAnimationDuration, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "discover_hero_background_scale",
        )
    } else {
        null
    }
    val backgroundTranslationXState = if (infiniteTransition != null) {
        infiniteTransition.animateFloat(
            initialValue = -18f,
            targetValue = 18f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = horizontalPanAnimationDuration, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "discover_hero_background_translation_x",
        )
    } else {
        null
    }
    val backgroundTranslationYState = if (infiniteTransition != null) {
        infiniteTransition.animateFloat(
            initialValue = -12f,
            targetValue = 12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = verticalPanAnimationDuration, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "discover_hero_background_translation_y",
        )
    } else {
        null
    }

    HeroAutoAdvanceEffect(
        pagerState = pagerState,
        pageCount = items.size,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (bottomContent == null || detachedBottomContent) {
                    Modifier.height(heroHeight + topContentInset)
                } else {
                    Modifier
                }
            ),
    ) {
        // 背景层限制在 hero 图片高度内，不延伸到 bottomContent
        val heroImageModifier = Modifier
            .fillMaxWidth()
            .height(heroHeight + topContentInset)
            .clipToBounds()

        Box(
            modifier = heroImageModifier
                .background(pageBackground),
        )
        if (panoramaPrefs.isEnabled) {
            Crossfade(
                targetState = selectedItem.id,
                animationSpec = tween(180),
                label = "discover_hero_background",
                modifier = heroImageModifier,
            ) { currentId ->
                val backgroundItem = items.firstOrNull { it.id == currentId } ?: selectedItem
                val backgroundRequest = remember(
                    currentId,
                    backgroundItem.coverUrl,
                    context,
                    panoramaPrefs.blurPercent,
                    panoramaRequestSize,
                ) {
                    ImageRequest.Builder(context)
                        .data(backgroundItem.coverUrl)
                        .size(panoramaRequestSize)
                        .apply {
                            if (!useRealtimeBlur) {
                                panoramaBlur(panoramaPrefs.blurPercent)
                            }
                        }
                        .build()
                }
                AsyncImage(
                    model = backgroundRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (useRealtimeBlur) {
                                Modifier.blur(
                                    radius = realtimeBlurRadius,
                                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                )
                            } else {
                                Modifier
                            }
                        )
                        .graphicsLayer {
                            val backgroundScale = backgroundScaleState?.value ?: 1f
                            scaleX = backgroundScale
                            scaleY = backgroundScale
                            translationX = backgroundTranslationXState?.value ?: 0f
                            translationY = backgroundTranslationYState?.value ?: 0f
                        }
                        .alpha(0.94f),
                )
            }
        }
        Box(
            modifier = heroImageModifier
                .drawBehind {
                    val verticalScrim = if (detachedBottomContent) {
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.42f to Color.Transparent,
                                0.62f to pageBackground.copy(alpha = 0.10f * panoramaGradientAlphaFactor),
                                0.80f to pageBackground.copy(alpha = 0.28f * panoramaGradientAlphaFactor),
                                0.92f to pageBackground.copy(alpha = 0.54f * panoramaGradientAlphaFactor),
                                1.0f to pageBackground.copy(alpha = 0.72f * panoramaGradientAlphaFactor),
                            ),
                        )
                    } else {
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.36f to Color.Transparent,
                                0.54f to pageBackground.copy(alpha = 0.08f * panoramaGradientAlphaFactor),
                                0.70f to pageBackground.copy(alpha = 0.22f * panoramaGradientAlphaFactor),
                                0.84f to pageBackground.copy(alpha = 0.52f * panoramaGradientAlphaFactor),
                                0.94f to pageBackground.copy(alpha = 0.82f * panoramaGradientAlphaFactor),
                                1.0f to pageBackground,
                            ),
                        )
                    }
                    drawRect(verticalScrim)
                    drawRect(
                        Brush.horizontalGradient(
                            colors = listOf(
                                pageBackground.copy(alpha = 0.24f),
                                Color.Transparent,
                                pageBackground.copy(alpha = 0.14f),
                            ),
                        ),
                    )
                },
        )
        // 底部渐变固定在 hero 图片区域底部，不随 bottomContent 延伸
        Spacer(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = topContentInset + heroHeight - heroBottomBlendHeight)
                .height(heroBottomBlendHeight)
                .background(
                    if (detachedBottomContent) {
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.16f to pageBackground.copy(alpha = 0.06f * panoramaGradientAlphaFactor),
                                0.42f to pageBackground.copy(alpha = 0.18f * panoramaGradientAlphaFactor),
                                0.72f to pageBackground.copy(alpha = 0.56f * panoramaGradientAlphaFactor),
                                0.90f to pageBackground.copy(alpha = 0.86f * panoramaGradientAlphaFactor),
                                1.0f to pageBackground,
                            ),
                        )
                    } else {
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.14f to pageBackground.copy(alpha = 0.06f * panoramaGradientAlphaFactor),
                                0.36f to pageBackground.copy(alpha = 0.22f * panoramaGradientAlphaFactor),
                                0.64f to pageBackground.copy(alpha = 0.58f * panoramaGradientAlphaFactor),
                                0.84f to pageBackground.copy(alpha = 0.86f * panoramaGradientAlphaFactor),
                                1.0f to pageBackground,
                            ),
                        )
                    },
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (bottomContent == null || detachedBottomContent) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                    }
                )
                .padding(
                    top = topContentInset + 12.dp,
                    bottom = when {
                        detachedBottomContent -> 44.dp
                        bottomContent == null -> 14.dp
                        else -> 0.dp
                    },
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                activeService?.let { service ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onOpenSchedule != null) {
                            IconButton(onClick = onOpenSchedule) {
                                Icon(
                                    imageVector = Icons.Filled.DateRange,
                                    contentDescription = stringResource(R.string.open_daily_schedule),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        Box {
                            AssistChip(
                                onClick = {
                                    isServiceMenuExpanded = true
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = rememberSafePainter(service.iconResId),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                label = {
                                    Text(stringResource(service.titleResId))
                                },
                            )
                            DropdownMenu(
                                expanded = isServiceMenuExpanded,
                                onDismissRequest = { isServiceMenuExpanded = false },
                            ) {
                                availableServices.forEach { candidate ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(candidate.titleResId)) },
                                        leadingIcon = {
                                            Icon(
                                                painter = rememberSafePainter(candidate.iconResId),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                        onClick = {
                                            isServiceMenuExpanded = false
                                            onSelectService(candidate)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalPager(
                state = pagerState,
                pageSpacing = 0.dp,
                contentPadding = PaddingValues(horizontal = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val item = items[page]
                val sharedElementKey = remember(item.id, page) { sharedElementKeyForItem(item, page) }
                var coverBounds by remember(item.id) { mutableStateOf<Rect?>(null) }
                val posterRequest = remember(item.id, item.coverUrl) {
                    ImageRequest.Builder(context)
                        .data(item.coverUrl)
                        .crossfade(true)
                        .build()
                }
                val sharedTransitionScope = LocalSharedTransitionScope.current
                val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(item, coverBounds, sharedElementKey) }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        AsyncImage(
                            model = posterRequest,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 96.dp, height = 132.dp)
                                .onGloballyPositioned { coordinates ->
                                    coverBounds = coordinates.unclippedBoundsInWindow()
                                }
                                .then(
                                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                        with(sharedTransitionScope) {
                                            Modifier.sharedElement(
                                                rememberSharedContentState(
                                                    key = sharedElementKey,
                                                ),
                                                animatedVisibilityScope = animatedVisibilityScope,
                                            )
                                        }
                                    } else Modifier,
                                )
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        item.scoreText?.takeIf { it.isNotBlank() }?.let { scoreText ->
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                            ) {
                                Text(
                                    text = scoreText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.secondaryTitleText()?.takeIf { it.isNotBlank() }?.let { secondaryTitle ->
                            Text(
                                text = secondaryTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        item.supportingText()?.takeIf { it.isNotBlank() }?.let { supportingText ->
                            Text(
                                text = supportingText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        DiscoverHeroPill(text = rememberResolvedSourceTitle(item.source))
                    }
                }
            }
            if (!detachedBottomContent) {
                HeroPagerIndicator(
                    pageCount = items.size,
                    currentPage = selectedIndex,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            if (bottomContent != null) {
                if (!detachedBottomContent) {
                    Spacer(modifier = Modifier.height(14.dp))
                }
                bottomContent()
            }
        }
        if (detachedBottomContent) {
            HeroPagerIndicator(
                pageCount = items.size,
                currentPage = selectedIndex,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
            )
        }
    }
}

@Composable
private fun DiscoverHeroPill(
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
