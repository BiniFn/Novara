package org.skepsun.kototoro.scrobbling.anilist.data

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.json.JSONObject
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.parsers.exception.GraphQLException
import org.skepsun.kototoro.parsers.util.await
import org.skepsun.kototoro.parsers.util.json.getStringOrNull
import org.skepsun.kototoro.parsers.util.json.mapJSON
import org.skepsun.kototoro.parsers.util.parseJson
import org.skepsun.kototoro.parsers.util.toIntUp
import org.skepsun.kototoro.scrobbling.common.data.ScrobblerRepository
import org.skepsun.kototoro.scrobbling.common.data.ScrobblerStorage
import org.skepsun.kototoro.scrobbling.common.data.ScrobblerUserProfileRepository
import org.skepsun.kototoro.scrobbling.common.data.ScrobblingEntity
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContent
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContentInfo
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerType
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerUser
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerUserProfile
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerUserStats
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val REDIRECT_URI = "kototoro://anilist-auth"
private const val BASE_URL = "https://anilist.co/api/v2/"
private const val ENDPOINT = "https://graphql.anilist.co"
private const val MANGA_PAGE_SIZE = 10
private const val REQUEST_QUERY = "query"
private const val REQUEST_MUTATION = "mutation"
private const val KEY_SCORE_FORMAT = "score_format"

