package org.skepsun.kototoro.aniyomi.model

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import org.skepsun.kototoro.parsers.model.MangaSource

/**
 * Wrapper that adapts an Aniyomi AnimeCatalogueSource to Kototoro's MangaSource interface.
 */
data class AniyomiAnimeSource(
    val animeCatalogueSource: AnimeCatalogueSource,
    val pkgName: String,
    val isNsfw: Boolean = false,
    /**
     * Whether this source should display its language in the name.
     */
    val hasLanguageSuffix: Boolean = false,
) : MangaSource {
    
    /**
     * The source name, following the Aniyomi convention: ANIYOMI_{sourceId}
     */
    override val name: String
        get() = "ANIYOMI_${animeCatalogueSource.id}"
    
    /**
     * The display name for the source.
     */
    val displayName: String
        get() = if (hasLanguageSuffix) {
            "${animeCatalogueSource.name} (${getLanguageDisplayName(language)})"
        } else {
            animeCatalogueSource.name
        }
    
    /**
     * The language code.
     */
    val language: String
        get() = animeCatalogueSource.lang
    
    /**
     * The unique source ID.
     */
    val sourceId: Long
        get() = animeCatalogueSource.id
    
    /**
     * Whether this source supports latest updates.
     */
    val supportsLatest: Boolean
        get() = animeCatalogueSource.supportsLatest
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MangaSource) return false
        return name == other.name
    }
    
    override fun hashCode(): Int {
        return name.hashCode()
    }
    
    override fun toString(): String {
        return "AniyomiAnimeSource(id=${animeCatalogueSource.id}, name=${animeCatalogueSource.name}, lang=$language)"
    }
    
    companion object {
        fun getLanguageDisplayName(langCode: String): String {
            return when (langCode.lowercase()) {
                "zh" -> "中文"
                "zh-hans" -> "简体中文"
                "zh-hant" -> "繁體中文"
                "en" -> "English"
                "ja" -> "日本語"
                "ko" -> "한국어"
                "es" -> "Español"
                "pt" -> "Português"
                "pt-br" -> "Português (Brasil)"
                "fr" -> "Français"
                "de" -> "Deutsch"
                "it" -> "Italiano"
                "ru" -> "Русский"
                "th" -> "ไทย"
                "vi" -> "Tiếng Việt"
                "id" -> "Bahasa Indonesia"
                "ar" -> "العربية"
                "tr" -> "Türkçe"
                "pl" -> "Polski"
                "all" -> "Multi"
                else -> langCode.uppercase()
            }
        }
    }
}
