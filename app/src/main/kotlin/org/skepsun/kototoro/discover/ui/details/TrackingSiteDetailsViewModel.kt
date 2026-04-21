package org.skepsun.kototoro.discover.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.entity.toContent
import org.skepsun.kototoro.core.db.entity.toEntity
import org.skepsun.kototoro.core.model.ContentSourceInfo
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.BaseViewModel
import org.skepsun.kototoro.core.util.ext.awaitCancellable
import org.skepsun.kototoro.details.data.CachedTranslationEntry
import org.skepsun.kototoro.details.data.DetailsTranslationCache
import org.skepsun.kototoro.details.ui.model.LinkedTrackingItemUiModel
import org.skepsun.kototoro.explore.data.ContentSourcesRepository
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.scrobbling.common.data.ScrobblingEntity
import org.skepsun.kototoro.scrobbling.common.domain.Scrobbler
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblingStatus
import org.skepsun.kototoro.search.domain.SearchKind
import org.skepsun.kototoro.search.domain.SearchV2Helper
import org.skepsun.kototoro.tracking.discovery.data.TrackingSiteCacheRepository
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteDiscoveryService
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails
import org.skepsun.kototoro.tracking.malsync.data.MALSyncMappingRepository
import javax.inject.Inject

@HiltViewModel
class TrackingSiteDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val db: MangaDatabase,
    private val discoveryService: TrackingSiteDiscoveryService,
    private val cacheRepository: TrackingSiteCacheRepository,
    private val scrobblers: Set<@JvmSuppressWildcards Scrobbler>,
    private val detailsTranslationCache: DetailsTranslationCache,
    private val sourcesRepository: ContentSourcesRepository,
    private val searchV2HelperFactory: SearchV2Helper.Factory,
    private val malSyncRepository: MALSyncMappingRepository,
    private val settings: AppSettings,
) : BaseViewModel() {

    private val service = savedStateHandle.get<Int>(AppRouter.KEY_ID)
        ?.let { serviceId -> ScrobblerService.entries.firstOrNull { it.id == serviceId } }
        ?: error("Missing tracking service argument")

    private val remoteId = savedStateHandle.get<Long>(AppRouter.KEY_REMOTE_ID)
        ?: error("Missing tracking remote id argument")

    private val urlHint = savedStateHandle.get<String>(AppRouter.KEY_URL)

    private val _details = MutableStateFlow<TrackingSiteItemDetails?>(null)
    private val _error = MutableStateFlow<Throwable?>(null)

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
    }.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

    val scrobblingEntity = selectedScrobblingEntity

    val linkedTrackingItem = combine(
        details,
        scrobblingEntity,
    ) { d, entity ->
        val info = d ?: return@combine null
        LinkedTrackingItemUiModel(
            service = info.service,
            remoteId = info.remoteId,
            title = info.title,
            coverUrl = info.coverUrl,
            summary = info.description,
            url = info.url,
            status = entity?.let(::resolveScrobblingStatus),
            rating = entity?.rating,
            isPreferred = false,
        )
    }.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

    // --- Translation ---

    val cachedTranslatedTitle = MutableStateFlow<String?>(null)
    val cachedTranslatedDescription = MutableStateFlow<String?>(null)
    val isShowingTranslation = MutableStateFlow(false)
    val isTranslating = MutableStateFlow(false)

    val hasTranslationCache: StateFlow<Boolean> = combine(
        cachedTranslatedTitle,
        cachedTranslatedDescription,
    ) { title, desc ->
        !title.isNullOrBlank() || !desc.isNullOrBlank()
    }.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, false)

    val translatedTitle: StateFlow<String?> = combine(
        isShowingTranslation,
        cachedTranslatedTitle,
    ) { showing, cached ->
        if (showing) cached else null
    }.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

    val translatedDescription: StateFlow<String?> = combine(
        isShowingTranslation,
        cachedTranslatedDescription,
    ) { showing, cached ->
        if (showing) cached else null
    }.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

    private var translationCacheSourceLang: String? = null
    private var translationCacheTargetLang: String? = null

    // --- Local source search (tabs) ---

    val availableLocalSources: StateFlow<List<ContentSourceInfo>> =
        sourcesRepository.observeEnabledSources()
            .stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

    val selectedLocalSourceName = MutableStateFlow<String?>(null)
    val isLocalSearchVisible = MutableStateFlow(false)
    val isTrackerSearchVisible = MutableStateFlow(false)

    private val _localSearchResults = MutableStateFlow<Map<String, LocalSearchState>>(emptyMap())
    val localSearchResults: StateFlow<Map<String, LocalSearchState>> = _localSearchResults

    private val searchSemaphore = Semaphore(MAX_PARALLEL_SEARCH)
    private val ongoingSearches = mutableSetOf<String>()

    // --- MALSync alternative trackers ---

    private val _alternativeTrackers = MutableStateFlow<List<MALSyncMappingRepository.Mapping>>(emptyList())
    val alternativeTrackers: StateFlow<List<MALSyncMappingRepository.Mapping>> = _alternativeTrackers
    val isAlternativeTrackersLoading = MutableStateFlow(false)
    private var alternativeTrackersLoaded = false

    private val scrobbler: Scrobbler?
        get() = scrobblers.find { it.scrobblerService == service }

    init {
        launchJob {
            val cached = runCatching { cacheRepository.readDetails(service, remoteId) }
                .getOrNull()
            cached?.let { value ->
                if (_details.value == null) {
                    _details.value = value
                }
            }
            if (cached == null) {
                refresh()
            }
        }
        observeDetailsForTranslation()
    }

    private fun observeDetailsForTranslation() {
        viewModelScope.launch(Dispatchers.Default) {
            details.collect { d ->
                if (d != null) {
                    restorePersistedTranslation(d)
                }
            }
        }
    }

    fun refresh() {
        launchLoadingJob {
            _error.value = null
            val cached = _details.value != null
            val d = runCatching {
                discoveryService.getDetails(service, remoteId, urlHint)
            }.getOrElse { error ->
                if (!cached) {
                    _error.value = error
                }
                return@launchLoadingJob
            }
            runCatching { cacheRepository.saveDetails(d) }
            _details.value = d
        }
    }

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

    fun unregisterScrobbling() {
        val entity = scrobblingEntity.value ?: return
        val s = scrobbler ?: return
        launchJob(Dispatchers.Default) {
            s.unregisterScrobbling(entity.mangaId)
        }
    }

    fun resolveScrobblingStatus(entity: ScrobblingEntity): ScrobblingStatus? {
        val s = scrobbler ?: return null
        return s.resolveStatus(entity.status)
    }

    fun getService(): ScrobblerService = service
    fun getRemoteId(): Long = remoteId

    fun getLinkedContent(): Content? = linkedContent.value

    // --- Translation ---

    fun translateTitleAndDescription(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!forceRefresh && hasTranslationCache.value) {
                isShowingTranslation.value = true
                persistCurrentTranslationState()
                return@launch
            }
            val d = details.value ?: return@launch
            val title = d.title
            val description = d.description.orEmpty()
            if (title.isBlank()) return@launch

            isTranslating.value = true
            try {
                val targetLang = currentTargetLang()
                val sourceLang = currentSourceLang()
                val translatedTitleText = translateViaMlKit(title, sourceLang, targetLang)
                val nextTranslatedTitle = translatedTitleText.takeIf { it.isNotBlank() && it != title }
                var nextTranslatedDescription: String? = null
                if (description.isNotBlank()) {
                    val translatedDescText = translateViaMlKit(description, sourceLang, targetLang)
                    nextTranslatedDescription = translatedDescText.takeIf { it.isNotBlank() && it != description }
                }
                if (nextTranslatedTitle != null || nextTranslatedDescription != null) {
                    cachedTranslatedTitle.value = nextTranslatedTitle
                    cachedTranslatedDescription.value = nextTranslatedDescription
                    isShowingTranslation.value = true
                    translationCacheSourceLang = sourceLang
                    translationCacheTargetLang = targetLang
                    persistCurrentTranslationState()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("TrackingDetailsVM", "Translation failed", e)
            } finally {
                isTranslating.value = false
            }
        }
    }

    fun toggleTranslationDisplay() {
        if (!hasTranslationCache.value) return
        isShowingTranslation.value = !isShowingTranslation.value
        persistCurrentTranslationState()
    }

    private suspend fun translateViaMlKit(text: String, sourceLang: String, targetLang: String): String {
        val resolvedSource = if (sourceLang.trim().lowercase() == "auto") {
            detectLanguageViaMlKit(text) ?: "en"
        } else {
            sourceLang
        }
        val mlSource = resolveMlKitLang(resolvedSource) ?: return ""
        val mlTarget = resolveMlKitLang(targetLang) ?: return ""
        val options = com.google.mlkit.nl.translate.TranslatorOptions.Builder()
            .setSourceLanguage(mlSource)
            .setTargetLanguage(mlTarget)
            .build()
        val translator = com.google.mlkit.nl.translate.Translation.getClient(options)
        return try {
            withTimeout(60_000) {
                translator.downloadModelIfNeeded().awaitCancellable()
            }
            withTimeout(15_000) {
                translator.translate(text).awaitCancellable()
            }
        } finally {
            translator.close()
        }
    }

    private suspend fun detectLanguageViaMlKit(text: String): String? {
        return try {
            val identifier = com.google.mlkit.nl.languageid.LanguageIdentification.getClient()
            val lang = withTimeout(5_000) {
                identifier.identifyLanguage(text).awaitCancellable()
            }
            if (lang == "und") null else lang
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveMlKitLang(lang: String): String? {
        val normalized = lang.trim().lowercase().replace("-", "_")
        return com.google.mlkit.nl.translate.TranslateLanguage.fromLanguageTag(normalized)
    }

    private fun restorePersistedTranslation(details: TrackingSiteItemDetails) {
        val content = linkedContent.value ?: return
        val sourceLang = currentSourceLang()
        val targetLang = currentTargetLang()
        val originalTitle = details.title
        val originalDescription = details.description.orEmpty()
        val restored = detailsTranslationCache.get(
            content = content,
            sourceLang = sourceLang,
            targetLang = targetLang,
            originalTitle = originalTitle,
            originalDescription = originalDescription,
        )
        if (restored == null) {
            cachedTranslatedTitle.value = null
            cachedTranslatedDescription.value = null
            isShowingTranslation.value = false
            return
        }
        cachedTranslatedTitle.value = restored.translatedTitle
        cachedTranslatedDescription.value = restored.translatedDescription
        isShowingTranslation.value = restored.isShowingTranslation
        translationCacheSourceLang = sourceLang
        translationCacheTargetLang = targetLang
    }

    private fun persistCurrentTranslationState() {
        val sourceLang = translationCacheSourceLang ?: return
        val targetLang = translationCacheTargetLang ?: return
        val d = details.value ?: return
        val content = linkedContent.value ?: return
        if (!hasTranslationCache.value) return
        detailsTranslationCache.put(
            content = content,
            sourceLang = sourceLang,
            targetLang = targetLang,
            entry = CachedTranslationEntry(
                originalTitle = d.title,
                translatedTitle = cachedTranslatedTitle.value,
                originalDescription = d.description.orEmpty(),
                translatedDescription = cachedTranslatedDescription.value,
                isShowingTranslation = isShowingTranslation.value,
            ),
        )
    }

    private fun currentSourceLang(): String =
        settings.readerTranslationSourceLanguage.ifBlank { "auto" }

    private fun currentTargetLang(): String =
        settings.readerTranslationTargetLanguage.ifBlank { "zh" }

    // --- Local source search ---

    fun setLocalSearchVisible(visible: Boolean) {
        isLocalSearchVisible.value = visible
        if (visible) {
            ensureLocalSourceSelection()
        }
    }

    fun selectLocalSource(sourceName: String) {
        selectedLocalSourceName.value = sourceName
        ensureLocalSearch(sourceName)
    }

    private fun ensureLocalSourceSelection() {
        if (selectedLocalSourceName.value != null) {
            selectedLocalSourceName.value?.let(::ensureLocalSearch)
            return
        }
        val first = availableLocalSources.value.firstOrNull() ?: return
        selectedLocalSourceName.value = first.mangaSource.name
        ensureLocalSearch(first.mangaSource.name)
    }

    fun ensureLocalSearch(sourceName: String) {
        val query = details.value?.title.orEmpty().takeIf { it.isNotBlank() } ?: return
        val existing = _localSearchResults.value[sourceName]
        if (existing is LocalSearchState.Loaded || existing is LocalSearchState.Loading) return
        if (sourceName in ongoingSearches) return
        val sourceInfo = availableLocalSources.value.firstOrNull { it.mangaSource.name == sourceName }
            ?: return
        ongoingSearches.add(sourceName)
        _localSearchResults.value = _localSearchResults.value + (sourceName to LocalSearchState.Loading)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                searchSemaphore.withPermit {
                    val helper = searchV2HelperFactory.create(sourceInfo.mangaSource)
                    val results = runCatching { helper.invoke(query, SearchKind.TITLE) }
                        .getOrNull()
                    val next = if (results == null) {
                        LocalSearchState.Loaded(emptyList())
                    } else {
                        LocalSearchState.Loaded(results.manga.take(12))
                    }
                    _localSearchResults.value = _localSearchResults.value + (sourceName to next)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _localSearchResults.value = _localSearchResults.value + (sourceName to LocalSearchState.Error(e))
            } finally {
                ongoingSearches.remove(sourceName)
            }
        }
    }

    fun retryLocalSearch(sourceName: String) {
        _localSearchResults.value = _localSearchResults.value - sourceName
        ensureLocalSearch(sourceName)
    }

    suspend fun bindLocalContent(content: Content) {
        val existing = db.getMangaDao().find(content.id)
        val mangaId = if (existing != null) {
            content.id
        } else {
            db.getMangaDao().upsert(content.toEntity(), content.tags.map { it.toEntity() })
            content.id
        }
        val now = System.currentTimeMillis()
        db.getTrackingSiteDao().upsertLink(
            org.skepsun.kototoro.core.db.entity.TrackingSiteLinkEntity(
                service = service.id,
                remoteId = remoteId,
                mangaId = mangaId,
                sourceName = content.source.name,
                confidence = 0.8f,
                isManual = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    // --- MALSync ---

    fun setTrackerSearchVisible(visible: Boolean) {
        isTrackerSearchVisible.value = visible
        if (visible && !alternativeTrackersLoaded) {
            loadAlternativeTrackers()
        }
    }

    fun loadAlternativeTrackers() {
        if (isAlternativeTrackersLoading.value) return
        isAlternativeTrackersLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mappings = malSyncRepository.resolve(service, remoteId, MALSyncMappingRepository.Kind.MANGA)
                _alternativeTrackers.value = mappings
                alternativeTrackersLoaded = true
            } finally {
                isAlternativeTrackersLoading.value = false
            }
        }
    }

    private companion object {
        const val MAX_PARALLEL_SEARCH = 4
    }
}

sealed class LocalSearchState {
    object Loading : LocalSearchState()
    data class Loaded(val items: List<Content>) : LocalSearchState()
    data class Error(val throwable: Throwable) : LocalSearchState()
}
