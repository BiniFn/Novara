package org.skepsun.kototoro.core.db.entity

data class JsonSourceEntity(
	val id: String,
	val name: String,
	val type: JsonSourceType,
	val config: String,
	val enabled: Boolean = true,
	val createdAt: Long,
	val updatedAt: Long,
	val lastUsedAt: Long = 0,
	val isPinned: Boolean = false,
)

enum class JsonSourceType {
	LEGADO,
	TVBOX,
	JS,
}
