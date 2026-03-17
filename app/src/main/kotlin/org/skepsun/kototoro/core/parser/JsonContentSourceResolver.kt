package org.skepsun.kototoro.core.parser

import kotlinx.coroutines.runBlocking
import org.skepsun.kototoro.core.jsonsource.JsonContentSource
import org.skepsun.kototoro.core.jsonsource.JsonSourceManager
import org.skepsun.kototoro.parsers.model.ContentSource
import javax.inject.Inject

class JsonContentSourceResolver @Inject constructor(
	private val jsonSourceManager: JsonSourceManager,
) : ContentSourceResolver {

	override fun resolve(source: ContentSource): ContentSource? {
		if (source is JsonContentSource || !source.name.startsWith(JSON_PREFIX)) {
			return null
		}
		return runBlocking {
			jsonSourceManager.getById(source.name)
		}?.let(::JsonContentSource)
	}

	private companion object {
		private const val JSON_PREFIX = "JSON_"
	}
}
