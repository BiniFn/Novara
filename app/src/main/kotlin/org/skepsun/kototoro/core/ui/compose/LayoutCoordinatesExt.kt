package org.skepsun.kototoro.core.ui.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates

fun LayoutCoordinates.unclippedBoundsInWindow(): Rect {
    val topLeft = localToWindow(Offset.Zero)
    val bottomRight = localToWindow(
        Offset(
            x = size.width.toFloat(),
            y = size.height.toFloat(),
        ),
    )
    return Rect(
        left = topLeft.x,
        top = topLeft.y,
        right = bottomRight.x,
        bottom = bottomRight.y,
    )
}
