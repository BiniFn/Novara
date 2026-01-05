package org.skepsun.kototoro.aniyomi

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.core.cache.MemoryContentCache
import org.skepsun.kototoro.core.parser.CachingMangaRepository
import org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource
import org.skepsun.kototoro.aniyomi.model.getPublicAnimeUrl
import org.skepsun.kototoro.aniyomi.model.toAniyomiAnime
import org.skepsun.kototoro.aniyomi.model.toAniyomiEpisode
import org.skepsun.kototoro.aniyomi.model.toKotoChapter
import org.skepsun.kototoro.aniyomi.model.toKotoManga
import org.skepsun.kototoro.aniyomi.model.toKotoPage
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaListFilter
import org.skepsun.kototoro.parsers.model.MangaListFilterCapabilities
import org.skepsun.kototoro.parsers.model.MangaListFilterOptions
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.SortOrder

/**
 * Repository that adapts an Aniyomi AnimeCatalogueSource to Kototoro's MangaRepository interface.
 */
class AniyomiAnimeRepository(
    override val source: AniyomiAnimeSource,
    cache: MemoryContentCache,
) : CachingMangaRepository(cache) {
    
    private var lastOffset = -1
    private var currentPage = 1
    
    val aniyomiSource = source.animeCatalogueSource
    
    override val sortOrders: Set<SortOrder> = buildSet {
        add(SortOrder.POPULARITY)
        if (aniyomiSource.supportsLatest) {
            add(SortOrder.UPDATED)
        }
    }
    
    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isSearchSupported = true,
            isMultipleTagsSupported = true,
            isSearchWithFiltersSupported = true,
        )
    
    override var defaultSortOrder: SortOrder = SortOrder.POPULARITY
    
    override suspend fun getList(
        offset: Int,
        order: SortOrder?,
        filter: MangaListFilter?,
    ): List<Manga> = withContext(Dispatchers.IO) {
        if (offset == 0) {
            currentPage = 1
        } else if (offset > lastOffset) {
            currentPage++
        }
        lastOffset = offset
        
        val page = currentPage
        val query = filter?.query
        
        val hasFilters = filter?.let { 
            it.query?.isNotBlank() == true || it.tags.isNotEmpty() || it.tagsExclude.isNotEmpty()
        } ?: false
        
        val animesPage = when {
            hasFilters -> {
                aniyomiSource.getSearchAnime(page, query ?: "", filter?.toAniyomiFilterList() ?: AnimeFilterList())
            }
            order == SortOrder.UPDATED && aniyomiSource.supportsLatest -> {
                aniyomiSource.getLatestUpdates(page)
            }
            else -> {
                aniyomiSource.getPopularAnime(page)
            }
        }
        
        animesPage.animes.map { sAnime ->
            sAnime.toKotoManga(
                source = source,
                publicUrl = (aniyomiSource as? AnimeHttpSource)?.getPublicAnimeUrl(sAnime) ?: "",
            )
        }
    }
    
    override suspend fun getDetailsImpl(manga: Manga): Manga = withContext(Dispatchers.IO) {
        val sAnime = manga.toAniyomiAnime()
        
        val details = try {
            aniyomiSource.getAnimeDetails(sAnime)
        } catch (e: Exception) {
            val ioException = when {
                e is java.io.IOException -> e
                e.cause is java.io.IOException -> e.cause as java.io.IOException
                else -> null
            }
            if (ioException != null) {
                kotlinx.coroutines.delay(500)
                aniyomiSource.getAnimeDetails(sAnime)
            } else {
                throw e
            }
        }

        val rawEpisodes = try {
            aniyomiSource.getEpisodeList(sAnime)
        } catch (e: Exception) {
            val ioException = when {
                e is java.io.IOException -> e
                e.cause is java.io.IOException -> e.cause as java.io.IOException
                else -> null
            }
            if (ioException != null) {
                kotlinx.coroutines.delay(500)
                aniyomiSource.getEpisodeList(sAnime)
            } else {
                throw e
            }
        }
        
        // Reverse and assign numbers if missing, like in MihonMangaRepository
        val chapters = rawEpisodes.asReversed()
            .mapIndexed { index, sEpisode ->
                val episodeNumber = if (sEpisode.episode_number > 0) {
                    sEpisode.episode_number
                } else {
                    (index + 1).toFloat()
                }
                sEpisode.toKotoChapter(source, episodeNumber)
            }
            .sortedBy { it.number }
        
        details.url = sAnime.url
        
        // Title fallback
        val detailsTitle = try { details.title } catch (e: Exception) { "" }
        if (detailsTitle.isNullOrBlank()) {
            details.title = sAnime.title
        }
        
        // Thumbnail fallback
        val detailsThumb = try { details.thumbnail_url } catch (e: Exception) { null }
        if (detailsThumb.isNullOrBlank() || detailsThumb == details.url) {
            if (!sAnime.thumbnail_url.isNullOrBlank()) {
                details.thumbnail_url = sAnime.thumbnail_url
            }
        }
        
        val publicUrl = (aniyomiSource as? AnimeHttpSource)?.getPublicAnimeUrl(details) ?: ""
        
        details.toKotoManga(
            source = source,
            chapters = chapters,
            publicUrl = publicUrl,
        ).copy(id = manga.id)
    }
    
    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        android.util.Log.d("AniyomiRepo", "getPagesImpl called for chapter: ${chapter.title} (${chapter.url})")
        val sEpisode = chapter.toAniyomiEpisode()
        val videos = try {
            android.util.Log.d("AniyomiRepo", "Calling getVideoList...")
            val result = aniyomiSource.getVideoList(sEpisode)
            android.util.Log.d("AniyomiRepo", "getVideoList returned ${result.size} videos")
            result
        } catch (e: Exception) {
            android.util.Log.e("AniyomiRepo", "getVideoList failed: ${e.message}", e)
            val ioException = when {
                e is java.io.IOException -> e
                e.cause is java.io.IOException -> e.cause as java.io.IOException
                else -> null
            }
            if (ioException != null) {
                kotlinx.coroutines.delay(500)
                aniyomiSource.getVideoList(sEpisode)
            } else {
                throw e
            }
        }
        
        videos.mapIndexed { index, video ->
            android.util.Log.d("AniyomiRepo", "Video $index: url=${video.videoUrl}, quality=${video.videoTitle}")
            video.toKotoPage(source, sEpisode, index)
        }
    }
    
    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
    
    override suspend fun getPageUrl(page: MangaPage): String = withContext(Dispatchers.IO) {
        // For video, the URL is already the stream URL
        page.url
    }
    
    override suspend fun getFilterOptions(): MangaListFilterOptions {
        val aniyomiFilters = try {
            aniyomiSource.getFilterList()
        } catch (e: Exception) {
            AnimeFilterList()
        }
        
        return AniyomiFilterMapper.mapOptions(aniyomiFilters, source)
    }

    private fun MangaListFilter.toAniyomiFilterList(): AnimeFilterList {
        val aniyomiFilters = try {
            aniyomiSource.getFilterList()
        } catch (e: Exception) {
            return AnimeFilterList()
        }
        
        AniyomiFilterMapper.updateAniyomiFilters(aniyomiFilters, this)
        return aniyomiFilters
    }
    
    override fun getRequestHeaders(): Map<String, String> {
        val httpSource = aniyomiSource as? AnimeHttpSource ?: return emptyMap()
        val headers = httpSource.headers
        val map = mutableMapOf<String, String>()
        for (i in 0 until headers.size) {
            map[headers.name(i)] = headers.value(i)
        }
        if (map["Referer"] == null) {
            map["Referer"] = httpSource.baseUrl
        }
        return map
    }
}
