package org.skepsun.kototoro.core.parser

import org.skepsun.kototoro.parsers.model.ContentSource

interface ContentRepositoryProvider {
	fun create(source: ContentSource): ContentRepository?
}
