package org.skepsun.kototoro.core.ui.compose

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class CompactPosterCardStyle(
    val itemWidth: Dp,
    val posterHeight: Dp,
    val cornerRadius: Dp,
)

fun compactPosterCardStyle(gridScale: Float): CompactPosterCardStyle {
    val normalizedScale = gridScale.coerceIn(0.5f, 1.4f)
    val width = (88f * normalizedScale).dp.coerceIn(44.dp, 116.dp)
    val height = (124f * normalizedScale).dp.coerceIn(62.dp, 164.dp)
    return CompactPosterCardStyle(
        itemWidth = width,
        posterHeight = height,
        cornerRadius = 18.dp,
    )
}

fun compactPosterRailCardStyle(gridScale: Float): CompactPosterCardStyle {
    val baseStyle = compactPosterCardStyle(gridScale)
    val scale = 80f / 88f
    return CompactPosterCardStyle(
        itemWidth = (baseStyle.itemWidth.value * scale).dp,
        posterHeight = (baseStyle.posterHeight.value * scale).dp,
        cornerRadius = baseStyle.cornerRadius,
    )
}
