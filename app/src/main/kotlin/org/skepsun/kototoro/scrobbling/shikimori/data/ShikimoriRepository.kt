package org.skepsun.kototoro.scrobbling.shikimori.data

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.util.ext.toRequestBody
import org.skepsun.kototoro.parsers.util.await
import org.skepsun.kototoro.parsers.util.json.getStringOrNull
import org.skepsun.kototoro.parsers.util.json.mapJSON
import org.skepsun.kototoro.parsers.util.parseJson
import org.skepsun.kototoro.parsers.util.parseJsonArray
import org.skepsun.kototoro.parsers.util.toAbsoluteUrl
import org.skepsun.kototoro.scrobbling.common.data.ScrobblerRepository
import org.skepsun.kototoro.scrobbling.common.data.ScrobblerStorage
import org.skepsun.kototoro.scrobbling.common.data.ScrobblingEntity
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContent
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContentInfo
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerType
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerUser
import javax.inject.Inject
import javax.inject.Singleton

private const val DOMAIN = "shikimori.one"
private const val REDIRECT_URI = "kotatsu://shikimori-auth"
private const val BASE_URL = "https://$DOMAIN/"
private const val MANGA_PAGE_SIZE = 10

@Singleton
class ShikimoriRepository @Inject constructor(
	@ApplicationContext context: Context,
	@ScrobblerType(ScrobblerService.SHIKIMORI) private val okHttp: OkHttpClient,
	@ScrobblerType(ScrobblerService.SHIKIMORI) private val storage: ScrobblerStorage,
	private val db: MangaDatabase,
) : ScrobblerRepository {

	private val clientId = context.getString(R.string.shikimori_clientId)
	private val clientSecret = context.getString(R.string.shikimori_clientSecret)

	override val oauthUrl: String
		get() = "${BASE_URL}oauth/authorize?client_id=$clientId&" +
			"redirect_uri=$REDIRECT_URI&response_type=code&scope="

	override val isAuthorized: Boolean
		get() = storage.accessToken != null

	override suspend fun authorize(code: String?) {
		val body = FormBody.Builder()
		body.add("client_id", clientId)
		body.add("client_secret", clientSecret)
		if (code != null) {
			body.add("grant_type", "authorization_code")
			body.add("redirect_uri", REDIRECT_URI)
			body.add("code", code)
		} else {
			body.add("grant_type", "refresh_token")
			body.add("refresh_token", checkNotNull(storage.refreshToken))
		}
		val request = Request.Builder()
			.post(body.build())
			.url("${BASE_URL}oauth/token")
		val response = okHttp.newCall(request.build()).await().parseJson()
		storage.accessToken = response.getString("access_token")
		storage.refreshToken = response.getString("refresh_token")
	}

	override suspend fun loadUser(): ScrobblerUser {
		val request = Request.Builder()
			.get()
			.url("${BASE_URL}api/users/whoami")
		val response = okHttp.newCall(request.build()).await().parseJson()
		return ShikimoriUser(response).also { storage.user = it }
	}

	override val cachedUser: ScrobblerUser?
		get() {
			return storage.user
		}

	override suspend fun unregister(mangaId: Long) {
		return db.getScrobblingDao().delete(ScrobblerService.SHIKIMORI.id, mangaId)
	}

	override fun logout() {
		storage.clear()
	}

	override suspend fun findContent(query: String, offset: Int): List<ScrobblerContent> {
		val page = offset / MANGA_PAGE_SIZE
		val pageOffset = offset % MANGA_PAGE_SIZE
		val url = BASE_URL.toHttpUrl().newBuilder()
			.addPathSegment("api")
			.addPathSegment("mangas")
			.addEncodedQueryParameter("page", (page + 1).toString())
			.addEncodedQueryParameter("limit", MANGA_PAGE_SIZE.toString())
			.addEncodedQueryParameter("censored", false.toString())
			.addQueryParameter("search", query)
			.build()
		val request = Request.Builder().url(url).get().build()
		val response = okHttp.newCall(request).await().parseJsonArray()
		val list = response.mapJSON { ScrobblerContent(it, query) }
		return if (pageOffset != 0) list.drop(pageOffset) else list
	}

	override suspend fun createRate(mangaId: Long, scrobblerContentId: Long) {
		val user = cachedUser ?: loadUser()
		val payload = JSONObject()
		payload.put(
			"user_rate",
			JSONObject().apply {
				put("target_id", scrobblerContentId)
				put("target_type", "Content")
				put("user_id", user.id)
			},
		)
		val url = BASE_URL.toHttpUrl().newBuilder()
			.addPathSegment("api")
			.addPathSegment("v2")
			.addPathSegment("user_rates")
			.build()
		val request = Request.Builder().url(url).post(payload.toRequestBody()).build()
		val response = okHttp.newCall(request).await().parseJson()
		saveRate(response, mangaId)
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, chapter: Int) {
		val payload = JSONObject()
		payload.put(
			"user_rate",
			JSONObject().apply {
				put("chapters", chapter)
			},
		)
		val url = BASE_URL.toHttpUrl().newBuilder()
			.addPathSegment("api")
			.addPathSegment("v2")
			.addPathSegment("user_rates")
			.addPathSegment(rateId.toString())
			.build()
		val request = Request.Builder().url(url).patch(payload.toRequestBody()).build()
		val response = okHttp.newCall(request).await().parseJson()
		saveRate(response, mangaId)
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, rating: Float, status: String?, comment: String?) {
		val payload = JSONObject()
		payload.put(
			"user_rate",
			JSONObject().apply {
				put("score", rating.toString())
				if (comment != null) {
					put("text", comment)
				}
				if (status != null) {
					put("status", status)
				}
			},
		)
		val url = BASE_URL.toHttpUrl().newBuilder()
			.addPathSegment("api")
			.addPathSegment("v2")
			.addPathSegment("user_rates")
			.addPathSegment(rateId.toString())
			.build()
		val request = Request.Builder().url(url).patch(payload.toRequestBody()).build()
		val response = okHttp.newCall(request).await().parseJson()
		saveRate(response, mangaId)
	}

	override suspend fun getContentInfo(id: Long): ScrobblerContentInfo {
		val request = Request.Builder()
			.get()
			.url("${BASE_URL}api/mangas/$id")
		val response = okHttp.newCall(request.build()).await().parseJson()
		return ScrobblerContentInfo(response)
	}

	/**
	 * Sync all manga rates from Shikimori to local database.
	 * Uses Shikimori API: GET /api/v2/user_rates?user_id={id}&target_type=Content
	 */
	suspend fun syncLibraryFromRemote(): Int {
		val user = cachedUser ?: loadUser()
		val oldMappings = db.getScrobblingDao()
			.findAllByScrobbler(ScrobblerService.SHIKIMORI.id)
			.groupBy { it.targetId }
			.mapValues { (_, values) ->
				values.firstOrNull { it.mangaId > 0L }?.mangaId ?: 0L
			}

		val synced = ArrayList<ScrobblingEntity>()
		var page = 1
		val limit = 50
		while (true) {
			val url = BASE_URL.toHttpUrl().newBuilder()
				.addPathSegment("api")
				.addPathSegment("v2")
				.addPathSegment("user_rates")
				.addEncodedQueryParameter("user_id", user.id.toString())
				.addEncodedQueryParameter("target_type", "Content")
				.addEncodedQueryParameter("page", page.toString())
				.addEncodedQueryParameter("limit", limit.toString())
				.build()
			val request = Request.Builder().url(url).get().build()
			val data = okHttp.newCall(request).await().parseJsonArray()
			if (data.length() == 0) break

			for (i in 0 until data.length()) {
				val json = data.optJSONObject(i) ?: continue
				val targetId = json.optLong("target_id", 0L)
				if (targetId == 0L) continue
				val mappedContentId = oldMappings[targetId] ?: 0L
				synced.add(
					ScrobblingEntity(
						scrobbler = ScrobblerService.SHIKIMORI.id,
						id = json.getInt("id"),
						mangaId = mappedContentId,
						targetId = targetId,
						status = json.getString("status"),
						chapter = json.getInt("chapters"),
						comment = json.optString("text", ""),
						rating = (json.getDouble("score").toFloat() / 10f).coerceIn(0f, 1f),
					),
				)
			}
			if (data.length() < limit) break
			page++
		}

		db.withTransaction {
			db.getScrobblingDao().deleteByScrobbler(ScrobblerService.SHIKIMORI.id)
			synced.forEach { entity ->
				db.getScrobblingDao().upsert(entity)
			}
		}
		return synced.size
	}

	// ── Discovery API (public, no auth required) ─────────────

	/**
	 * Get anime list from Shikimori with optional filters.
	 * @param order "ranked", "popularity", "aired_on", etc.
	 * @param status "ongoing", "anons", "released", or null for all
	 * @param season e.g. "spring_2026" or null for all
	 * @param kind "tv", "movie", "ova", "ona", etc. or null for all
	 */
	suspend fun getAnimeList(
		order: String = "ranked",
		status: String? = null,
		season: String? = null,
		kind: String? = null,
		page: Int = 1,
		limit: Int = 20,
	): List<ScrobblerContent> {
		val url = BASE_URL.toHttpUrl().newBuilder()
			.addPathSegment("api")
			.addPathSegment("animes")
			.addEncodedQueryParameter("page", page.toString())
			.addEncodedQueryParameter("limit", limit.toString())
			.addEncodedQueryParameter("order", order)
			.addEncodedQueryParameter("censored", "false")
			.apply {
				if (status != null) addEncodedQueryParameter("status", status)
				if (season != null) addEncodedQueryParameter("season", season)
				if (kind != null) addEncodedQueryParameter("kind", kind)
			}
			.build()
		val request = Request.Builder().url(url).get().build()
		val response = okHttp.newCall(request).await().parseJsonArray()
		return parseShikimoriList(response, "animes")
	}

	/**
	 * Get manga list from Shikimori with optional filters.
	 */
	suspend fun getMangaList(
		order: String = "ranked",
		status: String? = null,
		kind: String? = null,
		page: Int = 1,
		limit: Int = 20,
	): List<ScrobblerContent> {
		val url = BASE_URL.toHttpUrl().newBuilder()
			.addPathSegment("api")
			.addPathSegment("mangas")
			.addEncodedQueryParameter("page", page.toString())
			.addEncodedQueryParameter("limit", limit.toString())
			.addEncodedQueryParameter("order", order)
			.addEncodedQueryParameter("censored", "false")
			.apply {
				if (status != null) addEncodedQueryParameter("status", status)
				if (kind != null) addEncodedQueryParameter("kind", kind)
			}
			.build()
		val request = Request.Builder().url(url).get().build()
		val response = okHttp.newCall(request).await().parseJsonArray()
		return parseShikimoriList(response, "mangas")
	}

	/**
	 * Search anime by text query.
	 */
	suspend fun findAnime(query: String, offset: Int): List<ScrobblerContent> {
		val page = offset / MANGA_PAGE_SIZE
		val pageOffset = offset % MANGA_PAGE_SIZE
		val url = BASE_URL.toHttpUrl().newBuilder()
			.addPathSegment("api")
			.addPathSegment("animes")
			.addEncodedQueryParameter("page", (page + 1).toString())
			.addEncodedQueryParameter("limit", MANGA_PAGE_SIZE.toString())
			.addEncodedQueryParameter("censored", "false")
			.addQueryParameter("search", query)
			.build()
		val request = Request.Builder().url(url).get().build()
		val response = okHttp.newCall(request).await().parseJsonArray()
		val list = parseShikimoriList(response, "animes")
		return if (pageOffset != 0) list.drop(pageOffset) else list
	}

	/**
	 * Get anime details from Shikimori by anime ID.
	 */
	suspend fun getAnimeInfo(id: Long): ScrobblerContentInfo {
		val request = Request.Builder()
			.get()
			.url("${BASE_URL}api/animes/$id")
		val response = okHttp.newCall(request.build()).await().parseJson()
		return ScrobblerContentInfo(response)
	}

	private fun parseShikimoriList(array: org.json.JSONArray, mediaType: String): List<ScrobblerContent> {
		return array.mapJSON { json ->
			ScrobblerContent(
				id = json.getLong("id"),
				name = json.getString("name"),
				altName = json.getStringOrNull("russian"),
				cover = json.getJSONObject("image").getString("preview").toAbsoluteUrl(DOMAIN),
				url = json.getString("url").toAbsoluteUrl(DOMAIN),
			)
		}
	}

	private suspend fun saveRate(json: JSONObject, mangaId: Long) {
		val entity = ScrobblingEntity(
			scrobbler = ScrobblerService.SHIKIMORI.id,
			id = json.getInt("id"),
			mangaId = mangaId,
			targetId = json.getLong("target_id"),
			status = json.getString("status"),
			chapter = json.getInt("chapters"),
			comment = json.getString("text"),
			rating = (json.getDouble("score").toFloat() / 10f).coerceIn(0f, 1f),
		)
		db.getScrobblingDao().upsert(entity)
	}

	private fun ScrobblerContent(json: JSONObject, sourceTitle: String) = ScrobblerContent(
		id = json.getLong("id"),
		name = json.getString("name"),
		altName = json.getStringOrNull("russian"),
		cover = json.getJSONObject("image").getString("preview").toAbsoluteUrl(DOMAIN),
		url = json.getString("url").toAbsoluteUrl(DOMAIN),
		isBestMatch = sourceTitle.equals(json.getString("name"), ignoreCase = true)
			|| json.getStringOrNull("russian")?.equals(sourceTitle, ignoreCase = true) == true
	)

	private fun ScrobblerContentInfo(json: JSONObject) = ScrobblerContentInfo(
		id = json.getLong("id"),
		name = json.getString("name"),
		cover = json.getJSONObject("image").getString("preview").toAbsoluteUrl(DOMAIN),
		url = json.getString("url").toAbsoluteUrl(DOMAIN),
		descriptionHtml = json.getString("description_html"),
	)

	@Suppress("FunctionName")
	private fun ShikimoriUser(json: JSONObject) = ScrobblerUser(
		id = json.getLong("id"),
		nickname = json.getString("nickname"),
		avatar = json.getStringOrNull("avatar"),
		service = ScrobblerService.SHIKIMORI,
	)
}
