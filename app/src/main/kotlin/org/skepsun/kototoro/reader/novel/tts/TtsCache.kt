package org.skepsun.kototoro.reader.novel.tts

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.source
import org.skepsun.kototoro.core.util.ext.MimeType
import org.skepsun.kototoro.local.data.CacheDir
import org.skepsun.kototoro.local.data.LocalStorageCache
import org.skepsun.kototoro.reader.novel.tts.model.AudioData
import org.skepsun.kototoro.reader.novel.tts.model.Token
import java.io.File
import java.security.MessageDigest

class TtsCache(context: Context) {

    // 复用 Kototoro 的 LocalStorageCache，享受 DiskLruCache 和 自动大小管理
    private val localCache = LocalStorageCache(
        context = context,
        dir = CacheDir.TtsAudio, // Ensure you add this enum value in CacheDir if it doesn't exist
        defaultSize = 100L * 1024 * 1024, // 100MB 默认缓存容量
        minSize = 10L * 1024 * 1024 // 最少保留 10MB
    )

    fun buildCacheKey(
        token: Token,
        engineId: String,
        voiceId: String,
        speed: Float,
        pitch: Float
    ): String {
        val raw = "${token.text}|$engineId|$voiceId|$speed|$pitch"
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun get(key: String): AudioData? {
        val file = localCache.get(key)
        if (file != null && file.exists()) {
            return AudioData(file.toUri(), null)
        }
        return null
    }

    suspend fun put(key: String, data: AudioData) = withContext(Dispatchers.IO) {
        val scheme = data.uri.scheme
        if (scheme == "file") {
            val sourceFile = File(data.uri.path!!)
            if (sourceFile.exists()) {
                // 原子转移给 LocalCache，我们利用 source()
                // LocalStorageCache 内部的 set() 操作使用了 createBufferFile 生成临时文件，
                // 然后 writeAll 进去最后再 put()。在一定程度上也是原子保护。
                localCache.set(key, sourceFile.source(), MimeType("audio/wav"))
            }
        }
    }
    
    suspend fun clear() {
        localCache.clear()
    }
}
