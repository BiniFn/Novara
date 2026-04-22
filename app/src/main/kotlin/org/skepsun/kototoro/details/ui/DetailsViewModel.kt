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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.details.ui.model.ActiveLocalSourceOption
import org.skepsun.kototoro.details.ui.model.EntityChapterSourceInfo
import org.skepsun.kototoro.details.ui.model.toListItem
import org.skepsun.kototoro.details.ui.model.LinkedTrackingItemUiModel
import org.skepsun.kototoro.bookmarks.domain.BookmarksRepository
import org.skepsun.kototoro.core.model.ContentSource
import org.skepsun.kototoro.core.model.ContentSourceInfo
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
import org.skepsun.kototoro.details.ui.model.DetailsSourceOption
import org.skepsun.kototoro.details.ui.model.DetailsChapterSourceTab
import org.skepsun.kototoro.details.ui.model.ChapterListItem.Companion.FLAG_DOWNLOADED
import org.skepsun.kototoro.details.ui.pager.ChaptersPagesViewModel
import org.skepsun.kototoro.discover.ui.details.LocalSearchState
import org.skepsun.kototoro.download.ui.worker.DownloadWorker
import org.skepsun.kototoro.explore.data.ContentSourcesRepository
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
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentListFilter
import org.skepsun.kototoro.parsers.model.ContentState
import org.skepsun.kototoro.parsers.model.SortOrder
import org.skepsun.kototoro.parsers.util.findById
import org.skepsun.kototoro.parsers.util.ifNullOrEmpty
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import org.skepsun.kototoro.reader.ui.ReaderState
import org.skepsun.kototoro.scrobbling.common.domain.Scrobbler
import org.skepsun.kototoro.scrobbling.common.domain.tryScrobble
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblingInfo
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblingStatus
import org.skepsun.kototoro.stats.data.StatsRepository
import org.skepsun.kototoro.video.data.VideoDownloadIndex
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteCatalog
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatchResult
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteMatcher
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItem
import org.skepsun.kototoro.core.parser.ContentDataRepository.MetadataSourceSelection as PersistedMetadataSourceSelection
import javax.inject.Inject
import kotlin.experimental.or
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.entitygraph.domain.Entity
import org.skepsun.kototoro.entitygraph.domain.EntityBinding
import org.skepsun.kototoro.entitygraph.domain.EntityType
import org.skepsun.kototoro.entitygraph.domain.Relation
import org.skepsun.kototoro.entitygraph.domain.RelationType
import org.skepsun.kototoro.entitygraph.domain.TrackingCharacterDto
import org.skepsun.kototoro.entitygraph.domain.TrackingPersonDto
import org.skepsun.kototoro.entitygraph.domain.TrackingStaffDto
import org.skepsun.kototoro.entitygraph.domain.TrackingWorkDto
import org.skepsun.kototoro.entitygraph.ui.details.EntityRelationSection
import org.skepsun.kototoro.entitygraph.ui.details.EntityRelationItem
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.details.ui.model.TrackingDetailsSection
import org.skepsun.kototoro.details.ui.model.TrackingDetailsAction
import org.skepsun.kototoro.details.ui.model.TrackingDetailsSectionItem
import kotlinx.coroutines.channels.BufferOverflow
import java.util.Locale

