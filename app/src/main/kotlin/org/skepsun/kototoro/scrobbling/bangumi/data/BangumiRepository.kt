package org.skepsun.kototoro.scrobbling.bangumi.data

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.parsers.util.await
import org.skepsun.kototoro.parsers.util.json.getStringOrNull
import org.skepsun.kototoro.parsers.util.json.mapJSON
import org.skepsun.kototoro.parsers.util.parseJson
import org.skepsun.kototoro.scrobbling.common.data.ScrobblerRepository
import org.skepsun.kototoro.scrobbling.common.data.ScrobblerStorage
import org.skepsun.kototoro.scrobbling.common.data.ScrobblingEntity
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerManga
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerMangaInfo
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerType
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val REDIRECT_URI = "kototoro://bangumi-auth"
private const val BASE_URL = "https://bgm.tv/"
private const val API_URL = "https://api.bgm.tv/"

@Singleton
class BangumiRepository @Inject constructor(
	@ApplicationContext context: Context,
	@ScrobblerType(ScrobblerService.BANGUMI) private val okHttp: OkHttpClient,
	@ScrobblerType(ScrobblerService.BANGUMI) private val storage: ScrobblerStorage,
	private val db: MangaDatabase,
) : ScrobblerRepository {

	private val clientId = context.getString(R.string.bangumi_clientId)
	private val clientSecret = context.getString(R.string.bangumi_clientSecret)

	override val oauthUrl: String
		get() = "${BASE_URL}oauth/authorize?client_id=$clientId&" +
			"redirect_uri=${REDIRECT_URI}&response_type=code"

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
			.url("${BASE_URL}oauth/access_token")
		val response = okHttp.newCall(request.build()).await().parseJson()
		storage.accessToken = response.getString("access_token")
		storage.refreshToken = response.getString("refresh_token")
	}

	override suspend fun loadUser(): ScrobblerUser {
		val request = Request.Builder()
			.url("${API_URL}v0/me")
			.get()
		val jo = okHttp.newCall(request.build()).await().parseJson()
		return ScrobblerUser(
			id = jo.getLong("id"),
			nickname = jo.getString("nickname"),
			avatar = jo.getJSONObject("avatar").getStringOrNull("medium"),
			service = ScrobblerService.BANGUMI,
		).also { storage.user = it }
	}

	override val cachedUser: ScrobblerUser?
		get() = storage.user

	override suspend fun unregister(mangaId: Long) {
		db.getScrobblingDao().delete(ScrobblerService.BANGUMI.id, mangaId)
	}

	override fun logout() {
		storage.clear()
	}

	override suspend fun findManga(query: String, offset: Int): List<ScrobblerManga> {
		val requestBody = JSONObject().apply {
			put("keyword", query)
			put("filter", JSONObject().apply {
				put("type", JSONArray().apply { put(1) }) // 1 is Book
			})
		}.toString().toRequestBody("application/json".toMediaType())

		val request = Request.Builder()
			.url("${API_URL}v0/search/subjects?limit=10&offset=$offset")
			.post(requestBody)

		val response = okHttp.newCall(request.build()).await().parseJson()
		val data = response.getJSONArray("data")
		return data.mapJSON { json ->
			ScrobblerManga(
				id = json.getLong("id"),
				name = json.getString("name_cn").ifBlank { json.getString("name") },
				altName = json.getString("name"),
				cover = json.getJSONObject("images").getString("medium"),
				url = "https://bgm.tv/subject/${json.getLong("id")}",
				isBestMatch = false
			)
		}
	}

	override suspend fun createRate(mangaId: Long, scrobblerMangaId: Long) {
		val entity = ScrobblingEntity(
			scrobbler = ScrobblerService.BANGUMI.id,
			id = scrobblerMangaId.toInt(),
			mangaId = mangaId,
			targetId = scrobblerMangaId,
			status = "do",
			chapter = 0,
			comment = "",
			rating = 0f,
		)
		updateCollection(scrobblerMangaId, 2, null, null, null)
		db.getScrobblingDao().upsert(entity)
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, chapter: Int) {
		val entity = db.getScrobblingDao().find(ScrobblerService.BANGUMI.id, mangaId) ?: return
		updateCollection(entity.targetId, null, null, null, chapter)
		db.getScrobblingDao().upsert(entity.copy(chapter = chapter))
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, rating: Float, status: String?, comment: String?) {
		val entity = db.getScrobblingDao().find(ScrobblerService.BANGUMI.id, mangaId) ?: return
		val bgmStatus = when (status) {
			"wish" -> 1
			"do" -> 2
			"collect" -> 3
			"on_hold" -> 4
			"dropped" -> 5
			else -> null
		}
		val score = if (rating > 0) (rating * 10).roundToInt() else null
		updateCollection(entity.targetId, bgmStatus, score, comment, null)
		db.getScrobblingDao().upsert(entity.copy(
			status = status ?: entity.status,
			rating = rating,
			comment = comment ?: entity.comment
		))
	}

	private suspend fun updateCollection(subjectId: Long, status: Int?, rate: Int?, comment: String?, ep: Int?) {
		val body = JSONObject()
		status?.let { body.put("type", it) }
		rate?.let { body.put("rate", it) }
		comment?.let { body.put("comment", it) }
		ep?.let { body.put("ep_status", it) }

		val request = Request.Builder()
			.url("${API_URL}v0/users/-/collections/$subjectId")
			.patch(body.toString().toRequestBody("application/json".toMediaType()))

		okHttp.newCall(request.build()).await()
	}

	override suspend fun getMangaInfo(id: Long): ScrobblerMangaInfo {
		val request = Request.Builder()
			.url("${API_URL}v0/subjects/$id")
			.get()
		val json = okHttp.newCall(request.build()).await().parseJson()
		return ScrobblerMangaInfo(
			id = json.getLong("id"),
			name = json.getString("name_cn").ifBlank { json.getString("name") },
			cover = json.getJSONObject("images").getString("large"),
			url = "https://bgm.tv/subject/${json.getLong("id")}",
			descriptionHtml = json.getString("summary"),
		)
	}

	/**
	 * Sync all manga collections from Bangumi to local database.
	 * Called after authorization to pull the user's existing tracking data.
	 * Uses Bangumi API: GET /v0/users/{username}/collections?subject_type=1
	 */
	suspend fun syncLibraryFromRemote() {
		val user = cachedUser ?: loadUser()
		val oldMappings = db.getScrobblingDao()
			.findAllByScrobbler(ScrobblerService.BANGUMI.id)
			.groupBy { it.targetId }
			.mapValues { (_, values) ->
				values.firstOrNull { it.mangaId > 0L }?.mangaId ?: 0L
			}

		val synced = ArrayList<ScrobblingEntity>()
		var offset = 0
		val limit = 50
		while (true) {
			val request = Request.Builder()
				.url("${API_URL}v0/users/${user.nickname}/collections?subject_type=1&limit=$limit&offset=$offset")
				.get()
			val response = okHttp.newCall(request.build()).await().parseJson()
			val data = response.optJSONArray("data") ?: break
			if (data.length() == 0) break

			for (i in 0 until data.length()) {
				val item = data.optJSONObject(i) ?: continue
				val subjectId = item.optJSONObject("subject")?.optLong("id") ?: continue
				val mappedMangaId = oldMappings[subjectId] ?: 0L
				val typeInt = item.optInt("type", 0)
				val statusStr = when (typeInt) {
					1 -> "wish"
					2 -> "do"
					3 -> "collect"
					4 -> "on_hold"
					5 -> "dropped"
					else -> null
				}
				synced.add(
					ScrobblingEntity(
						scrobbler = ScrobblerService.BANGUMI.id,
						id = subjectId.toInt(),
						mangaId = mappedMangaId,
						targetId = subjectId,
						status = statusStr,
						chapter = item.optInt("ep_status", 0),
						comment = item.optString("comment", ""),
						rating = (item.optInt("rate", 0).toFloat() / 10f).coerceIn(0f, 1f),
					),
				)
			}
			offset += data.length()
			if (data.length() < limit) break
		}

		db.withTransaction {
			db.getScrobblingDao().deleteByScrobbler(ScrobblerService.BANGUMI.id)
			synced.forEach { entity ->
				db.getScrobblingDao().upsert(entity)
			}
		}
	}
}
