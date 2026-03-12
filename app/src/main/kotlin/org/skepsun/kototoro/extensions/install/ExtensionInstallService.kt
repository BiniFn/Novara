package org.skepsun.kototoro.extensions.install

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import okhttp3.OkHttpClient
import org.skepsun.kototoro.BuildConfig
import org.skepsun.kototoro.core.network.MangaHttpClient
import org.skepsun.kototoro.extensions.repo.RepoAvailableExtension
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionInstallService @Inject constructor(
	@ApplicationContext private val context: Context,
	@MangaHttpClient private val httpClient: OkHttpClient,
) {

	suspend fun createInstallIntent(extension: RepoAvailableExtension): Intent {
		val apkUrl = "${extension.repoUrl}/apk/${extension.apkName}"
		val outputDir = File(context.cacheDir, "extension-installs").apply { mkdirs() }
		val outputFile = File(outputDir, "${extension.pkgName}-${extension.versionCode}.apk")
		httpClient.newCall(GET(apkUrl)).awaitSuccess().use { response ->
			val body = requireNotNull(response.body) { "Missing APK response body" }
			outputFile.outputStream().use { output ->
				body.byteStream().use { input ->
					input.copyTo(output)
				}
			}
		}
		val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", outputFile)
		return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
			setDataAndType(uri, "application/vnd.android.package-archive")
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
		}
	}
}
