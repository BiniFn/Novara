package org.skepsun.kototoro.settings.sources.extensions

import android.os.Bundle
import androidx.fragment.app.Fragment
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import org.skepsun.kototoro.settings.SettingsActivity

abstract class LegacyExtensionsRedirectFragment : Fragment() {

	protected abstract val extensionType: ExternalExtensionType

	private var redirected = false

	override fun onResume() {
		super.onResume()
		if (redirected) {
			return
		}
		redirected = true
		(activity as? SettingsActivity)?.openFragment(
			fragmentClass = ExtensionsBrowserFragment::class.java,
			args = Bundle(1).apply { putString(ARG_EXTENSION_TYPE, extensionType.name) },
			isFromRoot = true,
		)
	}
}
