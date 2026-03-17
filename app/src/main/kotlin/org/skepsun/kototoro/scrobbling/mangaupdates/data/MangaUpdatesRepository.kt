package org.skepsun.kototoro.scrobbling.mangaupdates.data

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.parsers.util.await
import org.skepsun.kototoro.parsers.util.json.getStringOrNull
import org.skepsun.kototoro.parsers.util.parseJson
import org.skepsun.kototoro.parsers.util.parseJsonArray
import org.skepsun.kototoro.parsers.util.parseRaw
import org.skepsun.kototoro.scrobbling.common.data.ScrobblerRepository
import org.skepsun.kototoro.scrobbling.common.data.ScrobblerStorage
import org.skepsun.kototoro.scrobbling.common.data.ScrobblingEntity
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContent
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContentInfo
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerUser
import java.io.IOException

private const val BASE_API_URL = "https://api.mangaupdates.com/v1"
private val CONTENT_TYPE = "application/vnd.api+json".toMediaType()

class MangaUpdatesRepository(
	@ApplicationContext context: Context,
	private val okHttp: OkHttpClient,
	private val storage: ScrobblerStorage,
	private val db: MangaDatabase,
) : ScrobblerRepository {

	override val oauthUrl: String = "kototoro+mangaupdates://auth"

	override val isAuthorized: Boolean
		get() = storage.accessToken != null

	override val cachedUser: ScrobblerUser?
		get() = storage.user

	override suspend fun authorize(code: String?) {
		if (code == null) return // No refresh available without password

		val username = code.substringBefore(';')
		val password = code.substringAfter(';')

		val payload = JSONObject().apply {
			put("username", username)
			put("password", password)
		}

		val request = Request.Builder()
			.put(payload.toString().toRequestBody(CONTENT_TYPE))
			.url("$BASE_API_URL/account/login")
		
		val response = okHttp.newCall(request.build()).await().parseJson()
		val contextJson = response.getJSONObject("context")
		storage.accessToken = contextJson.getString("session_token")
		// Save the uid as refresh_token just for id purpose or keep it in user info
	}

	override suspend fun loadUser(): ScrobblerUser {
		val request = Request.Builder()
			.get()
			.url("$BASE_API_URL/account/profile")
		val responseStr = okHttp.newCall(request.build()).await().parseRaw()
		val response = try {
			JSONObject(responseStr)
		} catch (e: JSONException) {
			throw IOException("Invalid profile response: ${responseStr.take(200)}", e)
		}
		
		val avatarUrl = response.optJSONObject("avatar")?.let { avatar ->
			avatar.optJSONObject("url")?.getStringOrNull("original")
				?: avatar.getStringOrNull("url")
		}
		
		return ScrobblerUser(
			id = response.optLong("user_id", 0L),
			nickname = response.optString("username", "User"),
			avatar = avatarUrl,
			service = ScrobblerService.MANGAUPDATES,
		).also { storage.user = it }
	}

	override fun logout() {
		runCatching {
			// Try to call logout api
			val request = Request.Builder()
				.post(ByteArray(0).toRequestBody(null))
				.url("$BASE_API_URL/account/logout")
			okHttp.newCall(request.build()).execute()
		}
		storage.clear()
	}

	override suspend fun unregister(mangaId: Long) {
		db.getScrobblingDao().delete(ScrobblerService.MANGAUPDATES.id, mangaId)
	}

	override suspend fun findContent(query: String, offset: Int): List<ScrobblerContent> {
		val payload = JSONObject().apply {
			put("search", query)
			put("page", (offset / 100) + 1)
			put("perpage", 100)
		}
		
		val request = Request.Builder()
			.post(payload.toString().toRequestBody(CONTENT_TYPE))
			.url("$BASE_API_URL/series/search")

		val response = okHttp.newCall(request.build()).await().parseJson()
		val results = response.optJSONArray("results") ?: return emptyList()

		val mapped = mutableListOf<ScrobblerContent>()
		for (i in 0 until results.length()) {
			val result = results.getJSONObject(i)
			val record = result.getJSONObject("record")
			mapped.add(
				ScrobblerContent(
					id = record.getLong("series_id"),
					name = record.getString("title"),
					altName = null,
					cover = record.optJSONObject("image")?.optJSONObject("url")?.getStringOrNull("original"),
					url = record.getString("url"),
					isBestMatch = record.getString("title").equals(query, ignoreCase = true)
				)
			)
		}
		return mapped
	}

	override suspend fun getContentInfo(id: Long): ScrobblerContentInfo {
		val request = Request.Builder()
			.get()
			.url("$BASE_API_URL/series/$id")
		
		val response = okHttp.newCall(request.build()).await().parseJson()
		return ScrobblerContentInfo(
			id = response.getLong("series_id"),
			name = response.getString("title"),
			cover = response.optJSONObject("image")?.optJSONObject("url")?.getString("original").orEmpty(),
			url = response.getString("url"),
			descriptionHtml = response.getString("description").replace("\n", "<br>")
		)
	}

	override suspend fun createRate(mangaId: Long, scrobblerContentId: Long) {
		val payloadStr = """
			[
			  {
			    "series": {
			      "id": $scrobblerContentId
			    },
			    "list_id": 0,
			    "status": {
			      "chapter": 0
			    }
			  }
			]
		""".trimIndent()

		val request = Request.Builder()
			.post(payloadStr.toRequestBody(CONTENT_TYPE))
			.url("$BASE_API_URL/lists/series")

		val response = okHttp.newCall(request.build()).await()
		if (response.isSuccessful) {
			val entity = ScrobblingEntity(
				scrobbler = ScrobblerService.MANGAUPDATES.id,
				id = scrobblerContentId.toInt(),
				mangaId = mangaId,
				targetId = scrobblerContentId,
				status = "0", // READING list ID
				chapter = 0,
				comment = null,
				rating = 0f
			)
			db.getScrobblingDao().upsert(entity)
		} else {
			val responseBodyStr = response.body?.string() ?: "Empty body"
			throw IOException("Failed to create rate: ${response.code}, body: $responseBodyStr")
		}
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, chapter: Int) {
		// rateId here acts as the manga series id, because MU doesn't separate entry id from series id in lists update
		val entity = db.getScrobblingDao().find(ScrobblerService.MANGAUPDATES.id, mangaId)
			?: return
			
		val payload = JSONArray().apply {
			put(JSONObject().apply {
				put("series", JSONObject().apply { put("id", rateId) })
				put("list_id", entity.status?.toLongOrNull() ?: 0L)
				put("status", JSONObject().apply { put("chapter", chapter) })
			})
		}

		val request = Request.Builder()
			.post(payload.toString().toRequestBody(CONTENT_TYPE))
			.url("$BASE_API_URL/lists/series/update")
			
		okHttp.newCall(request.build()).await()

		val updated = entity.copy(chapter = chapter)
		db.getScrobblingDao().upsert(updated)
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, rating: Float, status: String?, comment: String?) {
		val entity = db.getScrobblingDao().find(ScrobblerService.MANGAUPDATES.id, mangaId)
			?: return

		// Update list status
		val payload = JSONArray().apply {
			put(JSONObject().apply {
				put("series", JSONObject().apply { put("id", rateId) })
				put("list_id", status?.toLongOrNull() ?: 0L)
				put("status", JSONObject().apply { put("chapter", entity.chapter) })
			})
		}

		val request = Request.Builder()
			.post(payload.toString().toRequestBody(CONTENT_TYPE))
			.url("$BASE_API_URL/lists/series/update")
			
		okHttp.newCall(request.build()).await()
		
		// Update rating
		if (rating > 0f) {
			val scorePayload = JSONObject().apply {
				put("rating", (rating * 10).toInt())
			}
			val ratingRequest = Request.Builder()
				.put(scorePayload.toString().toRequestBody(CONTENT_TYPE))
				.url("$BASE_API_URL/series/$rateId/rating")
			okHttp.newCall(ratingRequest.build()).await()
		} else {
			val ratingRequest = Request.Builder()
				.delete()
				.url("$BASE_API_URL/series/$rateId/rating")
			okHttp.newCall(ratingRequest.build()).await()
		}

		val updated = entity.copy(status = status, rating = rating, comment = comment)
		db.getScrobblingDao().upsert(updated)
	}

	suspend fun syncLibraryFromRemote(): Int {
		// MangaUpdates API does not provide a "list all tracked series" endpoint.
		// Tracked manga are managed locally via manual linking.
		// Return -1 to indicate sync is not applicable for this service.
		return -1
	}
}
