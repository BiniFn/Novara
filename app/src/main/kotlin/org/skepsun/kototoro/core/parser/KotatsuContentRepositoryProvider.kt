package org.skepsun.kototoro.core.parser

import org.skepsun.kototoro.core.cache.MemoryContentCache
import org.skepsun.kototoro.core.parser.kotatsu.KotatsuParserRepository
import org.skepsun.kototoro.core.parser.kotatsu.KotatsuParsersProvider
import org.skepsun.kototoro.core.parser.kotatsu.KotatsuParserSource
import org.skepsun.kototoro.parsers.ContentLoaderContext
import org.skepsun.kototoro.parsers.model.ContentSource
import javax.inject.Inject

class KotatsuContentRepositoryProvider @Inject constructor(
	private val loaderContext: ContentLoaderContext,
	private val contentCache: MemoryContentCache,
) : ContentRepositoryProvider {

	override fun create(source: ContentSource): ContentRepository? {
		if (source !is KotatsuParserSource) return null
		return KotatsuParserRepository(
			parser = KotatsuParsersProvider.newParserInstance(loaderContext, source),
			kotatsuSource = source,
			loaderContext = loaderContext,
			cache = contentCache,
		)
	}
}