private const val ENTITY_RELATION_SECTIONS_DEBOUNCE_MS = 120L
private const val TRACKING_SUGGESTION_THRESHOLD = 0.9f
private const val TRACKING_SUGGESTION_GAP_THRESHOLD = 0.03f
private const val TRACKING_SUGGESTION_RESULT_LIMIT = 3
private val CHARACTER_VOICE_ACTOR_REGEX = Regex(
	"""^\s*(.+?)\s*\((?:cv|cast|voice actor|voice|配音|声优)\s*[:：]?\s*(.+?)\)\s*$""",
	RegexOption.IGNORE_CASE,
)

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
	private val contentSourcesRepository: ContentSourcesRepository,
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
	val activeExternalOrigin = savedStateHandle.get<org.skepsun.kototoro.details.ui.model.DetailsOrigin>(
		org.skepsun.kototoro.core.nav.AppRouter.KEY_DETAILS_ORIGIN,
	)
	private val originContent = (activeExternalOrigin as? org.skepsun.kototoro.details.ui.model.DetailsOrigin.LocalMangaContent)?.manga
	private val initialLoadIntentOverride = when (val origin = activeExternalOrigin) {
		is org.skepsun.kototoro.details.ui.model.DetailsOrigin.LocalMangaContent -> ContentIntent.of(origin.manga)
		is org.skepsun.kototoro.details.ui.model.DetailsOrigin.LocalMangaId -> ContentIntent.of(origin.mangaId)
		else -> null
	}
	private var loadingJob: Job
	private var translateAvailabilityJob: Job? = null
	private var currentLoadIntentOverride: ContentIntent? = initialLoadIntentOverride
	private var translationCacheSourceLang: String? = null
	private var translationCacheTargetLang: String? = null
	private val activeMangaIdFlow = kotlinx.coroutines.flow.MutableStateFlow(
		when (val origin = activeExternalOrigin) {
			is org.skepsun.kototoro.details.ui.model.DetailsOrigin.LocalMangaId -> origin.mangaId.takeIf { it != 0L }
			is org.skepsun.kototoro.details.ui.model.DetailsOrigin.LocalMangaContent -> origin.manga.id.takeIf { it != 0L }
			else -> intent.mangaId.takeIf { it != 0L }
		},
	)
	val mangaId: Long get() = activeMangaIdFlow.value ?: intent.mangaId

	private val pendingEntityRelationSections = MutableSharedFlow<List<EntityRelationSection>>(
		replay = 1,
		extraBufferCapacity = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
	)
	val entityRelationSections: StateFlow<List<EntityRelationSection>> = pendingEntityRelationSections
		.debounce(ENTITY_RELATION_SECTIONS_DEBOUNCE_MS)
		.distinctUntilChanged()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, emptyList())
	val activeLocalSourceOptions = MutableStateFlow<List<ActiveLocalSourceOption>>(emptyList())
	val entityChapterSourceInfo = MutableStateFlow<EntityChapterSourceInfo?>(null)
	val metadataSourceOptions = MutableStateFlow<List<DetailsSourceOption>>(emptyList())
	val readingSourceOptions = MutableStateFlow<List<DetailsSourceOption>>(emptyList())
	val metadataChapterTabs = MutableStateFlow<List<DetailsChapterSourceTab>>(emptyList())
	val readingChapterTabs = MutableStateFlow<List<DetailsChapterSourceTab>>(emptyList())
	val trackingMetadataProperties = MutableStateFlow<List<Pair<String, String>>>(emptyList())
	val trackingDetailsSections = MutableStateFlow<List<TrackingDetailsSection>>(emptyList())
	val trackingDetailsActions = MutableStateFlow<List<TrackingDetailsAction>>(emptyList())
	val trackingCommentThreads = MutableStateFlow<List<org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails.CommentThread>>(emptyList())
	val trackingCommentsUrl = MutableStateFlow<String?>(null)
	val trackingReviews = MutableStateFlow<List<org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails.ReviewEntry>>(emptyList())
	val trackingReviewsUrl = MutableStateFlow<String?>(null)
	val metadataSearchServices = MutableStateFlow<List<ScrobblerService>>(emptyList())
	val authorizedTrackingServices = MutableStateFlow<Set<ScrobblerService>>(emptySet())
	val selectedMetadataSearchService = MutableStateFlow(ScrobblerService.ANILIST)
	val metadataSearchQuery = MutableStateFlow("")
	val metadataSearchResults = MutableStateFlow<List<TrackingSiteItem>>(emptyList())
	val metadataSearchLoading = MutableStateFlow(false)
	val metadataSearchError = MutableStateFlow<String?>(null)
	val readingSearchSources = MutableStateFlow<List<ContentSourceInfo>>(emptyList())
	val selectedReadingSearchSource = MutableStateFlow<String?>(null)
	val readingSearchQuery = MutableStateFlow("")
	val readingSearchState = MutableStateFlow<LocalSearchState?>(null)
	val resolvedMetadataContentType = MutableStateFlow<ContentType?>(null)
	val resolvedMetadataLanguage = MutableStateFlow<String?>(null)
	val resolvedReadingLanguage = MutableStateFlow<String?>(null)
	val showTranslateAction = MutableStateFlow(false)
	val activeLocalBrowserContent = MutableStateFlow<Content?>(null)
	private val allEnabledSourceInfos = MutableStateFlow<List<ContentSourceInfo>>(emptyList())

	private var baseLoadedDetails: ContentDetails? = null
	private val trackingMetadataCandidates = MutableStateFlow<List<TrackingMetadataCandidate>>(emptyList())
	private val selectedMetadataSource = MutableStateFlow<MetadataSourceSelection>(initialMetadataSourceSelection())
	private val cachedTrackingDetails = LinkedHashMap<String, org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails>()

	private sealed interface MetadataSourceSelection {
		data object Base : MetadataSourceSelection
		data class Tracking(
			val service: ScrobblerService,
			val remoteId: Long,
			val url: String?,
		) : MetadataSourceSelection
	}

	private data class TrackingMetadataCandidate(
		val service: ScrobblerService,
		val remoteId: Long,
		val url: String? = null,
	)

	private fun String?.normalizedImageUrl(): String? = this?.takeIf { it.isNotBlank() }

	private fun initialMetadataSourceSelection(): MetadataSourceSelection {
		val trackingOrigin = activeExternalOrigin as? org.skepsun.kototoro.details.ui.model.DetailsOrigin.TrackingItem
			?: return MetadataSourceSelection.Base
		val service = ScrobblerService.entries.firstOrNull {
			it.id == trackingOrigin.serviceId.toIntOrNull()
		} ?: return MetadataSourceSelection.Base
		return MetadataSourceSelection.Tracking(
			service = service,
			remoteId = trackingOrigin.remoteId,
			url = trackingOrigin.url,
		)
	}

	private fun currentDetailsTitle(): String {
		return mangaDetails.value?.toContent()?.title
			?: baseLoadedDetails?.toContent()?.title
			?: originContent?.title
			?: intent.manga?.title
			.orEmpty()
	}

	private fun currentBaseContentType(): ContentType? {
		return baseLoadedDetails?.toContent()?.source?.getContentType()
			?: originContent?.source?.getContentType()
			?: intent.manga?.source?.getContentType()
	}

	private fun currentMetadataContentType(): ContentType? {
		return when (selectedMetadataSource.value) {
			MetadataSourceSelection.Base -> currentBaseContentType()
			is MetadataSourceSelection.Tracking -> currentTrackingMetadataDetails()?.contentType ?: currentBaseContentType()
		}
	}

	private fun currentDetailsContentType(): ContentType? {
		return currentMetadataContentType()
	}

	private fun currentMetadataLanguageCode(): String? {
		return when (selectedMetadataSource.value) {
			MetadataSourceSelection.Base -> {
				baseLoadedDetails?.toContent()?.source?.locale
					?.takeIf { it.isNotBlank() }
					?: originContent?.source?.locale?.takeIf { it.isNotBlank() }
					?: intent.manga?.source?.locale?.takeIf { it.isNotBlank() }
			}
			is MetadataSourceSelection.Tracking -> {
				currentTrackingMetadataDetails()?.let { details ->
					resolveTrackingLanguage(details.infoboxProperties).takeIf { it.isNotBlank() }
						?: details.toContentLocale()
				}
			}
		}
	}

	private fun org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails.toContentLocale(): String? {
		return contentType?.let { _ ->
			resolveTrackingLanguage(infoboxProperties).takeIf { it.isNotBlank() }
		}
	}

	private fun currentReadingLanguageCode(): String? {
		return readingSourceOptions.value.firstOrNull { it.isSelected }?.source?.locale?.takeIf { it.isNotBlank() }
			?: activeLocalSourceOptions.value.firstOrNull { it.isActive }?.source?.locale?.takeIf { it.isNotBlank() }
			?: baseLoadedDetails?.local?.manga?.source?.locale?.takeIf { it.isNotBlank() }
	}

	private fun String.normalizedLanguageCode(): String {
		return trim()
			.substringBefore('-')
			.substringBefore('_')
			.lowercase(Locale.ROOT)
	}

	private fun isTrackingSource(source: org.skepsun.kototoro.parsers.model.ContentSource): Boolean {
		return source.name.startsWith("TRACKING_")
	}

	private fun isReadingSearchSourceMatch(
		source: org.skepsun.kototoro.parsers.model.ContentSource,
		contentType: ContentType?,
	): Boolean {
		if (isTrackingSource(source) || source.isLocal) {
			return false
		}
		val sourceType = source.getContentType()
		return when (contentType) {
			ContentType.VIDEO, ContentType.HENTAI_VIDEO -> {
				sourceType == ContentType.VIDEO || sourceType == ContentType.HENTAI_VIDEO
			}
			ContentType.NOVEL, ContentType.HENTAI_NOVEL -> {
				sourceType == ContentType.NOVEL || sourceType == ContentType.HENTAI_NOVEL
			}
			else -> {
				sourceType !in setOf(
					ContentType.VIDEO,
					ContentType.HENTAI_VIDEO,
					ContentType.NOVEL,
					ContentType.HENTAI_NOVEL,
				)
			}
		}
	}

	private fun TrackingSiteItem.toScrobblerContent(): org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContent {
		return org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContent(
			id = remoteId,
			name = title,
			altName = altTitle,
			cover = coverUrl,
			url = url.orEmpty(),
		)
	}

	private fun refreshReadingSearchSources() {
		val filtered = allEnabledSourceInfos.value.filter { info ->
			isReadingSearchSourceMatch(info.mangaSource, currentDetailsContentType())
		}
		readingSearchSources.value = filtered
		if (selectedReadingSearchSource.value !in filtered.map { it.mangaSource.name }.toSet()) {
			selectedReadingSearchSource.value = filtered.firstOrNull()?.mangaSource?.name
		}
	}

	private fun syntheticSource(
		name: String,
		contentType: ContentType,
		locale: String = "",
	): org.skepsun.kototoro.parsers.model.ContentSource = object : org.skepsun.kototoro.parsers.model.ContentSource {
		override val name: String = name
		override val locale: String = locale
		override val contentType: ContentType = contentType
	}

	private fun trackingDetailsToSyntheticChapters(
		details: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails,
		source: org.skepsun.kototoro.parsers.model.ContentSource,
	): List<ContentChapter> {
		return details.episodes.mapIndexed { index, episode ->
			ContentChapter(
				id = syntheticChapterId(details.service, details.remoteId, episode.url, index),
				title = episode.title.ifBlank { episode.number },
				number = episode.number.toFloatOrNull() ?: (index + 1).toFloat(),
				volume = 0,
				url = episode.url,
				scanlator = null,
				uploadDate = 0L,
				branch = null,
				source = source,
			)
		}
	}

	private fun trackingDetailsToSyntheticContent(details: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails): Content {
		val contentType = details.contentType ?: ContentType.MANGA
		val language = resolveTrackingLanguage(details.infoboxProperties)
		val source = syntheticSource("TRACKING_${details.service.name}", contentType, language)
		val authors = resolveTrackingAuthors(details)
		val chapters = trackingDetailsToSyntheticChapters(details, source).ifEmpty { null }
		return Content(
			id = details.remoteId,
			title = details.title,
			altTitles = setOfNotNull(details.altTitle?.takeIf { it.isNotBlank() }),
			url = details.url.orEmpty(),
			publicUrl = details.url.orEmpty(),
			rating = (details.score ?: 0f) / 10f,
			contentRating = null,
			coverUrl = details.coverUrl.normalizedImageUrl(),
			tags = details.tags.mapTo(linkedSetOf()) { tag ->
				org.skepsun.kototoro.parsers.model.ContentTag(
					title = tag,
					key = tag.lowercase(),
					source = source,
				)
			},
			state = resolveTrackingState(details.infoboxProperties),
			authors = authors,
			description = details.description,
			chapters = chapters,
			source = source,
		)
	}

	private fun syntheticChapterId(
		service: ScrobblerService,
		remoteId: Long,
		url: String,
		index: Int,
	): Long {
		return "${service.id}:$remoteId:$url:$index".hashCode().toLong() and Long.MAX_VALUE
	}

	private fun resolveTrackingAuthors(
		details: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails,
	): Set<String> {
		if (details.authors.isNotEmpty()) {
			return details.authors.filter { it.isNotBlank() }.toSet()
		}
		return details.infoboxProperties.mapNotNull { (key, value) ->
			if (!key.isAuthorProperty()) {
				return@mapNotNull null
			}
			value.takeIf { it.isNotBlank() }
		}.toSet()
	}

	private fun resolveTrackingState(properties: List<Pair<String, String>>): ContentState? {
		val value = properties.firstMappedValue("status", "publishing", "airing", "state")
			?.lowercase(Locale.ROOT)
			?: return null
		return when {
			value.contains("ongoing") || value.contains("publishing") || value.contains("releasing") || value.contains("airing") -> ContentState.ONGOING
			value.contains("finished") || value.contains("completed") || value.contains("complete") || value.contains("ended") -> ContentState.FINISHED
			value.contains("hiatus") || value.contains("pause") || value.contains("on hold") -> ContentState.PAUSED
			value.contains("cancel") || value.contains("abandon") || value.contains("dropped") -> ContentState.ABANDONED
			value.contains("upcoming") || value.contains("announced") || value.contains("not yet") || value.contains("tba") -> ContentState.UPCOMING
			else -> null
		}
	}

	private fun resolveTrackingLanguage(properties: List<Pair<String, String>>): String {
		val value = properties.firstMappedValue("language", "lang", "original language")
			?.lowercase(Locale.ROOT)
			?: return ""
		return when {
			value.contains("japanese") || value.contains("日本語") || value.contains("ja") -> "ja"
			value.contains("chinese") || value.contains("中文") || value.contains("mandarin") || value.contains("zh") -> "zh"
			value.contains("english") || value.contains("en") -> "en"
			value.contains("korean") || value.contains("한국어") || value.contains("ko") -> "ko"
			value.contains("french") || value.contains("fr") -> "fr"
			value.contains("spanish") || value.contains("es") -> "es"
			else -> ""
		}
	}

	private fun List<Pair<String, String>>.firstMappedValue(vararg keys: String): String? {
		return firstOrNull { (key, value) ->
			value.isNotBlank() && keys.any { expected ->
				key.normalizedMetadataKey().contains(expected)
			}
		}?.second
	}

	private fun String.normalizedMetadataKey(): String {
		return lowercase(Locale.ROOT)
			.replace("：", ":")
			.replace("_", " ")
			.replace("-", " ")
			.replace(" ", "")
	}

	private fun String.isAuthorProperty(): Boolean {
		val key = normalizedMetadataKey()
		return key.contains("author") ||
			key.contains("creator") ||
			key.contains("writer") ||
			key.contains("artist") ||
			key.contains("illustrator") ||
			key.contains("director") ||
			key.contains("studio") ||
			key.contains("staff") ||
			key.contains("原作") ||
			key.contains("作者") ||
			key.contains("作画") ||
			key.contains("编剧") ||
			key.contains("脚本") ||
			key.contains("监督") ||
			key.contains("导演") ||
			key.contains("制作")
	}

	private fun String.isCharacterProperty(): Boolean {
		val key = normalizedMetadataKey()
		return key.contains("character") ||
			key.contains("cast") ||
			key.contains("角色") ||
			key.contains("人物") ||
			key.contains("登场")
	}

	private fun splitTrackingNames(raw: String): List<String> {
		return raw.split('/', '／', ',', '，', ';', '；', '\n')
			.map { it.trim() }
			.filter { it.isNotBlank() }
	}

	private fun normalizeContributorName(raw: String): String {
		return raw.substringBefore(" (").substringBefore("（").trim()
	}

	private fun parseTrackingCharacterCredit(raw: String): TrackingCharacterDto? {
		val match = CHARACTER_VOICE_ACTOR_REGEX.matchEntire(raw.trim()) ?: return null
		val characterName = normalizeContributorName(match.groupValues[1]).takeIf { it.isNotBlank() } ?: return null
		val voiceActors = splitTrackingNames(match.groupValues[2]).mapNotNull { actor ->
			normalizeContributorName(actor).takeIf { it.isNotBlank() }?.let { TrackingPersonDto(primaryName = it) }
		}
		return TrackingCharacterDto(
			primaryName = characterName,
			voiceActors = voiceActors,
		)
	}

	private fun buildTrackingWorkDto(
		details: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails,
	): TrackingWorkDto {
		val staffByName = LinkedHashMap<String, TrackingStaffDto>()
		val charactersByName = LinkedHashMap<String, TrackingCharacterDto>()

		fun addStaff(raw: String) {
			val normalized = normalizeContributorName(raw)
			if (normalized.isBlank()) {
				return
			}
			staffByName.putIfAbsent(normalized, TrackingStaffDto(primaryName = normalized))
		}

		fun addCharacter(character: TrackingCharacterDto) {
			val key = character.primaryName.trim()
			if (key.isBlank()) {
				return
			}
			val existing = charactersByName[key]
			charactersByName[key] = if (existing == null) {
				character.copy(primaryName = key)
			} else {
				existing.copy(
					voiceActors = (existing.voiceActors + character.voiceActors)
						.distinctBy { it.primaryName },
				)
			}
		}

		details.characters.forEach { character ->
			addCharacter(
				TrackingCharacterDto(
					externalId = character.id.toString(),
					primaryName = normalizeContributorName(character.name),
					voiceActors = character.voiceActors.mapNotNull { actor ->
						normalizeContributorName(actor.name).takeIf { it.isNotBlank() }?.let { actorName ->
							TrackingPersonDto(
								externalId = actor.id?.toString(),
								primaryName = actorName,
							)
						}
					},
				),
			)
		}
		details.authors.forEach { author ->
			parseTrackingCharacterCredit(author)?.let(::addCharacter) ?: addStaff(author)
		}
		details.infoboxProperties.forEach { (key, value) ->
			when {
				key.isCharacterProperty() -> {
					splitTrackingNames(value).forEach { item ->
						parseTrackingCharacterCredit(item)?.let(::addCharacter)
					}
				}
				key.isAuthorProperty() -> {
					splitTrackingNames(value).forEach(::addStaff)
				}
			}
		}

		return TrackingWorkDto(
			externalId = details.remoteId.toString(),
			primaryName = details.title,
			contentType = details.contentType,
			aliases = listOfNotNull(details.altTitle?.takeIf { it.isNotBlank() }),
			characters = charactersByName.values.toList(),
			staff = staffByName.values.toList(),
		)
	}

	private suspend fun ingestTrackingDetailsIntoEntityGraph(
		details: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails,
	) {
		entityGraphRepository.ingestWorkFromTracking(
			source = details.service.id.toString(),
			workDto = buildTrackingWorkDto(details),
		)
	}

	init {
		// Apply instant first paint from Intent or DetailsOrigin
		baseLoadedDetails = (originContent ?: intent.manga)?.let { ContentDetails(it) }
		syncDisplayedState()
		metadataSearchServices.value = ScrobblerService.entries.filter { service ->
			trackingSiteDiscoveryService.getCapabilities(service).supportsSearch
		}
		authorizedTrackingServices.value = scrobblers.filter { it.isEnabled }.mapTo(linkedSetOf()) { it.scrobblerService }
		selectedMetadataSearchService.value = (selectedMetadataSource.value as? MetadataSourceSelection.Tracking)?.service
			?: metadataSearchServices.value.firstOrNull()
			?: settings.preferredTrackingSite
		val initialTitle = currentDetailsTitle()
		if (metadataSearchQuery.value.isBlank() && initialTitle.isNotBlank()) {
			metadataSearchQuery.value = initialTitle
		}
		if (readingSearchQuery.value.isBlank() && initialTitle.isNotBlank()) {
			readingSearchQuery.value = initialTitle
		}
		activeMangaIdFlow.value
			?.takeIf { activeExternalOrigin !is org.skepsun.kototoro.details.ui.model.DetailsOrigin.TrackingItem }
			?.let { mangaId ->
				launchJob(Dispatchers.IO) {
					restorePersistedMetadataSourceSelection(mangaId)
				}
			}

		launchJob(Dispatchers.Default) {
			mangaDetails.filterNotNull().collect { details ->
				val title = details.toContent().title
				if (title.isNotBlank()) {
					if (metadataSearchQuery.value.isBlank()) {
						metadataSearchQuery.value = title
					}
					if (readingSearchQuery.value.isBlank()) {
						readingSearchQuery.value = title
					}
				}
			}
		}

		launchJob(Dispatchers.Default) {
			contentSourcesRepository.observeEnabledSources().collect { sources ->
				allEnabledSourceInfos.value = sources
				refreshReadingSearchSources()
			}
		}

		launchJob(Dispatchers.IO) {
			activeMangaIdFlow.flatMapLatest { localId ->
				if (localId == null) {
					flowOf(emptyList())
				} else {
					db.getTrackingSiteDao().observeLinksByManga(localId)
				}
			}.collect { links ->
				val linkedCandidates = links.mapNotNull { link ->
					val service = ScrobblerService.entries.firstOrNull { it.id == link.service } ?: return@mapNotNull null
					val cached = trackingSiteCacheRepository.readDetails(service, link.remoteId)
					TrackingMetadataCandidate(
						service = service,
						remoteId = link.remoteId,
						url = cached?.url,
					)
				}
				trackingMetadataCandidates.value = mergeTrackingMetadataCandidates(linkedCandidates)
				syncDisplayedState()
			}
		}

		if (activeExternalOrigin is org.skepsun.kototoro.details.ui.model.DetailsOrigin.EntityGraph) {
			launchJob(Dispatchers.IO) {
				val graphGraphId = activeExternalOrigin.entityId
				val entity = entityGraphRepository.getEntity(graphGraphId) ?: return@launchJob
				val entityCoverUrl = resolveEntityCoverUrl(graphGraphId)
				
				if (mangaDetails.value == null) {
				    val syntheticContent = Content(
				        id = graphGraphId,
				        title = entity.primaryName,
				        altTitles = emptySet(),
				        url = "",
				        publicUrl = "",
				        rating = 0f,
				        contentRating = null,
				        coverUrl = entityCoverUrl,
				        tags = emptySet(),
				        state = null,
				        authors = emptySet(),
				        description = "",
				        chapters = null,
				        source = syntheticSource("Entity Graph", ContentType.MANGA),
				    )
				    baseLoadedDetails = ContentDetails(syntheticContent)
				    syncDisplayedState()
				}
				
				val bindings = entityGraphRepository.getBindings(graphGraphId)
				val boundLocalId = bindings.firstOrNull { it.source == "0" || it.source == "local_manga" }?.externalId?.toLongOrNull()
				activeLocalSourceOptions.value = buildActiveLocalSourceOptions(bindings, boundLocalId)
				entityChapterSourceInfo.value = resolveEntityChapterSourceInfo(boundLocalId)
				updateSourceOptions()
				if (boundLocalId != null) {
				    currentLoadIntentOverride = ContentIntent.of(boundLocalId)
				    activeMangaIdFlow.value = boundLocalId
				    loadingJob = doLoad(force = false) // Trigger reload locally!
				}
				
				submitEntityRelationSections(buildEntityRelationSections(graphGraphId))

			}
		} else if (activeExternalOrigin is org.skepsun.kototoro.details.ui.model.DetailsOrigin.TrackingItem) {
			entityChapterSourceInfo.value = null
			launchJob(Dispatchers.IO) {
			    val service = ScrobblerService.entries.firstOrNull { it.id == activeExternalOrigin.serviceId.toIntOrNull() } ?: return@launchJob
			    val cached = trackingSiteCacheRepository.readDetails(service, activeExternalOrigin.remoteId)
				trackingMetadataCandidates.value = mergeTrackingMetadataCandidates(
					listOf(
						TrackingMetadataCandidate(
							service = service,
							remoteId = activeExternalOrigin.remoteId,
							url = activeExternalOrigin.url ?: cached?.url,
						),
					),
				)
			    
			    if (mangaDetails.value == null && cached != null) {
				    cacheTrackingDetails(cached)
				    ingestTrackingDetailsIntoEntityGraph(cached)
				    baseLoadedDetails = ContentDetails(trackingDetailsToSyntheticContent(cached))
				    syncDisplayedState()
			    }
			    
			    val remoteDetails = try { trackingSiteDiscoveryService.getDetails(service, activeExternalOrigin.remoteId, activeExternalOrigin.url) } catch (e: Exception) { null }
			    if (remoteDetails != null) {
				    cacheTrackingDetails(remoteDetails)
				    ingestTrackingDetailsIntoEntityGraph(remoteDetails)
				    baseLoadedDetails = ContentDetails(trackingDetailsToSyntheticContent(remoteDetails))
				    syncDisplayedState()
			    }
			    
			    if (remoteDetails != null) {
			        trackingSiteCacheRepository.saveDetails(remoteDetails)
			    }
			    
			    // Bind local manga if tracked
			    db.getTrackingSiteDao().observeLinks(service.id, activeExternalOrigin.remoteId).collect { links ->
			        if (links.isNotEmpty() && activeMangaIdFlow.value == null) {
			            val trackingMangaId = links.first().mangaId
			            if (trackingMangaId != 0L) {
			                currentLoadIntentOverride = ContentIntent.of(trackingMangaId)
			                activeMangaIdFlow.value = trackingMangaId
			                persistMetadataSourceSelection(trackingMangaId)
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

	private fun mergeTrackingMetadataCandidates(
		candidates: List<TrackingMetadataCandidate>,
	): List<TrackingMetadataCandidate> {
		val originCandidate = (activeExternalOrigin as? org.skepsun.kototoro.details.ui.model.DetailsOrigin.TrackingItem)
			?.let { origin ->
				val service = ScrobblerService.entries.firstOrNull { it.id == origin.serviceId.toIntOrNull() }
					?: return@let null
				TrackingMetadataCandidate(
					service = service,
					remoteId = origin.remoteId,
					url = origin.url,
				)
			}
		// Keep the currently-selected tracking candidate visible even if the new
		// active local manga has no tracking link yet. Otherwise switching the
		// active local source would silently drop the user's tracking metadata
		// selection from the UI and appear as an auto-switch back to local.
		val selectedCandidate = (selectedMetadataSource.value as? MetadataSourceSelection.Tracking)
			?.let { selection ->
				TrackingMetadataCandidate(
					service = selection.service,
					remoteId = selection.remoteId,
					url = selection.url,
				)
			}
		return buildList {
			originCandidate?.let(::add)
			selectedCandidate?.let(::add)
			addAll(candidates)
		}.distinctBy { trackingMetadataKey(it.service, it.remoteId) }
	}

	private fun trackingMetadataKey(service: ScrobblerService, remoteId: Long): String {
		return "${service.id}:$remoteId"
	}

	private fun cacheTrackingDetails(details: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails) {
		cachedTrackingDetails[trackingMetadataKey(details.service, details.remoteId)] = details
	}

	private fun currentTrackingMetadataDetails(): org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails? {
		val selection = selectedMetadataSource.value as? MetadataSourceSelection.Tracking ?: return null
		return cachedTrackingDetails[trackingMetadataKey(selection.service, selection.remoteId)]
	}

	private fun org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails.hasRichMetadata(): Boolean {
		return infoboxProperties.isNotEmpty() ||
			characters.isNotEmpty() ||
			commentThreads.isNotEmpty() ||
			relatedWorks.isNotEmpty() ||
			recommendations.isNotEmpty() ||
			extraSections.isNotEmpty() ||
			actions.isNotEmpty()
	}

	private fun MetadataSourceSelection.toPersistedSelection(): PersistedMetadataSourceSelection {
		return when (this) {
			MetadataSourceSelection.Base -> PersistedMetadataSourceSelection.Base
			is MetadataSourceSelection.Tracking -> PersistedMetadataSourceSelection.Tracking(
				serviceId = service.id,
				remoteId = remoteId,
			)
		}
	}

	private suspend fun persistMetadataSourceSelection(mangaId: Long) {
		dataRepository.setMetadataSourceSelection(
			mangaId = mangaId,
			selection = selectedMetadataSource.value.toPersistedSelection(),
		)
	}

	private suspend fun restorePersistedMetadataSourceSelection(mangaId: Long) {
		when (val persisted = dataRepository.getMetadataSourceSelection(mangaId)) {
			null -> Unit
			PersistedMetadataSourceSelection.Base -> {
				if (selectedMetadataSource.value != MetadataSourceSelection.Base) {
					selectedMetadataSource.value = MetadataSourceSelection.Base
					syncDisplayedState()
				}
			}
			is PersistedMetadataSourceSelection.Tracking -> {
				val service = ScrobblerService.entries.firstOrNull { it.id == persisted.serviceId } ?: return
				val cached = trackingSiteCacheRepository.readDetails(service, persisted.remoteId)
				if (cached != null) {
					cacheTrackingDetails(cached)
					ingestTrackingDetailsIntoEntityGraph(cached)
				}
				trackingMetadataCandidates.value = mergeTrackingMetadataCandidates(
					trackingMetadataCandidates.value + TrackingMetadataCandidate(
						service = service,
						remoteId = persisted.remoteId,
						url = cached?.url,
					),
				)
				selectedMetadataSource.value = MetadataSourceSelection.Tracking(
					service = service,
					remoteId = persisted.remoteId,
					url = cached?.url,
				)
				selectedMetadataSearchService.value = service
				syncDisplayedState()
				ensureTrackingDetailsLoaded(
					service = service,
					remoteId = persisted.remoteId,
					url = cached?.url,
				)
			}
		}
	}

	private fun mergeActualAndMetadataChapters(
		metadataChapters: List<ContentChapter>,
		actualChapters: List<ContentChapter>,
	): List<ContentChapter> {
		if (actualChapters.isEmpty()) {
			return metadataChapters
		}
		if (metadataChapters.isEmpty()) {
			return actualChapters
		}
		val remainingActual = actualChapters.toMutableList()
		return buildList {
			metadataChapters.forEach { metadataChapter ->
				val match = remainingActual.firstOrNull { actual ->
					actual.id == metadataChapter.id ||
						(
							actual.number > 0f &&
								metadataChapter.number > 0f &&
								actual.number == metadataChapter.number
						) ||
						(
							!actual.title.isNullOrBlank() &&
								actual.title == metadataChapter.title
						)
				}
				if (match != null) {
					add(match)
					remainingActual.remove(match)
				} else {
					add(metadataChapter)
				}
			}
			addAll(remainingActual)
		}
	}

	private fun mergeTrackingMetadata(
		base: ContentDetails?,
		trackingDetails: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails,
	): ContentDetails {
		val trackingContent = trackingDetailsToSyntheticContent(trackingDetails)
		val baseContent = base?.toContent()
		if (baseContent == null) {
			return ContentDetails(
				manga = trackingContent,
				localContent = null,
				override = null,
				description = trackingDetails.description ?: trackingContent.description,
				isLoaded = true,
			)
		}
		// IMPORTANT: keep baseContent's identity (id, url, publicUrl, source, chapters)
		// so the reader/downloader can still resolve pages from the real reading source.
		// Only override display-only fields from tracking metadata.
		val mergedManga = baseContent.copy(
			title = trackingContent.title.ifBlank { baseContent.title },
			altTitles = (baseContent.altTitles + trackingContent.altTitles).toSet(),
			coverUrl = trackingContent.coverUrl.normalizedImageUrl() ?: baseContent.coverUrl,
			rating = if (trackingContent.rating > 0f) trackingContent.rating else baseContent.rating,
			tags = if (trackingContent.tags.isNotEmpty()) trackingContent.tags else baseContent.tags,
			state = trackingContent.state ?: baseContent.state,
			authors = if (trackingContent.authors.isNotEmpty()) trackingContent.authors else baseContent.authors,
			description = trackingContent.description?.takeIf { it.isNotBlank() } ?: baseContent.description,
		)
		return ContentDetails(
			manga = mergedManga,
			localContent = base.local,
			override = null,
			description = trackingDetails.description ?: base.description ?: trackingContent.description,
			isLoaded = true,
		)
	}

	private fun syncDisplayedState() {
		val base = baseLoadedDetails
		val trackingDetails = currentTrackingMetadataDetails()
		mangaDetails.value = when {
			trackingDetails != null -> mergeTrackingMetadata(base, trackingDetails)
			base != null -> base
			else -> null
		}
		updateSourceOptions()
		refreshReadingSearchSources()
		updateTrackingDetailsPanels(trackingDetails)
		refreshResolvedPresentationState()
		if (activeExternalOrigin !is org.skepsun.kototoro.details.ui.model.DetailsOrigin.EntityGraph) {
			refreshContextualEntityRelations()
		}
	}

	private fun refreshResolvedPresentationState() {
		val metadataLanguage = currentMetadataLanguageCode()?.takeIf { it.isNotBlank() }?.normalizedLanguageCode()
		val readingLanguage = currentReadingLanguageCode()?.takeIf { it.isNotBlank() }?.normalizedLanguageCode()
		resolvedMetadataContentType.value = currentMetadataContentType()
		resolvedMetadataLanguage.value = metadataLanguage
		resolvedReadingLanguage.value = readingLanguage
		activeLocalBrowserContent.value = baseLoadedDetails?.toContent()?.takeIf { it.publicUrl.isNotBlank() }
		refreshTranslateActionVisibility(metadataLanguage)
	}

	private fun refreshTranslateActionVisibility(metadataLanguage: String?) {
		val targetLanguage = currentTargetLang().takeIf { it.isNotBlank() }?.normalizedLanguageCode()
		if (targetLanguage.isNullOrBlank()) {
			showTranslateAction.value = false
			return
		}
		translateAvailabilityJob?.cancel()
		translateAvailabilityJob = viewModelScope.launch(Dispatchers.IO) {
			val details = mangaDetails.value
			val sampleText = buildString {
				append(details?.toContent()?.title.orEmpty())
				if (length < 24) {
					append(' ')
					append(details?.description?.toString().orEmpty())
				}
			}.trim()
			val detectedLanguage = if (sampleText.isNotBlank()) {
				detectLanguageViaMlKit(sampleText)?.normalizedLanguageCode()
			} else {
				null
			}
			val effectiveLanguage = metadataLanguage ?: detectedLanguage
			showTranslateAction.value = effectiveLanguage == null || effectiveLanguage != targetLanguage
		}
	}

	private fun updateSourceOptions() {
		val selection = selectedMetadataSource.value
		val baseSource = baseLoadedDetails?.toContent()?.source ?: originContent?.source ?: intent.manga?.source
		val baseLooksLikeTracking = baseSource?.name?.startsWith("TRACKING_") == true
		val metadata = buildList {
			if (baseSource != null && !baseLooksLikeTracking) {
				add(
					DetailsSourceOption(
						key = "base:${baseSource.name}",
						source = baseSource,
						isSelected = selection == MetadataSourceSelection.Base,
					),
				)
			}
			addAll(
				trackingMetadataCandidates.value.map { candidate ->
					DetailsSourceOption(
						key = trackingMetadataKey(candidate.service, candidate.remoteId),
						trackingService = candidate.service,
						remoteId = candidate.remoteId,
						url = candidate.url,
						isSelected = selection is MetadataSourceSelection.Tracking &&
							selection.service == candidate.service &&
							selection.remoteId == candidate.remoteId,
					)
				},
			)
			if (isEmpty() && baseSource != null) {
				add(
					DetailsSourceOption(
						key = "base:${baseSource.name}",
						source = baseSource,
						isSelected = true,
					),
				)
			}
		}.distinctBy(DetailsSourceOption::key)
		metadataSourceOptions.value = metadata

		val currentDisplayedDetails = mangaDetails.value
		readingSourceOptions.value = if (activeLocalSourceOptions.value.isNotEmpty()) {
			activeLocalSourceOptions.value.map { option ->
				DetailsSourceOption(
					key = "reading:${option.mangaId}",
					source = option.source,
					targetMangaId = option.mangaId,
					isSelected = option.isActive,
				)
			}
		} else {
			val source = currentDisplayedDetails?.local?.manga?.source
				?: baseLoadedDetails
					?.toContent()
					?.source
					?.takeUnless { it.name.startsWith("TRACKING_") }
			source?.let {
				listOf(
					DetailsSourceOption(
						key = "reading:${it.name}",
						source = it,
						isSelected = true,
					),
				)
			}.orEmpty()
		}
		updateChapterSourceTabs()
	}

	private fun updateChapterSourceTabs() {
		val trackingSelection = selectedMetadataSource.value as? MetadataSourceSelection.Tracking
		metadataChapterTabs.value = if (trackingSelection != null) {
			trackingMetadataCandidates.value.map { candidate ->
				val details = cachedTrackingDetails[trackingMetadataKey(candidate.service, candidate.remoteId)]
				val contentType = details?.contentType ?: currentMetadataContentType() ?: ContentType.MANGA
				val locale = details?.let { resolveTrackingLanguage(it.infoboxProperties) }.orEmpty()
				val source = syntheticSource(
					name = "TRACKING_${candidate.service.name}",
					contentType = contentType,
					locale = locale,
				)
				DetailsChapterSourceTab(
					key = trackingMetadataKey(candidate.service, candidate.remoteId),
					source = source,
					trackingService = candidate.service,
					remoteId = candidate.remoteId,
					url = candidate.url ?: details?.url,
					chapters = details?.let { trackingDetailsToSyntheticChapters(it, source) }.orEmpty(),
					isSelected = trackingSelection.service == candidate.service &&
						trackingSelection.remoteId == candidate.remoteId,
				)
			}
		} else {
			emptyList()
		}

		readingChapterTabs.value = readingSourceOptions.value.map { option ->
			DetailsChapterSourceTab(
				key = option.key,
				source = option.source,
				targetMangaId = option.targetMangaId,
				url = option.url,
				isSelected = option.isSelected,
			)
		}
	}

	private fun updateTrackingDetailsPanels(
		details: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails?,
	) {
		trackingMetadataProperties.value = details?.infoboxProperties.orEmpty()
		if (details == null) {
			trackingDetailsSections.value = emptyList()
			trackingDetailsActions.value = emptyList()
			trackingCommentThreads.value = emptyList()
			trackingCommentsUrl.value = null
			trackingReviews.value = emptyList()
			trackingReviewsUrl.value = null
			return
		}
		val commentsAction = details.actions.firstOrNull { action ->
			action.url.contains("/comments") || action.title.contains("吐槽")
		}
		val reviewsAction = details.actions.firstOrNull { action ->
			action.url.contains("/reviews") ||
				action.title.contains("长评") ||
				action.title.contains("评论")
		}
		trackingCommentsUrl.value = commentsAction?.url
		trackingCommentThreads.value = details.commentThreads
		trackingReviewsUrl.value = reviewsAction?.url
		trackingReviews.value = details.reviews
		trackingDetailsActions.value = details.actions.filterNot { action ->
			action == commentsAction || action == reviewsAction
		}.map { action ->
			TrackingDetailsAction(
				title = action.title,
				url = action.url,
			)
		}
		trackingDetailsSections.value = buildList {
			details.relatedWorks
				.takeIf { it.isNotEmpty() }
				?.let { works ->
					add(
						TrackingDetailsSection(
							titleRes = R.string.details_related_works,
							items = works.map { work ->
								TrackingDetailsSectionItem(
									service = details.service,
									remoteId = work.id,
									title = work.title,
									coverUrl = work.coverUrl.normalizedImageUrl(),
									subtitle = work.relationship,
									url = work.url,
								)
							},
						),
					)
				}
			details.recommendations
				.takeIf { it.isNotEmpty() }
				?.let { works ->
					add(
						TrackingDetailsSection(
							titleRes = R.string.details_recommendations,
							items = works.map { work ->
								TrackingDetailsSectionItem(
									service = details.service,
									remoteId = work.id,
									title = work.title,
									coverUrl = work.coverUrl.normalizedImageUrl(),
									subtitle = work.relationship,
									url = work.url,
								)
							},
						),
					)
				}
			details.extraSections.forEach { section ->
				if (section.items.isEmpty()) {
					return@forEach
				}
				add(
					TrackingDetailsSection(
						title = section.title,
						items = section.items.map { work ->
							TrackingDetailsSectionItem(
								service = details.service,
								remoteId = work.id,
								title = work.title,
								coverUrl = work.coverUrl.normalizedImageUrl(),
								subtitle = work.relationship,
								url = work.url,
							)
						},
					),
				)
			}
		}
	}

	private fun refreshContextualEntityRelations() {
		launchJob(Dispatchers.IO) {
			val entityId = resolveContextualEntityId()
			val sections = if (entityId != null) {
				buildEntityRelationSections(entityId)
			} else {
				emptyList()
			}
			submitEntityRelationSections(sections)
		}
	}

	private suspend fun resolveContextualEntityId(): Long? {
		val currentSelection = selectedMetadataSource.value
		if (currentSelection is MetadataSourceSelection.Tracking) {
			entityGraphRepository.findEntityByBinding(
				source = currentSelection.service.id.toString(),
				externalId = currentSelection.remoteId.toString(),
			)?.let { return it.id }
		}
		val localMangaId = activeMangaIdFlow.value ?: baseLoadedDetails?.id ?: return null
		entityGraphRepository.findEntityByBinding("0", localMangaId.toString())?.let { return it.id }
		return entityGraphRepository.findEntityByBinding("local_manga", localMangaId.toString())?.id
	}

	private suspend fun ensureTrackingDetailsLoaded(
		service: ScrobblerService,
		remoteId: Long,
		url: String?,
	) {
		val cacheKey = trackingMetadataKey(service, remoteId)
		if (cachedTrackingDetails.containsKey(cacheKey)) {
			return
		}
		trackingSiteCacheRepository.readDetails(service, remoteId)?.let { cached ->
			cacheTrackingDetails(cached)
			ingestTrackingDetailsIntoEntityGraph(cached)
			if (cached.hasRichMetadata()) {
				val currentSelection = selectedMetadataSource.value
				if (currentSelection is MetadataSourceSelection.Tracking &&
					currentSelection.service == service &&
					currentSelection.remoteId == remoteId
				) {
					syncDisplayedState()
				}
				return
			}
		}
		val details = runCatching {
			trackingSiteDiscoveryService.getDetails(service, remoteId, url)
		}.getOrNull() ?: return
		cacheTrackingDetails(details)
		ingestTrackingDetailsIntoEntityGraph(details)
		trackingSiteCacheRepository.saveDetails(details)
		val currentSelection = selectedMetadataSource.value
		if (currentSelection is MetadataSourceSelection.Tracking &&
			currentSelection.service == service &&
			currentSelection.remoteId == remoteId
		) {
			syncDisplayedState()
		}
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
					coverUrl = cached?.coverUrl.normalizedImageUrl() ?: scrobbling?.coverUrl.normalizedImageUrl(),
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

	private suspend fun buildEntityRelationSections(entityId: Long): List<EntityRelationSection> {
		val anchorEntity = entityGraphRepository.getEntity(entityId) ?: return emptyList()
		val relations = entityGraphRepository.getRelations(entityId)
		if (relations.isEmpty()) {
			return emptyList()
		}
		val trackingDetails = currentTrackingMetadataDetails()
		val relatedIds = relations.mapNotNull { relation ->
			relation.toEntityId.takeIf { it != entityId } ?: relation.fromEntityId.takeIf { it != entityId }
		}.distinct()
		val relatedEntities = entityGraphRepository.getEntitiesByIds(relatedIds).associateBy(Entity::id)
		return relations.groupBy(Relation::type).mapNotNull { (relationType, typeRelations) ->
			if (anchorEntity.type == EntityType.WORK && relationType == RelationType.BELONGS_TO) {
				return@mapNotNull null
			}
			val items = typeRelations.mapNotNull { relation ->
				val relatedId = relation.relatedEntityId(entityId) ?: return@mapNotNull null
				val related = relatedEntities[relatedId] ?: return@mapNotNull null
				EntityRelationItem(
					entityId = related.id,
					name = related.primaryName,
					type = related.type,
					coverUrl = resolveEntityCoverUrl(related.id)
						?: resolveTrackingRelatedCoverUrl(related, trackingDetails),
				)
			}.distinctBy(EntityRelationItem::entityId)
			val titleRes = relationSectionTitleRes(anchorEntity.type, typeRelations.first().type) ?: return@mapNotNull null
			items.takeIf { it.isNotEmpty() }?.let {
				EntityRelationSection(
					titleRes = titleRes,
					items = it,
				)
			}
		}
	}

	private suspend fun resolveTrackingRelatedCoverUrl(
		entity: Entity,
		details: org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails?,
	): String? {
		if (details == null || entity.type != EntityType.CHARACTER) {
			return null
		}
		val binding = entityGraphRepository.getBindings(entity.id).firstOrNull {
			it.source == details.service.id.toString()
		} ?: return null
		val characterId = binding.externalId.toLongOrNull() ?: return null
		return details.characters.firstOrNull { it.id == characterId }?.coverUrl.normalizedImageUrl()
	}

	private suspend fun resolveEntityCoverUrl(entityId: Long): String? {
		val bindings = entityGraphRepository.getBindings(entityId)
			.sortedWith(compareByDescending<EntityBinding> { it.isPrimary }.thenByDescending { it.confidence })
		for (binding in bindings) {
			resolveBindingCoverUrl(binding)?.let { return it }
		}
		return null
	}

	private suspend fun resolveBindingCoverUrl(binding: EntityBinding): String? {
		if (binding.source == "0" || binding.source == "local_manga") {
			val localMangaId = binding.externalId.toLongOrNull() ?: return null
			val localManga = db.getMangaDao().find(localMangaId)?.manga ?: return null
			return localManga.largeCoverUrl.ifNullOrEmpty { localManga.coverUrl }.normalizedImageUrl()
		}
		val serviceId = binding.source.toIntOrNull() ?: return null
		val remoteId = binding.externalId.toLongOrNull() ?: return null
		val service = ScrobblerService.entries.firstOrNull { it.id == serviceId } ?: return null
		return trackingSiteCacheRepository.readDetails(service, remoteId)?.coverUrl.normalizedImageUrl()
	}

	private fun relationSectionTitleRes(
		anchorType: EntityType,
		relationType: RelationType,
	): Int? = when (relationType) {
		RelationType.HAS_CHARACTER -> when (anchorType) {
			EntityType.CHARACTER -> null
			else -> R.string.entity_graph_section_characters
		}
		RelationType.CREATED_BY -> when (anchorType) {
			EntityType.PERSON, EntityType.ORGANIZATION -> R.string.entity_graph_section_created_works
			else -> R.string.entity_graph_section_creators
		}
		RelationType.RELATED_TO -> R.string.entity_graph_section_related_entities
		RelationType.VOICED_BY -> when (anchorType) {
			EntityType.PERSON -> R.string.entity_graph_section_voiced_characters
			else -> R.string.entity_graph_section_voice_actors
		}
		RelationType.BELONGS_TO -> R.string.entity_graph_section_parent_work
	}

	private fun Relation.relatedEntityId(anchorEntityId: Long): Long? {
		return toEntityId.takeIf { it != anchorEntityId } ?: fromEntityId.takeIf { it != anchorEntityId }
	}

	private suspend fun buildActiveLocalSourceOptions(
		bindings: List<EntityBinding>,
		activeMangaId: Long?,
	): List<ActiveLocalSourceOption> {
		val localMangaIds = bindings.asSequence()
			.filter { it.source == "0" || it.source == "local_manga" }
			.mapNotNull { it.externalId.toLongOrNull() }
			.distinct()
			.toList()
		if (localMangaIds.size <= 1) {
			return emptyList()
		}
		return localMangaIds.mapNotNull { localMangaId ->
			val manga = db.getMangaDao().find(localMangaId)?.manga ?: return@mapNotNull null
			ActiveLocalSourceOption(
				mangaId = localMangaId,
				title = manga.title,
				source = ContentSource(manga.source),
				isActive = localMangaId == activeMangaId,
			)
		}
	}

	private fun updateActiveLocalSourceSelection(activeMangaId: Long) {
		activeLocalSourceOptions.value = activeLocalSourceOptions.value.map { option ->
			option.copy(isActive = option.mangaId == activeMangaId)
		}
		updateSourceOptions()
	}

	private fun submitEntityRelationSections(sections: List<EntityRelationSection>) {
		pendingEntityRelationSections.tryEmit(sections)
	}

	private suspend fun resolveEntityChapterSourceInfo(mangaId: Long?): EntityChapterSourceInfo {
		val source = mangaId?.let { localMangaId ->
			db.getMangaDao().find(localMangaId)?.manga?.source?.let(::ContentSource)
		}
		return EntityChapterSourceInfo(source = source)
	}

	fun selectActiveLocalSource(mangaId: Long) {
		if (activeLocalSourceOptions.value.none { it.mangaId == mangaId } || activeMangaIdFlow.value == mangaId) {
			return
		}
		val shouldFollowSelectedLocalSource = selectedMetadataSource.value !is MetadataSourceSelection.Tracking
		currentLoadIntentOverride = ContentIntent.of(mangaId)
		activeMangaIdFlow.value = mangaId
		selectedBranch.value = null
		if (shouldFollowSelectedLocalSource) {
			selectedMetadataSource.value = MetadataSourceSelection.Base
		}
		updateActiveLocalSourceSelection(mangaId)
		syncDisplayedState()
		loadingJob.cancel()
		launchJob(Dispatchers.IO) {
			persistMetadataSourceSelection(mangaId)
			entityChapterSourceInfo.value = resolveEntityChapterSourceInfo(mangaId)
			loadingJob = doLoad(force = false)
		}
	}

	fun selectMetadataSource(option: DetailsSourceOption) {
		when {
			option.trackingService != null && option.remoteId != null -> {
				val activeLocalMangaId = activeMangaIdFlow.value
				val nextSelection = MetadataSourceSelection.Tracking(
					service = option.trackingService,
					remoteId = option.remoteId,
					url = option.url,
				)
				if (selectedMetadataSource.value == nextSelection) {
					return
				}
				selectedMetadataSource.value = nextSelection
				selectedMetadataSearchService.value = option.trackingService
				syncDisplayedState()
				launchJob(Dispatchers.IO) {
					if (activeLocalMangaId != null) {
						persistMetadataSourceSelection(activeLocalMangaId)
					}
					ensureTrackingDetailsLoaded(
						service = option.trackingService,
						remoteId = option.remoteId,
						url = option.url,
					)
				}
			}
			option.source != null -> {
				val activeLocalMangaId = activeMangaIdFlow.value
				if (selectedMetadataSource.value == MetadataSourceSelection.Base) {
					return
				}
				selectedMetadataSource.value = MetadataSourceSelection.Base
				syncDisplayedState()
				launchJob(Dispatchers.IO) {
					if (activeLocalMangaId != null) {
						persistMetadataSourceSelection(activeLocalMangaId)
					}
				}
			}
		}
	}

	fun setMetadataSearchService(service: ScrobblerService) {
		if (selectedMetadataSearchService.value == service) {
			return
		}
		selectedMetadataSearchService.value = service
	}

	fun updateMetadataSearchQuery(query: String) {
		metadataSearchQuery.value = query
	}

	fun searchMetadataBindings() {
		val service = selectedMetadataSearchService.value
		val query = metadataSearchQuery.value.trim().ifBlank { currentDetailsTitle() }
		launchJob(Dispatchers.IO) {
			metadataSearchLoading.value = true
			metadataSearchError.value = null
			val result = runCatchingCancellable {
				trackingSiteDiscoveryService.search(
					TrackingSiteCatalog(
						service = service,
						query = query.ifBlank { null },
						contentType = currentDetailsContentType(),
					),
				)
			}
			metadataSearchLoading.value = false
			result.onSuccess {
				metadataSearchResults.value = it
			}.onFailure {
				metadataSearchResults.value = emptyList()
				metadataSearchError.value = it.localizedMessage ?: it.javaClass.simpleName
			}
		}
	}

	fun bindMetadataSource(item: TrackingSiteItem) {
		launchJob(Dispatchers.IO) {
			trackingMetadataCandidates.value = mergeTrackingMetadataCandidates(
				trackingMetadataCandidates.value + TrackingMetadataCandidate(
					service = item.service,
					remoteId = item.remoteId,
					url = item.url,
				),
			)
			selectedMetadataSource.value = MetadataSourceSelection.Tracking(
				service = item.service,
				remoteId = item.remoteId,
				url = item.url,
			)
			val details = runCatchingCancellable {
				trackingSiteDiscoveryService.getDetails(item.service, item.remoteId, item.url)
			}.getOrNull()
			if (details != null) {
				cacheTrackingDetails(details)
				ingestTrackingDetailsIntoEntityGraph(details)
				trackingSiteCacheRepository.saveDetails(details)
			}
			val localMangaId = activeMangaIdFlow.value
			if (localMangaId != null) {
				trackingSiteMatcher.confirmMatch(item.service, localMangaId, item.remoteId)
				persistMetadataSourceSelection(localMangaId)
				dataRepository.setIgnoredTrackingSuggestion(localMangaId, null)
				autoLinkTrackingServiceIfAuthorized(
					mangaId = localMangaId,
					item = item,
				)
			}
			syncDisplayedState()
		}
	}

	fun setReadingSearchSource(sourceName: String) {
		if (selectedReadingSearchSource.value == sourceName) {
			return
		}
		selectedReadingSearchSource.value = sourceName
	}

	fun updateReadingSearchQuery(query: String) {
		readingSearchQuery.value = query
	}

	fun searchReadingBindings() {
		val sourceName = selectedReadingSearchSource.value ?: return
		val sourceInfo = readingSearchSources.value.firstOrNull { it.mangaSource.name == sourceName } ?: return
		val query = readingSearchQuery.value.trim().ifBlank { currentDetailsTitle() }
		launchJob(Dispatchers.IO) {
			readingSearchState.value = LocalSearchState.Loading
			val result = runCatchingCancellable {
				mangaRepositoryFactory.create(sourceInfo.mangaSource).getList(
					offset = 0,
					order = SortOrder.RELEVANCE,
					filter = ContentListFilter(query = query),
				).take(20)
			}
			readingSearchState.value = result.fold(
				onSuccess = { LocalSearchState.Loaded(it) },
				onFailure = { LocalSearchState.Error(it) },
			)
		}
	}

	fun bindReadingCandidateToTracking(content: Content, onComplete: (() -> Unit)? = null) {
		val selection = selectedMetadataSource.value as? MetadataSourceSelection.Tracking
		if (selection == null) {
			onComplete?.invoke()
			return
		}
		launchJob(Dispatchers.IO + SkipErrors) {
			try {
				// MangaPrefsEntity has a FK to MangaEntity; persist the candidate first
				// so setMetadataSourceSelection can't fail with a foreign key constraint
				// on a brand-new candidate that has never been opened.
				runCatchingCancellable {
					dataRepository.storeContent(content, replaceExisting = false)
				}
				runCatchingCancellable {
					trackingSiteMatcher.confirmMatch(selection.service, content.id, selection.remoteId)
				}
				runCatchingCancellable {
					dataRepository.setMetadataSourceSelection(
						mangaId = content.id,
						selection = PersistedMetadataSourceSelection.Tracking(
							serviceId = selection.service.id,
							remoteId = selection.remoteId,
						),
					)
				}
			} finally {
				withContext(Dispatchers.Main) {
					onComplete?.invoke()
				}
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
			dataRepository.setIgnoredTrackingSuggestion(content.id, null)
			refreshTrackingMatchSuggestion()
		}
	}

	fun ignoreTrackingSuggestion(match: TrackingSiteMatchResult) {
		launchJob(Dispatchers.Default) {
			val content = manga.filterNotNull().firstOrNull() ?: return@launchJob
			dataRepository.setIgnoredTrackingSuggestion(
				mangaId = content.id,
				suggestion = org.skepsun.kototoro.core.parser.ContentDataRepository.IgnoredTrackingSuggestion(
					serviceId = match.service.id,
					remoteId = match.remoteId,
				),
			)
			trackingMatchSuggestion.value = null
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
			val manga = baseLoadedDetails?.toContent()
				?: mangaDetails.value?.local?.manga
				?: mangaDetails.value?.toContent()?.takeUnless {
					selectedMetadataSource.value is MetadataSourceSelection.Tracking && activeMangaIdFlow.value == null
				}
				?: return@launchJob
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
		detailsLoadUseCase.invoke(currentLoadIntentOverride ?: intent, force)
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
				baseLoadedDetails = finalDetails
				syncDisplayedState()
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
			if (selectedMetadataSource.value is MetadataSourceSelection.Tracking || linkedTrackingItems.value.isNotEmpty()) {
				trackingMatchSuggestion.value = null
				return@launchJob
			}
			val ignored = dataRepository.getIgnoredTrackingSuggestion(content.id)
			val suggestions = findTrackingSuggestions(content)
			val best = selectTrackingSuggestion(
				candidates = suggestions,
				preferredService = settings.preferredTrackingSite,
				ignored = ignored,
			)
			trackingMatchSuggestion.value = best
		}
	}

	private suspend fun findTrackingSuggestions(content: Content): List<TrackingSiteMatchResult> {
		val services = candidateTrackingSuggestionServices()
		if (services.isEmpty()) {
			return emptyList()
		}
		return services.flatMap { service ->
			runCatchingCancellable {
				withTimeout(8_000) {
					trackingSiteMatcher.matchLocalContent(
						service = service,
						content = content,
						limit = TRACKING_SUGGESTION_RESULT_LIMIT,
						persistAutoMatch = false,
					)
				}
			}.getOrElse { emptyList() }
		}.distinctBy { "${it.service.id}:${it.remoteId}" }
	}

	private fun candidateTrackingSuggestionServices(): List<ScrobblerService> {
		val searchable = metadataSearchServices.value.ifEmpty {
			ScrobblerService.entries.filter { service ->
				trackingSiteDiscoveryService.getCapabilities(service).supportsSearch
			}
		}
		val preferred = settings.preferredTrackingSite
		return buildList {
			searchable.firstOrNull { it == preferred }?.let(::add)
			addAll(searchable.filterNot { it == preferred })
		}
	}

	private fun selectTrackingSuggestion(
		candidates: List<TrackingSiteMatchResult>,
		preferredService: ScrobblerService,
		ignored: org.skepsun.kototoro.core.parser.ContentDataRepository.IgnoredTrackingSuggestion?,
	): TrackingSiteMatchResult? {
		val filtered = candidates
			.asSequence()
			.filterNot(TrackingSiteMatchResult::isLinked)
			.filter { it.confidence >= TRACKING_SUGGESTION_THRESHOLD }
			.filterNot { candidate ->
				ignored?.serviceId == candidate.service.id && ignored.remoteId == candidate.remoteId
			}
			.sortedWith(
				compareByDescending<TrackingSiteMatchResult> { it.confidence }
					.thenByDescending { it.service == preferredService }
					.thenBy { it.title },
			)
			.toList()
		val best = filtered.firstOrNull() ?: return null
		val runnerUp = filtered.getOrNull(1) ?: return best
		return if (best.confidence - runnerUp.confidence >= TRACKING_SUGGESTION_GAP_THRESHOLD) {
			best
		} else {
			null
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

	private suspend fun autoLinkTrackingServiceIfAuthorized(
		mangaId: Long,
		item: TrackingSiteItem,
	) {
		val scrobbler = scrobblers.firstOrNull {
			it.scrobblerService == item.service && it.isEnabled
		} ?: return
		val manga = dataRepository.findContentById(mangaId, withChapters = false)
			?: manga.filterNotNull().firstOrNull { it.id == mangaId }
			?: return
		val previous = scrobbler.getScrobblingInfoOrNull(mangaId)
		scrobbler.linkContent(mangaId, item.toScrobblerContent())
		val history = historyRepository.getOne(manga)
		scrobbler.updateScrobblingInfo(
			mangaId = mangaId,
			rating = previous?.rating ?: 0f,
			status = previous?.status ?: when {
				history == null -> ScrobblingStatus.PLANNED
				org.skepsun.kototoro.list.domain.ReadingProgress.isCompleted(history.percent) -> ScrobblingStatus.COMPLETED
				else -> ScrobblingStatus.READING
			},
			comment = previous?.comment,
		)
		if (history != null) {
			scrobbler.tryScrobble(manga, history.chapterId)
		}
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
