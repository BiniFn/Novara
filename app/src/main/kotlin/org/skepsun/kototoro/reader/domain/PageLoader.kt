package org.skepsun.kototoro.reader.domain

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.net.Uri
import androidx.annotation.AnyThread
import androidx.annotation.CheckResult
import androidx.collection.LongSparseArray
import androidx.collection.set
import androidx.core.net.toFile
import androidx.core.net.toUri
import coil3.BitmapImage
import coil3.Image
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.toBitmap
import com.davemorrissey.labs.subscaleview.ImageSource
import dagger.hilt.android.ActivityRetainedLifecycle
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.source
import okio.use
import org.jetbrains.annotations.Blocking
import org.skepsun.kototoro.core.LocalizedAppContext
import org.skepsun.kototoro.core.image.BitmapDecoderCompat
import org.skepsun.kototoro.core.network.CommonHeaders
import org.skepsun.kototoro.core.network.MangaHttpClient
import org.skepsun.kototoro.core.network.imageproxy.ImageProxyInterceptor
import org.skepsun.kototoro.core.parser.CachingMangaRepository
import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.model.getLocale
import org.skepsun.kototoro.core.ui.image.TrimTransformation
import org.skepsun.kototoro.core.util.FileSize
import org.skepsun.kototoro.core.util.MimeTypes
import org.skepsun.kototoro.core.util.ext.URI_SCHEME_ZIP
import org.skepsun.kototoro.core.util.ext.cancelChildrenAndJoin
import org.skepsun.kototoro.core.util.ext.compressToPNG
import org.skepsun.kototoro.core.util.ext.ensureRamAtLeast
import org.skepsun.kototoro.core.util.ext.ensureSuccess
import org.skepsun.kototoro.core.util.ext.getCompletionResultOrNull
import org.skepsun.kototoro.core.util.ext.isFileUri
import org.skepsun.kototoro.core.util.ext.isNotEmpty
import org.skepsun.kototoro.core.util.ext.isPowerSaveMode
import org.skepsun.kototoro.core.util.ext.isZipUri
import org.skepsun.kototoro.core.util.ext.lifecycleScope
import org.skepsun.kototoro.core.util.ext.mangaSourceExtra
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.ext.ramAvailable
import org.skepsun.kototoro.core.util.ext.toMimeType
import org.skepsun.kototoro.core.util.ext.toMimeTypeOrNull
import org.skepsun.kototoro.core.util.ext.use
import org.skepsun.kototoro.core.util.ext.withProgress
import org.skepsun.kototoro.core.util.progress.ProgressDeferred
import org.skepsun.kototoro.download.ui.worker.DownloadSlowdownDispatcher
import org.skepsun.kototoro.local.data.LocalStorageCache
import org.skepsun.kototoro.local.data.PageCache
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.util.await
import org.skepsun.kototoro.parsers.util.requireBody
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import org.skepsun.kototoro.reader.ui.pager.ReaderPage
import org.skepsun.kototoro.reader.translate.domain.ReaderPageTranslationProcessor
import java.io.File
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@ActivityRetainedScoped
class PageLoader @Inject constructor(
	@LocalizedAppContext private val context: Context,
	lifecycle: ActivityRetainedLifecycle,
	@MangaHttpClient private val okHttp: OkHttpClient,
	@PageCache private val cache: LocalStorageCache,
	private val coil: ImageLoader,
	private val settings: AppSettings,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val imageProxyInterceptor: ImageProxyInterceptor,
	private val downloadSlowdownDispatcher: DownloadSlowdownDispatcher,
	private val translationProcessor: ReaderPageTranslationProcessor,
) {

	val loaderScope = lifecycle.lifecycleScope + InternalErrorHandler() + Dispatchers.Default

	private val tasks = LongSparseArray<PageTaskRecord>()
	private val translationUpdates = MutableSharedFlow<Long>(extraBufferCapacity = 64)
	private val translationStatusUpdates = MutableSharedFlow<TranslationLayerStateEvent>(extraBufferCapacity = 128)
	private val semaphore = Semaphore(settings.readerThreads)
	private val convertLock = Mutex()
	private val prefetchLock = Mutex()
	private val translationLock = Any()
	private val translationJobs = LongSparseArray<Job>()

	@Volatile
	private var repository: MangaRepository? = null
	private val prefetchQueue = LinkedList<MangaPage>()
	private val counter = AtomicInteger(0)
	private var prefetchQueueLimit = settings.readerPrefetchLimit
	private val edgeDetector = EdgeDetector(context)

	private data class PageTaskRecord(
		val task: ProgressDeferred<Uri, Float>,
		val translationWorkSignature: String,
	)

	data class TranslationLayerStateEvent(
		val pageId: Long,
		val state: TranslationLayerState,
	)

	enum class TranslationLayerState {
		IDLE,
		GENERATING,
		READY,
		FAILED,
	}

	fun isPrefetchApplicable(): Boolean {
		return repository is CachingMangaRepository
			&& settings.isPagesPreloadEnabled
			&& !context.isPowerSaveMode()
			&& !isLowRam()
	}

	@AnyThread
	fun prefetch(pages: List<ReaderPage>) = loaderScope.launch {
		prefetchLock.withLock {
			for (page in pages.asReversed()) {
				if (tasks.containsKey(page.id)) {
					continue
				}
				prefetchQueue.offerFirst(page.toMangaPage())
				if (prefetchQueue.size > prefetchQueueLimit) {
					prefetchQueue.pollLast()
				}
			}
		}
		if (counter.get() == 0) {
			onIdle()
		}
	}

	suspend fun loadPreview(page: MangaPage): ImageSource? {
		// JS/JSON 源缺少通用 Referer，预览请求容易带不上头导致 400，直接跳过
		if (page.source.name.startsWith("JSON_")) return null
		val preview = page.preview
		if (preview.isNullOrEmpty()) {
			return null
		}
		val request = ImageRequest.Builder(context)
			.data(preview)
			.mangaSourceExtra(page.source)
			.transformations(TrimTransformation())
			.build()
		return coil.execute(request).image?.toImageSource()
	}

	fun peekPreviewSource(preview: String?): ImageSource? {
		if (preview.isNullOrEmpty()) {
			return null
		}
		coil.memoryCache?.let { cache ->
			val key = MemoryCache.Key(preview)
			cache[key]?.image?.let {
				return if (it is BitmapImage) {
					ImageSource.cachedBitmap(it.toBitmap())
				} else {
					ImageSource.bitmap(it.toBitmap())
				}
			}
		}
		coil.diskCache?.let { cache ->
			cache.openSnapshot(preview)?.use { snapshot ->
				return ImageSource.file(snapshot.data.toFile())
			}
		}
		return null
	}

	fun loadPageAsync(page: MangaPage, force: Boolean): ProgressDeferred<Uri, Float> {
		val currentSignature = currentTranslationWorkSignature()
		var task = tasks[page.id]
			?.takeIf { it.translationWorkSignature == currentSignature }
			?.task
			?.takeIf { it.isValid() }
		if (force) {
			task?.cancel()
		} else if (task?.isCancelled == false) {
			return task
		}
		task = loadPageAsyncImpl(page, skipCache = force, isPrefetch = false)
		synchronized(tasks) {
			tasks[page.id] = PageTaskRecord(
				task = task,
				translationWorkSignature = currentSignature,
			)
		}
		return task
	}

	suspend fun loadPage(page: MangaPage, force: Boolean): Uri {
		return loadPageAsync(page, force).await()
	}

	@CheckResult
	suspend fun convertBimap(uri: Uri): Uri = convertLock.withLock {
		if (uri.isZipUri()) {
			runInterruptible(Dispatchers.IO) {
				ZipFile(uri.schemeSpecificPart).use { zip ->
					val entry = zip.getEntry(uri.fragment)
					context.ensureRamAtLeast(entry.size * 2)
					zip.getInputStream(entry).use {
						BitmapDecoderCompat.decode(it, MimeTypes.getMimeTypeFromExtension(entry.name))
					}
				}
			}.use { image ->
				cache.set(uri.toString(), image).toUri()
			}
		} else {
			val file = uri.toFile()
			runInterruptible(Dispatchers.IO) {
				context.ensureRamAtLeast(file.length() * 2)
				BitmapDecoderCompat.decode(file)
			}.use { image ->
				image.compressToPNG(file)
			}
			uri
		}
	}

	suspend fun getTrimmedBounds(uri: Uri): Rect? = runCatchingCancellable {
		edgeDetector.getBounds(ImageSource.uri(uri))
	}.onFailure { error ->
		error.printStackTraceDebug()
	}.getOrNull()

	suspend fun getPageUrl(page: MangaPage): String {
		return getRepository(page.source).getPageUrl(page)
	}

	suspend fun invalidate(clearCache: Boolean) {
		tasks.clear()
		synchronized(translationLock) {
			for (i in 0 until translationJobs.size()) {
				translationJobs.valueAt(i).cancel()
			}
			translationJobs.clear()
		}
		loaderScope.cancelChildrenAndJoin()
		if (clearCache) {
			cache.clear()
		}
	}

	fun invalidateTask(pageId: Long) {
		synchronized(tasks) {
			tasks[pageId]?.task?.cancel()
			tasks.remove(pageId)
		}
	}

	fun invalidateTranslationTask(pageId: Long) {
		invalidateTask(pageId)
		synchronized(translationLock) {
			translationJobs[pageId]?.cancel()
			translationJobs.remove(pageId)
		}
		translationStatusUpdates.tryEmit(TranslationLayerStateEvent(pageId, TranslationLayerState.IDLE))
	}

	suspend fun invalidateTranslationCacheForPage(pageId: Long) {
		translationProcessor.clearPageCaches(pageId)
	}

	suspend fun invalidateTranslationCaches() {
		translationProcessor.clearAllCaches()
	}

	fun observeTranslationUpdates(): Flow<Long> = translationUpdates
	fun observeTranslationStatusUpdates(): Flow<TranslationLayerStateEvent> = translationStatusUpdates
	fun observeTranslationDebugLogUpdates(): Flow<Long> = translationProcessor.observeDebugLogUpdates()
	fun getTranslationDebugLog(pageId: Long): String = translationProcessor.getPageDebugLog(pageId)

	suspend fun resolveDisplayVariant(
		page: MangaPage,
		currentUri: Uri,
		showTranslated: Boolean,
	): Uri? {
		return if (showTranslated) {
			translationProcessor.peekRendered(page, currentUri)
		} else {
			translationProcessor.peekSourceOfRendered(currentUri) ?: currentUri
		}
	}

	private fun onIdle() = loaderScope.launch {
		prefetchLock.withLock {
			while (prefetchQueue.isNotEmpty()) {
				val page = prefetchQueue.pollFirst() ?: return@launch
				synchronized(tasks) {
					tasks[page.id] = PageTaskRecord(
						task = loadPageAsyncImpl(page, skipCache = false, isPrefetch = true),
						translationWorkSignature = currentTranslationWorkSignature(),
					)
				}
			}
		}
	}

	private fun currentTranslationWorkSignature(): String {
		return buildString {
			append(settings.isReaderTranslationEnabled)
			append('|')
			append(settings.readerTranslationSourceLanguage)
			append('|')
			append(settings.readerTranslationTargetLanguage)
			append('|')
			append(settings.readerTranslationOcrEngine.name)
			append('|')
			append(settings.readerTranslationMode.name)
			append('|')
			append(settings.readerTranslationApiEndpoint)
			append('|')
			append(settings.readerTranslationApiModel)
			append('|')
			append(settings.readerTranslationBubbleGroupingTuning)
			append('|')
			append(settings.isReaderTranslationBubbleGroupingEnabled)
			append('|')
			append(settings.readerTranslationOverlayCompactness)
			append('|')
			append(settings.readerTranslationDetModelId)
			append('|')
			append(settings.readerTranslationOnnxModelId)
		}
	}

	private fun loadPageAsyncImpl(
		page: MangaPage,
		skipCache: Boolean,
		isPrefetch: Boolean,
	): ProgressDeferred<Uri, Float> {
		val progress = MutableStateFlow(PROGRESS_UNDEFINED)
		val deferred = loaderScope.async {
			counter.incrementAndGet()
			try {
				loadPageImpl(
					page = page,
					progress = progress,
					isPrefetch = isPrefetch,
					skipCache = skipCache,
				)
			} finally {
				if (counter.decrementAndGet() == 0) {
					onIdle()
				}
			}
		}
		return ProgressDeferred(deferred, progress)
	}

	@Synchronized
	private fun getRepository(source: MangaSource): MangaRepository {
		val result = repository
		return if (result != null && result.source == source) {
			result
		} else {
			mangaRepositoryFactory.create(source).also { repository = it }
		}
	}

	private suspend fun loadPageImpl(
		page: MangaPage,
		progress: MutableStateFlow<Float>,
		isPrefetch: Boolean,
		skipCache: Boolean,
	): Uri = semaphore.withPermit {
		val pageUrl = getPageUrl(page)
		check(pageUrl.isNotBlank()) { "Cannot obtain full image url for $page" }
		val sourceUri = if (!skipCache) {
			cache.get(pageUrl)?.toUri()
		} else {
			null
		} ?: run {
			val uri = pageUrl.toUri()
			when {
				uri.isZipUri() -> if (uri.scheme == URI_SCHEME_ZIP) {
					uri
				} else { // legacy uri
					uri.buildUpon().scheme(URI_SCHEME_ZIP).build()
				}

				uri.isFileUri() -> uri
				uri.scheme == "data" -> {
					val dataUrl = pageUrl
					val commaIndex = dataUrl.indexOf(',')
					if (commaIndex == -1) error("Invalid data URL: $dataUrl")

					val header = dataUrl.substring(0, commaIndex)
					val data = dataUrl.substring(commaIndex + 1)
					val isBase64 = header.contains(";base64")
					val contentType = header.substringAfter("data:").substringBefore(";")

					val bytes = if (isBase64) {
						android.util.Base64.decode(data, android.util.Base64.DEFAULT)
					} else {
						java.net.URLDecoder.decode(data, "UTF-8").toByteArray()
					}

					cache.set(pageUrl, bytes.inputStream().source(), contentType.toMimeTypeOrNull()).toUri()
				}

				else -> {
					if (isPrefetch) {
						downloadSlowdownDispatcher.delay(page.source)
					}
					val repo = getRepository(page.source)
					val request = repo.createPageRequest(pageUrl, page)
					val response = imageProxyInterceptor.interceptPageRequest(request, okHttp)
					Log.d(
						"JsPageResponse",
						"resp code=${response.code} protocol=${response.protocol} redirected=${response.priorResponse != null} reqUrl=${response.request.url} prior=${response.priorResponse?.code}"
					)
					response.ensureSuccess().use { resp ->
						resp.requireBody().withProgress(progress).use {
							cache.set(pageUrl, it.source(), it.contentType()?.toMimeType())
						}
					}.toUri()
				}
			}
		}
		val readyUri = if (settings.isReaderTranslationEnabled && sourceUri.isZipUri()) {
			convertBimap(sourceUri)
		} else {
			sourceUri
		}
		
		if (!settings.isReaderTranslationEnabled) {
			Log.d("ReaderTranslate", "PageLoader debug: translation disabled page=${page.id}")
			translationStatusUpdates.tryEmit(TranslationLayerStateEvent(page.id, TranslationLayerState.IDLE))
			return readyUri
		}
		
		val cachedRecord = translationProcessor.peekRendered(page, readyUri)
		if (cachedRecord != null) {
			Log.d("ReaderTranslate", "PageLoader debug: cached record found page=${page.id}")
			translationStatusUpdates.tryEmit(TranslationLayerStateEvent(page.id, TranslationLayerState.READY))
			return if (settings.isReaderTranslationShowTranslated) cachedRecord else readyUri
		}

		Log.d("ReaderTranslate", "PageLoader debug: scheduling translation for page=${page.id} (show=${settings.isReaderTranslationShowTranslated})")
		scheduleTranslation(page, readyUri)
		return readyUri
	}

	private fun scheduleTranslation(page: MangaPage, sourceUri: Uri) {
		synchronized(translationLock) {
			val existing = translationJobs[page.id]
			if (existing?.isActive == true) {
				Log.d("ReaderTranslate", "schedule skip active job page=${page.id}")
				return
			}
			translationStatusUpdates.tryEmit(TranslationLayerStateEvent(page.id, TranslationLayerState.GENERATING))
			translationJobs[page.id] = loaderScope.launch {
				val translated = runCatching {
					translationProcessor.process(page, sourceUri)
				}.onFailure {
					it.printStackTraceDebug()
				}.getOrDefault(sourceUri)
				if (translated != sourceUri) {
					translationStatusUpdates.tryEmit(TranslationLayerStateEvent(page.id, TranslationLayerState.READY))
					synchronized(tasks) {
						tasks.remove(page.id)
					}
					translationUpdates.tryEmit(page.id)
				} else {
					translationStatusUpdates.tryEmit(TranslationLayerStateEvent(page.id, TranslationLayerState.FAILED))
				}
				synchronized(translationLock) {
					translationJobs.remove(page.id)
				}
			}
		}
	}

	private fun isLowRam(): Boolean {
		return context.ramAvailable <= FileSize.MEGABYTES.convert(PREFETCH_MIN_RAM_MB, FileSize.BYTES)
	}

	private fun isTranslationBypassedForSource(source: MangaSource): Boolean {
		val sourceLang = source.getLocale()?.language?.lowercase().orEmpty()
		if (sourceLang.isBlank()) return false
		val targetLang = settings.readerTranslationTargetLanguage
			.lowercase()
			.substringBefore('-')
			.substringBefore('_')
		return sourceLang == targetLang
	}

	private fun Image.toImageSource(): ImageSource = if (this is BitmapImage) {
		ImageSource.cachedBitmap(toBitmap())
	} else {
		ImageSource.bitmap(toBitmap())
	}

	private fun Deferred<Uri>.isValid(): Boolean {
		return getCompletionResultOrNull()?.map { uri ->
			uri.exists() && uri.isTargetNotEmpty()
		}?.getOrDefault(false) != false
	}

	private class InternalErrorHandler : AbstractCoroutineContextElement(CoroutineExceptionHandler),
		CoroutineExceptionHandler {

		override fun handleException(context: CoroutineContext, exception: Throwable) {
			exception.printStackTraceDebug()
		}
	}

	companion object {

		private const val PROGRESS_UNDEFINED = -1f
		private const val PREFETCH_LIMIT_DEFAULT = 6
		private const val PREFETCH_MIN_RAM_MB = 80L

		fun createPageRequest(pageUrl: String, page: MangaPage): Request {
			val builder = Request.Builder()
				.url(pageUrl)
				.get()
				.header(CommonHeaders.ACCEPT, "image/avif,image/webp,image/png;q=0.9,image/jpeg,*/*;q=0.8")
				.cacheControl(CommonHeaders.CACHE_CONTROL_NO_STORE)
				.tag(MangaSource::class.java, page.source)
				// 传递 source 名称，便于下游拦截器获知来源
				.header(CommonHeaders.MANGA_SOURCE, page.source.name)
			page.headers?.forEach { (k, v) -> builder.header(k, v) }
			val lowerHeaders = page.headers?.keys?.associateBy { it.lowercase() } ?: emptyMap()
			if (!lowerHeaders.containsKey("referer") &&
				(pageUrl.contains("gold-usergeneratedcontent.net") || pageUrl.contains("hitomi.la"))
			) {
				builder.header("Referer", "https://hitomi.la/")
			}
			val request = builder.build()
			Log.d(
				"JsPageRequest",
				"build request url=$pageUrl headers=${request.headers} source=${page.source.name}"
			)
			return request
		}

		// Backward-compatible helper; strongly prefer the MangaPage overload to carry headers.
		fun createPageRequest(pageUrl: String, mangaSource: MangaSource, headers: Map<String, String>? = null): Request {
			val builder = Request.Builder()
				.url(pageUrl)
				.get()
				.header(CommonHeaders.ACCEPT, "image/avif,image/webp,image/png;q=0.9,image/jpeg,*/*;q=0.8")
				.cacheControl(CommonHeaders.CACHE_CONTROL_NO_STORE)
				// 传递来源，便于下游拦截器注入默认 UA/Referer
				.header(CommonHeaders.MANGA_SOURCE, mangaSource.name)
				.tag(MangaSource::class.java, mangaSource)
			headers?.forEach { (k, v) -> builder.header(k, v) }
			return builder.build()
		}


		@Blocking
		private fun Uri.exists(): Boolean = when {
			isFileUri() -> toFile().exists()
			isZipUri() -> {
				val file = File(requireNotNull(schemeSpecificPart))
				file.exists() && ZipFile(file).use { it.getEntry(fragment) != null }
			}

			else -> false
		}

		@Blocking
		private fun Uri.isTargetNotEmpty(): Boolean = when {
			isFileUri() -> toFile().isNotEmpty()
			isZipUri() -> {
				val file = File(requireNotNull(schemeSpecificPart))
				file.exists() && ZipFile(file).use { (it.getEntry(fragment)?.size ?: 0L) != 0L }
			}

			else -> false
		}
	}
}
