package org.skepsun.kototoro.core.parser.tvbox

import org.skepsun.kototoro.core.model.jsonsource.TVBoxStoredConfig
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaListFilter
import org.skepsun.kototoro.parsers.model.MangaListFilterOptions
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.SortOrder

internal interface TVBoxSpiderRuntime {

	val id: String

	fun describeCapability(config: TVBoxStoredConfig): String

	fun describeUnavailability(config: TVBoxStoredConfig): String?

	suspend fun getList(
		offset: Int,
		order: SortOrder?,
		filter: MangaListFilter?,
	): List<Manga>?

	suspend fun getDetails(manga: Manga): Manga?

	suspend fun getPages(chapter: MangaChapter, nextChapterUrl: String?): List<MangaPage>?

	suspend fun getFilterOptions(): MangaListFilterOptions?

	fun getRequestHeaders(): Map<String, String>?
}
