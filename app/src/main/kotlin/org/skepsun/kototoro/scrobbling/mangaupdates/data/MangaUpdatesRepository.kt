package org.skepsun.kototoro.scrobbling.mangaupdates.data

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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
private const val BASE_WEB_URL = "https://www.mangaupdates.com"
private const val COMMENTS_PAGE_SIZE = 10
private const val REVIEW_PAGE_SIZE = 3
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

	override suspend fun findContent(query: String, offset: Int, isAnime: Boolean): List<ScrobblerContent> {
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
					mediaType = record.optString("type").takeIf { it.isNotBlank() },
					subtitle = record.optString("year").takeIf { it.isNotBlank() },
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
		return parseSeriesDetails(response)
	}

	suspend fun getRankings(orderby: String, page: Int, type: String? = null, genre: String? = null): List<ScrobblerContent> {
		val payload = JSONObject().apply {
			put("page", page)
			put("perpage", 25)
			put("orderby", orderby)
			if (type != null) {
				put("type", JSONArray().apply { put(type) })
			}
			if (genre != null) {
				put("genre", JSONArray().apply { put(genre) })
			}
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
			val rating = record.optDouble("bayesian_rating", 0.0)
			val year = record.optString("year", "").ifBlank { null }
			mapped.add(
				ScrobblerContent(
					id = record.getLong("series_id"),
					name = record.getString("title"),
					altName = null,
					cover = record.optJSONObject("image")?.optJSONObject("url")?.getStringOrNull("original"),
					url = record.getString("url"),
					mediaType = record.optString("type").takeIf { it.isNotBlank() },
					subtitle = year,
					score = rating.takeIf { it > 0.0 }?.toFloat(),
					scoreMax = 10f,
					isBestMatch = false,
				)
			)
		}
		return mapped
	}

	private suspend fun parseSeriesDetails(response: JSONObject): ScrobblerContentInfo {
		val genres = mutableListOf<String>()
		response.optJSONArray("genres")?.let { arr ->
			for (i in 0 until arr.length()) {
				arr.optJSONObject(i)?.optString("genre")?.takeIf { it.isNotBlank() }?.let(genres::add)
			}
		}

		val categories = mutableListOf<String>()
		response.optJSONArray("categories")?.let { arr ->
			for (i in 0 until minOf(arr.length(), 10)) {
				arr.optJSONObject(i)?.optString("category")?.takeIf { it.isNotBlank() }?.let(categories::add)
			}
		}

		val authors = mutableListOf<String>()
		response.optJSONArray("authors")?.let { arr ->
			for (i in 0 until arr.length()) {
				val authorObj = arr.optJSONObject(i) ?: continue
				val name = authorObj.optString("name").takeIf { it.isNotBlank() } ?: continue
				val type = authorObj.optString("type").takeIf { it.isNotBlank() }
				authors.add(if (type != null) "$name ($type)" else name)
			}
		}

		val relatedWorks = mutableListOf<ScrobblerContentInfo.RelatedWork>()
		response.optJSONArray("related_series")?.let { arr ->
			for (i in 0 until arr.length()) {
				val rel = arr.optJSONObject(i) ?: continue
				val relId = rel.optLong("related_series_id", 0L)
				val relTitle = rel.optString("related_series_name").takeIf { it.isNotBlank() } ?: continue
				if (relId > 0L) {
					relatedWorks.add(ScrobblerContentInfo.RelatedWork(
						id = relId,
						title = relTitle,
						coverUrl = "",
						relationship = rel.optString("relation_type").takeIf { it.isNotBlank() },
						url = "https://www.mangaupdates.com/series/${relId}",
					))
				}
			}
		}

		val recommendations = mutableListOf<ScrobblerContentInfo.RelatedWork>()
		response.optJSONArray("recommendations")?.let { arr ->
			for (i in 0 until minOf(arr.length(), 10)) {
				val rec = arr.optJSONObject(i) ?: continue
				val recId = rec.optLong("series_id", 0L)
				val recTitle = rec.optString("series_name").takeIf { it.isNotBlank() } ?: continue
				val recCover = rec.optJSONObject("series_image")?.optJSONObject("url")?.getStringOrNull("original")
				if (recId > 0L) {
					recommendations.add(ScrobblerContentInfo.RelatedWork(
						id = recId,
						title = recTitle,
						coverUrl = recCover.orEmpty(),
						url = "https://www.mangaupdates.com/series/${recId}",
					))
				}
			}
		}

		val infoboxProperties = mutableListOf<Pair<String, String>>()
		response.optString("type").takeIf { it.isNotBlank() }?.let { infoboxProperties.add("Type" to it) }
		response.optString("year").takeIf { it.isNotBlank() }?.let { infoboxProperties.add("Year" to it) }
		response.optString("status").takeIf { it.isNotBlank() }?.let { infoboxProperties.add("Status" to it) }
		val rating = response.optDouble("bayesian_rating", 0.0)
		if (rating > 0) infoboxProperties.add("Rating" to String.format("%.2f", rating))
		val contentUrl = response.getString("url")
		val supplemental = fetchSupplementalDetails(contentUrl)

		return ScrobblerContentInfo(
			id = response.getLong("series_id"),
			name = response.getString("title"),
			cover = response.optJSONObject("image")?.optJSONObject("url")?.getString("original").orEmpty(),
			url = contentUrl,
			descriptionHtml = response.optString("description", "").replace("\n", "<br>"),
			tags = genres + categories,
			authors = authors,
			infoboxProperties = infoboxProperties,
			commentThreads = supplemental.commentThreads,
			reviews = supplemental.reviews,
			relatedWorks = relatedWorks,
			recommendations = recommendations,
			actions = supplemental.actions,
		)
	}

	private suspend fun fetchSupplementalDetails(contentUrl: String): MangaUpdatesSupplementalPayload {
		return runCatching {
			val html = Request.Builder()
				.get()
				.url(contentUrl)
				.build()
				.let { okHttp.newCall(it).await().parseRaw() }
			val doc = Jsoup.parse(html, contentUrl)
			val commentThreads = parseSeriesComments(doc)
			val reviewLinks = parseReviewLinks(doc)
			val reviews = reviewLinks.take(REVIEW_PAGE_SIZE).mapNotNull { reviewUrl ->
				runCatching {
					fetchReviewEntry(reviewUrl)
				}.getOrNull()
			}
			val actions = buildList {
				if (commentThreads.isNotEmpty()) {
					add(
						ScrobblerContentInfo.ExternalAction(
							title = "Comments",
							url = "${contentUrl.trimEnd('/') }#comments",
						),
					)
				}
				if (reviews.isNotEmpty() || reviewLinks.isNotEmpty()) {
					add(
						ScrobblerContentInfo.ExternalAction(
							title = "Reviews",
							url = reviewLinks.firstOrNull() ?: contentUrl,
						),
					)
				}
			}
			MangaUpdatesSupplementalPayload(
				commentThreads = commentThreads,
				reviews = reviews,
				actions = actions,
			)
		}.getOrElse { MangaUpdatesSupplementalPayload() }
	}

	private fun parseSeriesComments(doc: Document): List<ScrobblerContentInfo.CommentThread> {
		return doc.select("div[data-cy=comment-row]").take(COMMENTS_PAGE_SIZE).mapNotNull { row ->
			val body = row.nextElementSibling()?.takeIf { it.selectFirst(".mu-markdown-module___SC9hG__mu_markdown") != null }
				?: return@mapNotNull null
			val title = row.selectFirst(".comment_title")?.text()?.trim().orEmpty()
			val authorLink = row.selectFirst("a[href*=/member/]")
			val authorName = authorLink?.text()?.trim().orEmpty().ifBlank { "MangaUpdates User" }
			val avatarUrl = row.selectFirst("img[alt=user avatar]")?.absUrl("src").normalizeBlank()
			val rating = row.selectFirst(".comment_rating")?.text()?.extractFractionalNumber()
			val postedAt = row.selectFirst("time[datetime]")?.attr("datetime")?.takeIf { it.isNotBlank() }?.take(10)
			val content = body.selectFirst(".mu-markdown-module___SC9hG__mu_markdown")
				?.html()
				?.toPlainText()
				?.takeIf { it.isNotBlank() }
				?: return@mapNotNull null
			ScrobblerContentInfo.CommentThread(
				id = row.selectFirst("a[id^=comment]")?.id()?.removePrefix("comment").orEmpty().ifBlank { title },
				userName = authorName,
				userUrl = authorLink?.absUrl("href").normalizeBlank(),
				avatarUrl = avatarUrl,
				rating = rating,
				status = title.takeIf { it.isNotBlank() && !it.equals("No Subject", ignoreCase = true) },
				postedAt = postedAt,
				content = content,
			)
		}
	}

	private fun parseReviewLinks(doc: Document): List<String> {
		val header = doc.select("div[data-cy=info-box-unknown-header] b").firstOrNull {
			it.text().contains("User Reviews", ignoreCase = true)
		} ?: return emptyList()
		val content = header.parent()?.nextElementSibling() ?: return emptyList()
		return content.select("a[href^=/review/], a[href*=/review/]")
			.mapNotNull { anchor -> anchor.absUrl("href").normalizeBlank() }
			.distinct()
	}

	private suspend fun fetchReviewEntry(reviewUrl: String): ScrobblerContentInfo.ReviewEntry? {
		val html = Request.Builder()
			.get()
			.url(reviewUrl)
			.build()
			.let { okHttp.newCall(it).await().parseRaw() }
		val doc = Jsoup.parse(html, reviewUrl)
		val excerpt = doc.selectFirst(".mu-markdown-module___SC9hG__mu_markdown")
			?.html()
			?.toPlainText()
			?.takeIf { it.isNotBlank() }
			?: return null
		val metadata = doc.select("div.p-1.col-12.text").firstOrNull { element ->
			element.text().contains("by") && element.selectFirst("time[datetime]") != null
		}
		val authorLink = metadata?.selectFirst("a[href*=/member/]")
		val authorName = authorLink?.text()?.trim()
			?: metadata?.selectFirst("[class*=review-by-id-module__][class*=newsname]")?.text()?.trim()
			?: metadata?.text()
				?.substringAfter("by", missingDelimiterValue = "")
				?.substringBefore(" on", missingDelimiterValue = "")
				?.trim()
				?.takeIf { it.isNotBlank() }
			?: "MangaUpdates User"
		val postedAt = doc.selectFirst("time[datetime]")?.attr("datetime")?.takeIf { it.isNotBlank() }?.take(10)
		val rating = doc.selectFirst(".specialtext b")?.text()?.extractFractionalNumber()
		val commentsCount = doc.select("div[data-cy=comment-row]").size.takeIf { it > 0 }
		return ScrobblerContentInfo.ReviewEntry(
			id = reviewUrl.substringAfterLast('/'),
			title = excerpt.toReviewTitle(),
			authorName = authorName,
			authorUrl = authorLink?.absUrl("href").normalizeBlank(),
			avatarUrl = null,
			postedAt = postedAt,
			excerpt = buildString {
				rating?.let {
					append("Rating ")
					append(String.format("%.1f/10", it))
					append(" · ")
				}
				append(excerpt)
			},
			url = reviewUrl,
			repliesCount = commentsCount,
		)
	}

	override suspend fun createRate(mangaId: Long, content: ScrobblerContent) {
		val scrobblerContentId = content.id
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

	private fun String.toPlainText(): String {
		return Jsoup.parse(this).text().normalizeWhitespace()
	}

	private fun String.normalizeWhitespace(): String {
		return replace(Regex("\\s+"), " ").trim()
	}

	private fun String.toReviewTitle(): String {
		return lineSequence()
			.map { it.trim() }
			.firstOrNull { it.isNotBlank() }
			?.take(72)
			?.takeIf { it.isNotBlank() }
			?: "Review"
	}

	private fun String?.normalizeBlank(): String? = this?.trim()?.takeIf { it.isNotBlank() }

	private fun String.extractFractionalNumber(): Float? {
		val match = Regex("""(\d+(?:\.\d+)?)""").find(this) ?: return null
		return match.groupValues.getOrNull(1)?.toFloatOrNull()
	}

	private data class MangaUpdatesSupplementalPayload(
		val commentThreads: List<ScrobblerContentInfo.CommentThread> = emptyList(),
		val reviews: List<ScrobblerContentInfo.ReviewEntry> = emptyList(),
		val actions: List<ScrobblerContentInfo.ExternalAction> = emptyList(),
	)
}
