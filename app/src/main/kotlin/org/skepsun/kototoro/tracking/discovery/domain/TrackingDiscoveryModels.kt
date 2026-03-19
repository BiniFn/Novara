package org.skepsun.kototoro.tracking.discovery.domain

import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService

/**
 * Discovery 层只负责站点浏览、搜索、详情和匹配候选。
 *
 * `TrackingSiteState` / `TrackingSiteLink` 属于后续的同步与持久化层，
 * 不在当前 discovery package 中定义，避免把不同职责的模型混在一起。
 */
data class TrackingSiteCatalog(
	val service: ScrobblerService,
	val query: String? = null,
	val contentType: ContentType? = null,
	val page: Int = 0,
)

data class TrackingSiteItem(
	val service: ScrobblerService,
	val remoteId: Long,
	val title: String,
	val altTitle: String? = null,
	val coverUrl: String? = null,
	val subtitle: String? = null,
	val score: Float? = null,
	val url: String? = null,
)

@Deprecated(
	message = "Use TrackingSiteItem",
	replaceWith = ReplaceWith("TrackingSiteItem"),
)
typealias TrackingSiteListItem = TrackingSiteItem

data class TrackingSiteItemDetails(
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

@Deprecated(
	message = "Use TrackingSiteItemDetails",
	replaceWith = ReplaceWith("TrackingSiteItemDetails"),
)
typealias TrackingSiteDetails = TrackingSiteItemDetails

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
	val url: String? = null,
	val reason: String? = null,
	val isLinked: Boolean = false,
	val isManual: Boolean = false,
)
