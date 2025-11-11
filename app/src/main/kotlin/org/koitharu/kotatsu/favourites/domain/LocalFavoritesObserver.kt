package org.skepsun.kototoro.favourites.domain

import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.entity.toManga
import org.skepsun.kototoro.core.db.entity.toMangaTags
import org.skepsun.kototoro.favourites.data.FavouriteManga
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.list.domain.ListSortOrder
import org.skepsun.kototoro.local.data.index.LocalMangaIndex
import org.skepsun.kototoro.local.domain.LocalObserveMapper
import org.skepsun.kototoro.parsers.model.Manga
import javax.inject.Inject

@Reusable
class LocalFavoritesObserver @Inject constructor(
	localMangaIndex: LocalMangaIndex,
	private val db: MangaDatabase,
) : LocalObserveMapper<FavouriteManga, Manga>(localMangaIndex) {

	fun observeAll(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	): Flow<List<Manga>> = db.getFavouritesDao().observeAll(order, filterOptions, limit).mapToLocal()

	fun observeAll(
		categoryId: Long,
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	): Flow<List<Manga>> = db.getFavouritesDao().observeAll(categoryId, order, filterOptions, limit).mapToLocal()

	override fun toManga(e: FavouriteManga) = e.manga.toManga(e.tags.toMangaTags(), null)

	override fun toResult(e: FavouriteManga, manga: Manga) = manga
}
