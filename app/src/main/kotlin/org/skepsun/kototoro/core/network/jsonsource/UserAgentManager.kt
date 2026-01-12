package org.skepsun.kototoro.core.network.jsonsource

import org.skepsun.kototoro.parsers.network.UserAgents
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages User-Agent for JSON sources
 * Uses a fixed User-Agent to maintain consistent behavior across all requests
 * (Legado uses a fixed desktop Chrome UA by default)
 */
@Singleton
class UserAgentManager @Inject constructor() {

    // Use a fixed User-Agent to ensure consistent page layouts
    // Some websites serve different content (mobile vs desktop) based on UA
    // Using a fixed UA prevents issues where list pages work but detail pages fail
    private val defaultUserAgent = UserAgents.CHROME_DESKTOP

    /**
     * Get the current User-Agent
     * Returns a fixed User-Agent for consistency
     */
    fun getUserAgent(): String {
        return defaultUserAgent
    }

    /**
     * Get a specific User-Agent by name
     * @param name User-Agent identifier (chrome, firefox, safari, mobile)
     * @return User-Agent string or default if not found
     */
    fun getUserAgent(name: String): String {
        return when (name.lowercase()) {
            "chrome", "chrome_desktop" -> UserAgents.CHROME_DESKTOP
            "chrome_mobile" -> UserAgents.CHROME_MOBILE
            "firefox", "firefox_desktop" -> UserAgents.FIREFOX_DESKTOP
            "firefox_mobile" -> UserAgents.FIREFOX_MOBILE
            "mobile" -> UserAgents.CHROME_MOBILE
            else -> defaultUserAgent
        }
    }
}

