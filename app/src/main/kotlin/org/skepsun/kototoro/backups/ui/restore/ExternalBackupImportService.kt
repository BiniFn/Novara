package org.skepsun.kototoro.backups.ui.restore

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.annotation.CheckResult
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.backups.external.ExternalBackupDecoder
import org.skepsun.kototoro.backups.external.ExternalBackupApp
import org.skepsun.kototoro.backups.external.ExternalBackupRepository
import org.skepsun.kototoro.backups.ui.BaseBackupRestoreService
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.util.CompositeResult
import org.skepsun.kototoro.core.util.ext.getSerializableExtraCompat
import org.skepsun.kototoro.core.util.ext.powerManager
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.ext.toUriOrNull
import org.skepsun.kototoro.core.util.ext.withPartialWakeLock
import java.io.FileNotFoundException
import javax.inject.Inject
import androidx.appcompat.R as appcompatR

@AndroidEntryPoint
@SuppressLint("InlinedApi")
class ExternalBackupImportService : BaseBackupRestoreService() {

    override val notificationTag = TAG
    override val isRestoreService = true

    @Inject
    lateinit var decoder: ExternalBackupDecoder

    @Inject
    lateinit var repository: ExternalBackupRepository

    override suspend fun IntentJobContext.processIntent(intent: Intent) {
        val notification = buildNotification()
        setForeground(
            FOREGROUND_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        val source = intent.getStringExtra(AppRouter.KEY_DATA)?.toUriOrNull() ?: throw FileNotFoundException()
        val app = intent.getStringExtra(EXTRA_APP)
            ?.let(ExternalBackupApp::valueOf)
            ?: throw IllegalArgumentException("Missing external backup app")
        powerManager.withPartialWakeLock(TAG) {
            val result = runCatching {
                val records = withContext(Dispatchers.IO) { decoder.decode(source, app) }
                repository.import(records)
            }
            result.fold(
                onSuccess = {
                    showResultNotification(source, CompositeResult.success())
                },
                onFailure = {
                    showResultNotification(source, CompositeResult.failure(it))
                },
            )
        }
    }

    private fun IntentJobContext.buildNotification(): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(getString(R.string.import_backup_from_other_apps))
            .setContentText(getString(R.string.processing_))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(0)
            .setSilent(true)
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(
                appcompatR.drawable.abc_ic_clear_material,
                applicationContext.getString(android.R.string.cancel),
                getCancelIntent(),
            )
            .build()
    }

    companion object {
        private const val TAG = "EXTERNAL_BACKUP_IMPORT"
        private const val FOREGROUND_NOTIFICATION_ID = 40
        private const val EXTRA_APP = "external_backup_app"

        @CheckResult
        fun start(context: Context, uri: Uri, app: ExternalBackupApp): Boolean = try {
            val intent = Intent(context, ExternalBackupImportService::class.java)
            intent.putExtra(AppRouter.KEY_DATA, uri.toString())
            intent.putExtra(EXTRA_APP, app.name)
            intent.setData(uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            e.printStackTraceDebug()
            false
        }
    }
}
