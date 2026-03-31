package org.skepsun.kototoro.discover.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.entity.toContent
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.util.ext.findKeyByValue
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.scrobbling.common.data.ScrobblingEntity
import org.skepsun.kototoro.scrobbling.common.domain.Scrobbler
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblingStatus
import org.skepsun.kototoro.tracking.discovery.data.TrackingSiteCacheRepository
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteDiscoveryService
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails
import javax.inject.Inject

@HiltViewModel
class TrackingSiteDetailsViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val db: MangaDatabase,
	private val discoveryService: TrackingSiteDiscoveryService,
	private val cacheRepository: TrackingSiteCacheRepository,
	private val scrobblers: Set<@JvmSuppressWildcards Scrobbler>,
) : BaseViewModel() {

	private val service = savedStateHandle.get<Int>(AppRouter.KEY_ID)
		?.let { serviceId -> ScrobblerService.entries.firstOrNull { it.id == serviceId } }
		?: error("Missing tracking service argument")

	private val remoteId = savedStateHandle.get<Long>(AppRouter.KEY_REMOTE_ID)
		?: error("Missing tracking remote id argument")

	private val urlHint = savedStateHandle.get<String>(AppRouter.KEY_URL)

	private val _details = kotlinx.coroutines.flow.MutableStateFlow<TrackingSiteItemDetails?>(null)
	private val _error = kotlinx.coroutines.flow.MutableStateFlow<Throwable?>(null)

	val details = _details
	val error = _error
	private val trackingLinks = db.getTrackingSiteDao().observeLinks(service.id, remoteId)
	private val scrobblingEntities = db.getScrobblingDao().observeAllByTargetId(service.id, remoteId)
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

	private val selectedScrobblingEntity = combine(
		trackingLinks,
		scrobblingEntities,
	) { links, entities ->
		val linkedMangaIds = links.map { it.mangaId }.toSet()
		when {
			linkedMangaIds.isNotEmpty() -> entities.firstOrNull { it.mangaId in linkedMangaIds }
			else -> entities.firstOrNull { it.mangaId != 0L } ?: entities.firstOrNull()
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	val linkedContent = combine(
		trackingLinks,
		selectedScrobblingEntity,
	) { links, scrobbling ->
		links.firstOrNull()?.mangaId ?: scrobbling?.mangaId
	}.mapLatest { mangaId ->
		mangaId?.let { db.getMangaDao().find(it)?.toContent() }
	}
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	/**
	 * Observe scrobbling entity for the current tracking item.
	 * This provides the rating, status, chapter, and comment data.
	 */
	val scrobblingEntity = selectedScrobblingEntity

	private val scrobbler: Scrobbler?
		get() = scrobblers.find { it.scrobblerService == service }

	init {
		launchJob {
			runCatching { cacheRepository.readDetails(service, remoteId) }
				.getOrNull()
				?.let { cached ->
				if (_details.value == null) {
					_details.value = cached
				}
			}
		}
		refresh()
	}

	fun refresh() {
		launchLoadingJob {
			_error.value = null
			val cached = _details.value != null
			val details = runCatching {
				discoveryService.getDetails(service, remoteId, urlHint)
			}.getOrElse { error ->
				if (!cached) {
					_error.value = error
				}
				return@launchLoadingJob
			}
			runCatching { cacheRepository.saveDetails(details) }
			_details.value = details
		}
	}

	/**
	 * Update the scrobbling rating and status for this tracking item.
	 */
	fun updateScrobbling(rating: Float, status: ScrobblingStatus?) {
		val entity = scrobblingEntity.value ?: return
		val s = scrobbler ?: return
		launchJob(Dispatchers.Default) {
			s.updateScrobblingInfo(
				mangaId = entity.mangaId,
				rating = rating,
				status = status,
				comment = entity.comment,
			)
		}
	}

	/**
	 * Unregister scrobbling for this tracking item.
	 */
	fun unregisterScrobbling() {
		val entity = scrobblingEntity.value ?: return
		val s = scrobbler ?: return
		launchJob(Dispatchers.Default) {
			s.unregisterScrobbling(entity.mangaId)
		}
	}

	/**
	 * Resolve ScrobblingStatus from the entity's status string.
	 */
	fun resolveScrobblingStatus(entity: ScrobblingEntity): ScrobblingStatus? {
		val s = scrobbler ?: return null
		return s.resolveStatus(entity.status)
	}

	fun getService(): ScrobblerService = service

	fun getLinkedContent(): Content? = linkedContent.value
}
