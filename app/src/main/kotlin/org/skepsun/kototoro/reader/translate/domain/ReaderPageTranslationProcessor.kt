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
import org.skepsun.kototoro.core.network.MangaHttpClient
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ReaderOcrEngine
import org.skepsun.kototoro.core.prefs.ReaderTranslationMode
import org.skepsun.kototoro.core.util.ext.isFileUri
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.ext.toMimeTypeOrNull
import org.skepsun.kototoro.local.data.LocalStorageCache
import org.skepsun.kototoro.local.data.PageCache
import org.skepsun.kototoro.parsers.model.MangaPage
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
	@MangaHttpClient
	private val okHttpClient: OkHttpClient,
	private val mlKitOcrEngine: MlKitReaderOcrEngine,
	private val paddleOcrEngine: PaddleReaderOcrEngine,
	private val tfliteOcrEngine: TfLiteReaderOcrEngine,
	private val hybridOcrEngine: HybridReaderOcrEngine,
	private val ncnnOcrEngine: NcnnReaderOcrEngine,
	private val cvBubbleDetector: CvBubbleDetector,
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

	suspend fun peekRendered(page: MangaPage, sourceUri: Uri): Uri? {
		if (!settings.isReaderTranslationEnabled) {
			return null
		}
		val sourceLang = settings.readerTranslationSourceLanguage.lowercase()
		val targetLang = settings.readerTranslationTargetLanguage.lowercase()
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

	suspend fun process(page: MangaPage, sourceUri: Uri): Uri {
		val enabled = settings.isReaderTranslationEnabled
		val showTranslated = settings.isReaderTranslationShowTranslated
		Log.d(LOG_TAG, "process debug: page=${page.id} enabled=$enabled showTranslated=$showTranslated")
		if (!enabled) {
			return sourceUri
		}
		val sourceLang = settings.readerTranslationSourceLanguage.lowercase()
		val targetLang = settings.readerTranslationTargetLanguage.lowercase()
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
		var processResult = "source"
		appendPageLog(pageId, "metric.render_cache.hit=0")

		// OCR result caching: skip re-OCR if we already processed this image
		val ocrCacheKey = buildOcrCacheKey(sourceUri.toString(), sourceLang)
		try {
			val ocrStartMs = SystemClock.elapsedRealtime()
			val textBlocks = textCache[ocrCacheKey]?.let { cached ->
				ocrCacheHit = true
				log { "ocr cache hit" }
				deserializeOcrBlocks(cached)
			} ?: run {
				val blocks = recognizeTextWithFallback(sourceUri, sourceLang, pageId)
				if (blocks.isNotEmpty()) {
					textCache[ocrCacheKey] = serializeOcrBlocks(blocks)
				}
				blocks
			}
			ocrDurationMs = SystemClock.elapsedRealtime() - ocrStartMs
			ocrBlocks = textBlocks.size
			log { "ocr done blocks=${textBlocks.size}" }
			if (textBlocks.isEmpty()) {
				log { "fail_code=$FAIL_CODE_OCR_EMPTY" }
				return sourceUri
			}
			val sourceBitmap = runInterruptible(Dispatchers.IO) {
				BitmapDecoderCompat.decode(sourceUri.toFile())
			}
			val bitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
			if (bitmap !== sourceBitmap) {
				sourceBitmap.recycle()
			}
			val canvas = Canvas(bitmap)

			val drawableBlocks = textBlocks.filter { it.boundingBox != null && it.text.trim().isNotBlank() }
			val sourceFragments = drawableBlocks.map {
				TextFragment(
					rect = it.boundingBox!!,
					text = it.text.trim(),
				)
			}
			val groupingResult = groupFragmentsForTranslation(sourceFragments, bitmap)
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
			val roiResult = recognizeBubbleTextsByRoi(
				groups = groupingResult.groups,
				sourceUri = sourceUri,
				sourceLang = sourceLang,
				pageId = pageId,
				bitmap = bitmap,
			)
			roiRequestCount = roiResult.requestCount
			roiSuccessCount = roiResult.successCount
			roiFallbackCount = roiResult.fallbackCount
			roiDurationMs = roiResult.totalMs
			roiCoverageArea = roiResult.coverageArea
			val bubbleInputs = groupingResult.groups.mapIndexedNotNull { index, group ->
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
				)
			}
			bubbleCount = bubbleInputs.size
			val translateStartMs = SystemClock.elapsedRealtime()
			val translatedMap = translateBlocksCached(
				texts = bubbleInputs.map { it.sourceText },
				sourceLang = sourceLang,
				targetLang = targetLang,
			)
			translateDurationMs = SystemClock.elapsedRealtime() - translateStartMs
			val renderStartMs = SystemClock.elapsedRealtime()
			val preparedBubbles = mutableListOf<PreparedBubble>()
			var nonEmptyTranslatedCount = 0
			for (bubble in bubbleInputs) {
				val translated = translatedMap[bubble.sourceText].orEmpty().trim()
				log {
					"bubble translate src=${oneLine(bubble.sourceText)} out=${oneLine(translated)} box=${bubble.rect}"
				}
				if (translated.isBlank()) continue
				nonEmptyTranslatedCount++
				if (isLikelyGarbledText(translated)) continue
				if (shouldSuppressRenderedBubble(bubble.sourceText, translated, targetLang)) {
					log {
						"bubble render suppressed src=${oneLine(bubble.sourceText)} out=${oneLine(translated)} box=${bubble.rect}"
					}
					continue
				}
				val bubbleLikeRegion = isLikelySpeechBubbleRegion(bitmap, bubble.rect)
				prepareTranslatedBubble(
					rect = bubble.rect,
					text = translated,
					bitmapWidth = bitmap.width,
					bitmapHeight = bitmap.height,
					verticalPreferred = bubble.verticalPreferred,
					bubbleLikeRegion = bubbleLikeRegion,
				)?.let { preparedBubbles.add(it) }
			}
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
			add(ReaderOcrEngine.NCNN)
			add(ReaderOcrEngine.HYBRID)
			add(ReaderOcrEngine.TFLITE)
			add(ReaderOcrEngine.MLKIT)
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
		if (texts.isEmpty()) return emptyMap()
		val uniqueTexts = texts.distinct()
		val translated = LinkedHashMap<String, String>(uniqueTexts.size)
		val misses = ArrayList<String>(uniqueTexts.size)

			for (text in uniqueTexts) {
				val cacheKey = buildTextCacheKey(text, sourceLang, targetLang)
				val cached = textCache[cacheKey]
				if (!cached.isNullOrBlank()) {
					val sanitized = sanitizeTranslation(cached)
					if (sanitized.isNotBlank()) {
						translated[text] = sanitized
						if (sanitized != cached) {
							textCache[cacheKey] = sanitized
						}
						log { "translate cache hit src=${oneLine(text)} out=${oneLine(sanitized)}" }
				} else {
					textCache[cacheKey] = ""
					misses.add(text)
					log { "translate cache rejected src=${oneLine(text)} out=${oneLine(sanitized)}" }
				}
			} else {
				misses.add(text)
			}
		}
		if (misses.isEmpty()) return translated

		val mode = settings.readerTranslationMode
		val onnxModelId = settings.readerTranslationOnnxModelId.trim()
		if (mode != ReaderTranslationMode.API_ONLY && onnxModelId.isNotBlank()) {
			val needOnnx = misses.filter { translated[it].isNullOrBlank() }
			if (needOnnx.isNotEmpty()) {
				val onnxMap = runCatching {
					onnxTranslationEngine.translateBatch(needOnnx, sourceLang, targetLang, onnxModelId)
				}.onFailure {
					it.printStackTraceDebug()
					log { "translate onnx failed: ${it.message.orEmpty()}" }
				}.getOrDefault(emptyMap())
				for (text in needOnnx) {
					val onnxText = onnxMap[text]?.trim().orEmpty()
					if (onnxText.isNotBlank()) {
						val sanitized = sanitizeTranslation(onnxText)
						if (isAcceptableTranslation(text, sanitized, sourceLang, targetLang)) {
							translated[text] = sanitized
							textCache[buildTextCacheKey(text, sourceLang, targetLang)] = sanitized
							log { "translate onnx hit src=${oneLine(text)} out=${oneLine(sanitized)}" }
						} else {
							log { "translate onnx rejected src=${oneLine(text)} out=${oneLine(sanitized)}" }
						}
					}
				}
			}
		}

		if (mode != ReaderTranslationMode.API_ONLY) {
			val needLocal = misses.filter { translated[it].isNullOrBlank() }
			log { "translate local requested size=${needLocal.size}" }
			var localResults = runCatching {
				translateLocalBatch(needLocal, sourceLang, targetLang)
			}.onFailure {
				it.printStackTraceDebug()
				log { "translate local batch failed: ${it.message.orEmpty()}" }
			}.getOrDefault(emptyMap())
			if (needLocal.isNotEmpty() && localResults.values.none { it.isNotBlank() }) {
				log { "translate local batch empty, fallback to per-item translation" }
				localResults = coroutineScope {
					needLocal.map { text ->
						async {
							val local = runCatching {
								translateLocal(text, sourceLang, targetLang)
							}.onFailure {
								log { "translate local fallback failed src=${oneLine(text)} err=${it.message.orEmpty()}" }
							}.getOrDefault("").trim()
							text to local
						}
					}.awaitAll().toMap()
				}
			}
			for ((text, local) in localResults) {
				val raw = local.trim()
				if (raw.isNotBlank()) {
					val sanitized = sanitizeTranslation(raw)
					if (isAcceptableTranslation(text, sanitized, sourceLang, targetLang)) {
						translated[text] = sanitized
						textCache[buildTextCacheKey(text, sourceLang, targetLang)] = sanitized
						log { "translate local hit src=${oneLine(text)} out=${oneLine(sanitized)}" }
					} else {
						log { "translate local rejected src=${oneLine(text)} out=${oneLine(sanitized)}" }
					}
				}
			}
		}

		if (mode == ReaderTranslationMode.LOCAL_ONLY) {
			log { "translate mode=LOCAL_ONLY, skip api fallback" }
			for (text in uniqueTexts) {
				translated.putIfAbsent(text, "")
			}
			return translated
		}

			if (mode != ReaderTranslationMode.LOCAL_ONLY) {
				val needApi = misses.filter { translated[it].isNullOrBlank() }
				if (needApi.isNotEmpty()) {
					val apiMap = translateBatchByApi(needApi, sourceLang, targetLang)
					for (text in needApi) {
						val apiText = apiMap[text]?.trim().orEmpty()
						if (apiText.isNotBlank()) {
							val sanitized = sanitizeTranslation(apiText)
							if (sanitized.isNotBlank()) {
								translated[text] = sanitized
								textCache[buildTextCacheKey(text, sourceLang, targetLang)] = sanitized
								log { "translate api hit src=${oneLine(text)} out=${oneLine(sanitized)}" }
							} else {
								log { "translate api rejected src=${oneLine(text)} out=${oneLine(sanitized)}" }
						}
					}
				}
			}
		}

		for (text in uniqueTexts) {
			translated.putIfAbsent(text, "")
		}
		return translated
	}

	private suspend fun translateBatchByApi(
		texts: List<String>,
		sourceLang: String,
		targetLang: String,
	): Map<String, String> {
		val endpoint = settings.readerTranslationApiEndpoint.trim()
		if (endpoint.isBlank() || texts.isEmpty()) {
			return texts.associateWith { "" }
		}

		return if (isOpenAiCompatibleChatCompletionsEndpoint(endpoint)) {
			translateBatchByOpenAi(texts, sourceLang, targetLang)
		} else {
			val map = LinkedHashMap<String, String>(texts.size)
			for (text in texts) {
				map[text] = translateByApi(text, sourceLang, targetLang)
			}
			map
		}
	}

		private suspend fun translateBatchByOpenAi(
			texts: List<String>,
			sourceLang: String,
			targetLang: String,
		): Map<String, String> {
			if (texts.isEmpty()) return emptyMap()
			val mapped = LinkedHashMap<String, String>(texts.size)
			val batches = buildOpenAiMicroBatches(texts)
			log { "openai batch requests count=${batches.size} texts=${texts.size}" }
			for (batch in batches) {
				if (batch.size == 1) {
					val text = batch.first()
					mapped[text] = requestOpenAiSingle(text, sourceLang, targetLang)
					continue
				}
				val batchMap = requestOpenAiBatch(batch, sourceLang, targetLang)
				if (batchMap.isEmpty()) {
					batch.forEach { text ->
						mapped[text] = requestOpenAiSingle(text, sourceLang, targetLang)
					}
					continue
				}
				for (text in batch) {
					mapped[text] = batchMap[text].orEmpty()
				}
			}
			return mapped
		}

	private suspend fun requestOpenAiBatch(
		texts: List<String>,
		sourceLang: String,
		targetLang: String,
	): Map<String, String> {
		if (texts.isEmpty()) return emptyMap()
		val endpoint = settings.readerTranslationApiEndpoint.trim()
		val apiKey = settings.readerTranslationApiKey.trim()
		val model = settings.readerTranslationApiModel.trim().ifBlank { DEFAULT_OPENAI_MODEL }
		val userPrompt = buildString {
			appendLine("Translate manga OCR text from $sourceLang to $targetLang.")
			appendLine("Return strict JSON only.")
			appendLine("Use this array format:")
			appendLine("""[{"id":1,"translation":"..."},{"id":2,"translation":"..."}]""")
			appendLine("Keep ids unchanged. If unreadable or uncertain, use empty translation.")
			appendLine()
			appendLine("Texts:")
			texts.forEachIndexed { index, text ->
				appendLine("${index + 1}. $text")
			}
		}
		val payload = JSONObject().apply {
			put("model", model)
			put("temperature", 0)
			if (isDeepSeekEndpoint(endpoint)) {
				put("thinking", JSONObject().put("type", "disabled"))
			}
			put(
				"messages",
				JSONArray()
					.put(JSONObject().put("role", "system").put("content", OPENAI_TRANSLATION_SYSTEM_PROMPT))
					.put(JSONObject().put("role", "user").put("content", userPrompt))
			)
		}
		return runCatching {
			val requestBuilder = Request.Builder()
				.url(endpoint)
				.post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
				.header("Content-Type", "application/json")
			if (apiKey.isNotBlank()) {
				requestBuilder.header("Authorization", "Bearer $apiKey")
				requestBuilder.header("X-API-Key", apiKey)
			}
			val response = okHttpClient.newCall(requestBuilder.build()).await()
			response.use { resp ->
				val rawBody = resp.body.readJsonTextUtf8()
				if (!resp.isSuccessful) {
					log { "openai batch request failed code=${resp.code} msg=${resp.message} body=${oneLine(rawBody, 300)}" }
					return@use emptyMap()
				}
				if (rawBody.isBlank()) return@use emptyMap()
				val json = runCatching { JSONObject(rawBody) }.getOrNull() ?: return@use emptyMap()
				val content = extractOpenAiMessageContent(json).orEmpty()
				if (content.isBlank()) return@use emptyMap()
				log { "openai batch raw reply=${oneLine(content, 400)}" }
				val parsed = parseBatchTranslationJson(content, texts.size)
				if (parsed.isEmpty()) return@use emptyMap()
				LinkedHashMap<String, String>(texts.size).apply {
					texts.forEachIndexed { index, text ->
						put(text, sanitizeTranslation(parsed[index + 1].orEmpty()))
					}
				}
			}
		}.onFailure {
			log { "openai batch request failed size=${texts.size} err=${it.message.orEmpty()}" }
		}.getOrDefault(emptyMap())
	}

	private suspend fun requestOpenAiSingle(
		text: String,
		sourceLang: String,
		targetLang: String,
	): String {
		if (text.isBlank()) return ""
		val endpoint = settings.readerTranslationApiEndpoint.trim()
		val apiKey = settings.readerTranslationApiKey.trim()
		val model = settings.readerTranslationApiModel.trim().ifBlank { DEFAULT_OPENAI_MODEL }
		val userPrompt = buildString {
			appendLine("Translate manga OCR text from $sourceLang to $targetLang.")
			appendLine("Only output the translation itself.")
			appendLine("If unreadable or uncertain, output nothing.")
			appendLine("Keep short screams natural.")
			append(text)
		}
		val payload = JSONObject().apply {
			put("model", model)
			put("temperature", 0)
			if (isDeepSeekEndpoint(endpoint)) {
				put("thinking", JSONObject().put("type", "disabled"))
			}
			put(
				"messages",
				JSONArray()
					.put(JSONObject().put("role", "system").put("content", OPENAI_TRANSLATION_SYSTEM_PROMPT))
					.put(JSONObject().put("role", "user").put("content", userPrompt))
			)
		}

		return runCatching {
			val requestBuilder = Request.Builder()
				.url(endpoint)
				.post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
				.header("Content-Type", "application/json")
			if (apiKey.isNotBlank()) {
				requestBuilder.header("Authorization", "Bearer $apiKey")
				requestBuilder.header("X-API-Key", apiKey)
			}
			val response = okHttpClient.newCall(requestBuilder.build()).await()
			response.use { resp ->
				val rawBody = resp.body.readJsonTextUtf8()
				if (!resp.isSuccessful) {
					log { "openai request failed code=${resp.code} msg=${resp.message} body=${oneLine(rawBody, 300)}" }
					return@use ""
				}
				if (rawBody.isBlank()) return@use ""
				val json = runCatching { JSONObject(rawBody) }.getOrNull() ?: return@use ""
				val content = extractOpenAiMessageContent(json).orEmpty()
				if (content.isBlank()) return@use ""
				log { "openai raw reply=${oneLine(content, 400)}" }
				sanitizeTranslation(content)
			}
		}.onFailure {
			log { "openai single request failed src=${oneLine(text)} err=${it.message.orEmpty()}" }
		}.getOrDefault("")
	}

	private suspend fun translateLocal(text: String, sourceLang: String, targetLang: String): String {
		val source = TranslateLanguage.fromLanguageTag(sourceLang)
		val target = TranslateLanguage.fromLanguageTag(targetLang)
		if (source == null || target == null) {
			return text
		}
		val options = TranslatorOptions.Builder()
			.setSourceLanguage(source)
			.setTargetLanguage(target)
			.build()
		val translator = Translation.getClient(options)
		return try {
			translator.downloadModelIfNeeded().awaitCancellable()
			translator.translate(text).awaitCancellable()
		} finally {
			translator.close()
		}
	}

	private suspend fun translateLocalBatch(
		texts: List<String>,
		sourceLang: String,
		targetLang: String,
	): Map<String, String> {
		if (texts.isEmpty()) return emptyMap()
		val source = TranslateLanguage.fromLanguageTag(sourceLang)
		val target = TranslateLanguage.fromLanguageTag(targetLang)
		if (source == null || target == null) {
			return texts.associateWith { "" }
		}
		val options = TranslatorOptions.Builder()
			.setSourceLanguage(source)
			.setTargetLanguage(target)
			.build()
		val translator = Translation.getClient(options)
		return try {
			log { "translate local batch start size=${texts.size} source=$sourceLang target=$targetLang" }
			translator.downloadModelIfNeeded().awaitCancellable()
			val results = LinkedHashMap<String, String>(texts.size)
			for (text in texts) {
				val out = runCatching {
					withTimeout(15_000) {
						translator.translate(text).awaitCancellable()
					}
				}.onFailure {
					log { "translate local item failed src=${oneLine(text)} err=${it.message.orEmpty()}" }
				}.getOrDefault("").trim()
				results[text] = out
			}
			log { "translate local batch done translated=${results.count { it.value.isNotBlank() }}/${texts.size}" }
			results
		} finally {
			translator.close()
		}
	}

	private suspend fun translateByApi(text: String, sourceLang: String, targetLang: String): String {
		val endpoint = settings.readerTranslationApiEndpoint.trim()
		if (endpoint.isBlank()) {
			return ""
		}
		val payload = JSONObject().apply {
			put("q", text)
			put("source", sourceLang)
			put("target", targetLang)
			put("format", "text")
		}
		val requestBuilder = Request.Builder()
			.url(endpoint)
			.post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
		val key = settings.readerTranslationApiKey.trim()
		if (key.isNotBlank()) {
			requestBuilder.header("Authorization", "Bearer $key")
			requestBuilder.header("X-API-Key", key)
		}
		val request = requestBuilder.build()
		val response = okHttpClient.newCall(request).await()
		response.use { resp ->
			if (!resp.isSuccessful) {
				log { "api translate failed code=${resp.code} msg=${resp.message}" }
				return ""
			}
			val body = resp.body.readJsonTextUtf8()
			val sanitized = sanitizeTranslation(body)
			log { "api raw reply=${oneLine(body, 300)} sanitized=${oneLine(sanitized)} src=${oneLine(text)}" }
			if (sanitized.isNotBlank()) {
				val cacheKey = buildTextCacheKey(text, sourceLang, targetLang)
				textCache[cacheKey] = sanitized
			}
			return sanitized
		}
	}


	private fun extractOpenAiMessageContent(responseJson: JSONObject): String? {
		val choices = responseJson.optJSONArray("choices") ?: return null
		if (choices.length() == 0) return null
		val message = choices.optJSONObject(0)?.optJSONObject("message") ?: return null
		val content = message.opt("content")
		return when (content) {
			is String -> content
			is JSONArray -> {
				buildString {
					for (i in 0 until content.length()) {
						val chunk = content.optJSONObject(i) ?: continue
						append(chunk.optString("text"))
					}
				}
			}
			else -> null
		}
	}

		private fun parseBatchTranslationJson(content: String, expectedSize: Int): Map<Int, String> {
			val clean = normalizeJsonLikeContent(stripThinkContent(content).trim())
			if (clean.isBlank()) return emptyMap()

			fun validate(map: Map<Int, String>): Map<Int, String> {
				if (map.isEmpty() || map.size > expectedSize) {
					log { "openai parsed invalid mapSize=${map.size} expected<=$expectedSize content=${oneLine(clean, 400)}" }
					return emptyMap()
				}
				log { "openai parsed items=${map.size}" }
				return map
			}

			fun parseStandardJson(raw: String): Map<Int, String> {
				val map = LinkedHashMap<Int, String>(expectedSize)
				if (raw.startsWith("[")) {
					val arr = JSONArray(raw)
					for (i in 0 until arr.length()) {
						val obj = arr.optJSONObject(i) ?: continue
						val id = obj.optInt("id", i + 1)
						val translation = pickTranslationField(obj)
						if (id > 0 && translation.isNotBlank()) {
							map[id] = translation
						}
					}
				} else {
					val json = JSONObject(raw)
					val items = json.optJSONArray("items")
						?: json.optJSONArray("translations")
						?: json.optJSONArray("data")
					if (items != null) {
						for (i in 0 until items.length()) {
							val obj = items.optJSONObject(i) ?: continue
							val id = obj.optInt("id", i + 1)
							val translation = pickTranslationField(obj)
							if (id > 0 && translation.isNotBlank()) {
								map[id] = translation
							}
						}
					}
				}
				return map
			}

			return runCatching {
				validate(parseStandardJson(clean))
			}.getOrElse {
				val salvaged = parseMalformedBatchTranslationJson(clean, expectedSize)
				if (salvaged.isNotEmpty()) {
					log { "openai parse salvaged items=${salvaged.size}" }
					validate(salvaged)
				} else {
					log { "openai parse exception content=${oneLine(clean, 400)}" }
					emptyMap()
				}
			}
		}


	private fun isOpenAiCompatibleChatCompletionsEndpoint(endpoint: String): Boolean {
		val normalized = endpoint.lowercase()
		return normalized.contains("/v1/chat/completions") || normalized.contains("/chat/completions")
	}

	private fun isDeepSeekEndpoint(endpoint: String): Boolean {
		val normalized = endpoint.lowercase()
		return normalized.contains("api.deepseek.com")
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

	private data class PreparedBubble(
		val rect: Rect,
		val padding: Int,
		val contentWidth: Int,
		val contentHeight: Int,
		val layout: StaticLayout?,
		val verticalPlan: VerticalLayoutPlan?,
	)

	private data class VerticalLayoutPlan(
		val glyphs: List<String>,
		val textSize: Float,
		val cellSize: Int,
		val rowCapacity: Int,
	)

	private data class BubbleInput(
		val rect: Rect,
		val sourceText: String,
		val verticalPreferred: Boolean,
	)

	private data class GroupedBubbleSource(
		val fragments: List<TextFragment>,
		val bubbleRect: Rect?,
	)

	private data class BubbleGroupingResult(
		val groups: List<GroupedBubbleSource>,
		val detectorCandidateCount: Int,
		val detectorMatchedFragmentCount: Int,
		val detectorUsedGroupCount: Int,
		val detectorSubdividedGroupCount: Int,
		val detectorSubdividedFragmentCount: Int,
		val detectorCoverageRate: Float,
		val detectorEngine: String,
		val detectorModelId: String,
		val detectorRawBoxCount: Int,
		val detectorTotalMs: Long,
		val detectorFallbackReason: String,
		val fallbackFragmentCount: Int,
		val fallbackGroupCount: Int,
		val fallbackMode: String,
	)

	private data class BubbleDetectorOutcome(
		val groups: List<GroupedBubbleSource>,
		val matchedFragmentIndices: Set<Int>,
		val candidateCount: Int,
		val matchedFragmentCount: Int,
		val subdividedGroupCount: Int,
		val subdividedFragmentCount: Int,
		val engine: String,
		val modelId: String,
		val rawBoxCount: Int,
		val totalMs: Long,
		val fallbackReason: String,
	)

	private data class IndexedFragment(
		val index: Int,
		val fragment: TextFragment,
	)

	private data class DetectedBubbleCandidate(
		val rect: Rect,
		val fragmentIndices: List<Int>,
		val score: Float,
	) {
		fun isBetterThan(other: DetectedBubbleCandidate): Boolean {
			if (score != other.score) return score > other.score
			return rectArea(rect) < rectArea(other.rect)
		}

		private fun rectArea(rect: Rect): Float {
			return (rect.width().coerceAtLeast(0) * rect.height().coerceAtLeast(0)).toFloat()
		}
	}

	private data class BubbleRoiOcrResult(
		val textsByGroupIndex: Map<Int, String>,
		val requestCount: Int,
		val successCount: Int,
		val fallbackCount: Int,
		val totalMs: Long,
		val coverageArea: Float,
	)

	private data class TextFragment(
		val rect: Rect,
		val text: String,
	)

	private suspend fun groupFragmentsForTranslation(
		fragments: List<TextFragment>,
		bitmap: Bitmap,
	): BubbleGroupingResult {
		if (fragments.isEmpty()) {
			return BubbleGroupingResult(
				groups = emptyList(),
				detectorCandidateCount = 0,
				detectorMatchedFragmentCount = 0,
				detectorUsedGroupCount = 0,
				detectorSubdividedGroupCount = 0,
				detectorSubdividedFragmentCount = 0,
				detectorCoverageRate = 0f,
				detectorEngine = "none",
				detectorModelId = "",
				detectorRawBoxCount = 0,
				detectorTotalMs = 0L,
				detectorFallbackReason = "",
				fallbackFragmentCount = 0,
				fallbackGroupCount = 0,
				fallbackMode = if (settings.isReaderTranslationBubbleGroupingEnabled) "heuristic" else "individual",
			)
		}
		val detectorOutcome = detectBubbleGroups(bitmap, fragments)
		val fallbackFragments = fragments.filterIndexed { index, _ -> index !in detectorOutcome.matchedFragmentIndices }
		val forceHeuristicFallback = !settings.isReaderTranslationBubbleGroupingEnabled &&
			shouldForceHeuristicFallback(
				totalFragmentCount = fragments.size,
				fallbackFragmentCount = fallbackFragments.size,
			)
		val fallbackMode = when {
			settings.isReaderTranslationBubbleGroupingEnabled -> "heuristic"
			forceHeuristicFallback -> "individual_guarded_to_heuristic"
			else -> "individual"
		}
		val fallbackGroups = if (settings.isReaderTranslationBubbleGroupingEnabled || forceHeuristicFallback) {
			groupFragmentsByBubble(fallbackFragments, bitmap).map { group ->
				GroupedBubbleSource(
					fragments = group,
					bubbleRect = null,
				)
			}
		} else {
			fallbackFragments.map { fragment ->
				GroupedBubbleSource(
					fragments = listOf(fragment),
					bubbleRect = null,
				)
			}
		}
		return BubbleGroupingResult(
			groups = detectorOutcome.groups + fallbackGroups,
			detectorCandidateCount = detectorOutcome.candidateCount,
			detectorMatchedFragmentCount = detectorOutcome.matchedFragmentCount,
			detectorUsedGroupCount = detectorOutcome.groups.size,
			detectorSubdividedGroupCount = detectorOutcome.subdividedGroupCount,
			detectorSubdividedFragmentCount = detectorOutcome.subdividedFragmentCount,
			detectorCoverageRate = detectorOutcome.matchedFragmentCount.toFloat() / fragments.size.toFloat(),
			detectorEngine = detectorOutcome.engine,
			detectorModelId = detectorOutcome.modelId,
			detectorRawBoxCount = detectorOutcome.rawBoxCount,
			detectorTotalMs = detectorOutcome.totalMs,
			detectorFallbackReason = detectorOutcome.fallbackReason,
			fallbackFragmentCount = fallbackFragments.size,
			fallbackGroupCount = fallbackGroups.size,
			fallbackMode = fallbackMode,
		)
	}

	private fun shouldForceHeuristicFallback(
		totalFragmentCount: Int,
		fallbackFragmentCount: Int,
	): Boolean {
		if (fallbackFragmentCount <= 0) return false
		if (fallbackFragmentCount >= MAX_INDIVIDUAL_FALLBACK_FRAGMENTS) return true
		if (totalFragmentCount <= 0) return false
		return fallbackFragmentCount.toFloat() / totalFragmentCount.toFloat() >= MAX_INDIVIDUAL_FALLBACK_RATIO
	}

	private suspend fun detectBubbleGroups(
		bitmap: Bitmap,
		fragments: List<TextFragment>,
	): BubbleDetectorOutcome {
		val onnxAttempt = runCatching {
			onnxBubbleDetectorEngine.detectAttempt(bitmap)
		}.onFailure {
			it.printStackTraceDebug()
		}.getOrNull()
		if (onnxAttempt != null) {
			log { "metric.bubble.detector.onnx.status=${onnxAttempt.status.name.lowercase()}" }
			log { "metric.bubble.detector.onnx.stage=${onnxAttempt.stage.ifBlank { "none" }}" }
			log { "metric.bubble.detector.onnx.backend=${onnxAttempt.backend.ifBlank { "none" }}" }
			log { "metric.bubble.detector.onnx.parser=${onnxAttempt.parser.ifBlank { "none" }}" }
			log { "metric.bubble.detector.onnx.input_name=${onnxAttempt.inputName.ifBlank { "none" }}" }
			log { "metric.bubble.detector.onnx.input_shape=${onnxAttempt.inputShape.ifBlank { "none" }}" }
			log { "metric.bubble.detector.onnx.output_names=${onnxAttempt.outputNames.ifBlank { "none" }}" }
			onnxAttempt.result?.let { result ->
				log { "metric.bubble.detector.onnx.decoded_boxes=${result.decodedBoxCount}" }
				log { "metric.bubble.detector.onnx.final_boxes=${result.finalBoxCount}" }
			}
			if (onnxAttempt.error.isNotBlank()) {
				log { "bubble detector onnx error=${oneLine(onnxAttempt.error, 400)}" }
			}
		}
		val onnxResult = onnxAttempt?.result
		if (onnxResult != null) {
			val grouped = groupFragmentsByDetectedRects(
				fragments = fragments,
				detectedRects = onnxResult.boxes,
				bitmap = bitmap,
			)
			if (grouped.groups.isNotEmpty()) {
				return BubbleDetectorOutcome(
					groups = grouped.groups,
					matchedFragmentIndices = grouped.matchedFragmentIndices,
					candidateCount = grouped.candidateCount,
					matchedFragmentCount = grouped.matchedFragmentCount,
					subdividedGroupCount = grouped.subdividedGroupCount,
					subdividedFragmentCount = grouped.subdividedFragmentCount,
					engine = "onnx_${onnxResult.backend.lowercase()}",
					modelId = onnxResult.modelId,
					rawBoxCount = onnxResult.rawBoxCount,
					totalMs = onnxResult.totalMs,
					fallbackReason = "",
				)
			}
			log {
				"bubble detector onnx no usable groups model=${onnxResult.modelId} rawBoxes=${onnxResult.rawBoxCount}, fallback=cv"
			}
		}
		val fallbackReason = when (onnxAttempt?.status) {
			OnnxBubbleDetectorEngine.AttemptStatus.NO_MODEL_DOWNLOADED -> "onnx_no_model_downloaded"
			OnnxBubbleDetectorEngine.AttemptStatus.RUNTIME_UNAVAILABLE -> "onnx_runtime_unavailable"
			OnnxBubbleDetectorEngine.AttemptStatus.NO_BOXES -> "onnx_no_boxes"
			OnnxBubbleDetectorEngine.AttemptStatus.SUCCESS -> "onnx_no_usable_groups"
			null -> "onnx_attempt_failed"
		}
		val attemptedModelId = onnxAttempt?.modelId.orEmpty()

		val cvStartMs = SystemClock.elapsedRealtime()
		val detectorResult = cvBubbleDetector.detect(bitmap, fragments.map { it.rect })
		val cvDurationMs = SystemClock.elapsedRealtime() - cvStartMs
		val detectorMatched = linkedSetOf<Int>()
		val detectorGroups = detectorResult.groups.mapNotNull { group ->
			val bubbleFragments = group.fragmentIndices.mapNotNull { index ->
				fragments.getOrNull(index)
			}
			if (bubbleFragments.isEmpty()) {
				return@mapNotNull null
			}
			detectorMatched += group.fragmentIndices
			GroupedBubbleSource(
				fragments = bubbleFragments,
				bubbleRect = group.rect,
			)
		}
		return BubbleDetectorOutcome(
			groups = detectorGroups,
			matchedFragmentIndices = detectorMatched,
			candidateCount = detectorResult.candidateCount,
			matchedFragmentCount = detectorResult.matchedFragmentCount,
			subdividedGroupCount = 0,
			subdividedFragmentCount = 0,
			engine = "cv",
			modelId = attemptedModelId,
			rawBoxCount = detectorResult.candidateCount,
			totalMs = cvDurationMs,
			fallbackReason = fallbackReason,
		)
	}

	private fun groupFragmentsByDetectedRects(
		fragments: List<TextFragment>,
		detectedRects: List<Rect>,
		bitmap: Bitmap,
	): BubbleDetectorOutcome {
		if (detectedRects.isEmpty()) {
			return BubbleDetectorOutcome(
				groups = emptyList(),
				matchedFragmentIndices = emptySet(),
				candidateCount = 0,
				matchedFragmentCount = 0,
				subdividedGroupCount = 0,
				subdividedFragmentCount = 0,
				engine = "onnx",
				modelId = "",
				rawBoxCount = 0,
				totalMs = 0L,
				fallbackReason = "",
			)
		}
		val bitmapArea = (bitmap.width * bitmap.height).toFloat().coerceAtLeast(1f)
		val uniqueCandidates = linkedMapOf<String, DetectedBubbleCandidate>()
		for (detectedRect in detectedRects) {
			val matched = fragments.indices.filter { index ->
				matchesDetectedBubbleRect(detectedRect, fragments[index].rect)
			}
			if (matched.isEmpty()) continue
			val unionRect = mergeRects(matched.map { fragments[it].rect }) ?: continue
			val candidate = buildDetectedBubbleCandidate(
				detectedRect = detectedRect,
				unionRect = unionRect,
				fragmentRects = fragments.map { it.rect },
				matchedIndices = matched,
				bitmapArea = bitmapArea,
				bitmapWidth = bitmap.width,
				bitmapHeight = bitmap.height,
			) ?: continue
			val key = candidate.fragmentIndices.joinToString(",")
			val existing = uniqueCandidates[key]
			if (existing == null || candidate.isBetterThan(existing)) {
				uniqueCandidates[key] = candidate
			}
		}
		if (uniqueCandidates.isEmpty()) {
			return BubbleDetectorOutcome(
				groups = emptyList(),
				matchedFragmentIndices = emptySet(),
				candidateCount = 0,
				matchedFragmentCount = 0,
				subdividedGroupCount = 0,
				subdividedFragmentCount = 0,
				engine = "onnx",
				modelId = "",
				rawBoxCount = detectedRects.size,
				totalMs = 0L,
				fallbackReason = "",
			)
		}
			val claimed = linkedSetOf<Int>()
			var subdividedGroups = 0
			var subdividedFragments = 0
			val groups = buildList {
				for (candidate in uniqueCandidates.values.sortedWith(
					compareByDescending<DetectedBubbleCandidate> { it.fragmentIndices.size }
						.thenByDescending { it.score }
						.thenBy { rectArea(it.rect) }
				)) {
					val available = candidate.fragmentIndices.filterNot { it in claimed }
					if (available.isEmpty()) continue
					val subdivided = splitDetectedCandidate(
						candidate = candidate,
						fragmentIndices = available,
						fragments = fragments,
						bitmap = bitmap,
					)
					if (subdivided.isEmpty()) continue
					subdividedGroups += subdivided.size
					subdividedFragments += subdivided.sumOf { it.indices.size }
					subdivided.forEach { subgroup ->
						claimed += subgroup.indices
						add(
							GroupedBubbleSource(
								fragments = subgroup.fragments,
								bubbleRect = subgroup.bubbleRect,
							)
						)
					}
				}
			}
			return BubbleDetectorOutcome(
				groups = groups,
				matchedFragmentIndices = claimed,
				candidateCount = uniqueCandidates.size,
				matchedFragmentCount = claimed.size,
				subdividedGroupCount = subdividedGroups,
				subdividedFragmentCount = subdividedFragments,
				engine = "onnx",
				modelId = "",
				rawBoxCount = detectedRects.size,
				totalMs = 0L,
				fallbackReason = "",
			)
	}

	private fun matchesDetectedBubbleRect(candidateRect: Rect, fragmentRect: Rect): Boolean {
		if (candidateRect.contains(fragmentRect.centerX(), fragmentRect.centerY())) {
			return true
		}
		val fragmentArea = rectArea(fragmentRect).coerceAtLeast(1f)
		val directOverlap = overlapArea(candidateRect, fragmentRect) / fragmentArea
		if (directOverlap >= 0.28f) {
			return true
		}
		val padX = max(dp(6f), candidateRect.width() / 10)
		val padY = max(dp(6f), candidateRect.height() / 10)
		val expanded = Rect(
			(candidateRect.left - padX).coerceAtLeast(0),
			(candidateRect.top - padY).coerceAtLeast(0),
			candidateRect.right + padX,
			candidateRect.bottom + padY,
		)
		if (!expanded.contains(fragmentRect.centerX(), fragmentRect.centerY())) {
			return false
		}
		val expandedOverlap = overlapArea(expanded, fragmentRect) / fragmentArea
		return expandedOverlap >= 0.60f
	}

	private fun buildDetectedBubbleCandidate(
		detectedRect: Rect,
		unionRect: Rect,
		fragmentRects: List<Rect>,
		matchedIndices: List<Int>,
		bitmapArea: Float,
		bitmapWidth: Int,
		bitmapHeight: Int,
	): DetectedBubbleCandidate? {
		val candidateArea = rectArea(detectedRect).coerceAtLeast(1f)
		if (candidateArea > bitmapArea * 0.45f) return null
		val touchesEdge = detectedRect.left <= 0 || detectedRect.top <= 0 ||
			detectedRect.right >= bitmapWidth || detectedRect.bottom >= bitmapHeight
		if (touchesEdge && candidateArea > bitmapArea * 0.24f) return null
		val fragmentsArea = matchedIndices.sumOf { rectArea(fragmentRects[it]).toDouble() }.toFloat()
		val unionArea = rectArea(unionRect).coerceAtLeast(1f)
		val inflation = candidateArea / unionArea
		val textCoverage = fragmentsArea / candidateArea
		val matchedCount = matchedIndices.size
		if (matchedCount > MAX_DETECTED_GROUP_FRAGMENTS) {
			return null
		}
		val maxInflation = when {
			matchedCount >= 3 -> 16f
			matchedCount == 2 -> 20f
			else -> 26f
		}
		val minCoverage = when {
			matchedCount >= 3 -> 0.006f
			matchedCount == 2 -> 0.010f
			else -> 0.015f
		}
		if (inflation > maxInflation || textCoverage < minCoverage) {
			return null
		}
		val tightenedRect = tightenDetectedBubbleRect(detectedRect, unionRect)
		if (tightenedRect.width() <= dp(8f) || tightenedRect.height() <= dp(8f)) {
			return null
		}
		val score = matchedCount * 4f + textCoverage * 120f - inflation - if (touchesEdge) 2f else 0f
		return DetectedBubbleCandidate(
			rect = tightenedRect,
			fragmentIndices = matchedIndices.sorted(),
			score = score,
		)
	}

	private fun tightenDetectedBubbleRect(candidateRect: Rect, unionRect: Rect): Rect {
		val padX = max(dp(8f), unionRect.width() / 5)
		val padY = max(dp(8f), unionRect.height() / 5)
		val left = max(candidateRect.left, unionRect.left - padX)
		val top = max(candidateRect.top, unionRect.top - padY)
		val right = min(candidateRect.right, unionRect.right + padX)
		val bottom = min(candidateRect.bottom, unionRect.bottom + padY)
		return Rect(
			left,
			top,
			max(left + dp(8f), right),
			max(top + dp(8f), bottom),
		)
	}

	private fun overlapArea(a: Rect, b: Rect): Float {
		val width = (min(a.right, b.right) - max(a.left, b.left)).coerceAtLeast(0)
		val height = (min(a.bottom, b.bottom) - max(a.top, b.top)).coerceAtLeast(0)
		return (width * height).toFloat()
	}

	private data class DetectedCandidateSubdivision(
		val indices: List<Int>,
		val fragments: List<TextFragment>,
		val bubbleRect: Rect,
	)

	private fun splitDetectedCandidate(
		candidate: DetectedBubbleCandidate,
		fragmentIndices: List<Int>,
		fragments: List<TextFragment>,
		bitmap: Bitmap,
	): List<DetectedCandidateSubdivision> {
		val indexed = fragmentIndices.mapNotNull { index ->
			fragments.getOrNull(index)?.let { fragment ->
				IndexedFragment(index = index, fragment = fragment)
			}
		}
		if (indexed.isEmpty()) return emptyList()
		val groupedIndices = groupIndexedFragmentsByBubble(indexed, bitmap)
		return groupedIndices.mapNotNull { subgroup ->
			val subgroupFragments = subgroup.map { it.fragment }
			val subgroupRect = mergeRects(subgroupFragments.map { it.rect }) ?: return@mapNotNull null
			val tightened = tightenDetectedBubbleRect(candidate.rect, subgroupRect)
			if (tightened.width() <= dp(8f) || tightened.height() <= dp(8f)) {
				return@mapNotNull null
			}
			val pageArea = bitmap.width.toFloat() * bitmap.height.toFloat()
			val rectArea = tightened.width().toFloat() * tightened.height().toFloat()
			if (rectArea > pageArea * 0.35f || tightened.width() > bitmap.width * 0.8f || tightened.height() > bitmap.height * 0.8f) {
				return@mapNotNull null
			}
			DetectedCandidateSubdivision(
				indices = subgroup.map { it.index },
				fragments = subgroupFragments,
				bubbleRect = tightened,
			)
		}
	}

	private fun groupIndexedFragmentsByBubble(
		fragments: List<IndexedFragment>,
		bitmap: Bitmap,
	): List<List<IndexedFragment>> {
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

		for (i in fragments.indices) {
			val fA = fragments[i].fragment
			val aRect = fA.rect
			for (j in i + 1 until fragments.size) {
				val fB = fragments[j].fragment
				val bRect = fB.rect

				val xOverlap = overlapLen(aRect.left, aRect.right, bRect.left, bRect.right)
				val yOverlap = overlapLen(aRect.top, aRect.bottom, bRect.top, bRect.bottom)
				val gapX = axisGap(aRect.left, aRect.right, bRect.left, bRect.right)
				val gapY = axisGap(aRect.top, aRect.bottom, bRect.top, bRect.bottom)

				val minW = min(aRect.width(), bRect.width()).coerceAtLeast(1)
				val minH = min(aRect.height(), bRect.height()).coerceAtLeast(1)

				val sameCol = xOverlap > minW * 0.3f
				val sameRow = yOverlap > minH * 0.3f

				val canMerge = if (sameCol) {
					gapX <= dp(4f) && gapY <= dp(16f)
				} else if (sameRow) {
					gapY <= dp(4f) && gapX <= dp(16f)
				} else {
					gapX <= dp(2f) && gapY <= dp(2f) && (xOverlap > 0 || yOverlap > 0)
				}

				if (canMerge && shouldMergeFragments(fA, fB, bitmap)) {
					union(i, j)
				}
			}
		}

		val groups = linkedMapOf<Int, MutableList<IndexedFragment>>()
		for (i in fragments.indices) {
			val root = find(i)
			groups.getOrPut(root) { mutableListOf() }.add(fragments[i])
		}
		return groups.values.toList()
	}

	private suspend fun recognizeBubbleTextsByRoi(
		groups: List<GroupedBubbleSource>,
		sourceUri: Uri,
		sourceLang: String,
		pageId: Long,
		bitmap: Bitmap,
	): BubbleRoiOcrResult {
		if (groups.isEmpty()) {
			return BubbleRoiOcrResult(emptyMap(), 0, 0, 0, 0L, 0f)
		}
		val engine = preferredRoiOcrEngine()
		val textsByIndex = linkedMapOf<Int, String>()
		var requestCount = 0
		var successCount = 0
		var attemptedArea = 0f
		var successArea = 0f
		var totalMs = 0L
		for ((index, group) in groups.withIndex()) {
			if (requestCount >= MAX_ROI_OCR_REQUESTS_PER_PAGE) break
			val roiRect = group.bubbleRect ?: mergeRects(group.fragments.map { it.rect }) ?: continue
			if (!shouldTryRoiOcr(roiRect, bitmap)) continue
			requestCount++
			attemptedArea += rectArea(roiRect)
			val request = OcrRequest(
				sourceUri = sourceUri,
				sourceLang = sourceLang,
				roi = roiRect,
				pageId = pageId,
				requestType = OcrRequestType.ROI,
				debugTag = "page:$pageId:bubble:$index",
			)
			val startMs = SystemClock.elapsedRealtime()
			val roiBlocks = runCatching {
				recognizeTextByEngine(engine, request)
			}.onFailure {
				it.printStackTraceDebug()
			}.getOrDefault(emptyList())
			totalMs += SystemClock.elapsedRealtime() - startMs
			val roiText = composeOcrBlocksText(roiBlocks, sourceLang, roiRect).trim()
			if (roiText.isBlank()) continue
			successCount++
			successArea += rectArea(roiRect)
			textsByIndex[index] = roiText
			log { "roi ocr hit engine=${engine.name} idx=$index box=$roiRect text=${oneLine(roiText)}" }
		}
		return BubbleRoiOcrResult(
			textsByGroupIndex = textsByIndex,
			requestCount = requestCount,
			successCount = successCount,
			fallbackCount = (requestCount - successCount).coerceAtLeast(0),
			totalMs = totalMs,
			coverageArea = if (attemptedArea > 0f) successArea / attemptedArea else 0f,
		)
	}

	private fun preferredRoiOcrEngine(): ReaderOcrEngine {
		return when (settings.readerTranslationOcrEngine) {
			ReaderOcrEngine.PADDLE -> ReaderOcrEngine.NCNN
			else -> settings.readerTranslationOcrEngine
		}
	}

	private fun shouldTryRoiOcr(rect: Rect, bitmap: Bitmap): Boolean {
		if (rect.width() < dp(24f) || rect.height() < dp(24f)) return false
		val pageArea = (bitmap.width * bitmap.height).toFloat().coerceAtLeast(1f)
		if (rectArea(rect) / pageArea > 0.22f) return false
		return isLikelySpeechBubbleRegion(bitmap, rect)
	}

	private fun composeOcrBlocksText(
		blocks: List<OcrTextBlock>,
		sourceLang: String,
		fallbackRect: Rect,
	): String {
		if (blocks.isEmpty()) return ""
		val fragments = blocks.mapNotNull { block ->
			val text = block.text.trim()
			if (text.isBlank()) return@mapNotNull null
			TextFragment(
				rect = block.boundingBox ?: fallbackRect,
				text = text,
			)
		}
		if (fragments.isEmpty()) return ""
		return composeGroupedText(fragments, sourceLang).trim()
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

		val mergePad = dp(2f)
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
			if (verticalPreferred) {
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

			var textSize = initialHorizontalTextSize(width = width, height = height)
			var layout = buildTextLayout(text, width, textSize)
			while (layout.height > height && textSize > dp(8f)) {
				textSize -= 1f
				layout = buildTextLayout(text, width, textSize)
			}
			if (layout.height > height) {
				val lineHeight = max(1, layout.getLineBottom(0))
				val maxLines = max(1, height / lineHeight)
				layout = buildTextLayout(
					text = text,
					width = width,
					textSize = textSize,
					maxLines = maxLines,
					ellipsize = TextUtils.TruncateAt.END,
				)
			}
			val overflow = (layout.height - height).coerceAtLeast(0)
			val contentW = computeLayoutUsedWidth(layout)
			val contentH = layout.height
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
			val adjustedLayout = if (!bubbleLikeRegion && drawContentWidth != width) {
				buildTextLayout(
					text = text,
					width = drawContentWidth,
					textSize = textSize,
					maxLines = Int.MAX_VALUE,
				)
			} else {
				layout
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
		val block = Character.UnicodeBlock.of(this)
			return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
			block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
			block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
			block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C ||
			block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D ||
			block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E ||
			block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F ||
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

		private fun buildOpenAiMicroBatches(texts: List<String>): List<List<String>> {
			if (texts.isEmpty()) return emptyList()
			if (texts.size <= MAX_OPENAI_BATCH_SIZE) return listOf(texts)
			val result = mutableListOf<List<String>>()
			val current = mutableListOf<String>()

			fun flush() {
				if (current.isNotEmpty()) {
					result += current.toList()
					current.clear()
				}
			}

			for (text in texts) {
				val noisy = isLikelyNoisyOcrSource(text)
				val longText = text.length >= 28
				val shortSfxLike = text.length <= 10 && text.count { it.isJapaneseKana() } >= 2
				val preferSingle = noisy || longText
				if (preferSingle) {
					flush()
					result += listOf(text)
					continue
				}
				if (current.isNotEmpty()) {
					val hasShortSfxLike = current.any { it.length <= 10 && it.count { ch -> ch.isJapaneseKana() } >= 2 }
					if ((shortSfxLike && !hasShortSfxLike && current.size >= 2) || current.size >= MAX_OPENAI_BATCH_SIZE) {
						flush()
					}
				}
				current += text
				if (current.size >= MAX_OPENAI_BATCH_SIZE) {
					flush()
				}
			}
			flush()
			return result
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
			settings.readerTranslationApiEndpoint,
			settings.readerTranslationApiModel,
			settings.readerTranslationBubbleGroupingTuning,
			settings.isReaderTranslationBubbleGroupingEnabled.toString(),
			settings.readerTranslationOverlayCompactness,
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
			settings.readerTranslationApiEndpoint,
			settings.readerTranslationApiModel,
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

	private fun ResponseBody?.readJsonTextUtf8(): String {
		if (this == null) return ""
		return runCatching {
			bytes().toString(Charsets.UTF_8)
		}.getOrDefault("")
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
			const val TRANSLATION_PIPELINE_VERSION = "2026-03-11-roi-ocr-7"
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
		val BUBBLE_EXPAND_SCALES = floatArrayOf(1f)
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

			private fun normalizeBatchReplyContent(raw: String): String {
				val clean = stripThinkContent(raw).trim()
				if (clean.isBlank()) return ""
				return normalizeJsonLikeContent(clean)
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
