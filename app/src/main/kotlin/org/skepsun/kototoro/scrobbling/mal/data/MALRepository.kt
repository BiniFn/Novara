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

@Singleton
class MALRepository @Inject constructor(
	@ApplicationContext context: Context,
	@ScrobblerType(ScrobblerService.MAL) private val okHttp: OkHttpClient,
	@ScrobblerType(ScrobblerService.MAL) private val storage: ScrobblerStorage,
	private val db: MangaDatabase,
) : ScrobblerRepository {

	private val clientId = context.getString(R.string.mal_clientId)
	private val codeVerifier: String by lazy(::generateCodeVerifier)

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
		val endpoint = if (isAnime) "anime" else "manga"
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
		return data.mapJSONNotNull { jsonToContent(it, query) }
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
			.addPathSegment("anime")
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
			.addPathSegment("anime")
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
			.addPathSegment("manga")
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
			.addPathSegment("anime")
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
		val source = org.skepsun.kototoro.core.model.ContentSource(mangaItem.manga.source)
		val contentType = source.getContentType()
		return contentType == org.skepsun.kototoro.parsers.model.ContentType.VIDEO || contentType == org.skepsun.kototoro.parsers.model.ContentType.HENTAI_VIDEO
	}

	override suspend fun getContentInfo(id: Long): ScrobblerContentInfo {
		// This method might need mangaId to know if it's anime or manga
		// But id is the scrobbler ID here!
		// For now we assume manga, maybe we need getContentInfo signature change later if it fails on anime
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment("manga")
			.addPathSegment(id.toString())
			.addQueryParameter("fields", "synopsis")
			.build()
		val request = Request.Builder().url(url)
		val response = okHttp.newCall(request.build()).await().parseJson()
		return ScrobblerContentInfo(response, "manga")
	}

	/**
	 * Get anime details from MAL by anime ID.
	 * Uses the /anime/{id} endpoint instead of /manga/{id}.
	 */
	suspend fun getAnimeInfo(id: Long): ScrobblerContentInfo {
		val url = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment("anime")
			.addPathSegment(id.toString())
			.addQueryParameter("fields", "synopsis,genres,mean,num_episodes,rank,start_season,status,media_type")
			.build()
		val request = Request.Builder().url(url)
			.header("X-MAL-CLIENT-ID", clientId)
			.get().build()
		val response = okHttp.newCall(request).await().parseJson()
		return ScrobblerContentInfo(response, "anime")
	}

	override suspend fun createRate(mangaId: Long, scrobblerContentId: Long) {
		val endpoint = if (isAnime(mangaId)) "anime" else "manga"
		val body = FormBody.Builder()
			.add("status", "reading")
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
		saveRate(response, mangaId, scrobblerContentId)
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, chapter: Int) {
		val endpoint = if (isAnime(mangaId)) "anime" else "manga"
		val numKey = if (endpoint == "anime") "num_watched_episodes" else "num_chapters_read"
		val body = FormBody.Builder()
			.add(numKey, chapter.toString())
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
		saveRate(response, mangaId, rateId.toLong())
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, rating: Float, status: String?, comment: String?) {
		val endpoint = if (isAnime(mangaId)) "anime" else "manga"
		val mappedStatus = if (endpoint == "anime") {
			when (status) {
				"reading" -> "watching"
				"plan_to_read" -> "plan_to_watch"
				else -> status
			}
		} else {
			status
		}
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
		saveRate(response, mangaId, rateId.toLong())
	}

	/**
	 * Sync all manga list from MAL to local database.
	 * Uses MAL API: GET /v2/users/@me/mangalist?fields=list_status&limit=100
	 */
	suspend fun syncLibraryFromRemote(): Int {
		val oldMappings = db.getScrobblingDao()
			.findAllByScrobbler(ScrobblerService.MAL.id)
			.groupBy { it.targetId }
			.mapValues { (_, values) ->
				values.firstOrNull { it.mangaId != 0L }?.mangaId ?: 0L
			}

		val synced = ArrayList<ScrobblingEntity>()
		var nextUrl: String? = BASE_API_URL.toHttpUrl().newBuilder()
			.addPathSegment("users")
			.addPathSegment("@me")
			.addPathSegment("mangalist")
			.addQueryParameter("fields", "list_status{status,score,num_chapters_read,comments}")
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
				val mangaId = node.optLong("id", 0L)
				if (mangaId == 0L) continue
				val mappedContentId = oldMappings[mangaId] ?: 0L
				synced.add(
					ScrobblingEntity(
						scrobbler = ScrobblerService.MAL.id,
						id = mangaId.toInt(),
						mangaId = mappedContentId,
						targetId = mangaId,
						status = listStatus.optString("status", ""),
						chapter = listStatus.optInt("num_chapters_read", 0),
						comment = listStatus.optString("comments", ""),
						rating = (listStatus.optDouble("score", 0.0).toFloat() / 10f).coerceIn(0f, 1f),
					),
				)
			}

			// MAL uses "paging.next" for pagination
			nextUrl = response.optJSONObject("paging")?.getStringOrNull("next")
		}

		db.withTransaction {
			db.getScrobblingDao().deleteByScrobbler(ScrobblerService.MAL.id)
			synced.forEach { entity ->
				db.getScrobblingDao().upsert(entity)
			}
		}
		return synced.size
	}

	private suspend fun saveRate(json: JSONObject, mangaId: Long, scrobblerContentId: Long) {
		val entity = ScrobblingEntity(
			scrobbler = ScrobblerService.MAL.id,
			id = scrobblerContentId.toInt(),
			mangaId = mangaId,
			targetId = scrobblerContentId,
			status = json.getString("status"),
			chapter = json.getInt("num_chapters_read"),
			comment = json.getString("comments"),
			rating = (json.getDouble("score").toFloat() / 10f).coerceIn(0f, 1f),
		)
		db.getScrobblingDao().upsert(entity)
	}

	override fun logout() {
		storage.clear()
	}

	private fun jsonToContent(json: JSONObject, sourceTitle: String): ScrobblerContent {
		val node = json.getJSONObject("node")
		val title = node.getString("title")
		return ScrobblerContent(
			id = node.getLong("id"),
			name = title,
			altName = null,
			cover = node.optJSONObject("main_picture")?.getStringOrNull("large"),
			url = "$BASE_WEB_URL/manga/${node.getLong("id")}",
			isBestMatch = title.equals(sourceTitle, ignoreCase = true),
		)
	}

	private fun ScrobblerContentInfo(json: JSONObject, mediaType: String) = ScrobblerContentInfo(
		id = json.getLong("id"),
		name = json.getString("title"),
		cover = json.optJSONObject("main_picture")?.getStringOrNull("large") ?: "",
		url = "$BASE_WEB_URL/$mediaType/${json.getLong("id")}",
		descriptionHtml = json.optString("synopsis", ""),
	)

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
}