@Singleton
class AniListRepository @Inject constructor(
	@ApplicationContext context: Context,
	@ScrobblerType(ScrobblerService.ANILIST) private val okHttp: OkHttpClient,
	@ScrobblerType(ScrobblerService.ANILIST) private val storage: ScrobblerStorage,
	private val db: MangaDatabase,
) : ScrobblerRepository, ScrobblerUserProfileRepository {

	private val clientId = context.getString(R.string.anilist_clientId)
	private val clientSecret = context.getString(R.string.anilist_clientSecret)

	override val oauthUrl: String
		get() = "${BASE_URL}oauth/authorize?client_id=$clientId&" +
			"redirect_uri=${REDIRECT_URI}&response_type=code"

	override val isAuthorized: Boolean
		get() = storage.accessToken != null

	private val shrinkRegex = Regex("\\t+")

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
		return loadUserProfile().user
	}

	override suspend fun loadUserProfile(): ScrobblerUserProfile {
		val response = doRequest(
			REQUEST_QUERY,
			"""
			Viewer {
					id
					name
					avatar {
						medium
					}
					mediaListOptions {
						scoreFormat
					}
					statistics {
						anime {
							count
							episodesWatched
							meanScore
						}
						manga {
							count
							chaptersRead
							meanScore
						}
					}
				}
			""",
		)
		val jo = response.getJSONObject("data").getJSONObject("Viewer")
		storage[KEY_SCORE_FORMAT] = jo.getJSONObject("mediaListOptions").getString("scoreFormat")
		val user = AniListUser(jo).also { storage.user = it }
		val statistics = jo.optJSONObject("statistics")
		val anime = statistics?.optJSONObject("anime")
		val manga = statistics?.optJSONObject("manga")
		return ScrobblerUserProfile(
			user = user,
			stats = ScrobblerUserStats(
				animeCount = anime.optIntOrNull("count"),
				mangaCount = manga.optIntOrNull("count"),
				episodesWatched = anime.optIntOrNull("episodesWatched"),
				chaptersRead = manga.optIntOrNull("chaptersRead"),
				animeMeanScore = anime.optDoubleOrNull("meanScore"),
				mangaMeanScore = manga.optDoubleOrNull("meanScore"),
			),
		)
	}

	override val cachedUser: ScrobblerUser?
		get() {
			return storage.user
		}

	override suspend fun unregister(mangaId: Long) {
		return db.getScrobblingDao().delete(ScrobblerService.ANILIST.id, mangaId)
	}

	override fun logout() {
		storage.clear()
	}

	override suspend fun findContent(query: String, offset: Int, isAnime: Boolean): List<ScrobblerContent> {
		val page = (offset / MANGA_PAGE_SIZE.toFloat()).toIntUp() + 1
		val mediaType = if (isAnime) "ANIME" else "MANGA"
		val response = doRequest(
			REQUEST_QUERY,
			"""
			Page(page: $page, perPage: ${MANGA_PAGE_SIZE}) {
				media(type: $mediaType, sort: SEARCH_MATCH, search: ${JSONObject.quote(query)}) {
					id
					type
					format
					title {
						userPreferred
						native
					}
					coverImage {
						medium
					}
					siteUrl
				}
			}
			""",
		)
		val data = response.getJSONObject("data").getJSONObject("Page").getJSONArray("media")
		return data.mapJSON { ScrobblerContent(it, query) }
	}

	override suspend fun createRate(mangaId: Long, content: ScrobblerContent) {
		val scrobblerContentId = content.id
		val response = doRequest(
			REQUEST_MUTATION,
			"""
				SaveMediaListEntry(mediaId: $scrobblerContentId) {
					id
					mediaId
					status
					notes
					score
					progress
				}
			""",
		)
		saveRate(response.getJSONObject("data").getJSONObject("SaveMediaListEntry"), mangaId)
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, chapter: Int) {
		val response = doRequest(
			REQUEST_MUTATION,
			"""
				SaveMediaListEntry(id: $rateId, progress: $chapter) {
					id
					mediaId
					status
					notes
					score
					progress
				}
			""",
		)
		saveRate(response.getJSONObject("data").getJSONObject("SaveMediaListEntry"), mangaId)
	}

	override suspend fun updateRate(rateId: Int, mangaId: Long, rating: Float, status: String?, comment: String?) {
		val scoreRaw = (rating * 100f).roundToInt()
		val statusString = status?.let { ", status: $it" }.orEmpty()
		val notesString = comment?.let { ", notes: ${JSONObject.quote(it)}" }.orEmpty()
		val response = doRequest(
			REQUEST_MUTATION,
			"""
				SaveMediaListEntry(id: $rateId, scoreRaw: $scoreRaw$statusString$notesString) {
					id
					mediaId
					status
					notes
					score
					progress
				}
			""",
		)
		saveRate(response.getJSONObject("data").getJSONObject("SaveMediaListEntry"), mangaId)
	}

	override suspend fun getContentInfo(id: Long): ScrobblerContentInfo {
		return getMediaDetails(id)
	}

	suspend fun getAnimeInfo(id: Long): ScrobblerContentInfo {
		return getMediaDetails(id)
	}

	private suspend fun getMediaDetails(id: Long): ScrobblerContentInfo {
		val response = doRequest(
			REQUEST_QUERY,
			"""
			Media(id: $id) {
				id
				title {
					userPreferred
					native
					english
				}
				coverImage {
					extraLarge
					large
				}
				description(asHtml: true)
				siteUrl
				type
				format
				status
				episodes
				chapters
				volumes
				season
				seasonYear
				meanScore
				genres
				tags {
					name
					rank
				}
				staff(perPage: 10) {
					nodes {
						name {
							full
						}
					}
					edges {
						role
					}
				}
				characters(sort: [ROLE, FAVOURITES_DESC], perPage: 25, page: 1) {
					edges {
						role
						voiceActors {
							id
							name {
								full
								userPreferred
							}
							image {
								large
								medium
							}
							siteUrl
						}
						node {
							id
							name {
								userPreferred
								full
							}
							image {
								large
								medium
							}
							siteUrl
						}
					}
				}
				relations {
					edges {
						relationType
						node {
							id
							title {
								userPreferred
							}
							coverImage {
								extraLarge
								large
								medium
							}
							siteUrl
						}
					}
				}
				recommendations(perPage: 10, sort: RATING_DESC) {
					nodes {
						mediaRecommendation {
							id
							title {
								userPreferred
							}
							coverImage {
								extraLarge
								large
								medium
							}
							siteUrl
						}
					}
				}
				reviews(perPage: 10, sort: SCORE_DESC) {
					nodes {
						id
						summary
						body(asHtml: true)
						siteUrl
						createdAt
						user {
							id
							name
							avatar {
								large
								medium
							}
						}
					}
				}
			}
			""",
		)
		return parseMediaDetails(response.getJSONObject("data").getJSONObject("Media"))
	}

	private fun JSONObject?.optIntOrNull(key: String): Int? {
		if (this == null || isNull(key)) {
			return null
		}
		return optInt(key)
	}

	private fun JSONObject?.optDoubleOrNull(key: String): Double? {
		if (this == null || isNull(key)) {
			return null
		}
		return optDouble(key)
	}

	suspend fun getTrending(
		mediaType: String,
		sort: String,
		page: Int,
		perPage: Int = 25,
		format: String? = null,
		countryOfOrigin: String? = null,
	): List<ScrobblerContent> {
		val formatFilter = format?.let { ", format: $it" }.orEmpty()
		val countryFilter = countryOfOrigin?.let { ", countryOfOrigin: $it" }.orEmpty()
		val response = doRequest(
			REQUEST_QUERY,
			"""
			Page(page: $page, perPage: $perPage) {
				media(type: $mediaType, sort: $sort$formatFilter$countryFilter) {
					id
					title {
						userPreferred
						native
						english
						romaji
					}
					coverImage {
						large
					}
					siteUrl
					meanScore
					format
					seasonYear
					status
					updatedAt
					episodes
					chapters
					nextAiringEpisode {
						episode
						airingAt
					}
				}
			}
			""",
		)
		val data = response.getJSONObject("data").getJSONObject("Page").getJSONArray("media")
		val results = mutableListOf<ScrobblerContent>()
		for (i in 0 until data.length()) {
			val json = data.optJSONObject(i) ?: continue
			val title = json.getJSONObject("title")
			val preferredTitle = title.getString("userPreferred")
			val nativeTitle = title.getStringOrNull("native")
			val secondaryTitle = sequenceOf(
				preferredTitle,
				title.getStringOrNull("english"),
				title.getStringOrNull("romaji"),
			).filterNotNull().firstOrNull { !it.equals(nativeTitle, ignoreCase = true) }
			val score = json.optInt("meanScore", 0).takeIf { it > 0 }?.toFloat()
			val format = json.optString("format", "").ifBlank { null }?.replace("_", " ")
			val year = json.optInt("seasonYear", 0).takeIf { it > 0 }?.toString()
			results.add(
				ScrobblerContent(
					id = json.getLong("id"),
					name = preferredTitle,
					altName = nativeTitle ?: secondaryTitle,
					cover = json.getJSONObject("coverImage").getStringOrNull("large"),
					url = json.getString("siteUrl"),
					mediaType = format,
					primaryTitle = nativeTitle ?: preferredTitle,
					secondaryTitle = secondaryTitle,
					subtitle = listOfNotNull(
						format,
						year,
						json.getStringOrNull("status")?.toDiscoveryLabel(),
					).joinToString(" · ").ifBlank { null },
					progressText = if (mediaType == "ANIME") {
						json.optInt("episodes", 0).takeIf { it > 0 }?.let { "EP $it" }
							?: json.optJSONObject("nextAiringEpisode")?.optInt("episode")?.takeIf { it > 0 }?.let { "EP $it" }
					} else {
						json.optInt("chapters", 0).takeIf { it > 0 }?.let { "CH $it" }
					},
					updatedAtText = json.optInt("updatedAt", 0).takeIf { it > 0 }?.let(::aniListTimestampToDate)
						?: json.optJSONObject("nextAiringEpisode")
							?.optInt("airingAt")
							?.takeIf { it > 0 }
							?.let(::aniListTimestampToDate),
					score = score,
					scoreMax = 100f,
					isBestMatch = false,
				),
			)
		}
		return results
	}

	suspend fun getAiringSchedule(
		dateMillis: Long,
		page: Int,
		perPage: Int = 25,
	): List<ScrobblerContent> {
		val targetDate = Instant.ofEpochMilli(dateMillis)
			.atZone(ZoneId.systemDefault())
			.toLocalDate()
		val dayStart = targetDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
		val nextDayStart = targetDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
		val response = doRequest(
			REQUEST_QUERY,
			"""
			Page(page: $page, perPage: $perPage) {
				airingSchedules(
					airingAt_greater: ${dayStart - 1},
					airingAt_lesser: $nextDayStart,
					sort: TIME
				) {
					episode
					airingAt
					media {
						id
						title {
							userPreferred
							native
							english
							romaji
						}
						coverImage {
							large
						}
						siteUrl
						meanScore
						format
						seasonYear
						status
						episodes
					}
				}
			}
			""",
		)
		val data = response.getJSONObject("data").getJSONObject("Page").getJSONArray("airingSchedules")
		val results = mutableListOf<ScrobblerContent>()
		for (i in 0 until data.length()) {
			val json = data.optJSONObject(i) ?: continue
			val media = json.optJSONObject("media") ?: continue
			results.add(media.toAiringScheduleContent(json, targetDate))
		}
		return results
	}

	private fun String.toDiscoveryLabel(): String {
		return lowercase(Locale.ROOT)
			.replace("_", " ")
			.replaceFirstChar { it.uppercaseChar() }
	}

	private fun JSONObject.toAiringScheduleContent(
		scheduleJson: JSONObject,
		targetDate: LocalDate,
	): ScrobblerContent {
		val title = getJSONObject("title")
		val preferredTitle = title.getString("userPreferred")
		val nativeTitle = title.getStringOrNull("native")
		val secondaryTitle = sequenceOf(
			preferredTitle,
			title.getStringOrNull("english"),
			title.getStringOrNull("romaji"),
		).filterNotNull().firstOrNull { !it.equals(nativeTitle, ignoreCase = true) }
		val score = optInt("meanScore", 0).takeIf { it > 0 }?.toFloat()
		val format = optString("format", "").ifBlank { null }?.replace("_", " ")
		val airingEpisode = scheduleJson.optInt("episode", 0).takeIf { it > 0 }
		val airingAt = scheduleJson.optInt("airingAt", 0).takeIf { it > 0 }
		return ScrobblerContent(
			id = getLong("id"),
			name = preferredTitle,
			altName = nativeTitle ?: secondaryTitle,
			cover = getJSONObject("coverImage").getStringOrNull("large"),
			url = getString("siteUrl"),
			mediaType = format,
			primaryTitle = nativeTitle ?: preferredTitle,
			secondaryTitle = secondaryTitle,
			subtitle = listOfNotNull(
				format,
				getStringOrNull("status")?.toDiscoveryLabel(),
			).joinToString(" · ").ifBlank { null },
			progressText = airingEpisode?.let { "EP $it" }
				?: optInt("episodes", 0).takeIf { it > 0 }?.let { "EP $it" },
			updatedAtText = airingAt?.let(::aniListTimestampToLocalTime)
				?: targetDate.toString(),
			score = score,
			scoreMax = 100f,
			isBestMatch = false,
		)
	}

	private fun aniListTimestampToDate(timestampSeconds: Int): String {
		return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestampSeconds * 1000L))
	}

	private fun aniListTimestampToLocalTime(timestampSeconds: Int): String {
		return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampSeconds * 1000L))
	}

	/**
	 * Sync all manga list entries from AniList to local database.
	 * Uses AniList GraphQL query to fetch the user's MANGA media list.
	 */
	suspend fun syncLibraryFromRemote(): Int {
		val user = cachedUser ?: loadUser()
		val scoreFormat = ScoreFormat.of(storage[KEY_SCORE_FORMAT])
		val oldMappings = db.getScrobblingDao()
			.findAllByScrobbler(ScrobblerService.ANILIST.id)
			.groupBy { it.targetId }
			.mapValues { (_, values) ->
				values.firstOrNull { it.mangaId != 0L }?.mangaId ?: 0L
			}

		val synced = ArrayList<ScrobblingEntity>()
		val typesToSync = listOf("MANGA", "ANIME")
		
		for (type in typesToSync) {
			var page = 1
			while (true) {
				val response = doRequest(
					REQUEST_QUERY,
					"""
					Page(page: $page, perPage: 50) {
						pageInfo {
							hasNextPage
						}
						mediaList(userId: ${user.id}, type: $type) {
							id
							mediaId
							status
							notes
							score
							progress
						}
					}
					""",
				)
				val pageData = response.getJSONObject("data").getJSONObject("Page")
				val mediaList = pageData.getJSONArray("mediaList")
				val hasNextPage = pageData.getJSONObject("pageInfo").getBoolean("hasNextPage")

				for (i in 0 until mediaList.length()) {
					val json = mediaList.optJSONObject(i) ?: continue
					val mediaId = json.optLong("mediaId", 0L)
					if (mediaId == 0L) continue
					val mappedContentId = oldMappings[mediaId] ?: 0L
					synced.add(
						ScrobblingEntity(
							scrobbler = ScrobblerService.ANILIST.id,
							id = json.getInt("id"),
							mangaId = mappedContentId,
							targetId = mediaId,
							status = json.getString("status"),
							chapter = json.getInt("progress"),
							comment = json.optString("notes", ""),
							rating = scoreFormat.normalize(json.getDouble("score").toFloat()),
						),
					)
				}
				if (!hasNextPage) break
				page++
			}
		}

		db.withTransaction {
			db.getScrobblingDao().deleteByScrobbler(ScrobblerService.ANILIST.id)
			synced.forEach { entity ->
				db.getScrobblingDao().upsert(entity)
			}
		}
		return synced.size
	}

	private suspend fun saveRate(json: JSONObject, mangaId: Long) {
		val scoreFormat = ScoreFormat.of(storage[KEY_SCORE_FORMAT])
		val entity = ScrobblingEntity(
			scrobbler = ScrobblerService.ANILIST.id,
			id = json.getInt("id"),
			mangaId = mangaId,
			targetId = json.getLong("mediaId"),
			status = json.getString("status"),
			chapter = json.getInt("progress"),
			comment = json.getString("notes"),
			rating = scoreFormat.normalize(json.getDouble("score").toFloat()),
		)
		db.getScrobblingDao().upsert(entity)
	}

	private fun ScrobblerContent(json: JSONObject, sourceTitle: String): ScrobblerContent {
		val title = json.getJSONObject("title")
		val format = json.optString("format", "").ifBlank { json.optString("type", "") }.ifBlank { null }
		return ScrobblerContent(
			id = json.getLong("id"),
			name = title.getString("userPreferred"),
			altName = title.getStringOrNull("native"),
			cover = json.getJSONObject("coverImage").optAniListCoverUrl(),
			url = json.getString("siteUrl"),
			mediaType = format?.replace("_", " "),
			isBestMatch = sourceTitle.let {
				title.keys().forEach { key ->
					if (title.getStringOrNull(key)?.equals(it, ignoreCase = true) == true) {
						return@let true
					}
				}
				false
			},
		)
	}

	private fun parseMediaDetails(json: JSONObject): ScrobblerContentInfo {
		// Genres
		val genres = mutableListOf<String>()
		json.optJSONArray("genres")?.let { arr ->
			for (i in 0 until arr.length()) {
				arr.optString(i)?.takeIf { it.isNotBlank() }?.let(genres::add)
			}
		}
		// Tags (top 10 by rank)
		json.optJSONArray("tags")?.let { arr ->
			for (i in 0 until minOf(arr.length(), 10)) {
				arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let(genres::add)
			}
		}

		// Staff
		val authors = mutableListOf<String>()
		json.optJSONObject("staff")?.let { staffObj ->
			val nodes = staffObj.optJSONArray("nodes")
			val edges = staffObj.optJSONArray("edges")
			if (nodes != null && edges != null) {
				for (i in 0 until minOf(nodes.length(), edges.length())) {
					val name = nodes.optJSONObject(i)?.optJSONObject("name")?.optString("full")
					val role = edges.optJSONObject(i)?.optString("role")
					if (!name.isNullOrBlank()) {
						authors.add(if (!role.isNullOrBlank()) "$name ($role)" else name)
					}
				}
			}
		}

		val characters = mutableListOf<ScrobblerContentInfo.CharacterInfo>()
		json.optJSONObject("characters")?.optJSONArray("edges")?.let { edges ->
			for (i in 0 until edges.length()) {
				val edge = edges.optJSONObject(i) ?: continue
				val node = edge.optJSONObject("node") ?: continue
				val characterId = node.optLong("id", 0L)
				if (characterId <= 0L) {
					continue
				}
				val voiceActors = edge.optJSONArray("voiceActors")
					?.mapJSON { actor ->
						ScrobblerContentInfo.PersonInfo(
							id = actor.optLong("id").takeIf { it > 0L },
							name = actor.optJSONObject("name")?.getStringOrNull("userPreferred")
								?: actor.optJSONObject("name")?.getStringOrNull("full")
								.orEmpty(),
							avatarUrl = actor.optJSONObject("image")?.optAniListCoverUrl()?.takeIf { it.isNotBlank() },
							url = actor.getStringOrNull("siteUrl"),
						)
					}
					?.filter { it.name.isNotBlank() }
					.orEmpty()
					.distinctBy { it.id ?: it.name }
				characters.add(
					ScrobblerContentInfo.CharacterInfo(
						id = characterId,
						name = node.optJSONObject("name")?.getStringOrNull("userPreferred")
							?: node.optJSONObject("name")?.getStringOrNull("full")
							?: "Unknown",
						coverUrl = node.optJSONObject("image")?.optAniListCoverUrl().orEmpty(),
						role = edge.getStringOrNull("role")?.toDiscoveryLabel(),
						url = node.getStringOrNull("siteUrl") ?: "https://anilist.co/character/$characterId",
						voiceActors = voiceActors,
					),
				)
			}
		}

		// Relations
		val relatedWorks = mutableListOf<ScrobblerContentInfo.RelatedWork>()
		json.optJSONObject("relations")?.optJSONArray("edges")?.let { edges ->
			for (i in 0 until edges.length()) {
				val edge = edges.optJSONObject(i) ?: continue
				val node = edge.optJSONObject("node") ?: continue
				val relId = node.optLong("id", 0L)
				if (relId > 0L) {
					relatedWorks.add(ScrobblerContentInfo.RelatedWork(
						id = relId,
						title = node.optJSONObject("title")?.optString("userPreferred") ?: "Unknown",
						coverUrl = node.optJSONObject("coverImage")?.optAniListCoverUrl().orEmpty(),
						relationship = edge.optString("relationType")?.takeIf { it.isNotBlank() },
						url = node.optString("siteUrl") ?: "https://anilist.co/anime/$relId",
					))
				}
			}
		}

		// Recommendations
		val recommendations = mutableListOf<ScrobblerContentInfo.RelatedWork>()
		json.optJSONObject("recommendations")?.optJSONArray("nodes")?.let { nodes ->
			for (i in 0 until nodes.length()) {
				val rec = nodes.optJSONObject(i)?.optJSONObject("mediaRecommendation") ?: continue
				val recId = rec.optLong("id", 0L)
				if (recId > 0L) {
					recommendations.add(ScrobblerContentInfo.RelatedWork(
						id = recId,
						title = rec.optJSONObject("title")?.optString("userPreferred") ?: "Unknown",
						coverUrl = rec.optJSONObject("coverImage")?.optAniListCoverUrl().orEmpty(),
						url = rec.optString("siteUrl") ?: "https://anilist.co/anime/$recId",
					))
				}
			}
		}

		val reviews = mutableListOf<ScrobblerContentInfo.ReviewEntry>()
		json.optJSONObject("reviews")?.optJSONArray("nodes")?.let { nodes ->
			for (i in 0 until nodes.length()) {
				val review = nodes.optJSONObject(i) ?: continue
				val reviewId = review.optLong("id", 0L).takeIf { it > 0L }?.toString()
					?: "review_$i"
				val reviewUrl = review.getStringOrNull("siteUrl").orEmpty()
				val user = review.optJSONObject("user")
				val authorName = user?.getStringOrNull("name").orEmpty()
				val summary = review.getStringOrNull("summary").orEmpty().trim()
				val excerpt = review.getStringOrNull("body")
					?.htmlToPlainText()
					?.takeIf { it.isNotBlank() }
					?.truncateForReviewExcerpt()
					.orEmpty()
				if (reviewUrl.isBlank() || authorName.isBlank() || excerpt.isBlank()) {
					continue
				}
				val authorId = user?.optLong("id", 0L)?.takeIf { it > 0L }
				reviews.add(
					ScrobblerContentInfo.ReviewEntry(
						id = reviewId,
						title = summary.ifBlank { excerpt.take(48) },
						authorName = authorName,
						authorUrl = authorId?.let { "https://anilist.co/user/$it" },
						avatarUrl = user?.optJSONObject("avatar")?.optAniListCoverUrl()?.takeIf { it.isNotBlank() },
						postedAt = review.optInt("createdAt", 0).takeIf { it > 0 }?.let(::aniListTimestampToDate),
						excerpt = excerpt,
						url = reviewUrl,
					),
				)
			}
		}

		// Infobox
		val infobox = mutableListOf<Pair<String, String>>()
		json.optString("format").takeIf { it.isNotBlank() }?.let { infobox.add("Format" to it) }
		json.optString("status").takeIf { it.isNotBlank() }?.let { infobox.add("Status" to it) }
		json.optInt("episodes", 0).takeIf { it > 0 }?.let { infobox.add("Episodes" to it.toString()) }
		json.optInt("chapters", 0).takeIf { it > 0 }?.let { infobox.add("Chapters" to it.toString()) }
		json.optInt("volumes", 0).takeIf { it > 0 }?.let { infobox.add("Volumes" to it.toString()) }
		val score = json.optInt("meanScore", 0)
		if (score > 0) infobox.add("Score" to "$score%")
		val season = json.optString("season").takeIf { it.isNotBlank() }
		val seasonYear = json.optInt("seasonYear", 0).takeIf { it > 0 }
		if (season != null || seasonYear != null) {
			infobox.add("Season" to listOfNotNull(season, seasonYear?.toString()).joinToString(" "))
		}

		val coverImage = json.optJSONObject("coverImage")
		val cover = coverImage?.getStringOrNull("extraLarge")
			?: coverImage?.getStringOrNull("large").orEmpty()

		return ScrobblerContentInfo(
			id = json.getLong("id"),
			name = json.getJSONObject("title").getString("userPreferred"),
			cover = cover,
			url = json.getString("siteUrl"),
			descriptionHtml = json.optString("description", "").normalizeAniListDescriptionHtml(),
			tags = genres,
			authors = authors,
			infoboxProperties = infobox,
			characters = characters.distinctBy { it.id },
			reviews = reviews,
			relatedWorks = relatedWorks,
			recommendations = recommendations,
		)
	}

	private fun JSONObject.optAniListCoverUrl(): String {
		return getStringOrNull("extraLarge")
			?: getStringOrNull("large")
			?: getStringOrNull("medium")
			.orEmpty()
	}

	private fun String.normalizeAniListDescriptionHtml(): String {
		return replace(Regex("(?i)</p>\\s*<p>"), "<br/><br/>")
			.replace(Regex("(?i)</?p>"), "")
			.replace(Regex("(?i)(<br\\s*/?>\\s*){3,}"), "<br/><br/>")
			.replace("~!", "")
			.replace("!~", "")
			.trim()
	}

	private fun String.htmlToPlainText(): String {
		return Jsoup.parse(this)
			.text()
			.replace(Regex("\\s+"), " ")
			.trim()
	}

	private fun String.truncateForReviewExcerpt(maxLength: Int = 220): String {
		if (length <= maxLength) {
			return this
		}
		return take(maxLength).trimEnd() + "…"
	}

	@Suppress("FunctionName")
	private fun AniListUser(json: JSONObject) = ScrobblerUser(
		id = json.getLong("id"),
		nickname = json.getString("name"),
		avatar = json.getJSONObject("avatar").getStringOrNull("medium"),
		service = ScrobblerService.ANILIST,
	)

	private suspend fun doRequest(type: String, payload: String): JSONObject {
		val body = JSONObject()
		body.put("query", "$type { ${payload.shrink()} }")
		val mediaType = "application/json; charset=utf-8".toMediaType()
		val requestBody = body.toString().toRequestBody(mediaType)
		val request = Request.Builder()
			.post(requestBody)
			.url(ENDPOINT)
		val json = okHttp.newCall(request.build()).await().parseJson()
		json.optJSONArray("errors")?.let {
			if (it.length() != 0) {
				throw GraphQLException(it)
			}
		}
		return json
	}

	private fun String.shrink() = replace(shrinkRegex, " ")
}
