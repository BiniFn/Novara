package org.skepsun.kototoro.video.danmaku

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Xml
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

class DanmakuSourceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val danmakuRepository: DanDanPlayRepository,
    private val danmakuCache: DanmakuCache,
    private val bilibiliDanmakuRepository: BilibiliDanmakuRepository,
    private val qqDanmakuRepository: QqDanmakuRepository,
) {

    suspend fun loadFromSources(
        title: String,
        episode: Int,
        url: String,
        cacheKey: String,
        keywords: List<String>,
        enableDanDan: Boolean,
        enableBilibili: Boolean,
        enableQq: Boolean,
    ): List<DanmakuItem> {
        val localItems = loadLocalDanmakuByVideoUrl(url)
        if (localItems.isNotEmpty()) {
            Log.d("Danmaku", "Local danmaku result: ${localItems.size}")
            return localItems
        }

        if (!enableDanDan && !enableBilibili && !enableQq) {
            Log.d("Danmaku", "DanDanPlay filter disabled: all sources off")
            return emptyList()
        }

        if (enableDanDan || enableBilibili || enableQq) {
            val cached = danmakuCache.loadDanmaku(cacheKey, episode)
            if (!cached.isNullOrEmpty()) {
                Log.d("Danmaku", "DanDanPlay cache hit: ${cached.size}")
                return filterBySource(cached, enableDanDan, enableBilibili, enableQq)
            }
            val cachedAnimeId = danmakuCache.getDanDanAnimeId(cacheKey)
            val items = if (cachedAnimeId != null) {
                Log.d("Danmaku", "Trying DanDanPlay: animeId=$cachedAnimeId episode=$episode")
                runCatching { danmakuRepository.fetchDanmakuByAnimeId(cachedAnimeId, episode) }
                    .getOrElse {
                        Log.w("Danmaku", "DanDanPlay failed: animeId=$cachedAnimeId", it)
                        emptyList()
                    }
            } else {
                val animeId = resolveDanDanAnimeId(keywords)
                if (animeId == null) {
                    Log.d("Danmaku", "DanDanPlay resolve failed: title=$title")
                    emptyList()
                } else {
                    danmakuCache.setDanDanAnimeId(cacheKey, animeId)
                    runCatching { danmakuRepository.fetchDanmakuByAnimeId(animeId, episode) }
                        .getOrElse {
                            Log.w("Danmaku", "DanDanPlay failed: animeId=$animeId", it)
                            emptyList()
                        }
                }
            }
            val filtered = filterBySource(items, enableDanDan, enableBilibili, enableQq)
            Log.d("Danmaku", "DanDanPlay result: raw=${items.size} filtered=${filtered.size}")
            if (items.isNotEmpty()) {
                danmakuCache.saveDanmaku(cacheKey, episode, items)
                return filtered
            }
        }

        return emptyList()
    }

    private fun filterBySource(
        items: List<DanmakuItem>,
        enableDanDan: Boolean,
        enableBilibili: Boolean,
        enableQq: Boolean,
    ): List<DanmakuItem> {
        return items.filter { item ->
            when (classifySource(item.source)) {
                DanmakuSource.BILIBILI -> enableBilibili
                DanmakuSource.QQ -> enableQq
                DanmakuSource.DANDAN -> enableDanDan
            }
        }
    }

    private fun classifySource(source: String): DanmakuSource {
        val lower = source.trim().lowercase()
        return when {
            lower.contains("bili") -> DanmakuSource.BILIBILI
            lower.contains("qq") || lower.contains("tencent") -> DanmakuSource.QQ
            else -> DanmakuSource.DANDAN
        }
    }

    private enum class DanmakuSource {
        DANDAN,
        BILIBILI,
        QQ,
    }

    private suspend fun resolveDanDanAnimeId(keywords: List<String>): Int? {
        for (keyword in keywords) {
            Log.d("Danmaku", "Trying DanDanPlay: title=$keyword")
            val animeId = runCatching { danmakuRepository.resolveAnimeId(keyword) }
                .getOrNull()
                ?.takeIf { it > 0 }
            if (animeId != null) {
                return animeId
            }
        }
        return null
    }

    private suspend fun loadLocalDanmakuByVideoUrl(url: String): List<DanmakuItem> =
        withContext(Dispatchers.IO) {
            val danmakuFile = findLocalDanmakuFileByVideoUrl(url) ?: return@withContext emptyList()
            runCatching { parseLocalXmlDanmaku(danmakuFile) }
                .onFailure { Log.w("Danmaku", "Local danmaku parse failed: ${danmakuFile.absolutePath}", it) }
                .getOrElse { emptyList() }
        }

    private fun findLocalDanmakuFileByVideoUrl(url: String): File? {
        if (url.isBlank()) return null
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") return null
        val videoPath = when {
            scheme.isNullOrBlank() -> url
            scheme == "file" -> uri?.path
            else -> null
        } ?: return null
        val videoFile = File(videoPath)
        if (!videoFile.exists() || !videoFile.isFile) return null
        val videoDir = videoFile.parentFile ?: return null
        val videoNameWithoutExt = videoFile.nameWithoutExtension
        val candidateNames = listOf(
            "$videoNameWithoutExt.xml",
            "${videoNameWithoutExt}.danmaku.xml",
            "${videoNameWithoutExt}_danmaku.xml",
            "${videoNameWithoutExt}.dandan.xml",
            "${videoNameWithoutExt}_dandan.xml",
            "${videoNameWithoutExt}.acfun.xml",
            "danmaku.xml",
            "弹幕.xml",
            videoNameWithoutExt,
        )
        return candidateNames
            .asSequence()
            .map { File(videoDir, it) }
            .firstOrNull { it.exists() && it.isFile }
    }

    private fun parseLocalXmlDanmaku(file: File): List<DanmakuItem> {
        val parser = Xml.newPullParser()
        file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
            parser.setInput(reader)
            val result = ArrayList<DanmakuItem>(1024)
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "d") {
                    val p = parser.getAttributeValue(null, "p").orEmpty()
                    val parts = p.split(',')
                    val timeMs = ((parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0) * 1000.0).toLong()
                    val mode = parts.getOrNull(1)?.toIntOrNull() ?: 1
                    val colorRaw = parts.getOrNull(3)?.toLongOrNull()?.toInt() ?: 0xFFFFFF
                    val color = if (colorRaw ushr 24 == 0) (0xFF shl 24) or colorRaw else colorRaw
                    val type = when (mode) {
                        4 -> DanmakuType.BOTTOM
                        5 -> DanmakuType.TOP
                        else -> DanmakuType.SCROLL
                    }
                    val message = parser.nextText().trim()
                    if (message.isNotEmpty()) {
                        result.add(
                            DanmakuItem(
                                message = message,
                                timeMs = timeMs,
                                type = type,
                                color = color,
                            ),
                        )
                    }
                }
                eventType = parser.next()
            }
            return result
        }
    }
}
