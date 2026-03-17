package org.skepsun.kototoro.core.parser

import org.skepsun.kototoro.core.cache.MemoryContentCache
import org.skepsun.kototoro.parsers.ContentLoaderContext
import org.skepsun.kototoro.parsers.model.ContentParserSource
import org.skepsun.kototoro.parsers.model.ContentSource
import javax.inject.Inject

class ParserContentRepositoryProvider @Inject constructor(
	private val loaderContext: ContentLoaderContext,
	private val contentCache: MemoryContentCache,
	private val mirrorSwitcher: MirrorSwitcher,
) : ContentRepositoryProvider {

	override fun create(source: ContentSource): ContentRepository? {
		if (source !is ContentParserSource) return null
		return ParserContentRepository(
			parser = loaderContext.newParserInstance(source),
			cache = contentCache,
			mirrorSwitcher = mirrorSwitcher,
		)
	}
}
