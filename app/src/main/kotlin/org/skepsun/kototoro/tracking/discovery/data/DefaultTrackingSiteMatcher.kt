package org.skepsun.kototoro.tracking.discovery.data

import androidx.room.withTransaction
import dagger.Reusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.entity.toContent
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.util.levenshteinDistance
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.tracking.animeoffline.data.AnimeOfflineRepository
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteCatalog
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteDiscoveryService
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatcher
import javax.inject.Inject

private const val AUTO_MATCH_THRESHOLD = 0.82f
private const val SUGGESTION_LIMIT = 5

@Reusable
class DefaultTrackingSiteMatcher @Inject constructor(
	private val db: MangaDatabase,
	private val discoveryService: TrackingSiteDiscoveryService,
	private val animeOfflineRepository: AnimeOfflineRepository,
) : TrackingSiteMatcher {

	override suspend fun matchLocalContent(
		service: ScrobblerService,
		content: Content,
		limit: Int,
		persistAutoMatch: Boolean,
	): List<TrackingSiteMatchResult> = withContext(Dispatchers.Default) {
		if (!discoveryService.getCapabilities(service).supportsSearch) {
			return@withContext emptyList()
		}
		val dao = db.getTrackingSiteDao()
		val existing = dao.findLinksByManga(service.id, content.id)
		val linked = existing.firstOrNull()
		if (linked != null) {
			return@withContext listOf(linked.toMatchResult(content))
		}

		if (content.source.contentType == ContentType.VIDEO || content.source.contentType == ContentType.HENTAI_VIDEO) {
			val offlineMatches = animeOfflineRepository.matchLocalVideoContent(
				service = service,
				content = content,
				limit = limit,
			)
			if (offlineMatches.isNotEmpty()) {
				return@withContext offlineMatches
			}
		}

		val candidateQueries = buildCandidateQueries(content)
		val remoteCandidates = LinkedHashMap<Long, RemoteCandidate>()
		for (query in candidateQueries) {
			val items = runCatching {
				discoveryService.search(TrackingSiteCatalog(service = service, query = query))
			}.getOrDefault(emptyList())
			items.forEach { item ->
				val score = score(content, item.title, item.altTitle)
				val candidate = remoteCandidates[item.remoteId]
				if (candidate == null || score > candidate.confidence) {
					remoteCandidates[item.remoteId] = RemoteCandidate(
						remoteId = item.remoteId,
						title = item.title,
						url = item.url,
						confidence = score,
						reason = if (query == content.title) {
							"title"
						} else {
							"alias"
						},
					)
				}
			}
		}

		val resultLimit = if (limit > 0) limit else SUGGESTION_LIMIT
		val ranked = remoteCandidates.values
			.sortedWith(
				compareByDescending<RemoteCandidate> { it.confidence }
					.thenBy { it.title },
			)
			.take(resultLimit)
			.map {
				TrackingSiteMatchResult(
					service = service,
					remoteId = it.remoteId,
					localContent = content,
					confidence = it.confidence,
					title = it.title,
					url = it.url,
					reason = it.reason,
					isLinked = false,
					isManual = false,
				)
			}

		val best = ranked.firstOrNull()
		if (persistAutoMatch && best != null && best.confidence >= AUTO_MATCH_THRESHOLD) {
			db.withTransaction {
				dao.deleteLinksByManga(service.id, content.id)
				dao.upsertLink(
					org.skepsun.kototoro.core.db.entity.TrackingSiteLinkEntity(
						service = service.id,
						remoteId = best.remoteId,
						mangaId = content.id,
						sourceName = content.source.name,
						confidence = best.confidence,
						isManual = false,
						createdAt = System.currentTimeMillis(),
						updatedAt = System.currentTimeMillis(),
					),
				)
			}
		}
		ranked
	}

	override suspend fun confirmMatch(
		service: ScrobblerService,
		contentId: Long,
		remoteId: Long,
	): TrackingSiteMatchResult = withContext(Dispatchers.Default) {
		val content = db.getMangaDao().find(contentId)?.toContent() ?: error("Missing local content $contentId")
		val title = db.getTrackingSiteDao().findItem(service.id, remoteId)?.title ?: remoteId.toString()
		db.withTransaction {
			val dao = db.getTrackingSiteDao()
			dao.deleteLinksByManga(service.id, contentId)
			dao.upsertLink(
				org.skepsun.kototoro.core.db.entity.TrackingSiteLinkEntity(
					service = service.id,
					remoteId = remoteId,
					mangaId = contentId,
					sourceName = content.source.name,
					confidence = 1f,
					isManual = true,
					createdAt = System.currentTimeMillis(),
					updatedAt = System.currentTimeMillis(),
				),
			)
		}
		TrackingSiteMatchResult(
			service = service,
			remoteId = remoteId,
			localContent = content,
			confidence = 1f,
			title = title,
			url = db.getTrackingSiteDao().findItem(service.id, remoteId)?.siteUrl,
			reason = "manual",
			isLinked = true,
			isManual = true,
		)
	}

	override suspend fun removeMatch(
		service: ScrobblerService,
		contentId: Long,
	) {
		db.getTrackingSiteDao().deleteLinksByManga(service.id, contentId)
	}

	private fun buildCandidateQueries(content: Content): List<String> {
		return buildList {
			add(content.title.trim())
			addAll(content.altTitles.map { it.trim() })
		}.filter { it.isNotBlank() }
			.distinctBy(::normalizeTitle)
	}

	private fun score(content: Content, remoteTitle: String, remoteAltTitle: String?): Float {
		val localTitles = buildList {
			add(content.title)
			addAll(content.altTitles)
		}.filter { it.isNotBlank() }
		val remoteTitles = buildList {
			add(remoteTitle)
			remoteAltTitle?.let { add(it) }
		}.filter { it.isNotBlank() }
		if (localTitles.isEmpty() || remoteTitles.isEmpty()) {
			return 0f
		}
		var best = 0f
		for (localTitle in localTitles) {
			val normalizedLocal = normalizeTitle(localTitle)
			for (candidateTitle in remoteTitles) {
				val normalizedRemote = normalizeTitle(candidateTitle)
				if (normalizedLocal.isEmpty() || normalizedRemote.isEmpty()) {
					continue
				}
				val maxLen = maxOf(normalizedLocal.length, normalizedRemote.length).coerceAtLeast(1)
				val distance = normalizedLocal.levenshteinDistance(normalizedRemote)
				val similarity = 1f - (distance.toFloat() / maxLen.toFloat())
				if (similarity > best) {
					best = similarity
				}
			}
		}
		return best.coerceIn(0f, 1f)
	}

	private fun normalizeTitle(title: String): String {
		return title.lowercase()
			.replace(Regex("\\s+"), "")
			.replace(Regex("[^a-z0-9\\u4e00-\\u9fff\\u3040-\\u30ff\\u31f0-\\u31ff\\uff66-\\uff9d]"), "")
	}

	private suspend fun org.skepsun.kototoro.core.db.entity.TrackingSiteLinkEntity.toMatchResult(content: Content): TrackingSiteMatchResult {
		return TrackingSiteMatchResult(
			service = ScrobblerService.entries.first { it.id == this.service },
			remoteId = remoteId,
			localContent = content,
			confidence = confidence,
			title = db.getTrackingSiteDao().findItem(this.service, remoteId)?.title ?: remoteId.toString(),
			url = db.getTrackingSiteDao().findItem(this.service, remoteId)?.siteUrl,
			reason = if (isManual) "manual" else "auto",
			isLinked = true,
			isManual = isManual,
		)
	}

	private data class RemoteCandidate(
		val remoteId: Long,
		val title: String,
		val url: String?,
		val confidence: Float,
		val reason: String,
	)
}
