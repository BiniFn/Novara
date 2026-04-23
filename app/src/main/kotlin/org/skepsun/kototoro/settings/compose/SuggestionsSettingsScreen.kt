package org.skepsun.kototoro.settings.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings

@Composable
fun SuggestionsSettingsScreen(
    settings: AppSettings,
    excludeTags: String,
    preferredTags: String,
    onExcludeTagsChanged: (String) -> Unit,
    onPreferredTagsChanged: (String) -> Unit,
) {
    val isEnabled = settings.isSuggestionsEnabled

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            SettingsPreferenceSection(
                title = stringResource(R.string.suggestions),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SettingsSwitchPreference(
                    title = stringResource(R.string.suggestions_enable),
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        settings.prefs.edit().putBoolean(AppSettings.KEY_SUGGESTIONS, checked).apply()
                    },
                )
                SettingsSectionDivider()
                SettingsSwitchPreference(
                    title = stringResource(R.string.only_using_wifi),
                    summary = stringResource(R.string.suggestions_wifi_only_summary),
                    checked = settings.prefs.getBoolean("suggestions_wifi", false),
                    enabled = isEnabled,
                    onCheckedChange = { checked ->
                        settings.prefs.edit().putBoolean("suggestions_wifi", checked).apply()
                    },
                )
                SettingsSectionDivider()
                SettingsSwitchPreference(
                    title = stringResource(R.string.include_disabled_sources),
                    summary = stringResource(R.string.suggestions_disabled_sources_summary),
                    checked = settings.prefs.getBoolean("suggestions_disabled_sources", false),
                    enabled = isEnabled,
                    onCheckedChange = { checked ->
                        settings.prefs.edit().putBoolean("suggestions_disabled_sources", checked).apply()
                    },
                )
                SettingsSectionDivider()
                SettingsSwitchPreference(
                    title = stringResource(R.string.notifications_enable),
                    summary = stringResource(R.string.suggestions_notifications_summary),
                    checked = settings.prefs.getBoolean("suggestions_notifications", false),
                    enabled = isEnabled,
                    onCheckedChange = { checked ->
                        settings.prefs.edit().putBoolean("suggestions_notifications", checked).apply()
                    },
                )
                SettingsSectionDivider()
                SettingsTextInputPreference(
                    title = stringResource(R.string.suggestions_excluded_genres),
                    summary = excludeTags.ifEmpty { stringResource(R.string.suggestions_excluded_genres_summary) },
                    value = excludeTags,
                    enabled = isEnabled,
                    onValueChange = onExcludeTagsChanged,
                )
                SettingsSectionDivider()
                SettingsTextInputPreference(
                    title = stringResource(R.string.suggestions_preferred_genres),
                    summary = preferredTags.ifEmpty { stringResource(R.string.suggestions_preferred_genres_summary) },
                    value = preferredTags,
                    enabled = isEnabled,
                    onValueChange = onPreferredTagsChanged,
                )
                SettingsSectionDivider()
                SettingsInfoPreference(
                    title = stringResource(R.string.suggestions_info),
                    summary = "",
                )
            }
        }
    }
}
