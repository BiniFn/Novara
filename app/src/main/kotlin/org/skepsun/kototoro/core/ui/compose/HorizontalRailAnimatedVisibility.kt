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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import kotlin.math.abs
import kotlin.math.sign

val LocalRailAnimationFactor = staticCompositionLocalOf<Float?> { null }

@Composable
fun rememberHorizontalRailScrollIntensity(
    listState: LazyListState,
): Float {
    var velocityTarget by remember(listState) { mutableFloatStateOf(0f) }
    val scrollIntensity by animateFloatAsState(
        targetValue = velocityTarget,
        animationSpec = tween(
            durationMillis = if (listState.isScrollInProgress) 90 else 220,
            easing = FastOutSlowInEasing,
        ),
        label = "horizontal_rail_scroll_intensity",
    )

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
        }.distinctUntilChanged()
            .collectLatest { (currentIndex, currentOffset, isScrolling) ->
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

    return scrollIntensity
}

@Composable
fun rememberRailAnimationFactor(settings: AppSettings? = null): Float {
    LocalRailAnimationFactor.current?.let { return it }
    val context = LocalContext.current
    val fallbackSettings = remember(context.applicationContext) { AppSettings(context.applicationContext) }
    val resolvedSettings = settings ?: fallbackSettings
    val animationIntensityPercent by resolvedSettings.observeAsState(AppSettings.KEY_RAIL_ANIMATION_INTENSITY) {
        railAnimationIntensityPercent
    }
    return (animationIntensityPercent / 100f).coerceIn(0f, 3f)
}

@Composable
fun HorizontalRailAnimatedVisibility(
    animationKey: Any,
    index: Int,
    listState: LazyListState,
    scrollIntensity: Float,
    modifier: Modifier = Modifier,
    animationFactor: Float = rememberRailAnimationFactor(),
    enableScrollLinkedAnimation: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    if (animationFactor <= 0f) {
        content(modifier)
        return
    }

    var hasPlayed by rememberSaveable(animationKey) { mutableStateOf(false) }
    val entryProgress = remember(animationKey) { Animatable(if (hasPlayed) 1f else 0f) }
    val density = LocalDensity.current
    val initialOffsetPx = with(density) { 34.dp.toPx() } * animationFactor

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

    val animatedModifier = modifier.graphicsLayer {
        val entry = entryProgress.value
        val clampedFactor = animationFactor.coerceIn(0f, 1f)
        if (!enableScrollLinkedAnimation || scrollIntensity > 0.85f) {
            alpha = 1f - ((1f - entry) * 0.62f * clampedFactor)
            val entryScale = 1f - ((1f - entry) * 0.08f * clampedFactor)
            scaleX = entryScale
            scaleY = entryScale
            translationX = (1f - entry) * initialOffsetPx
            translationY = 0f
            return@graphicsLayer
        }

        val layoutInfo = listState.layoutInfo
        val viewportStart = layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
        val viewportWidth = (viewportEnd - viewportStart).coerceAtLeast(1f)
        val viewportCenter = viewportStart + viewportWidth / 2f

        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        val itemCenter = itemInfo?.let { it.offset + (it.size / 2f) }?.toFloat() ?: viewportCenter
        val distanceFraction = (abs(itemCenter - viewportCenter) / (viewportWidth * 0.55f)).coerceIn(0f, 1f)
        val edgeProgress = distanceFraction
        val direction = sign(itemCenter - viewportCenter)

        val linkedScale = 1f - (edgeProgress * (0.10f + 0.16f * scrollIntensity) * animationFactor)
        val linkedAlpha = 1f - (edgeProgress * (0.14f + 0.20f * scrollIntensity) * animationFactor)
        val linkedTranslationX = direction * edgeProgress * (12f + 34f * scrollIntensity) * animationFactor
        val linkedTranslationY = edgeProgress * (5f + 16f * scrollIntensity) * animationFactor

        alpha = (1f - ((1f - entry) * 0.62f * clampedFactor)) * linkedAlpha
        val entryScale = 1f - ((1f - entry) * 0.08f * clampedFactor)
        scaleX = entryScale * linkedScale
        scaleY = scaleX
        translationX = ((1f - entry) * initialOffsetPx) + linkedTranslationX
        translationY = linkedTranslationY
    }
    content(animatedModifier)
}
