package org.skepsun.kototoro.ireader

import ireader.core.source.Source
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
import org.skepsun.kototoro.parsers.model.SortOrder

class IReaderMangaRepository(
    override val source: IReaderMangaSource,
    cache: MemoryContentCache,
) : CachingContentRepository(cache) {

    private val ireaderSource: Source = source.catalogueSource

    override val sortOrders: Set<SortOrder> = setOf(SortOrder.POPULARITY, SortOrder.UPDATED)

    override val filterCapabilities: ContentListFilterCapabilities
        get() = ContentListFilterCapabilities(
            isSearchSupported = true,
            isMultipleTagsSupported = true,
            isSearchWithFiltersSupported = true,
        )

    override var defaultSortOrder: SortOrder = SortOrder.POPULARITY

    override suspend fun getList(
        offset: Int,
        order: SortOrder?,
        filter: ContentListFilter?
    ): List<Content> = withContext(Dispatchers.IO) {
        // Implementation mapped to IReader Source getList mechanism
        emptyList()
    }

    override suspend fun getDetailsImpl(manga: Content): Content = withContext(Dispatchers.IO) {
        manga
    }

    override suspend fun getPagesImpl(chapter: ContentChapter, nextChapterUrl: String?): List<ContentPage> = withContext(Dispatchers.IO) {
        emptyList()
    }

    override suspend fun getPageUrl(page: ContentPage): String = page.url

    override suspend fun getFilterOptions(): ContentListFilterOptions {
        return ContentListFilterOptions(emptySet(), emptyList())
    }

    override suspend fun getRelatedContentImpl(seed: Content): List<Content> = emptyList()
}
