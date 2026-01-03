package org.skepsun.kototoro.mihon.model

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import org.skepsun.kototoro.parsers.model.ContentRating
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.model.MangaState
import org.skepsun.kototoro.parsers.model.MangaTag
import org.skepsun.kototoro.parsers.model.RATING_UNKNOWN

/**
 * Extension functions for converting between Mihon and Kototoro data models.
 */

// ============ SManga <-> Manga ============

/**
 * Convert Mihon SManga to Kototoro Manga.
 */
fun SManga.toKotoManga(
    source: MihonMangaSource,
    chapters: List<MangaChapter>? = null,
    publicUrl: String = "",
): Manga {
    // Get baseUrl from source if available to resolve relative URLs
    val baseUrl = (source.catalogueSource as? HttpSource)?.baseUrl ?: ""
    
    val safeUrl = try { url } catch (e: UninitializedPropertyAccessException) { "" }
    val safeThumbnail = try { thumbnail_url } catch (e: UninitializedPropertyAccessException) { null }
    val absoluteThumbnailUrl = resolveUrl(baseUrl, safeThumbnail)
    val absolutePublicUrl = resolveUrl(baseUrl, safeUrl) ?: safeUrl
    
    // Safely access lateinit properties
    val safeTitle = try { title } catch (e: UninitializedPropertyAccessException) { "Unknown" }
    val safeGenres = try { getGenres() } catch (e: UninitializedPropertyAccessException) { null }
    val safeAuthor = try { author } catch (e: UninitializedPropertyAccessException) { null }
    val safeArtist = try { artist } catch (e: UninitializedPropertyAccessException) { null }
    val safeDescription = try { description } catch (e: UninitializedPropertyAccessException) { null }
    val safeStatus = try { status } catch (e: UninitializedPropertyAccessException) { SManga.UNKNOWN }
    
    android.util.Log.i("MihonDataConverters", "toKotoManga: title='$safeTitle' url='$safeUrl' -> absoluteThumbnail='$absoluteThumbnailUrl'")
    
    return Manga(
        id = generateMangaId(safeUrl, source.name),
        title = safeTitle,
        altTitles = emptySet(),
        url = safeUrl,
        publicUrl = if (publicUrl.isNotBlank()) publicUrl else absolutePublicUrl,
        rating = RATING_UNKNOWN,
        contentRating = run {
            val adultGenres = setOf("adult", "hentai", "18+", "nsfw", "mature", "ecchi")
            val isMangaNsfw = source.isNsfw || safeGenres?.any { it.lowercase() in adultGenres } == true
            if (isMangaNsfw) ContentRating.ADULT else null
        },
        coverUrl = absoluteThumbnailUrl,
        largeCoverUrl = absoluteThumbnailUrl, // Also set largeCoverUrl for details page
        tags = safeGenres?.map { genreName: String ->
            MangaTag(
                title = genreName,
                key = genreName.lowercase().replace(" ", "_"),
                source = source,
            )
        }?.toSet() ?: emptySet(),
        state = when (safeStatus) {
            SManga.ONGOING -> MangaState.ONGOING
            SManga.COMPLETED -> MangaState.FINISHED
            SManga.ON_HIATUS -> MangaState.PAUSED
            SManga.CANCELLED -> MangaState.ABANDONED
            SManga.LICENSED -> MangaState.RESTRICTED  // Map LICENSED to RESTRICTED
            SManga.PUBLISHING_FINISHED -> MangaState.FINISHED
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

/**
 * Convert Kototoro Manga to Mihon SManga (for calling Mihon APIs).
 */
fun Manga.toMihonManga(): SManga {
    // Get baseUrl from source if available
    val baseUrl = (source as? MihonMangaSource)?.let { mihonSource ->
        (mihonSource.catalogueSource as? HttpSource)?.baseUrl ?: ""
    } ?: ""
    
    var cleanUrl = url
    
    // Check if URL has duplicate protocol/baseUrl (e.g., "https://domain.comhttps//domain.com/path")
    // Look for embedded "http" that's not at the start
    val httpIndex = cleanUrl.indexOf("http", startIndex = 1)
    if (httpIndex > 0) {
        // Extract everything from the second "http" onwards
        cleanUrl = cleanUrl.substring(httpIndex)
        android.util.Log.w("MihonDataConverters", "Detected duplicate baseUrl, extracting: '$url' -> '$cleanUrl'")
    }
    
    // Fix malformed protocols (https// -> https://)
    cleanUrl = cleanUrl.replace(Regex("^(https?)/+"), "$1://")
    
    // If URL is absolute and starts with baseUrl, strip it to avoid duplicates in HttpSource
    if (baseUrl.isNotBlank()) {
        val baseHost = baseUrl.trimEnd('/')
        if (cleanUrl.startsWith(baseHost)) {
            val stripped = cleanUrl.substring(baseHost.length)
            if (stripped.startsWith("/") || stripped.isEmpty()) {
                cleanUrl = stripped
                android.util.Log.d("MihonDataConverters", "Stripped baseUrl from absolute URL: '$url' -> '$cleanUrl'")
            }
        }
    }
    
    // Ensure relative URL starts with / if it's not empty and not absolute
    if (cleanUrl.isNotEmpty() && !cleanUrl.startsWith("/") && !cleanUrl.startsWith("http")) {
        cleanUrl = "/$cleanUrl"
    }
    
    // If URL still doesn't look absolute, log warning
    if (!cleanUrl.matches(Regex("^https?://.*")) && !cleanUrl.startsWith("/")) {
        android.util.Log.w("MihonDataConverters", "URL may be invalid after cleanup: '$cleanUrl' (original: '$url')")
    }
    
    android.util.Log.d("MihonDataConverters", "toMihonManga: original='$url' cleaned='$cleanUrl'")
    
    return SManga.create().apply {
        this.url = cleanUrl
        this.title = this@toMihonManga.title
        this.author = this@toMihonManga.authors.firstOrNull()
        this.artist = this@toMihonManga.authors.drop(1).firstOrNull()
        this.description = this@toMihonManga.description
        this.genre = this@toMihonManga.tags.joinToString(", ") { it.title }
        this.status = when (this@toMihonManga.state) {
            MangaState.ONGOING -> SManga.ONGOING
            MangaState.FINISHED -> SManga.COMPLETED
            MangaState.PAUSED -> SManga.ON_HIATUS
            MangaState.ABANDONED -> SManga.CANCELLED
            MangaState.RESTRICTED -> SManga.LICENSED  // Map RESTRICTED to LICENSED
            else -> SManga.UNKNOWN
        }
        this.thumbnail_url = this@toMihonManga.coverUrl
        this.initialized = true
    }
}

// ============ SChapter <-> MangaChapter ============

/**
 * Convert Mihon SChapter to Kototoro MangaChapter.
 */
fun SChapter.toKotoChapter(source: MangaSource, overrideNumber: Float? = null): MangaChapter {
    val chapterId = generateChapterId(url, source.name)
    val finalNumber = overrideNumber ?: (if (chapter_number >= 0) chapter_number else 0f)
    
    android.util.Log.d("MihonDataConverters", "toKotoChapter: name='$name' url='$url' -> id=$chapterId number=$finalNumber")
    
    return MangaChapter(
        id = chapterId,
        title = name.takeIf { it.isNotBlank() },
        number = finalNumber,
        volume = 0, // Mihon doesn't have volume numbers in SChapter
        url = url,
        scanlator = scanlator,
        uploadDate = date_upload,
        branch = scanlator, // Use scanlator as branch for grouping
        source = source,
    )
}

/**
 * Convert Kototoro MangaChapter to Mihon SChapter.
 */
fun MangaChapter.toMihonChapter(): SChapter {
    return SChapter.create().apply {
        this.url = this@toMihonChapter.url
        this.name = this@toMihonChapter.title ?: "Chapter ${this@toMihonChapter.number}"
        this.chapter_number = this@toMihonChapter.number
        this.date_upload = this@toMihonChapter.uploadDate
        this.scanlator = this@toMihonChapter.scanlator
    }
}

// ============ Page <-> MangaPage ============

/**
 * Convert Mihon Page to Kototoro MangaPage.
 * 
 * NOTE: The chapter parameter is needed to generate unique page IDs.
 * Without it, all chapters would have pages with IDs 0, 1, 2... which causes
 * cache conflicts in the reader.
 */
fun Page.toKotoPage(
    source: MangaSource, 
    chapter: eu.kanade.tachiyomi.source.model.SChapter,
    headers: Map<String, String> = emptyMap()
): MangaPage {
    // Generate a unique page ID by combining chapter URL and page index
    // This prevents cache collisions between pages from different chapters
    val pageId = "${chapter.url}|page|$index".hashCode().toLong() and Long.MAX_VALUE
    
    return MangaPage(
        id = pageId,
        url = imageUrl ?: url,
        preview = null,
        headers = headers,
        source = source,
    )
}

/**
 * Convert Kototoro MangaPage to Mihon Page.
 */
fun MangaPage.toMihonPage(): Page {
    return Page(
        index = id.toInt(),
        url = url,
        imageUrl = url.takeIf { it.isNotBlank() },
    )
}

// ============ ID Generation ============

/**
 * Generate a stable ID for a manga based on URL and source.
 */
private fun generateMangaId(url: String, sourceName: String): Long {
    return "$sourceName|manga|$url".hashCode().toLong() and Long.MAX_VALUE
}

/**
 * Generate a stable ID for a chapter based on URL and source.
 */
private fun generateChapterId(url: String, sourceName: String): Long {
    return "$sourceName|chapter|$url".hashCode().toLong() and Long.MAX_VALUE
}

// ============ URL Helpers ============

/**
 * Get the public URL for a manga from an HttpSource.
 */
fun HttpSource.getPublicMangaUrl(manga: SManga): String {
    return try {
        getMangaUrl(manga)
    } catch (e: Exception) {
        ""
    }
}

/**
 * Get the public URL for a chapter from an HttpSource.
 */
fun HttpSource.getPublicChapterUrl(chapter: SChapter): String {
    return try {
        getChapterUrl(chapter)
    } catch (e: Exception) {
        ""
    }
}
/**
 * Resolve relative URL using baseUrl.
 */
private fun resolveUrl(baseUrl: String, url: String?): String? {
    if (url.isNullOrBlank()) return null
    if (url.startsWith("http")) return url
    if (url.startsWith("//")) return "https:$url"
    
    if (baseUrl.isNotBlank()) {
        return baseUrl.trimEnd('/') + "/" + url.trimStart('/')
    }
    return url
}
