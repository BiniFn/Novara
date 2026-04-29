package org.skepsun.kototoro.settings

import android.view.LayoutInflater
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.viewbinding.ViewBinding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import org.skepsun.kototoro.R

class SettingsActivityLayoutBinding private constructor(
	private val rootView: View,
	val legacyTopBarHost: View,
	val appbar: AppBarLayout,
	val toolbar: MaterialToolbar,
	val containerCompose: ComposeView,
	val containerMaster: ComposeView?,
	val appbarDetail: AppBarLayout?,
	val toolbarDetail: MaterialToolbar?,
) : ViewBinding {

	override fun getRoot(): View = rootView

	companion object {

		fun inflate(layoutInflater: LayoutInflater): SettingsActivityLayoutBinding {
			val root = layoutInflater.inflate(R.layout.activity_settings, null, false)
			return SettingsActivityLayoutBinding(
				rootView = root,
				legacyTopBarHost = root.findViewById(R.id.appbar),
				appbar = root.requireViewByIdCompat(R.id.appbar),
				toolbar = root.requireViewByIdCompat(R.id.toolbar),
				containerCompose = root.requireViewByIdCompat(R.id.container_compose),
				containerMaster = root.findViewById(R.id.container_master),
				appbarDetail = root.findViewById(R.id.appbar_detail),
				toolbarDetail = root.findViewById(R.id.toolbar_detail),
			)
		}

		private fun <T : View> View.requireViewByIdCompat(id: Int): T {
			return requireNotNull(findViewById(id)) {
				"Missing required view with id=${context.resources.getResourceName(id)}"
			}
		}
	}
}
