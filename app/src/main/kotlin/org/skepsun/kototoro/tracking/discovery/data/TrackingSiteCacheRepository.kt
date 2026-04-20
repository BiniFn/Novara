package org.skepsun.kototoro.tracking.discovery.data

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.entity.TrackingSiteItemEntity
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItem
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingSiteCacheRepository @Inject constructor(
	private val db: MangaDatabase,
	@ApplicationContext context: Context,
) {

	private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

	private data class CachedCategory(
		val items: List<TrackingSiteItem>,
		val timestamp: Long,
	)

	private val categoryCache = java.util.concurrent.ConcurrentHashMap<String, CachedCategory>()

	fun readCategoryCache(service: ScrobblerService, category: String): List<TrackingSiteItem>? {
		val key = "${service.id}_$category"
		val cached = categoryCache[key]
			?: readPersistedCategoryCache(service, key)
				?.let { restored ->
					CachedCategory(
						items = restored,
						timestamp = readPersistedTimestamp(key),
					).also { persisted ->
						categoryCache[key] = persisted
					}
				}
			?: return null
		if (System.currentTimeMillis() - cached.timestamp > CATEGORY_CACHE_TTL) {
			categoryCache.remove(key)
			clearPersistedCategoryCache(key)
			return null
		}
		return cached.items
	}

	fun saveCategoryCache(service: ScrobblerService, category: String, items: List<TrackingSiteItem>) {
		if (items.isEmpty()) return
		val key = "${service.id}_$category"
		val now = System.currentTimeMillis()
		categoryCache[key] = CachedCategory(items, now)
		persistCategoryCache(service, key, items, now)
	}

	fun clearCategoryCache(service: ScrobblerService, category: String) {
		val key = "${service.id}_$category"
		categoryCache.remove(key)
		clearPersistedCategoryCache(key)
	}

	suspend fun readTrending(service: ScrobblerService): List<TrackingSiteItem> = withContext(Dispatchers.Default) {
		db.getTrackingSiteDao()
			.findItems(service.id)
			.map { it.toTrackingItem() }
	}

	suspend fun saveTrending(items: List<TrackingSiteItem>) {
		if (items.isEmpty()) {
			return
		}
		val now = System.currentTimeMillis()
		db.withTransaction {
			val dao = db.getTrackingSiteDao()
			items.forEach { item ->
				val existing = dao.findItem(item.service.id, item.remoteId)
				dao.upsertItem(existing.mergeWith(item, cachedAt = now, updatedAt = now))
			}
		}
	}

	suspend fun readDetails(service: ScrobblerService, remoteId: Long): TrackingSiteItemDetails? = withContext(Dispatchers.Default) {
		db.getTrackingSiteDao()
			.findItem(service.id, remoteId)
			?.toTrackingDetails()
	}

	suspend fun saveDetails(details: TrackingSiteItemDetails) {
		val now = System.currentTimeMillis()
		db.withTransaction {
			val dao = db.getTrackingSiteDao()
			val existing = dao.findItem(details.service.id, details.remoteId)
			dao.upsertItem(existing.mergeWith(details, cachedAt = now, updatedAt = now))
		}
	}

	private fun TrackingSiteItemEntity?.mergeWith(
		item: TrackingSiteItem,
		cachedAt: Long,
		updatedAt: Long,
	): TrackingSiteItemEntity {
		val existing = this
		return TrackingSiteItemEntity(
			service = item.service.id,
			remoteId = item.remoteId,
			title = item.title,
			altTitles = encodeStringList(listOfNotNull(item.altTitle).ifEmpty { existing?.altTitles?.let(::decodeStringList).orEmpty() }),
			rating = item.score ?: existing?.rating,
			rank = existing?.rank,
			summary = existing?.summary,
			tags = existing?.tags,
			year = existing?.year,
			authors = existing?.authors,
			coverUrl = item.coverUrl ?: existing?.coverUrl,
			totalEpisodes = existing?.totalEpisodes,
			publishDate = existing?.publishDate,
			siteUrl = item.url ?: existing?.siteUrl,
			cachedAt = cachedAt,
			updatedAt = updatedAt,
		)
	}

	private fun TrackingSiteItemEntity?.mergeWith(
		details: TrackingSiteItemDetails,
		cachedAt: Long,
		updatedAt: Long,
	): TrackingSiteItemEntity {
		val existing = this
		return TrackingSiteItemEntity(
			service = details.service.id,
			remoteId = details.remoteId,
			title = details.title,
			altTitles = encodeStringList(listOfNotNull(details.altTitle).ifEmpty { existing?.altTitles?.let(::decodeStringList).orEmpty() }),
			rating = details.score ?: existing?.rating,
			rank = details.rank ?: existing?.rank,
			summary = details.description ?: existing?.summary,
			tags = encodeStringList(details.tags.ifEmpty { existing?.tags?.let(::decodeStringList).orEmpty() }),
			year = details.year ?: existing?.year,
			authors = encodeStringList(details.authors.ifEmpty { existing?.authors?.let(::decodeStringList).orEmpty() }),
			coverUrl = details.coverUrl ?: existing?.coverUrl,
			totalEpisodes = details.totalEpisodes ?: existing?.totalEpisodes,
			publishDate = existing?.publishDate,
			siteUrl = details.url ?: existing?.siteUrl,
			cachedAt = cachedAt,
			updatedAt = updatedAt,
		)
	}

	private fun TrackingSiteItemEntity.toTrackingItem(): TrackingSiteItem {
		val altTitles = decodeStringList(altTitles)
		return TrackingSiteItem(
			service = ScrobblerService.entries.first { it.id == service },
			remoteId = remoteId,
			title = title,
			altTitle = altTitles.firstOrNull(),
			coverUrl = coverUrl,
			subtitle = summary,
			score = rating,
			url = siteUrl,
		)
	}

	private fun TrackingSiteItemEntity.toTrackingDetails(): TrackingSiteItemDetails {
		return TrackingSiteItemDetails(
			service = ScrobblerService.entries.first { it.id == service },
			remoteId = remoteId,
			title = title,
			altTitle = decodeStringList(altTitles).firstOrNull(),
			coverUrl = coverUrl,
			description = summary,
			score = rating,
			rank = rank,
			tags = decodeStringList(tags),
			authors = decodeStringList(authors),
			year = year,
			totalEpisodes = totalEpisodes,
			url = siteUrl,
		)
	}

	private fun encodeStringList(values: List<String>): String? {
		if (values.isEmpty()) {
			return null
		}
		return JSONArray(values).toString()
	}

	private fun decodeStringList(raw: String?): List<String> {
		if (raw.isNullOrBlank()) {
			return emptyList()
		}
		return runCatching {
			JSONArray(raw).let { array ->
				buildList(array.length()) {
					for (index in 0 until array.length()) {
						val value = array.optString(index).trim()
						if (value.isNotEmpty()) {
							add(value)
						}
					}
				}
			}
		}.getOrElse { emptyList() }
	}

	private fun persistCategoryCache(
		service: ScrobblerService,
		key: String,
		items: List<TrackingSiteItem>,
		timestamp: Long,
	) {
		val payload = JSONArray().apply {
			items.forEach { item ->
				put(
					JSONObject().apply {
						put("remoteId", item.remoteId)
						put("title", item.title)
						put("altTitle", item.altTitle)
						put("coverUrl", item.coverUrl)
						put("subtitle", item.subtitle)
						if (item.score != null) {
							put("score", item.score.toDouble())
						}
						put("url", item.url)
					},
				)
			}
		}.toString()
		prefs.edit()
			.putString(categoryCacheItemsKey(key), payload)
			.putLong(categoryCacheTimestampKey(key), timestamp)
			.apply()
	}

	private fun readPersistedCategoryCache(
		service: ScrobblerService,
		key: String,
	): List<TrackingSiteItem>? {
		val payload = prefs.getString(categoryCacheItemsKey(key), null)?.takeIf { it.isNotBlank() } ?: return null
		return runCatching {
			JSONArray(payload).let { array ->
				buildList(array.length()) {
					for (index in 0 until array.length()) {
						val item = array.optJSONObject(index) ?: continue
						val remoteId = item.optLong("remoteId", -1L)
						val title = item.optString("title").trim()
						if (remoteId < 0L || title.isEmpty()) {
							continue
						}
						add(
							TrackingSiteItem(
								service = service,
								remoteId = remoteId,
								title = title,
								altTitle = item.optString("altTitle").nullIfBlank(),
								coverUrl = item.optString("coverUrl").nullIfBlank(),
								subtitle = item.optString("subtitle").nullIfBlank(),
								score = item.takeIf { it.has("score") }?.optDouble("score")?.toFloat(),
								url = item.optString("url").nullIfBlank(),
							),
						)
					}
				}
			}
		}.getOrNull()?.takeIf { it.isNotEmpty() }
	}

	private fun readPersistedTimestamp(key: String): Long {
		return prefs.getLong(categoryCacheTimestampKey(key), 0L)
	}

	private fun clearPersistedCategoryCache(key: String) {
		prefs.edit()
			.remove(categoryCacheItemsKey(key))
			.remove(categoryCacheTimestampKey(key))
			.apply()
	}

	private fun categoryCacheItemsKey(key: String): String = "category_items_$key"

	private fun categoryCacheTimestampKey(key: String): String = "category_timestamp_$key"

	private fun String.nullIfBlank(): String? = takeIf { it.isNotBlank() }

	companion object {
		private const val PREFS_NAME = "tracking_site_discovery_cache"
		private const val CATEGORY_CACHE_TTL = 12 * 60 * 60 * 1000L // 12 hours
	}
}
