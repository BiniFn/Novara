package org.skepsun.kototoro.discover.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.discover.ui.model.DiscoverItem
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.list.ui.model.toErrorState
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.tracking.discovery.data.TrackingSiteCacheRepository
import org.skepsun.kototoro.tracking.discovery.domain.PreferredTrackingSiteProvider
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteCatalog
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteDiscoveryService
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItem
import javax.inject.Inject

@HiltViewModel
class DiscoverViewModel @Inject constructor(
	preferredTrackingSiteProvider: PreferredTrackingSiteProvider,
	private val discoveryService: TrackingSiteDiscoveryService,
	private val cacheRepository: TrackingSiteCacheRepository,
) : BaseViewModel() {

	private val refreshTrigger = MutableStateFlow(0)
	private val searchQuery = MutableStateFlow("")
	private val selectedServiceOverride = MutableStateFlow<ScrobblerService?>(null)

	private val preferredService = preferredTrackingSiteProvider.preferredSite
		.stateIn(
			viewModelScope + Dispatchers.Default,
			SharingStarted.Eagerly,
			ScrobblerService.BANGUMI,
		)

	val availableServices = preferredService
		.map(::resolveAvailableServices)
		.stateIn(
			viewModelScope + Dispatchers.Default,
			SharingStarted.Eagerly,
			resolveAvailableServices(ScrobblerService.BANGUMI),
		)

	val query: StateFlow<String> = searchQuery.asStateFlow()

	val activeService = combine(
		preferredService,
		availableServices,
		selectedServiceOverride,
		searchQuery,
	) { preferredService, availableServices, selectedService, query ->
		resolveActiveService(preferredService, availableServices, selectedService, query)
	}
		.stateIn(
			viewModelScope + Dispatchers.Default,
			SharingStarted.Eagerly,
			ScrobblerService.BANGUMI,
		)

	val content = combine(activeService, refreshTrigger, searchQuery) { service, _, query ->
		service to query.trim()
	}
		.flatMapLatest { (service, query) ->
			flow<List<ListModel>> {
				val cachedItems = if (query.isBlank()) {
					runCatching { cacheRepository.readTrending(service) }.getOrElse { emptyList() }
				} else {
					emptyList()
				}
				if (cachedItems.isNotEmpty()) {
					emit(cachedItems.toDiscoverModels())
				}

				val items = runCatching {
					val catalog = TrackingSiteCatalog(
						service = service,
						query = query.ifEmpty { null },
					)
					if (query.isEmpty()) {
						discoveryService.getTrending(catalog)
					} else {
						discoveryService.search(catalog)
					}
				}.getOrElse { error ->
					if (cachedItems.isEmpty()) {
						emit(listOf(error.toErrorState(canRetry = true)))
					}
					return@flow
				}

				if (query.isBlank()) {
					runCatching { cacheRepository.saveTrending(items) }
				}
				if (items.isEmpty()) {
					if (cachedItems.isEmpty()) {
						emit(
							listOf(
								EmptyState(
									icon = R.drawable.ic_bangumi_outline,
									textPrimary = if (query.isEmpty()) {
										R.string.discover_empty_title
									} else {
										R.string.discover_search_empty_title
									},
									textSecondary = if (query.isEmpty()) {
										R.string.discover_empty_text
									} else {
										R.string.discover_search_empty_text
									},
									actionStringRes = 0,
								),
							),
						)
					}
				} else {
					emit(items.toDiscoverModels())
				}
			}.withLoading()
		}
		.stateIn(
			viewModelScope + Dispatchers.Default,
			SharingStarted.Eagerly,
			listOf(LoadingState),
		)

	fun refresh() {
		refreshTrigger.value += 1
	}

	fun submitQuery(query: String) {
		searchQuery.value = query.trim()
	}

	fun clearQuery() {
		searchQuery.value = ""
	}

	fun selectService(service: ScrobblerService) {
		if (!isServiceSelectable(service)) {
			return
		}
		selectedServiceOverride.value = service
	}

	fun isServiceSelectable(service: ScrobblerService): Boolean {
		return if (searchQuery.value.isBlank()) {
			supportsTrending(service)
		} else {
			supportsSearch(service)
		}
	}

	fun supportsTrending(service: ScrobblerService): Boolean {
		return discoveryService.getCapabilities(service).supportsTrending
	}

	fun supportsSearch(service: ScrobblerService): Boolean {
		return discoveryService.getCapabilities(service).supportsSearch
	}

	fun supportsDetails(service: ScrobblerService): Boolean {
		return discoveryService.getCapabilities(service).supportsDetails
	}

	private fun resolveAvailableServices(preferred: ScrobblerService): List<ScrobblerService> {
		return buildList {
			add(ScrobblerService.BANGUMI)
			if (preferred != ScrobblerService.BANGUMI && supportsSearch(preferred)) {
				add(preferred)
			}
		}
	}

	private fun resolveActiveService(
		preferred: ScrobblerService,
		availableServices: List<ScrobblerService>,
		selected: ScrobblerService?,
		query: String,
	): ScrobblerService {
		if (selected != null && selected in availableServices && canUseService(selected, query)) {
			return selected
		}
		return if (canUseService(preferred, query)) preferred else ScrobblerService.BANGUMI
	}

	private fun canUseService(service: ScrobblerService, query: String): Boolean {
		return if (query.isBlank()) {
			supportsTrending(service)
		} else {
			supportsSearch(service)
		}
	}

	private fun List<TrackingSiteItem>.toDiscoverModels(): List<ListModel> {
		return mapTo(ArrayList<ListModel>(size)) { DiscoverItem(it) }
	}
}
