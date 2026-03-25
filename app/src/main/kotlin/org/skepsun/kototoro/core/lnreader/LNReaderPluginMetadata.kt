package org.skepsun.kototoro.core.lnreader

import kotlinx.serialization.Serializable

/**
 * Metadata extracted from a LNReader JS plugin.
 * Mirrors IReader's PluginMetadata structure.
 */
@Serializable
data class LNReaderPluginMetadata(
	val id: String,
	val name: String,
	val site: String = "",
	val version: String = "1.0.0",
	val lang: String = "en",
	val icon: String = ""
) {
	companion object {
		/**
		 * Extract metadata from JS source code without executing it.
		 * Uses regex patterns matching IReader's extractMetadataFromCode.
		 */
		fun extractFromCode(jsCode: String, fallbackId: String): LNReaderPluginMetadata? {
			// Validate - reject error pages
			if (jsCode.contains("404") && jsCode.contains("Not Found") && jsCode.length < 1000) return null
			if (jsCode.trim().startsWith("<!DOCTYPE") || jsCode.trim().startsWith("<html")) return null
			if (jsCode.isBlank()) return null

			val id = listOf(
				"""(?s)id\s*[:=]\s*['"`]([^'"`]+)['"`]""".toRegex(),
				"""(?s)['"]id['"]\s*[:=]\s*['"`]([^'"`]+)['"`]""".toRegex(),
				"""(?s)get\s+id\s*\(\)\s*\{\s*return\s+['"`]([^'"`]+)['"`]""".toRegex()
			).firstNotNullOfOrNull { it.find(jsCode)?.groupValues?.get(1) } ?: fallbackId

			val name = listOf(
				"""(?s)name\s*[:=]\s*['"`]([^'"`]+)['"`]""".toRegex(),
				"""(?s)['"]name['"]\s*[:=]\s*['"`]([^'"`]+)['"`]""".toRegex(),
				"""(?s)get\s+name\s*\(\)\s*\{\s*return\s+['"`]([^'"`]+)['"`]""".toRegex()
			).firstNotNullOfOrNull {
				it.find(jsCode)?.groupValues?.get(1)?.takeIf { n -> n.length > 2 }
			} ?: id.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: "Unknown"

			val version = listOf(
				"""(?s)version\s*[:=]\s*['"`]([^'"`]+)['"`]""".toRegex(),
				"""(?s)['"]version['"]\s*[:=]\s*['"`]([^'"`]+)['"`]""".toRegex(),
				"""(?s)get\s+version\s*\(\)\s*\{\s*return\s+['"`]([^'"`]+)['"`]""".toRegex()
			).firstNotNullOfOrNull {
				it.find(jsCode)?.groupValues?.get(1)?.takeIf { v -> v.matches(Regex("""[\d.]+""")) }
			} ?: "1.0.0"

			val site = listOf(
				"""(?s)site\s*[:=]\s*['"`]([^'"`]+)['"`]""".toRegex(),
				"""(?s)baseUrl\s*[:=]\s*['"`]([^'"`]+)['"`]""".toRegex(),
				"""(?s)['"]site['"]\s*[:=]\s*['"`]([^'"`]+)['"`]""".toRegex(),
				"""(?s)get\s+site\s*\(\)\s*\{\s*return\s+['"`]([^'"`]+)['"`]""".toRegex()
			).firstNotNullOfOrNull {
				it.find(jsCode)?.groupValues?.get(1)?.takeIf { s -> s.startsWith("http") }
			} ?: ""

			val lang = listOf(
				"""(?s)lang\s*[:=]\s*['"`]([^'"`]+)['"`]""".toRegex(),
				"""(?s)['"]lang['"]\s*[:=]\s*['"`]([^'"`]+)['"`]""".toRegex(),
				"""(?s)get\s+lang\s*\(\)\s*\{\s*return\s+['"`]([^'"`]+)['"`]""".toRegex()
			).firstNotNullOfOrNull { it.find(jsCode)?.groupValues?.get(1) } ?: "en"

			val icon = listOf(
				"""(?s)icon\s*[:=]\s*['"`]([^'"`]+)['"`]""".toRegex(),
				"""(?s)['"]icon['"]\s*[:=]\s*['"`]([^'"`]+)['"`]""".toRegex(),
				"""(?s)get\s+icon\s*\(\)\s*\{\s*return\s+['"`]([^'"`]+)['"`]""".toRegex(),
				"""(?s)iconUrl\s*[:=]\s*['"`]([^'"`]+)['"`]""".toRegex()
			).firstNotNullOfOrNull { it.find(jsCode)?.groupValues?.get(1) } ?: ""

			return LNReaderPluginMetadata(id, name, site, version, lang, icon)
		}
	}
}
