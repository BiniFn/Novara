package org.skepsun.kototoro.scrobbling.common.domain.model

data class ScrobblerUserProfile(
    val user: ScrobblerUser,
    val stats: ScrobblerUserStats? = null,
)

data class ScrobblerUserStats(
    val animeCount: Int? = null,
    val mangaCount: Int? = null,
    val tvCount: Int? = null,
    val movieCount: Int? = null,
    val episodesWatched: Int? = null,
    val chaptersRead: Int? = null,
    val tvEpisodesWatched: Int? = null,
    val animeMeanScore: Double? = null,
    val mangaMeanScore: Double? = null,
)
