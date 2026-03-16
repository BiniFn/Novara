package org.skepsun.kototoro.core.parser.tvbox

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.skepsun.kototoro.core.jsonsource.JsonMangaSource
import org.skepsun.kototoro.core.model.jsonsource.TVBoxStoredConfig
import org.skepsun.kototoro.core.network.CommonHeaders
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaListFilter
import org.skepsun.kototoro.parsers.model.MangaListFilterOptions
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.MangaTag
import org.skepsun.kototoro.parsers.model.MangaTagGroup
import org.skepsun.kototoro.parsers.model.RATING_UNKNOWN
import org.skepsun.kototoro.parsers.model.SortOrder
import org.skepsun.kototoro.tvbox.bridge.TVBoxJarSpiderRequest
import org.skepsun.kototoro.tvbox.bridge.TVBoxJarSpiderResponse
import org.skepsun.kototoro.tvbox.bridge.TVBoxJarSpiderWorkerProtocol
import org.skepsun.kototoro.video.data.VideoLocalCacheProxy
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal class TVBoxJarSpiderIsolatedRuntime(
	private val source: JsonMangaSource,
	private val config: TVBoxStoredConfig,
	private val context: Context,
	private val videoLocalCacheProxy: VideoLocalCacheProxy,
) : TVBoxSpiderRuntime {

	companion object {
		private const val TAG = "TVBoxJarIsolated"
		private const val TAG_CATEGORY_PREFIX = "tvbox_csp_category:"
		private const val CHAPTER_SCHEME = "tvbox-csp://play"
		private const val SPIDER_CALL_TIMEOUT_MS = 20_000L
		private const val SPIDER_PLAY_TIMEOUT_MS = 45_000L
		private const val PLAY_CACHE_MAX_AGE_MS = 2L * 60L * 1000L
		private const val HOME_CATEGORY_FALLBACK_LIMIT = 5
		private const val HOME_CATEGORY_FALLBACK_TIMEOUT_MS = 6_000L

		private val playCache = ConcurrentHashMap<String, CachedPlayResult>()
		private val playRequestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
		private val inFlightPlayRequests = ConcurrentHashMap<String, Deferred<TVBoxJarPlayResult?>>()
	}

	override val id: String = "jar-csp-remote"

	private val remoteClient = TVBoxJarSpiderRemoteClient(context)
	private val homeMutex = Mutex()
	private val detailMutex = Mutex()
	private val filterOptionsMutex = Mutex()
	private val detailCache = ConcurrentHashMap<String, TVBoxJarDetailResult>()

	@Volatile
	private var homeCache: TVBoxJarHomeResult? = null

	@Volatile
	private var filterOptionsCache: MangaListFilterOptions? = null

	@Volatile
	private var fatalRuntimeUnavailability: String? = null

	override fun describeCapability(config: TVBoxStoredConfig): String {
		return "Remote isolated service(type=3/csp, per-call worker process)"
	}

	override fun describeUnavailability(config: TVBoxStoredConfig): String? {
		fatalRuntimeUnavailability?.let { return it }
		if (!config.site.api.startsWith("csp_", ignoreCase = true)) {
			return "TVBox type=3 source is not a csp_* spider entry"
		}
		val jarSpec = resolveJarSpec()
		if (jarSpec == null) {
			return "TVBox csp source is missing spider/jar locator"
		}
		if (jarSpec.url.isBlank()) {
			return "TVBox csp source has an empty spider/jar locator"
		}
		return "TVBox csp runtime is enabled in isolated mode; each spider call runs in a dedicated worker process"
	}

	override suspend fun getList(
		offset: Int,
		order: SortOrder?,
		filter: MangaListFilter?,
	): List<Manga>? {
		val page = offset + 1
		val query = filter?.query?.trim().orEmpty()
		val selectedCategoryId = filter?.tags
			?.firstNotNullOfOrNull { tag -> parseCategoryTagId(tag.key) }
		return runCatching {
			when {
				query.isNotBlank() && config.site.searchable -> search(query, page)
				selectedCategoryId != null -> loadCategory(selectedCategoryId, page)
				offset == 0 -> {
					val homeVodItems = loadHomeVod()
					if (homeVodItems.isNotEmpty()) {
						homeVodItems.map { it.toManga(source) }
					} else {
						loadInitialCategoryFallback(loadHome(), page)
					}
				}

				else -> loadInitialCategoryFallback(loadHome(), page)
			}
		}.onFailure {
			Log.w(TAG, "TVBox isolated getList failed for ${source.name}", it)
		}.getOrNull()
	}

	override suspend fun getDetails(manga: Manga): Manga? {
		return runCatching {
			val detail = loadDetail(manga) ?: return manga
			prefetchFirstChapterPlay(detail)
			detail.toManga(source).copy(
				id = manga.id,
				url = manga.url,
				publicUrl = manga.publicUrl,
			)
		}.onFailure {
			Log.w(TAG, "TVBox isolated getDetails failed for ${source.name}", it)
		}.getOrNull()
	}

	override suspend fun getPages(chapter: MangaChapter, nextChapterUrl: String?): List<MangaPage>? {
		return runCatching {
			val locator = parseChapterLocator(chapter.url)
				?: return listOf(
					MangaPage(
						id = positiveHash("${chapter.url}|page"),
						url = chapter.url,
						preview = null,
						headers = buildHeadersForUrl(chapter.url, emptyMap()),
						source = source,
					),
				)
			if (locator.id.startsWith("http://", ignoreCase = true) || locator.id.startsWith("https://", ignoreCase = true)) {
				return listOf(
					MangaPage(
						id = positiveHash("${chapter.url}|page"),
						url = locator.id,
						preview = null,
						headers = buildHeadersForUrl(locator.id, emptyMap()),
						source = source,
					),
				)
			}
			if (looksLikeDirectPlayableLocator(locator.id)) {
				Log.i(TAG, "Bypassing playerContent for direct TVBox locator on ${source.name}: ${locator.id.take(160)}")
				return listOf(
					MangaPage(
						id = positiveHash("${chapter.url}|page"),
						url = locator.id,
						preview = null,
						headers = buildHeadersForUrl(locator.id, emptyMap()),
						source = source,
					),
				)
			}
			val playResult = loadPlay(locator.flag, locator.id)
			val resolvedUrl = playResult?.url?.takeIf { it.isNotBlank() } ?: locator.id
			val finalUrl = if (resolvedUrl.startsWith("proxy://", ignoreCase = true)) {
				createProxyPlaybackUrl(resolvedUrl)
			} else {
				resolvedUrl
			}
			listOf(
				MangaPage(
					id = positiveHash("${chapter.url}|page"),
					url = finalUrl,
					preview = null,
					headers = if (finalUrl.startsWith("http://127.0.0.1:", ignoreCase = true) || finalUrl.startsWith("http://localhost:", ignoreCase = true)) {
						emptyMap()
					} else {
						buildHeadersForUrl(finalUrl, playResult?.headers.orEmpty())
					},
					source = source,
				),
			)
		}.onFailure {
			Log.w(TAG, "TVBox isolated getPages failed for ${source.name}", it)
		}.getOrNull()
	}

	override suspend fun getFilterOptions(): MangaListFilterOptions? {
		filterOptionsCache?.let { return it }
		return filterOptionsMutex.withLock {
			filterOptionsCache?.let { return it }
			runCatching {
				val home = loadHome()
				if (home.categories.isEmpty()) {
					MangaListFilterOptions()
				} else {
					val tags = home.categories.mapTo(linkedSetOf()) { category ->
						MangaTag(
							title = category.name,
							key = "$TAG_CATEGORY_PREFIX${category.id}",
							source = source,
						)
					}
					MangaListFilterOptions(
						availableTags = tags,
						tagGroups = listOf(MangaTagGroup("分类", tags)),
					)
				}
			}.onFailure {
				Log.w(TAG, "TVBox isolated getFilterOptions failed for ${source.name}", it)
			}.getOrNull()?.also {
				filterOptionsCache = it
			}
		}
	}

	override fun getRequestHeaders(): Map<String, String>? {
		return config.site.staticHeaders.takeIf { it.isNotEmpty() }
	}

	private suspend fun loadHome(): TVBoxJarHomeResult {
		homeCache?.let { return it }
		return homeMutex.withLock {
			homeCache?.let { return it }
			val raw = callPayload(
				buildRequest(
					action = TVBoxJarSpiderWorkerProtocol.ACTION_HOME,
					timeoutMs = SPIDER_CALL_TIMEOUT_MS,
				),
			).orEmpty()
			parseHomeResult(raw).also { homeCache = it }
		}
	}

	private suspend fun loadHomeVod(): List<TVBoxJarVodItem> {
		val raw = callPayload(
			buildRequest(
				action = TVBoxJarSpiderWorkerProtocol.ACTION_HOME_VOD,
				timeoutMs = SPIDER_CALL_TIMEOUT_MS,
			),
		).orEmpty()
		return parseVodList(raw)
	}

	private suspend fun loadCategory(
		categoryId: String,
		page: Int,
		timeoutMs: Long = SPIDER_CALL_TIMEOUT_MS,
	): List<Manga> {
		Log.i(TAG, "Loading TVBox category for ${source.name}: categoryId=$categoryId page=$page")
		val raw = callPayload(
			buildRequest(
				action = TVBoxJarSpiderWorkerProtocol.ACTION_CATEGORY,
				timeoutMs = timeoutMs,
				categoryId = categoryId,
				page = page,
			),
		).orEmpty()
		return parseVodList(raw).map { it.toManga(source) }
	}

	private suspend fun loadInitialCategoryFallback(
		home: TVBoxJarHomeResult,
		page: Int,
	): List<Manga> {
		val categories = home.categories.take(HOME_CATEGORY_FALLBACK_LIMIT)
		if (categories.isEmpty()) {
			Log.i(TAG, "TVBox home has no categories for ${source.name}")
			return emptyList()
		}
		categories.forEach { category ->
			Log.i(TAG, "Trying fallback category for ${source.name}: categoryId=${category.id} name=${category.name} page=$page")
			val items = loadCategory(
				categoryId = category.id,
				page = page,
				timeoutMs = HOME_CATEGORY_FALLBACK_TIMEOUT_MS,
			)
			if (items.isNotEmpty()) {
				Log.i(TAG, "Fallback category resolved for ${source.name}: categoryId=${category.id} name=${category.name} count=${items.size}")
				return items
			}
		}
		Log.i(
			TAG,
			"All fallback categories are empty for ${source.name}: tried=${categories.joinToString { it.id }} page=$page",
		)
		return emptyList()
	}

	private suspend fun search(query: String, page: Int): List<Manga> {
		val raw = callPayload(
			buildRequest(
				action = TVBoxJarSpiderWorkerProtocol.ACTION_SEARCH,
				timeoutMs = SPIDER_CALL_TIMEOUT_MS,
				query = query,
				page = page,
			),
		).orEmpty()
		return parseVodList(raw).map { it.toManga(source) }
	}

	private suspend fun loadDetail(manga: Manga): TVBoxJarDetailResult? {
		val itemId = (manga.url ?: manga.publicUrl).orEmpty().ifBlank { manga.id.toString() }
		detailCache[itemId]?.let { return it }
		return detailMutex.withLock {
			detailCache[itemId]?.let { return it }
			val raw = callPayload(
				buildRequest(
					action = TVBoxJarSpiderWorkerProtocol.ACTION_DETAIL,
					timeoutMs = SPIDER_CALL_TIMEOUT_MS,
					itemId = itemId,
				),
			).orEmpty()
			parseDetailResult(raw)?.also { detailCache[itemId] = it }
		}
	}

	private suspend fun loadPlay(flag: String, id: String): TVBoxJarPlayResult? {
		val cacheKey = "${source.entity.id}|$flag|$id"
		playCache[cacheKey]?.takeIf { cached ->
			System.currentTimeMillis() - cached.timestampMs <= PLAY_CACHE_MAX_AGE_MS
		}?.let { cached ->
			Log.i(TAG, "Using cached TVBox play result for ${source.name}: flag=$flag id=${id.take(120)}")
			return cached.result
		}
		inFlightPlayRequests[cacheKey]?.let { existing ->
			Log.i(TAG, "Awaiting in-flight TVBox play result for ${source.name}: flag=$flag id=${id.take(120)}")
			return existing.await()
		}
		val created = playRequestScope.async {
			val raw = callPayload(
				buildRequest(
					action = TVBoxJarSpiderWorkerProtocol.ACTION_PLAY,
					timeoutMs = SPIDER_PLAY_TIMEOUT_MS,
					flag = flag,
					playId = id,
				),
			).orEmpty()
			parsePlayResult(raw)?.also { result ->
				playCache[cacheKey] = CachedPlayResult(
					result = result,
					timestampMs = System.currentTimeMillis(),
				)
			}
		}
		created.invokeOnCompletion {
			inFlightPlayRequests.remove(cacheKey, created)
		}
		val deferred = inFlightPlayRequests.putIfAbsent(cacheKey, created) ?: created
		if (deferred !== created) {
			Log.i(TAG, "Joining existing TVBox play request for ${source.name}: flag=$flag id=${id.take(120)}")
			created.cancel()
		}
		return deferred.await()
	}

	private fun prefetchFirstChapterPlay(detail: TVBoxJarDetailResult) {
		val firstChapter = detail.chapters.firstOrNull() ?: return
		val locator = parseChapterLocator(firstChapter.url) ?: return
		if (locator.id.startsWith("http://", ignoreCase = true) || locator.id.startsWith("https://", ignoreCase = true)) {
			return
		}
		if (looksLikeDirectPlayableLocator(locator.id)) {
			return
		}
		playRequestScope.launch {
			runCatching { loadPlay(locator.flag, locator.id) }
				.onFailure { error ->
					Log.d(TAG, "TVBox play prefetch failed for ${source.name}: ${firstChapter.title}", error)
				}
		}
	}

	private fun createProxyPlaybackUrl(proxySpec: String): String {
		val params = parseProxyParams(proxySpec)
		val dynamicId = "${source.name}|${params.toSortedMap()}"
		return videoLocalCacheProxy.getDynamicProxyUrl(dynamicId) { request ->
			runBlocking {
				remoteClient.execute(
					buildRequest(
						action = TVBoxJarSpiderWorkerProtocol.ACTION_PROXY,
						timeoutMs = SPIDER_CALL_TIMEOUT_MS,
						proxySpec = proxySpec,
						queryParameters = request.queryParameters,
						headers = request.headers,
					),
				).toDynamicResponse()
			}
		}
	}

	private suspend fun callPayload(request: TVBoxJarSpiderRequest): String? {
		fatalRuntimeUnavailability?.let {
			Log.w(TAG, "Skipping TVBox isolated worker call for ${source.name}: action=${request.action}, reason=$it")
			return null
		}
		val response = remoteClient.execute(request)
		if (!response.isSuccess) {
			if (response.errorCode == TVBoxJarSpiderWorkerProtocol.ERROR_FATAL_NATIVE_CRASH) {
				fatalRuntimeUnavailability = buildString {
					append("TVBox Guard native runtime crashed during local execution")
					if (config.site.api.isNotBlank()) {
						append(": api=")
						append(config.site.api)
					}
					append(". ")
					append(
						when {
							response.errorMessage?.contains("JNI DETECTED ERROR IN APPLICATION", ignoreCase = true) == true ->
								"Guard JNI bridge is incompatible with the current local runtime."
							else ->
								"Guard native library is incompatible with the current local runtime."
						},
					)
				}
			}
			Log.w(
				TAG,
				"TVBox isolated worker failed for ${source.name}: action=${request.action}, code=${response.errorCode}, message=${response.errorMessage}",
			)
			return null
		}
		return response.payload
	}

	private fun buildRequest(
		action: String,
		timeoutMs: Long,
		categoryId: String? = null,
		page: Int? = null,
		query: String? = null,
		itemId: String? = null,
		flag: String? = null,
		playId: String? = null,
		proxySpec: String? = null,
		queryParameters: Map<String, String> = emptyMap(),
		headers: Map<String, String> = emptyMap(),
	): TVBoxJarSpiderRequest {
		return TVBoxJarSpiderRequest(
			sourceId = source.entity.id,
			sourceDisplayName = source.displayName,
			sourceConfig = source.entity.config,
			action = action,
			timeoutMs = timeoutMs,
			categoryId = categoryId,
			page = page,
			query = query,
			itemId = itemId,
			flag = flag,
			playId = playId,
			proxySpec = proxySpec,
			queryParameters = queryParameters,
			headers = headers,
		)
	}

	private fun TVBoxJarSpiderResponse.toDynamicResponse(): VideoLocalCacheProxy.DynamicResponse {
		if (!isSuccess) {
			return VideoLocalCacheProxy.DynamicResponse(
				statusCode = 500,
				contentType = "text/plain; charset=utf-8",
				body = (errorMessage ?: errorCode.orEmpty()).toByteArray(Charsets.UTF_8),
			)
		}
		val proxyFile = bodyFilePath?.let(::File)
		val bodyBytes = when {
			redirectUrl != null -> ByteArray(0)
			proxyFile == null -> body
			else -> runCatching { proxyFile.readBytes() }.getOrElse {
				Log.w(TAG, "Failed to read TVBox proxy IPC file for ${source.name}: ${proxyFile.absolutePath}", it)
				ByteArray(0)
			}.also {
				runCatching { proxyFile.delete() }
			}
		}
		return VideoLocalCacheProxy.DynamicResponse(
			statusCode = statusCode,
			contentType = contentType,
			headers = headers,
			body = bodyBytes,
			redirectUrl = redirectUrl,
		)
	}

	private fun parseProxyParams(proxySpec: String): Map<String, String> {
		val raw = proxySpec.removePrefix("proxy://")
		val uri = Uri.parse("http://127.0.0.1/proxy?$raw")
		return buildMap {
			uri.queryParameterNames.forEach { name ->
				val value = uri.getQueryParameter(name)
				if (!value.isNullOrBlank()) {
					put(name, value)
				}
			}
		}
	}

	private fun resolveJarSpec(): JarSpec? {
		val rawValue = config.site.jar?.takeIf { it.isNotBlank() }
			?: config.root.spider?.takeIf { it.isNotBlank() }
			?: return null
		val trimmed = rawValue.trim()
		val md5Index = trimmed.indexOf(";md5;", ignoreCase = true)
		val pkIndex = trimmed.indexOf(";pk;", ignoreCase = true)
		val cutIndex = listOf(md5Index, pkIndex)
			.filter { it >= 0 }
			.minOrNull()
			?: -1
		val urlPart = if (cutIndex >= 0) trimmed.substring(0, cutIndex).trim() else trimmed
		val md5 = if (md5Index >= 0) {
			trimmed.substring(md5Index + 5).substringBefore(';').trim().ifBlank { null }
		} else {
			null
		}
		val resolvedUrl = resolveCandidateUrl(urlPart) ?: return null
		return JarSpec(
			url = resolvedUrl,
			md5 = md5,
			cacheKey = resolvedUrl.md5Hex(),
		)
	}

	private fun buildHeadersForUrl(url: String?, extraHeaders: Map<String, String>): Map<String, String> {
		val headers = linkedMapOf<String, String>()
		headers += config.site.staticHeaders
		headers += extraHeaders
		val host = url?.toHttpUrlOrNull()?.host?.lowercase()
		if (!host.isNullOrBlank()) {
			config.root.headerRules
				.filter { host == it.host.lowercase() }
				.forEach { rule -> headers += rule.headers }
		}
		if (!headers.keys.any { it.equals(CommonHeaders.REFERER, ignoreCase = true) }) {
			url?.toHttpUrlOrNull()?.let { httpUrl ->
				headers[CommonHeaders.REFERER] = "${httpUrl.scheme}://${httpUrl.host}/"
			}
		}
		return headers
	}

	private fun resolveCandidateUrl(rawValue: String?): String? {
		val value = rawValue?.trim().orEmpty().extractPrimaryLocator()
		if (value.isBlank()) {
			return null
		}
		if (value.startsWith("http://", ignoreCase = true) ||
			value.startsWith("https://", ignoreCase = true) ||
			value.startsWith("content://", ignoreCase = true) ||
			value.startsWith("file://", ignoreCase = true)
		) {
			return value
		}
		if (value.startsWith("//")) {
			return "https:$value"
		}
		val baseHttpUrl = config.meta.sourceLocator?.toHttpUrlOrNull()
		return baseHttpUrl?.resolve(value)?.toString() ?: value
	}

	private fun parseCategoryTagId(key: String): String? {
		return key.takeIf { it.startsWith(TAG_CATEGORY_PREFIX) }
			?.removePrefix(TAG_CATEGORY_PREFIX)
			?.ifBlank { null }
	}

	private fun looksLikeDirectPlayableLocator(value: String): Boolean {
		val normalized = value.trim().lowercase()
		return normalized.startsWith("magnet:") ||
			normalized.startsWith("thunder:") ||
			normalized.startsWith("ed2k:") ||
			normalized.startsWith("ftp://") ||
			normalized.startsWith("rtsp://") ||
			normalized.startsWith("rtmp://") ||
			normalized.startsWith("mms://")
	}

	private fun buildChapterUrl(flag: String, id: String): String {
		return Uri.parse(CHAPTER_SCHEME).buildUpon()
			.appendQueryParameter("flag", flag)
			.appendQueryParameter("id", id)
			.build()
			.toString()
	}

	private fun parseChapterLocator(url: String): TVBoxJarChapterLocator? {
		val uri = Uri.parse(url)
		if (uri.scheme != "tvbox-csp") {
			return null
		}
		val flag = uri.getQueryParameter("flag").orEmpty().ifBlank { return null }
		val id = uri.getQueryParameter("id").orEmpty().ifBlank { return null }
		return TVBoxJarChapterLocator(flag = flag, id = id)
	}

	private fun parseHomeResult(raw: String): TVBoxJarHomeResult {
		val root = raw.toJsonValue() as? JSONObject ?: return TVBoxJarHomeResult(emptyList())
		root.optString("error").takeIf { it.isNotBlank() }?.let {
			Log.w(TAG, "TVBox isolated home error for ${source.name}: $it")
		}
		val categories = root.optJSONArray("class")
			?.toObjectList()
			?.mapNotNull { item ->
				val id = item.firstNonBlank("type_id", "id", "typeId") ?: return@mapNotNull null
				val name = item.firstNonBlank("type_name", "name", "title") ?: id
				TVBoxJarCategory(id = id, name = name)
			}
			.orEmpty()
		return TVBoxJarHomeResult(categories = categories)
	}

	private fun parseVodList(raw: String): List<TVBoxJarVodItem> {
		val jsonValue = raw.toJsonValue() ?: return emptyList()
		return when (jsonValue) {
			is JSONObject -> {
				jsonValue.optString("error").takeIf { it.isNotBlank() }?.let {
					Log.w(TAG, "TVBox isolated list error for ${source.name}: $it")
				}
				when {
					jsonValue.optJSONArray("list") != null -> {
						jsonValue.optJSONArray("list")!!.toObjectList().mapNotNull(::parseVodItem)
					}

					jsonValue.optJSONObject("data")?.optJSONArray("list") != null -> {
						jsonValue.optJSONObject("data")!!.optJSONArray("list")!!.toObjectList().mapNotNull(::parseVodItem)
					}

					jsonValue.has("vod_id") || jsonValue.has("vod_name") || jsonValue.has("name") -> {
						listOfNotNull(parseVodItem(jsonValue))
					}

					else -> emptyList()
				}
			}

			is JSONArray -> jsonValue.toObjectList().mapNotNull(::parseVodItem)
			else -> emptyList()
		}
	}

	private fun parseVodItem(node: JSONObject): TVBoxJarVodItem? {
		val itemId = node.firstNonBlank("vod_id", "id", "vodId", "url") ?: return null
		val title = node.firstNonBlank("vod_name", "title", "name") ?: itemId
		val cover = node.firstNonBlank("vod_pic", "vod_pic_thumb", "pic", "thumb", "cover")
		val category = node.firstNonBlank("type_name", "vod_class", "class")
		val remarks = node.firstNonBlank("vod_remarks", "remarks", "note")
		val tags = buildSet {
			category?.let { add(MangaTag(it, "category:${it.lowercase()}", source)) }
			remarks?.let { add(MangaTag(it, "remark:${it.lowercase()}", source)) }
		}
		val description = buildString {
			node.firstNonBlank("vod_content", "content", "vod_blurb")?.let { append(it) }
			node.firstNonBlank("vod_year", "year")?.takeIf { it.isNotBlank() }?.let {
				if (isNotBlank()) append('\n')
				append("年份: ")
				append(it)
			}
			node.firstNonBlank("vod_area", "area")?.takeIf { it.isNotBlank() }?.let {
				if (isNotBlank()) append('\n')
				append("地区: ")
				append(it)
			}
			node.firstNonBlank("vod_actor", "actor")?.takeIf { it.isNotBlank() }?.let {
				if (isNotBlank()) append('\n')
				append("演员: ")
				append(it)
			}
		}.ifBlank { null }
		return TVBoxJarVodItem(
			id = positiveHash("${source.name}|$itemId|$title"),
			itemId = itemId,
			title = title,
			coverUrl = cover,
			description = description,
			tags = tags,
		)
	}

	private fun parseDetailResult(raw: String): TVBoxJarDetailResult? {
		val jsonValue = raw.toJsonValue() ?: return null
		val root = when (jsonValue) {
			is JSONObject -> jsonValue
			is JSONArray -> JSONObject().put("list", jsonValue)
			else -> return null
		}
		root.optString("error").takeIf { it.isNotBlank() }?.let {
			Log.w(TAG, "TVBox isolated detail error for ${source.name}: $it")
		}
		val itemNode = when {
			(root.optJSONArray("list")?.length() ?: 0) > 0 -> root.optJSONArray("list")?.optJSONObject(0)
			(root.optJSONObject("data")?.optJSONArray("list")?.length() ?: 0) > 0 -> root.optJSONObject("data")?.optJSONArray("list")?.optJSONObject(0)
			root.has("vod_id") || root.has("vod_name") -> root
			else -> null
		} ?: return null
		val item = parseVodItem(itemNode) ?: return null
		val playSources = parsePlaySources(itemNode)
		val chapters = if (playSources.isNotEmpty()) {
			playSources.flatMapIndexed { groupIndex, sourceGroup ->
				sourceGroup.items.mapIndexed { index, playItem ->
					MangaChapter(
						id = positiveHash("${item.itemId}|${sourceGroup.flag}|${playItem.id}|$groupIndex|$index"),
						title = playItem.title,
						number = (index + 1).toFloat(),
						volume = 0,
						url = buildChapterUrl(sourceGroup.flag, playItem.id),
						scanlator = sourceGroup.flag,
						uploadDate = 0L,
						branch = sourceGroup.flag,
						source = source,
					)
				}
			}
		} else {
			listOf(
				MangaChapter(
					id = positiveHash("${item.itemId}|single"),
					title = item.title,
					number = 1f,
					volume = 0,
					url = buildChapterUrl(item.title, item.itemId),
					scanlator = null,
					uploadDate = 0L,
					branch = null,
					source = source,
				),
			)
		}
		return TVBoxJarDetailResult(item = item, chapters = chapters)
	}

	private fun parsePlaySources(node: JSONObject): List<TVBoxJarPlaySource> {
		val rawFlags = node.firstNonBlank("vod_play_from", "playFrom").orEmpty()
		val rawUrls = node.firstNonBlank("vod_play_url", "playUrl").orEmpty()
		if (rawUrls.isBlank()) {
			return emptyList()
		}
		val flags = rawFlags.split("$$$").map { it.trim() }
		val groups = rawUrls.split("$$$")
		return groups.mapIndexedNotNull { index, group ->
			val flag = flags.getOrNull(index).orEmpty().ifBlank { "播放源${index + 1}" }
			val items = group.split('#')
				.mapNotNull { entry ->
					val clean = entry.trim()
					if (clean.isBlank()) {
						return@mapNotNull null
					}
					val parts = clean.split('$', limit = 2)
					when (parts.size) {
						1 -> TVBoxJarPlayItem(
							title = "${flag} ${safeHashIndex(index, clean)}",
							id = parts[0].trim(),
						)

						else -> TVBoxJarPlayItem(
							title = parts[0].trim().ifBlank { "${flag} ${safeHashIndex(index, clean)}" },
							id = parts[1].trim(),
						)
					}
				}
			if (items.isEmpty()) null else TVBoxJarPlaySource(flag = flag, items = items)
		}
	}

	private fun parsePlayResult(raw: String): TVBoxJarPlayResult? {
		val root = raw.toJsonValue() as? JSONObject ?: return null
		root.optString("error").takeIf { it.isNotBlank() }?.let {
			Log.w(TAG, "TVBox isolated play error for ${source.name}: $it")
		}
		val url = root.firstNonBlank("url", "playUrl", "realUrl") ?: return null
		return TVBoxJarPlayResult(
			url = url,
			headers = root.optHeaderMapFlexible("header").ifEmpty { root.optHeaderMapFlexible("headers") },
		)
	}

	private fun safeHashIndex(groupIndex: Int, raw: String): String {
		return positiveHash("$groupIndex|$raw").toString()
	}

	private fun positiveHash(raw: String): Long {
		return raw.hashCode().toLong() and 0x7fffffffL
	}

	private data class JarSpec(
		val url: String,
		val md5: String?,
		val cacheKey: String,
	)

	private data class TVBoxJarHomeResult(
		val categories: List<TVBoxJarCategory>,
	)

	private data class TVBoxJarCategory(
		val id: String,
		val name: String,
	)

	private data class TVBoxJarVodItem(
		val id: Long,
		val itemId: String,
		val title: String,
		val coverUrl: String?,
		val description: String?,
		val tags: Set<MangaTag>,
	) {
		fun toManga(source: JsonMangaSource): Manga = Manga(
			id = id,
			title = title,
			altTitles = emptySet(),
			url = itemId,
			publicUrl = itemId,
			rating = RATING_UNKNOWN,
			contentRating = null,
			coverUrl = coverUrl,
			largeCoverUrl = coverUrl,
			tags = tags,
			state = null,
			authors = emptySet(),
			description = description,
			chapters = null,
			source = source,
		)
	}

	private data class TVBoxJarDetailResult(
		val item: TVBoxJarVodItem,
		val chapters: List<MangaChapter>,
	) {
		fun toManga(source: JsonMangaSource): Manga = item.toManga(source).copy(chapters = chapters)
	}

	private data class TVBoxJarPlaySource(
		val flag: String,
		val items: List<TVBoxJarPlayItem>,
	)

	private data class TVBoxJarPlayItem(
		val title: String,
		val id: String,
	)

	private data class TVBoxJarPlayResult(
		val url: String,
		val headers: Map<String, String>,
	)

	private data class CachedPlayResult(
		val result: TVBoxJarPlayResult,
		val timestampMs: Long,
	)

	private data class TVBoxJarChapterLocator(
		val flag: String,
		val id: String,
	)
}

