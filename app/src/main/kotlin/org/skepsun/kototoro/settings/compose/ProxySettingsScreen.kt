package org.skepsun.kototoro.settings.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import java.net.Proxy

@Composable
fun ProxySettingsScreen(
    settings: AppSettings,
    testSummary: String?,
    isTestRunning: Boolean,
    onTestConnection: () -> Unit,
) {

    val proxyType = settings.proxyType
    val isProxyEnabled = proxyType != Proxy.Type.DIRECT
    val typeOptions = listOf(
        SettingsChoiceOption(Proxy.Type.DIRECT.name, stringResource(R.string.disabled)),
        SettingsChoiceOption(Proxy.Type.HTTP.name, "HTTP"),
        SettingsChoiceOption(Proxy.Type.SOCKS.name, "SOCKS"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SettingsChoicePreference(
            title = stringResource(R.string.type),
            value = proxyType.name,
            options = typeOptions,
            onValueChange = { value ->
                settings.prefs.edit().putString(AppSettings.KEY_PROXY_TYPE, value).apply()
            },
        )
        SettingsTextInputPreference(
            title = stringResource(R.string.address),
            value = settings.prefs.getString(AppSettings.KEY_PROXY_ADDRESS, "") ?: "",
            enabled = isProxyEnabled,
            onValueChange = { value ->
                settings.prefs.edit().putString(AppSettings.KEY_PROXY_ADDRESS, value).apply()
            },
        )
        SettingsTextInputPreference(
            title = stringResource(R.string.port),
            value = settings.prefs.getString(AppSettings.KEY_PROXY_PORT, "") ?: "",
            enabled = isProxyEnabled,
            onValueChange = { value ->
                settings.prefs.edit().putString(AppSettings.KEY_PROXY_PORT, value).apply()
            },
        )
        SettingsPreferenceSection(title = stringResource(R.string.authorization_optional)) {
            SettingsTextInputPreference(
                title = stringResource(R.string.username),
                value = settings.prefs.getString(AppSettings.KEY_PROXY_LOGIN, "") ?: "",
                enabled = isProxyEnabled,
                onValueChange = { value ->
                    settings.prefs.edit().putString(AppSettings.KEY_PROXY_LOGIN, value).apply()
                },
            )
            SettingsTextInputPreference(
                title = stringResource(R.string.password),
                value = settings.prefs.getString(AppSettings.KEY_PROXY_PASSWORD, "") ?: "",
                enabled = isProxyEnabled,
                isPassword = true,
                onValueChange = { value ->
                    settings.prefs.edit().putString(AppSettings.KEY_PROXY_PASSWORD, value).apply()
                },
            )
        }
        SettingsActionPreference(
            title = stringResource(R.string.test_connection),
            summary = testSummary,
            enabled = isProxyEnabled && !isTestRunning,
            onClick = onTestConnection,
        )
    }
}
