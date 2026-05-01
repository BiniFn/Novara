package org.skepsun.kototoro.core.ui.compose

import android.view.View
import androidx.appcompat.R as appcompatR
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.util.ext.getThemeColor
import kotlin.math.roundToInt

private const val SCROLLBAR_HIDE_DELAY_MS = 1000L

private val FastScrollTouchWidth = 32.dp
private val FastScrollInsetEnd = 24.dp
private val FastScrollHandleEndPadding = 0.dp

@Composable
fun BoxScope.VerticalScrollbar(
	state: LazyListState,
	modifier: Modifier = Modifier,
	width: Dp = dimensionResource(R.dimen.fastscroll_handle_width),
	color: Color = Color.Unspecified,
	draggable: Boolean = true,
	labelProvider: ((Int) -> String)? = null,
) {
	FastScrollbar(
		modifier = modifier,
		width = width,
		color = color,
		draggable = draggable,
		labelProvider = labelProvider,
		totalItemsCount = { state.layoutInfo.totalItemsCount },
		visibleItemsCount = { state.layoutInfo.visibleItemsInfo.size },
		scrollPosition = { state.smoothListPosition() },
		isScrollInProgress = { state.isScrollInProgress },
		requestScrollToItem = state::requestScrollToItem,
	)
}

@Composable
fun BoxScope.VerticalScrollbar(
	state: LazyGridState,
	modifier: Modifier = Modifier,
	width: Dp = dimensionResource(R.dimen.fastscroll_handle_width),
	color: Color = Color.Unspecified,
	draggable: Boolean = true,
	labelProvider: ((Int) -> String)? = null,
) {
	FastScrollbar(
		modifier = modifier,
		width = width,
		color = color,
		draggable = draggable,
		labelProvider = labelProvider,
		totalItemsCount = { state.layoutInfo.totalItemsCount },
		visibleItemsCount = { state.layoutInfo.visibleItemsInfo.size },
		scrollPosition = { state.smoothGridPosition() },
		isScrollInProgress = { state.isScrollInProgress },
		requestScrollToItem = state::requestScrollToItem,
	)
}

@Composable
private fun BoxScope.FastScrollbar(
	modifier: Modifier,
	width: Dp,
	color: Color,
	draggable: Boolean,
	labelProvider: ((Int) -> String)?,
	totalItemsCount: () -> Int,
	visibleItemsCount: () -> Int,
	scrollPosition: () -> Float,
	isScrollInProgress: () -> Boolean,
	requestScrollToItem: (Int) -> Unit,
) {
	val context = LocalContext.current
	val rootView = LocalView.current
	val scrollTargets = remember { Channel<Int>(Channel.CONFLATED) }
	val showScrollbar by remember {
		derivedStateOf {
			val totalItems = totalItemsCount()
			totalItems > 0 && totalItems > visibleItemsCount()
		}
	}

	var isDragging by remember { mutableStateOf(false) }
	var dragPosition by remember { mutableFloatStateOf(0f) }
	var keepVisible by remember { mutableStateOf(false) }
	var lastDragTarget by remember { mutableIntStateOf(-1) }
	var trackHeightPx by remember { mutableFloatStateOf(0f) }
	val scrolling = isScrollInProgress()

	DisposableEffect(scrollTargets) {
		onDispose {
			scrollTargets.close()
		}
	}

	LaunchedEffect(scrollTargets) {
		while (true) {
			val firstTarget = scrollTargets.receiveCatching().getOrNull() ?: break
			withFrameNanos { }
			var target = firstTarget
			while (true) {
				target = scrollTargets.tryReceive().getOrNull() ?: break
			}
			requestScrollToItem(target)
		}
	}

	LaunchedEffect(scrolling, isDragging, showScrollbar) {
		if ((scrolling || isDragging) && showScrollbar) {
			keepVisible = true
		} else {
			delay(SCROLLBAR_HIDE_DELAY_MS)
			keepVisible = false
		}
	}

	val alpha = if (keepVisible && showScrollbar) 1f else 0f
	val density = LocalDensity.current
	val handleColor = remember(context, color) {
		if (color == Color.Unspecified) {
			Color(context.getThemeColor(appcompatR.attr.colorControlNormal, android.graphics.Color.DKGRAY))
		} else {
			color
		}
	}
	val handleHeight = dimensionResource(R.dimen.fastscroll_handle_height)
	val handleRadius = dimensionResource(R.dimen.fastscroll_handle_radius)
	val scrollbarMarginTop = dimensionResource(R.dimen.fastscroll_scrollbar_margin_top)
	val scrollbarMarginBottom = dimensionResource(R.dimen.fastscroll_scrollbar_margin_bottom)
	val handleWidthPx = with(density) { width.toPx() }
	val handleHeightPx = with(density) { handleHeight.toPx() }
	val handleRadiusPx = with(density) { handleRadius.toPx() }
	val handleEndPaddingPx = with(density) { FastScrollHandleEndPadding.toPx() }

	Box(
		modifier = modifier
			.align(Alignment.CenterEnd)
			.fillMaxHeight()
			.padding(
				top = scrollbarMarginTop,
				end = FastScrollInsetEnd,
				bottom = scrollbarMarginBottom,
			)
			.width(FastScrollTouchWidth)
			.then(
				if (draggable) {
					Modifier.fastScrollbarPointerInput(
						rootView = rootView,
						showScrollbar = { showScrollbar },
						totalItemsCount = totalItemsCount,
						visibleItemsCount = visibleItemsCount,
						handleHeightPx = handleHeightPx,
						onDragStart = {
							isDragging = true
							keepVisible = true
							lastDragTarget = -1
						},
						onDragStop = {
							isDragging = false
						},
						onDragChanged = { position, index ->
							dragPosition = position
							if (index != lastDragTarget) {
								lastDragTarget = index
								scrollTargets.trySend(index)
							}
						},
					)
				} else {
					Modifier
				},
			),
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.onSizeChanged { size -> trackHeightPx = size.height.toFloat() },
		) {
			val totalItems = totalItemsCount()
			val visibleItems = visibleItemsCount()
			val maxScrollPosition = (totalItems - 1).coerceAtLeast(1)
			val currentScrollFraction = if (isDragging) {
				dragPosition / maxScrollPosition
			} else {
				scrollPosition() / maxScrollPosition
			}
			val barHeightPx = handleHeightPx.coerceAtMost(trackHeightPx)
			val barTopPx = (trackHeightPx - barHeightPx) * currentScrollFraction.coerceIn(0f, 1f)

			Canvas(modifier = Modifier.fillMaxSize()) {
				if (alpha <= 0f || !showScrollbar || totalItems <= 0 || visibleItems <= 0 || totalItems <= visibleItems) {
					return@Canvas
				}

				val barLeft = size.width - handleEndPaddingPx - handleWidthPx
				drawRoundRect(
					color = handleColor.copy(alpha = handleColor.alpha * alpha),
					topLeft = Offset(barLeft, barTopPx),
					size = Size(handleWidthPx, barHeightPx),
					cornerRadius = CornerRadius(handleRadiusPx),
				)
			}
		}
	}
}

