package org.skepsun.kototoro.core.network.jsonsource

import org.junit.Assert.*
import org.junit.Test
import org.skepsun.kototoro.parsers.network.UserAgents

class UserAgentManagerTest {

    @Test
    fun `getUserAgent should rotate through user agents`() {
        // Given
        val manager = UserAgentManager()

        // When
        val ua1 = manager.getUserAgent()
        val ua2 = manager.getUserAgent()
        val ua3 = manager.getUserAgent()

        // Then - should get different user agents
        assertNotEquals(ua1, ua2)
        assertNotEquals(ua2, ua3)
    }

    @Test
    fun `getUserAgent should cycle back after all agents used`() {
        // Given
        val manager = UserAgentManager()

        // When - get 5 user agents (more than the 4 in the list)
        val agents = (1..5).map { manager.getUserAgent() }

        // Then - first and fifth should be the same (cycled)
        assertEquals(agents[0], agents[4])
    }

    @Test
    fun `getUserAgent by name should return correct agent`() {
        // Given
        val manager = UserAgentManager()

        // When & Then
        assertEquals(UserAgents.CHROME_MOBILE, manager.getUserAgent("chrome"))
        assertEquals(UserAgents.FIREFOX_MOBILE, manager.getUserAgent("firefox"))
        assertEquals(UserAgents.CHROME_DESKTOP, manager.getUserAgent("chrome_desktop"))
        assertEquals(UserAgents.FIREFOX_DESKTOP, manager.getUserAgent("firefox_desktop"))
    }

    @Test
    fun `getUserAgent with unknown name should use rotation`() {
        // Given
        val manager = UserAgentManager()

        // When
        val ua = manager.getUserAgent("unknown")

        // Then - should return a valid user agent
        assertNotNull(ua)
        assertTrue(ua.isNotEmpty())
    }

    @Test
    fun `getUserAgent should be case insensitive`() {
        // Given
        val manager = UserAgentManager()

        // When & Then
        assertEquals(UserAgents.CHROME_MOBILE, manager.getUserAgent("CHROME"))
        assertEquals(UserAgents.CHROME_MOBILE, manager.getUserAgent("Chrome"))
        assertEquals(UserAgents.CHROME_MOBILE, manager.getUserAgent("chrome"))
    }
}
