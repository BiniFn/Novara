package org.skepsun.kototoro.entitygraph.ui.details

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.entitygraph.data.EntityGraphRepository
import org.skepsun.kototoro.entitygraph.domain.Entity
import org.skepsun.kototoro.entitygraph.domain.EntityBinding
import org.skepsun.kototoro.entitygraph.domain.EntityGraphSourceAdapter
import org.skepsun.kototoro.entitygraph.domain.EntityType
import org.skepsun.kototoro.entitygraph.domain.Relation
import org.skepsun.kototoro.entitygraph.domain.RelationType
import org.skepsun.kototoro.entitygraph.domain.SourceResult
import org.skepsun.kototoro.entitygraph.domain.TrackingStaffDto
import org.skepsun.kototoro.entitygraph.domain.TrackingWorkDto
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteDiscoveryService
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails
import javax.inject.Inject

@HiltViewModel
class EntityDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val entityGraphRepository: EntityGraphRepository,
    private val sourceAdapter: EntityGraphSourceAdapter,
    private val trackingDiscoveryService: TrackingSiteDiscoveryService,
) : BaseViewModel() {

    private val initialEntityId = savedStateHandle.get<Long>(AppRouter.KEY_ENTITY_ID)?.takeIf { it > 0L }
    private val trackingService = savedStateHandle.get<Int>(AppRouter.KEY_ID)
        ?.let { serviceId -> ScrobblerService.entries.firstOrNull { it.id == serviceId } }
    private val trackingRemoteId = savedStateHandle.get<Long>(AppRouter.KEY_REMOTE_ID)?.takeIf { it > 0L }
    private val trackingUrlHint = savedStateHandle.get<String>(AppRouter.KEY_URL)

    val screenState = MutableStateFlow(EntityDetailsScreenState())
    val error = MutableStateFlow<Throwable?>(null)

    init {
        refresh()
    }

    fun refresh() {
        launchLoadingJob {
            error.value = null
            val result = runCatching {
                val entityId = resolveEntityId()
                loadScreenState(entityId)
            }
            result.onSuccess { state ->
                screenState.value = state
            }.onFailure { failure ->
                error.value = failure
            }
        }
    }

    private suspend fun resolveEntityId(): Long {
        initialEntityId?.let { return it }
        val service = trackingService ?: error("Missing entity or tracking service argument")
        val remoteId = trackingRemoteId ?: error("Missing tracking remote id argument")
        entityGraphRepository.findEntityByBinding(service.toBindingSourceKey(), remoteId.toString())?.let {
            return it.id
        }
        val details = trackingDiscoveryService.getDetails(service, remoteId, trackingUrlHint)
        return entityGraphRepository.ingestWorkFromTracking(
            source = service.toBindingSourceKey(),
            workDto = details.toTrackingWorkDto(),
        ).id
    }

    private suspend fun loadScreenState(entityId: Long): EntityDetailsScreenState {
        val entity = entityGraphRepository.getEntity(entityId) ?: error("Missing entity $entityId")
        val bindings = entityGraphRepository.getBindings(entityId)
        val relations = entityGraphRepository.getRelations(entityId)
        val relatedEntities = entityGraphRepository.getEntitiesByIds(
            relations.mapNotNull { relation ->
                when (entity.id) {
                    relation.fromEntityId -> relation.toEntityId
                    relation.toEntityId -> relation.fromEntityId
                    else -> null
                }
            },
        ).associateBy { it.id }
        val sourceResults = if (entity.type == EntityType.WORK) {
            sourceAdapter.findContentForEntity(entity)
        } else {
            emptyList()
        }
        return EntityDetailsScreenState(
            entity = entity,
            bindings = bindings,
            relationSections = buildRelationSections(entity, relations, relatedEntities),
            sourceResults = sourceResults,
            trackingReference = explicitTrackingReference() ?: bindings.firstTrackingReference(),
        )
    }

    private fun buildRelationSections(
        entity: Entity,
        relations: List<Relation>,
        relatedEntities: Map<Long, Entity>,
    ): List<EntityRelationSection> {
        val outgoing = relations.filter { it.fromEntityId == entity.id }
        val incoming = relations.filter { it.toEntityId == entity.id }
        return when (entity.type) {
            EntityType.WORK -> listOfNotNull(
                buildSection(
                    titleRes = R.string.entity_graph_section_characters,
                    relations = outgoing.filter { it.type == RelationType.HAS_CHARACTER },
                    relatedEntities = relatedEntities,
                    currentEntityId = entity.id,
                ),
                buildSection(
                    titleRes = R.string.entity_graph_section_creators,
                    relations = outgoing.filter { it.type == RelationType.CREATED_BY },
                    relatedEntities = relatedEntities,
                    currentEntityId = entity.id,
                ),
                buildSection(
                    titleRes = R.string.entity_graph_section_related_entities,
                    relations = relations.filter { it.type == RelationType.RELATED_TO },
                    relatedEntities = relatedEntities,
                    currentEntityId = entity.id,
                ),
            )

            EntityType.CHARACTER -> listOfNotNull(
                buildSection(
                    titleRes = R.string.entity_graph_section_parent_work,
                    relations = outgoing.filter { it.type == RelationType.BELONGS_TO },
                    relatedEntities = relatedEntities,
                    currentEntityId = entity.id,
                ),
                buildSection(
                    titleRes = R.string.entity_graph_section_voice_actors,
                    relations = outgoing.filter { it.type == RelationType.VOICED_BY },
                    relatedEntities = relatedEntities,
                    currentEntityId = entity.id,
                ),
                buildSection(
                    titleRes = R.string.entity_graph_section_related_entities,
                    relations = relations.filter { it.type == RelationType.RELATED_TO },
                    relatedEntities = relatedEntities,
                    currentEntityId = entity.id,
                ),
            )

            EntityType.PERSON -> listOfNotNull(
                buildSection(
                    titleRes = R.string.entity_graph_section_created_works,
                    relations = incoming.filter { it.type == RelationType.CREATED_BY },
                    relatedEntities = relatedEntities,
                    currentEntityId = entity.id,
                ),
                buildSection(
                    titleRes = R.string.entity_graph_section_voiced_characters,
                    relations = incoming.filter { it.type == RelationType.VOICED_BY },
                    relatedEntities = relatedEntities,
                    currentEntityId = entity.id,
                ),
                buildSection(
                    titleRes = R.string.entity_graph_section_related_entities,
                    relations = relations.filter { it.type == RelationType.RELATED_TO },
                    relatedEntities = relatedEntities,
                    currentEntityId = entity.id,
                ),
            )

            EntityType.ORGANIZATION -> listOfNotNull(
                buildSection(
                    titleRes = R.string.entity_graph_section_related_entities,
                    relations = relations,
                    relatedEntities = relatedEntities,
                    currentEntityId = entity.id,
                ),
            )
        }
    }

    private fun buildSection(
        titleRes: Int,
        relations: List<Relation>,
        relatedEntities: Map<Long, Entity>,
        currentEntityId: Long,
    ): EntityRelationSection? {
        val items = relations.mapNotNull { relation ->
            val relatedId = when (currentEntityId) {
                relation.fromEntityId -> relation.toEntityId
                relation.toEntityId -> relation.fromEntityId
                else -> null
            } ?: return@mapNotNull null
            relatedEntities[relatedId]?.let { related ->
                EntityRelationItem(
                    entityId = related.id,
                    name = related.primaryName,
                    type = related.type,
                )
            }
        }.distinctBy { it.entityId }
        if (items.isEmpty()) {
            return null
        }
        return EntityRelationSection(
            titleRes = titleRes,
            items = items,
        )
    }

    private fun explicitTrackingReference(): TrackingReference? {
        val service = trackingService ?: return null
        val remoteId = trackingRemoteId ?: return null
        return TrackingReference(
            service = service,
            remoteId = remoteId,
            url = trackingUrlHint,
        )
    }
}

