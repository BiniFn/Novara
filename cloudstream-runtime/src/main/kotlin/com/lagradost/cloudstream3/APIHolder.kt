package com.lagradost.cloudstream3

object APIHolder {
	@Volatile
	var apis: List<MainAPI> = emptyList()

	val allProviders: MutableList<MainAPI> = mutableListOf()
}
