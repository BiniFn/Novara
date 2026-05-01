package org.skepsun.kototoro.settings.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R

data class BackupsSettingsUiState(
    val isWebDavEnabled: Boolean,
    val backupOutputSummary: String,
    val isBackupOutputInvalid: Boolean,
    val backupFrequency: Float,
    val isPeriodicalTrimEnabled: Boolean,
    val periodicalBackupCount: Int,
    val lastBackupSummary: String?,
    val webDavServerUrl: String,
    val webDavUsername: String,
    val webDavPassword: String,
    val webDavRemotePath: String,
    val isWebDavCheckLoading: Boolean,
    val isWebDavAutoSyncEnabled: Boolean,
    val isWebDavAutoRestoreEnabled: Boolean,
    val isWebDavKeepLocalCopyEnabled: Boolean,
    val webDavLastActionSummary: String?,
    val isPolicyNoteVisible: Boolean,
)

@Composable
fun BackupsSettingsScreen(
    webDavTitle: String,
    backupRestoreTitle: String,
    state: BackupsSettingsUiState,
    snackbarHostState: SnackbarHostState,
    backupFrequencyOptions: List<SettingsChoiceOption<Float>>,
    onWebDavEnabledChange: (Boolean) -> Unit,
    onBackupOutputClick: () -> Unit,
    onBackupFrequencyChange: (Float) -> Unit,
    onPeriodicalTrimChange: (Boolean) -> Unit,
    onPeriodicalBackupCountChange: (Int) -> Unit,
    onWebDavServerUrlChange: (String) -> Unit,
    onWebDavUsernameChange: (String) -> Unit,
    onWebDavPasswordChange: (String) -> Unit,
    onWebDavRemotePathChange: (String) -> Unit,
    onWebDavTestClick: () -> Unit,
    onWebDavUploadNowClick: () -> Unit,
    onWebDavRestoreNowClick: () -> Unit,
    onWebDavAutoSyncChange: (Boolean) -> Unit,
    onWebDavAutoRestoreChange: (Boolean) -> Unit,
    onWebDavKeepLocalCopyChange: (Boolean) -> Unit,
    onCreateBackupClick: () -> Unit,
    onRestoreBackupClick: () -> Unit,
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
            item(key = "webdav") {
                SettingsPreferenceSection(title = webDavTitle) {
                    SettingsSwitchPreference(
                        title = stringResource(R.string.sync_webdav_enable),
                        checked = state.isWebDavEnabled,
                        summary = stringResource(R.string.sync_webdav_enable_summary),
                        onCheckedChange = onWebDavEnabledChange,
                    )
                    SettingsSectionDivider()
                    SettingsActionPreference(
                        title = stringResource(R.string.backups_output_directory),
                        summary = state.backupOutputSummary,
                        enabled = state.isWebDavEnabled,
                        iconRes = if (state.isBackupOutputInvalid) R.drawable.ic_info_outline else null,
                        onClick = onBackupOutputClick,
                    )
                    SettingsSectionDivider()
                    SettingsChoicePreference(
                        title = stringResource(R.string.backup_frequency),
                        value = state.backupFrequency,
                        options = backupFrequencyOptions,
                        enabled = state.isWebDavEnabled,
                        onValueChange = onBackupFrequencyChange,
                    )
                    SettingsSectionDivider()
                    SettingsSwitchPreference(
                        title = stringResource(R.string.delete_old_backups),
                        checked = state.isPeriodicalTrimEnabled,
                        summary = stringResource(R.string.delete_old_backups_summary),
                        enabled = state.isWebDavEnabled,
                        onCheckedChange = onPeriodicalTrimChange,
                    )
                    SettingsSectionDivider()
                    SettingsSliderPreference(
                        title = stringResource(R.string.max_backups_count),
                        value = state.periodicalBackupCount,
                        valueRange = 1..32,
                        step = 1,
                        enabled = state.isWebDavEnabled && state.isPeriodicalTrimEnabled,
                        valueText = { it.toString() },
                        onValueChange = onPeriodicalBackupCountChange,
                    )
                    state.lastBackupSummary?.let {
                        SettingsSectionDivider()
                        SettingsInfoPreference(
                            title = stringResource(R.string.create_backup),
                            summary = it,
                            iconRes = R.drawable.ic_info_outline,
                        )
                    }
                    SettingsSectionDivider()
                    SettingsTextInputPreference(
                        title = stringResource(R.string.webdav_server_url),
                        value = state.webDavServerUrl,
                        enabled = state.isWebDavEnabled,
                        placeholder = "https://example.com/dav",
                        onValueChange = onWebDavServerUrlChange,
                    )
                    SettingsSectionDivider()
                    SettingsTextInputPreference(
                        title = stringResource(R.string.webdav_username),
                        value = state.webDavUsername,
                        enabled = state.isWebDavEnabled,
                        placeholder = stringResource(R.string.username),
                        onValueChange = onWebDavUsernameChange,
                    )
                    SettingsSectionDivider()
                    SettingsTextInputPreference(
                        title = stringResource(R.string.webdav_password),
                        value = state.webDavPassword,
                        enabled = state.isWebDavEnabled,
                        isPassword = true,
                        onValueChange = onWebDavPasswordChange,
                    )
                    SettingsSectionDivider()
                    SettingsTextInputPreference(
                        title = stringResource(R.string.webdav_remote_path),
                        value = state.webDavRemotePath,
                        enabled = state.isWebDavEnabled,
                        placeholder = "/backup",
                        onValueChange = onWebDavRemotePathChange,
                    )
                    SettingsSectionDivider()
                    SettingsActionPreference(
                        title = stringResource(R.string.test_connection),
                        summary = stringResource(R.string.webdav_integration),
                        enabled = state.isWebDavEnabled && !state.isWebDavCheckLoading,
                        onClick = onWebDavTestClick,
                    )
                    SettingsSectionDivider()
                    SettingsActionPreference(
                        title = stringResource(R.string.webdav_upload_now),
                        summary = stringResource(R.string.create_backup),
                        enabled = state.isWebDavEnabled && !state.isWebDavCheckLoading,
                        onClick = onWebDavUploadNowClick,
                    )
                    SettingsSectionDivider()
                    SettingsActionPreference(
                        title = stringResource(R.string.webdav_restore_now),
                        summary = stringResource(R.string.restore_backup),
                        enabled = state.isWebDavEnabled && !state.isWebDavCheckLoading,
                        onClick = onWebDavRestoreNowClick,
                    )
                    SettingsSectionDivider()
                    SettingsSwitchPreference(
                        title = stringResource(R.string.webdav_auto_sync),
                        checked = state.isWebDavAutoSyncEnabled,
                        summary = stringResource(R.string.webdav_auto_sync_summary),
                        enabled = state.isWebDavEnabled,
                        onCheckedChange = onWebDavAutoSyncChange,
                    )
                    SettingsSectionDivider()
                    SettingsSwitchPreference(
                        title = stringResource(R.string.webdav_auto_restore),
                        checked = state.isWebDavAutoRestoreEnabled,
                        summary = stringResource(R.string.webdav_auto_restore_summary),
                        enabled = state.isWebDavEnabled,
                        onCheckedChange = onWebDavAutoRestoreChange,
                    )
                    SettingsSectionDivider()
                    SettingsSwitchPreference(
                        title = stringResource(R.string.webdav_keep_local_copy),
                        checked = state.isWebDavKeepLocalCopyEnabled,
                        summary = stringResource(R.string.webdav_keep_local_copy_summary),
                        enabled = state.isWebDavEnabled,
                        onCheckedChange = onWebDavKeepLocalCopyChange,
                    )
                    state.webDavLastActionSummary?.let {
                        SettingsSectionDivider()
                        SettingsInfoPreference(
                            title = stringResource(R.string.recent_webdav_action),
                            summary = it,
                        )
                    }
                    if (state.isPolicyNoteVisible) {
                        SettingsSectionDivider()
                        SettingsInfoPreference(
                            title = stringResource(R.string.read_more),
                            summary = stringResource(R.string.backup_periodic_explain_keep_local_copy_off),
                            iconRes = R.drawable.ic_info_outline,
                        )
                    }
                }
            }
            item(key = "backup_restore") {
                SettingsPreferenceSection(title = backupRestoreTitle) {
                    SettingsActionPreference(
                        title = stringResource(R.string.create_backup),
                        summary = stringResource(R.string.backup_information),
                        onClick = onCreateBackupClick,
                    )
                    SettingsSectionDivider()
                    SettingsActionPreference(
                        title = stringResource(R.string.restore_backup),
                        summary = stringResource(R.string.restore_summary),
                        onClick = onRestoreBackupClick,
                    )
                }
            }
        }
    }
}
