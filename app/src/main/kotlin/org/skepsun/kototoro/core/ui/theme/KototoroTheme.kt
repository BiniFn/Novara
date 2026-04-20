package org.skepsun.kototoro.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import org.skepsun.kototoro.core.util.ext.getThemeColor

@Composable
fun KototoroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = remember(context, darkTheme, dynamicColor) {
        context.resolveComposeColorScheme(darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

private fun android.content.Context.resolveComposeColorScheme(
    darkTheme: Boolean,
): ColorScheme {
    val background = themeColor(android.R.attr.colorBackground)
    val surface = themeColorByName("colorSurface", background)
    val primary = themeColorByName("colorPrimary")
    val surfaceVariant = themeColorByName("colorSurfaceVariant", surface)
    val surfaceContainer = themeColorByName("colorSurfaceContainer", surface)
    val surfaceContainerHigh = themeColorByName("colorSurfaceContainerHigh", surfaceContainer)

    val common = ThemeColorSnapshot(
        primary = primary,
        onPrimary = themeColorByName("colorOnPrimary"),
        primaryContainer = themeColorByName("colorPrimaryContainer", primary),
        onPrimaryContainer = themeColorByName("colorOnPrimaryContainer"),
        inversePrimary = themeColorByName("colorPrimaryInverse", primary),
        secondary = themeColorByName("colorSecondary"),
        onSecondary = themeColorByName("colorOnSecondary"),
        secondaryContainer = themeColorByName("colorSecondaryContainer"),
        onSecondaryContainer = themeColorByName("colorOnSecondaryContainer"),
        tertiary = themeColorByName("colorTertiary"),
        onTertiary = themeColorByName("colorOnTertiary"),
        tertiaryContainer = themeColorByName("colorTertiaryContainer"),
        onTertiaryContainer = themeColorByName("colorOnTertiaryContainer"),
        background = background,
        onBackground = themeColorByName("colorOnBackground"),
        surface = surface,
        onSurface = themeColorByName("colorOnSurface"),
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = themeColorByName("colorOnSurfaceVariant"),
        inverseSurface = themeColorByName("colorSurfaceInverse", background),
        inverseOnSurface = themeColorByName("colorOnSurfaceInverse"),
        error = themeColorByName("colorError"),
        onError = themeColorByName("colorOnError"),
        errorContainer = themeColorByName("colorErrorContainer"),
        onErrorContainer = themeColorByName("colorOnErrorContainer"),
        outline = themeColorByName("colorOutline"),
        outlineVariant = themeColorByName("colorOutlineVariant"),
        surfaceBright = themeColorByName("colorSurfaceBright", surface),
        surfaceDim = themeColorByName("colorSurfaceDim", surface),
        surfaceContainerLowest = themeColorByName("colorSurfaceContainerLowest", surface),
        surfaceContainerLow = themeColorByName("colorSurfaceContainerLow", surface),
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = themeColorByName("colorSurfaceContainerHighest", surfaceContainerHigh),
    )

    return if (darkTheme) {
        darkColorScheme(
            primary = common.primary,
            onPrimary = common.onPrimary,
            primaryContainer = common.primaryContainer,
            onPrimaryContainer = common.onPrimaryContainer,
            inversePrimary = common.inversePrimary,
            secondary = common.secondary,
            onSecondary = common.onSecondary,
            secondaryContainer = common.secondaryContainer,
            onSecondaryContainer = common.onSecondaryContainer,
            tertiary = common.tertiary,
            onTertiary = common.onTertiary,
            tertiaryContainer = common.tertiaryContainer,
            onTertiaryContainer = common.onTertiaryContainer,
            background = common.background,
            onBackground = common.onBackground,
            surface = common.surface,
            onSurface = common.onSurface,
            surfaceVariant = common.surfaceVariant,
            onSurfaceVariant = common.onSurfaceVariant,
            surfaceTint = common.primary,
            inverseSurface = common.inverseSurface,
            inverseOnSurface = common.inverseOnSurface,
            error = common.error,
            onError = common.onError,
            errorContainer = common.errorContainer,
            onErrorContainer = common.onErrorContainer,
            outline = common.outline,
            outlineVariant = common.outlineVariant,
            scrim = Color.Black,
            surfaceBright = common.surfaceBright,
            surfaceDim = common.surfaceDim,
            surfaceContainerLowest = common.surfaceContainerLowest,
            surfaceContainerLow = common.surfaceContainerLow,
            surfaceContainer = common.surfaceContainer,
            surfaceContainerHigh = common.surfaceContainerHigh,
            surfaceContainerHighest = common.surfaceContainerHighest,
        )
    } else {
        lightColorScheme(
            primary = common.primary,
            onPrimary = common.onPrimary,
            primaryContainer = common.primaryContainer,
            onPrimaryContainer = common.onPrimaryContainer,
            inversePrimary = common.inversePrimary,
            secondary = common.secondary,
            onSecondary = common.onSecondary,
            secondaryContainer = common.secondaryContainer,
            onSecondaryContainer = common.onSecondaryContainer,
            tertiary = common.tertiary,
            onTertiary = common.onTertiary,
            tertiaryContainer = common.tertiaryContainer,
            onTertiaryContainer = common.onTertiaryContainer,
            background = common.background,
            onBackground = common.onBackground,
            surface = common.surface,
            onSurface = common.onSurface,
            surfaceVariant = common.surfaceVariant,
            onSurfaceVariant = common.onSurfaceVariant,
            surfaceTint = common.primary,
            inverseSurface = common.inverseSurface,
            inverseOnSurface = common.inverseOnSurface,
            error = common.error,
            onError = common.onError,
            errorContainer = common.errorContainer,
            onErrorContainer = common.onErrorContainer,
            outline = common.outline,
            outlineVariant = common.outlineVariant,
            scrim = Color.Black,
            surfaceBright = common.surfaceBright,
            surfaceDim = common.surfaceDim,
            surfaceContainerLowest = common.surfaceContainerLowest,
            surfaceContainerLow = common.surfaceContainerLow,
            surfaceContainer = common.surfaceContainer,
            surfaceContainerHigh = common.surfaceContainerHigh,
            surfaceContainerHighest = common.surfaceContainerHighest,
        )
    }
}

private fun android.content.Context.themeColorByName(
    attrName: String,
    fallback: Color = Color.Unspecified,
): Color {
    val attrId = resources.getIdentifier(attrName, "attr", packageName)
        .takeIf { it != 0 }
        ?: resources.getIdentifier(attrName, "attr", "com.google.android.material")

    return if (attrId != 0) {
        themeColor(attrId, fallback)
    } else if (fallback.isSpecified) {
        fallback
    } else {
        Color.Transparent
    }
}

private fun android.content.Context.themeColor(
    attr: Int,
    fallback: Color = Color.Unspecified,
): Color {
    val fallbackArgb = if (fallback.isSpecified) fallback.toArgb() else android.graphics.Color.TRANSPARENT
    return Color(getThemeColor(attr, fallbackArgb))
}

private data class ThemeColorSnapshot(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val inversePrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val outline: Color,
    val outlineVariant: Color,
    val surfaceBright: Color,
    val surfaceDim: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
)
