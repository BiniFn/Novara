package org.skepsun.kototoro.tracking.discovery.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
) {

	private data class CachedCategory(
		val items: List<TrackingSiteItem>,
		val timestamp: Long,
	)

	private val categoryCache = java.util.concurrent.ConcurrentHashMap<String, CachedCategory>()

	fun readCategoryCache(service: ScrobblerService, category: String): List<TrackingSiteItem>? {
		val key = "${service.id}_$category"
		val cached = categoryCache[key] ?: return null
		if (System.currentTimeMillis() - cached.timestamp > CATEGORY_CACHE_TTL) {
			categoryCache.remove(key)
			return null
		}
		return cached.items
	}

	fun saveCategoryCache(service: ScrobblerService, category: String, items: List<TrackingSiteItem>) {
		if (items.isEmpty()) return
		val key = "${service.id}_$category"
		categoryCache[key] = CachedCategory(items, System.currentTimeMillis())
	}

	fun clearCategoryCache(service: ScrobblerService, category: String) {
		categoryCache.remove("${service.id}_$category")
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

	companion object {
		private const val CATEGORY_CACHE_TTL = 10 * 60 * 1000L // 10 minutes
	}
}
