package org.skepsun.kototoro.core.ui.compose

import android.os.SystemClock
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
fun HorizontalRailAnimatedVisibility(
    animationKey: Any,
    index: Int,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    var hasPlayed by rememberSaveable(animationKey) { mutableStateOf(false) }
    val entryProgress = remember(animationKey) { Animatable(if (hasPlayed) 1f else 0f) }
    val density = LocalDensity.current
    val initialOffsetPx = with(density) { 34.dp.toPx() }
    var velocityTarget by remember(listState) { mutableFloatStateOf(0f) }
    val scrollIntensity by animateFloatAsState(
        targetValue = velocityTarget,
        animationSpec = tween(
            durationMillis = if (listState.isScrollInProgress) 90 else 220,
            easing = FastOutSlowInEasing,
        ),
        label = "horizontal_rail_scroll_intensity",
    )

    LaunchedEffect(animationKey) {
        if (hasPlayed) return@LaunchedEffect
        delay((index.coerceAtMost(6) * 30L))
        entryProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 220,
                easing = FastOutSlowInEasing,
            ),
        )
        hasPlayed = true
    }

    LaunchedEffect(listState) {
        var lastTimestamp = SystemClock.elapsedRealtime()
        var lastIndex = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress,
            )
        }.collectLatest { (currentIndex, currentOffset, isScrolling) ->
            val now = SystemClock.elapsedRealtime()
            val dt = (now - lastTimestamp).coerceAtLeast(16L)
            val estimatedItemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
            val deltaPx = ((currentIndex - lastIndex) * estimatedItemSize) + (currentOffset - lastOffset)
            val pixelsPerSecond = (abs(deltaPx) * 1000f) / dt.toFloat()
            velocityTarget = if (isScrolling) {
                (pixelsPerSecond / 3200f).coerceIn(0f, 1f)
            } else {
                0f
            }
            lastTimestamp = now
            lastIndex = currentIndex
            lastOffset = currentOffset
        }
    }

    val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    val viewportStart = listState.layoutInfo.viewportStartOffset.toFloat()
    val viewportEnd = listState.layoutInfo.viewportEndOffset.toFloat()
    val viewportWidth = (viewportEnd - viewportStart).coerceAtLeast(1f)
    val viewportCenter = viewportStart + viewportWidth / 2f
    val itemCenter = itemInfo?.let { it.offset + (it.size / 2f) }?.toFloat() ?: viewportCenter
    val distanceFraction = ((itemCenter - viewportCenter).absoluteValue / (viewportWidth * 0.55f)).coerceIn(0f, 1f)
    val focusProgress = 1f - distanceFraction
    val edgeProgress = 1f - focusProgress
    val direction = sign(itemCenter - viewportCenter)
    val linkedScale = 1f - (edgeProgress * (0.10f + 0.16f * scrollIntensity))
    val linkedAlpha = 1f - (edgeProgress * (0.14f + 0.20f * scrollIntensity))
    val linkedTranslationX = direction * edgeProgress * (12f + 34f * scrollIntensity)
    val linkedTranslationY = edgeProgress * (5f + 16f * scrollIntensity)

    val animatedModifier = modifier.graphicsLayer {
        val entry = entryProgress.value
        alpha = (0.38f + (0.62f * entry)) * linkedAlpha
        val entryScale = 0.92f + (0.08f * entry)
        scaleX = entryScale * linkedScale
        scaleY = scaleX
        translationX = ((1f - entry) * initialOffsetPx) + linkedTranslationX
        translationY = linkedTranslationY
    }
    content(animatedModifier)
}

private val Float.absoluteValue: Float
    get() = abs(this)
