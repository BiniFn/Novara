package org.skepsun.kototoro.backups.domain

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.skepsun.kototoro.backups.ui.periodical.PeriodicalBackupService
import org.skepsun.kototoro.backups.ui.webdav.DataSyncManager
import org.skepsun.kototoro.backups.ui.webdav.WebDavAutoRestoreService
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.util.BackupFlow
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.logBackupFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupStartupCoordinator @Inject constructor(
	@ApplicationContext private val appContext: Context,
	private val settings: AppSettings,
	private val dataSyncManager: DataSyncManager,
) {

	fun startOnFirstLaunch(scope: CoroutineScope) {
		startPeriodicalBackupService()
		startAutoSyncObserver()
		scheduleAutoRestore(scope)
	}

	private fun startPeriodicalBackupService() {
		logBackupFlow(TAG, flow = BackupFlow.PERIODICAL_BACKUP, event = "startup_requested")
		runCatching {
			appContext.startService(Intent(appContext, PeriodicalBackupService::class.java))
		}.onFailure {
			logBackupFlow(TAG, flow = BackupFlow.PERIODICAL_BACKUP, event = "startup_failed", reason = it::class.java.simpleName)
			it.printStackTraceDebug()
		}
	}

	private fun startAutoSyncObserver() {
		logBackupFlow(TAG, flow = BackupFlow.WEBDAV_AUTO_SYNC_UPLOAD, event = "observer_start_requested")
		runCatching {
			dataSyncManager.start()
		}.onFailure {
			logBackupFlow(TAG, flow = BackupFlow.WEBDAV_AUTO_SYNC_UPLOAD, event = "observer_start_failed", reason = it::class.java.simpleName)
			it.printStackTraceDebug()
		}
	}

	private fun scheduleAutoRestore(scope: CoroutineScope) {
		if (!hasCompleteAutoRestoreConfig()) {
			logBackupFlow(
				TAG,
				flow = BackupFlow.WEBDAV_AUTO_RESTORE,
				event = "startup_skipped",
				reason = "feature_disabled_or_incomplete_config",
			)
			return
		}
		logBackupFlow(
			TAG,
			flow = BackupFlow.WEBDAV_AUTO_RESTORE,
			event = "startup_scheduled",
			reason = null,
			"delayMs" to AUTO_RESTORE_START_DELAY_MS,
		)
		scope.launch {
			delay(AUTO_RESTORE_START_DELAY_MS)
			runCatching {
				WebDavAutoRestoreService.start(appContext)
				logBackupFlow(TAG, flow = BackupFlow.WEBDAV_AUTO_RESTORE, event = "startup_requested")
			}.onFailure {
				logBackupFlow(TAG, flow = BackupFlow.WEBDAV_AUTO_RESTORE, event = "startup_failed", reason = it::class.java.simpleName)
				it.printStackTraceDebug()
			}
		}
	}

	private fun hasCompleteAutoRestoreConfig(): Boolean {
		return settings.isBackupWebDavAutoRestoreEnabled &&
			!settings.backupWebDavServerUrl.isNullOrBlank() &&
			!settings.backupWebDavUsername.isNullOrBlank() &&
			!settings.backupWebDavPassword.isNullOrBlank()
	}

	private companion object {
		private const val TAG = "BackupStartupCoordinator"
		private const val AUTO_RESTORE_START_DELAY_MS = 3000L
	}
}
