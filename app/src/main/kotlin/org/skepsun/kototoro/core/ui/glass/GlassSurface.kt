package org.skepsun.kototoro.core.ui.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dagger.hilt.android.EntryPointAccessors
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.BaseActivityEntryPoint
import dev.chrisbanes.haze.HazeDefaults as HazeBlurDefaults
import dev.chrisbanes.haze.hazeChild

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
    fun nestedCardColor(): Color {
        val colorScheme = MaterialTheme.colorScheme
        val isDarkTheme = colorScheme.background.luminance() < 0.5f
        return if (isDarkTheme) {
            colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)
        } else {
            colorScheme.surface.copy(alpha = 0.42f)
        }
    }

    @Composable
    fun nestedCardBorderColor(): Color {
        val colorScheme = MaterialTheme.colorScheme
        val isDarkTheme = colorScheme.background.luminance() < 0.5f
        return colorScheme.outlineVariant.copy(alpha = if (isDarkTheme) 0.28f else 0.18f)
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassDefaults.regularStyle(),
    shape: Shape = GlassDefaults.shape,
    allowRuntimeHaze: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val settings = remember(context.applicationContext) {
        EntryPointAccessors.fromApplication<BaseActivityEntryPoint>(context.applicationContext).settings
    }
    val blurMode = settings.observeAsState(AppSettings.KEY_BLUR_MODE) { blurMode }.value
    val hazeOpacityPercent = settings.observeAsState(AppSettings.KEY_HAZE_OPACITY) { hazeOpacityPercent }.value
    val hazeState = LocalHazeState.current
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val opacityFactor = (hazeOpacityPercent.coerceIn(45, 100)) / 100f
    val effectiveContainerAlpha = (when (blurMode) {
        AppSettings.BlurMode.STANDARD -> (style.containerAlpha * opacityFactor + 0.06f).coerceAtMost(0.94f)
        AppSettings.BlurMode.IMMERSIVE -> style.containerAlpha * opacityFactor
        AppSettings.BlurMode.ENHANCED -> (style.containerAlpha * opacityFactor - 0.04f).coerceAtLeast(0.20f)
    }).let { alpha ->
        // In dark mode, reduce opacity slightly for more transparency / brighter feel
        if (isDarkTheme) (alpha - 0.04f).coerceAtLeast(0.18f) else alpha
    }
    val baseColor = when {
        effectiveContainerAlpha >= 0.86f -> colorScheme.surfaceContainerHigh
        effectiveContainerAlpha >= 0.80f -> colorScheme.surfaceContainer
        else -> colorScheme.surfaceContainerLow
    }.let { candidate ->
        if (isDarkTheme) {
            // Increased lerp factor for brighter glass surfaces in dark mode
            lerp(candidate, colorScheme.surfaceBright, 0.16f)
        } else {
            candidate
        }
    }
    val baseBlurRadius = when {
        style.shadowElevation >= 10.dp -> 28.dp
        style.shadowElevation >= 6.dp -> 24.dp
        else -> 18.dp
    }
    val blurRadius = when (blurMode) {
        AppSettings.BlurMode.STANDARD -> baseBlurRadius * 0.78f
        AppSettings.BlurMode.IMMERSIVE -> baseBlurRadius
        AppSettings.BlurMode.ENHANCED -> baseBlurRadius * 1.24f
    }
    val tintAlpha = when (blurMode) {
        AppSettings.BlurMode.STANDARD -> (effectiveContainerAlpha * 0.44f).coerceIn(0.22f, 0.42f)
        AppSettings.BlurMode.IMMERSIVE -> (effectiveContainerAlpha * 0.32f).coerceIn(0.18f, 0.34f)
        AppSettings.BlurMode.ENHANCED -> (effectiveContainerAlpha * 0.24f).coerceIn(0.12f, 0.28f)
    }.let { alpha ->
        if (isDarkTheme) {
            // Higher tint offset in dark mode for better backdrop definition
            (alpha + 0.10f).coerceAtMost(0.50f)
        } else {
            alpha
        }
    }
    val hazeStyle = HazeBlurDefaults.style(
        Color.Transparent,
        HazeBlurDefaults.tint(baseColor.copy(alpha = tintAlpha)),
        blurRadius,
        0.12f,
    )
    val useRuntimeHaze = allowRuntimeHaze && supportsRuntimeHaze()
    val border = BorderStroke(
        width = 1.dp,
        color = colorScheme.outlineVariant.copy(
            alpha = if (isDarkTheme) {
                style.borderAlpha.coerceIn(0.16f, 0.28f)
            } else {
                style.borderAlpha.coerceAtMost(0.18f)
            },
        ),
    )

    CompositionLocalProvider(LocalAbsoluteTonalElevation provides 0.dp) {
        Surface(
            modifier = if (useRuntimeHaze) {
                modifier.hazeChild(hazeState, shape, hazeStyle)
            } else {
                modifier
            },
            shape = shape,
            color = baseColor.copy(alpha = effectiveContainerAlpha),
            contentColor = colorScheme.onSurface,
            tonalElevation = style.tonalElevation,
            shadowElevation = style.shadowElevation,
            border = border,
        ) {
            Box(content = content)
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
