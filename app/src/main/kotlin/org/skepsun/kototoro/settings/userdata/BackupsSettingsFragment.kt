package org.skepsun.kototoro.settings.userdata

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.backups.domain.BackupUtils
import org.skepsun.kototoro.backups.ui.backup.BackupService
import org.skepsun.kototoro.backups.ui.periodical.PeriodicalBackupSettingsViewModel
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.os.OpenDocumentTreeHelper
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.tryLaunch
import org.skepsun.kototoro.settings.utils.EditTextFallbackSummaryProvider
import java.util.Date

@AndroidEntryPoint
class BackupsSettingsFragment : BasePreferenceFragment(R.string.sync_settings) {

    private val viewModel by viewModels<PeriodicalBackupSettingsViewModel>()

    private val backupSelectCall = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            router.showBackupRestoreDialog(uri)
        }
    }

    private val backupCreateCall = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null && !BackupService.start(requireContext(), uri)) {
            Snackbar.make(
                listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT,
            ).show()
        }
    }

    private val outputSelectCall = OpenDocumentTreeHelper(this) { uri ->
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context?.contentResolver?.takePersistableUriPermission(uri, takeFlags)
            settings.periodicalBackupDirectory = uri
            viewModel.updateSummaryData()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_backups)
        findPreference<EditTextPreference>(AppSettings.KEY_BACKUP_WEBDAV_USERNAME)?.summaryProvider =
            EditTextFallbackSummaryProvider(R.string.username)
        findPreference<EditTextPreference>(AppSettings.KEY_BACKUP_WEBDAV_PATH)?.summaryProvider =
            EditTextFallbackSummaryProvider(R.string.webdav_remote_path)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.lastBackupDate.observe(viewLifecycleOwner, ::bindLastBackupInfo)
        viewModel.backupsDirectory.observe(viewLifecycleOwner, ::bindOutputSummary)
        viewModel.webDavLastAction.observe(viewLifecycleOwner, ::bindWebDavLastAction)
        viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(listView, this))
        viewModel.onActionDone.observeEvent(
            viewLifecycleOwner,
            org.skepsun.kototoro.core.ui.util.ReversibleActionObserver(listView),
        )
        viewModel.isWebDavCheckLoading.observe(viewLifecycleOwner) {
            findPreference<Preference>(AppSettings.KEY_BACKUP_WEBDAV_TEST)?.isEnabled = !it
            findPreference<Preference>(AppSettings.KEY_BACKUP_WEBDAV_UPLOAD_NOW)?.isEnabled = !it
            findPreference<Preference>(AppSettings.KEY_BACKUP_WEBDAV_RESTORE_NOW)?.isEnabled = !it
        }
        findPreference<androidx.preference.SwitchPreferenceCompat>(AppSettings.KEY_BACKUP_WEBDAV_KEEP_LOCAL_COPY)?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, _ ->
                updatePolicyNoteVisibility()
                true
            }
        findPreference<androidx.preference.SwitchPreferenceCompat>(AppSettings.KEY_BACKUP_WEBDAV_ENABLED)?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, _ ->
                updatePolicyNoteVisibility()
                true
            }
        updatePolicyNoteVisibility()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {
            AppSettings.KEY_BACKUP -> {
                if (!backupCreateCall.tryLaunch(BackupUtils.generateFileName(preference.context))) {
                    Snackbar.make(
                        listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT,
                    ).show()
                }
                true
            }

            AppSettings.KEY_RESTORE -> {
                if (!backupSelectCall.tryLaunch(arrayOf("*/*"))) {
                    Snackbar.make(
                        listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT,
                    ).show()
                }
                true
            }

            AppSettings.KEY_BACKUP_PERIODICAL_OUTPUT -> {
                if (!outputSelectCall.tryLaunch(null)) {
                    Snackbar.make(
                        listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT,
                    ).show()
                }
                true
            }

            AppSettings.KEY_BACKUP_WEBDAV_TEST -> {
                viewModel.checkWebDav()
                true
            }

            AppSettings.KEY_BACKUP_WEBDAV_UPLOAD_NOW -> {
                viewModel.uploadWebDavNow()
                true
            }

            AppSettings.KEY_BACKUP_WEBDAV_RESTORE_NOW -> {
                viewModel.restoreWebDavNow()
                true
            }

            else -> super.onPreferenceTreeClick(preference)
        }
    }

    private fun bindOutputSummary(path: String?) {
        val preference = findPreference<Preference>(AppSettings.KEY_BACKUP_PERIODICAL_OUTPUT) ?: return
        preference.summary = when (path) {
            null -> getString(R.string.invalid_value_message)
            "" -> null
            else -> path
        }
        preference.icon = if (path == null) getWarningIcon() else null
    }

    private fun bindLastBackupInfo(lastBackupDate: Date?) {
        val preference = findPreference<Preference>(AppSettings.KEY_BACKUP_PERIODICAL_LAST) ?: return
        val keepLocal = settings.isBackupWebDavKeepLocalCopyEnabled
        if (lastBackupDate != null) {
            preference.summary = getString(
                R.string.last_successful_backup,
                DateUtils.getRelativeTimeSpanString(lastBackupDate.time),
            )
            preference.isVisible = true
            preference.icon = null
        } else if (!keepLocal) {
            preference.summary = getString(R.string.backup_periodic_last_local_empty)
            preference.isVisible = true
            preference.icon = getWarningIcon()
        } else {
            preference.isVisible = false
            preference.icon = null
        }
    }

    private fun bindWebDavLastAction(action: Pair<Int, Long>?) {
        val preference = findPreference<Preference>(AppSettings.KEY_BACKUP_WEBDAV_LAST_ACTIONS) ?: return
        if (action == null) {
            preference.isVisible = false
            return
        }
        preference.title = getString(R.string.recent_webdav_action)
        preference.summary = getString(action.first) + " - " + DateUtils.getRelativeTimeSpanString(action.second)
        preference.isVisible = true
    }

    private fun updatePolicyNoteVisibility() {
        val pref = findPreference<Preference>(AppSettings.KEY_BACKUP_WEBDAV_POLICY_NOTE) ?: return
        pref.isVisible = !settings.isBackupWebDavKeepLocalCopyEnabled && settings.isBackupWebDavUploadEnabled
    }
}
