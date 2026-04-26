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
    ) {
        val widthPx = with(density) {
            (configuration.screenWidthDp * density.density * widthOverscan)
                .roundToInt()
                .coerceIn(minWidthPx, maxWidthPx)
        }
        val heightPx = with(density) {
            (configuration.screenHeightDp * density.density * heightOverscan)
                .roundToInt()
                .coerceIn(minHeightPx, maxHeightPx)
        }
        Size(widthPx, heightPx)
    }
}
