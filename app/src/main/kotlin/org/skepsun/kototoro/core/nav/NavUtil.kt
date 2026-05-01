package org.skepsun.kototoro.core.nav
import org.skepsun.kototoro.core.util.ext.findActivity

import android.app.ActivityOptions
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import org.skepsun.kototoro.core.util.ext.isAnimationsEnabled
import org.skepsun.kototoro.core.util.ext.isOnScreen

inline val FragmentActivity.router: AppRouter
	get() = AppRouter(this)

inline val Fragment.router: AppRouter
	get() = AppRouter(this)

tailrec fun Fragment.dismissParentDialog(): Boolean {
	return when (val parent = parentFragment) {
		null -> return false
		is DialogFragment -> {
			parent.dismiss()
			true
		}

		else -> parent.dismissParentDialog()
	}
}

fun scaleUpActivityOptionsOf(view: View): Bundle? {
	if (!view.context.isAnimationsEnabled || !view.isOnScreen()) {
		return null
	}
	return ActivityOptions.makeScaleUpAnimation(
		/* source = */ view,
		/* startX = */ 0,
		/* startY = */ 0,
		/* width = */ view.width,
		/* height = */ view.height,
	).toBundle()
}

fun sceneTransitionOptionsOf(view: View): Bundle? {
	if (!view.context.isAnimationsEnabled || !view.isOnScreen() || view.transitionName.isNullOrEmpty()) {
		return scaleUpActivityOptionsOf(view)
	}
	val activity = view.context.findActivity() ?: return scaleUpActivityOptionsOf(view)
	return androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(activity, view, view.transitionName).toBundle()
}

fun activityTransitionOptionsOf(activity: FragmentActivity): Bundle? {
	if (!activity.isAnimationsEnabled) {
		return null
	}
	return androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(activity).toBundle()
}
