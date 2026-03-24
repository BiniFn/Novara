package org.skepsun.kototoro.ireader

import ireader.core.source.CatalogSource
import ireader.core.source.Source
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.ImageUrl
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.PageUrl
import ireader.core.source.model.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.core.cache.MemoryContentCache
import org.skepsun.kototoro.core.parser.CachingContentRepository
import org.skepsun.kototoro.ireader.model.IReaderMangaSource
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentListFilter
import org.skepsun.kototoro.parsers.model.ContentListFilterCapabilities
import org.skepsun.kototoro.parsers.model.ContentListFilterOptions
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.parsers.model.ContentState
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.parsers.model.RATING_UNKNOWN
import org.skepsun.kototoro.parsers.model.SortOrder

class IReaderMangaRepository(
    override val source: IReaderMangaSource,
    cache: MemoryContentCache,
) : CachingContentRepository(cache) {

    private val ireaderSource: Source = source.catalogueSource
    private val catalogSource: CatalogSource? = ireaderSource as? CatalogSource

    override val sortOrders: Set<SortOrder> = setOf(SortOrder.POPULARITY, SortOrder.UPDATED)

    override val filterCapabilities: ContentListFilterCapabilities
        get() = ContentListFilterCapabilities(
            isSearchSupported = catalogSource?.supportsSearch() ?: false,
            isMultipleTagsSupported = false,
            isSearchWithFiltersSupported = false,
        )

    override var defaultSortOrder: SortOrder = SortOrder.POPULARITY

    override suspend fun getList(
        offset: Int,
        order: SortOrder?,
        filter: ContentListFilter?
    ): List<Content> = withContext(Dispatchers.IO) {
        val catalog = catalogSource ?: return@withContext emptyList()
        try {
            val page = offset + 1 // IReader uses 1-based pages
            val query = filter?.query
            val result = if (!query.isNullOrBlank()) {
                // Search: use filters with query
                val filters = catalog.getFilters().toMutableList()
                // Set first filter's value if it's a search filter
                catalog.getMangaList(
                    filters = listOf(ireader.core.source.model.Filter.Title(query)),
                    page = page
                )
            } else {
                // Browse: use listings
                val listings = catalog.getListings()
                val listing = when (order) {
                    SortOrder.UPDATED -> listings.getOrNull(1) ?: listings.firstOrNull()
                    else -> listings.firstOrNull()
                }
                catalog.getMangaList(sort = listing, page = page)
            }
            android.util.Log.d("IReaderRepo", "getList: page=$page query=$query results=${result.mangas.size} hasNext=${result.hasNextPage}")
            result.mangas.map { manga -> manga.toContent() }
        } catch (e: Exception) {
            android.util.Log.e("IReaderRepo", "getList failed", e)
            emptyList()
        }
    }

    override suspend fun getDetailsImpl(manga: Content): Content = withContext(Dispatchers.IO) {
        try {
            val mangaInfo = MangaInfo(key = manga.url, title = manga.title, cover = manga.coverUrl.orEmpty())
            val details = ireaderSource.getMangaDetails(mangaInfo, emptyList())
            val chapters = ireaderSource.getChapterList(mangaInfo, emptyList())
            android.util.Log.d("IReaderRepo", "getDetails: title=${details.title} chapters=${chapters.size}")
            details.toContent().copy(
                chapters = chapters.mapIndexed { index, ch -> ch.toContentChapter(index) },
            )
        } catch (e: Exception) {
            android.util.Log.e("IReaderRepo", "getDetails failed", e)
            manga
        }
    }

    override suspend fun getPagesImpl(chapter: ContentChapter, nextChapterUrl: String?): List<ContentPage> = withContext(Dispatchers.IO) {
        try {
            val chapterInfo = ChapterInfo(key = chapter.url, name = chapter.title.orEmpty())
            val pages = ireaderSource.getPageList(chapterInfo, emptyList())
            android.util.Log.d("IReaderRepo", "getPages: chapter=${chapter.title} pages=${pages.size}")
            pages.mapIndexedNotNull { index, page ->
                when (page) {
                    is ImageUrl -> ContentPage(
                        id = index.toLong(),
                        url = page.url,
                        preview = null,
                        source = source,
                    )
                    is PageUrl -> ContentPage(
                        id = index.toLong(),
                        url = page.url,
                        preview = null,
                        source = source,
                    )
                    is Text -> {
                        // Encode the full text content as an HTML data URL.
                        // NovelContentLoader.decodeChapterHtml() already handles data:text/html;base64,... 
                        val htmlContent = "<p>${page.text}</p>"
                        val encoded = android.util.Base64.encodeToString(
                            htmlContent.toByteArray(Charsets.UTF_8),
                            android.util.Base64.NO_WRAP
                        )
                        ContentPage(
                            id = index.toLong(),
                            url = "data:text/html;base64,$encoded",
                            preview = null,
                            source = source,
                        )
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("IReaderRepo", "getPages failed", e)
            emptyList()
        }
    }

    override suspend fun getPageUrl(page: ContentPage): String = page.url

    override suspend fun getFilterOptions(): ContentListFilterOptions {
        return ContentListFilterOptions(emptySet(), emptyList())
    }

    override suspend fun getRelatedContentImpl(seed: Content): List<Content> = emptyList()

    // --- Model Mapping ---

    private fun MangaInfo.toContent(): Content {
        val id = key.hashCode().toLong()
        return Content(
            id = id,
            title = title,
            altTitles = emptySet(),
            url = key,
            publicUrl = key,
            rating = RATING_UNKNOWN,
            contentRating = null,
            coverUrl = cover.ifBlank { null },
            tags = genres.map { ContentTag(it, it, source) }.toSet(),
            state = when (status) {
                MangaInfo.ONGOING -> ContentState.ONGOING
                MangaInfo.COMPLETED, MangaInfo.PUBLISHING_FINISHED -> ContentState.FINISHED
                MangaInfo.ON_HIATUS -> ContentState.PAUSED
                MangaInfo.CANCELLED -> ContentState.ABANDONED
                else -> null
            },
            authors = buildSet {
                if (author.isNotBlank()) add(author)
                if (artist.isNotBlank() && artist != author) add(artist)
            },
            description = description.ifBlank { null },
            source = source,
        )
    }

    private fun ChapterInfo.toContentChapter(index: Int): ContentChapter {
        val id = key.hashCode().toLong()
        return ContentChapter(
            id = id,
            title = name,
            number = if (number >= 0f) number else (index + 1).toFloat(),
            volume = 0,
            url = key,
            scanlator = scanlator.ifBlank { null },
            uploadDate = dateUpload,
            branch = null,
            source = source,
        )
    }
}
