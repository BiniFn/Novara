package org.skepsun.kototoro.video.danmaku

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.skepsun.kototoro.core.prefs.AppSettings

@Singleton
class DanmakuCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettings,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cacheDir by lazy { File(context.cacheDir, "danmaku_cache") }

    fun getDanDanAnimeId(cacheKey: String): Int? {
        val key = buildAnimeIdKey(cacheKey)
        val value = prefs.getInt(key, 0)
        return value.takeIf { it > 0 }
    }

    fun setDanDanAnimeId(cacheKey: String, animeId: Int) {
        if (animeId <= 0) return
        prefs.edit().putInt(buildAnimeIdKey(cacheKey), animeId).apply()
    }

    suspend fun loadDanmaku(cacheKey: String, episode: Int): List<DanmakuItem>? =
        withContext(Dispatchers.IO) {
            val file = getDanmakuFile(cacheKey, episode)
            if (!file.exists()) return@withContext null
            runCatching {
                val text = file.readText()
                val arr = JSONArray(text)
                val result = ArrayList<DanmakuItem>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val message = obj.optString(KEY_MESSAGE)
                    val timeMs = obj.optLong(KEY_TIME)
                    val type = parseType(obj.optString(KEY_TYPE))
                    val color = obj.optInt(KEY_COLOR, 0xFFFFFF)
                    val source = obj.optString(KEY_SOURCE, "DanDanPlay")
                    if (message.isBlank() || timeMs <= 0L) continue
                    result.add(
                        DanmakuItem(
                            message = message,
                            timeMs = timeMs,
                            type = type,
                            color = color,
                            source = source,
                        )
                    )
                }
                Log.d("Danmaku", "Cache load: key=$cacheKey episode=$episode size=${result.size}")
                result
            }.getOrElse {
                Log.w("Danmaku", "Cache load failed: key=$cacheKey episode=$episode", it)
                null
            }
        }

    suspend fun saveDanmaku(cacheKey: String, episode: Int, items: List<DanmakuItem>) =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val file = getDanmakuFile(cacheKey, episode)
            runCatching {
                val arr = JSONArray()
                items.forEach { item ->
                    val obj = JSONObject()
                    obj.put(KEY_MESSAGE, item.message)
                    obj.put(KEY_TIME, item.timeMs)
                    obj.put(KEY_TYPE, item.type.name)
                    obj.put(KEY_COLOR, item.color)
                    obj.put(KEY_SOURCE, item.source)
                    arr.put(obj)
                }
                file.writeText(arr.toString())
                trimCache()
                Log.d("Danmaku", "Cache saved: key=$cacheKey episode=$episode size=${items.size}")
            }.onFailure {
                Log.w("Danmaku", "Cache save failed: key=$cacheKey episode=$episode", it)
            }
        }

    private fun getDanmakuFile(cacheKey: String, episode: Int): File {
        val safeKey = sanitizeKey(cacheKey)
        return File(cacheDir, "dandan_${safeKey}_ep_${episode}.json")
    }

    private fun buildAnimeIdKey(cacheKey: String): String = "dandan_anime_id_${sanitizeKey(cacheKey)}"

    private fun sanitizeKey(value: String): String {
        val trimmed = value.trim().lowercase()
        val safe = trimmed.replace(Regex("[^a-z0-9._-]"), "_")
        return safe.take(80)
    }

    private fun parseType(raw: String): DanmakuType = runCatching {
        DanmakuType.valueOf(raw)
    }.getOrDefault(DanmakuType.SCROLL)

    private fun trimCache() {
        val limitBytes = settings.videoDanmakuCacheSizeMb * 1024L * 1024L
        if (limitBytes <= 0L || !cacheDir.exists()) return
        val files = cacheDir.listFiles().orEmpty().sortedBy { it.lastModified() }
        var currentSize = files.sumOf { it.length() }
        if (currentSize <= limitBytes) return
        files.forEach { file ->
            if (currentSize <= limitBytes) return
            val removed = file.length()
            if (file.delete()) {
                currentSize -= removed
            }
        }
    }

    private companion object {
        private const val PREFS_NAME = "video_danmaku_cache"
        private const val KEY_MESSAGE = "m"
        private const val KEY_TIME = "t"
        private const val KEY_TYPE = "y"
        private const val KEY_COLOR = "c"
        private const val KEY_SOURCE = "s"
    }
}
