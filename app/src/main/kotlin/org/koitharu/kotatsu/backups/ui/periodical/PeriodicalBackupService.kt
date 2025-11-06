package org.koitharu.kotatsu.backups.ui.periodical
import android.util.Log

import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backups.data.BackupRepository
import org.koitharu.kotatsu.backups.domain.BackupUtils
import org.koitharu.kotatsu.backups.domain.ExternalBackupStorage
import org.koitharu.kotatsu.backups.ui.BaseBackupRestoreService
import org.koitharu.kotatsu.core.ErrorReporterReceiver
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.CoroutineIntentService
import org.koitharu.kotatsu.core.util.ext.checkNotificationPermission
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class PeriodicalBackupService : CoroutineIntentService() {

	@Inject
	lateinit var externalBackupStorage: ExternalBackupStorage

	@Inject
	lateinit var telegramBackupUploader: TelegramBackupUploader

	@Inject
	lateinit var webDavBackupUploader: WebDavBackupUploader

	@Inject
	lateinit var repository: BackupRepository

	@Inject
	lateinit var settings: AppSettings

	override suspend fun IntentJobContext.processIntent(intent: Intent) {
	// 当启用周期性备份时，如果仅上传到远端（不保留本地副本），也应执行；
	// 因此这里不再强制要求本地目录存在，而是判断是否至少有一个目的地。
	if (!settings.isPeriodicalBackupEnabled) {
		return
	}
	val hasLocalCopyDestination = settings.isBackupWebDavKeepLocalCopyEnabled && settings.periodicalBackupDirectory != null
	val hasTelegramDestination = settings.isBackupTelegramUploadEnabled && telegramBackupUploader.isAvailable
	val hasWebDavDestination = settings.isBackupWebDavUploadEnabled
	if (!hasLocalCopyDestination && !hasTelegramDestination && !hasWebDavDestination) {
		// 没有任何可用的备份目的地，跳过
		Log.d(TAG, "No backup destinations available; skipping")
		return
	}

	// 频率判定：如果保留本地副本，则参考本地最近一次备份时间；
	// 如果不保留本地副本但启用了 WebDAV 上传，则参考最近一次 WebDAV 上传时间。
	val localLast = if (hasLocalCopyDestination) externalBackupStorage.getLastBackupDate()?.time else null
	val webDavLast = if (hasWebDavDestination) settings.backupWebDavLastUploadTime else 0L
	val effectiveLast = listOfNotNull(localLast, webDavLast.takeIf { it > 0L }).maxOrNull() ?: 0L
	if (effectiveLast > 0L && effectiveLast + settings.periodicalBackupFrequencyMillis > System.currentTimeMillis()) {
		return
	}

		val output = BackupUtils.createTempFile(applicationContext)
		try {
			ZipOutputStream(output.outputStream()).use {
				repository.createBackup(it, null)
			}
			// 仅在启用了“保留本地副本”且配置了目录时写入本地
			if (hasLocalCopyDestination) {
				externalBackupStorage.put(output)
				externalBackupStorage.trim(settings.periodicalBackupMaxCount)
			}
			if (settings.isBackupTelegramUploadEnabled && telegramBackupUploader.isAvailable) {
				telegramBackupUploader.uploadBackup(output)
			}
            if (settings.isBackupWebDavUploadEnabled) {
                val nextVersion = settings.backupWebDavDataVersion + 1
                webDavBackupUploader.uploadBackup(output, targetVersion = nextVersion)
                // 记录最近一次 WebDAV 上传时间，用于频率判定与最近操作展示
                settings.backupWebDavLastUploadTime = System.currentTimeMillis()
                settings.backupWebDavLastUploadKind = "auto"
                // 定时备份中的 WebDAV 上传成功后也自增数据版本
                settings.backupWebDavDataVersion = nextVersion
            }
        } finally {
            output.delete()
        }
    }

	override fun IntentJobContext.onError(error: Throwable) {
		if (!applicationContext.checkNotificationPermission(CHANNEL_ID)) {
			return
		}
		BaseBackupRestoreService.createNotificationChannel(applicationContext)
		val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setDefaults(0)
			.setSilent(true)
			.setAutoCancel(true)
		val title = getString(R.string.periodic_backups)
		val message = getString(
			R.string.inline_preference_pattern,
			getString(R.string.packup_creation_failed),
			error.getDisplayMessage(resources),
		)
		notification
			.setContentText(message)
			.setSmallIcon(android.R.drawable.stat_notify_error)
			.setStyle(
				NotificationCompat.BigTextStyle()
					.bigText(message)
					.setSummaryText(getString(R.string.packup_creation_failed))
					.setBigContentTitle(title),
			)
		ErrorReporterReceiver.getNotificationAction(applicationContext, error, startId, TAG)?.let { action ->
			notification.addAction(action)
		}
		notification.setContentIntent(
			PendingIntentCompat.getActivity(
				applicationContext,
				0,
				AppRouter.periodicBackupSettingsIntent(applicationContext),
				0,
				false,
			),
		)
		NotificationManagerCompat.from(applicationContext).notify(TAG, startId, notification.build())
	}

	private companion object {

		const val CHANNEL_ID = BaseBackupRestoreService.CHANNEL_ID
		const val TAG = "periodical_backup"
	}
}