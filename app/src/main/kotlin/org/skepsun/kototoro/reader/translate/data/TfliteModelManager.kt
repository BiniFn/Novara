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
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TfliteModelManager @Inject constructor(
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
		return File(modelDir, "encoder.tflite").exists() && File(modelDir, "decoder.tflite").exists()
	}

	fun getModelDir(version: String): File {
		return File(File(context.getExternalFilesDir(null), ROOT_DIR), version)
	}

	suspend fun ensureModelReady(
		version: String,
		encoderUrl: String,
		decoderUrl: String,
		vocabUrl: String? = null,
		embeddingsUrl: String? = null,
		onProgress: ((String, DownloadProgress) -> Unit)? = null,
	): String = mutex.withLock {
		val modelDir = getModelDir(version)
		modelDir.mkdirs()

		val encoderFile = File(modelDir, "encoder.tflite")
		val decoderFile = File(modelDir, "decoder.tflite")
		val vocabFile = File(modelDir, "vocab.csv")
		val embeddingsFile = embeddingsUrl?.let { File(modelDir, "embeddings.bin") }
		
		val requiresVocab = !vocabUrl.isNullOrBlank()
		val isReady = encoderFile.exists() &&
			decoderFile.exists() &&
			(!requiresVocab || vocabFile.exists()) &&
			(embeddingsFile == null || embeddingsFile.exists())

		if (!isReady) {
			if (!encoderFile.exists()) {
				downloadFile(encoderUrl, encoderFile) { onProgress?.invoke("Encoder", it) }
			}
			if (!decoderFile.exists()) {
				downloadFile(decoderUrl, decoderFile) { onProgress?.invoke("Decoder", it) }
			}
			if (requiresVocab && !vocabFile.exists()) {
				downloadFile(vocabUrl, vocabFile) { onProgress?.invoke("Vocab", it) }
			}
			if (embeddingsUrl != null && embeddingsFile != null && !embeddingsFile.exists()) {
				downloadFile(embeddingsUrl, embeddingsFile) { onProgress?.invoke("Embeddings", it) }
			}
			
			check(encoderFile.exists() && decoderFile.exists()) {
				"TFLite models (encoder.tflite, decoder.tflite) missing after download"
			}
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
				error("Download TFLite model failed: HTTP ${response.code} ${response.message}. URL: $downloadUrl")
			}
			val body = response.body ?: error("TFLite model response body is empty")
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
		const val ROOT_DIR = "models/ocr_mangaocr"
	}
}
