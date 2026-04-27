package org.skepsun.kototoro.core.util.ext

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.core.view.ancestors
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope

inline fun <T : Fragment> T.withArgs(size: Int, block: Bundle.() -> Unit): T {
	val b = Bundle(size)
	b.block()
	this.arguments = b
	return this
}

val Fragment.viewLifecycleScope
	inline get() = viewLifecycleOwner.lifecycle.coroutineScope

fun Fragment.addSupportMenuProvider(provider: MenuProvider) {
	val act = activity
	if (act != null) {
		val detailContainerId = act.resources.getIdentifier("container", "id", act.packageName)
		var isDetail = false
		var f: Fragment? = this
		while (f != null) {
			if (f.id == detailContainerId) {
				isDetail = true
				break
			}
			f = f.parentFragment
		}

		if (isDetail) {
			val toolbarId = act.resources.getIdentifier("toolbar_detail", "id", act.packageName)
			if (toolbarId != 0) {
				val detailToolbar = act.findViewById<androidx.appcompat.widget.Toolbar>(toolbarId)
				if (detailToolbar != null) {
					detailToolbar.addMenuProvider(provider, viewLifecycleOwner, Lifecycle.State.RESUMED)
					return
				}
			}
		}
	}
	requireActivity().addMenuProvider(provider, viewLifecycleOwner, Lifecycle.State.RESUMED)
}

@Suppress("UNCHECKED_CAST")
tailrec fun <T> Fragment.findParentCallback(cls: Class<T>): T? {
	val parent = parentFragment
	return when {
		parent == null -> cls.castOrNull(activity)
		cls.isInstance(parent) -> parent as T
		else -> parent.findParentCallback(cls)
	}
}

val Fragment.container: FragmentContainerView?
	get() = view?.ancestors?.firstNotNullOfOrNull {
		it as? FragmentContainerView // TODO check if direct parent
	}

fun Fragment.setSupportTitle(title: CharSequence?) {
	val act = activity
	if (act != null) {
		val detailContainerId = act.resources.getIdentifier("container", "id", act.packageName)
		var isDetail = false
		var f: Fragment? = this
		while (f != null) {
			if (f.id == detailContainerId) {
				isDetail = true
				break
			}
			f = f.parentFragment
		}

		if (isDetail) {
			val toolbarId = act.resources.getIdentifier("toolbar_detail", "id", act.packageName)
			if (toolbarId != 0) {
				val detailToolbar = act.findViewById<androidx.appcompat.widget.Toolbar>(toolbarId)
				if (detailToolbar != null) {
					detailToolbar.title = title
					return
				}
			}
		}
	}
	activity?.title = title
}

fun Fragment.setSupportTitle(titleRes: Int) {
	if (titleRes != 0) {
		setSupportTitle(getString(titleRes))
	} else {
		setSupportTitle(null as CharSequence?)
	}
}

fun Fragment.setSupportSubtitle(subtitle: CharSequence?) {
	val act = activity
	if (act != null) {
		val detailContainerId = act.resources.getIdentifier("container", "id", act.packageName)
		var isDetail = false
		var f: Fragment? = this
		while (f != null) {
			if (f.id == detailContainerId) {
				isDetail = true
				break
			}
			f = f.parentFragment
		}

		if (isDetail) {
			val toolbarId = act.resources.getIdentifier("toolbar_detail", "id", act.packageName)
			if (toolbarId != 0) {
				val detailToolbar = act.findViewById<androidx.appcompat.widget.Toolbar>(toolbarId)
				if (detailToolbar != null) {
					detailToolbar.subtitle = subtitle
					return
				}
			}
		}
	}
	(activity as? AppCompatActivity)?.supportActionBar?.subtitle = subtitle
}

fun Fragment.invalidateSupportMenu() {
	val act = activity
	if (act != null) {
		val detailContainerId = act.resources.getIdentifier("container", "id", act.packageName)
		var isDetail = false
		var f: Fragment? = this
		while (f != null) {
			if (f.id == detailContainerId) {
				isDetail = true
				break
			}
			f = f.parentFragment
		}

		if (isDetail) {
			val toolbarId = act.resources.getIdentifier("toolbar_detail", "id", act.packageName)
			if (toolbarId != 0) {
				val detailToolbar = act.findViewById<androidx.appcompat.widget.Toolbar>(toolbarId)
				if (detailToolbar != null) {
					(detailToolbar as? androidx.core.view.MenuHost)?.invalidateMenu()
					return
				}
			}
		}
	}
	activity?.invalidateOptionsMenu()
}
