package org.skepsun.kototoro.core.ui.compose

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.core.prefs.AppSettings

@Composable
fun KototoroLoadingIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    style: AppSettings.LoadingCircleStyle = AppSettings.LoadingCircleStyle.THICK_STRAIGHT,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    trackColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
) {
    val strokeWidth = when (style) {
        AppSettings.LoadingCircleStyle.THIN_STRAIGHT,
        AppSettings.LoadingCircleStyle.THIN_WAVY -> 2.dp
        else -> 4.dp
    }

    if (progress != null) {
        CircularProgressIndicator(
            progress = progress,
            modifier = modifier,
            strokeWidth = strokeWidth,
            color = color,
            trackColor = trackColor,
        )
    } else {
        CircularProgressIndicator(
            modifier = modifier,
            strokeWidth = strokeWidth,
            color = color,
            trackColor = trackColor,
        )
    }
}
