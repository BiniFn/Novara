package org.skepsun.kototoro.settings.sources.mihon

import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import org.skepsun.kototoro.settings.sources.extensions.LegacyExtensionsRedirectFragment

class MihonExtensionsFragment : LegacyExtensionsRedirectFragment() {

	override val extensionType: ExternalExtensionType = ExternalExtensionType.MIHON
}
