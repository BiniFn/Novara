package org.skepsun.kototoro.core.ui.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

@Immutable
data class GlassStyle(
    val containerAlpha: Float,
    val borderAlpha: Float,
    val tonalElevation: Dp,
    val shadowElevation: Dp,
)

object GlassDefaults {
    val shape: Shape = RoundedCornerShape(28.dp)

    @Composable
    fun subtleStyle(): GlassStyle = GlassStyle(
        containerAlpha = 0.72f,
        borderAlpha = 0.18f,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    )

    @Composable
    fun regularStyle(): GlassStyle = GlassStyle(
        containerAlpha = 0.82f,
        borderAlpha = 0.24f,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    )

    @Composable
    fun prominentStyle(): GlassStyle = GlassStyle(
        containerAlpha = 0.88f,
        borderAlpha = 0.30f,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
    )

    @Composable
    fun nestedCardColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)

    @Composable
    fun nestedCardBorderColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassDefaults.regularStyle(),
    shape: Shape = GlassDefaults.shape,
    content: @Composable BoxScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when {
        style.containerAlpha >= 0.86f -> colorScheme.surfaceContainerHigh
        style.containerAlpha >= 0.80f -> colorScheme.surfaceContainer
        else -> colorScheme.surfaceContainerLow
    }
    val border = BorderStroke(
        width = 1.dp,
        color = colorScheme.outlineVariant.copy(alpha = style.borderAlpha.coerceAtMost(0.12f)),
    )

    // Temporary fallback: while haze-backed glass is not wired through the main shell,
    // degrade glass containers to stable opaque cards instead of stacking translucent shells.
    CompositionLocalProvider(LocalAbsoluteTonalElevation provides 0.dp) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            contentColor = colorScheme.onSurface,
            tonalElevation = style.tonalElevation,
            shadowElevation = 0.dp,
            border = border,
        ) {
            Box(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
fun GlassTopBarContainer(
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassDefaults.prominentStyle(),
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        style = style,
        shape = RoundedCornerShape(30.dp),
        content = content,
    )
}

@Composable
fun GlassBottomBarContainer(
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassDefaults.prominentStyle(),
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        style = style,
        shape = RoundedCornerShape(32.dp),
        content = content,
    )
}
