package org.skepsun.kototoro.sync.domain

import android.accounts.Account
import android.content.ContentResolver
import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyncAuthorityExecutorTest {

    private val executor = SyncAuthorityExecutor()

    @Test
    fun `execute requests all authorities and returns correct result`() {
        mockkStatic(ContentResolver::class)
        every { ContentResolver.requestSync(any(), any(), any()) } returns Unit

        val account = mockk<Account>()
        val plan = SyncRequestPlanner.AuthorityExecutionPlan(
            requestedAuthorities = listOf("authority.favourites", "authority.history"),
            gcFavourites = false,
            gcHistory = false,
        )

        val result = executor.execute(
            account = account,
            plan = plan,
            authorityFavourites = "authority.favourites",
            authorityHistory = "authority.history",
        )

        verify(exactly = 2) { ContentResolver.requestSync(account, any(), Bundle.EMPTY) }
        assertEquals(listOf("authority.favourites", "authority.history"), result.requestedAuthorities)
        assertEquals(emptyList<String>(), result.disabledAuthorities)
    }

    @Test
    fun `execute with gc flags returns disabled authorities`() {
        mockkStatic(ContentResolver::class)
        every { ContentResolver.requestSync(any(), any(), any()) } returns Unit

        val account = mockk<Account>()
        val plan = SyncRequestPlanner.AuthorityExecutionPlan(
            requestedAuthorities = listOf("authority.favourites"),
            gcFavourites = false,
            gcHistory = true,
        )

        val result = executor.execute(
            account = account,
            plan = plan,
            authorityFavourites = "authority.favourites",
            authorityHistory = "authority.history",
        )

        verify(exactly = 1) { ContentResolver.requestSync(account, "authority.favourites", Bundle.EMPTY) }
        assertEquals(listOf("authority.favourites"), result.requestedAuthorities)
        assertEquals(listOf("authority.history"), result.disabledAuthorities)
    }

    @Test
    fun `execute with both gc flags returns both disabled authorities`() {
        mockkStatic(ContentResolver::class)
        every { ContentResolver.requestSync(any(), any(), any()) } returns Unit

        val account = mockk<Account>()
        val plan = SyncRequestPlanner.AuthorityExecutionPlan(
            requestedAuthorities = emptyList(),
            gcFavourites = true,
            gcHistory = true,
        )

        val result = executor.execute(
            account = account,
            plan = plan,
            authorityFavourites = "authority.favourites",
            authorityHistory = "authority.history",
        )

        verify(exactly = 0) { ContentResolver.requestSync(any(), any(), any()) }
        assertEquals(emptyList<String>(), result.requestedAuthorities)
        assertEquals(2, result.disabledAuthorities.size)
    }
}
