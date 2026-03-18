package org.skepsun.kototoro.backups.domain

import org.skepsun.kototoro.core.prefs.AppSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupWebDavRestoreCoordinator @Inject constructor(
	private val settings: AppSettings,
) {

	data class RestoreCommitResult(
		val restoredAt: Long,
		val restoredVersion: Int?,
		val effectiveDataVersion: Int,
		val restoreKind: String,
	)

	fun commitAutoRestore(
		restoredVersion: Int?,
		now: Long = System.currentTimeMillis(),
	): RestoreCommitResult {
		settings.backupWebDavLastRestoreTime = now
		val effectiveDataVersion = mergeRestoredVersion(restoredVersion)
		return RestoreCommitResult(
			restoredAt = now,
			restoredVersion = restoredVersion,
			effectiveDataVersion = effectiveDataVersion,
			restoreKind = "auto",
		)
	}

	fun commitManualRestore(
		now: Long = System.currentTimeMillis(),
	): RestoreCommitResult {
		settings.backupWebDavLastManualRestoreTime = now
		return RestoreCommitResult(
			restoredAt = now,
			restoredVersion = null,
			effectiveDataVersion = settings.backupWebDavDataVersion,
			restoreKind = "manual",
		)
	}

	private fun mergeRestoredVersion(restoredVersion: Int?): Int {
		val currentVersion = settings.backupWebDavDataVersion
		if (restoredVersion != null && restoredVersion > currentVersion) {
			settings.backupWebDavDataVersion = restoredVersion
			return restoredVersion
		}
		return currentVersion
	}
}
