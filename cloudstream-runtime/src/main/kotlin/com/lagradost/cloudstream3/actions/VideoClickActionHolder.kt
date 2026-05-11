package com.lagradost.cloudstream3.actions

fun interface VideoClickAction {
	fun invoke()
}

object VideoClickActionHolder {
	@JvmStatic
	val INSTANCE: VideoClickActionHolder = this

	@JvmStatic
	val allVideoClickActions: MutableList<VideoClickAction> = mutableListOf()
}
