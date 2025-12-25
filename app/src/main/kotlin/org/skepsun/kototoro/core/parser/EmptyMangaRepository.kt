package org.skepsun.kototoro.core.parser

import org.skepsun.kototoro.core.exceptions.UnsupportedSourceException
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaListFilter
import org.skepsun.kototoro.parsers.model.MangaListFilterCapabilities
import org.skepsun.kototoro.parsers.model.MangaListFilterOptions
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.model.SortOrder
import java.util.EnumSet

open class EmptyMangaRepository(override val source: MangaSource) : MangaRepository {

	override val sortOrders: Set<SortOrder>
		get() = EnumSet.allOf(SortOrder::class.java)

	override var defaultSortOrder: SortOrder
		get() = SortOrder.NEWEST
		set(value) = Unit

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities()

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> = stub(null)

	override suspend fun getDetails(manga: Manga): Manga = stub(manga)

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> = stub(null)

	override suspend fun getPageUrl(page: MangaPage): String = stub(null)

	override suspend fun getChapterContent(chapter: MangaChapter) = stub(null)

	override suspend fun getFilterOptions(): MangaListFilterOptions = stub(null)

	override suspend fun getRelated(seed: Manga): List<Manga> = stub(seed)

	private fun stub(manga: Manga?): Nothing {
		throw UnsupportedSourceException("This manga source is not supported", manga)
	}
}
