package org.skepsun.kototoro.entitygraph.ui.details

import org.skepsun.kototoro.entitygraph.domain.EntityType

data class EntityRelationSection(
    val titleRes: Int,
    val items: List<EntityRelationItem>,
)

data class EntityRelationItem(
    val entityId: Long,
    val name: String,
    val type: EntityType,
    val coverUrl: String?,
)
