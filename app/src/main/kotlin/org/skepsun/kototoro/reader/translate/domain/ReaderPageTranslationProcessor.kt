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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
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
	private val onnxTranslationEngine: OnnxReaderTranslationEngine,
) {

	private val processingMutex = Mutex()
	private val pageLogLock = Any()
	private val renderedSourceMap = LruCache<String, String>(512)
	private val pageDebugLogs = LongSparseArray<ArrayDeque<String>>()
	private val pageRenderEpochs = LongSparseArray<Int>()
	@Volatile
	private var currentLoggingPageId: Long = NO_LOGGING_PAGE_ID
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
		synchronized(pageLogLock) {
			pageDebugLogs.clear()
			pageRenderEpochs.clear()
		}
		renderCacheEpoch += 1
		log { "translation caches cleared epoch=$renderCacheEpoch" }
	}

	fun clearPageCaches(pageId: Long) {
		synchronized(pageLogLock) {
			pageDebugLogs.remove(pageId)
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
			rememberRenderedSource(it, sourceUri)
		}
	}

	fun peekSourceOfRendered(renderedUri: Uri): Uri? {
		return renderedSourceMap[renderedUri.toString()]?.toUri()
	}

	suspend fun process(page: MangaPage, sourceUri: Uri): Uri {
		if (!settings.isReaderTranslationEnabled || !settings.isReaderTranslationShowTranslated) {
			return sourceUri
		}
		val sourceLang = settings.readerTranslationSourceLanguage.lowercase()
		val targetLang = settings.readerTranslationTargetLanguage.lowercase()
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
		return processingMutex.withLock {
			currentLoggingPageId = page.id
			appendPageLog(page.id, "process start page=${page.id}")
			cache[renderCacheKey]?.let {
				return@withLock it.toUri().also { rendered ->
					rememberRenderedSource(rendered, localUri)
				}
			}
			runCatching {
				processImpl(localUri, renderCacheKey, sourceLang, targetLang)
			}.onFailure {
				it.printStackTraceDebug()
				appendPageLog(page.id, "process failed: ${it.javaClass.simpleName}: ${it.message.orEmpty()}")
				appendPageLog(page.id, "fail_code=$FAIL_CODE_PROCESS_EXCEPTION")
			}.getOrDefault(sourceUri)
		}.also {
			currentLoggingPageId = NO_LOGGING_PAGE_ID
		}
	}

	fun getPageDebugLog(pageId: Long): String {
		synchronized(pageLogLock) {
			val lines = pageDebugLogs[pageId] ?: return ""
			return lines.joinToString(separator = "\n")
		}
	}

	@WorkerThread
	private suspend fun processImpl(
		sourceUri: Uri,
		renderCacheKey: String,
		sourceLang: String,
		targetLang: String,
	): Uri {
		// OCR result caching: skip re-OCR if we already processed this image
		val ocrCacheKey = buildOcrCacheKey(sourceUri.toString(), sourceLang)
		val textBlocks = textCache[ocrCacheKey]?.let { cached ->
			log { "ocr cache hit" }
			deserializeOcrBlocks(cached)
		} ?: run {
			val blocks = recognizeTextWithFallback(sourceUri, sourceLang)
			if (blocks.isNotEmpty()) {
				textCache[ocrCacheKey] = serializeOcrBlocks(blocks)
			}
			blocks
		}
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
		val groupedFragments = groupFragmentsByBubble(sourceFragments, bitmap)
			val bubbleInputs = groupedFragments.mapNotNull { group ->
				val mergedRect = mergeRects(group.map { it.rect }) ?: return@mapNotNull null
				val sourceText = composeGroupedText(group, sourceLang).trim()
				if (sourceText.isBlank()) {
					return@mapNotNull null
				}
				val verticalPreferred = isVerticalTargetLanguage(targetLang) &&
					sourceLang.startsWith("ja") &&
					(isLikelyColumnLayout(group) || mergedRect.height() > mergedRect.width() * 13 / 10)
				BubbleInput(
					rect = mergedRect,
					sourceText = sourceText,
				verticalPreferred = verticalPreferred,
			)
		}
		val translatedMap = translateBlocksCached(
			texts = bubbleInputs.map { it.sourceText },
			sourceLang = sourceLang,
			targetLang = targetLang,
		)
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
		if (preparedBubbles.isEmpty()) {
			val failCode = if (nonEmptyTranslatedCount == 0) {
				FAIL_CODE_TRANSLATE_EMPTY
			} else {
				FAIL_CODE_RENDER_FILTERED
			}
			log { "fail_code=$failCode" }
			bitmap.recycle()
			return sourceUri
		}
		val output = cache.set(renderCacheKey, bitmap).toUri()
		rememberRenderedSource(output, sourceUri)
		bitmap.recycle()
		return output
	}

	private fun rememberRenderedSource(renderedUri: Uri, sourceUri: Uri) {
		renderedSourceMap.put(renderedUri.toString(), sourceUri.toString())
	}

	private suspend fun recognizeTextWithFallback(sourceUri: Uri, sourceLang: String): List<OcrTextBlock> {
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
			val result = runCatching {
				recognizeTextByEngine(engine, sourceUri, sourceLang)
			}.onFailure {
				it.printStackTraceDebug()
			}.getOrDefault(emptyList())
			if (result.isNotEmpty()) {
				if (result.size > bestResult.size) {
					bestResult = result
					bestEngine = engine
				}
				if (result.size >= minAcceptableBlocks || engine == ReaderOcrEngine.MLKIT) {
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
			log { "ocr fallback use best engine=$bestEngine blocks=${bestResult.size}" }
		}
		return bestResult
	}

	private suspend fun recognizeTextByEngine(
		engine: ReaderOcrEngine,
		sourceUri: Uri,
		sourceLang: String,
	): List<OcrTextBlock> {
		return when (engine) {
			ReaderOcrEngine.MLKIT -> mlKitOcrEngine.recognize(sourceUri, sourceLang)
			ReaderOcrEngine.PADDLE -> emptyList()
			ReaderOcrEngine.TFLITE -> tfliteOcrEngine.recognize(sourceUri, sourceLang)
			ReaderOcrEngine.HYBRID -> hybridOcrEngine.recognize(sourceUri, sourceLang)
			ReaderOcrEngine.NCNN -> ncnnOcrEngine.recognize(sourceUri, sourceLang)
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
				translated[text] = cached
				log { "translate cache hit src=${oneLine(text)} out=${oneLine(cached)}" }
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
						translated[text] = onnxText
						textCache[buildTextCacheKey(text, sourceLang, targetLang)] = onnxText
						log { "translate onnx hit src=${oneLine(text)} out=${oneLine(onnxText)}" }
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
				if (local.isNotBlank()) {
					translated[text] = local
					textCache[buildTextCacheKey(text, sourceLang, targetLang)] = local
					log { "translate local hit src=${oneLine(text)} out=${oneLine(local)}" }
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
						translated[text] = apiText
						textCache[buildTextCacheKey(text, sourceLang, targetLang)] = apiText
						log { "translate api hit src=${oneLine(text)} out=${oneLine(apiText)}" }
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
		fun mapById(input: List<String>, parsed: Map<Int, String>): Map<String, String> {
			return input.mapIndexed { index, text ->
				text to parsed[index + 1].orEmpty().trim()
			}.toMap()
		}

		val firstParsed = requestOpenAiBatch(texts, sourceLang, targetLang)
		val mapped = mapById(texts, firstParsed).toMutableMap()
		val missing = texts.filter { mapped[it].isNullOrBlank() }
		if (missing.isNotEmpty() && missing.size < texts.size) {
			log { "openai partial missing=${missing.size}, retrying missing batch" }
			val retryParsed = requestOpenAiBatch(missing, sourceLang, targetLang)
			val retryMapped = mapById(missing, retryParsed)
			for ((text, out) in retryMapped) {
				if (out.isNotBlank()) {
					mapped[text] = out
				}
			}
		}
		if (mapped.values.all { it.isBlank() }) {
			log { "openai parse/translate empty for whole batch" }
		}
		return mapped
	}

	private suspend fun requestOpenAiBatch(
		texts: List<String>,
		sourceLang: String,
		targetLang: String,
	): Map<Int, String> {
		if (texts.isEmpty()) return emptyMap()
		val indexedInput = JSONArray().apply {
			texts.forEachIndexed { index, text ->
				put(JSONObject().put("id", index + 1).put("text", text))
			}
		}
		val endpoint = settings.readerTranslationApiEndpoint.trim()
		val apiKey = settings.readerTranslationApiKey.trim()
		val model = settings.readerTranslationApiModel.trim().ifBlank { DEFAULT_OPENAI_MODEL }
		val userPrompt = buildString {
			appendLine("Translate from $sourceLang to $targetLang.")
			appendLine("Keep IDs unchanged.")
			appendLine("Return JSON object only in this format: {\"items\":[{\"id\":1,\"translation\":\"...\"}]}")
			appendLine("Input JSON:")
			append(indexedInput.toString())
		}

		suspend fun requestOnce(useJsonObject: Boolean): String? {
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
				if (useJsonObject) {
					put(
						"response_format",
						JSONObject().apply {
							put("type", "json_object")
						}
					)
				}
			}

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
				val rawBody = resp.body?.string().orEmpty()
				if (!resp.isSuccessful) {
					log { "openai request failed code=${resp.code} msg=${resp.message} body=${oneLine(rawBody, 300)}" }
					return null
				}
				if (rawBody.isBlank()) return null
				val json = runCatching { JSONObject(rawBody) }.getOrNull() ?: return null
				val content = extractOpenAiMessageContent(json).orEmpty()
				if (content.isBlank()) return null
				log { "openai raw reply=${oneLine(content, 400)}" }
				return stripThinkContent(content).trim().ifBlank { null }
			}
		}

		val content = requestOnce(useJsonObject = true) ?: requestOnce(useJsonObject = false)
		return parseBatchTranslationJson(content.orEmpty(), texts.size)
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
			val body = resp.body?.string().orEmpty()
			if (body.isBlank()) {
				return ""
			}
			val parsed = stripThinkContent(parseTranslatedText(body) ?: "").trim()
			log { "api raw reply=${oneLine(body, 300)} parsed=${oneLine(parsed)} src=${oneLine(text)}" }
			return parsed
		}
	}

	private fun parseTranslatedText(body: String): String? {
		return runCatching {
			val json = JSONObject(body)
			when {
				json.has("translatedText") -> json.getString("translatedText")
				json.has("translation") -> json.getString("translation")
				json.has("data") -> {
					val data = json.getJSONObject("data")
					val translations = data.optJSONArray("translations") ?: JSONArray()
					translations.optJSONObject(0)?.optString("translatedText")
				}

				else -> null
			}
		}.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
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

		return runCatching {
			val map = LinkedHashMap<Int, String>(expectedSize)
			if (clean.startsWith("[")) {
				val arr = JSONArray(clean)
				for (i in 0 until arr.length()) {
					val obj = arr.optJSONObject(i) ?: continue
					val id = obj.optInt("id", i + 1)
					val translation = pickTranslationField(obj)
					if (id > 0 && translation.isNotBlank()) {
						map[id] = translation
					}
				}
			} else {
				val json = JSONObject(clean)
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
			if (map.isEmpty() || map.size > expectedSize) {
				log { "openai parsed invalid mapSize=${map.size} expected<=$expectedSize content=${oneLine(clean, 400)}" }
				emptyMap()
			} else {
				log { "openai parsed items=${map.size}" }
				map
			}
		}.getOrElse {
			log { "openai parse exception content=${oneLine(clean, 400)}" }
			emptyMap()
		}
	}

	private fun pickTranslationField(obj: JSONObject): String {
		return listOf("translation", "translatedText", "text", "output")
			.firstNotNullOfOrNull { key ->
				obj.optString(key).trim().takeIf { it.isNotBlank() }
			}.orEmpty()
	}

	private fun normalizeJsonLikeContent(raw: String): String {
		val text = raw.trim()
		if (!text.startsWith("```")) return text
		val lines = text.lines()
		if (lines.isEmpty()) return text
		val body = lines.drop(1).dropLastWhile { it.trim().startsWith("```") }.joinToString("\n").trim()
		return body.ifBlank { text }
	}

	private fun stripThinkContent(text: String): String {
		if (text.isBlank()) return text
		return THINK_TAG_REGEX.replace(text, "")
			.replace("<analysis>", "", ignoreCase = true)
			.replace("</analysis>", "", ignoreCase = true)
			.trim()
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

	private data class TextFragment(
		val rect: Rect,
		val text: String,
	)

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
		val baseRect = Rect(
			rect.left.coerceIn(0, bitmapWidth - 1),
			rect.top.coerceIn(0, bitmapHeight - 1),
			rect.right.coerceIn(1, bitmapWidth),
			rect.bottom.coerceIn(1, bitmapHeight),
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

			var textSize = min(safeRect.height() * 0.42f, dp(18f).toFloat())
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
		var textSize = min(height * 0.42f, dp(18f).toFloat())
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

	private fun oneLine(text: String, limit: Int = 140): String {
		if (text.isBlank()) return ""
		return text.replace('\n', ' ').replace('\r', ' ').trim().let {
			if (it.length <= limit) it else it.take(limit) + "..."
		}
	}

	private inline fun log(message: () -> String) {
		val msg = message()
		val pageId = currentLoggingPageId
		if (pageId != NO_LOGGING_PAGE_ID) {
			appendPageLog(pageId, msg)
		}
		if (settings.isReaderTranslationDebugLogsEnabled) {
			Log.d(LOG_TAG, msg)
		}
	}

	private fun appendPageLog(pageId: Long, message: String) {
		synchronized(pageLogLock) {
			val queue = pageDebugLogs[pageId] ?: ArrayDeque<String>().also { pageDebugLogs.put(pageId, it) }
			if (queue.size >= MAX_PAGE_LOG_LINES) {
				repeat(queue.size - MAX_PAGE_LOG_LINES + 1) { queue.removeFirstOrNull() }
			}
			queue.addLast(message)
		}
	}

	private fun getPageRenderEpoch(pageId: Long): Int {
		synchronized(pageLogLock) {
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
			settings.readerTranslationOverlayCompactness,
		).joinToString("|")
		return "${RENDER_CACHE_PREFIX}${raw.sha256()}"
	}

	private fun buildTextCacheKey(text: String, sourceLang: String, targetLang: String): String {
		val raw = listOf(
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

	private fun serializeOcrBlocks(blocks: List<OcrTextBlock>): String {
		val arr = JSONArray()
		for (block in blocks) {
			val obj = JSONObject()
			obj.put("text", block.text)
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
			result.add(OcrTextBlock(text = obj.getString("text"), boundingBox = box))
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
		const val OPENAI_TRANSLATION_SYSTEM_PROMPT = """
You are a professional manga translator.
Rules:
- Translate source text to target language accurately and naturally.
- Keep item ids unchanged.
- Preserve tone and intent.
- Do not add explanations.
- Do not output reasoning, chain-of-thought, or any internal process.
- Never output tags like <think>...</think>.
- Output JSON only.
"""
		val THINK_TAG_REGEX = Regex("(?is)<think>.*?</think>")
		val BUBBLE_EXPAND_SCALES = floatArrayOf(1f)
		const val TEXT_CACHE_PREFIX = "reader_translate_text_"
		const val RENDER_CACHE_PREFIX = "reader_translate_render_"
		const val OCR_CACHE_PREFIX = "reader_translate_ocr_"
		const val LOG_TAG = "ReaderTranslate"
		const val MAX_PAGE_LOG_LINES = 500
		const val NO_LOGGING_PAGE_ID = -1L
		const val FAIL_CODE_OCR_EMPTY = "OCR_EMPTY"
		const val FAIL_CODE_TRANSLATE_EMPTY = "TRANSLATE_EMPTY"
		const val FAIL_CODE_RENDER_FILTERED = "RENDER_FILTERED"
		const val FAIL_CODE_PROCESS_EXCEPTION = "PROCESS_EXCEPTION"
	}
}
