package org.skepsun.kototoro.settings.sources.aniyomi

import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import org.skepsun.kototoro.settings.sources.extensions.LegacyExtensionsRedirectFragment

class AniyomiExtensionsFragment : LegacyExtensionsRedirectFragment() {

	override val extensionType: ExternalExtensionType = ExternalExtensionType.ANIYOMI
}
