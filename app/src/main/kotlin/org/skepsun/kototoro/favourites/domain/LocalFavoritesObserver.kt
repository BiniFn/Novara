package org.skepsun.kototoro.favourites.domain

import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.entity.toContent
import org.skepsun.kototoro.core.db.entity.toContentTags
import org.skepsun.kototoro.favourites.data.FavouriteContent
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.list.domain.ListSortOrder
import org.skepsun.kototoro.local.data.index.LocalContentIndex
import org.skepsun.kototoro.local.domain.LocalObserveMapper
import org.skepsun.kototoro.parsers.model.Content
import javax.inject.Inject

@Reusable
class LocalFavoritesObserver @Inject constructor(
	localContentIndex: LocalContentIndex,
	private val db: MangaDatabase,
) : LocalObserveMapper<FavouriteContent, Content>(localContentIndex) {

	fun observeAll(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	): Flow<List<Content>> = db.getFavouritesDao().observeAll(order, filterOptions, limit).mapToLocal()

	fun observeAll(
		categoryId: Long,
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	): Flow<List<Content>> = db.getFavouritesDao().observeAll(categoryId, order, filterOptions, limit).mapToLocal()

	override fun toContent(e: FavouriteContent) = e.manga.toContent(e.tags.toContentTags(), null)

	override fun toResult(e: FavouriteContent, manga: Content) = manga
}
