package org.skepsun.kototoro.core.parser.tvbox

import org.skepsun.kototoro.core.model.jsonsource.TVBoxStoredConfig
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentListFilter
import org.skepsun.kototoro.parsers.model.ContentListFilterOptions
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.parsers.model.SortOrder

internal interface TVBoxSpiderRuntime {

	val id: String

	fun describeCapability(config: TVBoxStoredConfig): String

	fun describeUnavailability(config: TVBoxStoredConfig): String?

	suspend fun getList(
		offset: Int,
		order: SortOrder?,
		filter: ContentListFilter?,
	): List<Content>?

	suspend fun getDetails(manga: Content): Content?

	suspend fun getPages(chapter: ContentChapter, nextChapterUrl: String?): List<ContentPage>?

	suspend fun getFilterOptions(): ContentListFilterOptions?

	fun getRequestHeaders(): Map<String, String>?
}
