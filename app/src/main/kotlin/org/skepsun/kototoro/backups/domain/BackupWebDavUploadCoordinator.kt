package org.skepsun.kototoro.backups.domain

import org.skepsun.kototoro.backups.ui.periodical.WebDavBackupUploader
import org.skepsun.kototoro.core.prefs.AppSettings
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupWebDavUploadCoordinator @Inject constructor(
	private val settings: AppSettings,
	private val webDavBackupUploader: WebDavBackupUploader,
) {

	data class UploadCommitResult(
		val uploadedAt: Long,
		val targetVersion: Int,
		val uploadKind: String,
	)

	suspend fun uploadAndCommit(
		file: File,
		uploadKind: String,
		now: Long = System.currentTimeMillis(),
	): UploadCommitResult {
		val targetVersion = settings.backupWebDavDataVersion + 1
		webDavBackupUploader.uploadBackup(file, targetVersion = targetVersion)
		settings.backupWebDavLastUploadTime = now
		settings.backupWebDavLastUploadKind = uploadKind
		settings.backupWebDavDataVersion = targetVersion
		return UploadCommitResult(
			uploadedAt = now,
			targetVersion = targetVersion,
			uploadKind = uploadKind,
		)
	}
}
