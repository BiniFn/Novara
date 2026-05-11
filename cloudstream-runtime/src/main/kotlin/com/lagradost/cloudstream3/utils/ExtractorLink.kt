package com.lagradost.cloudstream3.utils

data class ExtractorLink(
	val source: String,
	val name: String,
	val url: String,
	val referer: String? = null,
	val quality: Int = 0,
	val type: String? = null,
	private val headers: Map<String, String> = emptyMap(),
) {
	fun getAllHeaders(): Map<String, String> {
		return buildMap {
			putAll(headers)
			if (!referer.isNullOrBlank()) {
				putIfAbsent("Referer", referer)
			}
		}
	}
}
