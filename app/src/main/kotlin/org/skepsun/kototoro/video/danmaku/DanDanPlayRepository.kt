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
import org.skepsun.kototoro.BuildConfig
import kotlin.math.max
import java.security.MessageDigest
import android.util.Base64

class DanDanPlayRepository @Inject constructor(
    @BaseHttpClient private val okHttpClient: OkHttpClient,
) {
    suspend fun fetchDanmaku(title: String, episode: Int): List<DanmakuItem> = withContext(Dispatchers.IO) {
        if (title.isBlank() || episode <= 0) return@withContext emptyList()
        val animeId = searchAnimeId(title) ?: run {
            Log.d("Danmaku", "DanDanPlay search failed: title=$title")
            return@withContext emptyList()
        }
        val episodeId = findEpisodeId(animeId, episode) ?: run {
            Log.d("Danmaku", "DanDanPlay episode not found: animeId=$animeId episode=$episode")
            return@withContext emptyList()
        }
        val items = fetchDanmakuByEpisodeId(episodeId)
        Log.d("Danmaku", "DanDanPlay fetched: ${items.size} items (episodeId=$episodeId)")
        items
    }

    suspend fun resolveAnimeId(title: String): Int? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val animeId = searchAnimeId(title)
        if (animeId == null) {
            Log.d("Danmaku", "DanDanPlay resolve failed: title=$title")
        } else {
            Log.d("Danmaku", "DanDanPlay resolve animeId=$animeId title=$title")
        }
        animeId
    }

    suspend fun fetchDanmakuByAnimeId(animeId: Int, episode: Int): List<DanmakuItem> =
        withContext(Dispatchers.IO) {
            if (animeId <= 0 || episode <= 0) return@withContext emptyList()
            val episodeId = findEpisodeId(animeId, episode) ?: run {
                Log.d("Danmaku", "DanDanPlay episode not found: animeId=$animeId episode=$episode")
                return@withContext emptyList()
            }
            val items = fetchDanmakuByEpisodeId(episodeId)
            Log.d("Danmaku", "DanDanPlay fetched: ${items.size} items (episodeId=$episodeId)")
            items
        }

    private fun searchAnimeId(title: String): Int? {
        val url = buildUrl(
            "https://api.dandanplay.net",
            "/api/v2/search/episodes",
            mapOf("anime" to title),
        )
        val json = getJson(url) ?: run {
            Log.d("Danmaku", "DanDanPlay search failed: empty response title=$title")
            return null
        }
        val animes = json.optJSONArray("animes") ?: run {
            Log.d("Danmaku", "DanDanPlay search failed: no animes title=$title")
            return null
        }
        Log.d("Danmaku", "DanDanPlay search animes=${animes.length()} title=$title")
        for (i in 0 until minOf(5, animes.length())) {
            val obj = animes.optJSONObject(i) ?: continue
            val animeId = obj.optInt("animeId")
            val animeTitle = obj.optString("animeTitle")
            if (animeId > 0 && animeTitle.isNotBlank()) {
                Log.d("Danmaku", "DanDanPlay candidate[$i]: id=$animeId title=$animeTitle")
            }
        }
        var bestId: Int? = null
        var bestScore = 0.0
        for (i in 0 until animes.length()) {
            val obj = animes.optJSONObject(i) ?: continue
            val animeId = obj.optInt("animeId")
            val animeTitle = obj.optString("animeTitle")
            if (animeId <= 0 || animeTitle.isBlank()) continue
            val score = similarity(normalizeTitle(animeTitle), normalizeTitle(title))
            if (score > bestScore) {
                bestScore = score
                bestId = animeId
            }
        }
        Log.d("Danmaku", "DanDanPlay best animeId=$bestId score=$bestScore for title=$title")
        return bestId
    }

    private fun findEpisodeId(animeId: Int, episode: Int): Int? {
        val url = buildUrl("https://api.dandanplay.net", "/api/v2/bangumi/$animeId", emptyMap())
        val json = getJson(url) ?: return null
        val episodes = json.optJSONArray("episodes") ?: return null
        var bestId: Int? = null
        var bestScore = Int.MAX_VALUE
        for (i in 0 until episodes.length()) {
            val obj = episodes.optJSONObject(i) ?: continue
            val episodeId = obj.optInt("episodeId")
            val episodeNumber = obj.optInt("episodeNumber", 0)
            val episodeTitle = obj.optString("episodeTitle")
            val parsed = if (episodeNumber > 0) episodeNumber else parseEpisodeNumber(episodeTitle)
            if (episodeId <= 0 || parsed <= 0) continue
            val diff = kotlin.math.abs(parsed - episode)
            if (diff < bestScore) {
                bestScore = diff
                bestId = episodeId
                if (diff == 0) break
            }
        }
        Log.d("Danmaku", "DanDanPlay best episodeId=$bestId diff=$bestScore for episode=$episode")
        return bestId
    }

    private fun fetchDanmakuByEpisodeId(episodeId: Int): List<DanmakuItem> {
        val url = buildUrl(
            "https://api.dandanplay.net",
            "/api/v2/comment/$episodeId",
            mapOf("withRelated" to "true"),
        )
        val json = getJson(url) ?: return emptyList()
        val comments = json.optJSONArray("comments") ?: return emptyList()
        val result = ArrayList<DanmakuItem>(comments.length())
        for (i in 0 until comments.length()) {
            val item = comments.optJSONObject(i) ?: continue
            val message = item.optString("m")
            val p = item.optString("p")
            if (message.isBlank() || p.isBlank()) continue
            val parts = p.split(',')
            if (parts.size < 3) continue
            val timeMs = (parts[0].toDoubleOrNull() ?: 0.0) * 1000
            val type = when (parts[1].toIntOrNull()) {
                4 -> DanmakuType.BOTTOM
                5 -> DanmakuType.TOP
                else -> DanmakuType.SCROLL
            }
            val colorValue = parts[2].toIntOrNull() ?: 0xFFFFFF
            val source = extractSource(item, parts)
            val color = Color.rgb(
                (colorValue shr 16) and 0xFF,
                (colorValue shr 8) and 0xFF,
                colorValue and 0xFF,
            )
            result.add(
                DanmakuItem(
                    message = message,
                    timeMs = timeMs.toLong(),
                    type = type,
                    color = color,
                    source = source,
                )
            )
        }
        return result
    }

    private fun getJson(url: String): JSONObject? {
        val httpUrl = url.toHttpUrl()
        val path = httpUrl.encodedPath
        val timestamp = System.currentTimeMillis() / 1000
        val appId = BuildConfig.DANDANPLAY_APP_ID
        val appSecret = BuildConfig.DANDANPLAY_APP_SECRET
        val signature = generateSignature(appId, appSecret, timestamp, path)
        val request = Request.Builder()
            .url(httpUrl)
            .get()
            .header("User-Agent", "DandanPlay/1.0 (Android)")
            .header("Accept", "application/json")
            .header("Accept-Encoding", "gzip, deflate")
            .header("Referer", "https://www.dandanplay.com/")
            .header("Origin", "https://www.dandanplay.com")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,ja;q=0.7")
            .header("X-Auth", "1")
            .header("X-AppId", appId)
            .header("X-Timestamp", timestamp.toString())
            .header("X-Signature", signature)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful || body.isNullOrBlank()) {
                val snippet = body?.take(200).orEmpty()
                Log.d(
                    "Danmaku",
                    "DanDanPlay http failed: code=${response.code} msg=${response.message} url=$url body=$snippet"
                )
                return null
            }
            return JSONObject(body)
        }
    }

    private fun generateSignature(
        appId: String,
        appSecret: String,
        timestamp: Long,
        path: String,
    ): String {
        val data = appId + timestamp.toString() + path + appSecret
        val digest = MessageDigest.getInstance("SHA-256").digest(data.toByteArray())
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun buildUrl(base: String, path: String, params: Map<String, String>): String {
        val builder = base.toHttpUrl().newBuilder().addEncodedPathSegments(path.trimStart('/'))
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }

    private fun parseEpisodeNumber(title: String): Int {
        val match = Regex("(\\d+)").find(title) ?: return 0
        return match.groupValues[1].toIntOrNull() ?: 0
    }

    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("\\s+"), "")
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fff\\u3040-\\u30ff\\u31f0-\\u31ff\\uff66-\\uff9d]"), "")
    }

    private fun extractSource(item: JSONObject, parts: List<String>): String {
        val source = item.optString("source")
            .ifBlank { item.optString("src") }
            .ifBlank { parts.getOrNull(3).orEmpty() }
            .trim()
        return if (source.isBlank()) "DanDanPlay" else source
    }

    private fun similarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        val distance = levenshteinDistance(a, b)
        val maxLen = max(a.length, b.length).coerceAtLeast(1)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
                prev = temp
            }
        }
        return dp[b.length]
    }
}
