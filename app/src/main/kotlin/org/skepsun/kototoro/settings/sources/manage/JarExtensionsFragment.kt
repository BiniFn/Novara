package org.skepsun.kototoro.settings.sources.manage

import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import org.skepsun.kototoro.settings.sources.extensions.LegacyExtensionsRedirectFragment

class JarExtensionsFragment : LegacyExtensionsRedirectFragment() {

	override val extensionType: ExternalExtensionType = ExternalExtensionType.JAR
}
