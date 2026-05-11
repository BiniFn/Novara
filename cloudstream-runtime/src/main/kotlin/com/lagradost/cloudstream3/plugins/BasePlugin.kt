package com.lagradost.cloudstream3.plugins

open class BasePlugin {
	var filename: String? = null

	open fun load() = Unit

	open fun beforeUnload() = Unit
}
