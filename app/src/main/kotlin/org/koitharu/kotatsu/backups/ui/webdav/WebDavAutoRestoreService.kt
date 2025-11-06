package org.koitharu.kotatsu.backups.ui.webdav

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backups.data.BackupRepository
import org.koitharu.kotatsu.backups.domain.BackupSection
import org.koitharu.kotatsu.backups.ui.BaseBackupRestoreService
import org.koitharu.kotatsu.backups.ui.periodical.WebDavBackupUploader
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject

@AndroidEntryPoint
class WebDavAutoRestoreService : Service() {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var backupRepository: BackupRepository

	@Inject
	lateinit var webDavUploader: WebDavBackupUploader

	private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	override fun onCreate() {
		super.onCreate()
		BaseBackupRestoreService.createNotificationChannel(this)
	}

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		if (!settings.isBackupWebDavAutoRestoreEnabled) {
			stopSelf()
			return START_NOT_STICKY
		}

		// 创建前台服务通知
		val notification = NotificationCompat.Builder(this, BaseBackupRestoreService.CHANNEL_ID)
			.setContentTitle(getString(R.string.webdav_auto_restore))
			.setContentText(getString(R.string.checking_for_backups))
			.setSmallIcon(R.drawable.ic_backup_restore)
			.setOngoing(true)
			.setSilent(true)
			.build()

		startForeground(NOTIFICATION_ID, notification)

		serviceScope.launch {
			try {
				performAutoRestore()
			} catch (e: Exception) {
				Log.e(TAG, "Auto restore failed", e)
				e.printStackTraceDebug()
			} finally {
				stopSelf()
			}
		}

		return START_NOT_STICKY
	}

	override fun onDestroy() {
		super.onDestroy()
		serviceScope.cancel()
	}

    private suspend fun performAutoRestore() {
        val currentTime = System.currentTimeMillis()

        Log.d(TAG, "Starting WebDAV auto restore check")

        try {
            // 拉取远端文件列表并按数据版本选择
            val remoteFiles = webDavUploader.listBackupFiles()
            if (remoteFiles.isEmpty()) {
                Log.d(TAG, "No backup files found on WebDAV server")
                return
            }

            // 强制策略：每次都选择“最新版本”的备份（若无法解析版本则选最新时间）
            val highestVersionItem = remoteFiles.filter { it.dataVersion != null }
                .maxByOrNull { it.dataVersion!! }
            val candidate = if (highestVersionItem != null) {
                // 若同一版本存在多个文件，取修改时间最新的一个
                remoteFiles.filter { it.dataVersion == highestVersionItem.dataVersion }
                    .maxByOrNull { it.lastModified } ?: highestVersionItem
            } else {
                remoteFiles.maxByOrNull { it.lastModified }!!
            }

            Log.d(TAG, "Selected backup: ${candidate.name}, modified: ${candidate.lastModified}, dataVersion: ${candidate.dataVersion}")

            // 下载并恢复备份
            val tempFile = File.createTempFile("webdav_backup", ".bk.zip", this.cacheDir)
            try {
                Log.d(TAG, "Downloading backup file: ${candidate.name}")
                webDavUploader.downloadBackup(candidate.name, tempFile)

                Log.d(TAG, "Restoring backup from: ${tempFile.absolutePath}")
                val zipInputStream = ZipInputStream(FileInputStream(tempFile))
                val allSections = setOf(
                    BackupSection.HISTORY,
                    BackupSection.CATEGORIES,
                    BackupSection.FAVOURITES,
                    BackupSection.BOOKMARKS,
                    BackupSection.SOURCES,
                    BackupSection.SETTINGS
                )

                val restoreResult = zipInputStream.use { zis ->
                    backupRepository.restoreBackup(zis, allSections, null)
                }

                val changesApplied = !restoreResult.isEmpty

                // 更新最后恢复时间与本地数据版本（不降低版本）
                settings.backupWebDavLastRestoreTime = currentTime
                candidate.dataVersion?.let { v ->
                    if (v > settings.backupWebDavDataVersion) {
                        settings.backupWebDavDataVersion = v
                    }
                }
                Log.d(TAG, "WebDAV auto restore completed successfully")

                // 仅在实际合并有改动时触发备份上传
                if (changesApplied && settings.isBackupWebDavUploadEnabled) {
                    if (settings.isBackupWebDavAutoSyncEnabled) {
                        // 开启自动同步时，数据库变更会被监听并自动上传，这里不重复上传
                        Log.d(TAG, "Changes applied; auto-sync enabled, relying on DataSyncManager upload")
                    } else {
                        // 未开启自动同步时，显式备份并上传一次
                        kotlin.runCatching {
                            val out = File.createTempFile("webdav_backup_post_restore", ".bk.zip", this.cacheDir)
                            try {
                                java.util.zip.ZipOutputStream(out.outputStream()).use { zos ->
                                    backupRepository.createBackup(zos, null)
                                }
                                val nextVersion = settings.backupWebDavDataVersion + 1
                                webDavUploader.uploadBackup(out, targetVersion = nextVersion)
                                settings.backupWebDavLastUploadTime = System.currentTimeMillis()
                                settings.backupWebDavLastUploadKind = "auto"
                                settings.backupWebDavDataVersion = nextVersion
                                Log.d(TAG, "Post-restore re-upload completed")
                            } finally {
                                out.delete()
                            }
                        }.onFailure { e ->
                            Log.e(TAG, "Post-restore re-upload failed", e)
                        }
                    }
                }

            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform auto restore", e)
            throw e
        }

        // 记录本次检查的时间（不再用于节流，仅用于状态展示）
        settings.backupWebDavLastAutoRestoreCheckTime = currentTime
    }

	companion object {
		private const val TAG = "WebDavAutoRestore"
		private const val NOTIFICATION_ID = 2001

		fun start(context: Context) {
			val intent = Intent(context, WebDavAutoRestoreService::class.java)
			ContextCompat.startForegroundService(context, intent)
		}
	}
}