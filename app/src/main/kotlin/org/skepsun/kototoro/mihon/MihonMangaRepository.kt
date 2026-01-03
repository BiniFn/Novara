package org.skepsun.kototoro.mihon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.core.cache.MemoryContentCache
import org.skepsun.kototoro.core.parser.CachingMangaRepository
import org.skepsun.kototoro.mihon.model.MihonMangaSource
import org.skepsun.kototoro.mihon.model.getPublicMangaUrl
import org.skepsun.kototoro.mihon.model.toKotoChapter
import org.skepsun.kototoro.mihon.model.toKotoManga
import org.skepsun.kototoro.mihon.model.toKotoPage
import org.skepsun.kototoro.mihon.model.toMihonChapter
import org.skepsun.kototoro.mihon.model.toMihonManga
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaListFilter
import org.skepsun.kototoro.parsers.model.MangaListFilterCapabilities
import org.skepsun.kototoro.parsers.model.MangaListFilterOptions
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.SortOrder

/**
 * Repository that adapts a Mihon CatalogueSource to Kototoro's MangaRepository interface.
 */
class MihonMangaRepository(
    override val source: MihonMangaSource,
    cache: MemoryContentCache,
) : CachingMangaRepository(cache) {
    
    companion object {
        private const val TAG = "MihonMangaRepository"
        
        private fun extractChapterNumber(name: String): Float {
            // Try Chinese format: 第X话
            val chineseRegex = Regex("""第\s*(\d+(?:\.\d+)?)\s*话""")
            chineseRegex.find(name)?.let {
                return it.groupValues[1].toFloatOrNull() ?: -1f
            }
            
            // Try English format: Chapter X, Ch. X
            val englishRegex = Regex("""(?:Chapter|Ch\.?)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
            englishRegex.find(name)?.let {
                return it.groupValues[1].toFloatOrNull() ?: -1f
            }
            
            // Try pure number
            val numberRegex = Regex("""(\d+(?:\.\d+)?)""")
            numberRegex.find(name)?.let {
                return it.groupValues[1].toFloatOrNull() ?: -1f
            }
            
            return -1f
        }
    }

    private var lastOffset = -1
    private var currentPage = 1
    
    val mihonSource = source.catalogueSource
    
    override val sortOrders: Set<SortOrder> = buildSet {
        add(SortOrder.POPULARITY)
        if (mihonSource.supportsLatest) {
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
        
        val mangasPage = when {
            hasFilters -> {
                mihonSource.getSearchManga(page, query ?: "", filter?.toMihonFilterList() ?: FilterList())
            }
            order == SortOrder.UPDATED && mihonSource.supportsLatest -> {
                mihonSource.getLatestUpdates(page)
            }
            else -> {
                mihonSource.getPopularManga(page)
            }
        }
        
        mangasPage.mangas.map { sManga ->
            sManga.toKotoManga(
                source = source,
                publicUrl = (mihonSource as? HttpSource)?.getPublicMangaUrl(sManga) ?: "",
            )
        }
    }
    
    override suspend fun getDetailsImpl(manga: Manga): Manga = withContext(Dispatchers.IO) {
        val sManga = manga.toMihonManga()
        
        val details = try {
            mihonSource.getMangaDetails(sManga)
        } catch (e: Exception) {
            val ioException = when {
                e is java.io.IOException -> e
                e.cause is java.io.IOException -> e.cause as java.io.IOException
                else -> null
            }
            
            if (ioException != null) {
                kotlinx.coroutines.delay(500)
                mihonSource.getMangaDetails(sManga)
            } else {
                throw e
            }
        }
        
        val rawChapters = try {
            mihonSource.getChapterList(sManga)
        } catch (e: Exception) {
            val ioException = when {
                e is java.io.IOException -> e
                e.cause is java.io.IOException -> e.cause as java.io.IOException
                else -> null
            }
            
            if (ioException != null) {
                kotlinx.coroutines.delay(500)
                mihonSource.getChapterList(sManga)
            } else {
                throw e
            }
        }
        
        val totalChapters = rawChapters.size
        // 采用最直观的策略：直接反转原始列表（假设原始是“最新在前”），并依次分配虚拟编号。
        // 这能确保 Page 1 对应 1.0，Page 15 对应 15.0，解决排序识别反向的问题。
        val chapters = rawChapters.asReversed()
            .mapIndexed { index, sChapter ->
                // 如果插件有提供合法的编号则保留，否则使用我们在反转列表中的索引位置。
                val chapterNumber = if (sChapter.chapter_number > 0) {
                    sChapter.chapter_number
                } else {
                    (index + 1).toFloat()
                }
                sChapter.toKotoChapter(source, chapterNumber)
            }
            .sortedBy { it.number } // Kototoro 内部列表始终保持升序
        
        // Copy missing fields from original manga to details
        // Some sources don't return all fields in getMangaDetails, or return them empty.
        details.url = sManga.url
        
        // Title fallback
        val detailsTitle = try { details.title } catch (e: Exception) { "" }
        if (detailsTitle.isBlank()) {
            details.title = sManga.title
        }
        
        // Thumbnail fallback - IMPORTANT: many sources return empty or same-as-manga-url thumbnail in details
        val detailsThumb = try { details.thumbnail_url } catch (e: Exception) { null }
        val searchThumb = try { sManga.thumbnail_url } catch (e: Exception) { null }
        
        if (detailsThumb.isNullOrBlank() || detailsThumb == details.url || detailsThumb == sManga.url) {
            if (!searchThumb.isNullOrBlank()) {
                android.util.Log.d("MihonMangaRepository", "Detail thumb is invalid/missing, falling back to search thumb: $searchThumb")
                details.thumbnail_url = searchThumb
            }
        }
        
        android.util.Log.d("MihonMangaRepository", "Final details thumbnail: ${try { details.thumbnail_url } catch (e: Exception) { "uninitialized" }}")
        
        val publicUrl = (mihonSource as? HttpSource)?.getPublicMangaUrl(details) ?: ""
        
        details.toKotoManga(
            source = source,
            chapters = chapters,
            publicUrl = publicUrl,
        ).copy(id = manga.id)
    }
    
    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        val sChapter = chapter.toMihonChapter()
        val pages = mihonSource.getPageList(sChapter)
        
        pages.mapIndexed { index, page ->
            if (mihonSource !is HttpSource) {
                return@mapIndexed page.toKotoPage(source, sChapter)
            }

            val headers = try {
                if (!page.imageUrl.isNullOrBlank()) {
                    val h = mihonSource.getPageHeaders(page)
                    val map = mutableMapOf<String, String>()
                    for (i in 0 until h.size) {
                        map[h.name(i)] = h.value(i)
                    }
                    map
                } else {
                    emptyMap()
                }
            } catch (e: Exception) {
                emptyMap()
            }

            page.toKotoPage(source, sChapter, headers).let { kotoPage ->
                if (page.imageUrl.isNullOrBlank() && page.url.isNotBlank()) {
                    kotoPage.copy(
                        url = "mihon://resolve?page_url=${java.net.URLEncoder.encode(page.url, "UTF-8")}&index=$index"
                    )
                } else {
                    kotoPage
                }
            }
        }
    }
    
    override suspend fun getPageUrl(page: MangaPage): String = withContext(Dispatchers.IO) {
        val url = page.url
        
        if (url.startsWith("mihon://resolve")) {
            val pageUrl = try {
                val params = url.substringAfter("?")
                val pageUrlEncoded = params.substringAfter("page_url=").substringBefore("&")
                java.net.URLDecoder.decode(pageUrlEncoded, "UTF-8")
            } catch (e: Exception) {
                return@withContext url
            }
            
            val mihonPage = eu.kanade.tachiyomi.source.model.Page(0, pageUrl)
            
            val httpSource = mihonSource as? HttpSource
            if (httpSource != null) {
                return@withContext httpSource.getImageUrl(mihonPage)
            }
            
            pageUrl
        } else {
            url
        }
    }
    
    override suspend fun getFilterOptions(): MangaListFilterOptions {
        val mihonFilters = try {
            mihonSource.getFilterList()
        } catch (e: Exception) {
            FilterList()
        }
        
        return MihonFilterMapper.mapOptions(mihonFilters, source)
    }

    private fun MangaListFilter.toMihonFilterList(): FilterList {
        val mihonFilters = try {
            mihonSource.getFilterList()
        } catch (e: Exception) {
            return FilterList()
        }
        
        MihonFilterMapper.updateMihonFilters(mihonFilters, this)
        return mihonFilters
    }
    
    override fun getRequestHeaders(): Map<String, String> {
        val httpSource = mihonSource as? HttpSource ?: return emptyMap()
        val headers = httpSource.headers
        val map = mutableMapOf<String, String>()
        for (i in 0 until headers.size) {
            map[headers.name(i)] = headers.value(i)
        }
        // Ensure Referer is present for sources that don't define it in headersBuilder
        if (map["Referer"] == null) {
            map["Referer"] = httpSource.baseUrl
        }
        return map
    }
    
    override fun createPageRequest(pageUrl: String, page: MangaPage): okhttp3.Request {
        if (pageUrl.isBlank()) return super.createPageRequest(pageUrl, page)
        val httpSource = mihonSource as? HttpSource ?: return super.createPageRequest(pageUrl, page)
        val sPage = page.toMihonPage(pageUrl)
        return httpSource.imageRequest(sPage)
    }

    override fun createCoverRequest(imageUrl: String): okhttp3.Request {
        val httpSource = mihonSource as? HttpSource ?: return super.createCoverRequest(imageUrl)
        return try {
            // Some sources might have specific Referer logic in imageRequest
            val sPage = eu.kanade.tachiyomi.source.model.Page(0, imageUrl = imageUrl)
            httpSource.imageRequest(sPage)
        } catch (e: Throwable) {
            // Fallback for sources that assume Page is always a chapter page (e.g. DM5 crashes on missing 'cid')
            super.createCoverRequest(imageUrl)
        }
    }

    private fun MangaPage.toMihonPage(imageUrl: String): eu.kanade.tachiyomi.source.model.Page {
        return eu.kanade.tachiyomi.source.model.Page(
            index = id.toInt(), // Use id as index
            url = url,
            imageUrl = imageUrl
        )
    }
    
    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
}
