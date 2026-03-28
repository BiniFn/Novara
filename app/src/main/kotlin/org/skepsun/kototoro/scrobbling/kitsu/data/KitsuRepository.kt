package org.skepsun.kototoro.scrobbling.kitsu.data

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import org.json.JSONObject
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.util.ext.parseJsonOrNull
import org.skepsun.kototoro.parsers.util.await
import org.skepsun.kototoro.parsers.util.json.getFloatOrDefault
import org.skepsun.kototoro.parsers.util.json.getIntOrDefault
import org.skepsun.kototoro.parsers.util.json.getStringOrNull
import org.skepsun.kototoro.parsers.util.json.mapJSON
import org.skepsun.kototoro.parsers.util.parseJson
import org.skepsun.kototoro.parsers.util.urlEncoded
import org.skepsun.kototoro.scrobbling.common.data.ScrobblerRepository
import org.skepsun.kototoro.scrobbling.common.data.ScrobblerStorage
import org.skepsun.kototoro.scrobbling.common.data.ScrobblingEntity
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContent
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContentInfo
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerUser
import org.skepsun.kototoro.scrobbling.kitsu.data.KitsuInterceptor.Companion.VND_JSON

private const val BASE_WEB_URL = "https://kitsu.app"

class KitsuRepository(
	@ApplicationContext context: Context,
	private val okHttp: OkHttpClient,
	private val storage: ScrobblerStorage,
	private val db: MangaDatabase,
) : ScrobblerRepository {

	// not in use yet
	private val clientId = context.getString(R.string.kitsu_clientId)
	private val clientSecret = context.getString(R.string.kitsu_clientSecret)

	override val oauthUrl: String = "kototoro+kitsu://auth"

	override val isAuthorized: Boolean
		get() = storage.accessToken != null

	override val cachedUser: ScrobblerUser?
		get() {
			return storage.user
		}

	override suspend fun authorize(code: String?) {
		val body = FormBody.Builder()
		if (code != null) {
			body.add("grant_type", "password")
			body.add("username", code.substringBefore(';'))
			body.add("password", code.substringAfter(';'))
		} else {
			body.add("grant_type", "refresh_token")
			body.add("refresh_token", checkNotNull(storage.refreshToken))
		}
		val request = Request.Builder()
			.post(body.build())
			.url("$BASE_WEB_URL/api/oauth/token")
		val response = okHttp.newCall(request.build()).await().parseJson()
		storage.accessToken = response.getString("access_token")
		storage.refreshToken = response.getString("refresh_token")
	}

	override suspend fun loadUser(): ScrobblerUser {
		val request = Request.Builder()
			.get()
			.url("$BASE_WEB_URL/api/edge/users?filter[self]=true")
		val response = okHttp.newCall(request.build()).await().parseJson()
			.getJSONArray("data")
			.getJSONObject(0)
		return ScrobblerUser(
			id = response.getAsLong("id"),
			nickname = response.getJSONObject("attributes").getString("name"),
			avatar = response.getJSONObject("attributes").optJSONObject("avatar")?.getStringOrNull("small"),
			service = ScrobblerService.KITSU,
		).also { storage.user = it }
	}

	override fun logout() {
		storage.clear()
	}

	override suspend fun unregister(mangaId: Long) {
		return db.getScrobblingDao().delete(ScrobblerService.KITSU.id, mangaId)
	}

	override suspend fun findContent(query: String, offset: Int, isAnime: Boolean): List<ScrobblerContent> {
		val type = if (isAnime) "anime" else "manga"
		val request = Request.Builder()
			.get()
			.url("$BASE_WEB_URL/api/edge/$type?page[limit]=20&page[offset]=$offset&filter[text]=${query.urlEncoded()}")
		val response = okHttp.newCall(request.build()).await().parseJson().ensureSuccess()
		return response.getJSONArray("data").mapJSON { jo ->
			val attrs = jo.getJSONObject("attributes")
			val titles = attrs.getJSONObject("titles").valuesToStringList()
			ScrobblerContent(
				id = jo.getAsLong("id"),
				name = titles.first(),
				altName = titles.drop(1).joinToString(),
				cover = attrs.getJSONObject("posterImage").getStringOrNull("small").orEmpty(),
				url = "$BASE_WEB_URL/$type/${attrs.getString("slug")}",
				isBestMatch = titles.any {
					it.equals(query, ignoreCase = true)
				}
			)
		}
	}

	// ── Discovery API (public, no auth) ─────────────────────────

	/**
	 * Fetch trending anime or manga.
	 * @param mediaType "anime" or "manga"
	 */
	suspend fun getTrending(mediaType: String): List<ScrobblerContent> {
		val request = Request.Builder()
			.get()
			.url("$BASE_WEB_URL/api/edge/trending/$mediaType")
		return parseMediaList(okHttp.newCall(request.build()).await().parseJson(), mediaType)
	}

	/**
	 * Browse anime/manga by category slug, sorted by popularity (descending userCount).
	 * @param mediaType "anime" or "manga"
	 * @param categorySlug e.g. "action", "romance", "ecchi", or null for all
	 * @param page 1-based page number
	 */
	suspend fun getRankings(mediaType: String, categorySlug: String?, page: Int): List<ScrobblerContent> {
		val limit = 20
		val offset = (page - 1) * limit
		val urlBuilder = StringBuilder("$BASE_WEB_URL/api/edge/$mediaType?page[limit]=$limit&page[offset]=$offset&sort=-userCount")
		if (!categorySlug.isNullOrBlank()) {
			urlBuilder.append("&filter[categories]=${categorySlug.urlEncoded()}")
		}
		val request = Request.Builder()
			.get()
			.url(urlBuilder.toString())
		return parseMediaList(okHttp.newCall(request.build()).await().parseJson().ensureSuccess(), mediaType)
	}

	/**
	 * Search anime by text query (similar to [findContent] but for anime).
	 */
	suspend fun findAnime(query: String, offset: Int): List<ScrobblerContent> {
		val request = Request.Builder()
			.get()
			.url("$BASE_WEB_URL/api/edge/anime?page[limit]=20&page[offset]=$offset&filter[text]=${query.urlEncoded()}")
		return parseMediaList(okHttp.newCall(request.build()).await().parseJson().ensureSuccess(), "anime")
	}

	private fun parseMediaList(json: JSONObject, mediaType: String): List<ScrobblerContent> {
		return json.getJSONArray("data").mapJSON { jo ->
			val attrs = jo.getJSONObject("attributes")
			val titles = attrs.optJSONObject("titles")?.valuesToStringList() ?: listOf(attrs.optString("canonicalTitle", ""))
			val slug = attrs.optString("slug", "")
			val posterImage = attrs.optJSONObject("posterImage")
			ScrobblerContent(
				id = jo.getAsLong("id"),
				name = attrs.optString("canonicalTitle", titles.firstOrNull() ?: ""),
				altName = titles.drop(1).joinToString(),
				cover = posterImage?.getStringOrNull("small") ?: posterImage?.getStringOrNull("medium").orEmpty(),
				url = "$BASE_WEB_URL/$mediaType/$slug",
			)
		}
	}
	
	private suspend fun isAnimeContent(mangaId: Long): Boolean {
		val mangaItem = db.getMangaDao().find(mangaId) ?: return false
		val source = org.skepsun.kototoro.core.model.ContentSource(mangaItem.manga.source)
		val contentType = source.getContentType()
		return contentType == org.skepsun.kototoro.parsers.model.ContentType.VIDEO || contentType == org.skepsun.kototoro.parsers.model.ContentType.HENTAI_VIDEO
	}

	override suspend fun getContentInfo(id: Long): ScrobblerContentInfo {
		// Try manga first, fall back to anime
		val (data, mediaType) = try {
			val req = Request.Builder().get()
				.url("$BASE_WEB_URL/api/edge/manga/$id?include=categories,mediaRelationships.destination")
			val json = okHttp.newCall(req.build()).await().parseJson().ensureSuccess()
			json to "manga"
		} catch (_: Exception) {
			val req = Request.Builder().get()
				.url("$BASE_WEB_URL/api/edge/anime/$id?include=categories,mediaRelationships.destination")
			val json = okHttp.newCall(req.build()).await().parseJson().ensureSuccess()
			json to "anime"
		}

		val mainData = data.getJSONObject("data")
		val attrs = mainData.getJSONObject("attributes")
		val included = data.optJSONArray("included")

		// --- Basic info ---
		val canonicalTitle = attrs.optString("canonicalTitle", "")
		val titlesObj = attrs.optJSONObject("titles")
		val slug = attrs.optString("slug", "")
		val synopsis = attrs.optString("synopsis", "").replace("\\n", "<br>")
		val posterImage = attrs.optJSONObject("posterImage")
		val cover = posterImage?.getStringOrNull("large")
			?: posterImage?.getStringOrNull("medium").orEmpty()

		// --- Infobox properties ---
		val infobox = mutableListOf<Pair<String, String>>()

		// Titles
		titlesObj?.let { titles ->
			titles.getStringOrNull("ja_jp")?.let { infobox.add("日语" to it) }
			titles.getStringOrNull("en_jp")?.let { infobox.add("日语 (Romaji)" to it) }
			titles.getStringOrNull("en")?.let { infobox.add("英语" to it) }
		}

		// Type
		attrs.getStringOrNull("subtype")?.let { subtype ->
			infobox.add("类型" to subtype.replaceFirstChar { it.uppercase() })
		}

		// Status
		attrs.getStringOrNull("status")?.let { status ->
			val statusLabel = when (status) {
				"current" -> "正在播出"
				"finished" -> "已完结"
				"tba" -> "待定"
				"unreleased" -> "未播出"
				"upcoming" -> "即将播出"
				else -> status
			}
			infobox.add("状态" to statusLabel)
		}

		// Dates
		attrs.getStringOrNull("startDate")?.let { infobox.add("开始日期" to it) }
		attrs.getStringOrNull("endDate")?.let { infobox.add("结束日期" to it) }

		// Age rating
		val ageRating = attrs.getStringOrNull("ageRating")
		val ageGuide = attrs.getStringOrNull("ageRatingGuide")
		if (ageRating != null) {
			val ratingStr = if (ageGuide != null) "$ageRating - $ageGuide" else ageRating
			infobox.add("评分" to ratingStr)
		}

		// Episode count & length
		val episodeCount = attrs.optInt("episodeCount", 0)
		if (episodeCount > 0) {
			infobox.add("集数" to episodeCount.toString())
		}
		val episodeLength = attrs.optInt("episodeLength", 0)
		if (episodeLength > 0) {
			infobox.add("时长" to "${episodeLength}分钟/集")
		}

		// Average rating
		attrs.getStringOrNull("averageRating")?.let { infobox.add("平均评分" to "$it%") }

		// Rankings
		val popularityRank = attrs.optInt("popularityRank", 0)
		if (popularityRank > 0) infobox.add("受欢迎排名" to "#$popularityRank")
		val ratingRank = attrs.optInt("ratingRank", 0)
		if (ratingRank > 0) infobox.add("好评排名" to "#$ratingRank")

		// --- Tags (categories) ---
		val tags = mutableListOf<String>()
		if (included != null) {
			for (i in 0 until included.length()) {
				val item = included.optJSONObject(i) ?: continue
				if (item.optString("type") == "categories") {
					item.optJSONObject("attributes")?.getStringOrNull("title")?.let { tags.add(it) }
				}
			}
		}

		// --- Related works ---
		val relatedWorks = mutableListOf<ScrobblerContentInfo.RelatedWork>()
		if (included != null) {
			// Build a map of included media by type+id for quick lookup
			val mediaMap = HashMap<String, JSONObject>()
			for (i in 0 until included.length()) {
				val item = included.optJSONObject(i) ?: continue
				val type = item.optString("type")
				if (type == "anime" || type == "manga") {
					val itemId = item.optString("id")
					mediaMap["$type:$itemId"] = item
				}
			}

			// Parse mediaRelationships to get role and destination
			for (i in 0 until included.length()) {
				val item = included.optJSONObject(i) ?: continue
				if (item.optString("type") != "mediaRelationships") continue
				val role = item.optJSONObject("attributes")?.getStringOrNull("role") ?: continue
				val destData = item.optJSONObject("relationships")
					?.optJSONObject("destination")
					?.optJSONObject("data") ?: continue
				val destType = destData.optString("type")
				val destId = destData.optString("id")
				val destMedia = mediaMap["$destType:$destId"] ?: continue
				val destAttrs = destMedia.optJSONObject("attributes") ?: continue
				val destTitle = destAttrs.optString("canonicalTitle", "")
				val destSlug = destAttrs.optString("slug", "")
				val destPoster = destAttrs.optJSONObject("posterImage")
				val destCover = destPoster?.getStringOrNull("small")
					?: destPoster?.getStringOrNull("medium").orEmpty()
				val destIdLong = destId.toLongOrNull() ?: continue

				val roleLabel = when (role) {
					"sequel" -> "续集"
					"prequel" -> "前传"
					"alternative_setting" -> "替代设定"
					"alternative_version" -> "替代版本"
					"side_story" -> "番外篇"
					"parent_story" -> "母篇"
					"summary" -> "总集篇"
					"full_story" -> "完整版"
					"spinoff" -> "衍生作"
					"adaptation" -> "改编"
					"character" -> "角色"
					"other" -> "其他"
					else -> role
				}

				relatedWorks.add(
					ScrobblerContentInfo.RelatedWork(
						id = destIdLong,
						title = destTitle,
						coverUrl = destCover,
						relationship = roleLabel,
						url = "$BASE_WEB_URL/$destType/$destSlug",
					),
				)
			}
		}

		// --- Episodes (separate request, only for anime or if applicable) ---
		val episodes = mutableListOf<ScrobblerContentInfo.EpisodeInfo>()
		try {
			val epRequest = Request.Builder().get()
				.url("$BASE_WEB_URL/api/edge/$mediaType/$id/episodes?page[limit]=40&sort=number")
			val epResponse = okHttp.newCall(epRequest.build()).await().parseJson()
			val epData = epResponse.optJSONArray("data")
			if (epData != null) {
				for (i in 0 until epData.length()) {
					val ep = epData.optJSONObject(i) ?: continue
					val epAttrs = ep.optJSONObject("attributes") ?: continue
					val epNum = epAttrs.optInt("number", i + 1)
					val epTitle = epAttrs.optString("canonicalTitle", "").ifBlank {
						"Episode $epNum"
					}
					val thumbnail = epAttrs.optJSONObject("thumbnail")
					val thumbUrl = thumbnail?.getStringOrNull("original")
						?: thumbnail?.getStringOrNull("large")
						?: thumbnail?.getStringOrNull("small")
					episodes.add(
						ScrobblerContentInfo.EpisodeInfo(
							number = epNum.toString(),
							title = epTitle,
							url = "$BASE_WEB_URL/$mediaType/$slug/episodes/$epNum",
							thumbnailUrl = thumbUrl,
						),
					)
				}
			}
		} catch (_: Exception) {
			// Episodes not available for some content types, that's OK
		}

		return ScrobblerContentInfo(
			id = mainData.getAsLong("id"),
			name = canonicalTitle.ifBlank { "Unknown" },
			cover = cover,
			url = "$BASE_WEB_URL/$mediaType/$slug",
			descriptionHtml = synopsis,
			tags = tags,
			infoboxProperties = infobox,
			episodes = episodes,
			relatedWorks = relatedWorks,
		)
	}

	override suspend fun createRate(mangaId: Long, scrobblerContentId: Long) {
		val isAnime = isAnimeContent(mangaId)
		val typeKey = if (isAnime) "anime" else "manga"
		findExistingRate(scrobblerContentId, isAnime)?.let {
			saveRate(it, mangaId, typeKey)
			return
		}
		val user = cachedUser ?: loadUser()
		val payload = JSONObject()
		payload.putJO("data") {
			put("type", "libraryEntries")
			putJO("attributes") {
				put("status", "planned") // will be updated by next call
				put("progress", 0)
			}
			putJO("relationships") {
				putJO(typeKey) {
					putJO("data") {
						put("type", typeKey)
						put("id", scrobblerContentId)
					}
				}
				putJO("user") {
					putJO("data") {
						put("type", "users")
						put("id", user.id)
					}
				}
			}
		}
		val request = Request.Builder()
			.url("$BASE_WEB_URL/api/edge/library-entries?include=$typeKey")
			.post(payload.toKitsuRequestBody())
		val response = okHttp.newCall(request.build()).await().parseJson().ensureSuccess().getJSONObject("data")
		saveRate(response, mangaId, typeKey)
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, chapter: Int) {
		val typeKey = if (isAnimeContent(mangaId)) "anime" else "manga"
		val payload = JSONObject()
		payload.putJO("data") {
			put("type", "libraryEntries")
			put("id", rateId)
			putJO("attributes") {
				put("progress", chapter)
			}
		}
		val request = Request.Builder()
			.url("$BASE_WEB_URL/api/edge/library-entries/$rateId?include=$typeKey")
			.patch(payload.toKitsuRequestBody())
		val response = okHttp.newCall(request.build()).await().parseJson().ensureSuccess().getJSONObject("data")
		saveRate(response, mangaId, typeKey)
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, rating: Float, status: String?, comment: String?) {
		val typeKey = if (isAnimeContent(mangaId)) "anime" else "manga"
		val payload = JSONObject()
		payload.putJO("data") {
			put("type", "libraryEntries")
			put("id", rateId)
			putJO("attributes") {
				put("status", status)
				put("ratingTwenty", (rating * 20).toInt().coerceIn(2, 20))
				put("notes", comment)
			}
		}
		val request = Request.Builder()
			.url("$BASE_WEB_URL/api/edge/library-entries/$rateId?include=$typeKey")
			.patch(payload.toKitsuRequestBody())
		val response = okHttp.newCall(request.build()).await().parseJson().ensureSuccess().getJSONObject("data")
		saveRate(response, mangaId, typeKey)
	}

	suspend fun syncLibraryFromRemote(): Int {
		val userId = (cachedUser ?: loadUser()).id
		val oldMappings = db.getScrobblingDao()
			.findAllByScrobbler(ScrobblerService.KITSU.id)
			.groupBy { it.targetId }
			.mapValues { (_, values) ->
				values.firstOrNull { it.mangaId != 0L }?.mangaId ?: 0L
			}

		val synced = ArrayList<ScrobblingEntity>()
		var offset = 0
		while (true) {
			val request = Request.Builder()
				.get()
				.url("$BASE_WEB_URL/api/edge/library-entries?page[limit]=20&page[offset]=$offset&filter[userId]=$userId&include=manga,anime")
			val data = okHttp.newCall(request.build()).await().parseJson().ensureSuccess().optJSONArray("data") ?: break
			if (data.length() == 0) {
				break
			}
			for (i in 0 until data.length()) {
				val json = data.optJSONObject(i) ?: continue
				val attrs = json.optJSONObject("attributes") ?: continue
				val rels = json.optJSONObject("relationships")
				val media = rels?.optJSONObject("manga")?.optJSONObject("data")
					?: rels?.optJSONObject("anime")?.optJSONObject("data")
				val targetId = media?.optString("id")?.toLongOrNull() ?: continue
				val mappedContentId = oldMappings[targetId] ?: 0L
				synced.add(
					ScrobblingEntity(
						scrobbler = ScrobblerService.KITSU.id,
						id = json.optString("id").toIntOrNull() ?: continue,
						mangaId = mappedContentId,
						targetId = targetId,
						status = attrs.getStringOrNull("status"),
						chapter = attrs.getIntOrDefault("progress", 0),
						comment = attrs.getStringOrNull("notes"),
						rating = (attrs.getFloatOrDefault("ratingTwenty", 0f) / 20f).coerceIn(0f, 1f),
					),
				)
			}
			offset += data.length()
		}

		db.withTransaction {
			db.getScrobblingDao().deleteByScrobbler(ScrobblerService.KITSU.id)
			synced.forEach { entity ->
				db.getScrobblingDao().upsert(entity)
			}
		}
		return synced.size
	}

	private fun JSONObject.valuesToStringList(): List<String> {
		val result = ArrayList<String>(length())
		for (key in keys()) {
			result.add(getStringOrNull(key) ?: continue)
		}
		return result
	}

	private inline fun JSONObject.putJO(name: String, init: JSONObject.() -> Unit) {
		put(name, JSONObject().apply(init))
	}

	private fun JSONObject.toKitsuRequestBody() = toString().toRequestBody(VND_JSON.toMediaType())

	private suspend fun findExistingRate(scrobblerContentId: Long, isAnime: Boolean): JSONObject? {
		val userId = (cachedUser ?: loadUser()).id
		val filterKey = if (isAnime) "anime_id" else "manga_id"
		val includeKey = if (isAnime) "anime" else "manga"
		val request = Request.Builder()
			.get()
			.url("$BASE_WEB_URL/api/edge/library-entries?filter[$filterKey]=$scrobblerContentId&filter[userId]=$userId&include=$includeKey")
		val data = okHttp.newCall(request.build()).await().parseJsonOrNull()?.optJSONArray("data") ?: return null
		return data.optJSONObject(0)
	}

	private suspend fun saveRate(json: JSONObject, mangaId: Long, typeKey: String) {
		val attrs = json.getJSONObject("attributes")
		val media = json.getJSONObject("relationships").getJSONObject(typeKey).getJSONObject("data")
		val entity = ScrobblingEntity(
			scrobbler = ScrobblerService.KITSU.id,
			id = json.getInt("id"),
			mangaId = mangaId,
			targetId = media.getAsLong("id"),
			status = attrs.getString("status"),
			chapter = attrs.getIntOrDefault("progress", 0),
			comment = attrs.getStringOrNull("notes"),
			rating = (attrs.getFloatOrDefault("ratingTwenty", 0f) / 20f).coerceIn(0f, 1f),
		)
		db.getScrobblingDao().upsert(entity)
	}

	private fun JSONObject.ensureSuccess(): JSONObject {
		val error = optJSONArray("errors")?.optJSONObject(0) ?: return this
		val title = error.getString("title")
		val detail = error.getStringOrNull("detail")
		throw IOException("$title: $detail")
	}

	private fun JSONObject.getAsLong(name: String): Long = when (val rawValue = opt(name)) {
		is Long -> rawValue
		is Number -> rawValue.toLong()
		is String -> rawValue.toLong()
		else -> throw IllegalArgumentException("Value $rawValue at \"$name\" is not of type long")
	}
}
