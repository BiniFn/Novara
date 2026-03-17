package org.skepsun.kototoro.settings.userdata.storage

data class StorageUsage(
	val savedContent: Item,
	val pagesCache: Item,
	val otherCache: Item,
	val available: Item,
) {
	data class Item(
		val bytes: Long,
		val percent: Float,
	)
}
