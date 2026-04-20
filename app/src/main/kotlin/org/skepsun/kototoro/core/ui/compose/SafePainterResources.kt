package org.skepsun.kototoro.core.ui.compose

import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

@Composable
fun rememberSafePainter(@DrawableRes id: Int): Painter {
    val context = LocalContext.current
    return remember(id, context) {
        val drawable = ContextCompat.getDrawable(context, id)
        if (drawable != null) {
            DrawablePainter(drawable.mutate())
        } else {
            ColorPainter(Color.Transparent)
        }
    }
}

private class DrawablePainter(
    private val drawable: Drawable,
) : Painter() {
    override val intrinsicSize: Size
        get() = Size(
            width = drawable.intrinsicWidth.toFloat().takeIf { it >= 0 } ?: Size.Unspecified.width,
            height = drawable.intrinsicHeight.toFloat().takeIf { it >= 0 } ?: Size.Unspecified.height,
        )

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }

    override fun applyAlpha(alpha: Float): Boolean {
        drawable.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        drawable.colorFilter = colorFilter?.asAndroidColorFilter()
        return true
    }
}
