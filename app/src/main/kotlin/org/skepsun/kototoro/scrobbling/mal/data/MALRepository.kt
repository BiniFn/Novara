package org.skepsun.kototoro.scrobbling.mal.data

import android.content.Context
import android.util.Base64
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.parsers.util.await
import org.skepsun.kototoro.parsers.util.json.getStringOrNull
import org.skepsun.kototoro.parsers.util.json.mapJSONNotNull
import org.skepsun.kototoro.parsers.util.parseJson
import org.skepsun.kototoro.scrobbling.common.data.ScrobblerRepository
import org.skepsun.kototoro.scrobbling.common.data.ScrobblerStorage
import org.skepsun.kototoro.scrobbling.common.data.ScrobblingEntity
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContent
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContentInfo
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerType
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerUser
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

private const val REDIRECT_URI = "kototoro://mal-auth"
private const val BASE_WEB_URL = "https://myanimelist.net"
private const val BASE_API_URL = "https://api.myanimelist.net/v2"
private const val ANIME_ENDPOINT = "anime"
private const val MANGA_ENDPOINT = "manga"

@Singleton
class MALRepository @Inject constructor(
	@ApplicationContext context: Context,
	@ScrobblerType(ScrobblerService.MAL) private val okHttp: OkHttpClient,
	@ScrobblerType(ScrobblerService.MAL) private val storage: ScrobblerStorage,
	private val db: MangaDatabase,
) : ScrobblerRepository {

	private val clientId = context.getString(R.string.mal_clientId)
	private val codeVerifier: String by lazy(::generateCodeVerifier)
	private val contentTypeHints = LinkedHashMap<Long, String>()

	override val oauthUrl: String
		get() = "$BASE_WEB_URL/v1/oauth2/authorize?" +
			"response_type=code" +
			"&client_id=$clientId" +
			"&redirect_uri=$REDIRECT_URI" +
			"&code_challenge=$codeVerifier" +
			"&code_challenge_method=plain"

	override val isAuthorized: Boolean
		get() = storage.accessToken != null

	override val cachedUser: ScrobblerUser?
		get() {
			return storage.user
		}

	override suspend fun authorize(code: String?) {
		val body = FormBody.Builder()
		if (code != null) {
			body.add("client_id", clientId)
			body.add("grant_type", "authorization_code")
			body.add("code", code)
			body.add("redirect_uri", REDIRECT_URI)
			body.add("code_verifier", codeVerifier)
		}
		val request = Request.Builder()
			.post(body.build())
			.url("${BASE_WEB_URL}/v1/oauth2/token")

		val response = okHttp.newCall(request.build()).await().parseJson()
		storage.accessToken = response.getString("access_token")
		storage.refreshToken = response.getString("refresh_token")
	}

	override suspend fun loadUser(): ScrobblerUser {
		val request = Request.Builder()
			.get()
			.url("${BASE_API_URL}/users/@me")
		val response = okHttp.newCall(request.build()).await().parseJson()
		return MALUser(response).also { storage.user = it }
	}

	override suspend fun unregister(mangaId: Long) {
		return db.getScrobblingDao().delete(ScrobblerService.MAL.id, mangaId)
	}

	override suspend fun findContent(query: String, offset: Int, isAnime: Boolean): List<ScrobblerContent> {
		val endpoint = mediaEndpoint(isAnime)
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment(endpoint)
			.addQueryParameter("offset", offset.toString())
			.addQueryParameter("nsfw", "true")
			// WARNING! MAL API throws a 400 when the query is over 64 characters
			.addQueryParameter("q", query.take(64))
			.build()
		val request = Request.Builder().url(url).header("X-MAL-CLIENT-ID", clientId).get().build()
		val response = okHttp.newCall(request).await().parseJson()
		check(response.has("data")) { "Invalid response: \"$response\"" }
		val data = response.getJSONArray("data")
		return data.mapJSONNotNull { jsonToContent(it, query, endpoint) }
	}

	// ── Discovery API (public, uses X-MAL-CLIENT-ID) ─────────────

	/**
	 * Get anime ranking from MAL.
	 * @param rankingType "all", "airing", "upcoming", "tv", "ova", "movie", "special", "bypopularity", "favorite"
	 * @param limit max items per page
	 * @param offset pagination offset
	 */
	suspend fun getAnimeRanking(rankingType: String = "all", limit: Int = 20, offset: Int = 0): List<ScrobblerContent> {
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment(ANIME_ENDPOINT)
			.addPathSegment("ranking")
			.addQueryParameter("ranking_type", rankingType)
			.addQueryParameter("limit", limit.toString())
			.addQueryParameter("offset", offset.toString())
			.addQueryParameter("nsfw", "true")
			.build()
		val request = Request.Builder().url(url)
			.header("X-MAL-CLIENT-ID", clientId)
			.get().build()
		return parseRankingList(okHttp.newCall(request).await().parseJson(), "anime")
	}

	/**
	 * Get seasonal anime from MAL.
	 * @param year e.g. 2026
	 * @param season "winter", "spring", "summer", "fall"
	 * @param sort "anime_score" or "anime_num_list_users"
	 */
	suspend fun getSeasonalAnime(year: Int, season: String, sort: String = "anime_num_list_users", limit: Int = 20, offset: Int = 0): List<ScrobblerContent> {
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment(ANIME_ENDPOINT)
			.addPathSegment("season")
			.addPathSegment(year.toString())
			.addPathSegment(season)
			.addQueryParameter("sort", sort)
			.addQueryParameter("limit", limit.toString())
			.addQueryParameter("offset", offset.toString())
			.addQueryParameter("nsfw", "true")
			.build()
		val request = Request.Builder().url(url)
			.header("X-MAL-CLIENT-ID", clientId)
			.get().build()
		return parseRankingList(okHttp.newCall(request).await().parseJson(), "anime")
	}

	/**
	 * Get manga ranking from MAL.
	 */
	suspend fun getMangaRanking(rankingType: String = "all", limit: Int = 20, offset: Int = 0): List<ScrobblerContent> {
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment(MANGA_ENDPOINT)
			.addPathSegment("ranking")
			.addQueryParameter("ranking_type", rankingType)
			.addQueryParameter("limit", limit.toString())
			.addQueryParameter("offset", offset.toString())
			.addQueryParameter("nsfw", "true")
			.build()
		val request = Request.Builder().url(url)
			.header("X-MAL-CLIENT-ID", clientId)
			.get().build()
		return parseRankingList(okHttp.newCall(request).await().parseJson(), "manga")
	}

	/**
	 * Search anime by text query.
	 */
	suspend fun searchAnime(query: String, offset: Int): List<ScrobblerContent> {
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment(ANIME_ENDPOINT)
			.addQueryParameter("offset", offset.toString())
			.addQueryParameter("nsfw", "true")
			.addQueryParameter("q", query.take(64))
			.build()
		val request = Request.Builder().url(url)
			.header("X-MAL-CLIENT-ID", clientId)
			.get().build()
		val response = okHttp.newCall(request).await().parseJson()
		check(response.has("data")) { "Invalid response: \"$response\"" }
		return response.getJSONArray("data").mapJSONNotNull { jo ->
			val node = jo.getJSONObject("node")
			ScrobblerContent(
				id = node.getLong("id"),
				name = node.getString("title"),
				altName = null,
				cover = node.optJSONObject("main_picture")?.getStringOrNull("large"),
				url = "$BASE_WEB_URL/anime/${node.getLong("id")}",
				mediaType = ANIME_ENDPOINT,
			)
		}
	}

	private fun parseRankingList(json: JSONObject, mediaType: String): List<ScrobblerContent> {
		val data = json.optJSONArray("data") ?: return emptyList()
		return data.mapJSONNotNull { jo ->
			val node = jo.getJSONObject("node")
			ScrobblerContent(
				id = node.getLong("id"),
				name = node.getString("title"),
				altName = null,
				cover = node.optJSONObject("main_picture")?.getStringOrNull("large"),
				url = "$BASE_WEB_URL/$mediaType/${node.getLong("id")}",
			)
		}
	}

	private suspend fun isAnime(mangaId: Long): Boolean {
		val mangaItem = db.getMangaDao().find(mangaId) ?: return false
		if (mangaItem.manga.url.startsWith("file://") && (mangaItem.manga.url.contains("/video/") || arrayOf(".mp4", ".mkv", ".webm", ".ts", ".avi", ".m3u8").any { mangaItem.manga.url.endsWith(it, ignoreCase = true) })) {
			return true
		}
		val source = org.skepsun.kototoro.core.model.ContentSource(mangaItem.manga.source)
		val contentType = source.getContentType()
		return contentType == org.skepsun.kototoro.parsers.model.ContentType.VIDEO || contentType == org.skepsun.kototoro.parsers.model.ContentType.HENTAI_VIDEO
	}

	override suspend fun getContentInfo(id: Long): ScrobblerContentInfo {
		val endpoint = contentTypeHints[id] ?: db.getScrobblingDao()
			.findAllByScrobbler(ScrobblerService.MAL.id)
			.firstOrNull { it.targetId == id }
			?.let { mediaEndpoint(isAnime(it.mangaId)) }
			?: MANGA_ENDPOINT
		return getContentInfo(id, endpoint)
	}

	suspend fun getContentInfo(id: Long, mangaId: Long): ScrobblerContentInfo {
		return getContentInfo(id, resolvedEndpoint(id, mangaId))
	}

	private suspend fun getContentInfo(id: Long, endpoint: String): ScrobblerContentInfo {
		val response = requestContentInfo(id, endpoint)
		if (response.has("id")) {
			contentTypeHints[id] = endpoint
			return ScrobblerContentInfo(response, endpoint)
		}
		val fallbackEndpoint = alternateEndpoint(endpoint)
		val fallbackResponse = requestContentInfo(id, fallbackEndpoint)
		check(fallbackResponse.has("id")) { "Invalid MAL content response for $id: \"$fallbackResponse\"" }
		contentTypeHints[id] = fallbackEndpoint
		return ScrobblerContentInfo(fallbackResponse, fallbackEndpoint)
	}

	/**
	 * Get anime details from MAL by anime ID.
	 * Uses the /anime/{id} endpoint instead of /manga/{id}.
	 */
	suspend fun getAnimeInfo(id: Long): ScrobblerContentInfo {
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment(ANIME_ENDPOINT)
			.addPathSegment(id.toString())
			.addQueryParameter("fields", "synopsis,genres,mean,num_episodes,rank,start_season,status,media_type")
			.build()
		val request = Request.Builder().url(url)
			.header("X-MAL-CLIENT-ID", clientId)
			.get().build()
		val response = okHttp.newCall(request).await().parseJson()
		return ScrobblerContentInfo(response, "anime")
	}

	override suspend fun createRate(mangaId: Long, content: ScrobblerContent) {
		val scrobblerContentId = content.id
		content.mediaType?.let { contentTypeHints[scrobblerContentId] = it }
		val endpoint = content.mediaType ?: resolvedEndpoint(scrobblerContentId, mangaId)
		val body = FormBody.Builder()
			.add("status", defaultStatus(endpoint == ANIME_ENDPOINT))
			.add("score", "0")
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment(endpoint)
			.addPathSegment(scrobblerContentId.toString())
			.addPathSegment("my_list_status")
			.addQueryParameter("fields", "synopsis")
			.build()
		val request = Request.Builder()
			.url(url)
			.put(body.build())
			.build()
		val response = okHttp.newCall(request).await().parseJson()
		saveRate(response, mangaId, scrobblerContentId, endpoint)
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, chapter: Int) {
		val endpoint = resolvedEndpoint(rateId.toLong(), mangaId)
		val body = FormBody.Builder()
			.add(progressField(endpoint), chapter.toString())
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment(endpoint)
			.addPathSegment(rateId.toString())
			.addPathSegment("my_list_status")
			.build()
		val request = Request.Builder()
			.url(url)
			.put(body.build())
			.build()
		val response = okHttp.newCall(request).await().parseJson()
		saveRate(response, mangaId, rateId.toLong(), endpoint)
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, rating: Float, status: String?, comment: String?) {
		val endpoint = resolvedEndpoint(rateId.toLong(), mangaId)
		val mappedStatus = mapStatusForEndpoint(status, endpoint)
		val body = FormBody.Builder()
			.add("status", mappedStatus.toString())
			.add("score", rating.toInt().toString())
			.add("comments", comment.orEmpty())
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment(endpoint)
			.addPathSegment(rateId.toString())
			.addPathSegment("my_list_status")
			.build()
		val request = Request.Builder()
			.url(url)
			.put(body.build())
			.build()
		val response = okHttp.newCall(request).await().parseJson()
		saveRate(response, mangaId, rateId.toLong(), endpoint)
	}

	/**
	 * Sync both anime and manga lists from MAL to local database.
	 * Uses MAL API:
	 * GET /v2/users/@me/animelist?fields=list_status&limit=100
	 * GET /v2/users/@me/mangalist?fields=list_status&limit=100
	 */
	suspend fun syncLibraryFromRemote(): Int {
		val oldMappings = buildOldMappings()
		val synced = ArrayList<ScrobblingEntity>()
		synced += syncRemoteLibraryEntries(ANIME_ENDPOINT, oldMappings)
		synced += syncRemoteLibraryEntries(MANGA_ENDPOINT, oldMappings)

		db.withTransaction {
			db.getScrobblingDao().deleteByScrobbler(ScrobblerService.MAL.id)
			synced.forEach { entity ->
				db.getScrobblingDao().upsert(entity)
			}
		}
		return synced.size
	}

	private suspend fun saveRate(json: JSONObject, mangaId: Long, scrobblerContentId: Long, endpoint: String) {
		val statusJson = json.optJSONObject("my_list_status") ?: json
		contentTypeHints[scrobblerContentId] = endpoint
		val entity = ScrobblingEntity(
			scrobbler = ScrobblerService.MAL.id,
			id = scrobblerContentId.toInt(),
			mangaId = mangaId,
			targetId = scrobblerContentId,
			status = statusJson.optString("status", defaultStatus(endpoint == ANIME_ENDPOINT)),
			chapter = statusJson.optInt(progressField(endpoint), 0),
			comment = statusJson.optString("comments", ""),
			rating = (statusJson.optDouble("score", 0.0).toFloat() / 10f).coerceIn(0f, 1f),
		)
		db.getScrobblingDao().upsert(entity)
	}

	private suspend fun buildOldMappings(): Map<RemoteListKey, Long> {
		val existing = db.getScrobblingDao().findAllByScrobbler(ScrobblerService.MAL.id)
		val mappings = LinkedHashMap<RemoteListKey, Long>(existing.size)
		for (entity in existing) {
			val targetId = entity.targetId
			if (targetId == 0L) continue
			val endpoint = when {
				entity.mangaId == 0L -> null
				isAnime(entity.mangaId) -> ANIME_ENDPOINT
				else -> MANGA_ENDPOINT
			} ?: continue
			mappings.putIfAbsent(RemoteListKey(endpoint, targetId), entity.mangaId)
		}
		return mappings
	}

	private suspend fun syncRemoteLibraryEntries(
		endpoint: String,
		oldMappings: Map<RemoteListKey, Long>,
	): List<ScrobblingEntity> {
		val synced = ArrayList<ScrobblingEntity>()
		var nextUrl: String? = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment("users")
			.addPathSegment("@me")
			.addPathSegment("${endpoint}list")
			.addQueryParameter("fields", "list_status{status,score,${progressField(endpoint)},comments}")
			.addQueryParameter("limit", "100")
			.addQueryParameter("nsfw", "true")
			.build()
			.toString()

		while (nextUrl != null) {
			val request = Request.Builder().url(nextUrl).get().build()
			val response = okHttp.newCall(request).await().parseJson()
			val data = response.optJSONArray("data") ?: break

			for (i in 0 until data.length()) {
				val entry = data.optJSONObject(i) ?: continue
				val node = entry.optJSONObject("node") ?: continue
				val listStatus = entry.optJSONObject("list_status") ?: continue
				val targetId = node.optLong("id", 0L)
				if (targetId == 0L) continue
				synced.add(
					ScrobblingEntity(
						scrobbler = ScrobblerService.MAL.id,
						id = targetId.toInt(),
						mangaId = oldMappings[RemoteListKey(endpoint, targetId)] ?: 0L,
						targetId = targetId,
						status = listStatus.optString("status", ""),
						chapter = listStatus.optInt(progressField(endpoint), 0),
						comment = listStatus.optString("comments", ""),
						rating = (listStatus.optDouble("score", 0.0).toFloat() / 10f).coerceIn(0f, 1f),
					),
				)
				contentTypeHints[targetId] = endpoint
			}

			nextUrl = response.optJSONObject("paging")?.getStringOrNull("next")
		}

		return synced
	}

	private fun mediaEndpoint(isAnime: Boolean): String {
		return if (isAnime) ANIME_ENDPOINT else MANGA_ENDPOINT
	}

	private suspend fun resolvedEndpoint(targetId: Long, mangaId: Long): String {
		return contentTypeHints[targetId] ?: mediaEndpoint(isAnime(mangaId))
	}

	private suspend fun requestContentInfo(id: Long, endpoint: String): JSONObject {
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment(endpoint)
			.addPathSegment(id.toString())
			.addQueryParameter("fields", detailFields(endpoint))
			.build()
		val request = Request.Builder().url(url)
		return okHttp.newCall(request.build()).await().parseJson()
	}

	private fun alternateEndpoint(endpoint: String): String {
		return if (endpoint == ANIME_ENDPOINT) MANGA_ENDPOINT else ANIME_ENDPOINT
	}

	private fun defaultStatus(isAnime: Boolean): String {
		return if (isAnime) "watching" else "reading"
	}

	private fun mapStatusForEndpoint(status: String?, endpoint: String): String? {
		if (endpoint != ANIME_ENDPOINT) return status
		return when (status) {
			"reading" -> "watching"
			"plan_to_read" -> "plan_to_watch"
			else -> status
		}
	}

	private fun progressField(endpoint: String): String {
		return if (endpoint == ANIME_ENDPOINT) "num_watched_episodes" else "num_chapters_read"
	}

	private fun detailFields(endpoint: String): String {
		return if (endpoint == ANIME_ENDPOINT) {
			"synopsis,genres,mean,num_episodes,rank,start_season,status,media_type"
		} else {
			"synopsis,genres,mean,num_chapters,rank,start_date,status,media_type,authors"
		}
	}

	override fun logout() {
		storage.clear()
	}

	private fun jsonToContent(json: JSONObject, sourceTitle: String, mediaType: String): ScrobblerContent {
		val node = json.getJSONObject("node")
		val title = node.getString("title")
		val id = node.getLong("id")
		contentTypeHints[id] = mediaType
		return ScrobblerContent(
			id = id,
			name = title,
			altName = null,
			cover = node.optJSONObject("main_picture")?.getStringOrNull("large"),
			url = "$BASE_WEB_URL/$mediaType/$id",
			mediaType = mediaType,
			isBestMatch = title.equals(sourceTitle, ignoreCase = true),
		)
	}

	private fun ScrobblerContentInfo(json: JSONObject, mediaType: String): ScrobblerContentInfo {
		val tags = json.optJSONArray("genres")?.mapJSONNotNull {
			it.getStringOrNull("name")
		}.orEmpty()
		val authors = json.optJSONArray("authors")?.mapJSONNotNull { edge ->
			val node = edge.optJSONObject("node") ?: return@mapJSONNotNull null
			listOfNotNull(
				node.getStringOrNull("first_name")?.takeIf { it.isNotBlank() },
				node.getStringOrNull("last_name")?.takeIf { it.isNotBlank() },
			).joinToString(" ").ifBlank { null }
		}.orEmpty()
		val infobox = buildList {
			json.optDouble("mean").takeIf { !it.isNaN() && it > 0.0 }?.let {
				add("Score" to it.toString())
			}
			json.optInt("rank").takeIf { it > 0 }?.let {
				add("Rank" to "#$it")
			}
			json.getStringOrNull("status")?.takeIf { it.isNotBlank() }?.let {
				add("Status" to it)
			}
			json.getStringOrNull("media_type")?.takeIf { it.isNotBlank() }?.let {
				add("Type" to it)
			}
			if (mediaType == ANIME_ENDPOINT) {
				json.optInt("num_episodes").takeIf { it > 0 }?.let {
					add("Episodes" to it.toString())
				}
				json.optJSONObject("start_season")?.let { season ->
					val year = season.optInt("year", 0)
					val value = listOfNotNull(
						season.getStringOrNull("season"),
						year.takeIf { it > 0 }?.toString(),
					).joinToString(" ")
					if (value.isNotBlank()) {
						add("Season" to value)
					}
				}
			} else {
				json.optInt("num_chapters").takeIf { it > 0 }?.let {
					add("Chapters" to it.toString())
				}
				json.getStringOrNull("start_date")?.takeIf { it.isNotBlank() }?.let {
					add("Start date" to it)
				}
			}
		}
		return ScrobblerContentInfo(
			id = json.getLong("id"),
			name = json.getString("title"),
			cover = json.optJSONObject("main_picture")?.getStringOrNull("large") ?: "",
			url = "$BASE_WEB_URL/$mediaType/${json.getLong("id")}",
			descriptionHtml = json.optString("synopsis", ""),
			tags = tags,
			authors = authors,
			infoboxProperties = infobox,
		)
	}

	@Suppress("FunctionName")
	private fun MALUser(json: JSONObject) = ScrobblerUser(
		id = json.getLong("id"),
		nickname = json.getString("name"),
		avatar = json.getStringOrNull("picture"),
		service = ScrobblerService.MAL,
	)

	private fun generateCodeVerifier(): String {
		val codeVerifier = ByteArray(50)
		SecureRandom().nextBytes(codeVerifier)
		return Base64.encodeToString(codeVerifier, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
	}

	private data class RemoteListKey(
		val endpoint: String,
		val targetId: Long,
	)
}
