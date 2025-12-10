package org.skepsun.kototoro.core.network.jsonsource

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for LegadoHttpClient
 * 
 * Note: Full integration tests with actual HTTP requests are in androidTest
 */
class LegadoHttpClientTest {

    @Test
    fun `UserAgentManager should be created successfully`() {
        val manager = UserAgentManager()
        assertNotNull(manager)
    }

    @Test
    fun `UserAgentManager should provide valid user agents`() {
        val manager = UserAgentManager()
        val userAgent = manager.getUserAgent()
        
        assertNotNull(userAgent)
        assertTrue(userAgent.isNotEmpty())
        assertTrue(userAgent.contains("Mozilla"))
    }
}
