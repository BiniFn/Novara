package org.skepsun.kototoro.details.ui.model

import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService

data class DetailsSourceOption(
    val key: String,
    val source: ContentSource? = null,
    val trackingService: ScrobblerService? = null,
    val targetMangaId: Long? = null,
    val remoteId: Long? = null,
    val url: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val coverUrl: String? = null,
    val isSelected: Boolean = false,
)
