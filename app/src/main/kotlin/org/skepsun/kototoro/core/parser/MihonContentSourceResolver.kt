package org.skepsun.kototoro.core.parser

import org.skepsun.kototoro.mihon.MihonExtensionManager
import org.skepsun.kototoro.mihon.model.MihonMangaSource
import org.skepsun.kototoro.parsers.model.ContentSource
import javax.inject.Inject

class MihonContentSourceResolver @Inject constructor(
	private val mihonExtensionManager: MihonExtensionManager,
) : ContentSourceResolver {

	override fun resolve(source: ContentSource): ContentSource? {
		if (source is MihonMangaSource || !source.name.startsWith(MIHON_PREFIX)) {
			return null
		}
		android.util.Log.d("MihonResolver", "Resolving source: ${source.name}")
		val resolved = mihonExtensionManager.getMihonMangaSourceByName(source.name)
		android.util.Log.d("MihonResolver", "Resolved result: $resolved")
		return resolved
	}

	private companion object {
		private const val MIHON_PREFIX = "MIHON_"
	}
}
