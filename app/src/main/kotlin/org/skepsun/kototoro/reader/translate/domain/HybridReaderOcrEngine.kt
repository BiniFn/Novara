package org.skepsun.kototoro.reader.translate.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
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
import javax.inject.Inject

/**
 * Hybrid OCR engine: PP-OCRv5 NCNN for text detection (det) + TFLite manga-ocr for recognition (rec).
 *
 * Pipeline:
 *   1. PP-OCRv5 NCNN det-only 鈫?bounding boxes
 *   2. Crop each text region from the source image
 *   3. Keep crop orientation as-is (manga-ocr supports both horizontal and vertical text)
 *   4. Split horizontal multi-line crops into individual lines via horizontal projection
 *   5. Feed each line into FastOcrEngine (manga-ocr) for recognition (GPU semaphore limited)
 *   6. Return OcrTextBlock list with text + boundingBox
 */
@ActivityRetainedScoped
class HybridReaderOcrEngine @Inject constructor(
	private val settings: AppSettings,
	private val ncnnReaderOcrEngine: NcnnReaderOcrEngine,
	private val tfliteModelManager: TfliteModelManager,
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

	override suspend fun recognize(sourceUri: Uri, sourceLang: String): List<OcrTextBlock> {
		log { "hybrid recognize start lang=$sourceLang uri=$sourceUri" }

		// 1. Ensure models are ready
		val tflitePath = resolveTfliteModelPath()
		ensureRecEngineReady(tflitePath)
		if (recEngine == null) {
			log { "hybrid rec unavailable, fallback to ncnn full recognize" }
			return ncnnReaderOcrEngine.recognize(sourceUri, sourceLang)
		}

		// 2. Load the source bitmap
		val decodedBitmap = runInterruptible(Dispatchers.IO) {
			BitmapDecoderCompat.decode(sourceUri.toFile())
		}
		val bitmap = ensureSoftwareBitmap(decodedBitmap)

		return try {
			// 3. Run NCNN det-only to get bounding boxes
			val boxes = ncnnReaderOcrEngine.detectBoxes(sourceUri)
			log { "hybrid ncnn-det done boxes=${boxes.size}" }
			if (boxes.isEmpty()) return emptyList()

			// 4. For each box: crop -> split lines if horizontal -> rec
			val results = coroutineScope {
				boxes.map { box ->
					async {
						recognizeBox(bitmap, box)
					}
				}.awaitAll()
			}

			val filtered = results.filter { it.text.isNotBlank() }
			log { "hybrid rec done blocks=${filtered.size}" }
			filtered
		} finally {
			bitmap.recycle()
			if (bitmap !== decodedBitmap) {
				decodedBitmap.recycle()
			}
		}
	}

	/**
	 * Process a single detected text box:
	 * crop -> dHash cache check -> split lines (horizontal only) -> recognize -> join text
	 */
	private suspend fun recognizeBox(source: Bitmap, box: Rect): OcrTextBlock {
		val crop = cropBitmap(source, box)
		try {
			// Cross-page reuse: check dHash feature cache
			val hash = dHash(crop)
			val cached = findInFeatureCache(hash)
			if (cached != null) {
				log { "feature cache hit hash=$hash text=${cached.take(20)}" }
				return OcrTextBlock(text = cached, boundingBox = box)
			}

			val text = recognizeOrientedText(crop)

			// Store result in feature cache for cross-page reuse
			if (text.isNotBlank()) {
				featureCacheMutex.withLock {
					featureCache.put(hash, text)
				}
			}

			return OcrTextBlock(text = text, boundingBox = box)
		} finally {
			crop.recycle()
		}
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
	}
}

