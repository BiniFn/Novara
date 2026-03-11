package org.skepsun.kototoro.reader.translate.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.collection.LruCache
import androidx.core.net.toFile
import com.google.ai.edge.litert.Environment
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.skepsun.kototoro.core.image.BitmapDecoderCompat
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.reader.translate.data.TfliteModelManager
import org.skepsun.kototoro.reader.translate.data.TfliteOfficialModelCatalog
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Hybrid OCR engine: NCNN as the primary OCR backend with TFLite manga-ocr fallback.
 *
 * Pipeline:
 *   1. Run NCNN detect + recognize on the full page
 *   2. Keep high-confidence NCNN text directly
 *   3. Only crop and re-recognize low-confidence regions with manga-ocr
 *   4. Return OcrTextBlock list with text + boundingBox
 */
@ActivityRetainedScoped
class HybridReaderOcrEngine @Inject constructor(
	private val settings: AppSettings,
	private val ncnnReaderOcrEngine: NcnnReaderOcrEngine,
	private val tfliteModelManager: TfliteModelManager,
	private val debugLogStore: ReaderTranslationDebugLogStore,
) : ReaderOcrService {

	// --- TFLite manga-ocr rec ---
	private var recEngine: FastOcrEngine? = null
	private val recMutex = Mutex()
	private var recModelPath: String? = null

	// Limit GPU inference concurrency to prevent queue congestion
	private val recSemaphore = Semaphore(2)

	// Cross-page OCR reuse: dHash 鈫?recognized text
	private val featureCache = LruCache<Long, String>(512)
	private val featureCacheMutex = Mutex()

	override suspend fun recognize(sourceUri: Uri, sourceLang: String, pageId: Long?): List<OcrTextBlock> {
		log { "hybrid recognize start lang=$sourceLang uri=$sourceUri" }
		val totalStartMs = SystemClock.elapsedRealtime()
		val fallbackThreshold = settings.readerTranslationHybridFallbackThreshold
		val ncnnStartMs = SystemClock.elapsedRealtime()
		val ncnnResults = ncnnReaderOcrEngine.recognize(sourceUri, sourceLang, pageId)
		val ncnnDurationMs = SystemClock.elapsedRealtime() - ncnnStartMs
		if (ncnnResults.isEmpty()) {
			appendMetric(pageId, "hybrid.ncnn_ms", ncnnDurationMs)
			appendMetric(pageId, "hybrid.ncnn_blocks", 0)
			appendMetric(pageId, "hybrid.total_ms", SystemClock.elapsedRealtime() - totalStartMs)
			log { "hybrid ncnn returned 0 blocks" }
			return emptyList()
		}
		val fallbackCandidates = ncnnResults.count { shouldFallback(it, fallbackThreshold) }
		appendMetric(pageId, "hybrid.ncnn_ms", ncnnDurationMs)
		appendMetric(pageId, "hybrid.ncnn_blocks", ncnnResults.size)
		appendMetric(pageId, "hybrid.fallback_candidates", fallbackCandidates)
		log {
			"hybrid ncnn done blocks=${ncnnResults.size} fallback=$fallbackCandidates threshold=$fallbackThreshold"
		}
		if (fallbackCandidates == 0) {
			appendMetric(pageId, "hybrid.feature_cache_hits", 0)
			appendMetric(pageId, "hybrid.tflite_fallbacks", 0)
			appendMetric(pageId, "hybrid.tflite_ms", 0)
			appendMetric(pageId, "hybrid.fallback_rate", formatRate(0f))
			appendMetric(pageId, "hybrid.total_ms", SystemClock.elapsedRealtime() - totalStartMs)
			return ncnnResults.filter { it.text.isNotBlank() }
		}

		// Load the source bitmap only after fallback is confirmed.
		val decodedBitmap = runInterruptible(Dispatchers.IO) {
			BitmapDecoderCompat.decode(sourceUri.toFile())
		}
		val bitmap = ensureSoftwareBitmap(decodedBitmap)
		val featureCacheHits = AtomicInteger(0)
		val tfliteFallbacks = AtomicInteger(0)
		val tfliteDurationMs = AtomicInteger(0)

		return try {
			val results = coroutineScope {
				ncnnResults.map { block ->
					async {
						if (shouldFallback(block, fallbackThreshold)) {
							recognizeFallbackBlock(bitmap, block, featureCacheHits, tfliteFallbacks, tfliteDurationMs)
						} else {
							block
						}
					}
				}.awaitAll()
			}

			val filtered = results.filter { it.text.isNotBlank() }
			appendMetric(pageId, "hybrid.feature_cache_hits", featureCacheHits.get())
			appendMetric(pageId, "hybrid.tflite_fallbacks", tfliteFallbacks.get())
			appendMetric(pageId, "hybrid.tflite_ms", tfliteDurationMs.get())
			appendMetric(pageId, "hybrid.fallback_rate", formatRate(tfliteFallbacks.get().toFloat() / ncnnResults.size.toFloat()))
			appendMetric(pageId, "hybrid.total_ms", SystemClock.elapsedRealtime() - totalStartMs)
			log {
				"hybrid rec done blocks=${filtered.size} fallbackCandidates=$fallbackCandidates cacheHits=${featureCacheHits.get()} tfliteFallbacks=${tfliteFallbacks.get()}"
			}
			filtered
		} finally {
			bitmap.recycle()
			if (bitmap !== decodedBitmap) {
				decodedBitmap.recycle()
			}
		}
	}

	/**
	 * Process a single low-confidence text box:
	 * crop -> dHash cache check -> split lines (horizontal only) -> recognize -> join text
	 */
	private suspend fun recognizeFallbackBlock(
		source: Bitmap,
		block: OcrTextBlock,
		featureCacheHits: AtomicInteger,
		tfliteFallbacks: AtomicInteger,
		tfliteDurationMs: AtomicInteger,
	): OcrTextBlock {
		val box = block.boundingBox ?: return block
		val crop = cropBitmap(source, box)
		try {
			// Cross-page reuse: check dHash feature cache
			val hash = dHash(crop)
			val cached = findInFeatureCache(hash)
			if (cached != null) {
				featureCacheHits.incrementAndGet()
				log { "feature cache hit hash=$hash text=${cached.take(20)}" }
				return block.copy(text = cached, confidence = FALLBACK_CONFIDENCE)
			}

			if (!ensureRecEngineAvailable()) {
				log { "hybrid rec unavailable after cache miss, keep ncnn result" }
				return block
			}
			val tfliteStartMs = SystemClock.elapsedRealtime()
			tfliteFallbacks.incrementAndGet()
			val text = recognizeOrientedText(crop)
			tfliteDurationMs.addAndGet((SystemClock.elapsedRealtime() - tfliteStartMs).toInt())
			if (text.isBlank()) {
				return block
			}

			// Store result in feature cache for cross-page reuse
			featureCacheMutex.withLock {
				featureCache.put(hash, text)
			}

			return block.copy(text = text, confidence = FALLBACK_CONFIDENCE)
		} finally {
			crop.recycle()
		}
	}

	private fun shouldFallback(block: OcrTextBlock, threshold: Float): Boolean {
		return block.boundingBox != null && (block.text.isBlank() || block.confidence < threshold)
	}

	private suspend fun recognizeOrientedText(oriented: Bitmap): String {
		// manga-ocr supports vertical text, avoid horizontal projection split on tall crops
		if (oriented.height > oriented.width * 1.2) {
			return recSemaphore.withPermit {
				recEngine?.recognizeText(oriented) ?: ""
			}.trim()
		}

		// Split into text lines via horizontal projection
		val lines = splitTextLines(oriented)
		return if (lines.isEmpty()) {
			// Single line: recognize the whole crop
			recSemaphore.withPermit {
				recEngine?.recognizeText(oriented) ?: ""
			}.trim()
		} else {
			// Multi-line: recognize each line
			lines.map { lineBitmap ->
				try {
					recSemaphore.withPermit {
						recEngine?.recognizeText(lineBitmap) ?: ""
					}
				} finally {
					lineBitmap.recycle()
				}
			}.joinToString("") { it.trim() }
		}
	}


	// ---- TFLite manga-ocr rec ----

	private suspend fun ensureRecEngineReady(modelPath: String) {
		recMutex.withLock {
			if (recModelPath != modelPath || recEngine == null) {
				recEngine?.close()

				val encoderFile = File(modelPath, "encoder.tflite").absolutePath
				val decoderFile = File(modelPath, "decoder.tflite").absolutePath

				val engine = FastOcrEngine(
					encoderModelPath = encoderFile,
					decoderModelPath = decoderFile,
					environment = Environment.create(),
					textPostprocessor = TextPostprocessor(),
				)
				val initialized = runCatching {
					engine.ensureInitialized()
				}.isSuccess
				if (initialized) {
					recEngine = engine
					recModelPath = modelPath
					log { "tflite rec model initialized path=$modelPath" }
				} else {
					engine.close()
					recEngine = null
					recModelPath = null
					log { "tflite rec init failed, will fallback to ncnn rec" }
				}
			}
		}
	}

	private suspend fun ensureRecEngineAvailable(): Boolean {
		if (recEngine != null) return true
		val tflitePath = resolveTfliteModelPath()
		ensureRecEngineReady(tflitePath)
		return recEngine != null
	}

	private fun appendMetric(pageId: Long?, key: String, value: Any) {
		if (pageId != null && pageId > 0L) {
			debugLogStore.metric(pageId, key, value)
		}
	}

	private fun formatRate(value: Float): String {
		return String.format(java.util.Locale.US, "%.3f", value)
	}

	// ---- Image Processing Utilities ----

	private fun cropBitmap(source: Bitmap, box: Rect): Bitmap {
		val left = box.left.coerceIn(0, source.width - 1)
		val top = box.top.coerceIn(0, source.height - 1)
		val right = box.right.coerceIn(left + 1, source.width)
		val bottom = box.bottom.coerceIn(top + 1, source.height)
		return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
	}

	private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
		if (bitmap.config != Bitmap.Config.HARDWARE) {
			return bitmap
		}
		return bitmap.copy(Bitmap.Config.ARGB_8888, false)
	}

	/**
	 * Split a text crop into individual lines using horizontal projection profile.
	 *
	 * Algorithm:
	 *   1. Convert to grayscale, threshold to find "text" pixels
	 *   2. For each row, count text pixels 鈫?projection array
	 *   3. Find gaps (rows with few text pixels) 鈫?split boundaries
	 *   4. Return list of line bitmaps
	 *
	 * Returns empty list if only one line is detected (no splitting needed).
	 */
	private fun splitTextLines(bitmap: Bitmap): List<Bitmap> {
		val w = bitmap.width
		val h = bitmap.height
		if (h < 16 || w < 8) return emptyList() // Too small to split

		// Build horizontal projection: count dark pixels per row
		val projection = IntArray(h)
		val pixels = IntArray(w)
		for (y in 0 until h) {
			bitmap.getPixels(pixels, 0, w, 0, y, w, 1)
			var count = 0
			for (x in 0 until w) {
				val pixel = pixels[x]
				val gray = (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114).toInt()
				if (gray < 160) { // Dark pixel = likely text
					count++
				}
			}
			projection[y] = count
		}

		// Find text line segments: contiguous rows with enough dark pixels
		val threshold = (w * 0.02).toInt().coerceAtLeast(1) // At least 2% of width
		val segments = mutableListOf<Pair<Int, Int>>() // (startY, endY)
		var inSegment = false
		var segStart = 0

		for (y in 0 until h) {
			if (projection[y] >= threshold) {
				if (!inSegment) {
					segStart = y
					inSegment = true
				}
			} else {
				if (inSegment) {
					segments.add(segStart to y)
					inSegment = false
				}
			}
		}
		if (inSegment) {
			segments.add(segStart to h)
		}

		// If only 0 or 1 segment, no splitting needed
		if (segments.size <= 1) return emptyList()

		// Filter out very thin segments (noise)
		val minHeight = 4
		val validSegments = segments.filter { (start, end) -> end - start >= minHeight }
		if (validSegments.size <= 1) return emptyList()

		// Create bitmap per line
		return validSegments.map { (startY, endY) ->
			Bitmap.createBitmap(bitmap, 0, startY, w, endY - startY)
		}
	}

	// ---- Cross-Page Feature Cache ----

	/**
	 * Compute a 64-bit difference hash (dHash) for perceptual similarity matching.
	 * Downscales to 9脳8, computes horizontal gradient 鈫?64 bits.
	 */
	private fun dHash(bitmap: Bitmap): Long {
		val resized = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
		var hash = 0L
		var bit = 1L
		for (y in 0 until 8) {
			for (x in 0 until 8) {
				val left = gray(resized.getPixel(x, y))
				val right = gray(resized.getPixel(x + 1, y))
				if (left > right) {
					hash = hash or bit
				}
				bit = bit shl 1
			}
		}
		resized.recycle()
		return hash
	}

	private fun gray(pixel: Int): Int {
		return (Color.red(pixel) * 77 + Color.green(pixel) * 150 + Color.blue(pixel) * 29) shr 8
	}

	/**
	 * Find a cached OCR result by dHash with Hamming distance tolerance 鈮?5.
	 */
	private suspend fun findInFeatureCache(hash: Long): String? {
		return featureCacheMutex.withLock {
			// Exact match first (fast path)
			featureCache.get(hash)?.let { return@withLock it }
			// Fuzzy match via Hamming distance
			val snapshot = featureCache.snapshot()
			for ((cachedHash, text) in snapshot) {
				if (java.lang.Long.bitCount(hash xor cachedHash) <= HAMMING_THRESHOLD) {
					return@withLock text
				}
			}
			null
		}
	}

	// ---- Path Resolution ----

	private suspend fun resolveTfliteModelPath(): String {
		val customPath = settings.readerTranslationRecModelPath.trim()
		if (customPath.isNotBlank()) return customPath
		val modelId = settings.readerTranslationRecModelId
		val model = TfliteOfficialModelCatalog.findById(modelId) ?: TfliteOfficialModelCatalog.models.first()
		
		return tfliteModelManager.ensureModelReady(
			version = model.version,
			encoderUrl = model.encoderUrl,
			decoderUrl = model.decoderUrl,
			vocabUrl = model.vocabUrl,
			embeddingsUrl = model.embeddingsUrl,
		)
	}

	private inline fun log(message: () -> String) {
		if (settings.isReaderTranslationDebugLogsEnabled) {
			Log.d(LOG_TAG, message())
		}
	}

	private companion object {
		const val LOG_TAG = "ReaderOcrHybrid"
		const val HAMMING_THRESHOLD = 5
		const val FALLBACK_CONFIDENCE = 1f
	}
}

