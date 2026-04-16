package org.skepsun.kototoro.settings.sources.extensions

import org.skepsun.kototoro.extensions.repo.ExternalExtensionType

internal fun ExternalExtensionType.normalizePackageNameForMatching(packageName: String): String {
	return when (this) {
		ExternalExtensionType.IREADER -> packageName.toInstalledIReaderPackageName()
		else -> packageName
	}
}

internal fun String.toInstalledIReaderPackageName(): String {
	if (!startsWith("ireader-")) {
		return this
	}
	val parts = split("-")
	if (parts.size < 3 || parts.first() != "ireader") {
		return this
	}
	val lang = parts[1]
	val sourceName = parts.drop(2).joinToString("-")
	return "ireader.$sourceName.$lang"
}
