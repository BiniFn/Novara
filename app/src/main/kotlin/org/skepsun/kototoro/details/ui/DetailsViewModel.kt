package org.skepsun.kototoro.details.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.details.ui.model.toListItem
import org.skepsun.kototoro.details.ui.model.LinkedTrackingItemUiModel
import org.skepsun.kototoro.bookmarks.domain.BookmarksRepository
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.core.model.getPreferredBranch
import org.skepsun.kototoro.core.nav.ContentIntent
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.prefs.TriStateOption
import org.skepsun.kototoro.core.ui.util.ReversibleAction
import org.skepsun.kototoro.core.util.ext.awaitCancellable
import org.skepsun.kototoro.core.util.ext.call
import org.skepsun.kototoro.core.util.ext.computeSize
import org.skepsun.kototoro.core.util.ext.onEachWhile
import org.skepsun.kototoro.details.data.CachedTranslationEntry
import org.skepsun.kototoro.details.data.ContentDetails
import org.skepsun.kototoro.details.data.DetailsTranslationCache
import org.skepsun.kototoro.details.domain.BranchComparator
import org.skepsun.kototoro.details.domain.DetailsInteractor
import org.skepsun.kototoro.details.domain.DetailsLoadUseCase
import org.skepsun.kototoro.details.domain.ProgressUpdateUseCase
import org.skepsun.kototoro.details.domain.ReadingTimeUseCase
import org.skepsun.kototoro.details.domain.RelatedContentUseCase
import org.skepsun.kototoro.details.ui.model.HistoryInfo
import org.skepsun.kototoro.details.ui.model.ContentBranch
import org.skepsun.kototoro.details.ui.model.ChapterListItem.Companion.FLAG_DOWNLOADED
import org.skepsun.kototoro.details.ui.pager.ChaptersPagesViewModel
import org.skepsun.kototoro.download.ui.worker.DownloadWorker
import org.skepsun.kototoro.history.data.HistoryRepository
import org.skepsun.kototoro.list.domain.ContentListMapper
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.local.data.LocalStorageChanges
import org.skepsun.kototoro.local.domain.DeleteLocalContentUseCase
import org.skepsun.kototoro.local.domain.model.LocalContent
import org.skepsun.kototoro.favourites.domain.FavouritesRepository
import org.skepsun.kototoro.core.model.FavouriteCategory
import org.skepsun.kototoro.core.model.ids
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.util.findById
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import org.skepsun.kototoro.reader.ui.ReaderState
import org.skepsun.kototoro.scrobbling.common.domain.Scrobbler
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblingInfo
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblingStatus
import org.skepsun.kototoro.stats.data.StatsRepository
import org.skepsun.kototoro.video.data.VideoDownloadIndex
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatcher
import javax.inject.Inject
import kotlin.experimental.or
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.entitygraph.ui.details.EntityRelationSection
import org.skepsun.kototoro.entitygraph.ui.details.EntityRelationItem
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService

