package org.skepsun.kototoro.cloudstream.runtime

import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.parsers.model.ContentSource

data class CloudstreamPlaybackPage(
	val id: Long,
	val url: String,
	val headers: Map<String, String>? = null,
	val subtitleTracks: List<CloudstreamPlaybackSubtitle> = emptyList(),
	val playbackLabel: String? = null,
	val playbackQuality: Int? = null,
) {
	fun toContentPage(source: ContentSource): ContentPage = ContentPage(
		id = id,
		url = url,
		preview = null,
		headers = headers,
		source = source,
	)
}

data class CloudstreamPlaybackSubtitle(
	val url: String,
	val lang: String,
	val headers: Map<String, String>? = null,
)