private fun Modifier.fastScrollbarPointerInput(
	rootView: View,
	showScrollbar: () -> Boolean,
	totalItemsCount: () -> Int,
	visibleItemsCount: () -> Int,
	handleHeightPx: Float,
	onDragStart: () -> Unit,
	onDragStop: () -> Unit,
	onDragChanged: (position: Float, targetIndex: Int) -> Unit,
): Modifier = pointerInput(rootView, handleHeightPx) {
	awaitEachGesture {
		val down = awaitFirstDown(requireUnconsumed = false)
		if (!showScrollbar()) {
			return@awaitEachGesture
		}

		rootView.parent?.requestDisallowInterceptTouchEvent(true)
		onDragStart()
		down.consume()

		fun scrollToPointer(y: Float) {
			val totalItems = totalItemsCount()
			val visibleItems = visibleItemsCount()
			if (totalItems <= 0 || totalItems <= visibleItems) {
				return
			}

			val maxScrollPosition = (totalItems - 1).coerceAtLeast(1)
			val availableHeight = (size.height - handleHeightPx).coerceAtLeast(1f)
			val fraction = (y - handleHeightPx / 2f).coerceIn(0f, availableHeight) / availableHeight
			val position = fraction * maxScrollPosition
			val targetIndex = position.roundToInt().coerceIn(0, totalItems - 1)
			onDragChanged(position, targetIndex)
		}

		try {
			scrollToPointer(down.position.y)
			while (true) {
				val event = awaitPointerEvent()
				val change = event.changes.firstOrNull { it.id == down.id } ?: break
				if (!change.pressed) {
					break
				}
				change.consume()
				scrollToPointer(change.position.y)
			}
		} finally {
			onDragStop()
			rootView.parent?.requestDisallowInterceptTouchEvent(false)
		}
	}
}

private fun LazyListState.smoothListPosition(): Float {
	val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == firstVisibleItemIndex }
		?: layoutInfo.visibleItemsInfo.firstOrNull()
		?: return firstVisibleItemIndex.toFloat()
	val itemSize = itemInfo.size.coerceAtLeast(1)
	val offsetFraction = firstVisibleItemScrollOffset.toFloat() / itemSize
	return firstVisibleItemIndex + offsetFraction.coerceIn(0f, 1f)
}

private fun LazyGridState.smoothGridPosition(): Float {
	val firstInfo = layoutInfo.visibleItemsInfo.minByOrNull { it.index }
		?: return firstVisibleItemIndex.toFloat()
	val itemSize = firstInfo.size.height.coerceAtLeast(1)
	val offsetFraction = firstVisibleItemScrollOffset.toFloat() / itemSize
	return firstVisibleItemIndex + offsetFraction.coerceIn(0f, 1f)
}