@HiltViewModel
class DetailsViewModel @Inject constructor(
	private val historyRepository: HistoryRepository,
	bookmarksRepository: BookmarksRepository,
	settings: AppSettings,
	private val scrobblers: Set<@JvmSuppressWildcards Scrobbler>,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalContent?>,
	downloadScheduler: DownloadWorker.Scheduler,
	interactor: DetailsInteractor,
	savedStateHandle: SavedStateHandle,
	deleteLocalContentUseCase: DeleteLocalContentUseCase,
	private val relatedContentUseCase: RelatedContentUseCase,
	private val mangaListMapper: ContentListMapper,
	private val detailsLoadUseCase: DetailsLoadUseCase,
	private val progressUpdateUseCase: ProgressUpdateUseCase,
	private val readingTimeUseCase: ReadingTimeUseCase,
	statsRepository: StatsRepository,
	private val epubChapterMappingDao: org.skepsun.kototoro.core.db.dao.EpubChapterMappingDao,
	private val localEpubSource: org.skepsun.kototoro.local.epub.LocalEpubSource,
	private val epubStorageManager: org.skepsun.kototoro.local.epub.EpubStorageManager,
	private val videoDownloadIndex: VideoDownloadIndex,
	private val favouritesRepository: FavouritesRepository,
	mangaRepositoryFactory: org.skepsun.kototoro.core.parser.ContentRepository.Factory,
	private val trackingSiteMatcher: TrackingSiteMatcher,
	private val dataRepository: org.skepsun.kototoro.core.parser.ContentDataRepository,
	private val detailsTranslationCache: DetailsTranslationCache,
	private val db: org.skepsun.kototoro.core.db.MangaDatabase,
	private val trackingSiteCacheRepository: org.skepsun.kototoro.tracking.discovery.data.TrackingSiteCacheRepository,
	private val entityGraphRepository: org.skepsun.kototoro.entitygraph.data.EntityGraphRepository,
	private val trackingSiteDiscoveryService: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteDiscoveryService,
) : ChaptersPagesViewModel(
	settings = settings,
	interactor = interactor,
	bookmarksRepository = bookmarksRepository,
	historyRepository = historyRepository,
	downloadScheduler = downloadScheduler,
	deleteLocalContentUseCase = deleteLocalContentUseCase,
	mangaRepositoryFactory = mangaRepositoryFactory,
	localStorageChanges = localStorageChanges,
) {

	private val intent = ContentIntent(savedStateHandle)
	private var loadingJob: Job
	private var translationCacheSourceLang: String? = null
	private var translationCacheTargetLang: String? = null
	val activeExternalOrigin = savedStateHandle.get<org.skepsun.kototoro.details.ui.model.DetailsOrigin>(org.skepsun.kototoro.core.nav.AppRouter.KEY_DETAILS_ORIGIN)
	private val activeMangaIdFlow = kotlinx.coroutines.flow.MutableStateFlow(intent.mangaId.takeIf { it != 0L })
	val mangaId: Long get() = activeMangaIdFlow.value ?: intent.mangaId

	val entityRelationSections = MutableStateFlow<List<EntityRelationSection>>(emptyList())

	init {
		// Apply instant first paint from Intent or DetailsOrigin
		val originContent = (activeExternalOrigin as? org.skepsun.kototoro.details.ui.model.DetailsOrigin.LocalMangaContent)?.parcelableContent?.manga
		mangaDetails.value = (originContent ?: intent.manga)?.let { ContentDetails(it) }

		if (activeExternalOrigin is org.skepsun.kototoro.details.ui.model.DetailsOrigin.EntityGraph) {
			launchJob(Dispatchers.IO) {
				val graphGraphId = activeExternalOrigin.entityId
				val entity = entityGraphRepository.getEntity(graphGraphId) ?: return@launchJob
				
				if (mangaDetails.value == null) {
				    val syntheticContent = Content(
				        id = graphGraphId,
				        title = entity.primaryName,
				        altTitle = null, url = "", publicUrl = "", rating = 0f, isNsfw = false, tags = emptySet(), state = null, author = null,
				        coverUrl = "",
				        description = "",
				        source = object : org.skepsun.kototoro.parsers.model.ContentSource { override val name = "Kototoro Entity" ; override val locale = "" ; override val contentType = org.skepsun.kototoro.parsers.model.ContentType.MANGA }
				    )
				    mangaDetails.value = ContentDetails(syntheticContent)
				}
				
				val boundLocalId = entityGraphRepository.getBindings(graphGraphId).firstOrNull { it.source == "0" || it.source == "local_manga" }?.externalId?.toLongOrNull()
				if (boundLocalId != null) {
				    activeMangaIdFlow.value = boundLocalId
				    loadingJob = doLoad(force = false) // Trigger reload locally!
				}
				
				// Fetch Relations
				val relations = entityGraphRepository.getRelations(graphGraphId)
				val relatedIds = relations.mapNotNull { it.toEntityId.takeIf { t -> t != graphGraphId } ?: it.fromEntityId.takeIf { f -> f != graphGraphId } }
				val relatedEntities = entityGraphRepository.getEntitiesByIds(relatedIds).associateBy { it.id }
				val mappedSections = relations.groupBy { it.type }.mapNotNull { (type, typeRelations) ->
				    val items = typeRelations.mapNotNull { relation ->
				        val relatedId = relation.toEntityId.takeIf { it != graphGraphId } ?: relation.fromEntityId.takeIf { it != graphGraphId } ?: return@mapNotNull null
				        relatedEntities[relatedId]?.let { related ->
				            EntityRelationItem(
				                entityId = related.id,
				                name = related.primaryName,
				                type = related.type
				            )
				        }
				    }
                    val typeTitleRes = when (type) {
                        org.skepsun.kototoro.entitygraph.domain.RelationType.HAS_CHARACTER -> org.skepsun.kototoro.R.string.entity_graph_section_characters
                        org.skepsun.kototoro.entitygraph.domain.RelationType.CREATED_BY -> org.skepsun.kototoro.R.string.entity_graph_section_creators
                        org.skepsun.kototoro.entitygraph.domain.RelationType.RELATED_TO -> org.skepsun.kototoro.R.string.entity_graph_section_related_entities
                        org.skepsun.kototoro.entitygraph.domain.RelationType.VOICED_BY -> org.skepsun.kototoro.R.string.entity_graph_section_voice_actors
                        org.skepsun.kototoro.entitygraph.domain.RelationType.BELONGS_TO -> org.skepsun.kototoro.R.string.entity_graph_section_parent_work
                    }
				    if (items.isEmpty()) null else EntityRelationSection(titleRes = typeTitleRes, items = items)
				}
				entityRelationSections.value = mappedSections

			}
		} else if (activeExternalOrigin is org.skepsun.kototoro.details.ui.model.DetailsOrigin.TrackingItem) {
			launchJob(Dispatchers.IO) {
			    val service = ScrobblerService.entries.firstOrNull { it.id == activeExternalOrigin.serviceId.toIntOrNull() } ?: return@launchJob
			    val cached = trackingSiteCacheRepository.readDetails(service, activeExternalOrigin.remoteId)
			    
			    if (mangaDetails.value == null && cached != null) {
			        val syntheticContent = Content(
				        id = activeExternalOrigin.remoteId,
				        title = cached.title ?: "",
				        altTitle = null, url = "", publicUrl = "", rating = 0f, isNsfw = false, tags = emptySet(), state = null, author = null,
				        coverUrl = cached.coverUrl,
				        description = cached.description,
				        source = object : org.skepsun.kototoro.parsers.model.ContentSource { override val name = "Kototoro Tracker" ; override val locale = "" ; override val contentType = org.skepsun.kototoro.parsers.model.ContentType.MANGA }
				    )
				    mangaDetails.value = ContentDetails(syntheticContent)
			    }
			    
			    val remoteDetails = try { trackingSiteDiscoveryService.getDetails(service, activeExternalOrigin.remoteId, activeExternalOrigin.url) } catch (e: Exception) { null }
			    if (mangaDetails.value == null && remoteDetails != null) {
			        val syntheticContent = Content(
				        id = activeExternalOrigin.remoteId,
				        title = remoteDetails.title ?: "",
				        altTitle = null, url = "", publicUrl = "", rating = 0f, isNsfw = false, tags = emptySet(), state = null, author = null,
				        coverUrl = remoteDetails.coverUrl,
				        description = remoteDetails.description?.toString() ?: "",
				        source = object : org.skepsun.kototoro.parsers.model.ContentSource { override val name = "Kototoro Tracker" ; override val locale = "" ; override val contentType = org.skepsun.kototoro.parsers.model.ContentType.MANGA }
				    )
				    mangaDetails.value = ContentDetails(syntheticContent)
			    }
			    
			    if (remoteDetails != null) {
			        trackingSiteCacheRepository.saveDetails(remoteDetails)
			    }
			    
			    // Bind local manga if tracked
			    db.getTrackingSiteDao().observeLinks(service.id, activeExternalOrigin.remoteId).collect { links ->
			        if (links.isNotEmpty() && activeMangaIdFlow.value == null) {
			            val trackingMangaId = links.first().mangaId
			            if (trackingMangaId != 0L) {
			                activeMangaIdFlow.value = trackingMangaId
			                loadingJob = doLoad(force = false)
			            }
			        }
			    }
			}
		}

		videoDownloadIndex.changes
			.onEach { changedContentId ->
				if (changedContentId == mangaId) {
					notifyDownloadChanged()
				}
			}
			.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, 0L)
	}

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	val history = activeMangaIdFlow.filterNotNull().flatMapLatest { historyRepository.observeOne(it) }
		.onEach { h ->
			readingState.value = h?.let(::ReaderState)
		}.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	val favouriteCategories = activeMangaIdFlow.filterNotNull().flatMapLatest { interactor.observeFavourite(it) }
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptySet())

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	val isStatsAvailable = activeMangaIdFlow.filterNotNull().flatMapLatest { statsRepository.observeHasStats(it) }
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, false)

	val isMarkedSafe = MutableStateFlow(false)

	val remoteContent = MutableStateFlow<Content?>(null)

	private val cachedTranslatedTitle = MutableStateFlow<String?>(null)
	private val cachedTranslatedDescription = MutableStateFlow<String?>(null)
	val isShowingTranslation = MutableStateFlow(false)
	val hasTranslationCache: StateFlow<Boolean> = combine(
		cachedTranslatedTitle,
		cachedTranslatedDescription,
	) { title, description ->
		title != null || description != null
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, false)
	val translatedTitle: StateFlow<String?> = combine(
		cachedTranslatedTitle,
		isShowingTranslation,
	) { title, isShowing ->
		title.takeIf { isShowing }
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)
	val translatedDescription: StateFlow<String?> = combine(
		cachedTranslatedDescription,
		isShowingTranslation,
	) { description, isShowing ->
		description.takeIf { isShowing }
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)
	val isTranslating = MutableStateFlow(false)

	val historyInfo: StateFlow<HistoryInfo> = combine(
		mangaDetails,
		selectedBranch,
		history,
		interactor.observeIncognitoMode(manga),
	) { m, b, h, im ->
		val estimatedTime = readingTimeUseCase.invoke(m, b, h)
		HistoryInfo(m, b, h, im == TriStateOption.ENABLED, estimatedTime)
	}.withErrorHandling()
		.stateIn(
			scope = viewModelScope + Dispatchers.Default,
			started = SharingStarted.Eagerly,
			initialValue = HistoryInfo(null, null, null, false, null),
		)

	val localSize = mangaDetails
		.map { it }  // 获取完整的ContentDetails
		.distinctUntilChanged()
		.combine(localStorageChanges.onStart { emit(null) }) { details, _ -> details }
		.map { details ->
			if (details == null) return@map 0L
			
			val local = details.local
			if (local != null) {
				// 普通本地漫画：计算文件夹大小
				runCatchingCancellable {
					local.file.computeSize()
				}.getOrDefault(0L)
			} else {
				// 检查是否有EPUB文件（对于非本地但有EPUB下载的manga）
				val manga = details.toContent()
				runCatchingCancellable {
					val epubDir = epubStorageManager.getEpubDir(manga.id)
					if (epubDir.exists()) {
						epubDir.computeSize()
					} else {
						0L
					}
				}.getOrDefault(0L)
			}
		}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(5000), 0L)

	val isScrobblingAvailable: Boolean
		get() = scrobblers.any { it.isEnabled }

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	val scrobblingInfo: StateFlow<List<ScrobblingInfo>> = activeMangaIdFlow.filterNotNull().flatMapLatest { interactor.observeScrobblingInfo(it) }
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

	val trackingMatchSuggestion = MutableStateFlow<TrackingSiteMatchResult?>(null)

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	val linkedTrackingItems: StateFlow<List<LinkedTrackingItemUiModel>> = activeMangaIdFlow.filterNotNull().flatMapLatest { db.getTrackingSiteDao()
		.observeLinksByManga(it) }
		.mapLatest { links ->
			links.mapNotNull { link ->
				val service = org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService.entries
					.firstOrNull { it.id == link.service }
					?: return@mapNotNull null
				val cached = trackingSiteCacheRepository.readDetails(service, link.remoteId)
				val scrobbling = scrobblingInfo.value.firstOrNull {
					it.scrobbler == service && it.targetId == link.remoteId
				}
				LinkedTrackingItemUiModel(
					service = service,
					remoteId = link.remoteId,
					title = cached?.title ?: scrobbling?.title ?: contentTitleFallback(service),
					coverUrl = cached?.coverUrl ?: scrobbling?.coverUrl,
					summary = cached?.description ?: scrobbling?.description?.toString(),
					url = cached?.url ?: scrobbling?.externalUrl,
					status = scrobbling?.status,
					rating = scrobbling?.rating,
					isPreferred = service == settings.preferredTrackingSite,
				)
			}
		}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

	val relatedContent: StateFlow<List<ContentListModel>> = manga.mapLatest {
		if (it != null && settings.isRelatedContentEnabled) {
			mangaListMapper.toListModelList(
				manga = relatedContentUseCase(it).orEmpty(),
				mode = ListMode.GRID,
			)
		} else {
			emptyList()
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Lazily, emptyList())

	val tags = manga.mapLatest {
		mangaListMapper.mapTags(it?.tags.orEmpty())
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

	val branches: StateFlow<List<ContentBranch>> = combine(
		mangaDetails,
		selectedBranch,
		history,
	) { m, b, h ->
		val c = m?.chapters
		if (c.isNullOrEmpty()) {
			return@combine emptyList()
		}
		val currentBranch = h?.let { m.allChapters.findById(it.chapterId) }?.branch
		c.map { x ->
			ContentBranch(
				name = x.key,
				count = x.value.size,
				isSelected = x.key == b,
				isCurrent = h != null && x.key == currentBranch,
			)
		}.sortedWith(BranchComparator())
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())

	val selectedBranchValue: String?
		get() = selectedBranch.value

	init {
		loadingJob = doLoad(force = false)
		scrobblingInfo
			.onEach {
				refreshTrackingMatchSuggestion()
			}
			.launchIn(viewModelScope + Dispatchers.Default)
		launchJob(Dispatchers.Default + SkipErrors) {
			val manga = mangaDetails.firstOrNull { !it?.chapters.isNullOrEmpty() } ?: return@launchJob
			val h = history.firstOrNull()
			if (h != null) {
				progressUpdateUseCase(manga.toContent())
			}
		}
		launchJob(Dispatchers.Default) {
			val manga = mangaDetails.firstOrNull { it != null && it.isLocal } ?: return@launchJob
			remoteContent.value = interactor.findRemote(manga.toContent())
		}
		launchJob(Dispatchers.Default) {
			val content = manga.filterNotNull().firstOrNull() ?: return@launchJob
			if (!content.isLocal) {
				trackingMatchSuggestion.value = null
				return@launchJob
			}
			refreshTrackingMatchSuggestion()
		}
		launchJob(Dispatchers.Default) {
			mangaDetails.filterNotNull().collect { details ->
				val override = dataRepository.getOverride(details.id)
				isMarkedSafe.value = override?.contentRating == org.skepsun.kototoro.parsers.model.ContentRating.SAFE
			}
		}
		launchJob(Dispatchers.Default) {
			mangaDetails.filterNotNull().collect { details ->
				restorePersistedTranslation(details)
			}
		}
	}

	fun reload() {
		loadingJob.cancel()
		loadingJob = doLoad(force = true)
	}

	fun updateScrobbling(scrobblerServiceId: Int, rating: Float, status: ScrobblingStatus?) {
		val scrobbler = getScrobbler(scrobblerServiceId) ?: return
		launchJob(Dispatchers.Default) {
			scrobbler.updateScrobblingInfo(
				mangaId = mangaId,
				rating = rating,
				status = status,
				comment = null,
			)
		}
	}

	fun unregisterScrobbling(scrobblerServiceId: Int) {
		val scrobbler = getScrobbler(scrobblerServiceId) ?: return
		launchJob(Dispatchers.Default) {
			scrobbler.unregisterScrobbling(
				mangaId = mangaId,
			)
		}
	}

	fun bindTrackingMatch(match: TrackingSiteMatchResult) {
		launchJob(Dispatchers.Default) {
			val content = manga.filterNotNull().firstOrNull() ?: return@launchJob
			trackingSiteMatcher.confirmMatch(match.service, content.id, match.remoteId)
			refreshTrackingMatchSuggestion()
		}
	}

	fun removeTrackingMatch(match: TrackingSiteMatchResult) {
		launchJob(Dispatchers.Default) {
			val content = manga.filterNotNull().firstOrNull() ?: return@launchJob
			trackingSiteMatcher.removeMatch(match.service, content.id)
			refreshTrackingMatchSuggestion()
		}
	}

	fun removeFromHistory() {
		launchJob(Dispatchers.Default) {
			val handle = historyRepository.delete(setOf(mangaId))
			onActionDone.call(ReversibleAction(R.string.removed_from_history, handle))
		}
	}

	// --- Favorite Category Management (for Compose dialog) ---

	val allCategories: StateFlow<List<FavouriteCategory>> = favouritesRepository.observeCategories()
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Lazily, emptyList())

	fun setFavouriteCategory(categoryId: Long, isChecked: Boolean) {
		launchJob(Dispatchers.Default) {
			val content = getContentOrNull() ?: return@launchJob
			if (isChecked) {
				favouritesRepository.addToCategory(categoryId, listOf(content))
			} else {
				favouritesRepository.removeFromCategory(categoryId, listOf(content.id))
			}
		}
	}

	fun toggleMarkSafe() {
		launchJob(Dispatchers.Default) {
			val manga = mangaDetails.value?.toContent() ?: return@launchJob
			val override = dataRepository.getOverride(manga.id) ?: org.skepsun.kototoro.core.ui.model.ContentOverride(null, null, null)
			
			val isCurrentlyNsfw = manga.isNsfw()
			val newRating = if (isCurrentlyNsfw) {
				org.skepsun.kototoro.parsers.model.ContentRating.SAFE
			} else {
				org.skepsun.kototoro.parsers.model.ContentRating.ADULT
			}
			
			dataRepository.setOverride(manga, override.copy(contentRating = newRating))
			doLoad(false)
		}
	}

	private fun doLoad(force: Boolean) = launchLoadingJob(Dispatchers.Default) {
		detailsLoadUseCase.invoke(intent, force)
			.onEachWhile {
				if (it.allChapters.isNotEmpty()) {
					val manga = it.toContent()
					// find default branch
					val hist = historyRepository.getOne(manga)
					selectedBranch.value = manga.getPreferredBranch(hist)
					true
				} else {
					false
				}
			}.collect { details ->
				// For EPUB sources, DetailsLoadUseCase already handles chapter expansion
				// We just need to reset selectedBranch to null for EPUB chapters
				val finalDetails = if (localEpubSource.hasEpubFile(details.id)) {
					android.util.Log.d("DetailsViewModel", "EPUB file detected for manga ${details.id}")
					android.util.Log.d("DetailsViewModel", "Using chapters from DetailsLoadUseCase (${details.allChapters.size} chapters)")
					
					// IMPORTANT: Reset selectedBranch to null for EPUB
					// EPUB chapters all have branch=null, so we need to reset selectedBranch
					// to avoid branch mismatch (e.g., selectedBranch="中日对照" but EPUB branch=null)
					selectedBranch.value = null
					android.util.Log.d("DetailsViewModel", "Reset selectedBranch to null for EPUB")
					
					// Use the details as-is, which already contains expanded EPUB chapters
					// from DetailsLoadUseCase (including both internal chapters and download links)
					details
				} else {
					details
				}
				
					mangaDetails.value = finalDetails
				}
	}

	private fun contentTitleFallback(service: org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService): String {
		return service.name
	}

	private fun refreshTrackingMatchSuggestion() {
		launchJob(Dispatchers.Default) {
			val content = manga.filterNotNull().firstOrNull() ?: return@launchJob
			if (!content.isLocal) {
				trackingMatchSuggestion.value = null
				return@launchJob
			}
			val preferredService = settings.preferredTrackingSite
			val match = trackingSiteMatcher
				.matchLocalContent(preferredService, content, limit = 5, persistAutoMatch = false)
				.firstOrNull { it.confidence >= 0.82f }
			val hasPreferredScrobbling = scrobblingInfo.value.any { it.scrobbler == preferredService }
			trackingMatchSuggestion.value = when {
				match == null -> null
				match.isLinked -> match
				!hasPreferredScrobbling -> match
				else -> null
			}
		}
	}

	private fun getScrobbler(scrobblerServiceId: Int): Scrobbler? {
		val scrobbler = scrobblers.find {
			it.scrobblerService.id == scrobblerServiceId && it.isEnabled
		}
		if (scrobbler == null) {
			errorEvent.call(IllegalStateException("Scrobbler [$scrobblerServiceId] is not available"))
		}
		return scrobbler
	}

	/**
	 * Expand EPUB chapters in the details page (NEW ARCHITECTURE - SIMPLIFIED)
	 * 
	 * In the new architecture, EPUB chapters are already loaded by LocalEpubSource
	 * in the doLoad() method, so this method simply returns the chapters as-is.
	 * 
	 * This method is kept for backward compatibility with old EPUB data that
	 * still uses the file://path#chapter/N format. Once all data is migrated,
	 * this method can be removed entirely.
	 */
	override suspend fun expandEpubChaptersIfNeeded(chapters: List<org.skepsun.kototoro.details.ui.model.ChapterListItem>): List<org.skepsun.kototoro.details.ui.model.ChapterListItem> {
		android.util.Log.d("DetailsViewModel", "expandEpubChaptersIfNeeded: NEW ARCHITECTURE - returning chapters as-is (${chapters.size} chapters)")
		val manga = mangaDetails.value?.toContent() ?: return chapters
		val contentType = manga.source.getContentType()
		if (contentType != ContentType.VIDEO && contentType != ContentType.HENTAI_VIDEO) {
			return chapters
		}
		val downloadedIds = videoDownloadIndex.getDownloadedChapterIds(manga.id)
		if (downloadedIds.isEmpty()) return chapters
		val downloadedOnly = isDownloadedOnly.value
		return chapters.mapNotNull { item ->
			val isDownloaded = item.chapter.id in downloadedIds || item.isDownloaded
			if (downloadedOnly && !isDownloaded) {
				return@mapNotNull null
			}
			if (isDownloaded && !item.isDownloaded) {
				item.copy(flags = item.flags or FLAG_DOWNLOADED)
			} else {
				item
			}
		}
	}

	fun translateTitleAndDescription(forceRefresh: Boolean = false) {
		viewModelScope.launch(Dispatchers.IO) {
			if (!forceRefresh && hasTranslationCache.value) {
				isShowingTranslation.value = true
				persistCurrentTranslationState()
				return@launch
			}
			val manga = getContentOrNull() ?: return@launch
			val title = manga.title
			val description = mangaDetails.value?.description?.toString() ?: ""
			if (title.isBlank()) return@launch

			isTranslating.value = true
			try {
				val targetLang = currentTargetLang()
				val sourceLang = currentSourceLang()

				// Use ML Kit for simple text translation (same as reader pipeline)
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
					detailsTranslationCache.put(
						content = manga,
						sourceLang = sourceLang,
						targetLang = targetLang,
						entry = CachedTranslationEntry(
							originalTitle = title,
							translatedTitle = nextTranslatedTitle,
							originalDescription = description,
							translatedDescription = nextTranslatedDescription,
							isShowingTranslation = true,
						),
					)
				}
			} catch (e: Exception) {
				if (e is kotlinx.coroutines.CancellationException) throw e
				android.util.Log.e("DetailsViewModel", "Translation failed", e)
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

	fun clearTranslationCache() {
		clearInMemoryTranslationState()
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

	private fun restorePersistedTranslation(details: ContentDetails) {
		val content = details.toContent()
		val sourceLang = currentSourceLang()
		val targetLang = currentTargetLang()
		val originalTitle = content.title
		val originalDescription = details.description?.toString().orEmpty()
		val restored = detailsTranslationCache.get(
			content = content,
			sourceLang = sourceLang,
			targetLang = targetLang,
			originalTitle = originalTitle,
			originalDescription = originalDescription,
		)
		if (restored == null) {
			clearInMemoryTranslationState()
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
		val details = mangaDetails.value ?: return
		val content = details.toContent()
		if (!hasTranslationCache.value) return
		detailsTranslationCache.put(
			content = content,
			sourceLang = sourceLang,
			targetLang = targetLang,
			entry = CachedTranslationEntry(
				originalTitle = content.title,
				translatedTitle = cachedTranslatedTitle.value,
				originalDescription = details.description?.toString().orEmpty(),
				translatedDescription = cachedTranslatedDescription.value,
				isShowingTranslation = isShowingTranslation.value,
			),
		)
	}

	private fun clearInMemoryTranslationState() {
		cachedTranslatedTitle.value = null
		cachedTranslatedDescription.value = null
		isShowingTranslation.value = false
		translationCacheSourceLang = null
		translationCacheTargetLang = null
	}

	private fun currentSourceLang(): String {
		return settings.readerTranslationSourceLanguage.ifBlank { "auto" }
	}

	private fun currentTargetLang(): String {
		return settings.readerTranslationTargetLanguage.ifBlank { "zh" }
	}
}
