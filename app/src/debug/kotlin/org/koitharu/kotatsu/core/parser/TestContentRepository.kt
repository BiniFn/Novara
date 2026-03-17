package org.skepsun.kototoro.core.parser

import org.skepsun.kototoro.core.cache.MemoryContentCache
import org.skepsun.kototoro.core.model.TestContentSource
import org.skepsun.kototoro.parsers.ContentLoaderContext
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentListFilter
import org.skepsun.kototoro.parsers.model.ContentListFilterCapabilities
import org.skepsun.kototoro.parsers.model.ContentListFilterOptions
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.parsers.model.SortOrder
import java.util.EnumSet

/*
 This class is for parser development and testing purposes
 You can open it in the app via Settings -> Debug
 */
class TestContentRepository(
	@Suppress("unused") private val loaderContext: ContentLoaderContext,
	cache: MemoryContentCache
) : CachingContentRepository(cache) {

	override val source = TestContentSource

	override val sortOrders: Set<SortOrder> = EnumSet.allOf(SortOrder::class.java)

	override var defaultSortOrder: SortOrder
		get() = sortOrders.first()
		set(value) = Unit

	override val filterCapabilities = ContentListFilterCapabilities()

	override suspend fun getFilterOptions() = ContentListFilterOptions()

	override suspend fun getList(
		offset: Int,
		order: SortOrder?,
		filter: ContentListFilter?
	): List<Content> = TODO("Get manga list by filter")

	override suspend fun getDetailsImpl(
		manga: Content
	): Content = TODO("Fetch manga details")

	override suspend fun getPagesImpl(
		chapter: ContentChapter,
		nextChapterUrl: String?
	): List<ContentPage> = TODO("Get pages for specific chapter")

	override suspend fun getPageUrl(
		page: ContentPage
	): String = TODO("Return direct url of page image or page.url if it is already a direct url")

	override suspend fun getRelatedContentImpl(
		seed: Content
	): List<Content> = TODO("Get list of related manga. This method is optional and parser library has a default implementation")
}
