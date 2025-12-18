package org.skepsun.kototoro.filter.ui.model

import org.skepsun.kototoro.parsers.model.MangaTag

data class UiTagGroup(
    val title: String,
    val tags: Set<MangaTag>,
    val selected: Set<MangaTag> = emptySet(),
)
