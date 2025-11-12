package org.skepsun.kototoro.settings.sources.catalog

import org.skepsun.kototoro.parsers.model.ContentType

data class SourcesCatalogFilter(
	val types: Set<ContentType>,
	val locale: String?,
	val isNewOnly: Boolean,
)
