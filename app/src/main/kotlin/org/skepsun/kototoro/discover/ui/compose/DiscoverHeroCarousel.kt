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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.ui.compose.HeroAutoAdvanceEffect
import org.skepsun.kototoro.core.ui.compose.HeroBackdropCard
import org.skepsun.kototoro.core.ui.compose.HeroBackdropScrim
import org.skepsun.kototoro.core.ui.compose.HeroPagerIndicator
import org.skepsun.kototoro.list.ui.model.ContentListModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscoverHeroCarousel(
    title: String,
    items: List<ContentListModel>,
    onItemClick: (ContentListModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { items.size })
    val selectedIndex by remember(items, pagerState) {
        derivedStateOf { pagerState.currentPage.coerceIn(0, items.lastIndex) }
    }
    val selectedItem = items[selectedIndex]
    val infiniteTransition = rememberInfiniteTransition(label = "discover_hero_background")
    val backgroundScale by infiniteTransition.animateFloat(
        initialValue = 1.08f,
        targetValue = 1.16f,
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

    HeroBackdropCard(
        modifier = modifier,
        minHeight = 280.dp,
        shape = RoundedCornerShape(0.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        elevation = 0.dp,
        background = {
            Crossfade(targetState = selectedItem.id, label = "discover_hero_background") { currentId ->
                val backgroundItem = items.firstOrNull { it.id == currentId } ?: selectedItem
                AsyncImage(
                    model = backgroundItem.coverUrl,
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
                        .blur(28.dp),
                )
            }
            HeroBackdropScrim(
                verticalColors = listOf(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.10f),
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.36f),
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                ),
                horizontalColors = listOf(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    Color.Transparent,
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.44f),
                ),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalPager(
                state = pagerState,
                pageSpacing = 0.dp,
                contentPadding = PaddingValues(horizontal = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val item = items[page]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(item) }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 100.dp, height = 140.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        DiscoverHeroPill(text = item.source.getTitle(context))
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroPagerIndicator(
                    pageCount = items.size,
                    currentPage = selectedIndex,
                )
                TextButton(onClick = { onItemClick(selectedItem) }) {
                    Text(text = stringResource(R.string.more))
                }
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
