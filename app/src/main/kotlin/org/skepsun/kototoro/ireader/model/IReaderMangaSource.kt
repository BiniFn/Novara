package org.skepsun.kototoro.ireader.model

import ireader.core.source.Source
import org.skepsun.kototoro.parsers.model.ContentSource

data class IReaderMangaSource(
    val pkgName: String,
    val catalogueSource: Source,
    val isNsfw: Boolean,
    val language: String,
    val displayName: String = catalogueSource.name,
) : ContentSource {
    override val name: String
        get() = "IREADER_${pkgName}_${catalogueSource.id}"
}
