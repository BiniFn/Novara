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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnnxModelManager @Inject constructor(
	@ApplicationContext private val context: Context,
	@BaseHttpClient
	private val okHttpClient: OkHttpClient,
	private val appSettings: org.skepsun.kototoro.core.prefs.AppSettings,
) {

	data class DownloadProgress(
		val downloadedBytes: Long,
		val totalBytes: Long,
	)

	private val mutex = Mutex()

	fun getModelDir(modelId: String): File {
		return File(File(context.getExternalFilesDir(null), ROOT_DIR), modelId)
	}

	fun isModelDownloaded(modelId: String): Boolean {
		return File(getModelDir(modelId), INSTALLED_MARKER).isFile
	}

	suspend fun ensureModelReady(
		model: OnnxOfficialModel,
		onProgress: ((DownloadProgress) -> Unit)? = null,
	): String = mutex.withLock {
		val modelId = model.id
		val modelDir = getModelDir(modelId)
		val marker = File(modelDir, INSTALLED_MARKER)
		if (marker.isFile) {
			return@withLock modelDir.absolutePath
		}

		modelDir.mkdirs()
		if (!model.archiveUrl.isNullOrBlank()) {
			val archiveFile = File(modelDir, "package.zip")
			downloadFile(model.archiveUrl, archiveFile, onProgress)
			verifySha256(archiveFile, model.sha256.orEmpty())
			unzipToDir(archiveFile, modelDir)
			archiveFile.delete()
		} else {
			check(model.files.isNotEmpty()) { "ONNX model has no downloadable content: ${model.id}" }
			for ((index, file) in model.files.withIndex()) {
				val target = File(modelDir, file.fileName)
				val downloadUrl = if (modelId == "manga_bubble_yolo_hf_main" && file.fileName == "yolo26s.onnx") {
					appSettings.readerTranslationBubbleYoloUrl.takeIf { it.isNotBlank() } ?: file.downloadUrl
				} else {
					file.downloadUrl
				}
				downloadFile(downloadUrl, target) { progress ->
					if (progress.totalBytes > 0) {
						val weight = 1.0 / model.files.size
						val done = index + progress.downloadedBytes.toDouble() / progress.totalBytes.toDouble()
						val mergedTotal = model.files.size.toLong() * 100L
						val mergedDone = (done * 100.0).toLong()
						onProgress?.invoke(DownloadProgress(mergedDone, mergedTotal))
					} else {
						onProgress?.invoke(progress)
					}
				}
				verifySha256(target, file.sha256.orEmpty())
			}
		}
		marker.writeText("ok")
		modelDir.absolutePath
	}

	private suspend fun downloadFile(
		downloadUrl: String,
		targetFile: File,
		onProgress: ((DownloadProgress) -> Unit)?,
	) {
		val finalUrl = when (appSettings.huggingFaceMirror) {
			org.skepsun.kototoro.core.prefs.AppSettings.HuggingFaceMirror.HF_MIRROR -> downloadUrl.replaceFirst("https://huggingface.co", "https://hf-mirror.com")
			else -> downloadUrl
		}
		val request = Request.Builder().url(finalUrl).build()
		okHttpClient.newCall(request).await().use { response ->
			if (!response.isSuccessful) {
				error("Download ONNX model failed: HTTP ${response.code} ${response.message}. URL: $finalUrl")
			}
			val body = response.body ?: error("ONNX model response body is empty")
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
							if (read <= 0) break
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

	private suspend fun verifySha256(file: File, expected: String) {
		val normalizedExpected = expected.trim().lowercase()
		if (normalizedExpected.isBlank()) return
		val actual = runInterruptible(Dispatchers.IO) {
			val digest = MessageDigest.getInstance("SHA-256")
			FileInputStream(file).use { input ->
				val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
				while (true) {
					val read = input.read(buffer)
					if (read <= 0) break
					digest.update(buffer, 0, read)
				}
			}
			digest.digest().joinToString("") { b -> "%02x".format(b) }
		}
		check(actual == normalizedExpected) {
			"ONNX model checksum mismatch: expected=$normalizedExpected actual=$actual"
		}
	}

	private suspend fun unzipToDir(zipFile: File, targetDir: File) {
		runInterruptible(Dispatchers.IO) {
			ZipInputStream(FileInputStream(zipFile)).use { zipInput ->
				var entry = zipInput.nextEntry
				while (entry != null) {
					val outFile = File(targetDir, entry.name).canonicalFile
					check(outFile.path.startsWith(targetDir.canonicalPath)) {
						"Blocked zip-slip path: ${entry.name}"
					}
					if (entry.isDirectory) {
						outFile.mkdirs()
					} else {
						outFile.parentFile?.mkdirs()
						FileOutputStream(outFile).use { output ->
							val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
							while (true) {
								val read = zipInput.read(buffer)
								if (read <= 0) break
								output.write(buffer, 0, read)
							}
						}
					}
					zipInput.closeEntry()
					entry = zipInput.nextEntry
				}
			}
		}
	}
	fun deleteModel(modelId: String): Boolean {
		val dir = File(context.getExternalFilesDir(null), "$ROOT_DIR/$modelId")
		return dir.deleteRecursively()
	}

	private companion object {
		const val ROOT_DIR = "models/translation_onnx"
		const val INSTALLED_MARKER = ".installed"
	}
}