private fun String.toJsonValue(): Any? {
	val trimmed = trim()
	if (trimmed.isBlank()) {
		return null
	}
	return runCatching { JSONTokener(trimmed).nextValue() }.getOrNull()
}

private fun JSONArray.toObjectList(): List<JSONObject> {
	return buildList {
		for (index in 0 until length()) {
			optJSONObject(index)?.let(::add)
		}
	}
}

private fun JSONObject.firstNonBlank(vararg keys: String): String? {
	keys.forEach { key ->
		val value = optStringOrNull(key)
		if (!value.isNullOrBlank()) {
			return value
		}
	}
	return null
}

private fun JSONObject.optStringOrNull(key: String): String? {
	if (!has(key)) {
		return null
	}
	val value = opt(key)
	if (value == null || value === JSONObject.NULL) {
		return null
	}
	return value.toString().trim().ifBlank { null }
}

private fun JSONObject.optHeaderMapFlexible(key: String): Map<String, String> {
	val value = opt(key) ?: return emptyMap()
	return when (value) {
		is JSONObject -> value.toHeaderMap()
		is String -> {
			val parsed = runCatching { JSONTokener(value).nextValue() as? JSONObject }.getOrNull()
			parsed?.toHeaderMap().orEmpty()
		}

		else -> emptyMap()
	}
}

private fun JSONObject.toHeaderMap(): Map<String, String> {
	return buildMap {
		val iterator = keys()
		while (iterator.hasNext()) {
			val headerKey = iterator.next()
			val headerValue = opt(headerKey)?.toString()?.trim().orEmpty()
			if (headerValue.isNotBlank()) {
				put(headerKey, headerValue)
			}
		}
	}
}

private fun String.extractPrimaryLocator(): String {
	val trimmed = trim()
	if (trimmed.isBlank()) {
		return trimmed
	}
	val markers = listOf(";md5;", ";pk;")
	markers.forEach { marker ->
		val index = trimmed.indexOf(marker, ignoreCase = true)
		if (index >= 0) {
			return trimmed.substring(0, index).trim()
		}
	}
	val separatorIndex = trimmed.indexOf(';')
	return if (separatorIndex >= 0) {
		trimmed.substring(0, separatorIndex).trim()
	} else {
		trimmed
	}
}

private fun String.md5Hex(): String {
	val digest = MessageDigest.getInstance("MD5").digest(toByteArray(Charsets.UTF_8))
	return digest.joinToString("") { "%02x".format(it) }
}
