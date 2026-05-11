package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.SubtitleFile

open class ExtractorApi {
	open val name: String = ""
	open val mainUrl: String = ""
	open val requiresReferer: Boolean = false
	open var sourcePlugin: String? = null

	open suspend fun getUrl(
		url: String,
		referer: String?,
		subtitleCallback: (SubtitleFile) -> Unit,
		callback: (ExtractorLink) -> Unit,
	) = Unit
}
