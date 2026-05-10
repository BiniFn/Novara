package org.skepsun.kototoro.cloudstream.runtime

import android.util.Log
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.cloudstream.model.CloudstreamSource
import org.skepsun.kototoro.core.cache.MemoryContentCache
import org.skepsun.kototoro.core.parser.CachingContentRepository
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentExternalTrack
import org.skepsun.kototoro.parsers.model.ContentListFilter
import org.skepsun.kototoro.parsers.model.ContentListFilterCapabilities
import org.skepsun.kototoro.parsers.model.ContentListFilterOptions
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.parsers.model.ContentRating
import org.skepsun.kototoro.parsers.model.ContentState
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.parsers.model.ContentTagGroup
import org.skepsun.kototoro.parsers.model.RATING_UNKNOWN
import org.skepsun.kototoro.parsers.model.SortOrder

@OptIn(Prerelease::class)
class CloudstreamContentRepository(
	override val source: CloudstreamSource,
	cache: MemoryContentCache,
) : CachingContentRepository(cache) {

	override val sortOrders: Set<SortOrder> = setOf(SortOrder.RELEVANCE)

	override var defaultSortOrder: SortOrder = SortOrder.RELEVANCE

	override val filterCapabilities: ContentListFilterCapabilities = ContentListFilterCapabilities(
		isSearchSupported = true,
	)

	override suspend fun getList(offset: Int, order: SortOrder?, filter: ContentListFilter?): List<Content> {
		val query = filter?.query?.trim().orEmpty()
		Log.d(
			TAG,
			"getList source=${source.displayName} offset=$offset order=$order query=${query.takeIf { it.isNotBlank() }} " +
				"hasMainPage=${source.api.hasMainPage} mainPageCount=${source.api.mainPage.size} filter=$filter",
		)
		if (query.isBlank()) {
			if (source.api.hasMainPage) {
				return loadMainPage(offset, filter)
			}
			Log.w(
				TAG,
				"getList returning empty because query is blank for source=${source.displayName} hasMainPage=${source.api.hasMainPage}",
			)
			return emptyList()
		}
		val page = (offset + 1).coerceAtLeast(1)
		val result = withContext(Dispatchers.IO) {
			source.api.search(query, page)
		}
		if (result == null) {
			Log.w(TAG, "search returned null source=${source.displayName} query=$query page=$page")
			return emptyList()
		}
		Log.d(
			TAG,
			"search result source=${source.displayName} query=$query page=$page items=${result.items.size} hasNext=${result.hasNext}",
		)
		return result.items.mapIndexed { index, item ->
			item.toKotoContent(source, page, index)
		}
	}

	override suspend fun getDetailsImpl(manga: Content): Content {
		val response = withContext(Dispatchers.IO) { source.api.load(manga.url) } ?: return manga
		val chapters = response.toChapters(source)
		return manga.copy(
			title = response.name.ifBlank { manga.title },
			publicUrl = response.url.ifBlank { manga.publicUrl },
			rating = response.score.toKotoRating() ?: manga.rating,
			contentRating = response.contentRating.toKotoContentRating() ?: manga.contentRating,
			coverUrl = response.posterUrl ?: manga.coverUrl,
			largeCoverUrl = response.backgroundPosterUrl ?: response.posterUrl ?: manga.largeCoverUrl,
			description = response.plot ?: manga.description,
			tags = response.tags.orEmpty()
				.map { ContentTag(it, it, source) }
				.toSet()
				.ifEmpty { manga.tags },
			state = response.toKotoState() ?: manga.state,
			authors = manga.authors,
			chapters = chapters,
		)
	}

	override suspend fun getPagesImpl(chapter: ContentChapter, nextChapterUrl: String?): List<ContentPage> {
		val links = resolveVideoPages(chapter)
		if (links.isNotEmpty()) {
			return links
		}
		if (chapter.url.isDirectPlayableUrl()) {
			Log.w(
				TAG,
				"loadLinks empty, falling back to direct url source=${source.displayName} chapterId=${chapter.id} url=${chapter.url}",
			)
			return listOf(
				ContentPage(
					id = chapter.id,
					url = chapter.url,
					preview = null,
					source = source,
				),
			)
		}
		Log.w(
			TAG,
			"loadLinks resolved no playable links source=${source.displayName} chapterId=${chapter.id} url=${chapter.url}",
		)
		return emptyList()
	}

	override suspend fun getPageUrl(page: ContentPage): String = page.url

	override suspend fun getFilterOptions(): ContentListFilterOptions {
		val sectionTags = source.api.mainPage
			.mapIndexedNotNull { index, page ->
				page.name.takeIf { it.isNotBlank() }?.let { name ->
					ContentTag(
						title = name,
						key = sectionTagKey(index),
						source = source,
					)
				}
			}
			.toSet()
		if (sectionTags.isEmpty()) {
			return ContentListFilterOptions()
		}
		return ContentListFilterOptions(
			availableTags = sectionTags,
			tagGroups = listOf(
				ContentTagGroup(
					title = "分区",
					tags = sectionTags,
					isExclusive = true,
				),
			),
		)
	}

	override suspend fun getRelatedContentImpl(seed: Content): List<Content> = emptyList()

	private fun SearchResponse.toKotoContent(
		source: CloudstreamSource,
		page: Int,
		index: Int,
	): Content {
		val type = type ?: TvType.Movie
		return Content(
			id = stableId("${source.name}|$url|$page|$index"),
			title = name,
			altTitles = buildSet {
				if (this@toKotoContent is AnimeSearchResponse) {
					otherName?.takeIf { it.isNotBlank() }?.let(::add)
				}
			},
			url = url,
			publicUrl = url,
			rating = score.toKotoRating() ?: RATING_UNKNOWN,
			contentRating = null,
			coverUrl = posterUrl,
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			largeCoverUrl = posterUrl,
			description = null,
			chapters = buildPreviewChapters(type, source, this),
			source = source,
		)
	}

	private fun buildPreviewChapters(
		type: TvType,
		source: CloudstreamSource,
		response: SearchResponse,
	): List<ContentChapter>? {
		if (!type.isMovieType()) return null
		return listOf(
			ContentChapter(
				id = stableId("${source.name}|movie|${response.url}"),
				title = response.name,
				number = 1f,
				volume = 1,
				url = response.url,
				scanlator = null,
				uploadDate = 0L,
				branch = null,
				source = source,
			),
		)
	}

	private fun LoadResponse.toChapters(source: CloudstreamSource): List<ContentChapter> {
		if (this is MovieLoadResponse) {
			return listOf(
				ContentChapter(
					id = stableId("${source.name}|movie|$dataUrl"),
					title = name,
					number = 1f,
					volume = 1,
					url = dataUrl,
					scanlator = null,
					uploadDate = 0L,
					branch = null,
					source = source,
				),
			)
		}

		val episodes = when (this) {
			is TvSeriesLoadResponse -> episodes
			is AnimeLoadResponse -> episodes.values.flatten()
			else -> emptyList()
		}
		if (episodes.isEmpty()) {
			return listOf(
				ContentChapter(
					id = stableId("${source.name}|fallback|$url"),
					title = name,
					number = 1f,
					volume = 1,
					url = url,
					scanlator = null,
					uploadDate = 0L,
					branch = null,
					source = source,
				),
			)
		}
		return episodes.mapIndexed { index, episode ->
			ContentChapter(
				id = stableId("${source.name}|${episode.data}|$index"),
				title = episode.name,
				number = (episode.episode ?: (index + 1)).toFloat(),
				volume = episode.season ?: 1,
				url = episode.data,
				scanlator = null,
				uploadDate = episode.date ?: 0L,
				branch = resolveBranch(this, episode),
				source = source,
			)
		}
	}

	private fun resolveBranch(response: LoadResponse, episode: com.lagradost.cloudstream3.Episode): String? {
		if (response !is AnimeLoadResponse) return null
		return response.episodes.entries.firstOrNull { (_, value) -> episode in value }?.key
			?.takeUnless { it == DubStatus.None }
			?.name
	}

	private fun LoadResponse.toKotoState(): ContentState? {
		return null
	}

	private fun String?.toKotoContentRating(): ContentRating? {
		return this?.takeIf { it.contains("18", true) || it.contains("adult", true) }?.let {
			ContentRating.ADULT
		}
	}

	private fun Score?.toKotoRating(): Float? {
		return this?.toInt(100)?.div(100f)
	}

	private fun stableId(value: String): Long {
		return value.hashCode().toLong() and Long.MAX_VALUE
	}

	private fun sectionTagKey(index: Int): String = "$SECTION_TAG_PREFIX$index"

	private fun parseSectionTagIndex(key: String): Int? {
		if (!key.startsWith(SECTION_TAG_PREFIX)) return null
		return key.removePrefix(SECTION_TAG_PREFIX).toIntOrNull()
	}

	private suspend fun resolveVideoPages(chapter: ContentChapter): List<ContentPage> {
		Log.d(
			TAG,
			"loadLinks start source=${source.displayName} chapterId=${chapter.id} chapterTitle=${chapter.title} locator=${chapter.url}",
		)
		val subtitles = ArrayList<SubtitleFile>()
		val links = ArrayList<ExtractorLink>()
		val success = runCatching {
			withContext(Dispatchers.IO) {
				source.api.loadLinks(
					data = chapter.url,
					isCasting = false,
					subtitleCallback = { subtitle ->
						subtitles += subtitle
					},
					callback = { link ->
						links += link
						Log.d(
							TAG,
							"loadLinks link source=${source.displayName} chapterId=${chapter.id} name=${link.name} " +
								"type=${link.type} quality=${link.quality} url=${link.url} headers=${link.getAllHeaders().keys}",
						)
					},
				)
			}
		}.onFailure { error ->
			Log.e(
				TAG,
				"loadLinks failed source=${source.displayName} chapterId=${chapter.id} url=${chapter.url}",
				error,
			)
		}.getOrDefault(false)
		val pages = links
			.distinctBy { it.url to it.getAllHeaders() }
			.sortedWith(compareByDescending<ExtractorLink> { it.url.contains("/config-", ignoreCase = true) }
				.thenByDescending { it.url.contains("master.m3u8", ignoreCase = true) }
				.thenByDescending { it.url.contains("/playlist.m3u8", ignoreCase = true) }
				.thenByDescending { it.quality })
			.mapIndexed { index, link ->
				ContentPage(
					id = stableId("${chapter.id}|${link.name}|${link.url}|$index"),
					url = link.url,
					preview = null,
					headers = link.getAllHeaders()
						.toMutableMap()
						.apply {
							putIfAbsent("User-Agent", USER_AGENT)
						}
						.takeIf { it.isNotEmpty() },
						externalSubtitleTracks = subtitles.map { subtitle ->
							ContentExternalTrack(
								url = subtitle.url,
								lang = subtitle.lang,
								headers = subtitle.headers,
							)
						},
						playbackLabel = link.name.takeIf { it.isNotBlank() },
						playbackQuality = link.quality.takeIf { it > 0 },
						source = source,
					)
				}
		Log.d(
			TAG,
			"loadLinks done source=${source.displayName} chapterId=${chapter.id} success=$success links=${pages.size} " +
				"subtitles=${subtitles.size} selected=${pages.firstOrNull()?.url}",
		)
		return pages
	}

	private fun String.isDirectPlayableUrl(): Boolean {
		if (!startsWith("http://") && !startsWith("https://")) {
			return false
		}
		val lower = lowercase()
		return lower.contains(".m3u8") ||
			lower.contains(".mp4") ||
			lower.contains(".mkv") ||
			lower.contains(".webm") ||
			lower.contains(".mpd")
	}

	private suspend fun logMainPageProbe(requestIndex: Int) {
		val request = source.api.mainPage.getOrNull(requestIndex)?.let { page ->
			MainPageRequest(page.name, page.data, page.horizontalImages)
		} ?: source.api.mainPage.firstOrNull()?.let { page ->
			MainPageRequest(page.name, page.data, page.horizontalImages)
		}
		if (request == null) {
			Log.w(TAG, "main page probe skipped source=${source.displayName} because mainPage is empty")
			return
		}
		runCatching {
			withContext(Dispatchers.IO) {
				source.api.getMainPage(1, request)
			}
		}.onSuccess { response ->
			logMainPageProbeResult(request, response)
		}.onFailure { error ->
			Log.e(
				TAG,
				"main page probe failed source=${source.displayName} requestName=${request.name} requestData=${request.data}",
				error,
			)
		}
	}

	private suspend fun loadMainPage(offset: Int, filter: ContentListFilter?): List<Content> {
		val mainPages = source.api.mainPage
		if (mainPages.isEmpty()) {
			Log.w(TAG, "main page load skipped source=${source.displayName} because mainPage is empty")
			return emptyList()
		}
		val page = (offset + 1).coerceAtLeast(1)
		val selectedSectionIndex = filter?.tags
			?.firstNotNullOfOrNull { tag -> parseSectionTagIndex(tag.key) }
			?.takeIf { it in mainPages.indices }
		val requestIndex = selectedSectionIndex ?: (offset % mainPages.size)
		val requestPage = if (selectedSectionIndex != null) {
			val sectionPageSize = probeMainPageSize(mainPages[requestIndex]).coerceAtLeast(1)
			(offset / sectionPageSize) + 1
		} else {
			(offset / mainPages.size) + 1
		}
		logMainPageProbe(requestIndex)
		val requests = listOf(mainPages[requestIndex])
		val aggregated = ArrayList<SearchResponse>()
		requests.forEachIndexed { requestIndex, page ->
			val request = MainPageRequest(page.name, page.data, page.horizontalImages)
			val response = runCatching {
				withContext(Dispatchers.IO) {
					source.api.getMainPage(page = requestPage, request = request)
				}
			}.onFailure { error ->
				Log.e(
					TAG,
					"main page load failed source=${source.displayName} requestName=${request.name} requestData=${request.data} " +
						"slot=$requestIndex page=$requestPage",
					error,
				)
			}.getOrNull() ?: return@forEachIndexed
			Log.d(
				TAG,
				"main page load source=${source.displayName} requestName=${request.name} requestData=${request.data} " +
					"slot=$requestIndex page=$requestPage rows=${response.items.size} hasNext=${response.hasNext}",
			)
			response.items.forEach { row ->
				aggregated += row.list
			}
		}
		val deduped = aggregated.distinctBy { it.url }
		Log.d(
			TAG,
			"main page aggregated source=${source.displayName} page=$page slot=$requestIndex slotPage=$requestPage " +
				"requestCount=${requests.size} items=${deduped.size} selectedSectionIndex=$selectedSectionIndex",
		)
		return deduped.mapIndexed { index, item ->
			item.toKotoContent(source, page, index)
		}
	}

	private fun logMainPageProbeResult(request: MainPageRequest, response: HomePageResponse?) {
		if (response == null) {
			Log.w(
				TAG,
				"main page probe returned null source=${source.displayName} requestName=${request.name} requestData=${request.data}",
			)
			return
		}
		val rowSummary = response.items.joinToString(limit = 3) { row ->
			"${row.name}:${row.list.size}"
		}
		Log.d(
			TAG,
			"main page probe source=${source.displayName} requestName=${request.name} requestData=${request.data} " +
				"rows=${response.items.size} hasNext=${response.hasNext} sample=$rowSummary",
		)
	}

	private suspend fun probeMainPageSize(page: com.lagradost.cloudstream3.MainPageData): Int {
		val request = MainPageRequest(page.name, page.data, page.horizontalImages)
		val response = withContext(Dispatchers.IO) {
			source.api.getMainPage(1, request)
		}
		return response?.items?.sumOf { it.list.size } ?: 0
	}

	companion object {
		private const val TAG = "CloudstreamRepo"
		private const val SECTION_TAG_PREFIX = "cloudstream-section:"
	}
}
