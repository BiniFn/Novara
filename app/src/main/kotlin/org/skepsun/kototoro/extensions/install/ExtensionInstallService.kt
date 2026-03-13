package org.skepsun.kototoro.extensions.install

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Call
import okhttp3.OkHttpClient
import org.skepsun.kototoro.BuildConfig
import org.skepsun.kototoro.core.network.MangaHttpClient
import org.skepsun.kototoro.extensions.repo.RepoAvailableExtension
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionInstallService @Inject constructor(
	@ApplicationContext private val context: Context,
	@MangaHttpClient private val httpClient: OkHttpClient,
) {

	private val activeCalls = ConcurrentHashMap<String, Call>()
	private val _downloadStates = MutableStateFlow<Map<String, ExtensionInstallDownloadState>>(emptyMap())

	val downloadStates: StateFlow<Map<String, ExtensionInstallDownloadState>> = _downloadStates.asStateFlow()

	suspend fun createInstallIntent(extension: RepoAvailableExtension): Intent {
		val apkUrl = "${extension.repoUrl}/apk/${extension.apkName}"
		val outputDir = File(context.cacheDir, "extension-installs").apply { mkdirs() }
		val outputFile = File(outputDir, "${extension.pkgName}-${extension.versionCode}.apk")
		val call = httpClient.newCachelessCallWithProgress(GET(apkUrl), ExtensionInstallProgressListener(extension.pkgName))
		check(activeCalls.putIfAbsent(extension.pkgName, call) == null) {
			"Extension install download already in progress for ${extension.pkgName}"
		}
		updateDownloadState(extension.pkgName, bytesRead = 0L, contentLength = -1L)
		try {
			call.awaitSuccess().use { response ->
				val body = requireNotNull(response.body) { "Missing APK response body" }
				outputFile.outputStream().use { output ->
					body.byteStream().use { input ->
						input.copyTo(output)
					}
				}
			}
		} catch (e: IOException) {
			if (call.isCanceled()) {
				throw CancellationException("Extension install download cancelled for ${extension.pkgName}", e)
			}
			throw e
		} finally {
			activeCalls.remove(extension.pkgName)
			_downloadStates.update { it - extension.pkgName }
		}
		val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", outputFile)
		return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
			setDataAndType(uri, "application/vnd.android.package-archive")
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
		}
	}

	fun cancelDownload(packageName: String) {
		activeCalls[packageName]?.cancel()
	}

	private fun updateDownloadState(packageName: String, bytesRead: Long, contentLength: Long) {
		_downloadStates.update { states ->
			states + (packageName to ExtensionInstallDownloadState(packageName, bytesRead, contentLength))
		}
	}

	private inner class ExtensionInstallProgressListener(
		private val packageName: String,
	) : eu.kanade.tachiyomi.network.ProgressListener {

		override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
			updateDownloadState(packageName, bytesRead, contentLength)
		}
	}
}

data class ExtensionInstallDownloadState(
	val packageName: String,
	val bytesRead: Long,
	val contentLength: Long,
) {

	val progressPercent: Int?
		get() = if (contentLength <= 0L) {
			null
		} else {
			((bytesRead * 100L) / contentLength)
				.coerceIn(0L, 100L)
				.toInt()
		}
}
