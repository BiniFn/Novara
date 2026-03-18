package org.skepsun.kototoro.sync.domain

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.favourites.data.FavouriteCategoriesDao
import org.skepsun.kototoro.favourites.data.FavouritesDao
import org.skepsun.kototoro.history.data.HistoryDao

class SyncGcCoordinatorTest {

	private val historyDao = mockk<HistoryDao>(relaxed = true)
	private val favouritesDao = mockk<FavouritesDao>(relaxed = true)
	private val favouriteCategoriesDao = mockk<FavouriteCategoriesDao>(relaxed = true)
	private val dbProvider = mockk<javax.inject.Provider<MangaDatabase>>()
	private val db = mockk<MangaDatabase>(relaxed = true)
	private val coordinator = SyncGcCoordinator(dbProvider)

	init {
		every { dbProvider.get() } returns db
		every { db.getHistoryDao() } returns historyDao
		every { db.getFavouritesDao() } returns favouritesDao
		every { db.getFavouriteCategoriesDao() } returns favouriteCategoriesDao
	}

	@Test
	fun `gcIfNeeded runs gc when either flag set`() {
		kotlinx.coroutines.runBlocking {
			coordinator.gcIfNeeded(
				favourites = false,
				history = false,
				gcFavourites = true,
				gcHistory = false,
			)
			coordinator.gcIfNeeded(
				favourites = false,
				history = true,
				gcFavourites = false,
				gcHistory = true,
			)
		}

		coVerify(exactly = 1) { historyDao.gc(any()) }
		coVerify(exactly = 1) { favouritesDao.gc(any()) }
		coVerify(exactly = 1) { favouriteCategoriesDao.gc(any()) }
	}

	@Test
	fun `gcIfNeeded skips when no flags true`() {
		kotlinx.coroutines.runBlocking {
			coordinator.gcIfNeeded(
				favourites = false,
				history = false,
				gcFavourites = false,
				gcHistory = false,
			)
		}

		coVerify(exactly = 0) { historyDao.gc(any()) }
		coVerify(exactly = 0) { favouritesDao.gc(any()) }
		coVerify(exactly = 0) { favouriteCategoriesDao.gc(any()) }
	}
}
