package org.skepsun.kototoro.history.data

import dagger.Reusable
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.entity.toContent
import org.skepsun.kototoro.core.db.entity.toContentTags
import org.skepsun.kototoro.history.domain.model.ContentWithHistory
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.list.domain.ListSortOrder
import org.skepsun.kototoro.local.data.index.LocalContentIndex
import org.skepsun.kototoro.local.domain.LocalObserveMapper
import org.skepsun.kototoro.parsers.model.Content
import javax.inject.Inject

@Reusable
class HistoryLocalObserver @Inject constructor(
	localContentIndex: LocalContentIndex,
	private val db: MangaDatabase,
) : LocalObserveMapper<HistoryWithContent, ContentWithHistory>(localContentIndex) {

	fun observeAll(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	) = db.getHistoryDao().observeAll(order, filterOptions, limit).mapToLocal()

	override fun toContent(e: HistoryWithContent) = e.manga.toContent(e.tags.toContentTags(), null)

	override fun toResult(e: HistoryWithContent, manga: Content) = ContentWithHistory(
		manga = manga,
		history = e.history.toContentHistory(),
	)
}
