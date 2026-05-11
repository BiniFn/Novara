package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.ExtractorLink

data class MainPageData(
	val name: String,
	val data: String,
	val horizontalImages: Boolean = false,
)

data class MainPageRequest(
	val name: String,
	val data: String,
	val horizontalImages: Boolean = false,
)

open class SearchResponse(
	open val name: String,
	open val url: String,
	open val apiName: String? = null,
	open val type: TvType? = null,
	open val posterUrl: String? = null,
	open val score: Score? = null,
)

class AnimeSearchResponse(
	override val name: String,
	override val url: String,
	override val apiName: String? = null,
	override val type: TvType? = null,
	override val posterUrl: String? = null,
	override val score: Score? = null,
	val otherName: String? = null,
) : SearchResponse(
	name = name,
	url = url,
	apiName = apiName,
	type = type,
	posterUrl = posterUrl,
	score = score,
)

data class SearchResponseList(
	val items: List<SearchResponse>,
	val hasNext: Boolean = false,
)

data class HomePageList(
	val name: String,
	val list: List<SearchResponse>,
)

data class HomePageResponse(
	val items: List<HomePageList>,
	val hasNext: Boolean = false,
)

data class Episode(
	val data: String,
	val name: String,
	val episode: Int? = null,
	val season: Int? = null,
	val date: Long? = null,
)

open class LoadResponse(
	open val name: String,
	open val url: String,
	open val score: Score? = null,
	open val contentRating: String? = null,
	open val posterUrl: String? = null,
	open val backgroundPosterUrl: String? = null,
	open val plot: String? = null,
	open val tags: List<String>? = null,
)

data class MovieLoadResponse(
	override val name: String,
	override val url: String,
	val dataUrl: String,
	override val score: Score? = null,
	override val contentRating: String? = null,
	override val posterUrl: String? = null,
	override val backgroundPosterUrl: String? = null,
	override val plot: String? = null,
	override val tags: List<String>? = null,
) : LoadResponse(
	name = name,
	url = url,
	score = score,
	contentRating = contentRating,
	posterUrl = posterUrl,
	backgroundPosterUrl = backgroundPosterUrl,
	plot = plot,
	tags = tags,
)

data class TvSeriesLoadResponse(
	override val name: String,
	override val url: String,
	val episodes: List<Episode>,
	override val score: Score? = null,
	override val contentRating: String? = null,
	override val posterUrl: String? = null,
	override val backgroundPosterUrl: String? = null,
	override val plot: String? = null,
	override val tags: List<String>? = null,
) : LoadResponse(
	name = name,
	url = url,
	score = score,
	contentRating = contentRating,
	posterUrl = posterUrl,
	backgroundPosterUrl = backgroundPosterUrl,
	plot = plot,
	tags = tags,
)

data class AnimeLoadResponse(
	override val name: String,
	override val url: String,
	val episodes: Map<DubStatus, List<Episode>>,
	override val score: Score? = null,
	override val contentRating: String? = null,
	override val posterUrl: String? = null,
	override val backgroundPosterUrl: String? = null,
	override val plot: String? = null,
	override val tags: List<String>? = null,
) : LoadResponse(
	name = name,
	url = url,
	score = score,
	contentRating = contentRating,
	posterUrl = posterUrl,
	backgroundPosterUrl = backgroundPosterUrl,
	plot = plot,
	tags = tags,
)

data class SubtitleFile(
	val url: String,
	val lang: String,
	val headers: Map<String, String>? = null,
)

@Suppress("unused")
data class ExtractorLoadResult(
	val links: List<ExtractorLink>,
	val subtitles: List<SubtitleFile> = emptyList(),
)
