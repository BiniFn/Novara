package org.skepsun.kototoro.favourites.domain.model

import org.skepsun.kototoro.core.model.MangaSource

data class Cover(
	val url: String?,
	val source: String,
) {
	val mangaSource by lazy { MangaSource(source) }
}
