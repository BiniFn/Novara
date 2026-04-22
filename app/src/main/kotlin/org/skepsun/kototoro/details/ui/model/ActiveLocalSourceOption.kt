package org.skepsun.kototoro.details.ui.model

import org.skepsun.kototoro.parsers.model.ContentSource

data class ActiveLocalSourceOption(
    val mangaId: Long,
    val title: String,
    val source: ContentSource,
    val isActive: Boolean,
)
