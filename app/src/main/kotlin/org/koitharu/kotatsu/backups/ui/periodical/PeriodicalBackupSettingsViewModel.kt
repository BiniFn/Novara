package org.koitharu.kotatsu.backups.ui.periodical

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backups.domain.BackupUtils
import org.koitharu.kotatsu.backups.domain.ExternalBackupStorage
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.ui.util.ReversibleAction
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.core.util.ext.resolveFile
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class PeriodicalBackupSettingsViewModel @Inject constructor(
	private val settings: AppSettings,
	private val telegramUploader: TelegramBackupUploader,
	private val webDavUploader: WebDavBackupUploader,
	private val backupStorage: ExternalBackupStorage,
	private val repository: org.koitharu.kotatsu.backups.data.BackupRepository,
	@ApplicationContext private val appContext: Context,
) : BaseViewModel() {

	val isTelegramAvailable
		get() = telegramUploader.isAvailable

	val lastBackupDate = MutableStateFlow<Date?>(null)
	val backupsDirectory = MutableStateFlow<String?>("")
	val isTelegramCheckLoading = MutableStateFlow(false)
	val isWebDavCheckLoading = MutableStateFlow(false)
	val onActionDone = MutableEventFlow<ReversibleAction>()

	// 最近一次 WebDAV 操作（类型文案资源ID，发生时间毫秒）
	val webDavLastAction = MutableStateFlow<Pair<Int, Long>?>(null)

	init {
		updateSummaryData()
	}

	fun checkTelegram() {
		launchJob(Dispatchers.Default) {
			try {
				isTelegramCheckLoading.value = true
				telegramUploader.sendTestMessage()
				onActionDone.call(ReversibleAction(R.string.connection_ok, null))
			} finally {
				isTelegramCheckLoading.value = false
			}
		}
	}

	fun checkWebDav() {
		launchJob(Dispatchers.Default) {
			try {
				isWebDavCheckLoading.value = true
				webDavUploader.sendTestConnection()
				onActionDone.call(ReversibleAction(R.string.connection_ok, null))
			} finally {
				isWebDavCheckLoading.value = false
			}
		}
	}

	fun uploadWebDavNow() {
		launchJob(Dispatchers.Default) {
			val output = org.koitharu.kotatsu.backups.domain.BackupUtils.createTempFile(appContext)
			try {
				java.util.zip.ZipOutputStream(output.outputStream()).use {
					repository.createBackup(it, null)
				}
				// 根据设置决定是否保留本地副本
				if (settings.isBackupWebDavKeepLocalCopyEnabled) {
					// 保存到本地外部备份目录，便于用户查看与修剪
					backupStorage.put(output)
					backupStorage.trim(settings.periodicalBackupMaxCount)
				}
                // 上传到 WebDAV（使用下一个版本号命名）
                val nextVersion = settings.backupWebDavDataVersion + 1
                webDavUploader.uploadBackup(output, targetVersion = nextVersion)
                onActionDone.call(ReversibleAction(R.string.webdav_upload_success, null))
                settings.backupWebDavLastUploadTime = System.currentTimeMillis()
                settings.backupWebDavLastUploadKind = "manual"
                // 手动上传成功后自增数据版本
                settings.backupWebDavDataVersion = nextVersion
                // 仅在保留本地副本时，更新上次本地备份时间展示
                if (settings.isBackupWebDavKeepLocalCopyEnabled) {
                    updateLastBackupDate()
                }
                updateWebDavLastAction()
			} catch (e: Exception) {
				errorEvent.call(e)
			} finally {
				output.delete()
			}
		}
	}

	fun restoreWebDavNow() {
		launchJob(Dispatchers.Default) {
			try {
				val latest = webDavUploader.getLatestBackup()
				if (latest == null) {
					throw IllegalStateException("No WebDAV backups found")
				}
				val tempFile = java.io.File.createTempFile("webdav_backup_manual", ".bk.zip", appContext.cacheDir)
				try {
					webDavUploader.downloadBackup(latest.name, tempFile)
					val allSections = setOf(
						org.koitharu.kotatsu.backups.domain.BackupSection.HISTORY,
						org.koitharu.kotatsu.backups.domain.BackupSection.CATEGORIES,
						org.koitharu.kotatsu.backups.domain.BackupSection.FAVOURITES,
						org.koitharu.kotatsu.backups.domain.BackupSection.BOOKMARKS,
						org.koitharu.kotatsu.backups.domain.BackupSection.SOURCES,
						org.koitharu.kotatsu.backups.domain.BackupSection.SETTINGS,
					)
					java.util.zip.ZipInputStream(java.io.FileInputStream(tempFile)).use { zis ->
						repository.restoreBackup(zis, allSections, null)
					}
					onActionDone.call(ReversibleAction(R.string.webdav_restore_success, null))
					settings.backupWebDavLastManualRestoreTime = System.currentTimeMillis()
					updateWebDavLastAction()
				} finally {
					if (tempFile.exists()) tempFile.delete()
				}
			} catch (e: Exception) {
				errorEvent.call(e)
			}
		}
	}

	fun updateSummaryData() {
		updateBackupsDirectory()
		updateLastBackupDate()
		updateWebDavLastAction()
	}

	private fun updateBackupsDirectory() = launchJob(Dispatchers.Default) {
		val dir = settings.periodicalBackupDirectory
		backupsDirectory.value = if (dir != null) {
			dir.toUserFriendlyString()
		} else {
			BackupUtils.getAppBackupDir(appContext).path
		}
	}

	private fun updateLastBackupDate() = launchJob(Dispatchers.Default) {
		lastBackupDate.value = backupStorage.getLastBackupDate()
	}

	private fun updateWebDavLastAction() = launchJob(Dispatchers.Default) {
		val upload = settings.backupWebDavLastUploadTime
		val autoRestore = settings.backupWebDavLastRestoreTime
		val manualRestore = settings.backupWebDavLastManualRestoreTime
		val max = listOf(upload, autoRestore, manualRestore).maxOrNull() ?: 0L
		val label = when (max) {
			upload -> when (settings.backupWebDavLastUploadKind) {
				"manual" -> R.string.action_manual_upload
				else -> R.string.action_auto_upload
			}
			autoRestore -> R.string.action_auto_restore
			manualRestore -> R.string.action_manual_restore
			else -> null
		}
		webDavLastAction.value = label?.let { it to max }
	}

	private fun Uri.toUserFriendlyString(): String? {
		val df = DocumentFile.fromTreeUri(appContext, this)
		if (df?.canWrite() != true) {
			return null
		}
		return resolveFile(appContext)?.path ?: toString()
	}
}
