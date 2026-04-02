package org.skepsun.kototoro.reader.translate.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.os.SystemClock
import androidx.annotation.WorkerThread
import androidx.collection.LruCache
import androidx.collection.LongSparseArray
import androidx.core.net.toFile
import androidx.core.net.toUri
import dagger.hilt.android.scopes.ActivityRetainedScoped
import eu.kanade.tachiyomi.network.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.skepsun.kototoro.core.LocalizedAppContext
import org.skepsun.kototoro.core.image.BitmapDecoderCompat
import org.skepsun.kototoro.core.model.getLocale
import org.skepsun.kototoro.core.network.ContentHttpClient
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ReaderOcrEngine
import org.skepsun.kototoro.core.prefs.ReaderTranslationMode
import org.skepsun.kototoro.core.util.ext.isFileUri
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.ext.toMimeTypeOrNull
import org.skepsun.kototoro.local.data.LocalStorageCache
import org.skepsun.kototoro.local.data.PageCache
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.reader.translate.data.ReaderTranslationTextCache
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import org.skepsun.kototoro.core.util.ext.awaitCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import okio.source
import java.security.MessageDigest
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@ActivityRetainedScoped
class ReaderPageTranslationProcessor @Inject constructor(
	@LocalizedAppContext private val context: Context,
	private val settings: AppSettings,
	@PageCache private val cache: LocalStorageCache,
	private val textCache: ReaderTranslationTextCache,
	@ContentHttpClient
	private val okHttpClient: OkHttpClient,
	private val mlKitOcrEngine: MlKitReaderOcrEngine,
	private val paddleOcrEngine: PaddleReaderOcrEngine,
	private val tfliteOcrEngine: TfLiteReaderOcrEngine,
	private val hybridOcrEngine: HybridReaderOcrEngine,
	private val ncnnOcrEngine: NcnnReaderOcrEngine,
	private val onnxBubbleDetectorEngine: OnnxBubbleDetectorEngine,
	private val onnxTranslationEngine: OnnxReaderTranslationEngine,
	private val debugLogStore: ReaderTranslationDebugLogStore,
) {

	private val processingSemaphore = Semaphore(MAX_PARALLEL_TRANSLATION_PAGES)
	private val pageStateLock = Any()
	private val renderedSourceMap = LruCache<String, String>(512)
	private val pageRenderEpochs = LongSparseArray<Int>()
	private val loggingPageId = ThreadLocal<Long?>()
	@Volatile
	private var renderCacheEpoch: Int = 0
	private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		style = Paint.Style.FILL
		alpha = 242
	}
	private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.BLACK
		textAlign = Paint.Align.LEFT
	}
	private val bubbleRoiOcrCoordinator by lazy(LazyThreadSafetyMode.NONE) {
		ReaderBubbleRoiOcrCoordinator(
			settings = settings,
			recognizeTextByEngine = { engine, request -> recognizeTextByEngine(engine, request) },
			composeGroupedText = ::composeGroupedText,
			mergeRects = ::mergeRects,
			isLikelySpeechBubbleRegion = ::isLikelySpeechBubbleRegion,
			dp = ::dp,
			log = ::log,
		)
	}
	private val bubbleGroupingCoordinator by lazy(LazyThreadSafetyMode.NONE) {
		ReaderBubbleGroupingCoordinator(
			settings = settings,
			onnxBubbleDetectorEngine = onnxBubbleDetectorEngine,
			heuristicGroupFragments = ::groupFragmentsByBubble,
			shouldMergeFragments = ::shouldMergeFragments,
			mergeRects = ::mergeRects,
			rectArea = ::rectArea,
			dp = ::dp,
			log = ::log,
			formatError = ::oneLine,
			maxIndividualFallbackFragments = MAX_INDIVIDUAL_FALLBACK_FRAGMENTS,
			maxIndividualFallbackRatio = MAX_INDIVIDUAL_FALLBACK_RATIO,
			maxDetectedGroupFragments = MAX_DETECTED_GROUP_FRAGMENTS,
		)
	}
	private val bubbleRenderCoordinator by lazy(LazyThreadSafetyMode.NONE) {
		ReaderBubbleRenderCoordinator(
			isLikelyGarbledText = ::isLikelyGarbledText,
			shouldSuppressRenderedBubble = ::shouldSuppressRenderedBubble,
			isLikelySpeechBubbleRegion = ::isLikelySpeechBubbleRegion,
			prepareTranslatedBubble = ::prepareTranslatedBubble,
			qualityFilterEnabled = { settings.isReaderTranslationQualityFilterEnabled },
			log = ::log,
			oneLine = ::oneLine,
		)
	}
	private val translationCoordinator by lazy(LazyThreadSafetyMode.NONE) {
		ReaderTranslationCoordinator(
			settings = settings,
			textCache = textCache,
			onnxTranslationEngine = onnxTranslationEngine,
			okHttpClient = okHttpClient,
			jsonMediaType = JSON_MEDIA_TYPE,
			defaultOpenAiModel = DEFAULT_OPENAI_MODEL,
			openAiTranslationSystemPrompt = OPENAI_TRANSLATION_SYSTEM_PROMPT,
			maxOpenAiBatchSize = MAX_OPENAI_BATCH_SIZE,
			thinkTagRegex = THINK_TAG_REGEX,
			buildTextCacheKey = ::buildTextCacheKey,
			sanitizeTranslation = ::sanitizeTranslation,
			isAcceptableTranslation = ::isAcceptableTranslation,
			log = ::log,
			oneLine = ::oneLine,
		)
	}
	private val ocrPipelineCoordinator by lazy(LazyThreadSafetyMode.NONE) {
		ReaderOcrPipelineCoordinator(
			loadPageText = ::loadPageTextWithCache,
			detectBubbleRects = onnxBubbleDetectorEngine::detectAttempt,
			groupFragmentsForTranslation = ::groupFragmentsForTranslation,
			recognizeBubbleTextsByRoi = ::recognizeBubbleTextsByRoi,
			heuristicGroupFragments = ::groupFragmentsByBubble,
		)
	}

	fun clearAllCaches() {
		textCache.clear()
		debugLogStore.clearAll()
		synchronized(pageStateLock) {
			pageRenderEpochs.clear()
		}
		renderCacheEpoch += 1
		log { "translation caches cleared epoch=$renderCacheEpoch" }
	}

	fun clearPageCaches(pageId: Long) {
		debugLogStore.clearPage(pageId)
		synchronized(pageStateLock) {
			val current = pageRenderEpochs[pageId] ?: 0
			pageRenderEpochs.put(pageId, current + 1)
		}
		log { "translation page cache cleared page=$pageId" }
	}

	suspend fun peekRendered(page: ContentPage, sourceUri: Uri): Uri? {
		if (!settings.isReaderTranslationEnabled) {
			return null
		}
		val sourceLang = resolveSourceLanguage(page)
		val targetLang = settings.readerTranslationTargetLanguage.normalizeReaderTranslationLanguageTag() ?: "zh"
		if (sourceLang == targetLang) {
			return null
		}
		val renderCacheKey = buildRenderedCacheKey(
			pageUrl = page.url,
			sourceUri = sourceUri.toString(),
			sourceLang = sourceLang,
			targetLang = targetLang,
			pageEpoch = getPageRenderEpoch(page.id),
		)
		return cache[renderCacheKey]?.toUri()?.also {
			appendPageLog(page.id, "metric.render_cache.hit=1")
			rememberRenderedSource(it, sourceUri)
		}
	}

	fun peekSourceOfRendered(renderedUri: Uri): Uri? {
		return renderedSourceMap[renderedUri.toString()]?.toUri()
	}

	suspend fun process(page: ContentPage, sourceUri: Uri): Uri {
		val enabled = settings.isReaderTranslationEnabled
		val showTranslated = settings.isReaderTranslationShowTranslated
		Log.d(LOG_TAG, "process debug: page=${page.id} enabled=$enabled showTranslated=$showTranslated")
		if (!enabled) {
			return sourceUri
		}
		val sourceLang = resolveSourceLanguage(page)
		val targetLang = settings.readerTranslationTargetLanguage.normalizeReaderTranslationLanguageTag() ?: "zh"
		Log.d(LOG_TAG, "process debug: page=${page.id} sourceLang=$sourceLang targetLang=$targetLang")
		if (sourceLang == targetLang) {
			return sourceUri
		}
		val localUri = ensureLocalFileUri(sourceUri) ?: run {
			log { "process skip: cannot localize uri=$sourceUri" }
			return sourceUri
		}
		val renderCacheKey = buildRenderedCacheKey(
			pageUrl = page.url,
			sourceUri = sourceUri.toString(),
			sourceLang = sourceLang,
			targetLang = targetLang,
			pageEpoch = getPageRenderEpoch(page.id),
		)
		cache[renderCacheKey]?.let {
			return it.toUri().also { rendered ->
				rememberRenderedSource(rendered, localUri)
			}
		}
		log { "process start page=${page.id} sourceLang=$sourceLang targetLang=$targetLang ocr=${settings.readerTranslationOcrEngine}" }
		return withContext(loggingPageId.asContextElement(page.id)) {
			processingSemaphore.withPermit {
				appendPageLog(page.id, "process start page=${page.id}")
				cache[renderCacheKey]?.let {
					appendPageLog(page.id, "metric.render_cache.hit=1")
					return@withPermit it.toUri().also { rendered ->
						rememberRenderedSource(rendered, localUri)
					}
				}
				runCatching {
					processImpl(page.id, localUri, renderCacheKey, sourceLang, targetLang)
				}.onFailure {
					it.printStackTraceDebug()
					appendPageLog(page.id, "process failed: ${it.javaClass.simpleName}: ${it.message.orEmpty()}")
					appendPageLog(page.id, "fail_code=$FAIL_CODE_PROCESS_EXCEPTION")
				}.getOrDefault(sourceUri)
			}
		}
	}

	fun getPageDebugLog(pageId: Long): String {
		return debugLogStore.get(pageId)
	}

	fun observeDebugLogUpdates(): Flow<Long> {
		return debugLogStore.observeUpdates()
	}

	@WorkerThread
	private suspend fun processImpl(
		pageId: Long,
		sourceUri: Uri,
		renderCacheKey: String,
		sourceLang: String,
		targetLang: String,
	): Uri {
		val totalStartMs = SystemClock.elapsedRealtime()
		var ocrCacheHit = false
		var ocrDurationMs = -1L
		var translateDurationMs = -1L
		var renderDurationMs = -1L
		var ocrBlocks = 0
		var bubbleCount = 0
		var renderedBubbleCount = 0
		val bubbleGroupingEnabled = settings.isReaderTranslationBubbleGroupingEnabled
		var bubbleDetectorCandidates = 0
		var bubbleDetectorMatchedFragments = 0
		var bubbleDetectorUsedGroups = 0
		var bubbleDetectorSubdividedGroups = 0
		var bubbleDetectorSubdividedFragments = 0
		var bubbleDetectorCoverageRate = 0f
		var bubbleDetectorEngine = "cv"
		var bubbleDetectorModel = ""
		var bubbleDetectorRawBoxes = 0
		var bubbleDetectorTotalMs = 0L
		var bubbleDetectorFallbackReason = ""
		var bubbleGroupingFallbackFragments = 0
		var bubbleGroupingFallbackGroups = 0
		var bubbleGroupingFallbackMode = "heuristic"
		var roiRequestCount = 0
		var roiSuccessCount = 0
		var roiFallbackCount = 0
		var roiDurationMs = 0L
		var roiCoverageArea = 0f
		var ocrPipelineStrategy = OcrPipelineStrategy.PAGE_FIRST
		var ocrPipelineFallbackReason = ""
		var roiFirstDetectedBoxes = 0
		var processResult = "source"
		appendPageLog(pageId, "metric.render_cache.hit=0")

		// OCR result caching: skip re-OCR if we already processed this image
		try {
			val sourceBitmap = runInterruptible(Dispatchers.IO) {
				BitmapDecoderCompat.decode(sourceUri.toFile())
			}
			val bitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
			if (bitmap !== sourceBitmap) {
				sourceBitmap.recycle()
			}
			val canvas = Canvas(bitmap)
			val ocrPipeline = ocrPipelineCoordinator.execute(
				sourceUri = sourceUri,
				sourceLang = sourceLang,
				pageId = pageId,
				bitmap = bitmap,
				strategy = OcrPipelineStrategy.ROI_FIRST_FALLBACK,
				catchAllEnabled = settings.isReaderTranslationBubbleCatchAllEnabled,
			)
			ocrPipelineStrategy = ocrPipeline.strategy
			ocrPipelineFallbackReason = ocrPipeline.fallbackReason
			roiFirstDetectedBoxes = ocrPipeline.roiFirstDetectedBoxCount
			ocrCacheHit = ocrPipeline.pageOcr?.cacheHit ?: false
			ocrDurationMs = ocrPipeline.pageOcr?.durationMs ?: 0L
			ocrBlocks = ocrPipeline.pageTextBlocks.size
			val groupingResult = ocrPipeline.groupingResult
			if (groupingResult == null) {
				log { "fail_code=$FAIL_CODE_OCR_EMPTY" }
				bitmap.recycle()
				return sourceUri
			}
			bubbleDetectorCandidates = groupingResult.detectorCandidateCount
			bubbleDetectorMatchedFragments = groupingResult.detectorMatchedFragmentCount
			bubbleDetectorUsedGroups = groupingResult.detectorUsedGroupCount
			bubbleDetectorSubdividedGroups = groupingResult.detectorSubdividedGroupCount
			bubbleDetectorSubdividedFragments = groupingResult.detectorSubdividedFragmentCount
			bubbleDetectorCoverageRate = groupingResult.detectorCoverageRate
			bubbleDetectorEngine = groupingResult.detectorEngine
			bubbleDetectorModel = groupingResult.detectorModelId
			bubbleDetectorRawBoxes = groupingResult.detectorRawBoxCount
			bubbleDetectorTotalMs = groupingResult.detectorTotalMs
			bubbleDetectorFallbackReason = groupingResult.detectorFallbackReason
			bubbleGroupingFallbackFragments = groupingResult.fallbackFragmentCount
			bubbleGroupingFallbackGroups = groupingResult.fallbackGroupCount
			bubbleGroupingFallbackMode = groupingResult.fallbackMode
			val roiResult = ocrPipeline.roiResult
			roiRequestCount = roiResult.requestCount
			roiSuccessCount = roiResult.successCount
			roiFallbackCount = roiResult.fallbackCount
			roiDurationMs = roiResult.totalMs
			roiCoverageArea = roiResult.coverageArea
			val bubbleInputs = buildBubbleInputs(
				groups = groupingResult.groups,
				roiResult = roiResult,
				sourceLang = sourceLang,
				targetLang = targetLang,
			)
			bubbleCount = bubbleInputs.size
			val translateStartMs = SystemClock.elapsedRealtime()
			val translatedMap = translateBlocksCached(
				texts = bubbleInputs.map { it.sourceText },
				sourceLang = sourceLang,
				targetLang = targetLang,
			)
			translateDurationMs = SystemClock.elapsedRealtime() - translateStartMs
			val renderStartMs = SystemClock.elapsedRealtime()
			val renderPreparation = bubbleRenderCoordinator.prepareBubbles(
				bubbleInputs = bubbleInputs,
				translatedMap = translatedMap,
				targetLang = targetLang,
				bitmap = bitmap,
			)
			val preparedBubbles = renderPreparation.preparedBubbles
			val nonEmptyTranslatedCount = renderPreparation.nonEmptyTranslatedCount
			// Two-pass render: draw all bubble backgrounds first, then all texts to avoid later bubbles covering earlier texts.
			for (bubble in preparedBubbles) {
				drawBubbleBackground(canvas, bubble)
			}
			for (bubble in preparedBubbles) {
				drawBubbleText(canvas, bubble)
			}
			log { "render done translatedBubbles=${preparedBubbles.size}" }
			renderedBubbleCount = preparedBubbles.size
			if (preparedBubbles.isEmpty()) {
				val failCode = if (nonEmptyTranslatedCount == 0) {
					FAIL_CODE_TRANSLATE_EMPTY
				} else {
					FAIL_CODE_RENDER_FILTERED
				}
				renderDurationMs = SystemClock.elapsedRealtime() - renderStartMs
				log { "fail_code=$failCode" }
				bitmap.recycle()
				return sourceUri
			}
			val output = cache.set(renderCacheKey, bitmap).toUri()
			rememberRenderedSource(output, sourceUri)
			renderDurationMs = SystemClock.elapsedRealtime() - renderStartMs
			processResult = "rendered"
			bitmap.recycle()
			return output
		} finally {
			log { "metric.ocr.cache_hit=${if (ocrCacheHit) 1 else 0}" }
			if (ocrDurationMs >= 0L) log { "metric.ocr.total_ms=$ocrDurationMs" }
			log { "metric.ocr.pipeline.strategy=${ocrPipelineStrategy.name.lowercase()}" }
			log { "metric.ocr.pipeline.fallback_reason=${ocrPipelineFallbackReason.ifBlank { "none" }}" }
			log { "metric.ocr.pipeline.roi_first_detected_boxes=$roiFirstDetectedBoxes" }
			log { "metric.ocr.blocks=$ocrBlocks" }
			log { "metric.translation.bubbles=$bubbleCount" }
			log { "metric.bubble.grouping.enabled=$bubbleGroupingEnabled" }
			log { "metric.bubble.grouping.detector_groups=$bubbleDetectorUsedGroups" }
			log { "metric.bubble.grouping.fallback_fragments=$bubbleGroupingFallbackFragments" }
			log { "metric.bubble.grouping.fallback_groups=$bubbleGroupingFallbackGroups" }
			log { "metric.bubble.grouping.fallback_mode=$bubbleGroupingFallbackMode" }
			log { "metric.bubble.detector.candidates=$bubbleDetectorCandidates" }
			log { "metric.bubble.detector.matched_fragments=$bubbleDetectorMatchedFragments" }
			log { "metric.bubble.detector.used_groups=$bubbleDetectorUsedGroups" }
			log { "metric.bubble.detector.subdivided_groups=$bubbleDetectorSubdividedGroups" }
			log { "metric.bubble.detector.subdivided_fragments=$bubbleDetectorSubdividedFragments" }
			log { "metric.bubble.detector.coverage_rate=$bubbleDetectorCoverageRate" }
			log { "metric.bubble.detector.engine=$bubbleDetectorEngine" }
			log { "metric.bubble.detector.model=${bubbleDetectorModel.ifBlank { "none" }}" }
			log { "metric.bubble.detector.raw_boxes=$bubbleDetectorRawBoxes" }
			log { "metric.bubble.detector.total_ms=$bubbleDetectorTotalMs" }
			log { "metric.bubble.detector.fallback_reason=${bubbleDetectorFallbackReason.ifBlank { "none" }}" }
			log { "metric.ocr.roi.request_count=$roiRequestCount" }
			log { "metric.ocr.roi.success_count=$roiSuccessCount" }
			log { "metric.ocr.roi.fallback_count=$roiFallbackCount" }
			log { "metric.ocr.roi.total_ms=$roiDurationMs" }
			log { "metric.ocr.roi.coverage_area=$roiCoverageArea" }
			if (translateDurationMs >= 0L) log { "metric.translation.total_ms=$translateDurationMs" }
			if (renderDurationMs >= 0L) log { "metric.render.total_ms=$renderDurationMs" }
			log { "metric.render.translated_bubbles=$renderedBubbleCount" }
			log { "metric.process.result=$processResult" }
			log { "metric.process.total_ms=${SystemClock.elapsedRealtime() - totalStartMs}" }
		}
	}

	private fun rememberRenderedSource(renderedUri: Uri, sourceUri: Uri) {
		renderedSourceMap.put(renderedUri.toString(), sourceUri.toString())
	}

	private suspend fun loadPageTextWithCache(sourceUri: Uri, sourceLang: String, pageId: Long): PageOcrLoadResult {
		val startMs = SystemClock.elapsedRealtime()
		val ocrCacheKey = buildOcrCacheKey(sourceUri.toString(), sourceLang)
		val cached = textCache[ocrCacheKey]
		if (cached != null) {
			log { "ocr cache hit" }
			val textBlocks = deserializeOcrBlocks(cached)
			log { "ocr done blocks=${textBlocks.size}" }
			return PageOcrLoadResult(
				textBlocks = textBlocks,
				cacheHit = true,
				durationMs = SystemClock.elapsedRealtime() - startMs,
			)
		}
		val textBlocks = recognizeTextWithFallback(sourceUri, sourceLang, pageId)
		if (textBlocks.isNotEmpty()) {
			textCache[ocrCacheKey] = serializeOcrBlocks(textBlocks)
		}
		log { "ocr done blocks=${textBlocks.size}" }
		return PageOcrLoadResult(
			textBlocks = textBlocks,
			cacheHit = false,
			durationMs = SystemClock.elapsedRealtime() - startMs,
		)
	}

	private suspend fun recognizeTextWithFallback(sourceUri: Uri, sourceLang: String, pageId: Long): List<OcrTextBlock> {
		val primary = when (settings.readerTranslationOcrEngine) {
			ReaderOcrEngine.PADDLE -> ReaderOcrEngine.NCNN
			else -> settings.readerTranslationOcrEngine
		}
		val minAcceptableBlocks = when {
			sourceLang.startsWith("ja") -> 3
			sourceLang.startsWith("zh") || sourceLang.startsWith("ko") -> 2
			else -> 1
		}
		val order = linkedSetOf<ReaderOcrEngine>().apply {
			add(primary)
			// Avoid aggressively falling back to heavy local models to prevent unexpected memory exhaustion.
			// Users specifically selecting MLKit or TFLite should not silently load NCNN in the background.
		}
		var bestResult: List<OcrTextBlock> = emptyList()
		var bestEngine: ReaderOcrEngine? = null
		for (engine in order) {
			val attemptStartMs = SystemClock.elapsedRealtime()
			val result = runCatching {
				recognizeTextByEngine(engine, sourceUri, sourceLang, pageId)
			}.onFailure {
				it.printStackTraceDebug()
			}.getOrDefault(emptyList())
			val attemptDurationMs = SystemClock.elapsedRealtime() - attemptStartMs
			log { "metric.ocr.attempt.${engine.name.lowercase()}.ms=$attemptDurationMs" }
			log { "metric.ocr.attempt.${engine.name.lowercase()}.blocks=${result.size}" }
			if (result.isNotEmpty()) {
				if (result.size > bestResult.size) {
					bestResult = result
					bestEngine = engine
				}
				if (result.size >= minAcceptableBlocks || engine == ReaderOcrEngine.MLKIT) {
					log { "metric.ocr.selected_engine=${engine.name.lowercase()}" }
					log { "metric.ocr.selected_blocks=${result.size}" }
					log { "ocr engine=$engine blocks=${result.size}" }
					return result
				}
				log {
					"ocr engine=$engine blocks=${result.size}, below threshold=$minAcceptableBlocks, trying fallback"
				}
				continue
			}
			log { "ocr engine=$engine blocks=0, trying fallback" }
		}
		if (bestResult.isNotEmpty()) {
			bestEngine?.let {
				log { "metric.ocr.selected_engine=${it.name.lowercase()}" }
			}
			log { "metric.ocr.selected_blocks=${bestResult.size}" }
			log { "ocr fallback use best engine=$bestEngine blocks=${bestResult.size}" }
		}
		return bestResult
	}

	private suspend fun recognizeTextByEngine(
		engine: ReaderOcrEngine,
		sourceUri: Uri,
		sourceLang: String,
		pageId: Long,
	): List<OcrTextBlock> {
		return recognizeTextByEngine(
			engine = engine,
			request = OcrRequest(
				sourceUri = sourceUri,
				sourceLang = sourceLang,
				pageId = pageId,
				requestType = OcrRequestType.PAGE,
				debugTag = "page:$pageId:${engine.name.lowercase()}",
			),
		)
	}

	private suspend fun recognizeTextByEngine(
		engine: ReaderOcrEngine,
		request: OcrRequest,
	): List<OcrTextBlock> {
		return when (engine) {
			ReaderOcrEngine.MLKIT -> mlKitOcrEngine.recognize(request)
			ReaderOcrEngine.PADDLE -> emptyList()
			ReaderOcrEngine.TFLITE -> tfliteOcrEngine.recognize(request)
			ReaderOcrEngine.HYBRID -> hybridOcrEngine.recognize(request)
			ReaderOcrEngine.NCNN -> ncnnOcrEngine.recognize(request)
		}
	}

	private suspend fun translateBlocksCached(
		texts: List<String>,
		sourceLang: String,
		targetLang: String,
	): Map<String, String> {
		return translationCoordinator.translateBlocksCached(
			texts = texts,
			sourceLang = sourceLang,
			targetLang = targetLang,
		)
	}

	private suspend fun ensureLocalFileUri(sourceUri: Uri): Uri? {
		if (sourceUri.isFileUri()) return sourceUri
		return runCatching {
			val key = "reader_translate_src_${sourceUri}".sha256()
			cache[key]?.toUri()?.takeIf { it.isFileUri() }?.let { return it }
			when (sourceUri.scheme?.lowercase()) {
				"content", "android.resource" -> {
					val type = context.contentResolver.getType(sourceUri)?.toMimeTypeOrNull()
					context.contentResolver.openInputStream(sourceUri)?.use { input ->
						cache.set(key, input.source(), type).toUri()
					}
				}

				"http", "https" -> {
					val request = Request.Builder().url(sourceUri.toString()).get().build()
					okHttpClient.newCall(request).await().use { resp ->
						if (!resp.isSuccessful) return null
						val body = resp.body ?: return null
						val mimeType = body.contentType()?.toString()?.toMimeTypeOrNull()
						cache.set(key, body.source(), mimeType).toUri()
					}
				}

				else -> null
			}
		}.onFailure {
			it.printStackTraceDebug()
		}.getOrNull()
	}

	private suspend fun groupFragmentsForTranslation(
		fragments: List<TextFragment>,
		bitmap: Bitmap,
	): BubbleGroupingResult {
		return bubbleGroupingCoordinator.groupFragmentsForTranslation(
			fragments = fragments,
			bitmap = bitmap,
		)
	}

	private suspend fun recognizeBubbleTextsByRoi(
		groups: List<GroupedBubbleSource>,
		sourceUri: Uri,
		sourceLang: String,
		pageId: Long,
		bitmap: Bitmap,
	): BubbleRoiOcrResult {
		return bubbleRoiOcrCoordinator.recognize(
			groups = groups,
			sourceUri = sourceUri,
			sourceLang = sourceLang,
			pageId = pageId,
			bitmap = bitmap,
			maxRequestsPerPage = MAX_ROI_OCR_REQUESTS_PER_PAGE,
		)
	}

	private fun buildBubbleInputs(
		groups: List<GroupedBubbleSource>,
		roiResult: BubbleRoiOcrResult,
		sourceLang: String,
		targetLang: String,
	): List<BubbleInput> {
		return groups.mapIndexedNotNull { index, group ->
			val mergedRect = group.bubbleRect ?: mergeRects(group.fragments.map { it.rect }) ?: return@mapIndexedNotNull null
			val sourceText = (roiResult.textsByGroupIndex[index] ?: composeGroupedText(group.fragments, sourceLang)).trim()
			if (sourceText.isBlank()) {
				return@mapIndexedNotNull null
			}
			val verticalPreferred = isVerticalTargetLanguage(targetLang) &&
				sourceLang.startsWith("ja") &&
				(isLikelyColumnLayout(group.fragments) || mergedRect.height() > mergedRect.width() * 13 / 10)
			BubbleInput(
				rect = mergedRect,
				sourceText = sourceText,
				verticalPreferred = verticalPreferred,
				classId = group.classId,
			)
		}
	}

	private fun groupFragmentsByBubble(fragments: List<TextFragment>, bitmap: Bitmap): List<List<TextFragment>> {
		if (fragments.isEmpty()) return emptyList()
		val parent = IntArray(fragments.size) { it }

		fun find(x: Int): Int {
			var cur = x
			while (parent[cur] != cur) {
				parent[cur] = parent[parent[cur]]
				cur = parent[cur]
			}
			return cur
		}

		fun union(a: Int, b: Int) {
			val ra = find(a)
			val rb = find(b)
			if (ra != rb) parent[rb] = ra
		}

		val mergePad = dp(24f)
		for (i in fragments.indices) {
			for (j in i + 1 until fragments.size) {
				val ra = expandRect(fragments[i].rect, mergePad)
				val rb = expandRect(fragments[j].rect, mergePad)
				if (Rect.intersects(ra, rb) && shouldMergeFragments(fragments[i], fragments[j], bitmap)) {
					union(i, j)
				}
			}
		}

		val groups = linkedMapOf<Int, MutableList<TextFragment>>()
		for (i in fragments.indices) {
			val root = find(i)
			groups.getOrPut(root) { mutableListOf() }.add(fragments[i])
		}
		return groups.values.toList()
	}

	private fun shouldMergeFragments(a: TextFragment, b: TextFragment, bitmap: Bitmap): Boolean {
		val grouping = groupingTuningLevel()
		val acx = a.rect.centerX().toFloat()
		val acy = a.rect.centerY().toFloat()
		val bcx = b.rect.centerX().toFloat()
		val bcy = b.rect.centerY().toFloat()
		val dx = kotlin.math.abs(acx - bcx)
		val dy = kotlin.math.abs(acy - bcy)
		val minW = min(a.rect.width(), b.rect.width()).toFloat().coerceAtLeast(1f)
		val minH = min(a.rect.height(), b.rect.height()).toFloat().coerceAtLeast(1f)
		val xOverlap = overlapLen(a.rect.left, a.rect.right, b.rect.left, b.rect.right).toFloat()
		val yOverlap = overlapLen(a.rect.top, a.rect.bottom, b.rect.top, b.rect.bottom).toFloat()
		val xOverlapRatio = xOverlap / minW
		val yOverlapRatio = yOverlap / minH
		val gapX = axisGap(a.rect.left, a.rect.right, b.rect.left, b.rect.right).toFloat()
		val gapY = axisGap(a.rect.top, a.rect.bottom, b.rect.top, b.rect.bottom).toFloat()
		val maxGapScale = when (grouping) {
			TuningLevel.STRICT -> 0.70f
			TuningLevel.BALANCED -> 0.85f
			TuningLevel.RELAXED -> 1.05f
		}
		val minOverlapRatio = when (grouping) {
			TuningLevel.STRICT -> 0.26f
			TuningLevel.BALANCED -> 0.18f
			TuningLevel.RELAXED -> 0.12f
		}
		val inflationLimit = when (grouping) {
			TuningLevel.STRICT -> 1.85f
			TuningLevel.BALANCED -> 2.2f
			TuningLevel.RELAXED -> 2.8f
		}
		val maxGapX = minW * maxGapScale + dp(3f)
		val maxGapY = minH * maxGapScale + dp(3f)

		// At least one axis should have meaningful overlap, otherwise nearby panels are easily merged.
		if (xOverlapRatio < minOverlapRatio && yOverlapRatio < minOverlapRatio) return false
		// Distance gate, normalized by glyph size.
		if (gapX > maxGapX || gapY > maxGapY) return false
		// Reject pair if merged area grows too much compared with two source boxes.
		val merged = Rect(
			min(a.rect.left, b.rect.left),
			min(a.rect.top, b.rect.top),
			max(a.rect.right, b.rect.right),
			max(a.rect.bottom, b.rect.bottom),
		)
		val sumArea = rectArea(a.rect) + rectArea(b.rect)
		val mergedArea = rectArea(merged)
		if (sumArea > 0f) {
			val inflation = mergedArea / sumArea
			if (inflation > inflationLimit && xOverlapRatio < 0.45f && yOverlapRatio < 0.45f) return false
		}
		// Detect separator line between two fragments (panel border / gutter) and block cross-panel merge.
		// Separator check is only meaningful for fragments with clear gap.
		val separatorMinGapDp = when (grouping) {
			TuningLevel.STRICT -> 4f
			TuningLevel.BALANCED -> 6f
			TuningLevel.RELAXED -> 8f
		}
		if (max(gapX, gapY) >= dp(separatorMinGapDp) && hasStrongSeparatorBetween(bitmap, a.rect, b.rect)) return false

		// Final center-distance guard.
		val distanceScale = when (grouping) {
			TuningLevel.STRICT -> 1.9f
			TuningLevel.BALANCED -> 2.2f
			TuningLevel.RELAXED -> 2.6f
		}
		return dx <= minW * distanceScale && dy <= minH * distanceScale
	}

	private fun overlapLen(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Int {
		return (min(aEnd, bEnd) - max(aStart, bStart)).coerceAtLeast(0)
	}

	private fun axisGap(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Int {
		return when {
			aEnd < bStart -> bStart - aEnd
			bEnd < aStart -> aStart - bEnd
			else -> 0
		}
	}

	private fun rectArea(rect: Rect): Float {
		return (rect.width().coerceAtLeast(0) * rect.height().coerceAtLeast(0)).toFloat()
	}

	private fun hasStrongSeparatorBetween(bitmap: Bitmap, a: Rect, b: Rect): Boolean {
		val grouping = groupingTuningLevel()
		val x0 = a.centerX().coerceIn(0, bitmap.width - 1)
		val y0 = a.centerY().coerceIn(0, bitmap.height - 1)
		val x1 = b.centerX().coerceIn(0, bitmap.width - 1)
		val y1 = b.centerY().coerceIn(0, bitmap.height - 1)
		val dx = (x1 - x0).toFloat()
		val dy = (y1 - y0).toFloat()
		val dist = kotlin.math.sqrt(dx * dx + dy * dy)
		if (dist < dp(6f)) return false

		val steps = max(18, (dist / dp(2f).coerceAtLeast(1)).toInt())
		var brightRun = 0
		val runThreshold = when (grouping) {
			TuningLevel.STRICT -> max(6, steps / 4)
			TuningLevel.BALANCED -> max(8, steps / 3)
			TuningLevel.RELAXED -> max(10, steps / 2)
		}
		for (i in 1 until steps) {
			val t = i / steps.toFloat()
			val x = (x0 + dx * t).toInt().coerceIn(0, bitmap.width - 1)
			val y = (y0 + dy * t).toInt().coerceIn(0, bitmap.height - 1)
			val pixel = bitmap.getPixel(x, y)
			val lum = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
			if (lum >= 246) {
				brightRun++
			} else {
				brightRun = 0
			}
			// Prefer white gutter detection; dark-run is too sensitive to text strokes.
			if (brightRun >= runThreshold) {
				return true
			}
		}
		return false
	}

	private fun composeGroupedText(group: List<TextFragment>, sourceLang: String): String {
		if (group.isEmpty()) return ""
		val isJa = sourceLang.startsWith("ja")
		val sorted = if (isJa) {
			group.sortedWith(
				compareByDescending<TextFragment> { it.rect.centerX() }
					.thenBy { it.rect.centerY() }
			)
		} else {
			group.sortedWith(compareBy<TextFragment> { it.rect.centerY() }.thenBy { it.rect.centerX() })
		}
		val separator = if (isJa && isLikelyColumnLayout(group)) "\n" else ""
		return sorted.joinToString(separator) { it.text.trim() }.trim()
	}

	private fun isLikelyColumnLayout(group: List<TextFragment>): Boolean {
		if (group.size < 2) return false
		val avgWidth = group.map { it.rect.width() }.average()
		val avgHeight = group.map { it.rect.height() }.average()
		val xBuckets = group.map { it.rect.centerX() / max(1, dp(24f)) }.toSet().size
		return avgHeight > avgWidth * 1.2 && xBuckets >= 2
	}

	private fun isVerticalTargetLanguage(targetLang: String): Boolean {
		val normalized = targetLang.trim().lowercase()
		return normalized.startsWith("zh") || normalized.startsWith("ja")
	}

	private fun mergeRects(rects: List<Rect>): Rect? {
		if (rects.isEmpty()) return null
		var left = rects[0].left
		var top = rects[0].top
		var right = rects[0].right
		var bottom = rects[0].bottom
		for (i in 1 until rects.size) {
			left = min(left, rects[i].left)
			top = min(top, rects[i].top)
			right = max(right, rects[i].right)
			bottom = max(bottom, rects[i].bottom)
		}
		return Rect(left, top, right, bottom)
	}

	private fun expandRect(rect: Rect, pad: Int): Rect {
		return Rect(rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad)
	}

	private fun prepareTranslatedBubble(
		rect: Rect,
		text: String,
		bitmapWidth: Int,
		bitmapHeight: Int,
		verticalPreferred: Boolean,
		bubbleLikeRegion: Boolean,
	): PreparedBubble? {
		if (bitmapWidth <= 1 || bitmapHeight <= 1) {
			return null
		}
		val padding = dp(4f)
		val rawRect = Rect(
			rect.left.coerceIn(0, bitmapWidth - 1),
			rect.top.coerceIn(0, bitmapHeight - 1),
			rect.right.coerceIn(1, bitmapWidth),
			rect.bottom.coerceIn(1, bitmapHeight),
		)
		val baseRect = stabilizeRenderRect(
			rect = rawRect,
			bitmapWidth = bitmapWidth,
			bitmapHeight = bitmapHeight,
			bubbleLikeRegion = bubbleLikeRegion,
		)

		if (verticalPreferred) {
			for (scale in BUBBLE_EXPAND_SCALES) {
				val safeRect = if (scale <= 1f) {
					baseRect
				} else {
					expandRectAroundCenter(baseRect, scale, bitmapWidth, bitmapHeight)
				}
				val width = max(1, safeRect.width() - padding * 2)
				val height = max(1, safeRect.height() - padding * 2)
				if (width <= 1 || height <= 1) continue
				val vertical = buildVerticalPlan(text, width, height) ?: continue
				val contentW = computeVerticalUsedWidth(vertical)
				val contentH = computeVerticalUsedHeight(vertical)
				val drawRect = if (bubbleLikeRegion) {
					Rect(safeRect)
				} else {
					centerRectByContent(
						outer = safeRect,
						contentWidth = contentW,
						contentHeight = contentH,
						padding = padding,
					)
				}
				val drawContentWidth = max(1, drawRect.width() - padding * 2)
				val drawContentHeight = max(1, drawRect.height() - padding * 2)
				return PreparedBubble(
					rect = drawRect,
					padding = padding,
					contentWidth = drawContentWidth,
					contentHeight = drawContentHeight,
					layout = null,
					verticalPlan = vertical,
				)
			}
		}

		var best: PreparedBubble? = null
		var bestOverflow = Int.MAX_VALUE
		for (scale in BUBBLE_EXPAND_SCALES) {
			val safeRect = if (scale <= 1f) {
				baseRect
			} else {
				expandRectAroundCenter(baseRect, scale, bitmapWidth, bitmapHeight)
			}
			val width = max(1, safeRect.width() - padding * 2)
			val height = max(1, safeRect.height() - padding * 2)
			if (width <= 1 || height <= 1) continue
			var textSize = initialHorizontalTextSize(width = width, height = height)
			var layout = buildTextLayout(text, width, textSize)
			while (layout.height > height && textSize > dp(8f)) {
				textSize -= 1f
				layout = buildTextLayout(text, width, textSize)
			}
			val overflow = (layout.height - height).coerceAtLeast(0)
			val contentW = computeLayoutUsedWidth(layout)
			val contentH = min(layout.height, height)
			val drawRect = if (bubbleLikeRegion) {
				Rect(safeRect)
			} else {
				centerRectByContent(
					outer = safeRect,
					contentWidth = contentW,
					contentHeight = contentH,
					padding = padding,
				)
			}
			val drawContentWidth = max(1, drawRect.width() - padding * 2)
			val drawContentHeight = max(1, drawRect.height() - padding * 2)
			var adjustedLayout = if (!bubbleLikeRegion && drawContentWidth != width) {
				buildTextLayout(
					text = text,
					width = drawContentWidth,
					textSize = textSize,
					maxLines = Int.MAX_VALUE,
				)
			} else {
				layout
			}
			if (adjustedLayout.height > drawContentHeight) {
				val lineHeight = max(1, adjustedLayout.getLineBottom(0))
				val maxLines = max(1, drawContentHeight / lineHeight)
				adjustedLayout = buildTextLayout(
					text = text,
					width = drawContentWidth,
					textSize = textSize,
					maxLines = maxLines,
					ellipsize = TextUtils.TruncateAt.END,
				)
			}
			val candidate = PreparedBubble(
				rect = drawRect,
				padding = padding,
				contentWidth = drawContentWidth,
				contentHeight = drawContentHeight,
				layout = adjustedLayout,
				verticalPlan = null,
			)
			if (overflow < bestOverflow) {
				bestOverflow = overflow
				best = candidate
			}
			if (overflow == 0) break
		}
		return best
	}

	private fun computeLayoutUsedWidth(layout: StaticLayout): Int {
		var maxWidth = 1f
		for (i in 0 until layout.lineCount) {
			maxWidth = max(maxWidth, layout.getLineWidth(i))
		}
		return ceil(maxWidth.toDouble()).toInt().coerceAtLeast(1)
	}

	private fun computeVerticalUsedWidth(plan: VerticalLayoutPlan): Int {
		val colsUsed = ceil(plan.glyphs.size / plan.rowCapacity.toDouble()).toInt().coerceAtLeast(1)
		return colsUsed * plan.cellSize
	}

	private fun computeVerticalUsedHeight(plan: VerticalLayoutPlan): Int {
		val rowsUsed = min(plan.rowCapacity, plan.glyphs.size).coerceAtLeast(1)
		return rowsUsed * plan.cellSize
	}

	private fun stabilizeRenderRect(
		rect: Rect,
		bitmapWidth: Int,
		bitmapHeight: Int,
		bubbleLikeRegion: Boolean,
	): Rect {
		if (bubbleLikeRegion) return rect
		val width = rect.width()
		val height = rect.height()
		if (width <= 0 || height <= 0) return rect
		val minRenderColumnWidth = dp(56f)
		val maxRenderColumnWidth = dp(120f)
		if (width >= minRenderColumnWidth || height <= width * 2) return rect
		val targetWidth = max(
			minRenderColumnWidth,
			min(maxRenderColumnWidth, (height * MIN_RENDER_COLUMN_WIDTH_RATIO).toInt()),
		).coerceAtMost(bitmapWidth)
		if (targetWidth <= width) return rect
		val cx = rect.centerX()
		var left = cx - targetWidth / 2
		var right = left + targetWidth
		if (left < 0) {
			left = 0
			right = targetWidth
		}
		if (right > bitmapWidth) {
			right = bitmapWidth
			left = right - targetWidth
		}
		return Rect(
			left.coerceIn(0, bitmapWidth - 1),
			rect.top.coerceIn(0, bitmapHeight - 1),
			right.coerceIn(1, bitmapWidth),
			rect.bottom.coerceIn(1, bitmapHeight),
		)
	}

	private fun initialHorizontalTextSize(width: Int, height: Int): Float {
		return min(
			dp(18f).toFloat(),
			min(
				height * 0.42f,
				width * HORIZONTAL_TEXT_SIZE_WIDTH_RATIO,
			),
		).coerceAtLeast(dp(8f).toFloat())
	}

	private fun centerRectByContent(outer: Rect, contentWidth: Int, contentHeight: Int, padding: Int): Rect {
		val compactness = overlayCompactnessLevel()
		val extraScale = when (compactness) {
			TuningLevel.STRICT -> 1.02f
			TuningLevel.BALANCED -> 1.10f
			TuningLevel.RELAXED -> 1.22f
		}
		val minSide = when (compactness) {
			TuningLevel.STRICT -> dp(10f)
			TuningLevel.BALANCED -> dp(12f)
			TuningLevel.RELAXED -> dp(16f)
		}
		val minTargetW = min(minSide, outer.width()).coerceAtLeast(1)
		val minTargetH = min(minSide, outer.height()).coerceAtLeast(1)
		val targetW = ((contentWidth + padding * 2) * extraScale).toInt().coerceIn(minTargetW, outer.width())
		val targetH = ((contentHeight + padding * 2) * extraScale).toInt().coerceIn(minTargetH, outer.height())
		val cx = outer.centerX()
		val cy = outer.centerY()
		var left = cx - targetW / 2
		var top = cy - targetH / 2
		var right = left + targetW
		var bottom = top + targetH
		if (left < outer.left) {
			left = outer.left
			right = left + targetW
		}
		if (top < outer.top) {
			top = outer.top
			bottom = top + targetH
		}
		if (right > outer.right) {
			right = outer.right
			left = right - targetW
		}
		if (bottom > outer.bottom) {
			bottom = outer.bottom
			top = bottom - targetH
		}
		return Rect(left, top, right, bottom)
	}

	private fun isLikelySpeechBubbleRegion(bitmap: Bitmap, rect: Rect): Boolean {
		val compactness = overlayCompactnessLevel()
		val left = rect.left.coerceIn(0, bitmap.width - 1)
		val top = rect.top.coerceIn(0, bitmap.height - 1)
		val right = rect.right.coerceIn(left + 1, bitmap.width)
		val bottom = rect.bottom.coerceIn(top + 1, bitmap.height)
		val w = right - left
		val h = bottom - top
		if (w <= 0 || h <= 0) return false
		val stepX = max(1, w / 10)
		val stepY = max(1, h / 10)
		var total = 0
		var bright = 0
		for (y in top until bottom step stepY) {
			for (x in left until right step stepX) {
				val pixel = bitmap.getPixel(x, y)
				val lum = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
				total++
				if (lum >= 220) bright++
			}
		}
		if (total == 0) return false
		val brightRatio = bright.toFloat() / total.toFloat()
		val threshold = when (compactness) {
			TuningLevel.STRICT -> 0.70f
			TuningLevel.BALANCED -> 0.62f
			TuningLevel.RELAXED -> 0.54f
		}
		return brightRatio >= threshold
	}

	private fun groupingTuningLevel(): TuningLevel {
		return when (settings.readerTranslationBubbleGroupingTuning.trim().uppercase()) {
			"STRICT" -> TuningLevel.STRICT
			"RELAXED" -> TuningLevel.RELAXED
			else -> TuningLevel.BALANCED
		}
	}

	private fun overlayCompactnessLevel(): TuningLevel {
		return when (settings.readerTranslationOverlayCompactness.trim().uppercase()) {
			"STRICT" -> TuningLevel.STRICT
			"RELAXED" -> TuningLevel.RELAXED
			else -> TuningLevel.BALANCED
		}
	}

	private fun drawBubbleBackground(canvas: Canvas, bubble: PreparedBubble) {
		val roundRadius = dp(6f).toFloat()
		canvas.drawRoundRect(RectF(bubble.rect), roundRadius, roundRadius, bubblePaint)
	}

	private fun drawBubbleText(canvas: Canvas, bubble: PreparedBubble) {
		canvas.save()
		canvas.translate((bubble.rect.left + bubble.padding).toFloat(), (bubble.rect.top + bubble.padding).toFloat())
		canvas.clipRect(0, 0, bubble.contentWidth, bubble.contentHeight)
		val vertical = bubble.verticalPlan
		if (vertical != null) {
			drawVerticalText(canvas, vertical, bubble.contentWidth)
		} else {
			bubble.layout?.draw(canvas)
		}
		canvas.restore()
	}

	private fun drawVerticalText(canvas: Canvas, plan: VerticalLayoutPlan, contentWidth: Int) {
		textPaint.textSize = plan.textSize
		textPaint.textAlign = Paint.Align.CENTER
		val fm = textPaint.fontMetrics
		val baselineOffset = -(fm.ascent + fm.descent) / 2f
		val cell = plan.cellSize.toFloat()
		plan.glyphs.forEachIndexed { index, glyph ->
			val col = index / plan.rowCapacity
			val row = index % plan.rowCapacity
			val cx = contentWidth - cell * (col + 0.5f)
			val cy = cell * (row + 0.5f)
			canvas.drawText(glyph, cx, cy + baselineOffset, textPaint)
		}
	}

	private fun buildVerticalPlan(text: String, width: Int, height: Int): VerticalLayoutPlan? {
		val glyphs = textToGlyphs(text)
		if (glyphs.isEmpty()) return null
		var textSize = min(
			dp(18f).toFloat(),
			min(
				height * 0.42f,
				width * VERTICAL_TEXT_SIZE_WIDTH_RATIO,
			),
		)
		val minSize = dp(8f).toFloat()
		while (textSize >= minSize) {
			val cell = max(1, (textSize * 1.1f).toInt())
			val rows = max(1, height / cell)
			val colsMax = max(1, width / cell)
			val colsNeed = ceil(glyphs.size / rows.toDouble()).toInt()
			if (colsNeed <= colsMax) {
				return VerticalLayoutPlan(
					glyphs = glyphs,
					textSize = textSize,
					cellSize = cell,
					rowCapacity = rows,
				)
			}
			textSize -= 1f
		}
		val cell = max(1, (minSize * 1.1f).toInt())
		val rows = max(1, height / cell)
		val colsMax = max(1, width / cell)
		val maxGlyphs = max(1, rows * colsMax)
		val trimmed = if (glyphs.size > maxGlyphs) {
			glyphs.take(maxGlyphs - 1) + "…"
		} else {
			glyphs
		}
		return VerticalLayoutPlan(
			glyphs = trimmed,
			textSize = minSize,
			cellSize = cell,
			rowCapacity = rows,
		)
	}

	private fun textToGlyphs(text: String): List<String> {
		val cleaned = text.replace("\r", "").replace("\n", "").trim()
		if (cleaned.isBlank()) return emptyList()
		val result = ArrayList<String>(cleaned.length)
		var i = 0
		while (i < cleaned.length) {
			val cp = Character.codePointAt(cleaned, i)
			result.add(String(Character.toChars(cp)))
			i += Character.charCount(cp)
		}
		return result
	}

	private fun expandRectAroundCenter(rect: Rect, scale: Float, maxWidth: Int, maxHeight: Int): Rect {
		val cx = (rect.left + rect.right) / 2f
		val cy = (rect.top + rect.bottom) / 2f
		val halfW = rect.width() * scale * 0.5f
		val halfH = rect.height() * scale * 0.5f
		var left = (cx - halfW).toInt()
		var top = (cy - halfH).toInt()
		var right = (cx + halfW).toInt()
		var bottom = (cy + halfH).toInt()

		left = left.coerceAtLeast(0)
		top = top.coerceAtLeast(0)
		right = right.coerceAtMost(maxWidth)
		bottom = bottom.coerceAtMost(maxHeight)
		if (right <= left) right = (left + 1).coerceAtMost(maxWidth)
		if (bottom <= top) bottom = (top + 1).coerceAtMost(maxHeight)
		return Rect(left, top, right, bottom)
	}

	private fun buildTextLayout(
		text: String,
		width: Int,
		textSize: Float,
		maxLines: Int = Int.MAX_VALUE,
		ellipsize: TextUtils.TruncateAt? = null,
	): StaticLayout {
		textPaint.textSize = textSize
		return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, max(1, width))
			.setAlignment(Layout.Alignment.ALIGN_NORMAL)
			.setIncludePad(false)
			.setLineSpacing(0f, 1.05f)
			.setMaxLines(maxLines)
			.setEllipsize(ellipsize)
			.build()
	}

	private fun isLikelyGarbledText(text: String): Boolean {
		if (text.isBlank()) return true
		if (text.any { it == '\uFFFD' }) return true
		val len = text.length
		val qCount = text.count { it == '?' || it == '？' }
		if (len >= 4 && qCount * 2 >= len) return true
		val normalized = normalizeForTranslationCompare(text)
		if (normalized.length >= 10) {
			val freq = normalized.groupingBy { it }.eachCount().values.sortedDescending()
			val top1 = freq.firstOrNull() ?: 0
			val top2 = freq.take(2).sum()
			if (top1 * 100 / normalized.length >= 60) return true
			if (top2 * 100 / normalized.length >= 85) return true
		}
		var maxRun = 1
		var run = 1
		for (i in 1 until text.length) {
			if (text[i] == text[i - 1]) {
				run++
				if (run > maxRun) maxRun = run
			} else {
				run = 1
			}
		}
		return len >= 6 && maxRun >= len * 2 / 3
	}

	private fun shouldSuppressRenderedBubble(
		sourceText: String,
		translatedText: String,
		targetLang: String,
	): Boolean {
		if (!settings.isReaderTranslationQualityFilterEnabled) return false
		val sourceNoisy = isLikelyNoisyOcrSource(sourceText)
		if (!sourceNoisy) return false
		if (isWeakTranslatedNoise(translatedText, targetLang)) return true
		val sourceNormalized = normalizeForTranslationCompare(sourceText)
		val translatedNormalized = normalizeForTranslationCompare(translatedText)
		if (sourceNormalized.isNotBlank() && translatedNormalized.isNotBlank() && sourceNormalized == translatedNormalized) {
			return true
		}
		return false
	}

	private fun isWeakTranslatedNoise(text: String, targetLang: String): Boolean {
		if (text.isBlank()) return true
		val compact = text.filterNot(Char::isWhitespace)
		if (compact.isBlank()) return true
		val normalized = normalizeForTranslationCompare(compact)
		if (normalized.isBlank()) return true
		val digits = compact.count { it.isDigit() }
		val latin = compact.count { it.isLatinLetterLike() }
		val cjk = compact.count { it.isCjkUnifiedIdeograph() }
		val kana = compact.count { it.isJapaneseKana() }
		val strongText = cjk + kana
		if (normalized.length <= 3 && digits + latin >= normalized.length) return true
		if (normalized.length <= 5 && digits >= 2 && strongText <= 1) return true
		if (targetLang.startsWith("zh") && normalized.length <= 4 && cjk == 0 && digits + latin >= 2) return true
		return false
	}

	private fun isAcceptableTranslation(
		sourceText: String,
		translatedText: String,
		sourceLang: String,
		targetLang: String,
	): Boolean {
		if (translatedText.isBlank()) return false
		if (translatedText == "..." || translatedText == "…") return false
		if (!settings.isReaderTranslationQualityFilterEnabled) return true
		if (shouldSuppressRenderedBubble(sourceText, translatedText, targetLang)) return false
		return true
	}

	private fun normalizeForTranslationCompare(text: String): String {
		return buildString(text.length) {
			for (ch in text) {
				if (ch.isLetterOrDigit() || ch.isCjkUnifiedIdeograph() || ch.isJapaneseKana()) {
					append(ch)
				}
			}
		}.trim()
	}

	private fun Char.isJapaneseKana(): Boolean {
		return this in '\u3040'..'\u30ff' || this == 'ー'
	}

	private fun Char.isAsciiLetter(): Boolean {
		return this in 'a'..'z' || this in 'A'..'Z'
	}

	private fun Char.isLatinLetterLike(): Boolean {
		if (isAsciiLetter()) return true
		return Character.UnicodeScript.of(code) == Character.UnicodeScript.LATIN
	}

		private fun Char.isCjkUnifiedIdeograph(): Boolean {
			val block = Character.UnicodeBlock.of(this) ?: return false
			val blockName = block.toString()
			return blockName.startsWith("CJK_UNIFIED_IDEOGRAPHS") ||
				block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
		}

		private fun isLikelyNoisyOcrSource(text: String): Boolean {
			if (text.isBlank()) return false
			val len = text.length
			if (len < 8) return false
			val digits = text.count { it.isDigit() }
			val symbols = text.count {
				!it.isWhitespace() &&
					!it.isLetterOrDigit() &&
					!it.isJapaneseKana() &&
					!it.isCjkUnifiedIdeograph()
			}
			val separators = text.count { it in setOf(':', '：', '/', '／', '.', '．', '…', '-', 'ー') }
			val ratio = (digits + symbols + separators).toFloat() / len.toFloat()
			return ratio >= 0.28f || (digits >= 4 && separators >= 4) || Regex("""(?:\d[：:/／．.]){3,}""").containsMatchIn(text)
		}

		private fun oneLine(text: String, limit: Int = 140): String {
		if (text.isBlank()) return ""
		return text.replace('\n', ' ').replace('\r', ' ').trim().let {
			if (it.length <= limit) it else it.take(limit) + "..."
		}
	}

	private inline fun log(message: () -> String) {
		val msg = message()
		val pageId = loggingPageId.get()
		if (pageId != null && pageId != NO_LOGGING_PAGE_ID) {
			appendPageLog(pageId, msg)
		}
		if (settings.isReaderTranslationDebugLogsEnabled) {
			Log.d(LOG_TAG, msg)
		}
	}

	private fun appendPageLog(pageId: Long, message: String) {
		debugLogStore.append(pageId, message)
	}

	private fun getPageRenderEpoch(pageId: Long): Int {
		synchronized(pageStateLock) {
			return pageRenderEpochs[pageId] ?: 0
		}
	}

	private fun buildRenderedCacheKey(
		pageUrl: String,
		sourceUri: String,
		sourceLang: String,
		targetLang: String,
		pageEpoch: Int,
	): String {
		val raw = listOf(
			TRANSLATION_PIPELINE_VERSION,
			pageUrl,
			sourceUri,
			sourceLang,
			targetLang,
			renderCacheEpoch.toString(),
			pageEpoch.toString(),
			settings.readerTranslationMode.name,
			settings.readerTranslationOnnxModelId,
			settings.readerTranslationApiEndpoint,
			settings.readerTranslationApiModel,
			settings.readerTranslationOcrEngine.name,
			settings.readerTranslationBubbleGroupingTuning,
			settings.isReaderTranslationBubbleGroupingEnabled.toString(),
			settings.readerTranslationOverlayCompactness,
			settings.readerTranslationHybridFallbackThreshold.toString(),
			settings.isReaderTranslationQualityFilterEnabled.toString(),
		).joinToString("|")
		return "${RENDER_CACHE_PREFIX}${raw.sha256()}"
	}

	private fun buildTextCacheKey(text: String, sourceLang: String, targetLang: String): String {
		val raw = listOf(
			TRANSLATION_PIPELINE_VERSION,
			text,
			sourceLang,
			targetLang,
			settings.readerTranslationMode.name,
			settings.readerTranslationOnnxModelId,
			settings.readerTranslationApiEndpoint,
			settings.readerTranslationApiModel,
			settings.isReaderTranslationQualityFilterEnabled.toString(),
		).joinToString("|")
		return "${TEXT_CACHE_PREFIX}${raw.sha256()}"
	}

	private fun String.sha256(): String {
		val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
		return buildString(digest.size * 2) {
			for (b in digest) {
				append(((b.toInt() and 0xff) + 0x100).toString(16).substring(1))
			}
		}
	}

	private fun buildOcrCacheKey(sourceUri: String, sourceLang: String): String {
		val raw = listOf(sourceUri, sourceLang, settings.readerTranslationOcrEngine.name).joinToString("|")
		return "${OCR_CACHE_PREFIX}${raw.sha256()}"
	}

	private fun resolveSourceLanguage(page: ContentPage): String {
		return resolveReaderTranslationSourceLanguage(
			preferredLanguage = settings.readerTranslationSourceLanguage,
			contentLanguage = page.source.getLocale()?.language,
		)
	}

	private fun serializeOcrBlocks(blocks: List<OcrTextBlock>): String {
		val arr = JSONArray()
		for (block in blocks) {
			val obj = JSONObject()
			obj.put("text", block.text)
			obj.put("confidence", block.confidence)
			block.boundingBox?.let { box ->
				obj.put("left", box.left)
				obj.put("top", box.top)
				obj.put("right", box.right)
				obj.put("bottom", box.bottom)
			}
			arr.put(obj)
		}
		return arr.toString()
	}

	private fun deserializeOcrBlocks(json: String): List<OcrTextBlock> {
		val arr = JSONArray(json)
		val result = mutableListOf<OcrTextBlock>()
		for (i in 0 until arr.length()) {
			val obj = arr.getJSONObject(i)
			val box = if (obj.has("left")) {
				Rect(obj.getInt("left"), obj.getInt("top"), obj.getInt("right"), obj.getInt("bottom"))
			} else null
			result.add(
				OcrTextBlock(
					text = obj.getString("text"),
					boundingBox = box,
					confidence = obj.optDouble("confidence", 1.0).toFloat(),
				)
			)
		}
		return result
	}

	private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

	private companion object {

		private enum class TuningLevel {
			STRICT,
			BALANCED,
			RELAXED,
		}

		val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
		const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
		const val MAX_OPENAI_BATCH_SIZE = 3
			const val TRANSLATION_PIPELINE_VERSION = "2026-03-11-roi-ocr-8"
		const val OPENAI_TRANSLATION_SYSTEM_PROMPT = """
		You translate manga OCR text.
		Output only the translation.
		Do not explain.
"""
		const val MAX_PARALLEL_TRANSLATION_PAGES = 2
		const val MAX_ROI_OCR_REQUESTS_PER_PAGE = 8
		const val MAX_INDIVIDUAL_FALLBACK_FRAGMENTS = 8
		const val MAX_INDIVIDUAL_FALLBACK_RATIO = 0.25f
		const val MAX_DETECTED_GROUP_FRAGMENTS = 28
		const val MIN_RENDER_COLUMN_WIDTH_RATIO = 0.22f
		const val HORIZONTAL_TEXT_SIZE_WIDTH_RATIO = 0.58f
		const val VERTICAL_TEXT_SIZE_WIDTH_RATIO = 0.78f
		val THINK_TAG_REGEX = Regex("(?is)<think>.*?</think>")
		val BUBBLE_EXPAND_SCALES = floatArrayOf(1f, 1.12f, 1.24f)
		const val TEXT_CACHE_PREFIX = "reader_translate_text_"
		const val RENDER_CACHE_PREFIX = "reader_translate_render_"
		const val OCR_CACHE_PREFIX = "reader_translate_ocr_"
		const val LOG_TAG = "ReaderTranslate"
		const val MAX_PAGE_LOG_LINES = 500
		const val NO_LOGGING_PAGE_ID = Long.MIN_VALUE
		const val FAIL_CODE_OCR_EMPTY = "OCR_EMPTY"
		const val FAIL_CODE_TRANSLATE_EMPTY = "TRANSLATE_EMPTY"
		const val FAIL_CODE_RENDER_FILTERED = "RENDER_FILTERED"
		const val FAIL_CODE_PROCESS_EXCEPTION = "PROCESS_EXCEPTION"

			private fun sanitizeTranslation(text: String): String {
			if (text.isBlank()) return ""
			val clean = stripThinkContent(text)
			if (clean.isBlank()) return ""
			val normalized = normalizeJsonLikeContent(clean)
			if (normalized.isBlank()) return ""
			if (
				normalized.contains("Thinking Process", ignoreCase = true) ||
				normalized.contains("Analyze the Request", ignoreCase = true)
			) {
				return ""
			}

			val jsonStart = normalized.indexOf('{')
			val jsonEnd = normalized.lastIndexOf('}')
			if (jsonStart != -1 && jsonEnd != -1 && (jsonEnd.toInt() > jsonStart.toInt())) {
				val jsonText = normalized.substring(jsonStart, jsonEnd + 1)
				runCatching {
					val json = JSONObject(jsonText)
					val result = pickTranslationField(json)
					if (result.isNotBlank()) return result
				}
			}

			extractTranslationFromMalformedJson(normalized)?.let { extracted ->
				if (extracted.isNotBlank()) return extracted
			}

			return normalized
				.replace(Regex("^\\{.*\"translation\":\\s*\"", RegexOption.IGNORE_CASE), "")
				.replace(Regex("\"\\s*\\}$"), "")
				.removeSurrounding("**")
				.removeSurrounding("\"")
				.trim()
				.takeUnless {
					it.isBlank() ||
						it == "..." ||
						it == "…"
				}
				.orEmpty()
		}

		private fun pickTranslationField(obj: JSONObject): String {
			val direct = listOf("translation", "translatedText", "text", "output")
				.firstNotNullOfOrNull { key ->
					obj.optString(key).trim().takeIf { it.isNotBlank() }
				}
			if (!direct.isNullOrBlank()) return direct

			return runCatching {
				obj.optJSONObject("data")?.optJSONArray("translations")?.optJSONObject(0)?.optString("translatedText")?.trim()
			}.getOrNull().orEmpty()
		}

		private fun extractTranslationFromMalformedJson(raw: String): String? {
			val regexes = listOf(
				Regex("""(?is)"translation"\s*:\s*"((?:\\.|[^"\\])*)(?:"|$)"""),
				Regex("""(?is)"translatedText"\s*:\s*"((?:\\.|[^"\\])*)(?:"|$)"""),
			)
			for (regex in regexes) {
				val value = regex.find(raw)?.groupValues?.getOrNull(1).orEmpty()
				val decoded = decodeJsonStringFragment(value)
				if (decoded.isNotBlank()) {
					return decoded
				}
			}
			return null
		}

			private fun decodeJsonStringFragment(value: String): String {
			if (value.isBlank()) return ""
			return value
				.replace("\\n", "\n")
				.replace("\\r", "\r")
				.replace("\\t", "\t")
				.replace("\\\"", "\"")
				.replace("\\\\", "\\")
				.trim()
				.removeSurrounding("\"")
		}

			private fun normalizeJsonLikeContent(raw: String): String {
				val text = raw.trim()
				if (!text.startsWith("```")) return text
				val lines = text.lines()
				if (lines.isEmpty()) return text
				val body = lines.drop(1).dropLastWhile { it.trim().startsWith("```") }.joinToString("\n").trim()
				return body.ifBlank { text }
			}

			private fun parseMalformedBatchTranslationJson(raw: String, expectedSize: Int): Map<Int, String> {
				val result = LinkedHashMap<Int, String>(expectedSize)
				val objectRegex = Regex("""(?s)\{[^{}]*}""")
				val idRegex = Regex("""(?is)"\s*id\s*"\s*:\s*"?(\d+)""")
				val pairRegex = Regex(
					"""(?is)"\s*id\s*"\s*:\s*"?(\d+)"?[^{}\[\]]*?"\s*(?:translation|translatedText|output)\s*"\s*:\s*"((?:\\.|[^"\\])*)"""
				)
				val translationRegexes = listOf(
					Regex("""(?is)"\s*translation\s*"\s*:\s*"((?:\\.|[^"\\])*)"""),
					Regex("""(?is)"\s*translatedText\s*"\s*:\s*"((?:\\.|[^"\\])*)"""),
					Regex("""(?is)"\s*output\s*"\s*:\s*"((?:\\.|[^"\\])*)"""),
				)

				for (match in objectRegex.findAll(raw)) {
					val item = match.value
					val id = idRegex.find(item)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: continue
					if (id <= 0 || id > expectedSize || result.containsKey(id)) continue
					val translation = translationRegexes.firstNotNullOfOrNull { regex ->
						regex.find(item)?.groupValues?.getOrNull(1)?.let(::decodeJsonStringFragment)?.trim()?.takeIf { it.isNotBlank() }
					}.orEmpty()
					if (translation.isNotBlank()) {
						result[id] = translation
					}
				}
				if (result.size < expectedSize) {
					for (match in pairRegex.findAll(raw)) {
						val id = match.groupValues.getOrNull(1)?.toIntOrNull() ?: continue
						if (id <= 0 || id > expectedSize || result.containsKey(id)) continue
						val translation = decodeJsonStringFragment(match.groupValues.getOrNull(2).orEmpty()).trim()
						if (translation.isNotBlank()) {
							result[id] = translation
						}
					}
				}
				return result
			}

			private fun stripThinkContent(text: String): String {
			if (text.isBlank()) return text
			return THINK_TAG_REGEX.replace(text, "")
				.replace(Regex("(?is)<think>.*$"), "")
				.replace("<analysis>", "", ignoreCase = true)
				.replace("</analysis>", "", ignoreCase = true)
				.trim()
		}
	}
}
