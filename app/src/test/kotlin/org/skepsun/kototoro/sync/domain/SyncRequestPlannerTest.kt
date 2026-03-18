package org.skepsun.kototoro.sync.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncRequestPlannerTest {

	private val planner = SyncRequestPlanner()

	@Test
	fun `planInvalidation skips authorities already active or pending`() {
		val decision = planner.planInvalidation(
			tables = setOf("history", "favourites", "favourite_categories"),
			favouritesTable = "favourites",
			favouriteCategoriesTable = "favourite_categories",
			historyTable = "history",
			isFavouritesSyncActiveOrPending = true,
			isHistorySyncActiveOrPending = false,
		)

		assertFalse(decision.favourites)
		assertTrue(decision.history)
		assertTrue(decision.shouldRequestSync)
	}

	@Test
	fun `planAuthorityExecution separates requested authorities from gc fallback`() {
		val plan = planner.planAuthorityExecution(
			favouritesRequested = true,
			historyRequested = true,
			authorityFavourites = "sync.favourites",
			authorityHistory = "sync.history",
			isFavouritesAuthorityEnabled = false,
			isHistoryAuthorityEnabled = true,
		)

		assertEquals(listOf("sync.history"), plan.requestedAuthorities)
		assertTrue(plan.gcFavourites)
		assertFalse(plan.gcHistory)
	}

	@Test
	fun `planAuthorityExecution returns empty request list when nothing selected`() {
		val plan = planner.planAuthorityExecution(
			favouritesRequested = false,
			historyRequested = false,
			authorityFavourites = "sync.favourites",
			authorityHistory = "sync.history",
			isFavouritesAuthorityEnabled = true,
			isHistoryAuthorityEnabled = true,
		)

		assertTrue(plan.requestedAuthorities.isEmpty())
		assertFalse(plan.gcFavourites)
		assertFalse(plan.gcHistory)
	}
}
