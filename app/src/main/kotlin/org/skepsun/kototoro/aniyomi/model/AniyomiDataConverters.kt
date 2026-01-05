package org.skepsun.kototoro.aniyomi.model

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import org.skepsun.kototoro.parsers.model.ContentRating
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.model.MangaState
import org.skepsun.kototoro.parsers.model.MangaTag
import org.skepsun.kototoro.parsers.model.RATING_UNKNOWN

/**
 * Extension functions for converting between Aniyomi and Kototoro data models.
 */

// ============ SAnime <-> Manga ============

fun SAnime.toKotoManga(
    source: AniyomiAnimeSource,
    chapters: List<MangaChapter>? = null,
    publicUrl: String = "",
): Manga {
    val baseUrl = (source.animeCatalogueSource as? AnimeHttpSource)?.baseUrl ?: ""
    
    val safeUrl = try { url } catch (e: Exception) { "" }
    val safeThumbnail = try { thumbnail_url } catch (e: Exception) { null }
    val absoluteThumbnailUrl = resolveUrl(baseUrl, safeThumbnail)
    val absolutePublicUrl = resolveUrl(baseUrl, safeUrl) ?: safeUrl
    
    val safeTitle = try { title } catch (e: Exception) { "Unknown" }
    val safeGenres = try { getGenres() } catch (e: Exception) { null }
    val safeAuthor = try { author } catch (e: Exception) { null }
    val safeArtist = try { artist } catch (e: Exception) { null }
    val safeDescription = try { description } catch (e: Exception) { null }
    val safeStatus = try { status } catch (e: Exception) { SAnime.UNKNOWN }
    
    return Manga(
        id = generateId(safeUrl, source.name, "manga"),
        title = safeTitle,
        altTitles = emptySet(),
        url = safeUrl,
        publicUrl = if (publicUrl.isNotBlank()) publicUrl else absolutePublicUrl,
        rating = RATING_UNKNOWN,
        contentRating = run {
            val adultGenres = setOf("adult", "hentai", "18+", "nsfw", "mature", "ecchi")
            val isNsfw = source.isNsfw || safeGenres?.any { it.lowercase() in adultGenres } == true
            if (isNsfw) ContentRating.ADULT else null
        },
        coverUrl = absoluteThumbnailUrl,
        largeCoverUrl = absoluteThumbnailUrl,
        tags = safeGenres?.map { genreName ->
            MangaTag(
                title = genreName,
                key = genreName.lowercase().replace(" ", "_"),
                source = source,
            )
        }?.toSet() ?: emptySet(),
        state = when (safeStatus) {
            SAnime.ONGOING -> MangaState.ONGOING
            SAnime.COMPLETED -> MangaState.FINISHED
            SAnime.ON_HIATUS -> MangaState.PAUSED
            SAnime.CANCELLED -> MangaState.ABANDONED
            SAnime.LICENSED -> MangaState.RESTRICTED
            SAnime.PUBLISHING_FINISHED -> MangaState.FINISHED
            else -> null
        },
        authors = buildSet {
            safeAuthor?.takeIf { it.isNotBlank() }?.let { add(it) }
            safeArtist?.takeIf { it.isNotBlank() && it != safeAuthor }?.let { add(it) }
        },
        description = safeDescription,
        chapters = chapters,
        source = source,
    )
}

fun Manga.toAniyomiAnime(): SAnime {
    val baseUrl = (source as? AniyomiAnimeSource)?.let { 
        (it.animeCatalogueSource as? AnimeHttpSource)?.baseUrl ?: ""
    } ?: ""
    
    var cleanUrl = url
    // Handle duplicate baseUrl and fix protocols as in MihonDataConverters
    cleanUrl = cleanUrl.replace(Regex("^(https?)/+"), "$1://")
    if (baseUrl.isNotBlank() && cleanUrl.startsWith(baseUrl.trimEnd('/'))) {
        cleanUrl = cleanUrl.substring(baseUrl.trimEnd('/').length)
    }
    
    return SAnime.create().apply {
        this.url = cleanUrl
        this.title = this@toAniyomiAnime.title
        this.author = this@toAniyomiAnime.authors.firstOrNull()
        this.artist = this@toAniyomiAnime.authors.drop(1).firstOrNull()
        this.description = this@toAniyomiAnime.description
        this.genre = this@toAniyomiAnime.tags.joinToString(", ") { it.title }
        this.status = when (this@toAniyomiAnime.state) {
            MangaState.ONGOING -> SAnime.ONGOING
            MangaState.FINISHED -> SAnime.COMPLETED
            MangaState.PAUSED -> SAnime.ON_HIATUS
            MangaState.ABANDONED -> SAnime.CANCELLED
            MangaState.RESTRICTED -> SAnime.LICENSED
            else -> SAnime.UNKNOWN
        }
        this.thumbnail_url = this@toAniyomiAnime.coverUrl
        this.initialized = true
    }
}

// ============ SEpisode <-> MangaChapter ============

fun SEpisode.toKotoChapter(source: MangaSource, overrideNumber: Float? = null): MangaChapter {
    val chapterNumber = overrideNumber ?: (if (episode_number >= 0) episode_number else 0f)
    return MangaChapter(
        id = generateId(url, source.name, "episode"),
        title = name.takeIf { it.isNotBlank() },
        number = chapterNumber,
        volume = 0,
        url = url,
        scanlator = scanlator,
        uploadDate = date_upload,
        branch = scanlator,
        source = source,
    )
}

fun MangaChapter.toAniyomiEpisode(): SEpisode {
    return SEpisode.create().apply {
        this.url = this@toAniyomiEpisode.url
        this.name = this@toAniyomiEpisode.title ?: "Episode ${this@toAniyomiEpisode.number}"
        this.episode_number = this@toAniyomiEpisode.number
        this.date_upload = this@toAniyomiEpisode.uploadDate
        this.scanlator = this@toAniyomiEpisode.scanlator
    }
}

// ============ Video <-> MangaPage ============

fun Video.toKotoPage(
    source: MangaSource,
    episode: SEpisode,
    index: Int
): MangaPage {
    val pageId = "${episode.url}|video|$index".hashCode().toLong() and Long.MAX_VALUE
    
    // Convert Headers to Map
    val headerMap = mutableMapOf<String, String>()
    headers?.let { h ->
        for (i in 0 until h.size) {
            headerMap[h.name(i)] = h.value(i)
        }
    }
    
    return MangaPage(
        id = pageId,
        url = videoUrl ?: "",
        preview = null,
        headers = headerMap,
        source = source,
    )
}

// ============ ID Generation ============

private fun generateId(url: String, sourceName: String, type: String): Long {
    return "$sourceName|$type|$url".hashCode().toLong() and Long.MAX_VALUE
}

// ============ URL Helpers ============

fun AnimeHttpSource.getPublicAnimeUrl(anime: SAnime): String {
    return try {
        getAnimeUrl(anime)
    } catch (e: Exception) {
        ""
    }
}

private fun resolveUrl(baseUrl: String, url: String?): String? {
    if (url.isNullOrBlank()) return null
    if (url.startsWith("http")) return url
    if (url.startsWith("//")) return "https:$url"
    if (baseUrl.isNotBlank()) {
        return baseUrl.trimEnd('/') + "/" + url.trimStart('/')
    }
    return url
}
