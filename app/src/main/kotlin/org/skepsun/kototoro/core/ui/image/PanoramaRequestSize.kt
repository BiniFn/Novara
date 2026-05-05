package org.skepsun.kototoro.core.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import coil3.size.Size
import kotlin.math.roundToInt

@Composable
fun rememberPanoramaRequestSize(
    minWidthPx: Int = 720,
    minHeightPx: Int = 720,
    maxWidthPx: Int = 1440,
    maxHeightPx: Int = 1280,
    widthOverscan: Float = 1.28f,
    heightOverscan: Float = 0.78f,
    downsample: Boolean = false,
): Size {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        density,
        minWidthPx,
        minHeightPx,
        maxWidthPx,
        maxHeightPx,
        widthOverscan,
        heightOverscan,
        downsample,
    ) {
        val effectiveMinWidth = if (downsample) (minWidthPx * 0.47f).roundToInt() else minWidthPx
        val effectiveMinHeight = if (downsample) (minHeightPx * 0.47f).roundToInt() else minHeightPx
        val effectiveMaxWidth = if (downsample) (maxWidthPx * 0.55f).roundToInt() else maxWidthPx
        val effectiveMaxHeight = if (downsample) (maxHeightPx * 0.55f).roundToInt() else maxHeightPx
        val widthPx = with(density) {
            (configuration.screenWidthDp * density.density * widthOverscan)
                .roundToInt()
                .coerceIn(effectiveMinWidth, effectiveMaxWidth)
        }
        val heightPx = with(density) {
            (configuration.screenHeightDp * density.density * heightOverscan)
                .roundToInt()
                .coerceIn(effectiveMinHeight, effectiveMaxHeight)
        }
        Size(widthPx, heightPx)
    }
}
