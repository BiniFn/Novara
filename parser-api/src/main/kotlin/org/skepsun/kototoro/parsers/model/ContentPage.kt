package org.skepsun.kototoro.parsers.model

import org.skepsun.kototoro.parsers.ContentParser

public data class ContentPage(
	/**
	 * Unique identifier for page
	 */
	@JvmField public val id: Long,
	/**
	 * Relative url to page (**without** a domain) or any other uri.
	 * Used principally in parsers.
	 * May contain link to image or html page.
	 * @see ContentParser.getPageUrl
	 */
	@JvmField public val url: String,
	/**
	 * Absolute url of the small page image if exists, null otherwise
	 */
	@JvmField public val preview: String?,
	/**
	 * Optional per-page request headers (e.g., Referer) to be applied when fetching the page/image.
	 */
	@JvmField public val headers: Map<String, String>? = null,
	/**
	 * Optional external subtitle tracks associated with this page/media item.
	 */
	@JvmField public val externalSubtitleTracks: List<ContentExternalTrack> = emptyList(),
	/**
	 * Optional human-readable playback label for video sources, e.g. line/group name.
	 */
	@JvmField public val playbackLabel: String? = null,
	/**
	 * Optional nominal video quality/resolution for video sources.
	 */
	@JvmField public val playbackQuality: Int? = null,
	@JvmField public val source: ContentSource,
)

public data class ContentExternalTrack(
	@JvmField public val url: String,
	@JvmField public val lang: String,
	@JvmField public val headers: Map<String, String>? = null,
)

@Deprecated("Use id instead of index", ReplaceWith("ContentPage(index.toLong(), url, previewUrl, source)"))
public fun ContentPage(index: Int, url: String, previewUrl: String?, source: ContentSource): ContentPage = ContentPage(
	id = index.toLong(),
	url = url,
	preview = previewUrl,
	source = source,
)
