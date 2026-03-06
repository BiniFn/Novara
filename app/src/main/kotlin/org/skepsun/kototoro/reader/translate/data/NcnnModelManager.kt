package org.skepsun.kototoro.reader.translate.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.skepsun.kototoro.core.network.BaseHttpClient
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NcnnModelManager @Inject constructor(
	@ApplicationContext private val context: Context,
	@BaseHttpClient
	private val okHttpClient: OkHttpClient,
) {

	data class DownloadProgress(
		val downloadedBytes: Long,
		val totalBytes: Long,
	)

	private val mutex = Mutex()

	fun isModelDownloaded(version: String): Boolean {
		val modelDir = getModelDir(version)
		return REQUIRED_FILES.all { File(modelDir, it).isFile }
	}

	fun getModelDir(version: String): File {
		return File(File(context.getExternalFilesDir(null), ROOT_DIR), version)
	}

	suspend fun ensureModelReady(
		version: String,
		detParamUrl: String,
		detBinUrl: String,
		recParamUrl: String,
		recBinUrl: String,
		onProgress: ((String, DownloadProgress) -> Unit)? = null,
	): String = mutex.withLock {
		val modelDir = getModelDir(version)
		modelDir.mkdirs()

		val files = mapOf(
			"Det Param" to (detParamUrl to File(modelDir, DET_PARAM_FILE)),
			"Det Bin" to (detBinUrl to File(modelDir, DET_BIN_FILE)),
			"Rec Param" to (recParamUrl to File(modelDir, REC_PARAM_FILE)),
			"Rec Bin" to (recBinUrl to File(modelDir, REC_BIN_FILE)),
		)

		for ((component, pair) in files) {
			val (url, file) = pair
			if (!file.isFile) {
				downloadFile(url, file) { progress ->
					onProgress?.invoke(component, progress)
				}
			}
		}

		check(REQUIRED_FILES.all { File(modelDir, it).isFile }) {
			"NCNN models missing after download: ${REQUIRED_FILES.joinToString()}"
		}

		modelDir.absolutePath
	}

	private suspend fun downloadFile(
		downloadUrl: String,
		targetFile: File,
		onProgress: ((DownloadProgress) -> Unit)?,
	) {
		val request = Request.Builder().url(downloadUrl).build()
		okHttpClient.newCall(request).await().use { response ->
			if (!response.isSuccessful) {
				error("Download NCNN model failed: HTTP ${response.code} ${response.message}. URL: $downloadUrl")
			}
			val body = response.body ?: error("NCNN model response body is empty")
			runInterruptible(Dispatchers.IO) {
				val totalBytes = body.contentLength().takeIf { it > 0L } ?: -1L
				FileOutputStream(targetFile).use { output ->
					body.byteStream().use { input ->
						val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
						var downloaded = 0L
						var lastNotified = -1L
						onProgress?.invoke(DownloadProgress(0L, totalBytes))
						while (true) {
							val read = input.read(buffer)
							if (read <= 0) {
								break
							}
							output.write(buffer, 0, read)
							downloaded += read
							if (downloaded - lastNotified >= 256 * 1024 || downloaded == totalBytes) {
								lastNotified = downloaded
								onProgress?.invoke(DownloadProgress(downloaded, totalBytes))
							}
						}
						onProgress?.invoke(DownloadProgress(downloaded, totalBytes))
					}
				}
			}
		}
	}

	private companion object {
		const val ROOT_DIR = "models/ocr_ncnn"
		const val DET_PARAM_FILE = "det.ncnn.param"
		const val DET_BIN_FILE = "det.ncnn.bin"
		const val REC_PARAM_FILE = "rec.ncnn.param"
		const val REC_BIN_FILE = "rec.ncnn.bin"
		val REQUIRED_FILES = listOf(DET_PARAM_FILE, DET_BIN_FILE, REC_PARAM_FILE, REC_BIN_FILE)
	}
}
