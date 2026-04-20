package org.skepsun.kototoro.discover.ui.compose

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import org.skepsun.kototoro.core.ui.compose.HeroAutoAdvanceEffect
import org.skepsun.kototoro.core.ui.compose.HeroPagerIndicator
import org.skepsun.kototoro.core.ui.compose.rememberSafePainter
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService

private val DiscoverHeroHeight = 340.dp
private val DiscoverHeroHeightLandscape = 220.dp
private val DiscoverHeroBottomBlendHeight = 160.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscoverHeroCarousel(
    title: String,
    items: List<ContentListModel>,
    activeService: ScrobblerService?,
    availableServices: List<ScrobblerService>,
    onItemClick: (ContentListModel) -> Unit,
    onSelectService: (ScrobblerService) -> Unit,
    topContentInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
    bottomContent: (@Composable () -> Unit)? = null,
) {
    if (items.isEmpty()) return

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val heroHeight = if (isLandscape) DiscoverHeroHeightLandscape else DiscoverHeroHeight

    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { items.size })
    val selectedIndex by remember(items, pagerState) {
        derivedStateOf { pagerState.currentPage.coerceIn(0, items.lastIndex) }
    }
    val selectedItem = items[selectedIndex]
    var isServiceMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "discover_hero_background")
    val backgroundScale by infiniteTransition.animateFloat(
        initialValue = 1.15f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "discover_hero_background_scale",
    )
    val backgroundTranslationX by infiniteTransition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "discover_hero_background_translation_x",
    )
    val backgroundTranslationY by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "discover_hero_background_translation_y",
    )

    HeroAutoAdvanceEffect(
        pagerState = pagerState,
        pageCount = items.size,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (bottomContent == null) Modifier.height(heroHeight + topContentInset)
                else Modifier
            ),
    ) {
        // 背景层限制在 hero 图片高度内，不延伸到 bottomContent
        val heroImageModifier = if (bottomContent != null)
            Modifier.fillMaxWidth().height(heroHeight + topContentInset).clipToBounds()
        else
            Modifier.matchParentSize()

        Box(
            modifier = heroImageModifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.82f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.90f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )
        Crossfade(
            targetState = selectedItem.id,
            label = "discover_hero_background",
            modifier = heroImageModifier,
        ) { currentId ->
            val backgroundItem = items.firstOrNull { it.id == currentId } ?: selectedItem
            val backgroundRequest = remember(currentId, backgroundItem.coverUrl) {
                ImageRequest.Builder(context)
                    .data(backgroundItem.coverUrl)
                    .build()
            }
            AsyncImage(
                model = backgroundRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = backgroundScale
                        scaleY = backgroundScale
                        translationX = backgroundTranslationX
                        translationY = backgroundTranslationY
                    }
                    .blur(32.dp)
                    .alpha(0.94f),
            )
        }
        Box(
            modifier = heroImageModifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.06f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.54f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.84f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )
        Box(
            modifier = heroImageModifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
                        ),
                    ),
                ),
        )
        // 底部渐变固定在 hero 图片区域底部，不随 bottomContent 延伸
        Spacer(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = topContentInset + heroHeight - DiscoverHeroBottomBlendHeight)
                .height(DiscoverHeroBottomBlendHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.28f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (bottomContent == null) Modifier.fillMaxSize()
                    else Modifier
                )
                .padding(top = topContentInset + 12.dp, bottom = if (bottomContent == null) 14.dp else 0.dp),
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
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalPager(
                state = pagerState,
                pageSpacing = 0.dp,
                contentPadding = PaddingValues(horizontal = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val item = items[page]
                val posterRequest = remember(item.id, item.coverUrl) {
                    ImageRequest.Builder(context)
                        .data(item.coverUrl)
                        .crossfade(true)
                        .build()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(item) }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = posterRequest,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 96.dp, height = 132.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
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
                        DiscoverHeroPill(text = item.source.getTitle(context))
                    }
                }
            }
            HeroPagerIndicator(
                pageCount = items.size,
                currentPage = selectedIndex,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (bottomContent != null) {
                Spacer(modifier = Modifier.height(14.dp))
                bottomContent()
            }
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
