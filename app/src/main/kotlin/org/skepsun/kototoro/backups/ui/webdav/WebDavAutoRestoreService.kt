package org.skepsun.kototoro.backups.ui.webdav

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.backups.data.BackupRepository
import org.skepsun.kototoro.backups.domain.BackupSection
import org.skepsun.kototoro.backups.ui.BaseBackupRestoreService
import org.skepsun.kototoro.backups.ui.periodical.WebDavBackupUploader
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
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
		// 创建前台服务通知
		val notification = NotificationCompat.Builder(this, BaseBackupRestoreService.CHANNEL_ID)
			.setContentTitle(getString(R.string.webdav_auto_restore))
			.setContentText(getString(R.string.checking_for_backups))
			.setSmallIcon(R.drawable.ic_backup_restore)
			.setOngoing(true)
			.setSilent(true)
			.build()

		startForeground(NOTIFICATION_ID, notification)

		// 遵循开关并校验 WebDAV 配置有效性，避免错误
		if (!settings.isBackupWebDavAutoRestoreEnabled ||
			settings.backupWebDavServerUrl.isNullOrBlank() ||
			settings.backupWebDavUsername.isNullOrBlank() ||
			settings.backupWebDavPassword.isNullOrBlank()
		) {
			stopSelf()
			return START_NOT_STICKY
		}

		// 检查策略：仅当每天第一次启动时执行（比较日期）
		val lastCheck = settings.backupWebDavLastAutoRestoreCheckTime
		val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
		if (lastCheck > 0 && df.format(Date(lastCheck)) == df.format(Date())) {
			Log.d(TAG, "Auto restore check already performed today; skipping")
			stopSelf()
			return START_NOT_STICKY
		}

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
                // 自动恢复不包含 SETTINGS，以免覆盖本地偏好（例如阅读器横屏双页开关）
                // 手动恢复仍可包含 SETTINGS（见 PeriodicalBackupSettingsViewModel.restoreWebDavNow）
                val allSections = setOf(
                    BackupSection.HISTORY,
                    BackupSection.CATEGORIES,
                    BackupSection.FAVOURITES,
                    BackupSection.BOOKMARKS,
                    BackupSection.SOURCES
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

                // 仅在“合并后的本地备份”与“拉取的远端备份”内容不同的情况下，上传一次
                kotlin.runCatching {
                    val out = File.createTempFile("webdav_backup_post_restore", ".bk.zip", this.cacheDir)
                    try {
                        java.util.zip.ZipOutputStream(out.outputStream()).use { zos ->
                            backupRepository.createBackup(zos, null)
                        }
                        val isSame = areBackupsEqual(tempFile, out)
                        if (!isSame) {
                            val nextVersion = settings.backupWebDavDataVersion + 1
                            webDavUploader.uploadBackup(out, targetVersion = nextVersion)
                            settings.backupWebDavLastUploadTime = System.currentTimeMillis()
                            settings.backupWebDavLastUploadKind = "auto"
                            settings.backupWebDavDataVersion = nextVersion
                            Log.d(TAG, "Post-restore upload completed (content differed)")
                        } else {
                            Log.d(TAG, "Post-restore upload skipped (content identical)")
                        }
                    } finally {
                        out.delete()
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Post-restore comparison/upload failed", e)
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

    /**
     * 比较两个备份 zip 文件的实际内容是否相同。
     * 逐项读取每个条目（entry）内容并计算 SHA-256 摘要，按文件名比对。
     */
    private fun areBackupsEqual(fileA: File, fileB: File): Boolean {
        fun digestOfZip(file: File): Map<String, String> {
            val map = mutableMapOf<String, String>()
            ZipInputStream(FileInputStream(file)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        // 读取内容并计算摘要
                        val md = java.security.MessageDigest.getInstance("SHA-256")
                        val buf = ByteArray(8192)
                        var read: Int
                        while (true) {
                            read = zis.read(buf)
                            if (read <= 0) break
                            md.update(buf, 0, read)
                        }
                        map[entry.name] = md.digest().joinToString(separator = "") { b ->
                            val i = (b.toInt() and 0xFF)
                            i.toString(16).padStart(2, '0')
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            return map
        }

        val a = digestOfZip(fileA)
        val b = digestOfZip(fileB)
        if (a.size != b.size) return false
        for ((name, hashA) in a) {
            val hashB = b[name] ?: return false
            if (hashA != hashB) return false
        }
        return true
    }
}