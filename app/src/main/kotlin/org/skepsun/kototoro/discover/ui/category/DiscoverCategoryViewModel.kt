package org.skepsun.kototoro.discover.ui.category

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.skepsun.kototoro.core.model.ContentSource
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.list.domain.ContentListMapper
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.list.ui.model.toErrorState
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteCatalog
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteDiscoveryService
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItem
import org.skepsun.kototoro.tracking.discovery.data.TrackingSiteCacheRepository
import javax.inject.Inject
import org.skepsun.kototoro.R

@HiltViewModel
class DiscoverCategoryViewModel @Inject constructor(
	private val discoveryService: TrackingSiteDiscoveryService,
	private val contentListMapper: ContentListMapper,
	private val appSettings: AppSettings,
	private val cacheRepository: TrackingSiteCacheRepository,
	val filterCoordinator: org.skepsun.kototoro.filter.ui.FilterCoordinator,
) : BaseViewModel() {

	private val _items = MutableStateFlow<List<TrackingSiteItem>>(emptyList())
	private val _page = MutableStateFlow(0)
	private val _contentState = MutableStateFlow<List<ListModel>>(listOf(LoadingState))

	val content: StateFlow<List<ListModel>> = _contentState.asStateFlow()

	private var isPageLoading = false
	private var currentService: ScrobblerService? = null
	private var currentCategory: String? = null
	private var isFilterObserverPrimed = false

	fun initialize(serviceName: String, categoryId: String) {
		val service = ScrobblerService.entries.find { it.name == serviceName } ?: return
		if (currentService == service && currentCategory == categoryId) return
		currentService = service
		currentCategory = categoryId
		// Show cached items instantly
		val cached = cacheRepository.readCategoryCache(service, categoryId)
		if (cached != null && cached.isNotEmpty()) {
			_items.value = cached
			viewModelScope.launch {
				_contentState.value = cached.toDiscoverModels()
			}
		} else {
			refresh()
		}
	}

	init {
		viewModelScope.launch {
			filterCoordinator.observe()
				.distinctUntilChanged()
				.collect {
					if (!isFilterObserverPrimed) {
						isFilterObserverPrimed = true
						return@collect
					}
					if (currentService != null) {
						refresh()
					}
				}
		}
	}

    fun applyDayFilter(day: Int) {
        val service = currentService ?: return
        currentCategory = "calendar_$day"
        refresh()
    }

	fun refresh() {
		val service = currentService ?: return
		val category = currentCategory ?: return
		cacheRepository.clearCategoryCache(service, category)
		_items.value = emptyList()
		_page.value = 0
		viewModelScope.launch {
			loadingCounter.increment()
			try {
				loadData(service, category, 0)
			} finally {
				loadingCounter.decrement()
			}
		}
	}

	fun loadNextPage() {
		if (isPageLoading) return
		val service = currentService ?: return
		val category = currentCategory ?: return
		val nextPage = _page.value + 1
		viewModelScope.launch {
			isPageLoading = true
			try {
				loadData(service, category, nextPage)
			} finally {
				isPageLoading = false
			}
		}
	}



	private suspend fun loadData(service: ScrobblerService, category: String, pageRequested: Int) {
		val isFirstPage = pageRequested == 0
		if (isFirstPage) {
			_contentState.value = listOf(LoadingState)
		}

		val filterSnapshot = filterCoordinator.snapshot()
		val result = runCatching {
			discoveryService.getTrending(
				TrackingSiteCatalog(
					service = service,
					category = category,
					page = pageRequested,
					sortOrder = filterSnapshot.sortOrder,
					listFilter = filterSnapshot.listFilter,
				)
			)
		}

		result.onSuccess { newItems ->
			if (newItems.isEmpty() && isFirstPage) {
				_contentState.value = listOf(EmptyState(icon = R.drawable.ic_bangumi_outline, textPrimary = R.string.discover_empty_title, textSecondary = R.string.discover_empty_text, actionStringRes = 0))
				return@onSuccess
			}
			val updatedItems = if (isFirstPage) newItems else _items.value + newItems
			val hasMore = newItems.isNotEmpty()

			_items.value = updatedItems.distinctBy { it.remoteId }
			val models = _items.value.toDiscoverModels()
			_contentState.value = if (hasMore) models + listOf(LoadingState) else models
			_page.value = pageRequested
			// Save to cache
			cacheRepository.saveCategoryCache(service, category, _items.value)

		}.onFailure { error ->
			val models = _items.value.toDiscoverModels()
			if (models.isEmpty()) {
				_contentState.value = listOf(error.toErrorState(canRetry = true))
			} else {
				_contentState.value = models
			}
		}
	}

	fun supportsDetails(serviceName: String): Boolean {
		val service = ScrobblerService.entries.find { it.name == serviceName } ?: return false
		return discoveryService.getCapabilities(service).supportsDetails
	}

	private suspend fun List<TrackingSiteItem>.toDiscoverModels(): List<ListModel> {
		val proxyContents = this.map { item ->
			Content(
				id = item.remoteId,
				title = item.title,
				altTitle = item.subtitle ?: item.altTitle,
				url = item.url ?: "",
				publicUrl = item.url ?: "",
				rating = item.score ?: 0f,
				isNsfw = false,
				coverUrl = item.coverUrl,
				tags = emptySet(),
				state = org.skepsun.kototoro.parsers.model.ContentState.ONGOING,
				author = null,
				source = ContentSource("TRACKING_${item.service.name}"),
			)
		}
		return contentListMapper.toListModelList(proxyContents, appSettings.listMode)
	}
}
