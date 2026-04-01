package org.skepsun.kototoro.reader.translate.domain

private const val AUTO_LANGUAGE = "auto"
private const val DEFAULT_SOURCE_LANGUAGE = "ja"

private val supportedTranslationLanguages = setOf(
	"ar",
	"bg",
	"bn",
	"ca",
	"cs",
	"da",
	"de",
	"el",
	"en",
	"es",
	"fi",
	"fr",
	"hi",
	"hr",
	"it",
	"ja",
	"ko",
	"nl",
	"pl",
	"pt",
	"ro",
	"ru",
	"sk",
	"sv",
	"tl",
	"tr",
	"uk",
	"vi",
	"zh",
)

fun isAutoReaderTranslationLanguage(language: String?): Boolean {
	return language.normalizeReaderTranslationLanguageTag() == AUTO_LANGUAGE
}

fun resolveReaderTranslationSourceLanguage(
	preferredLanguage: String?,
	contentLanguage: String?,
): String {
	val normalizedPreference = preferredLanguage.normalizeReaderTranslationLanguageTag()
	return if (normalizedPreference == AUTO_LANGUAGE) {
		contentLanguage.normalizeReaderTranslationLanguageTag()
			?.takeIf { it in supportedTranslationLanguages }
			?: DEFAULT_SOURCE_LANGUAGE
	} else {
		normalizedPreference
			?.takeIf { it in supportedTranslationLanguages }
			?: DEFAULT_SOURCE_LANGUAGE
	}
}

fun String?.normalizeReaderTranslationLanguageTag(): String? {
	return this
		?.trim()
		?.lowercase()
		?.replace('_', '-')
		?.substringBefore('-')
		?.ifBlank { null }
}
