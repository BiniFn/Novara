package org.skepsun.kototoro.filter.ui.model

import org.skepsun.kototoro.parsers.model.ContentTag

data class UiTagGroup(
    val title: String,
    val tags: Set<ContentTag>,
    val selected: Set<ContentTag> = emptySet(),
    val isExclusive: Boolean = false,
)
