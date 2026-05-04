package org.skepsun.kototoro.settings

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import org.skepsun.kototoro.backups.ui.periodical.PeriodicalBackupSettingsViewModel
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.util.ext.getDisplayMessage
import org.skepsun.kototoro.settings.compose.SyncSettingsScreen
import org.skepsun.kototoro.settings.compose.SyncSettingsUiState
import org.skepsun.kototoro.sync.data.SyncSettings
import org.skepsun.kototoro.sync.ui.SyncHostDialogFragment
import javax.inject.Inject

@AndroidEntryPoint
class SyncSettingsFragment : Fragment(), FragmentResultListener {

    @Inject
    lateinit var appSettings: AppSettings

    @Inject
    lateinit var syncSettings: SyncSettings

    private val backupSettingsViewModel by viewModels<PeriodicalBackupSettingsViewModel>()

    private val syncUrlFlow = MutableStateFlow("")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        syncUrlFlow.value = syncSettings.syncUrl ?: ""
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KototoroTheme {
                    SyncSettingsRoute(
                        settings = appSettings,
                        backupSettingsViewModel = backupSettingsViewModel,
                        syncUrlFlow = syncUrlFlow,
                        onSyncUrlClick = { SyncHostDialogFragment.show(childFragmentManager, null) },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.sync_settings))
        childFragmentManager.setFragmentResultListener(SyncHostDialogFragment.REQUEST_KEY, viewLifecycleOwner, this)
    }

    override fun onFragmentResult(requestKey: String, result: Bundle) {
        syncUrlFlow.value = syncSettings.syncUrl ?: ""
    }
}

@Composable
fun SyncSettingsRoute(
    settings: AppSettings,
    backupSettingsViewModel: PeriodicalBackupSettingsViewModel,
    syncUrlFlow: MutableStateFlow<String>,
    onSyncUrlClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val syncUrl by syncUrlFlow.collectAsState()
    val webDavLastAction = backupSettingsViewModel.webDavLastAction.collectAsStateWithLifecycle().value
    val isWebDavCheckLoading = backupSettingsViewModel.isWebDavCheckLoading.collectAsStateWithLifecycle().value
    val isWebDavEnabled =
        settings.observeAsState(AppSettings.KEY_BACKUP_WEBDAV_ENABLED) { isBackupWebDavUploadEnabled }.value
    val webDavServerUrl =
        settings.observeAsState(AppSettings.KEY_BACKUP_WEBDAV_URL) { backupWebDavServerUrl.orEmpty() }.value
    val webDavUsername =
        settings.observeAsState(AppSettings.KEY_BACKUP_WEBDAV_USERNAME) { backupWebDavUsername.orEmpty() }.value
    val webDavPassword =
        settings.observeAsState(AppSettings.KEY_BACKUP_WEBDAV_PASSWORD) { backupWebDavPassword.orEmpty() }.value
    val webDavRemotePath =
        settings.observeAsState(AppSettings.KEY_BACKUP_WEBDAV_PATH) { backupWebDavRemotePath.orEmpty() }.value
    val isWebDavAutoSyncEnabled =
        settings.observeAsState(AppSettings.KEY_BACKUP_WEBDAV_AUTO_SYNC) { isBackupWebDavAutoSyncEnabled }.value
    val isWebDavAutoRestoreEnabled =
        settings.observeAsState(AppSettings.KEY_BACKUP_WEBDAV_AUTO_RESTORE) { isBackupWebDavAutoRestoreEnabled }.value
    val isWebDavKeepLocalCopyEnabled =
        settings.observeAsState(AppSettings.KEY_BACKUP_WEBDAV_KEEP_LOCAL_COPY) { isBackupWebDavKeepLocalCopyEnabled }.value
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(backupSettingsViewModel.onError, context, snackbarHostState) {
        backupSettingsViewModel.onError.collect { event ->
            event?.consume { error ->
                snackbarHostState.showSnackbar(error.getDisplayMessage(context.resources))
            }
        }
    }
    LaunchedEffect(backupSettingsViewModel.onActionDone, context, snackbarHostState) {
        backupSettingsViewModel.onActionDone.collect { event ->
            event?.consume { action ->
                snackbarHostState.showSnackbar(context.getString(action.stringResId))
            }
        }
    }

    val webDavLastActionSummary = webDavLastAction?.let {
        context.getString(it.first) + " - " + DateUtils.getRelativeTimeSpanString(it.second)
    }
    SyncSettingsScreen(
        settings = settings,
        state = SyncSettingsUiState(
            syncUrl = syncUrl,
            isWebDavEnabled = isWebDavEnabled,
            webDavServerUrl = webDavServerUrl,
            webDavUsername = webDavUsername,
            webDavPassword = webDavPassword,
            webDavRemotePath = webDavRemotePath,
            isWebDavCheckLoading = isWebDavCheckLoading,
            isWebDavAutoSyncEnabled = isWebDavAutoSyncEnabled,
            isWebDavAutoRestoreEnabled = isWebDavAutoRestoreEnabled,
            isWebDavKeepLocalCopyEnabled = isWebDavKeepLocalCopyEnabled,
            webDavLastActionSummary = webDavLastActionSummary,
            isPolicyNoteVisible = !isWebDavKeepLocalCopyEnabled && isWebDavEnabled,
        ),
        snackbarHostState = snackbarHostState,
        onSyncUrlClick = onSyncUrlClick,
        onWebDavEnabledChange = { settings.isBackupWebDavUploadEnabled = it },
        onWebDavServerUrlChange = { settings.backupWebDavServerUrl = it },
        onWebDavUsernameChange = { settings.backupWebDavUsername = it },
        onWebDavPasswordChange = { settings.backupWebDavPassword = it },
        onWebDavRemotePathChange = { settings.backupWebDavRemotePath = it },
        onWebDavTestClick = { backupSettingsViewModel.checkWebDav() },
        onWebDavUploadNowClick = { backupSettingsViewModel.uploadWebDavNow() },
        onWebDavRestoreNowClick = { backupSettingsViewModel.restoreWebDavNow() },
        onWebDavAutoSyncChange = { settings.isBackupWebDavAutoSyncEnabled = it },
        onWebDavAutoRestoreChange = { settings.isBackupWebDavAutoRestoreEnabled = it },
        onWebDavKeepLocalCopyChange = { settings.isBackupWebDavKeepLocalCopyEnabled = it },
        modifier = modifier,
    )
}
