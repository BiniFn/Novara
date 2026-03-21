package org.skepsun.kototoro.scrobbling.common.domain.model

import org.skepsun.kototoro.list.ui.model.ListModel

data class ScrobblerContent(
	val id: Long,
	val name: String,
	val altName: String?,
	val cover: String?,
	val url: String,
	val isBestMatch: Boolean = false,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is ScrobblerContent && other.id == id
	}

	override fun toString(): String {
		return "ScrobblerContent #$id \"$name\" $url"
	}
}
