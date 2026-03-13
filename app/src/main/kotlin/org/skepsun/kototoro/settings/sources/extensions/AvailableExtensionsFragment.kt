package org.skepsun.kototoro.settings.sources.extensions

import org.skepsun.kototoro.extensions.repo.ExternalExtensionType

class AvailableExtensionsFragment : LegacyExtensionsRedirectFragment() {

	override val extensionType: ExternalExtensionType
		get() = enumValueOf(checkNotNull(requireArguments().getString(ARG_EXTENSION_TYPE)))
}
