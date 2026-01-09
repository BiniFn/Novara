package org.skepsun.kototoro.core.parser.kotatsu

import org.koitharu.kotatsu.parsers.MangaLoaderContext as KTMangaLoaderContext
import org.koitharu.kotatsu.parsers.model.MangaParserSource as KTMangaParserSource
import org.skepsun.kototoro.parsers.MangaLoaderContext
import org.skepsun.kototoro.parsers.model.MangaParserSource

/**
 * 提供 kotatsu 源列表与解析器实例化。
 */
object KotatsuParsersProvider {

	private val delegateSources: List<KTMangaParserSource> by lazy {
		runCatching { KTMangaParserSource::class.java.enumConstants?.toList().orEmpty() }
			.getOrDefault(emptyList())
	}

	val sources: List<KotatsuParserSource> by lazy {
		val nativeNames = MangaParserSource.entries.map { it.name }.toSet()
		delegateSources
			.asSequence()
			.filterNot { it.isBroken }
			.filterNot { it.name in nativeNames }
			.map { KotatsuParserSource(it) }
			.toList()
	}

	fun findByName(name: String): KotatsuParserSource? = sources.find { it.name == name }

	fun newParserInstance(
		context: MangaLoaderContext,
		source: KotatsuParserSource,
	): org.koitharu.kotatsu.parsers.MangaParser {
		val adapterContext: KTMangaLoaderContext = KotatsuLoaderContextAdapter(context)
		return adapterContext.newParserInstance(source.delegate)
	}
}
