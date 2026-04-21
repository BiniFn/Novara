package org.skepsun.kototoro.tracking.malsync.data

import androidx.collection.LruCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.skepsun.kototoro.core.network.BaseHttpClient
import org.skepsun.kototoro.parsers.util.await
import org.skepsun.kototoro.parsers.util.parseJson
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps a (service, remoteId) pair to the equivalent entries on other tracking sites using MALSync.
 *
 * Uses the free public endpoint: https://api.malsync.moe/{service}/{kind}/{id}
 * Response shape: { "Sites": { "Anilist": { "<id>": { identifier, url, title, ... }, ... }, ... } }
 */
@Singleton
class MALSyncMappingRepository @Inject constructor(
    @BaseHttpClient private val okHttpClient: OkHttpClient,
) {

    enum class Kind(val slug: String) { MANGA("manga"), ANIME("anime") }

    data class Mapping(
        val service: ScrobblerService,
        val remoteId: Long,
        val title: String?,
        val url: String?,
    )

    private val cache = LruCache<String, List<Mapping>>(CACHE_SIZE)
    private val inflightMutex = Mutex()
    private val perKeyMutexes = mutableMapOf<String, Mutex>()

    suspend fun resolve(service: ScrobblerService, remoteId: Long, kind: Kind): List<Mapping> {
        val servicePath = servicePath(service) ?: return emptyList()
        val key = "$servicePath:${kind.slug}:$remoteId"
        cache.get(key)?.let { return it }

        val mutex = inflightMutex.withLock {
            perKeyMutexes.getOrPut(key) { Mutex() }
        }
        return mutex.withLock {
            cache.get(key)?.let { return@withLock it }
            val fetched = runCatching { fetch(servicePath, kind, remoteId, service) }
                .getOrDefault(emptyList())
            cache.put(key, fetched)
            fetched
        }
    }

    private suspend fun fetch(
        servicePath: String,
        kind: Kind,
        remoteId: Long,
        source: ScrobblerService,
    ): List<Mapping> {
        val url = "$BASE_URL/$servicePath/${kind.slug}/$remoteId"
        val request = Request.Builder().url(url).get().build()
        val response = okHttpClient.newCall(request).await()
        if (!response.isSuccessful) {
            response.close()
            return emptyList()
        }
        val json = response.parseJson()
        val sitesObj = json.optJSONObject("Sites") ?: return emptyList()
        val results = mutableListOf<Mapping>()
        val sourceKey = servicePath.lowercase()
        sitesObj.keys().forEach { siteKey ->
            val site = resolveServiceFromSiteKey(siteKey) ?: return@forEach
            if (site == source) return@forEach
            val entries = sitesObj.optJSONObject(siteKey) ?: return@forEach
            entries.keys().forEach inner@{ idKey ->
                val entry = entries.optJSONObject(idKey) ?: return@inner
                val parsedId = entry.optString("identifier")
                    .takeIf { it.isNotBlank() }
                    ?.toLongOrNull()
                    ?: idKey.toLongOrNull()
                    ?: return@inner
                results += Mapping(
                    service = site,
                    remoteId = parsedId,
                    title = entry.optString("title").ifBlank { null },
                    url = entry.optString("url").ifBlank { null },
                )
            }
        }
        return results.distinctBy { it.service to it.remoteId }
    }

    private fun servicePath(service: ScrobblerService): String? = when (service) {
        ScrobblerService.MAL -> "mal"
        ScrobblerService.ANILIST -> "anilist"
        ScrobblerService.KITSU -> "kitsu"
        ScrobblerService.SHIKIMORI -> "shikimori"
        else -> null
    }

    private fun resolveServiceFromSiteKey(siteKey: String): ScrobblerService? =
        when (siteKey.lowercase()) {
            "mal" -> ScrobblerService.MAL
            "anilist" -> ScrobblerService.ANILIST
            "kitsu" -> ScrobblerService.KITSU
            "shikimori" -> ScrobblerService.SHIKIMORI
            "bangumi" -> ScrobblerService.BANGUMI
            "mangaupdates" -> ScrobblerService.MANGAUPDATES
            else -> null
        }

    private companion object {
        const val BASE_URL = "https://api.malsync.moe"
        const val CACHE_SIZE = 64
    }
}
