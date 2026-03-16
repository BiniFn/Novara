package org.skepsun.kototoro.core.jsonsource

import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.parsers.model.MangaSource

data class JsonMangaSource(
	val entity: JsonSourceEntity,
) : MangaSource {

	override val name: String
		get() = entity.id

	val displayName: String
		get() = entity.name
}
