package org.skepsun.kototoro.core.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

fun Modifier.verticalScrollbar(
    state: LazyListState,
    width: Dp = 8.dp,
    color: Color = Color.Gray.copy(alpha = 0.5f),
    draggable: Boolean = true,
    labelProvider: ((Int) -> String)? = null,
): Modifier = composed {
    val coroutineScope = rememberCoroutineScope()
    val isScrollInProgress = state.isScrollInProgress
    val totalItems = state.layoutInfo.totalItemsCount
    val visibleItems = state.layoutInfo.visibleItemsInfo.size
    val showScrollbar = totalItems > visibleItems

    var isDragging by remember { mutableStateOf(false) }
    var dragLabelIndex by remember { mutableIntStateOf(0) }

    val alpha by animateFloatAsState(
        targetValue = if ((isScrollInProgress || isDragging) && showScrollbar) 1f else 0f,
        animationSpec = tween(durationMillis = if (isScrollInProgress || isDragging) 150 else 1000),
        label = "scrollbar_alpha",
    )

    val thumbWidthPx = with(LocalDensity.current) { width.toPx() }
    val touchWidthPx = with(LocalDensity.current) { 24.dp.toPx() }

    this
        .then(
            if (draggable) {
                Modifier.pointerInput(totalItems, visibleItems) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val barHeightFraction = (visibleItems.toFloat() / totalItems).coerceIn(0.05f, 1f)
                            val barHeight = size.height * barHeightFraction
                            val firstVisible = state.firstVisibleItemIndex
                            val scrollFraction = firstVisible.toFloat() / (totalItems - visibleItems).coerceAtLeast(1)
                            val barTop = (size.height - barHeight) * scrollFraction
                            val inThumbX = offset.x >= size.width - touchWidthPx
                            val inThumbY = offset.y >= barTop - 24f && offset.y <= barTop + barHeight + 24f
                            if (inThumbX && inThumbY) {
                                isDragging = true
                            }
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, _ ->
                            if (isDragging) {
                                change.consume()
                                val newY = change.position.y.coerceIn(0f, size.height.toFloat())
                                val fraction = newY / size.height
                                val targetIndex = (fraction * totalItems).toInt().coerceIn(0, totalItems - 1)
                                dragLabelIndex = targetIndex
                                coroutineScope.launch {
                                    state.scrollToItem(targetIndex)
                                }
                            }
                        },
                    )
                }
            } else {
                Modifier
            },
        )
        .drawWithContent {
            drawContent()
            if (alpha > 0f && totalItems > 0 && visibleItems > 0) {
                val firstVisible = if (isDragging) dragLabelIndex else state.firstVisibleItemIndex
                val scrollFraction = firstVisible.toFloat() / (totalItems - visibleItems).coerceAtLeast(1)
                val barHeightFraction = (visibleItems.toFloat() / totalItems).coerceIn(0.05f, 1f)
                val barHeight = size.height * barHeightFraction
                val barTop = (size.height - barHeight) * scrollFraction

                drawRoundRect(
                    color = color.copy(alpha = color.alpha * alpha * 0.3f),
                    topLeft = Offset(size.width - thumbWidthPx, 0f),
                    size = Size(thumbWidthPx, size.height),
                    cornerRadius = CornerRadius(thumbWidthPx / 2f),
                )
                drawRoundRect(
                    color = color.copy(alpha = color.alpha * alpha),
                    topLeft = Offset(size.width - thumbWidthPx, barTop),
                    size = Size(thumbWidthPx, barHeight.coerceAtLeast(48.dp.toPx())),
                    cornerRadius = CornerRadius(thumbWidthPx / 2f),
                )
            }
        }
}

fun Modifier.verticalScrollbar(
    state: LazyGridState,
    width: Dp = 8.dp,
    color: Color = Color.Gray.copy(alpha = 0.5f),
    draggable: Boolean = false,
    labelProvider: ((Int) -> String)? = null,
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
                color = color.copy(alpha = color.alpha * alpha * 0.3f),
                topLeft = Offset(size.width - widthPx, 0f),
                size = Size(widthPx, size.height),
                cornerRadius = CornerRadius(widthPx / 2f),
            )
            drawRoundRect(
                color = color.copy(alpha = color.alpha * alpha),
                topLeft = Offset(size.width - widthPx, barTop),
                size = Size(widthPx, barHeight.coerceAtLeast(48.dp.toPx())),
                cornerRadius = CornerRadius(widthPx / 2f),
            )
        }
    }
}
