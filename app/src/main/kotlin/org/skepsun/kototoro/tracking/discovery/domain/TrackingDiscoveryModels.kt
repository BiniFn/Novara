package org.skepsun.kototoro.tracking.discovery.domain

import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService

data class TrackingSiteCatalog(
	val service: ScrobblerService,
	val query: String? = null,
	val contentType: ContentType? = null,
	val page: Int = 0,
)

data class TrackingSiteListItem(
	val service: ScrobblerService,
	val remoteId: Long,
	val title: String,
	val altTitle: String? = null,
	val coverUrl: String? = null,
	val subtitle: String? = null,
	val score: Float? = null,
	val url: String? = null,
)

data class TrackingSiteDetails(
	val service: ScrobblerService,
	val remoteId: Long,
	val title: String,
	val altTitle: String? = null,
	val coverUrl: String? = null,
	val description: String? = null,
	val score: Float? = null,
	val rank: Int? = null,
	val tags: List<String> = emptyList(),
	val authors: List<String> = emptyList(),
	val year: Int? = null,
	val totalEpisodes: Int? = null,
	val url: String? = null,
)

data class TrackingSiteCapabilities(
	val supportsDiscovery: Boolean,
	val supportsTrending: Boolean,
	val supportsSearch: Boolean,
	val supportsDetails: Boolean,
	val supportsStatusSync: Boolean,
	val supportsManualBinding: Boolean,
)

data class TrackingSiteMatchResult(
	val service: ScrobblerService,
	val remoteId: Long,
	val localContent: Content? = null,
	val confidence: Float,
	val title: String,
	val reason: String? = null,
	val isManual: Boolean = false,
)
