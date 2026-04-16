package org.skepsun.kototoro.settings.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService

data class ServicesTrackingItem(
    val service: ScrobblerService,
    val title: String,
    val summary: String,
    @DrawableRes val iconRes: Int,
)

data class ServicesSettingsUiState(
    val suggestionsSummary: String,
    val isRelatedContentEnabled: Boolean,
    val isStatsEnabled: Boolean,
    val isReadingTimeEstimationEnabled: Boolean,
    val trackingItems: List<ServicesTrackingItem>,
)

@Composable
fun ServicesSettingsScreen(
    servicesTitle: String,
    trackingTitle: String,
    state: ServicesSettingsUiState,
    snackbarHostState: SnackbarHostState,
    pendingAuthService: ScrobblerService?,
    onDismissAuthPrompt: () -> Unit,
    onConfirmAuthPrompt: (ScrobblerService) -> Unit,
    onSyncSettingsClick: () -> Unit,
    onSuggestionsClick: () -> Unit,
    onRelatedContentChange: (Boolean) -> Unit,
    onStatsClick: () -> Unit,
    onStatsEnabledChange: (Boolean) -> Unit,
    onReadingTimeChange: (Boolean) -> Unit,
    onTrackingServiceClick: (ScrobblerService) -> Unit,
    onDiscordSettingsClick: () -> Unit,
) {
    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 20.dp,
                bottom = innerPadding.calculateBottomPadding() +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "services") {
                SettingsPreferenceSection(title = servicesTitle) {
                    SettingsActionPreference(
                        title = stringResource(R.string.sync_settings),
                        summary = stringResource(R.string.sync_settings_summary),
                        onClick = onSyncSettingsClick,
                    )
                    SettingsSectionDivider()
                    SettingsActionPreference(
                        title = stringResource(R.string.suggestions),
                        summary = state.suggestionsSummary,
                        onClick = onSuggestionsClick,
                    )
                    SettingsSectionDivider()
                    SettingsSwitchPreference(
                        title = stringResource(R.string.related_manga),
                        checked = state.isRelatedContentEnabled,
                        summary = stringResource(R.string.related_manga_summary),
                        onCheckedChange = onRelatedContentChange,
                    )
                    SettingsSectionDivider()
                    SettingsSplitSwitchPreference(
                        title = stringResource(R.string.reading_stats),
                        checked = state.isStatsEnabled,
                        summary = if (state.isStatsEnabled) {
                            stringResource(R.string.enabled)
                        } else {
                            stringResource(R.string.disabled)
                        },
                        onClick = onStatsClick,
                        onCheckedChange = onStatsEnabledChange,
                    )
                    SettingsSectionDivider()
                    SettingsSwitchPreference(
                        title = stringResource(R.string.reading_time_estimation),
                        checked = state.isReadingTimeEstimationEnabled,
                        summary = stringResource(R.string.reading_time_estimation_summary),
                        onCheckedChange = onReadingTimeChange,
                    )
                    SettingsSectionDivider()
                    SettingsActionPreference(
                        title = stringResource(R.string.discord_rpc),
                        summary = stringResource(R.string.discord_rpc_summary),
                        iconRes = R.drawable.ic_discord,
                        onClick = onDiscordSettingsClick,
                    )
                }
            }
            item(key = "tracking") {
                SettingsPreferenceSection(title = trackingTitle) {
                    state.trackingItems.forEachIndexed { index, item ->
                        SettingsActionPreference(
                            title = item.title,
                            summary = item.summary,
                            iconRes = item.iconRes,
                            onClick = { onTrackingServiceClick(item.service) },
                        )
                        if (index != state.trackingItems.lastIndex) {
                            SettingsSectionDivider()
                        }
                    }
                }
            }
        }
    }

    pendingAuthService?.let { service ->
        AlertDialog(
            onDismissRequest = onDismissAuthPrompt,
            title = { Text(text = stringResource(service.titleResId)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.scrobbler_auth_intro,
                        stringResource(service.titleResId),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirmAuthPrompt(service) }) {
                    Text(text = stringResource(R.string.sign_in))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissAuthPrompt) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
