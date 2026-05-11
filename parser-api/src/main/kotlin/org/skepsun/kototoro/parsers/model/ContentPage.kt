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
	@JvmField public val source: ContentSource,
) {

	public constructor(
		id: Long,
		url: String,
		preview: String?,
		source: ContentSource,
	) : this(
		id = id,
		url = url,
		preview = preview,
		headers = null,
		source = source,
	)

	/**
	 * Source compatibility constructor for legacy call sites that still pass video-specific
	 * playback metadata through parser-api. Those values are now ignored.
	 */
	@Deprecated(
		message = "Use app-internal playback models instead of ContentPage video metadata.",
		level = DeprecationLevel.WARNING,
	)
	@Suppress("UNUSED_PARAMETER")
	public constructor(
		id: Long,
		url: String,
		preview: String?,
		headers: Map<String, String>?,
		externalSubtitleTracks: List<ContentExternalTrack>,
		playbackLabel: String?,
		playbackQuality: Int?,
		source: ContentSource,
	) : this(
		id = id,
		url = url,
		preview = preview,
		headers = headers,
		source = source,
	)

}

/**
 * Compatibility type kept only so old source/plugin code can still link.
 * New code should use app-internal playback models instead.
 */
@Deprecated(
	message = "Use app-internal playback models instead of ContentPage video metadata.",
	level = DeprecationLevel.WARNING,
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
