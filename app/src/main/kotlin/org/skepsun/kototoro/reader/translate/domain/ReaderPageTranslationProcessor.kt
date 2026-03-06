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
import java.security.MessageDigest
import javax.inject.Inject
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
) {

	private val processingMutex = Mutex()
	private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		style = Paint.Style.FILL
		alpha = 242
	}
	private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.BLACK
		textAlign = Paint.Align.LEFT
	}

	suspend fun process(page: MangaPage, sourceUri: Uri): Uri {
		if (!settings.isReaderTranslationEnabled || !settings.isReaderTranslationShowTranslated) {
			return sourceUri
		}
		if (!sourceUri.isFileUri()) {
			return sourceUri
		}
		val sourceLang = settings.readerTranslationSourceLanguage.lowercase()
		val targetLang = settings.readerTranslationTargetLanguage.lowercase()
		if (sourceLang == targetLang) {
			return sourceUri
		}
		val renderCacheKey = buildRenderedCacheKey(page.url, sourceUri.toString(), sourceLang, targetLang)
		cache[renderCacheKey]?.let { return it.toUri() }
		log { "process start page=${page.id} sourceLang=$sourceLang targetLang=$targetLang ocr=${settings.readerTranslationOcrEngine}" }
		return processingMutex.withLock {
			cache[renderCacheKey]?.let { return@withLock it.toUri() }
			runCatching {
				processImpl(sourceUri, renderCacheKey, sourceLang, targetLang)
			}.onFailure {
				it.printStackTraceDebug()
			}.getOrDefault(sourceUri)
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
			val blocks = recognizeText(sourceUri, sourceLang)
			if (blocks.isNotEmpty()) {
				textCache[ocrCacheKey] = serializeOcrBlocks(blocks)
			}
			blocks
		}
		log { "ocr done blocks=${textBlocks.size}" }
		if (textBlocks.isEmpty()) {
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

		// Translate all blocks in parallel for speed
		val translatedBlocks = coroutineScope {
			textBlocks
				.filter { it.boundingBox != null && it.text.trim().isNotBlank() }
				.map { block ->
					async {
						val translated = translateTextCached(block.text.trim(), sourceLang, targetLang)
						block to translated
					}
				}.awaitAll()
		}
		for ((block, translated) in translatedBlocks) {
			if (translated.isBlank()) continue
			drawTranslatedBubble(canvas, block.boundingBox!!, translated, bitmap.width, bitmap.height)
		}
		log { "render done translatedBlocks=${translatedBlocks.size}" }
		val output = cache.set(renderCacheKey, bitmap).toUri()
		bitmap.recycle()
		return output
	}

	private suspend fun recognizeText(sourceUri: Uri, sourceLang: String): List<OcrTextBlock> {
		return when (settings.readerTranslationOcrEngine) {
			ReaderOcrEngine.MLKIT -> mlKitOcrEngine.recognize(sourceUri, sourceLang)
			ReaderOcrEngine.PADDLE -> runCatching {
				paddleOcrEngine.recognize(sourceUri, sourceLang)
			}.onFailure {
				it.printStackTraceDebug()
			}.getOrElse {
				mlKitOcrEngine.recognize(sourceUri, sourceLang)
			}
			ReaderOcrEngine.TFLITE -> runCatching {
				tfliteOcrEngine.recognize(sourceUri, sourceLang)
			}.onFailure {
				it.printStackTraceDebug()
			}.getOrElse {
				mlKitOcrEngine.recognize(sourceUri, sourceLang)
			}
			ReaderOcrEngine.HYBRID -> runCatching {
				hybridOcrEngine.recognize(sourceUri, sourceLang)
			}.onFailure {
				it.printStackTraceDebug()
			}.getOrElse {
				mlKitOcrEngine.recognize(sourceUri, sourceLang)
			}
			ReaderOcrEngine.NCNN -> runCatching {
				ncnnOcrEngine.recognize(sourceUri, sourceLang)
			}.onFailure {
				it.printStackTraceDebug()
			}.getOrElse {
				mlKitOcrEngine.recognize(sourceUri, sourceLang)
			}
		}
	}

	private suspend fun translateTextCached(text: String, sourceLang: String, targetLang: String): String {
		val cacheKey = buildTextCacheKey(text, sourceLang, targetLang)
		textCache[cacheKey]?.let {
			log { "text cache hit len=${text.length}" }
			return it
		}
		val translated = translateText(text, sourceLang, targetLang)
		if (translated.isNotBlank()) {
			textCache[cacheKey] = translated
		}
		return translated
	}

	private suspend fun translateText(text: String, sourceLang: String, targetLang: String): String {
		val mode = settings.readerTranslationMode
		val local = if (mode != ReaderTranslationMode.API_ONLY) {
			runCatching {
				translateLocal(text, sourceLang, targetLang)
			}.onFailure {
				it.printStackTraceDebug()
			}.getOrNull()
		} else {
			null
		}
		if (!local.isNullOrBlank()) {
			log { "translate local hit len=${text.length}" }
			return local
		}
		if (mode == ReaderTranslationMode.LOCAL_ONLY) {
			return text
		}
		val api = runCatching {
			translateByApi(text, sourceLang, targetLang)
		}.onFailure {
			it.printStackTraceDebug()
		}.getOrNull()
		if (!api.isNullOrBlank()) {
			log { "translate api hit len=${text.length}" }
		}
		return api?.ifBlank { text } ?: text
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

	private suspend fun translateByApi(text: String, sourceLang: String, targetLang: String): String {
		val endpoint = settings.readerTranslationApiEndpoint.trim()
		if (endpoint.isBlank()) {
			return text
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
				return text
			}
			val body = resp.body?.string().orEmpty()
			if (body.isBlank()) {
				return text
			}
			return parseTranslatedText(body) ?: text
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

	private fun drawTranslatedBubble(
		canvas: Canvas,
		rect: Rect,
		text: String,
		bitmapWidth: Int,
		bitmapHeight: Int,
	) {
		val padding = dp(4f)
		val safeRect = Rect(
			rect.left.coerceIn(0, bitmapWidth - 1),
			rect.top.coerceIn(0, bitmapHeight - 1),
			rect.right.coerceIn(1, bitmapWidth),
			rect.bottom.coerceIn(1, bitmapHeight),
		)
		val width = max(1, safeRect.width() - padding * 2)
		val height = max(1, safeRect.height() - padding * 2)
		val roundRadius = dp(6f).toFloat()
		canvas.drawRoundRect(RectF(safeRect), roundRadius, roundRadius, bubblePaint)
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
		canvas.save()
		canvas.translate((safeRect.left + padding).toFloat(), (safeRect.top + padding).toFloat())
		canvas.clipRect(0, 0, width, height)
		layout.draw(canvas)
		canvas.restore()
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

	private inline fun log(message: () -> String) {
		if (settings.isReaderTranslationDebugLogsEnabled) {
			Log.d(LOG_TAG, message())
		}
	}

	private fun buildRenderedCacheKey(pageUrl: String, sourceUri: String, sourceLang: String, targetLang: String): String {
		val raw = listOf(
			pageUrl,
			sourceUri,
			sourceLang,
			targetLang,
			settings.readerTranslationMode.name,
			settings.readerTranslationApiEndpoint,
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

		val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
		const val TEXT_CACHE_PREFIX = "reader_translate_text_"
		const val RENDER_CACHE_PREFIX = "reader_translate_render_"
		const val OCR_CACHE_PREFIX = "reader_translate_ocr_"
		const val LOG_TAG = "ReaderTranslate"
	}
}
