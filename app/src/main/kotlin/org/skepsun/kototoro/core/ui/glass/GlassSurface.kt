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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.FluentMaterials
import dev.chrisbanes.haze.materials.HazeMaterials
import dagger.hilt.android.EntryPointAccessors
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.BaseActivityEntryPoint
import dev.chrisbanes.haze.HazeDefaults as HazeBlurDefaults
import dev.chrisbanes.haze.hazeChild

@Immutable
data class GlassSurfaceColors(
    val containerColor: Color,
    val baseTintColor: Color,
    val blurRadius: Dp,
    val noiseFactor: Float,
    val border: BorderStroke,
)

@Composable
fun rememberGlassPrefs(settings: AppSettings): GlassPrefs {
    val prefs by settings.observeAsState(
        AppSettings.KEY_GLASS_EFFECT_ENABLED,
        AppSettings.KEY_GLASS_MATERIAL_PRESET,
        AppSettings.KEY_HAZE_OPACITY,
        AppSettings.KEY_GLASS_BLUR_STRENGTH,
        AppSettings.KEY_GLASS_NOISE_STRENGTH,
        AppSettings.KEY_GLASS_IMMERSIVE_STRENGTH,
    ) {
        GlassPrefs(
            isGlassEffectEnabled = isGlassEffectEnabled,
            materialPreset = glassMaterialPreset,
            hazeOpacityPercent = hazeOpacityPercent,
            blurStrengthPercent = glassBlurStrengthPercent,
            noiseStrengthPercent = glassNoiseStrengthPercent,
            immersiveStrengthPercent = glassImmersiveStrengthPercent,
        )
    }
    return prefs
}

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
    val glassPrefs = rememberGlassPrefsOrFallback()
    val hazeState = LocalHazeState.current
    val colorScheme = MaterialTheme.colorScheme
    val glassColors = rememberGlassSurfaceColors(style = style, glassPrefs = glassPrefs)

    val useRuntimeHaze = glassPrefs.isGlassEffectEnabled && allowRuntimeHaze && supportsRuntimeHaze()
    val hazeStyle = rememberGlassHazeStyle(
        glassPrefs = glassPrefs,
        glassColors = glassColors,
    )
    val hazeBackgroundColor = rememberGlassHazeBackgroundColor(
        glassPrefs = glassPrefs,
        glassColors = glassColors,
        hazeStyle = hazeStyle,
    )

    CompositionLocalProvider(LocalAbsoluteTonalElevation provides 0.dp) {
        Surface(
            modifier = if (useRuntimeHaze) {
                modifier
                    .clip(shape)
                    .hazeChild(hazeState, hazeStyle) {
                        backgroundColor = hazeBackgroundColor
                        blurredEdgeTreatment = BlurredEdgeTreatment(shape)
                        clipToAreasBounds = true
                        expandLayerBounds = true
                        forceInvalidateOnPreDraw = true
                    }
            } else {
                modifier
            },
            shape = shape,
            color = if (useRuntimeHaze) Color.Transparent else glassColors.containerColor,
            contentColor = colorScheme.onSurface,
            tonalElevation = style.tonalElevation,
            shadowElevation = style.shadowElevation,
            border = glassColors.border,
        ) {
            Box(content = content)
        }
    }
}

@Composable
fun rememberGlassPrefsOrFallback(): GlassPrefs {
    val context = LocalContext.current
    val settings = remember(context.applicationContext) {
        EntryPointAccessors.fromApplication<BaseActivityEntryPoint>(context.applicationContext).settings
    }
    return rememberGlassPrefs(settings)
}

