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
import org.skepsun.kototoro.BuildConfig
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.github.VersionId
import org.skepsun.kototoro.core.github.isStable

@Composable
fun AboutSettingsScreen(
    settings: AppSettings,
    isUpdateSupported: Boolean,
    isLoading: Boolean,
    onCheckUpdate: () -> Unit,
    onChangelogClick: () -> Unit,
    onLinkClick: (key: String) -> Unit,
    onCrashLogsClick: () -> Unit,
) {
    val isStableVersion = VersionId(BuildConfig.VERSION_NAME).isStable

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SettingsActionPreference(
            title = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
            summary = stringResource(R.string.check_for_updates),
            enabled = isUpdateSupported && !isLoading,
            onClick = onCheckUpdate,
        )
        if (isUpdateSupported) {
            SettingsSwitchPreference(
                title = stringResource(R.string.allow_unstable_updates),
                summary = stringResource(R.string.allow_unstable_updates_summary),
                checked = if (isStableVersion) settings.prefs.getBoolean(AppSettings.KEY_UPDATES_UNSTABLE, false) else true,
                enabled = isStableVersion,
                onCheckedChange = { checked ->
                    settings.prefs.edit().putBoolean(AppSettings.KEY_UPDATES_UNSTABLE, checked).apply()
                },
            )
        }
        SettingsActionPreference(
            title = stringResource(R.string.changelog),
            summary = stringResource(R.string.changelog_summary),
            onClick = onChangelogClick,
        )
        SettingsActionPreference(
            title = stringResource(R.string.user_manual),
            summary = stringResource(R.string.url_user_manual),
            onClick = { onLinkClick(AppSettings.KEY_LINK_MANUAL) },
        )
        SettingsActionPreference(
            title = stringResource(R.string.source_code),
            summary = stringResource(R.string.url_github),
            onClick = { onLinkClick(AppSettings.KEY_LINK_GITHUB) },
        )
        SettingsActionPreference(
            title = stringResource(R.string.about_donate),
            summary = stringResource(R.string.url_donate),
            onClick = { onLinkClick(AppSettings.KEY_LINK_DONATE) },
        )
        SettingsActionPreference(
            title = stringResource(R.string.about_discord),
            summary = stringResource(R.string.url_discord),
            onClick = { onLinkClick(AppSettings.KEY_LINK_DISCORD) },
        )
        SettingsActionPreference(
            title = stringResource(R.string.crash_logs),
            summary = stringResource(R.string.crash_logs_summary),
            onClick = onCrashLogsClick,
        )
    }
}
