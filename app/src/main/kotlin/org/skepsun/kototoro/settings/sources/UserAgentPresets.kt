package org.skepsun.kototoro.settings.sources

import org.skepsun.kototoro.parsers.network.UserAgents

internal data class UserAgentPreset(
    val label: String,
    val value: String,
)

internal fun userAgentPresets(): List<UserAgentPreset> = listOf(
    UserAgentPreset(
        label = "Firefox Mobile",
        value = UserAgents.FIREFOX_MOBILE,
    ),
    UserAgentPreset(
        label = "Chrome Mobile",
        value = UserAgents.CHROME_MOBILE,
    ),
    UserAgentPreset(
        label = "Firefox Desktop",
        value = UserAgents.FIREFOX_DESKTOP,
    ),
    UserAgentPreset(
        label = "Chrome Desktop",
        value = UserAgents.CHROME_DESKTOP,
    ),
    UserAgentPreset(
        label = "JM WebView",
        value = UserAgents.JM_WEBVIEW,
    ),
)
