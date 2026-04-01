package org.skepsun.kototoro.settings.about

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.net.toUri
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.github.AppUpdateRepository
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.util.ext.MutableEventFlow
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.core.util.ext.requireValue
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
	private val repository: AppUpdateRepository,
	@ApplicationContext private val context: Context,
) : BaseViewModel() {

	val nextVersion = repository.observeAvailableUpdate()
	val downloadProgress = MutableStateFlow(-1f)
	val downloadState = MutableStateFlow(DownloadManager.STATUS_PENDING)
	val installIntent = MutableStateFlow<Intent?>(null)
	val updateMessage = MutableStateFlow<String?>(null)
	val onDownloadDone = MutableEventFlow<Intent>()

	private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
	private val appName = context.getString(R.string.app_name)

	init {
		if (nextVersion.value == null) {
			launchLoadingJob(Dispatchers.Default) {
				repository.fetchUpdate()
			}
		}
	}

	fun startDownload() {
		launchLoadingJob(Dispatchers.Default) {
			val version = nextVersion.requireValue()
			val isPatch = version.patchUrl != null
			val url = (version.patchUrl ?: version.apkUrl).toUri()
			val title = if (isPatch) "$appName v${version.name} (Delta)" else "$appName v${version.name}"
			val request = DownloadManager.Request(url)
				.setTitle(title)
				.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, url.lastPathSegment)
				.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
				.setMimeType(if (isPatch) "application/octet-stream" else "application/vnd.android.package-archive")
			val downloadId = downloadManager.enqueue(request)
			observeDownload(downloadId)
		}
	}

	fun onDownloadComplete(intent: Intent) {
		launchLoadingJob(Dispatchers.Default) {
			val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L)
			if (downloadId == 0L) {
				return@launchLoadingJob
			}
			val uri = downloadManager.getUriForDownloadedFile(downloadId) ?: return@launchLoadingJob
			val mimeType = downloadManager.getMimeTypeForDownloadedFile(downloadId)
			
			val installUri = if (mimeType == "application/octet-stream" || uri.path?.endsWith(".patch") == true) {
				// Incremental update patch downloaded. We need to merge it with the base APK.
				val patchFile = java.io.File(context.cacheDir, "update.patch")
				context.contentResolver.openInputStream(uri)?.use { input ->
					patchFile.outputStream().use { output ->
						input.copyTo(output)
					}
				}
				val oldApk = java.io.File(context.applicationInfo.sourceDir)
				val newApk = java.io.File(context.cacheDir, "update_merged.apk")
				newApk.delete()
				
				updateMessage.value = context.getString(R.string.assembling_apk)
				org.skepsun.kototoro.core.os.PatchUtils.patch(oldApk, patchFile, newApk)
				updateMessage.value = null
				
				// Optional cleanup of the downloaded patch
				runCatching { patchFile.delete() }
				
				androidx.core.content.FileProvider.getUriForFile(
					context,
					"${org.skepsun.kototoro.BuildConfig.APPLICATION_ID}.files",
					newApk,
				)
			} else {
				uri
			}

			@Suppress("DEPRECATION")
			val installerIntent = Intent(Intent.ACTION_INSTALL_PACKAGE)
			installerIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
			installerIntent.setDataAndType(installUri, "application/vnd.android.package-archive")
			installerIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
			installIntent.value = installerIntent
			onDownloadDone.call(installerIntent)
		}
	}

	private suspend fun observeDownload(id: Long) {
		val query = DownloadManager.Query()
		query.setFilterById(id)
		while (currentCoroutineContext().isActive) {
			downloadManager.query(query).use { cursor ->
				if (cursor.moveToFirst()) {
					val bytesDownloaded = cursor.getLong(
						cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
					)
					val bytesTotal = cursor.getLong(
						cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
					)
					downloadProgress.value = bytesDownloaded.toFloat() / bytesTotal
					val state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
					downloadState.value = state
					if (state == DownloadManager.STATUS_SUCCESSFUL || state == DownloadManager.STATUS_FAILED) {
						return
					}
				}
			}
			delay(100)
		}
	}
}
