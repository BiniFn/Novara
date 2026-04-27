package org.skepsun.kototoro.entitygraph.ui.details

import androidx.annotation.StringRes
import org.skepsun.kototoro.entitygraph.domain.EntityType
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService

data class EntityRelationSection(
    @StringRes val titleRes: Int? = null,
    val title: String? = null,
    val items: List<EntityRelationItem>,
)

data class EntityRelationItem(
    val stableKey: String,
    val name: String,
    val coverUrl: String?,
    val entityId: Long? = null,
    val type: EntityType? = null,
    val subtitle: String? = null,
    val supportingText: String? = null,
    val detailLines: List<String> = emptyList(),
    val trackingService: ScrobblerService? = null,
    val remoteId: Long? = null,
    val url: String? = null,
)
