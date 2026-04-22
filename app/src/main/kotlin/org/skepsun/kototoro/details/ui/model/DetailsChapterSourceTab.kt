package org.skepsun.kototoro.details.ui.model

import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService

data class DetailsChapterSourceTab(
    val key: String,
    val source: ContentSource? = null,
    val trackingService: ScrobblerService? = null,
    val targetMangaId: Long? = null,
    val remoteId: Long? = null,
    val url: String? = null,
    val chapters: List<ContentChapter> = emptyList(),
    val isSelected: Boolean = false,
)
