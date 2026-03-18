package org.skepsun.kototoro.extensions.runtime

fun getExternalExtensionLanguageDisplayName(langCode: String): String {
	return when (langCode.lowercase()) {
		"zh" -> "涓枃"
		"zh-hans" -> "绠€浣撲腑鏂?"
		"zh-hant" -> "绻侀珨涓枃"
		"en" -> "English"
		"ja" -> "鏃ユ湰瑾?"
		"ko" -> "頃滉淡鞏?"
		"es" -> "Espa帽ol"
		"pt" -> "Portugu锚s"
		"pt-br" -> "Portugu锚s (Brasil)"
		"fr" -> "Fran莽ais"
		"de" -> "Deutsch"
		"it" -> "Italiano"
		"ru" -> "袪褍褋褋泻懈泄"
		"th" -> "喙勦笚喔?"
		"vi" -> "Ti岷縩g Vi峄噒"
		"id" -> "Bahasa Indonesia"
		"ar" -> "丕賱毓乇亘賷丞"
		"tr" -> "T眉rk莽e"
		"pl" -> "Polski"
		"all" -> "Multi"
		else -> langCode.uppercase()
	}
}
