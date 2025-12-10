package org.skepsun.kototoro.core.network.jsonsource

import org.skepsun.kototoro.parsers.network.UserAgents
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages User-Agent rotation for JSON sources
 * Rotates through a list of common User-Agents to avoid detection
 */
@Singleton
class UserAgentManager @Inject constructor() {

    private val userAgents = listOf(
        UserAgents.CHROME_MOBILE,
        UserAgents.FIREFOX_MOBILE,
        UserAgents.CHROME_DESKTOP,
        UserAgents.FIREFOX_DESKTOP,
    )

    private val currentIndex = AtomicInteger(0)

    /**
     * Get the current User-Agent
     * Rotates to the next one on each call
     */
    fun getUserAgent(): String {
        val index = currentIndex.getAndUpdate { current ->
            (current + 1) % userAgents.size
        }
        return userAgents[index]
    }

    /**
     * Get a specific User-Agent by name
     * @param name User-Agent identifier (chrome, firefox, safari)
     * @return User-Agent string or default if not found
     */
    fun getUserAgent(name: String): String {
        return when (name.lowercase()) {
            "chrome", "chrome_mobile" -> UserAgents.CHROME_MOBILE
            "firefox", "firefox_mobile" -> UserAgents.FIREFOX_MOBILE
            "chrome_desktop" -> UserAgents.CHROME_DESKTOP
            "firefox_desktop" -> UserAgents.FIREFOX_DESKTOP
            else -> getUserAgent() // Default to rotation
        }
    }
}
