package org.skepsun.kototoro.core.ui.model

import org.skepsun.kototoro.parsers.model.ContentRating

data class MangaOverride(
	val coverUrl: String?,
	val title: String?,
	val contentRating: ContentRating?,
)
