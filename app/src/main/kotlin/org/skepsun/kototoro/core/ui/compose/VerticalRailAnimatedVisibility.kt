package org.skepsun.kototoro.core.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun rememberVerticalRailScrollIntensity(
    listState: LazyListState,
): Float {
    var velocityTarget by remember(listState) { mutableFloatStateOf(0f) }
    val scrollIntensity by animateFloatAsState(
        targetValue = velocityTarget,
        animationSpec = tween(
            durationMillis = if (listState.isScrollInProgress) 90 else 220,
            easing = FastOutSlowInEasing,
        ),
        label = "vertical_rail_scroll_intensity",
    )

    LaunchedEffect(listState) {
        var lastIndex = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress,
            )
        }.collectLatest { (currentIndex, currentOffset, isScrolling) ->
            val estimatedItemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
            val deltaPx = ((currentIndex - lastIndex) * estimatedItemSize) + (currentOffset - lastOffset)
            val pixelsPerSecond = abs(deltaPx) * 60f
            velocityTarget = if (isScrolling) {
                (pixelsPerSecond / 3600f).coerceIn(0f, 1f)
            } else {
                0f
            }
            lastIndex = currentIndex
            lastOffset = currentOffset
        }
    }

    return scrollIntensity
}

@Composable
fun VerticalRailAnimatedVisibility(
    animationKey: Any,
    index: Int,
    listState: LazyListState,
    isAnimationEnabled: Boolean = true,
    animationFactor: Float = 1f,
    scaleFactor: Float = 1f,
    scrollIntensity: Float = 0f,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    if (!isAnimationEnabled || animationFactor <= 0f) {
        content(modifier)
        return
    }
    var hasPlayed by rememberSaveable(animationKey) { mutableStateOf(false) }
    val entryProgress = remember(animationKey) { Animatable(if (hasPlayed) 1f else 0f) }
    val density = LocalDensity.current
    val initialOffsetPx = with(density) { 28.dp.toPx() } * animationFactor

    LaunchedEffect(animationKey) {
        if (hasPlayed) return@LaunchedEffect
        delay((index.coerceAtMost(8) * 26L))
        entryProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 220,
                easing = FastOutSlowInEasing,
            ),
        )
        hasPlayed = true
    }

    val clampedFactor = animationFactor.coerceIn(0f, 1f)
    val clampedScaleFactor = scaleFactor.coerceIn(0f, 1f)
    val animatedModifier = modifier.graphicsLayer {
        val entry = entryProgress.value
        if (scrollIntensity > 0.85f) {
            alpha = 1f - ((1f - entry) * 0.58f * clampedFactor)
            val entryScale = 1f - ((1f - entry) * 0.04f * clampedScaleFactor * clampedFactor)
            scaleX = entryScale
            scaleY = entryScale
            translationY = (1f - entry) * initialOffsetPx
            translationX = 0f
            return@graphicsLayer
        }

        val info = listState.layoutInfo
        val viewportStart = info.viewportStartOffset.toFloat()
        val viewportEnd = info.viewportEndOffset.toFloat()
        val viewportHeight = (viewportEnd - viewportStart).coerceAtLeast(1f)
        val viewportCenter = viewportStart + viewportHeight / 2f
        val itemInfo = info.visibleItemsInfo.firstOrNull { it.index == index }
        val itemCenter = itemInfo?.let { it.offset + (it.size / 2f) }?.toFloat() ?: viewportCenter
        val distanceFraction = (abs(itemCenter - viewportCenter) / (viewportHeight * 0.58f)).coerceIn(0f, 1f)
        val edgeProgress = distanceFraction
        val direction = sign(itemCenter - viewportCenter)
        val si = scrollIntensity

        val linkedScale = 1f - (edgeProgress * (0.04f + 0.08f * si) * clampedScaleFactor * animationFactor)
        val linkedAlpha = 1f - (edgeProgress * (0.08f + 0.16f * si) * animationFactor)
        val linkedTranslationY = direction * edgeProgress * (10f + 28f * si) * animationFactor
        val linkedTranslationX = edgeProgress * (4f + 10f * si) * animationFactor

        alpha = (1f - ((1f - entry) * 0.58f * clampedFactor)) * linkedAlpha
        val entryScale = 1f - ((1f - entry) * 0.04f * clampedScaleFactor * clampedFactor)
        scaleX = entryScale * linkedScale
        scaleY = scaleX
        translationY = ((1f - entry) * initialOffsetPx) + linkedTranslationY
        translationX = linkedTranslationX
    }
    content(animatedModifier)
}
