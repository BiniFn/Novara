package org.skepsun.kototoro.video.danmaku

import android.graphics.Color
import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.skepsun.kototoro.core.network.BaseHttpClient

class QqDanmakuRepository @Inject constructor(
    @BaseHttpClient private val okHttpClient: OkHttpClient,
) {
    suspend fun fetchDanmakuByUrl(url: String): List<DanmakuItem> = withContext(Dispatchers.IO) {
        val vid = extractVid(url) ?: return@withContext emptyList()
        val baseInfo = getJson("https://dm.video.qq.com/barrage/base/$vid") ?: return@withContext emptyList()
        val segments = baseInfo.optJSONArray("segment_index") ?: JSONArray()
        val result = mutableListOf<DanmakuItem>()
        for (i in 0 until segments.length()) {
            val segment = segments.optInt(i)
            if (segment <= 0) continue
            val segmentUrl = "https://dm.video.qq.com/barrage/segment/$vid/t/v1/$segment"
            val segmentJson = getJson(segmentUrl) ?: continue
            val list = segmentJson.optJSONArray("barrage_list") ?: continue
            parseSegment(list, result)
        }
        Log.d("Danmaku", "QQ fetched: ${result.size} items (vid=$vid)")
        result
    }

    private fun extractVid(url: String): String? {
        Regex("[?&]vid=([A-Za-z0-9]+)").find(url)?.let {
            return it.groupValues[1]
        }
        Regex("/([A-Za-z0-9]+)\\.html").find(url)?.let {
            return it.groupValues[1]
        }
        return null
    }

    private fun parseSegment(list: JSONArray, output: MutableList<DanmakuItem>) {
        for (i in 0 until list.length()) {
            val obj = list.optJSONObject(i) ?: continue
            val content = obj.optString("content").ifBlank { obj.optString("txt") }
            if (content.isBlank()) continue
            val timeMs = when {
                obj.has("time_offset") -> obj.optLong("time_offset")
                obj.has("time") -> (obj.optDouble("time") * 1000).toLong()
                else -> 0L
            }
            val position = obj.optInt("position", obj.optInt("place", 0))
            val type = when (position) {
                2 -> DanmakuType.TOP
                3 -> DanmakuType.BOTTOM
                else -> DanmakuType.SCROLL
            }
            val colorValue = obj.optInt("color", 0xFFFFFF)
            val color = Color.rgb(
                (colorValue shr 16) and 0xFF,
                (colorValue shr 8) and 0xFF,
                colorValue and 0xFF,
            )
            output.add(
                DanmakuItem(
                    message = content,
                    timeMs = timeMs,
                    type = type,
                    color = color,
                )
            )
        }
    }

    private fun getJson(url: String): JSONObject? {
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return JSONObject(body)
        }
    }
}
