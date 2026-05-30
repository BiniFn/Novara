package org.skepsun.kototoro.main.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.core.ui.glass.GlassDefaults
import org.skepsun.kototoro.core.ui.glass.LocalHazeState
import org.skepsun.kototoro.core.ui.glass.rememberGlassPrefsOrFallback
import org.skepsun.kototoro.core.ui.glass.rememberGlassSurfaceColors
import org.skepsun.kototoro.core.ui.glass.supportsRuntimeHaze
import dev.chrisbanes.haze.HazeDefaults as HazeBlurDefaults
import dev.chrisbanes.haze.hazeChild

@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val style = GlassDefaults.prominentStyle()
    val glassPrefs = rememberGlassPrefsOrFallback()
    val glassColors = rememberGlassSurfaceColors(style = style, glassPrefs = glassPrefs)
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val hazeState = LocalHazeState.current
    val useRuntimeHaze = glassPrefs.isGlassEffectEnabled && supportsRuntimeHaze()
    val hazeBackgroundColor = if (isDarkTheme) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    }
    val menuContainerColor = if (useRuntimeHaze) {
        Color.Transparent
    } else if (isDarkTheme) {
        glassColors.containerColor
    } else {
        glassColors.containerColor.copy(alpha = glassColors.containerColor.alpha * 0.82f)
    }
    val hazeStyle = HazeBlurDefaults.style(
        Color.Transparent,
        HazeBlurDefaults.tint(glassColors.baseTintColor),
        glassColors.blurRadius,
        glassColors.noiseFactor,
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .heightIn(max = 360.dp)
            .clip(shape)
            .then(
                if (useRuntimeHaze) {
                    Modifier.hazeChild(hazeState, hazeStyle) {
                        backgroundColor = hazeBackgroundColor
                        blurredEdgeTreatment = BlurredEdgeTreatment(shape)
                        clipToAreasBounds = true
                        expandLayerBounds = false
                        forceInvalidateOnPreDraw = true
                    }
                } else {
                    Modifier
                },
            ),
        offset = offset,
        shape = shape,
        containerColor = menuContainerColor,
        tonalElevation = 0.dp,
        shadowElevation = if (isDarkTheme) style.shadowElevation else 2.dp,
        border = glassColors.border,
        content = content,
    )
}
