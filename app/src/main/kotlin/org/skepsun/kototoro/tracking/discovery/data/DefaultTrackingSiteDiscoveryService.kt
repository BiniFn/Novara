
package org.skepsun.kototoro.tracking.discovery.data

import org.skepsun.kototoro.scrobbling.bangumi.data.BangumiRepository
import org.skepsun.kototoro.scrobbling.common.domain.ScrobblerRepositoryMap
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContent
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContentInfo
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteCapabilities
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteCatalog
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteCategory
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteDiscoveryService
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItem
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTrackingSiteDiscoveryService @Inject constructor(
	private val repositoryMap: ScrobblerRepositoryMap,
	private val bangumiRepository: BangumiRepository,
) : TrackingSiteDiscoveryService {

	override fun getCapabilities(service: ScrobblerService): TrackingSiteCapabilities = when (service) {
		ScrobblerService.BANGUMI -> TrackingSiteCapabilities(
			supportsDiscovery = true,
			supportsTrending = true,
			supportsSearch = true,
			supportsDetails = true,
			supportsStatusSync = true,
			supportsManualBinding = true,
			discoveryCategories = listOf(
				TrackingSiteCategory("calendar", org.skepsun.kototoro.R.string.discover_category_calendar),
				TrackingSiteCategory("anime", org.skepsun.kototoro.R.string.discover_category_anime_rank),
				TrackingSiteCategory("book", org.skepsun.kototoro.R.string.discover_category_book_rank),
				TrackingSiteCategory("music", org.skepsun.kototoro.R.string.discover_category_music_rank),
				TrackingSiteCategory("game", org.skepsun.kototoro.R.string.discover_category_game_rank),
				TrackingSiteCategory("real", org.skepsun.kototoro.R.string.discover_category_real_rank),
			),
		)

		else -> TrackingSiteCapabilities(
			supportsDiscovery = false,
			supportsTrending = false,
			supportsSearch = true,
			supportsDetails = true,
			supportsStatusSync = true,
			supportsManualBinding = true,
		)
	}

	override suspend fun getTrending(catalog: TrackingSiteCatalog): List<TrackingSiteItem> {
		if (catalog.service != ScrobblerService.BANGUMI) {
			return emptyList()
		}
		
		val requestCategory = catalog.category ?: "anime"

		if (requestCategory.startsWith("calendar")) {
			// Calendar scrape returns all schedule items at once, so paging is ignored
			if (catalog.page > 0) return emptyList()
			
			val dailyCalendar = bangumiRepository.getDailyCalendar()
			val dayFilter = requestCategory.substringAfter("_", "").toIntOrNull()
			
			val targetDay = dayFilter ?: run {
				val cal = java.util.Calendar.getInstance()
				val today = cal.get(java.util.Calendar.DAY_OF_WEEK)
				if (today == java.util.Calendar.SUNDAY) 7 else today - 1
			}

			return dailyCalendar[targetDay].orEmpty()
				.map { item -> item.toTrackingListItem(ScrobblerService.BANGUMI) }
		}
		
		return bangumiRepository.getRankings(
			category = requestCategory, 
			page = catalog.page + 1,
			sortOrder = catalog.sortOrder,
			listFilter = catalog.listFilter
		).map { item -> item.toTrackingListItem(ScrobblerService.BANGUMI) }
	}

	override suspend fun search(catalog: TrackingSiteCatalog): List<TrackingSiteItem> {
		val query = catalog.query?.trim().orEmpty()
		if (query.isEmpty()) {
			return getTrending(catalog)
		}
		return repositoryMap[catalog.service]
			.findContent(query, catalog.page * 10)
			.map { item -> item.toTrackingListItem(catalog.service) }
	}

	override suspend fun getDetails(service: ScrobblerService, remoteId: Long): TrackingSiteItemDetails {
		return repositoryMap[service]
			.getContentInfo(remoteId)
			.toTrackingDetails(service)
	}

	private fun ScrobblerContent.toTrackingListItem(service: ScrobblerService): TrackingSiteItem {
		return TrackingSiteItem(
			service = service,
			remoteId = id,
			title = name,
			altTitle = altName,
			coverUrl = cover,
			url = url,
		)
	}

	private fun ScrobblerContentInfo.toTrackingDetails(service: ScrobblerService): TrackingSiteItemDetails {
		return TrackingSiteItemDetails(
			service = service,
			remoteId = id,
			title = name,
			coverUrl = cover,
			description = descriptionHtml,
			tags = tags,
			authors = authors,
			url = url,
			infoboxProperties = infoboxProperties,
			episodes = episodes.map { ep ->
				TrackingSiteItemDetails.EpisodeInfo(
					number = ep.number,
					title = ep.title,
					url = ep.url,
				)
			},
			relatedWorks = relatedWorks.map { rw ->
				TrackingSiteItemDetails.RelatedWork(
					id = rw.id,
					title = rw.title,
					coverUrl = rw.coverUrl,
					relationship = rw.relationship,
					url = rw.url,
				)
			},
			recommendations = recommendations.map { rec ->
				TrackingSiteItemDetails.RelatedWork(
					id = rec.id,
					title = rec.title,
					coverUrl = rec.coverUrl,
					url = rec.url,
				)
			},
		)
	}
}
