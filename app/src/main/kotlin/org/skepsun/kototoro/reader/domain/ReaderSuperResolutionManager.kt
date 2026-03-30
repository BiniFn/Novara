package org.skepsun.kototoro.reader.domain

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.akari.realcugan_ncnn_android.RealCUGAN
import java.lang.reflect.Method
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.core.image.BitmapDecoderCompat
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReaderSuperResolutionManager @Inject constructor(
	@ApplicationContext private val context: Context,
) {
	private val TAG = "ReaderSuperResolutionManager"
	private var engine: RealCUGAN? = null
	private val engineMutex = Mutex()

	private val cacheDir = File(context.cacheDir, "sr_cache").apply { mkdirs() }
	private val cacheLimitBytesDefault = 512L * 1024 * 1024 // 512MB default

	suspend fun processImage(
		originalUri: Uri,
		model: String,
		noiseLevel: Int,
		cacheLimitMb: Int
	): Uri? = withContext(Dispatchers.IO) {
		if (originalUri.scheme != "file") {
			return@withContext null
		}
		val originalFile = originalUri.toFile()
		if (!originalFile.exists()) return@withContext null

		val hash = "${originalFile.name}_${model}_${noiseLevel}".hashCode().toString()
		val outputFile = File(cacheDir, "sr_$hash.webp")

		if (outputFile.exists() && outputFile.length() > 0) {
			Log.d(TAG, "Using cached SR image: \${outputFile.name}")
			updateCacheLru(outputFile)
			return@withContext outputFile.toUri()
		}

		Log.d(TAG, "Starting SR processing for \${originalFile.name}")
		
		val resultBitmap = try {
			engineMutex.withLock {
				val bytes = originalFile.readBytes()
				initEngineIfNeeded(model, noiseLevel)
				// Using reflection to call process(ByteArray) which is 'suspend'
				// But wait! process(ByteArray, ProgressListener) is suspend. Reflection with suspend is annoying.
				engine?.process(bytes, null)
			}
		} catch (e: Exception) {
			Log.e(TAG, "Engine processing failed", e)
			null
		}

		if (resultBitmap != null) {
			try {
				FileOutputStream(outputFile).use { out ->
					resultBitmap.compress(Bitmap.CompressFormat.WEBP, 90, out)
				}
				Log.d(TAG, "SR processing successful: \${outputFile.name}")
				manageCache(cacheLimitMb)
				return@withContext outputFile.toUri()
			} catch (e: Exception) {
				Log.e(TAG, "Failed to save SR image", e)
				outputFile.delete()
			} finally {
				resultBitmap.recycle()
			}
		}

		null
	}

	@Throws(Exception::class)
	private suspend fun initEngineIfNeeded(model: String, noiseLevel: Int) {
		val modelDir = "models-${model.lowercase()}"
		Log.d(TAG, "Initializing RealCUGAN with model: $modelDir, noise: $noiseLevel")

		engine?.release()

		// Let's try native init directly via reflection to bypass Coroutines reflection complexity
		val realCugClass = Class.forName("com.akari.realcugan_ncnn_android.RealCUGAN")
		val companionField = realCugClass.getField("Companion")
		val companionObj = companionField.get(null)
		
		val copyModelsMethod = companionObj.javaClass.getDeclaredMethod("copyModels\$realcugan_ncnn_android_release", Context::class.java)
		copyModelsMethod.isAccessible = true
		val modelsDir = copyModelsMethod.invoke(companionObj, context) as java.io.File
		
		System.loadLibrary("realcugan_ncnn_android")
		
		val initMethod = companionObj.javaClass.getDeclaredMethod(
			"nativeInitialize", 
			String::class.java, java.lang.Integer::class.java, java.lang.Integer::class.java, 
			java.lang.Integer::class.java, String::class.java, java.lang.Boolean::class.java, java.lang.Integer::class.java
		)
		initMethod.isAccessible = true
		val handle = initMethod.invoke(companionObj, modelsDir.absolutePath, noiseLevel, 2, 0, modelDir, false, null) as Long
		
		val mainCons = realCugClass.getDeclaredConstructors().first()
		mainCons.isAccessible = true
		engine = mainCons.newInstance(handle, 2) as RealCUGAN
	}

	private fun updateCacheLru(file: File) {
		file.setLastModified(System.currentTimeMillis())
	}

	private fun manageCache(limitMb: Int) {
		if (limitMb < 0) return // Unlimited

		val limitBytes = limitMb * 1024L * 1024L
		val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
		var totalSize = files.sumOf { it.length() }

		for (file in files) {
			if (totalSize <= limitBytes) break
			totalSize -= file.length()
			file.delete()
		}
	}

	fun release() {
		engine?.release()
		engine = null
	}
}
