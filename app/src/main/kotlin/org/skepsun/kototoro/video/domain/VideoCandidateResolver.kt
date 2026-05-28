package org.skepsun.kototoro.video.domain

import okhttp3.Headers
import org.skepsun.kototoro.aniyomi.AniyomiAnimeRepository
import org.skepsun.kototoro.core.parser.ContentRepository
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.parsers.util.runCatchingCancellable

data class VideoCandidate(
    val url: String,
    val title: String,
    val resolution: Int?,
    val headers: Map<String, String>?,
    val subtitleTracks: List<eu.kanade.tachiyomi.animesource.model.Track> = emptyList(),
    val audioTracks: List<eu.kanade.tachiyomi.animesource.model.Track> = emptyList(),
)

suspend fun ContentRepository.resolveVideoCandidates(chapter: ContentChapter): List<VideoCandidate> {
    val aniyomiRepo = this as? AniyomiAnimeRepository
    if (aniyomiRepo != null) {
        return aniyomiRepo.getVideoListForChapter(chapter)
            .filter { it.videoUrl.isNotBlank() }
            .map { video ->
                VideoCandidate(
                    url = video.videoUrl,
                    title = video.videoTitle,
                    resolution = video.resolution,
                    headers = video.headers
                        ?.toMultimap()
                        ?.mapValues { entry -> entry.value.firstOrNull().orEmpty() }
                        ?.filterValues { it.isNotBlank() },
                    subtitleTracks = video.subtitleTracks,
                    audioTracks = video.audioTracks,
                )
            }
    }
    val pages = getPages(chapter, nextChapterUrl = null)
    return pages.toFallbackVideoCandidates(this)
}

private suspend fun List<ContentPage>.toFallbackVideoCandidates(repo: ContentRepository): List<VideoCandidate> {
    return mapNotNull { page ->
        val streamUrl = runCatchingCancellable { repo.getPageUrl(page) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        VideoCandidate(
            url = streamUrl,
            title = buildFallbackTitle(page),
            resolution = page.playbackQuality,
            headers = page.headers?.takeIf { it.isNotEmpty() },
            subtitleTracks = page.externalSubtitleTracks.map {
                eu.kanade.tachiyomi.animesource.model.Track(it.url, it.lang)
            },
        )
    }
}

private fun buildFallbackTitle(page: ContentPage): String {
    val qualityLabel = page.playbackQuality?.takeIf { it > 0 }?.let { "${it}p" }
    val label = page.playbackLabel?.trim().orEmpty()
    return when {
        !qualityLabel.isNullOrBlank() && label.isNotBlank() -> "$qualityLabel · $label"
        !qualityLabel.isNullOrBlank() -> qualityLabel
        label.isNotBlank() -> label
        else -> ""
    }
}

fun VideoCandidate.toOkHttpHeaders(): Headers? {
    val headerMap = headers?.takeIf { it.isNotEmpty() } ?: return null
    return Headers.headersOf(*headerMap.flatMap { listOf(it.key, it.value) }.toTypedArray())
}
