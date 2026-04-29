package org.skepsun.kototoro.core.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.verticalScrollbar(
	state: LazyListState,
	width: Dp = 4.dp,
	color: Color = Color.Gray.copy(alpha = 0.5f),
): Modifier = composed {
	val isScrollInProgress = state.isScrollInProgress
	val totalItems = state.layoutInfo.totalItemsCount
	val visibleItems = state.layoutInfo.visibleItemsInfo.size
	val showScrollbar = totalItems > visibleItems

	val alpha by animateFloatAsState(
		targetValue = if (isScrollInProgress && showScrollbar) 1f else 0f,
		animationSpec = tween(durationMillis = if (isScrollInProgress) 150 else 1000),
		label = "scrollbar_alpha",
	)

	drawWithContent {
		drawContent()
		if (alpha > 0f && totalItems > 0 && visibleItems > 0) {
			val firstVisible = state.firstVisibleItemIndex
			val scrollFraction = firstVisible.toFloat() / (totalItems - visibleItems).coerceAtLeast(1)
			val barHeightFraction = (visibleItems.toFloat() / totalItems).coerceIn(0.05f, 1f)
			val barHeight = size.height * barHeightFraction
			val barTop = (size.height - barHeight) * scrollFraction
			val widthPx = width.toPx()

			drawRoundRect(
				color = color.copy(alpha = color.alpha * alpha),
				topLeft = Offset(size.width - widthPx, barTop),
				size = Size(widthPx, barHeight),
				cornerRadius = CornerRadius(widthPx / 2f),
			)
		}
	}
}

fun Modifier.verticalScrollbar(
	state: LazyGridState,
	width: Dp = 4.dp,
	color: Color = Color.Gray.copy(alpha = 0.5f),
): Modifier = composed {
	val isScrollInProgress = state.isScrollInProgress
	val totalItems = state.layoutInfo.totalItemsCount
	val visibleItems = state.layoutInfo.visibleItemsInfo.size
	val showScrollbar = totalItems > visibleItems

	val alpha by animateFloatAsState(
		targetValue = if (isScrollInProgress && showScrollbar) 1f else 0f,
		animationSpec = tween(durationMillis = if (isScrollInProgress) 150 else 1000),
		label = "scrollbar_alpha",
	)

	drawWithContent {
		drawContent()
		if (alpha > 0f && totalItems > 0 && visibleItems > 0) {
			val firstVisible = state.firstVisibleItemIndex
			val scrollFraction = firstVisible.toFloat() / (totalItems - visibleItems).coerceAtLeast(1)
			val barHeightFraction = (visibleItems.toFloat() / totalItems).coerceIn(0.05f, 1f)
			val barHeight = size.height * barHeightFraction
			val barTop = (size.height - barHeight) * scrollFraction
			val widthPx = width.toPx()

			drawRoundRect(
				color = color.copy(alpha = color.alpha * alpha),
				topLeft = Offset(size.width - widthPx, barTop),
				size = Size(widthPx, barHeight),
				cornerRadius = CornerRadius(widthPx / 2f),
			)
		}
	}
}
