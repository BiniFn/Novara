package org.skepsun.kototoro.tracking.discovery.domain

import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService

interface TrackingSiteDiscoveryService {

	fun getCapabilities(service: ScrobblerService): TrackingSiteCapabilities

	suspend fun getTrending(catalog: TrackingSiteCatalog): List<TrackingSiteListItem>

	suspend fun search(catalog: TrackingSiteCatalog): List<TrackingSiteListItem>

	suspend fun getDetails(service: ScrobblerService, remoteId: Long): TrackingSiteDetails
}
