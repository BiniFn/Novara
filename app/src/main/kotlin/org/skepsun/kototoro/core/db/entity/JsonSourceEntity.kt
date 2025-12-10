package org.skepsun.kototoro.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.skepsun.kototoro.core.db.TABLE_JSON_SOURCES

@Entity(
	tableName = TABLE_JSON_SOURCES,
)
data class JsonSourceEntity(
	@PrimaryKey(autoGenerate = false)
	@ColumnInfo(name = "id")
	val id: String,
	@ColumnInfo(name = "name")
	val name: String,
	@ColumnInfo(name = "type")
	val type: JsonSourceType,
	@ColumnInfo(name = "config")
	val config: String,
	@ColumnInfo(name = "enabled")
	val enabled: Boolean = true,
	@ColumnInfo(name = "created_at")
	val createdAt: Long,
	@ColumnInfo(name = "updated_at")
	val updatedAt: Long,
	@ColumnInfo(name = "last_used_at")
	val lastUsedAt: Long = 0,
	@ColumnInfo(name = "is_pinned")
	val isPinned: Boolean = false,
)

enum class JsonSourceType {
	LEGADO,
	TVBOX
}
