package org.koitharu.kotatsu.backups.ui.webdav

import android.content.Context
import androidx.room.InvalidationTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.collect
import org.koitharu.kotatsu.backups.data.BackupRepository
import org.koitharu.kotatsu.backups.domain.BackupUtils
import org.koitharu.kotatsu.backups.domain.ExternalBackupStorage
import org.koitharu.kotatsu.backups.ui.periodical.WebDavBackupUploader
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.TABLE_CHAPTERS
import org.koitharu.kotatsu.core.db.TABLE_FAVOURITE_CATEGORIES
import org.koitharu.kotatsu.core.db.TABLE_FAVOURITES
import org.koitharu.kotatsu.core.db.TABLE_HISTORY
import org.koitharu.kotatsu.core.db.TABLE_MANGA
import org.koitharu.kotatsu.core.db.TABLE_MANGA_TAGS
import org.koitharu.kotatsu.core.db.TABLE_PREFERENCES
import org.koitharu.kotatsu.core.db.TABLE_SOURCES
import org.koitharu.kotatsu.core.db.TABLE_TAGS
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 监听数据库关键表的变更，按需进行 WebDAV 自动同步上传。
 * 使用去抖动策略聚合短时间内的多次变更，避免频繁写入与上传。
 */
@Singleton
class DataSyncManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val database: MangaDatabase,
    private val settings: AppSettings,
    private val repository: BackupRepository,
    private val webDavUploader: WebDavBackupUploader,
    private val externalBackupStorage: ExternalBackupStorage,
) {

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var debounceJob: Job? = null
    private var settingsJob: Job? = null
    private val uploadMutex = Mutex()

    private val tablesToObserve = arrayOf(
        TABLE_HISTORY,
        TABLE_FAVOURITES,
        TABLE_FAVOURITE_CATEGORIES,
        TABLE_MANGA,
        TABLE_TAGS,
        TABLE_MANGA_TAGS,
        TABLE_SOURCES,
        TABLE_CHAPTERS,
        TABLE_PREFERENCES,
    )

    private val observer = object : InvalidationTracker.Observer(tablesToObserve) {
        override fun onInvalidated(tables: Set<String>) {
            scheduleUpload()
        }
    }

    /** 启动监听（幂等） */
    fun start() {
        runCatching {
            database.invalidationTracker.addObserver(observer)
        }.onFailure { it.printStackTraceDebug() }

        // 监听数据版本变化：发生变化时跳过节流，立即执行一次上传
        settingsJob?.cancel()
        settingsJob = scope.launch {
            settings.observe(AppSettings.KEY_BACKUP_WEBDAV_DATA_VERSION).collect { key ->
                if (key == AppSettings.KEY_BACKUP_WEBDAV_DATA_VERSION) {
                    runCatching { uploadNow() }.onFailure { it.printStackTraceDebug() }
                }
            }
        }
    }

    /** 停止监听并取消任务 */
    fun stop() {
        runCatching {
            database.invalidationTracker.removeObserver(observer)
        }.onFailure { it.printStackTraceDebug() }
        debounceJob?.cancel()
        settingsJob?.cancel()
    }

    private fun scheduleUpload() {
        // 条件判断：需启用自动同步且 WebDAV 上传可用、配置完整
        if (!settings.isBackupWebDavAutoSyncEnabled || !settings.isBackupWebDavUploadEnabled) return
        val url = settings.backupWebDavServerUrl
        val user = settings.backupWebDavUsername
        val pass = settings.backupWebDavPassword
        if (url.isNullOrBlank() || user.isNullOrBlank() || pass.isNullOrBlank()) return

        debounceJob?.cancel()
        debounceJob = scope.launch {
            // 聚合 30 秒内的变更
            delay(30_000)
            runCatching { uploadNow() }.onFailure { it.printStackTraceDebug() }
        }
    }

    private suspend fun uploadNow() {
        // 条件判断：需启用自动同步且 WebDAV 上传可用、配置完整
        if (!settings.isBackupWebDavAutoSyncEnabled || !settings.isBackupWebDavUploadEnabled) return
        val url = settings.backupWebDavServerUrl
        val user = settings.backupWebDavUsername
        val pass = settings.backupWebDavPassword
        if (url.isNullOrBlank() || user.isNullOrBlank() || pass.isNullOrBlank()) return

        uploadMutex.withLock {
            val output = BackupUtils.createTempFile(appContext)
            try {
                ZipOutputStream(output.outputStream()).use {
                    repository.createBackup(it, null)
                }
                // 按设置保留本地副本
                if (settings.isBackupWebDavKeepLocalCopyEnabled) {
                    externalBackupStorage.put(output)
                    externalBackupStorage.trim(settings.periodicalBackupMaxCount)
                }
                // 使用“下一个版本号”命名远端文件，上传成功后再持久化版本
                val nextVersion = settings.backupWebDavDataVersion + 1
                webDavUploader.uploadBackup(output, targetVersion = nextVersion)
                settings.backupWebDavLastUploadTime = System.currentTimeMillis()
                settings.backupWebDavLastUploadKind = "auto"
                // 上传成功后自增数据版本，用于版本化文件名与兼容判定
                settings.backupWebDavDataVersion = nextVersion
            } finally {
                output.delete()
            }
        }
    }
}