@Composable
fun rememberGlassSurfaceColors(
    style: GlassStyle = GlassDefaults.regularStyle(),
    glassPrefs: GlassPrefs = rememberGlassPrefsOrFallback(),
): GlassSurfaceColors {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    return remember(glassPrefs, isDarkTheme, style, colorScheme) {
        computeGlassColors(
            hazeOpacityPercent = glassPrefs.hazeOpacityPercent,
            blurStrengthPercent = glassPrefs.blurStrengthPercent,
            noiseStrengthPercent = glassPrefs.noiseStrengthPercent,
            isDarkTheme = isDarkTheme,
            style = style,
            colorScheme = colorScheme,
        )
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

private fun computeGlassColors(
    hazeOpacityPercent: Int,
    blurStrengthPercent: Int,
    noiseStrengthPercent: Int,
    isDarkTheme: Boolean,
    style: GlassStyle,
    colorScheme: androidx.compose.material3.ColorScheme,
): GlassSurfaceColors {
    val preferenceAlpha = (hazeOpacityPercent.coerceIn(0, 100)) / 100f
    val effectiveContainerAlpha = (preferenceAlpha * style.containerAlpha).coerceIn(0f, 1f)
    val baseColor = when {
        effectiveContainerAlpha >= 0.86f -> colorScheme.surfaceContainerHigh
        effectiveContainerAlpha >= 0.80f -> colorScheme.surfaceContainer
        else -> colorScheme.surfaceContainerLow
    }.let { candidate ->
        if (isDarkTheme) lerp(candidate, colorScheme.surfaceBright, 0.16f) else candidate
    }
    val baseBlurRadius = when {
        style.shadowElevation >= 10.dp -> 28.dp
        style.shadowElevation >= 6.dp -> 24.dp
        else -> 18.dp
    }
    val blurRadius = blurStrengthPercent.coerceIn(0, 80).dp
        .takeIf { it > 0.dp }
        ?: baseBlurRadius
    val noiseFactor = (noiseStrengthPercent.coerceIn(0, 100)) / 100f
    val tintAlpha = ((preferenceAlpha * 0.22f) + (style.containerAlpha * 0.14f)).coerceIn(0.18f, 0.38f)
        .let { alpha ->
            if (isDarkTheme) (alpha + 0.10f).coerceAtMost(0.50f) else alpha
        }
    val border = BorderStroke(
        width = 1.dp,
        color = colorScheme.outlineVariant.copy(
            alpha = if (isDarkTheme) style.borderAlpha.coerceIn(0.16f, 0.28f) else style.borderAlpha.coerceAtMost(0.18f),
        ),
    )
    return GlassSurfaceColors(
        containerColor = baseColor.copy(alpha = effectiveContainerAlpha),
        baseTintColor = baseColor.copy(alpha = tintAlpha),
        blurRadius = blurRadius,
        noiseFactor = noiseFactor,
        border = border,
    )
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun rememberGlassHazeStyle(
    glassPrefs: GlassPrefs,
    glassColors: GlassSurfaceColors,
): HazeStyle {
    val baseStyle = when (glassPrefs.materialPreset) {
        AppSettings.GlassMaterialPreset.KOTOTORO,
        AppSettings.GlassMaterialPreset.CUSTOM -> HazeBlurDefaults.style(
            Color.Transparent,
            HazeBlurDefaults.tint(glassColors.baseTintColor),
            glassColors.blurRadius,
            glassColors.noiseFactor,
        )
        AppSettings.GlassMaterialPreset.HAZE_REGULAR -> HazeMaterials.regular(
            containerColor = glassColors.containerColor,
        )
        AppSettings.GlassMaterialPreset.CUPERTINO_REGULAR -> CupertinoMaterials.regular(
            containerColor = glassColors.containerColor,
        )
        AppSettings.GlassMaterialPreset.FLUENT_ACRYLIC -> FluentMaterials.acrylicDefault()
    }
    return remember(baseStyle, glassColors, glassPrefs.materialPreset) {
        val isCustomKototoroStyle = glassPrefs.materialPreset == AppSettings.GlassMaterialPreset.KOTOTORO ||
            glassPrefs.materialPreset == AppSettings.GlassMaterialPreset.CUSTOM
        baseStyle.copy(
            backgroundColor = if (isCustomKototoroStyle) {
                glassColors.containerColor
            } else {
                baseStyle.backgroundColor.takeOrElse { glassColors.containerColor }
            },
            blurRadius = if (isCustomKototoroStyle) {
                glassColors.blurRadius
            } else {
                if (baseStyle.blurRadius != Dp.Unspecified) baseStyle.blurRadius else glassColors.blurRadius
            },
            noiseFactor = if (isCustomKototoroStyle) {
                glassColors.noiseFactor
            } else {
                if (baseStyle.noiseFactor >= 0f) baseStyle.noiseFactor else glassColors.noiseFactor
            },
            fallbackTint = if (isCustomKototoroStyle) {
                HazeTint(glassColors.baseTintColor)
            } else {
                baseStyle.fallbackTint.takeIf { it.isSpecified } ?: HazeTint(glassColors.baseTintColor)
            },
        )
    }
}

@Composable
fun rememberGlassHazeBackgroundColor(
    glassPrefs: GlassPrefs,
    glassColors: GlassSurfaceColors,
    hazeStyle: HazeStyle,
): Color {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    return remember(glassPrefs.materialPreset, glassColors, hazeStyle, isDarkTheme, colorScheme) {
        when (glassPrefs.materialPreset) {
            AppSettings.GlassMaterialPreset.KOTOTORO,
            AppSettings.GlassMaterialPreset.CUSTOM -> glassColors.containerColor
            AppSettings.GlassMaterialPreset.HAZE_REGULAR,
            AppSettings.GlassMaterialPreset.CUPERTINO_REGULAR,
            AppSettings.GlassMaterialPreset.FLUENT_ACRYLIC -> if (isDarkTheme) {
                colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
            } else {
                colorScheme.surface.copy(alpha = 0.96f)
            }
        }
    }
}