data class EntityDetailsScreenState(
    val entity: Entity? = null,
    val bindings: List<EntityBinding> = emptyList(),
    val relationSections: List<EntityRelationSection> = emptyList(),
    val sourceResults: List<SourceResult> = emptyList(),
    val trackingReference: TrackingReference? = null,
)

data class EntityRelationSection(
    val titleRes: Int,
    val items: List<EntityRelationItem>,
)

data class EntityRelationItem(
    val entityId: Long,
    val name: String,
    val type: EntityType,
)

data class TrackingReference(
    val service: ScrobblerService,
    val remoteId: Long,
    val url: String?,
)

private fun TrackingSiteItemDetails.toTrackingWorkDto(): TrackingWorkDto {
    val aliases = listOfNotNull(altTitle)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return TrackingWorkDto(
        externalId = remoteId.toString(),
        primaryName = title,
        aliases = aliases,
        staff = authors.map { name ->
            TrackingStaffDto(
                primaryName = name,
            )
        },
    )
}

private fun List<EntityBinding>.firstTrackingReference(): TrackingReference? {
    return this.sortedByDescending { it.isPrimary }
        .firstNotNullOfOrNull { binding ->
            val service = ScrobblerService.entries.firstOrNull { it.name.equals(binding.source, ignoreCase = true) }
                ?: return@firstNotNullOfOrNull null
            val remoteId = binding.externalId.toLongOrNull() ?: return@firstNotNullOfOrNull null
            TrackingReference(
                service = service,
                remoteId = remoteId,
                url = null,
            )
        }
}

private fun ScrobblerService.toBindingSourceKey(): String = name.lowercase()
