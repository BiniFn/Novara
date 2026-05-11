package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.ExtractorLink

@OptIn(Prerelease::class)
open class MainAPI {
	open var name: String = ""
	open var lang: String = ""
	open var supportedTypes: Set<TvType> = setOf(TvType.Movie)
	open var sourcePlugin: String? = null
	open val mainPage: List<MainPageData> = emptyList()
	open val hasMainPage: Boolean
		get() = mainPage.isNotEmpty()

	open suspend fun search(query: String, page: Int): SearchResponseList? = null

	open suspend fun load(url: String): LoadResponse? = null

	open suspend fun loadLinks(
		data: String,
		isCasting: Boolean,
		subtitleCallback: (SubtitleFile) -> Unit,
		callback: (ExtractorLink) -> Unit,
	): Boolean = false

	open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
}
