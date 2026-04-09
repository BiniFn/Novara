package org.skepsun.kototoro.core.ui.widgets

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.IdRes
import androidx.core.animation.doOnEnd
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.NavItem
import org.skepsun.kototoro.core.util.ext.resolveDp

class FrostedBottomNavigationView @JvmOverloads constructor(
	context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

	private val inactiveColor = Color.parseColor("#9CA3AF")
	private val activeColor = Color.WHITE
	private val activeBgColor = Color.parseColor("#26FFFFFF") // 15% white
	private val inactiveBgColor = Color.TRANSPARENT

	private val baseWidth by lazy { resources.resolveDp(48) }
	private val expandedWidth by lazy { resources.resolveDp(80) }

	private var currentSelectedIndex = 0
	private var onItemSelectedListener: ((NavItem) -> Unit)? = null
	
	private val items = mutableListOf<NavItem>()

	init {
		orientation = HORIZONTAL
		gravity = Gravity.CENTER
		setBackgroundResource(R.drawable.bg_frosted_nav)
		setPadding(resources.resolveDp(12), 0, resources.resolveDp(12), 0)
		minimumWidth = resources.resolveDp(280)
	}

	fun setItems(navItems: List<NavItem>) {
		removeAllViews()
		items.clear()
		items.addAll(navItems)

		val inflater = LayoutInflater.from(context)
		for ((index, item) in items.withIndex()) {
			val itemView = inflater.inflate(R.layout.frosted_nav_item, this, false) as FrameLayout
			val iconView = itemView.findViewById<ImageView>(R.id.navItemIcon)
			iconView.setImageResource(item.icon)

			itemView.setOnClickListener {
				val prevIndex = currentSelectedIndex
				if (prevIndex != index) {
					currentSelectedIndex = index
					animateSelection(prevIndex, index)
					onItemSelectedListener?.invoke(item)
				}
			}

			addView(itemView)
		}

		// Initial state
		doOnLayout {
			for ((index, item) in children.withIndex()) {
				val isSelected = index == currentSelectedIndex
				val layoutParams = item.layoutParams as LayoutParams
				layoutParams.width = if (isSelected) expandedWidth else baseWidth
				item.layoutParams = layoutParams
				
				applyColorToItem(
					item,
					if (isSelected) activeBgColor else inactiveBgColor,
					if (isSelected) activeColor else inactiveColor
				)
			}
		}
	}
	
	// Wait, the item background is @drawable/bg_frosted_nav_item which is a transparent shape with corners.
	// We can mutate the drawable to change its color without losing the shape.
	
	private fun applyColorToItem(itemView: View, bgColor: Int, iconColor: Int) {
		val bg = itemView.background.mutate() as android.graphics.drawable.GradientDrawable
		bg.setColor(bgColor)
		
		val iconView = itemView.findViewById<ImageView>(R.id.navItemIcon)
		iconView.setColorFilter(iconColor)
	}

	override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
		super.onLayout(changed, l, t, r, b)
		// Ensure initial state colors are correct after layout
		for ((index, item) in children.withIndex()) {
			val isSelected = index == currentSelectedIndex
			applyColorToItem(
				item,
				if (isSelected) activeBgColor else inactiveBgColor,
				if (isSelected) activeColor else inactiveColor
			)
		}
	}

	fun setOnItemSelectedListener(listener: (NavItem) -> Unit) {
		this.onItemSelectedListener = listener
	}
	
	fun setSelectedItemId(@IdRes itemId: Int) {
		val index = items.indexOfFirst { it.id == itemId }
		if (index != -1 && index != currentSelectedIndex) {
			val prevIndex = currentSelectedIndex
			currentSelectedIndex = index
			animateSelection(prevIndex, index)
		}
	}

	private fun animateSelection(oldIndex: Int, newIndex: Int) {
		val oldView = getChildAt(oldIndex)
		val newView = getChildAt(newIndex)

		// Expand newView, shrink oldView
		val widthAnimator = ValueAnimator.ofInt(baseWidth, expandedWidth)
		widthAnimator.addUpdateListener { anim ->
			val currentVal = anim.animatedValue as Int
			val remainingVal = expandedWidth + baseWidth - currentVal
			
			if (newView != null) {
				val lpNew = newView.layoutParams as LayoutParams
				lpNew.width = currentVal
				newView.layoutParams = lpNew
			}
			
			if (oldView != null) {
				val lpOld = oldView.layoutParams as LayoutParams
				lpOld.width = remainingVal
				oldView.layoutParams = lpOld
			}
		}
		// "Bouncy" spring approximation
		widthAnimator.interpolator = OvershootInterpolator(1.2f)
		widthAnimator.duration = 400

		// Background & Icon Color fading
		val colorAnimator = ValueAnimator.ofFloat(0f, 1f)
		val argbEval = ArgbEvaluator()
		colorAnimator.addUpdateListener { anim ->
			val fraction = anim.animatedFraction
			
			if (oldView != null) {
				val oldBgColor = argbEval.evaluate(fraction, activeBgColor, inactiveBgColor) as Int
				val oldIkColor = argbEval.evaluate(fraction, activeColor, inactiveColor) as Int
				applyColorToItem(oldView, oldBgColor, oldIkColor)
			}
			
			if (newView != null) {
				val newBgColor = argbEval.evaluate(fraction, inactiveBgColor, activeBgColor) as Int
				val newIkColor = argbEval.evaluate(fraction, inactiveColor, activeColor) as Int
				applyColorToItem(newView, newBgColor, newIkColor)
			}
		}
		colorAnimator.interpolator = FastOutSlowInInterpolator()
		colorAnimator.duration = 300

		widthAnimator.start()
		colorAnimator.start()
	}
}
