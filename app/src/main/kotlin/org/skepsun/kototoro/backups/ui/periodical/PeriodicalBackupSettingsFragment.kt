package org.skepsun.kototoro.backups.ui.periodical

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultCallback
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.os.OpenDocumentTreeHelper
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.ui.util.ReversibleActionObserver
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.tryLaunch
import org.skepsun.kototoro.settings.SettingsActivity
import org.skepsun.kototoro.settings.compose.PeriodicalBackupSettingsScreen
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class PeriodicalBackupSettingsFragment : Fragment(), ActivityResultCallback<Uri?> {

    @Inject
    lateinit var telegramBackupUploader: TelegramBackupUploader

    @Inject
    lateinit var appSettings: AppSettings

    private val viewModel by viewModels<PeriodicalBackupSettingsViewModel>()

    private val outputSelectCall = OpenDocumentTreeHelper(this, this)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val lastBackupDate by viewModel.lastBackupDate.collectAsState(initial = null)
                val backupsDirectory by viewModel.backupsDirectory.collectAsState(initial = null)
                val webDavLastAction by viewModel.webDavLastAction.collectAsState(initial = null)
                val isTelegramCheckLoading by viewModel.isTelegramCheckLoading.collectAsState(initial = false)
                val isWebDavCheckLoading by viewModel.isWebDavCheckLoading.collectAsState(initial = false)

                val keepLocal = appSettings.isBackupWebDavKeepLocalCopyEnabled
                val isLastBackupVisible = lastBackupDate != null || !keepLocal
                val isLastBackupError = lastBackupDate == null && !keepLocal
                val lastBackupSummary = if (lastBackupDate != null) {
                    getString(R.string.last_successful_backup, DateUtils.getRelativeTimeSpanString(lastBackupDate!!.time))
                } else if (!keepLocal) {
                    getString(R.string.backup_periodic_last_local_empty)
                } else {
                    null
                }

                val outputSummary = when (backupsDirectory) {
                    null -> getString(R.string.invalid_value_message)
                    "" -> null
                    else -> backupsDirectory
                }

                val webDavLastActionText = webDavLastAction?.let { action ->
                    "${getString(action.first)} • ${DateUtils.getRelativeTimeSpanString(action.second)}"
                }

                KototoroTheme {
                    PeriodicalBackupSettingsScreen(
                        settings = appSettings,
                        outputSummary = outputSummary,
                        isOutputError = backupsDirectory == null,
                        lastBackupSummary = lastBackupSummary,
                        isLastBackupVisible = isLastBackupVisible,
                        isLastBackupError = isLastBackupError,
                        isTelegramAvailable = viewModel.isTelegramAvailable,
                        isTelegramCheckLoading = isTelegramCheckLoading,
                        isWebDavCheckLoading = isWebDavCheckLoading,
                        webDavLastActionText = webDavLastActionText,
                        onOutputClick = { outputSelectCall.tryLaunch(null) },
                        onTelegramOpenClick = { telegramBackupUploader.openBotInApp(router) },
                        onTelegramTestClick = { viewModel.checkTelegram() },
                        onWebDavTestClick = { viewModel.checkWebDav() },
                        onWebDavUploadClick = { viewModel.uploadWebDavNow() },
                        onWebDavRestoreClick = { viewModel.restoreWebDavNow() },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.periodic_backups))
        
        viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(view, this))
        viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(view))
    }

    override fun onActivityResult(result: Uri?) {
        if (result != null) {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context?.contentResolver?.takePersistableUriPermission(result, takeFlags)
            appSettings.periodicalBackupDirectory = result
            viewModel.updateSummaryData()
        }
    }
}
