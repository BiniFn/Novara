package org.skepsun.kototoro.ireader.model

import ireader.core.source.Source
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentType

data class IReaderMangaSource(
    val pkgName: String,
    val catalogueSource: Source,
    val isNsfw: Boolean,
    val language: String,
    val displayName: String = catalogueSource.name,
) : ContentSource {
    override val locale: String get() = org.skepsun.kototoro.core.model.mapIReaderLangToLocale(language) ?: language
    override val contentType: ContentType get() = if (isNsfw) ContentType.HENTAI_NOVEL else ContentType.NOVEL

    override val name: String
        get() = "IREADER_${catalogueSource.id}"
}
