package org.skepsun.kototoro.core.parser.kotatsu

import org.koitharu.kotatsu.parsers.model.MangaParserSource as KTMangaParserSource
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.ContentSource

/**
 * Kotatsu 解析器源的包装，暴露为 Kototoro 的 ContentSource。
 */
data class KotatsuParserSource(
	val delegate: KTMangaParserSource,
) : ContentSource {
	override val name: String = delegate.name
	val title: String = delegate.title
	val locale: String = delegate.locale
	val contentType: ContentType = delegate.contentType.toKototoro()
	val isBroken: Boolean = delegate.isBroken
}
