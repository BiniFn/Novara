package org.skepsun.kototoro.reader.translate.data

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class RealEsrganNcnnEngine(private val gpuId: Int = 0) {
    private var nativeHandle: Long = 0

    init {
        System.loadLibrary("realesrgan_ncnn") // using the shared lib containing both
    }

    private external fun initNative(paramPath: String, binPath: String, gpuId: Int, ttaMode: Boolean): Long
    private external fun processNative(handle: Long, inBitmap: Bitmap, outBitmap: Bitmap): Boolean
    private external fun releaseNative(handle: Long)

    suspend fun initialize(paramPath: String, binPath: String, ttaMode: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        nativeHandle = initNative(paramPath, binPath, gpuId, ttaMode)
        nativeHandle != 0L
    }

    suspend fun process(inBitmap: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        if (nativeHandle == 0L) {
            Log.e("RealEsrganNcnnEngine", "Engine not initialized.")
            return@withContext null
        }

        // RealESRGAN anime models are 4x
        val outW = inBitmap.width * 4
        val outH = inBitmap.height * 4
        val outBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)

        val success = processNative(nativeHandle, inBitmap, outBitmap)
        if (success) outBitmap else null
    }

    fun release() {
        if (nativeHandle != 0L) {
            releaseNative(nativeHandle)
            nativeHandle = 0L
        }
    }
}
