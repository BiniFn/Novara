package org.skepsun.kototoro.reader.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.GlobalScope
import org.skepsun.kototoro.reader.translate.data.OnnxModelManager
import org.skepsun.kototoro.reader.translate.data.RealCuganNcnnEngine
import org.skepsun.kototoro.reader.translate.data.RealEsrganNcnnEngine
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReaderSuperResolutionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onnxModelManager: OnnxModelManager,
) {
    private val TAG = "ReaderSuperResolutionManager"
    private val engineMutex = Mutex()
    private val cacheDir = File(context.cacheDir, "sr_cache").apply { mkdirs() }

    private var activeModelId: String? = null
    private var realesrganEngine: RealEsrganNcnnEngine? = null
    private var realcuganEngine: RealCuganNcnnEngine? = null

    suspend fun processImage(
        originalUri: Uri,
        modelId: String,
        noiseLevel: Int,
        cacheLimitMb: Int
    ): Uri? = withContext(Dispatchers.IO) {
        if (originalUri.scheme != "file") {
            return@withContext null
        }
        val originalFile = originalUri.toFile()
        if (!originalFile.exists()) return@withContext null

        val hash = "${originalFile.name}_${modelId}_ncnn".hashCode().toString()
        val outputFile = File(cacheDir, "sr_$hash.webp")

        if (outputFile.exists() && outputFile.length() > 0) {
            Log.d(TAG, "Using cached SR image: ${outputFile.name}")
            updateCacheLru(outputFile)
            return@withContext outputFile.toUri()
        }

        Log.d(TAG, "Starting NCNN SR processing for ${originalFile.name}")

        val resultBitmap: Bitmap? = try {
            engineMutex.withLock {
                if (!isActive) return@withLock null
                initEngineIfNeeded(modelId)

                val originalBitmap = BitmapFactory.decodeFile(originalFile.absolutePath) ?: return@withLock null

                val outBmp: Bitmap? = if (modelId.contains("realesrgan", ignoreCase = true)) {
                    realesrganEngine?.process(originalBitmap)
                } else {
                    realcuganEngine?.process(originalBitmap)
                }
                originalBitmap.recycle()
                outBmp
            }
        } catch (e: Exception) {
            Log.e(TAG, "NCNN SR processing failed", e)
            null
        }

        if (resultBitmap != null) {
            try {
                FileOutputStream(outputFile).use { out ->
                    resultBitmap.compress(Bitmap.CompressFormat.WEBP, 90, out)
                }
                manageCache(cacheLimitMb)
                return@withContext outputFile.toUri()
            } catch (e: Exception) {
                Log.e(TAG, "NCNN SR Processing Save failed", e)
                outputFile.delete()
            } finally {
                resultBitmap.recycle()
            }
        }

        null
    }

    private suspend fun initEngineIfNeeded(modelId: String) {
        if (activeModelId == modelId) {
            if (realesrganEngine != null || realcuganEngine != null) return
        }
        
        releaseEngines()

        if (!onnxModelManager.isModelDownloaded(modelId)) {
            Log.e(TAG, "SR model not downloaded: $modelId")
            throw IllegalStateException("Super resolution model not downloaded")
        }

        val modelsDir = onnxModelManager.getModelDir(modelId)
        val expectedParamName = if (modelId.contains("realesrgan", ignoreCase = true)) "realesrgan-x4plus-anime.param" else "up2x-conservative.param"
        val expectedBinName = if (modelId.contains("realesrgan", ignoreCase = true)) "realesrgan-x4plus-anime.bin" else "up2x-conservative.bin"

        val paramFile = modelsDir.walkTopDown().firstOrNull { it.name == expectedParamName }
            ?: throw IllegalStateException("No parameter file $expectedParamName found for model $modelId")
        val binFile = modelsDir.walkTopDown().firstOrNull { it.name == expectedBinName }
            ?: throw IllegalStateException("No binary file $expectedBinName found for model $modelId")

        if (modelId.contains("realesrgan", ignoreCase = true)) {
            realesrganEngine = RealEsrganNcnnEngine()
            realesrganEngine?.initialize(paramFile.absolutePath, binFile.absolutePath, ttaMode = false)
        } else {
            realcuganEngine = RealCuganNcnnEngine()
            realcuganEngine?.initialize(paramFile.absolutePath, binFile.absolutePath, ttaMode = false)
        }
        activeModelId = modelId
    }

    private fun releaseEngines() {
        realesrganEngine?.release()
        realesrganEngine = null
        realcuganEngine?.release()
        realcuganEngine = null
    }

    private fun updateCacheLru(file: File) {
        file.setLastModified(System.currentTimeMillis())
    }

    private fun manageCache(limitMb: Int) {
        if (limitMb < 0) return
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
        GlobalScope.launch(Dispatchers.IO) {
            engineMutex.withLock {
                releaseEngines()
                activeModelId = null
            }
        }
    }
}
