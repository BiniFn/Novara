package org.skepsun.kototoro.tracker.domain.model

import org.skepsun.kototoro.parsers.model.Content
import java.time.Instant

data class TrackingLogItem(
	val id: Long,
	val manga: Content,
	val chapters: List<String>,
	val createdAt: Instant,
	val isNew: Boolean,
)
