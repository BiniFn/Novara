package org.skepsun.kototoro.core.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NestedScrollBridgingFrameLayout @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), NestedScrollingParent3 {

	private val parentHelper = NestedScrollingParentHelper(this)

	private val _nestedScrollDeltaY = MutableStateFlow(0f)
	val nestedScrollDeltaY = _nestedScrollDeltaY.asStateFlow()

	override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
		return (axes and ViewCompat.SCROLL_AXIS_VERTICAL) != 0
	}

	override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
		parentHelper.onNestedScrollAccepted(child, target, axes, type)
	}

	override fun onStopNestedScroll(target: View, type: Int) {
		parentHelper.onStopNestedScroll(target, type)
		// Reset state or signal stop if needed, but typically delta is enough.
	}

	override fun onNestedScroll(
		target: View,
		dxConsumed: Int,
		dyConsumed: Int,
		dxUnconsumed: Int,
		dyUnconsumed: Int,
		type: Int,
		consumed: IntArray
	) {
		_nestedScrollDeltaY.value = dyConsumed.toFloat()
	}

	override fun onNestedScroll(
		target: View,
		dxConsumed: Int,
		dyConsumed: Int,
		dxUnconsumed: Int,
		dyUnconsumed: Int,
		type: Int
	) {
		onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, IntArray(2))
	}

	override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
		_nestedScrollDeltaY.value = dy.toFloat()
	}
}
