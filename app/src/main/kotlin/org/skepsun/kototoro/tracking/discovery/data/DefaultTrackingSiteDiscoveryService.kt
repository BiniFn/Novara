package org.skepsun.kototoro.tracking.discovery.data

import org.skepsun.kototoro.scrobbling.bangumi.data.BangumiRepository
import org.skepsun.kototoro.scrobbling.common.domain.ScrobblerRepositoryMap
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContent
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContentInfo
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteCapabilities
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteCatalog
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteDetails
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteDiscoveryService
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteListItem
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

	override suspend fun getTrending(catalog: TrackingSiteCatalog): List<TrackingSiteListItem> {
		if (catalog.service != ScrobblerService.BANGUMI) {
			return emptyList()
		}
		return bangumiRepository.findContent(query = "", offset = catalog.page * 10)
			.map { item -> item.toTrackingListItem(ScrobblerService.BANGUMI) }
	}

	override suspend fun search(catalog: TrackingSiteCatalog): List<TrackingSiteListItem> {
		val query = catalog.query?.trim().orEmpty()
		if (query.isEmpty()) {
			return getTrending(catalog)
		}
		return repositoryMap[catalog.service]
			.findContent(query, catalog.page * 10)
			.map { item -> item.toTrackingListItem(catalog.service) }
	}

	override suspend fun getDetails(service: ScrobblerService, remoteId: Long): TrackingSiteDetails {
		return repositoryMap[service]
			.getContentInfo(remoteId)
			.toTrackingDetails(service)
	}

	private fun ScrobblerContent.toTrackingListItem(service: ScrobblerService): TrackingSiteListItem {
		return TrackingSiteListItem(
			service = service,
			remoteId = id,
			title = name,
			altTitle = altName,
			coverUrl = cover,
			url = url,
		)
	}

	private fun ScrobblerContentInfo.toTrackingDetails(service: ScrobblerService): TrackingSiteDetails {
		return TrackingSiteDetails(
			service = service,
			remoteId = id,
			title = name,
			coverUrl = cover,
			description = descriptionHtml,
			url = url,
		)
	}
}
