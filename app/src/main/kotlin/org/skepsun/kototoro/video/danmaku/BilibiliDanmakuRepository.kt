package org.skepsun.kototoro.video.danmaku

import android.graphics.Color
import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.skepsun.kototoro.core.network.BaseHttpClient

class BilibiliDanmakuRepository @Inject constructor(
    @BaseHttpClient private val okHttpClient: OkHttpClient,
) {
    suspend fun fetchDanmakuByUrl(url: String, episodeNumber: Int): List<DanmakuItem> = withContext(Dispatchers.IO) {
        val cid = resolveCid(url, episodeNumber) ?: return@withContext emptyList()
        val xml = fetchXml(cid) ?: return@withContext emptyList()
        val items = parseXml(xml)
        Log.d("Danmaku", "Bilibili fetched: ${items.size} items (cid=$cid)")
        items
    }

    private fun resolveCid(url: String, episodeNumber: Int): Long? {
        val cid = when {
            url.contains("/bangumi/play/") -> resolveBangumiCid(url, episodeNumber)
            url.contains("bilibili.com/video/") -> resolveVideoCid(url)
            else -> null
        }
        Log.d("Danmaku", "Bilibili resolve cid: $cid url=$url episode=$episodeNumber")
        return cid
    }

    private fun resolveVideoCid(url: String): Long? {
        val bvid = Regex("BV[0-9A-Za-z]+").find(url)?.value
        val aid = Regex("av(\\d+)").find(url)?.groupValues?.getOrNull(1)
        val page = Regex("[?&]p=(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val apiUrl = when {
            bvid != null -> buildUrl(
                "https://api.bilibili.com",
                "/x/player/pagelist",
                mapOf("bvid" to bvid),
            )
            aid != null -> buildUrl(
                "https://api.bilibili.com",
                "/x/player/pagelist",
                mapOf("aid" to aid),
            )
            else -> return null
        }
        Log.d("Danmaku", "Bilibili cid api: $apiUrl")
        val json = getJson(apiUrl) ?: return null
        if (json.optInt("code") != 0) return null
        val data = json.optJSONArray("data") ?: return null
        val index = (page - 1).coerceIn(0, data.length() - 1)
        return data.optJSONObject(index)?.optLong("cid")
    }

    private fun resolveBangumiCid(url: String, episodeNumber: Int): Long? {
        val seasonId = Regex("ss(\\d+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
        val epId = Regex("ep(\\d+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
        val apiUrl = when {
            seasonId != null -> "https://api.bilibili.com/pgc/view/web/season?season_id=$seasonId"
            epId != null -> "https://api.bilibili.com/pgc/view/web/season?ep_id=$epId"
            else -> return null
        }
        Log.d("Danmaku", "Bilibili bangumi api: $apiUrl")
        val json = getJson(apiUrl) ?: return null
        if (json.optInt("code") != 0) return null
        val result = json.optJSONObject("result") ?: return null
        val episodes = result.optJSONArray("episodes") ?: return null
        var bestCid: Long? = null
        for (i in 0 until episodes.length()) {
            val ep = episodes.optJSONObject(i) ?: continue
            val cid = ep.optLong("cid")
            val epIdValue = ep.optLong("id")
            if (epId != null && epIdValue.toString() == epId) {
                return cid
            }
            val title = ep.optString("long_title").ifBlank { ep.optString("title") }
            val parsed = parseEpisodeNumber(title)
            if (parsed == episodeNumber) {
                bestCid = cid
                break
            }
            if (bestCid == null && i == 0) {
                bestCid = cid
            }
        }
        return bestCid
    }

    private fun fetchXml(cid: Long): String? {
        val url = "https://comment.bilibili.com/$cid.xml"
        Log.d("Danmaku", "Bilibili xml url: $url")
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful || body.isNullOrBlank()) {
                val snippet = body?.take(200).orEmpty()
                Log.d("Danmaku", "Bilibili xml failed: code=${response.code} msg=${response.message} body=$snippet")
                return null
            }
            return body
        }
    }

    private fun parseXml(xml: String): List<DanmakuItem> {
        val result = mutableListOf<DanmakuItem>()
        val regex = Regex("<d p=\"([^\"]+)\">([^<]*)</d>")
        regex.findAll(xml).forEach { match ->
            val p = match.groupValues[1]
            val textRaw = match.groupValues[2]
            val parts = p.split(',')
            if (parts.size < 4) return@forEach
            val timeMs = ((parts[0].toDoubleOrNull() ?: 0.0) * 1000).toLong()
            val type = when (parts[1].toIntOrNull()) {
                4 -> DanmakuType.BOTTOM
                5 -> DanmakuType.TOP
                else -> DanmakuType.SCROLL
            }
            val colorValue = parts[3].toIntOrNull() ?: 0xFFFFFF
            val color = Color.rgb(
                (colorValue shr 16) and 0xFF,
                (colorValue shr 8) and 0xFF,
                colorValue and 0xFF,
            )
            val text = unescapeXml(textRaw)
            result.add(
                DanmakuItem(
                    message = text,
                    timeMs = timeMs,
                    type = type,
                    color = color,
                    source = "Bilibili",
                )
            )
        }
        return result
    }

    private fun parseEpisodeNumber(title: String): Int {
        val match = Regex("(\\d+)").find(title) ?: return 0
        return match.groupValues[1].toIntOrNull() ?: 0
    }

    private fun unescapeXml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    private fun getJson(url: String): JSONObject? {
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful || body.isNullOrBlank()) {
                val snippet = body?.take(200).orEmpty()
                Log.d("Danmaku", "Bilibili json failed: code=${response.code} msg=${response.message} url=$url body=$snippet")
                return null
            }
            return JSONObject(body)
        }
    }

    private fun buildUrl(base: String, path: String, params: Map<String, String>): String {
        val builder = base.toHttpUrl().newBuilder().addEncodedPathSegments(path.trimStart('/'))
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }
}
