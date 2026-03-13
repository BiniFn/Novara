package org.skepsun.kototoro.core.parser.tvbox

import android.content.Context
import android.net.Uri
import android.util.Log
import com.github.catvod.crawler.Spider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.skepsun.kototoro.core.jsonsource.JsonMangaSource
import org.skepsun.kototoro.core.model.jsonsource.TVBoxStoredConfig
import org.skepsun.kototoro.core.network.CommonHeaders
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaListFilter
import org.skepsun.kototoro.parsers.model.MangaListFilterOptions
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.MangaTag
import org.skepsun.kototoro.parsers.model.MangaTagGroup
import org.skepsun.kototoro.parsers.model.RATING_UNKNOWN
import org.skepsun.kototoro.parsers.model.SortOrder
import org.skepsun.kototoro.video.data.VideoLocalCacheProxy
import java.io.File
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class TVBoxJarSpiderRuntime(
	private val source: JsonMangaSource,
	private val config: TVBoxStoredConfig,
	private val context: Context,
	private val httpClient: LegadoHttpClient,
	private val videoLocalCacheProxy: VideoLocalCacheProxy,
) : TVBoxSpiderRuntime {

	companion object {
		private const val TAG = "TVBoxJarRuntime"
		private const val TAG_CATEGORY_PREFIX = "tvbox_csp_category:"
		private const val CHAPTER_SCHEME = "tvbox-csp://play"
		private const val CACHE_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L
		private const val SPIDER_CALL_TIMEOUT_MS = 20_000L
		private const val HOME_CATEGORY_FALLBACK_LIMIT = 5
		private const val HOME_CATEGORY_FALLBACK_TIMEOUT_MS = 6_000L

		private val loadedJars = ConcurrentHashMap<String, LoadedJar>()
		private val loadMutex = Mutex()
	}

	override val id: String = "jar-csp"

	private val spiderMutex = Mutex()
	private val homeMutex = Mutex()
	private val detailMutex = Mutex()
	private val filterOptionsMutex = Mutex()
	private val detailCache = ConcurrentHashMap<String, TVBoxJarDetailResult>()
	private val spiderExecutor by lazy(LazyThreadSafetyMode.NONE) {
		Executors.newSingleThreadExecutor { runnable ->
			Thread(runnable, "tvbox-jar-spider-${source.name}").apply {
				isDaemon = true
			}
		}
	}

	@Volatile
	private var spiderCache: Spider? = null

	@Volatile
	private var homeCache: TVBoxJarHomeResult? = null

	@Volatile
	private var filterOptionsCache: MangaListFilterOptions? = null

	override fun describeCapability(config: TVBoxStoredConfig): String {
		return "DexClassLoader(type=3/csp, basic TVBox spider bridge)"
	}

	override fun describeUnavailability(config: TVBoxStoredConfig): String? {
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
		return "TVBox csp runtime is enabled in basic mode; advanced proxy/parser branches may still be incomplete"
	}

	override suspend fun getList(
		offset: Int,
		order: SortOrder?,
		filter: MangaListFilter?,
	): List<Manga>? {
		val spider = getSpiderOrNull() ?: return null
		val page = offset + 1
		val query = filter?.query?.trim().orEmpty()
		val selectedCategoryId = filter?.tags
			?.firstNotNullOfOrNull { tag -> parseCategoryTagId(tag.key) }
		return runCatching {
			when {
				query.isNotBlank() && config.site.searchable -> search(spider, query, page)
				selectedCategoryId != null -> loadCategory(spider, selectedCategoryId, page)
				offset == 0 -> {
					val homeVodItems = loadHomeVod(spider)
					if (homeVodItems.isNotEmpty()) {
						homeVodItems.map { it.toManga(source) }
					} else {
						loadInitialCategoryFallback(spider, loadHome(spider), page)
					}
				}
				else -> {
					loadInitialCategoryFallback(spider, loadHome(spider), page)
				}
			}
		}.onFailure {
			Log.w(TAG, "TVBox jar getList failed for ${source.name}", it)
		}.getOrNull()
	}

	override suspend fun getDetails(manga: Manga): Manga? {
		val spider = getSpiderOrNull() ?: return null
		return runCatching {
			val detail = loadDetail(spider, manga) ?: return manga
			detail.toManga(source).copy(
				id = manga.id,
				url = manga.url,
				publicUrl = manga.publicUrl,
			)
		}.onFailure {
			Log.w(TAG, "TVBox jar getDetails failed for ${source.name}", it)
		}.getOrNull()
	}

	override suspend fun getPages(chapter: MangaChapter, nextChapterUrl: String?): List<MangaPage>? {
		val spider = getSpiderOrNull() ?: return null
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
			val playResult = loadPlay(spider, locator.flag, locator.id)
			val resolvedUrl = playResult?.url?.takeIf { it.isNotBlank() } ?: locator.id
			val finalUrl = if (resolvedUrl.startsWith("proxy://", ignoreCase = true)) {
				createProxyPlaybackUrl(spider, resolvedUrl)
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
			Log.w(TAG, "TVBox jar getPages failed for ${source.name}", it)
		}.getOrNull()
	}

	override suspend fun getFilterOptions(): MangaListFilterOptions? {
		filterOptionsCache?.let { return it }
		val spider = getSpiderOrNull() ?: return null
		return filterOptionsMutex.withLock {
			filterOptionsCache?.let { return it }
			runCatching {
				val home = loadHome(spider)
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
				Log.w(TAG, "TVBox jar getFilterOptions failed for ${source.name}", it)
			}.getOrNull()?.also {
				filterOptionsCache = it
			}
		}
	}

	override fun getRequestHeaders(): Map<String, String>? {
		return config.site.staticHeaders.takeIf { it.isNotEmpty() }
	}

	private suspend fun getSpiderOrNull(): Spider? {
		spiderCache?.let { return it }
		return spiderMutex.withLock {
			spiderCache?.let { return it }
			createSpider().also { spiderCache = it }
		}
	}

	private suspend fun createSpider(): Spider? = withContext(Dispatchers.IO) {
		if (!config.site.api.startsWith("csp_", ignoreCase = true)) {
			return@withContext null
		}
		val loadedJar = ensureLoadedJar() ?: return@withContext null
		val className = "com.github.catvod.spider.${config.site.api.removePrefix("csp_")}"
		Log.i(TAG, "Creating TVBox spider instance for ${source.name}: class=$className jar=${loadedJar.spec.url}")
		val spider = runCatching {
			loadedJar.classLoader.loadClass(className)
				.getDeclaredConstructor()
				.newInstance() as? Spider
		}.getOrElse {
			Log.w(TAG, "Unable to instantiate TVBox spider class $className for ${source.name}", it)
			null
		} ?: return@withContext null
		val extLiteral = buildExtLiteral()
		runCatching {
			spider.init(context, extLiteral)
		}.recoverCatching {
			spider.init(context)
		}.onFailure {
			Log.w(TAG, "TVBox spider init failed for ${source.name}", it)
			logReflectiveFailure("init", it)
			return@withContext null
		}
		spider
	}

	private suspend fun ensureLoadedJar(): LoadedJar? = withContext(Dispatchers.IO) {
		val jarSpec = resolveJarSpec() ?: return@withContext null
		val cacheKey = jarSpec.cacheKey
		loadedJars[cacheKey]?.let { return@withContext it }
		loadMutex.withLock {
			loadedJars[cacheKey]?.let { return@withLock it }
			val cacheDir = File(context.filesDir, "tvbox_csp").apply { mkdirs() }
			val jarFile = File(cacheDir, "$cacheKey.jar")
			val optimizedDir = File(context.codeCacheDir, "tvbox_csp_opt").apply { mkdirs() }
			Log.i(TAG, "Preparing TVBox spider jar for ${source.name}: url=${jarSpec.url} cache=${jarFile.absolutePath}")
			if (!isUsableJarCache(jarFile, jarSpec.md5)) {
				downloadJar(jarSpec, jarFile)
			}
			if (!isUsableJarCache(jarFile, jarSpec.md5)) {
				Log.w(TAG, "TVBox spider jar cache is still unusable after download for ${source.name}: ${jarFile.absolutePath}")
				return@withLock null
			}
			prepareJarForLoading(jarFile)
			val classLoader = ChildFirstDexClassLoader(
				jarFile.absolutePath,
				optimizedDir.absolutePath,
				null,
				context.classLoader,
			)
			runCatching {
				val initClass = classLoader.loadClass("com.github.catvod.spider.Init")
				val initMethod = initClass.getMethod("init", Context::class.java)
				initMethod.invoke(null, context)
			}.onFailure {
				Log.d(TAG, "TVBox spider Init not available for ${source.name}: ${it.message}")
			}
			val proxyMethod = runCatching {
				classLoader.loadClass("com.github.catvod.spider.Proxy")
					.getMethod("proxy", Map::class.java)
			}.getOrNull()
			LoadedJar(
				spec = jarSpec,
				classLoader = classLoader,
				proxyMethod = proxyMethod,
			).also { loadedJars[cacheKey] = it }
		}
	}

	private fun isUsableJarCache(file: File, expectedMd5: String?): Boolean {
		if (!file.exists() || file.length() <= 0L) {
			return false
		}
		if (!expectedMd5.isNullOrBlank()) {
			return file.md5Hex().equals(expectedMd5, ignoreCase = true)
		}
		return System.currentTimeMillis() - file.lastModified() <= CACHE_MAX_AGE_MS
	}

	private suspend fun downloadJar(spec: JarSpec, destination: File) {
		val response = httpClient.get(spec.url, buildHeadersForUrl(spec.url, emptyMap()), source)
		try {
			if (!response.isSuccessful) {
				throw IllegalArgumentException("HTTP ${response.code} when loading TVBox spider jar")
			}
			val bytes = response.body?.bytes()
				?: throw IllegalArgumentException("TVBox spider jar response body is empty")
			destination.parentFile?.mkdirs()
			if (destination.exists() && !destination.setWritable(true, true)) {
				Log.w(TAG, "Unable to mark TVBox spider jar writable before overwrite: ${destination.absolutePath}")
			}
			destination.writeBytes(bytes)
			Log.i(TAG, "Downloaded TVBox spider jar for ${source.name}: bytes=${bytes.size} file=${destination.absolutePath}")
			if (!spec.md5.isNullOrBlank() && !destination.md5Hex().equals(spec.md5, ignoreCase = true)) {
				Log.w(
					TAG,
					"TVBox spider jar MD5 mismatch for ${source.name}: expected=${spec.md5}, actual=${destination.md5Hex()}, url=${spec.url}",
				)
			}
			prepareJarForLoading(destination)
		} finally {
			response.close()
		}
	}

	private fun prepareJarForLoading(file: File) {
		if (!file.exists()) {
			return
		}
		file.setReadable(true, true)
		file.setExecutable(false, false)
		if (!file.setWritable(false, false) && !file.setReadOnly()) {
			Log.w(TAG, "Unable to mark TVBox spider jar read-only: ${file.absolutePath}")
		}
	}

	private suspend fun loadHome(spider: Spider): TVBoxJarHomeResult {
		homeCache?.let { return it }
		return homeMutex.withLock {
			homeCache?.let { return it }
			val raw = invokeSpider("homeContent(true)") {
				spider.homeContent(true)
			}.orEmpty()
			parseHomeResult(raw).also { homeCache = it }
		}
	}

	private suspend fun loadHomeVod(spider: Spider): List<TVBoxJarVodItem> {
		val raw = invokeSpider("homeVideoContent()") {
			spider.homeVideoContent()
		}.orEmpty()
		return parseVodList(raw)
	}

	private suspend fun loadCategory(
		spider: Spider,
		categoryId: String,
		page: Int,
		timeoutMs: Long = SPIDER_CALL_TIMEOUT_MS,
	): List<Manga> {
		Log.i(TAG, "Loading TVBox category for ${source.name}: categoryId=$categoryId page=$page")
		val raw = invokeSpider(
			action = "categoryContent(tid=$categoryId, pg=$page)",
			timeoutMs = timeoutMs,
		) {
			spider.categoryContent(categoryId, page.toString(), true, hashMapOf())
		}.orEmpty()
		return parseVodList(raw).map { it.toManga(source) }
	}

	private suspend fun loadInitialCategoryFallback(
		spider: Spider,
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
				spider = spider,
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

	private suspend fun search(spider: Spider, query: String, page: Int): List<Manga> {
		val raw = invokeSpider("searchContent(query=$query, page=$page)") {
			runCatching {
				spider.searchContent(query, false, page.toString())
			}.getOrElse {
				spider.searchContent(query, false)
			}
		}.orEmpty()
		return parseVodList(raw).map { it.toManga(source) }
	}

	private suspend fun loadDetail(spider: Spider, manga: Manga): TVBoxJarDetailResult? {
		val itemId = (manga.url ?: manga.publicUrl).orEmpty().ifBlank { manga.id.toString() }
		detailCache[itemId]?.let { return it }
		return detailMutex.withLock {
			detailCache[itemId]?.let { return it }
			val raw = invokeSpider("detailContent(ids=$itemId)") {
				spider.detailContent(listOf(itemId))
			}.orEmpty()
			parseDetailResult(raw)?.also { detailCache[itemId] = it }
		}
	}

	private suspend fun loadPlay(spider: Spider, flag: String, id: String): TVBoxJarPlayResult? {
		val raw = invokeSpider("playerContent(flag=$flag, id=$id)") {
			spider.playerContent(flag, id, emptyList())
		}.orEmpty()
		return parsePlayResult(raw)
	}

	private fun createProxyPlaybackUrl(spider: Spider, proxySpec: String): String {
		val params = parseProxyParams(proxySpec)
		val dynamicId = "${source.name}|${params.toSortedMap()}"
		return videoLocalCacheProxy.getDynamicProxyUrl(dynamicId) { request ->
			val mergedParams = LinkedHashMap<String, String>()
			mergedParams.putAll(params)
			mergedParams.putAll(request.queryParameters)
			mergedParams.putAll(request.headers)
			mergedParams["request-headers"] = JSONObject(request.headers).toString()
			val result = when {
				mergedParams.containsKey("do") -> runCatching { spider.proxyLocal(mergedParams) }.getOrNull()
				mergedParams.containsKey("go") -> runCatching { invokeStaticProxy(mergedParams) }.getOrNull()
				else -> null
			}
			result.toDynamicResponse()
		}
	}

	private fun invokeStaticProxy(params: Map<String, String>): Array<Any?>? {
		val jarSpec = resolveJarSpec() ?: return null
		val proxyMethod = loadedJars[jarSpec.cacheKey]?.proxyMethod ?: return null
		val result = proxyMethod.invoke(null, params)
		return if (result is Array<*>) {
			arrayOfNulls<Any?>(result.size).also { array ->
				result.indices.forEach { index -> array[index] = result[index] }
			}
		} else {
			null
		}
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

	private fun Array<Any?>?.toDynamicResponse(): VideoLocalCacheProxy.DynamicResponse {
		if (this == null || isEmpty()) {
			return VideoLocalCacheProxy.DynamicResponse(
				statusCode = 500,
				contentType = "text/plain; charset=utf-8",
				body = "TVBox proxy returned empty result".toByteArray(Charsets.UTF_8),
			)
		}
		val statusCode = (getOrNull(0) as? Number)?.toInt() ?: 500
		val contentType = getOrNull(1)?.toString().orEmpty().ifBlank { "application/octet-stream" }
		val rawBody = getOrNull(2)
		val headers = (getOrNull(3) as? Map<*, *>)
			?.mapNotNull { (key, value) ->
				val headerKey = key?.toString()?.trim().orEmpty()
				val headerValue = value?.toString()?.trim().orEmpty()
				if (headerKey.isBlank() || headerValue.isBlank()) {
					null
				} else {
					headerKey to headerValue
				}
			}
			?.toMap()
			.orEmpty()
		val redirectUrl = headers.entries.firstNotNullOfOrNull { (key, value) ->
			value.takeIf { key.equals("Location", ignoreCase = true) }
		}
		val body = when (rawBody) {
			null -> ByteArray(0)
			is ByteArray -> rawBody
			is InputStream -> rawBody.use { it.readBytes() }
			else -> rawBody.toString().toByteArray(Charsets.UTF_8)
		}
		return VideoLocalCacheProxy.DynamicResponse(
			statusCode = statusCode,
			contentType = contentType,
			headers = headers,
			body = if (redirectUrl != null) ByteArray(0) else body,
			redirectUrl = redirectUrl,
		)
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
			raw = rawValue,
			url = resolvedUrl,
			md5 = md5,
			cacheKey = resolvedUrl.md5Hex(),
		)
	}

	private fun buildExtLiteral(): String {
		val ext = config.site.ext ?: return ""
		return when (ext) {
			is String -> ext
			is JSONObject -> ext.toString()
			is JSONArray -> ext.toString()
			else -> ext.toString()
		}
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
			Log.w(TAG, "TVBox jar home error for ${source.name}: $it")
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
					Log.w(TAG, "TVBox jar list error for ${source.name}: $it")
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
			Log.w(TAG, "TVBox jar detail error for ${source.name}: $it")
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
			Log.w(TAG, "TVBox jar play error for ${source.name}: $it")
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

	private suspend fun invokeSpider(
		action: String,
		timeoutMs: Long = SPIDER_CALL_TIMEOUT_MS,
		block: () -> String,
	): String? {
		return withContext(Dispatchers.IO) {
			Log.i(TAG, "TVBox spider call start for ${source.name}: $action")
			runCatching { executeSpiderCall(action, timeoutMs, block) }
				.onSuccess { result ->
					Log.i(
						TAG,
						"TVBox spider call succeeded for ${source.name}: $action, resultLength=${result.length}, preview=${result.take(160)}",
					)
				}
				.onFailure { error ->
					Log.w(TAG, "TVBox spider call failed for ${source.name}: $action", error)
					logReflectiveFailure(action, error)
				}
				.getOrNull()
		}
	}

	private fun executeSpiderCall(action: String, timeoutMs: Long, block: () -> String): String {
		var future: Future<String>? = null
		try {
			future = spiderExecutor.submit<String> {
				block()
			}
			return future.get(timeoutMs, TimeUnit.MILLISECONDS)
		} catch (error: TimeoutException) {
			future?.cancel(true)
			Log.e(TAG, "TVBox spider call timed out for ${source.name}: $action after ${timeoutMs}ms")
			throw error
		} catch (error: Throwable) {
			future?.cancel(true)
			throw error
		}
	}

	private fun logReflectiveFailure(action: String, error: Throwable) {
		when (error) {
			is InvocationTargetException -> {
				val target = error.targetException ?: error.cause
				Log.e(
					TAG,
					"TVBox spider reflective target failure for ${source.name}: action=$action, target=${target?.javaClass?.name}, message=${target?.message}",
					target ?: error,
				)
			}
			is NoClassDefFoundError -> {
				Log.e(
					TAG,
					"TVBox spider missing class for ${source.name}: action=$action, message=${error.message}",
					error,
				)
			}
			is ClassNotFoundException -> {
				Log.e(
					TAG,
					"TVBox spider class not found for ${source.name}: action=$action, message=${error.message}",
					error,
				)
			}
		}
		error.cause?.let { cause ->
			if (cause !== error) {
				logReflectiveFailure("$action.cause", cause)
			}
		}
	}

	private data class JarSpec(
		val raw: String,
		val url: String,
		val md5: String?,
		val cacheKey: String,
	)

	private data class LoadedJar(
		val spec: JarSpec,
		val classLoader: ClassLoader,
		val proxyMethod: Method?,
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

private fun File.md5Hex(): String {
	val digest = MessageDigest.getInstance("MD5")
	inputStream().use { input ->
		val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
		while (true) {
			val read = input.read(buffer)
			if (read <= 0) {
				break
			}
			digest.update(buffer, 0, read)
		}
	}
	return digest.digest().joinToString("") { "%02x".format(it) }
}
