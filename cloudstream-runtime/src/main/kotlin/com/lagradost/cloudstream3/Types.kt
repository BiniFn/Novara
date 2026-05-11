package com.lagradost.cloudstream3

@OptIn(Prerelease::class)
enum class TvType {
	Movie,
	AnimeMovie,
	TvSeries,
	Cartoon,
	Anime,
	OVA,
	Torrent,
	Documentary,
	AsianDrama,
	Live,
	NSFW,
	Others,
	AudioBook,
	CustomMedia,
	Audio,
	Podcast,
	Video,
	Music,
}

enum class DubStatus {
	None,
	Subbed,
	Dubbed,
}

data class Score(
	private val normalizedValue: Int,
) {
	fun toInt(scale: Int): Int = when {
		scale <= 0 -> normalizedValue
		normalizedValue in 0..scale -> normalizedValue
		normalizedValue in 0..100 -> (normalizedValue * scale) / 100
		else -> normalizedValue.coerceAtMost(scale)
	}
}

fun TvType.isMovieType(): Boolean = when (this) {
	TvType.Movie,
	TvType.AnimeMovie -> true
	else -> false
}
