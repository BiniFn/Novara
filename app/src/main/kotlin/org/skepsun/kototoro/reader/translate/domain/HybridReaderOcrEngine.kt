package org.skepsun.kototoro.reader.translate.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import androidx.collection.LruCache
import androidx.core.net.toFile
import com.equationl.paddleocr4android.OCR
import com.equationl.paddleocr4android.OcrConfig
import com.google.ai.edge.litert.Environment
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.skepsun.kototoro.core.LocalizedAppContext
import org.skepsun.kototoro.core.image.BitmapDecoderCompat
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.reader.translate.data.PaddleModelManager
import org.skepsun.kototoro.reader.translate.data.TfliteModelManager
import org.skepsun.kototoro.reader.translate.data.TfliteOfficialModelCatalog
import java.io.File
import javax.inject.Inject

/**
 * Hybrid OCR engine: PaddleOCR for text detection (det) + TFLite manga-ocr for recognition (rec).
 *
 * Pipeline:
 *   1. PaddleOCR det-only → bounding boxes
 *   2. Crop each text region from the source image
 *   3. Auto-rotate vertical text (height > width * 1.2) → 90°
 *   4. Split multi-line crops into individual lines via horizontal projection
 *   5. Feed each line into FastOcrEngine (manga-ocr) for recognition (GPU semaphore limited)
 *   6. Return OcrTextBlock list with text + boundingBox
 */
@ActivityRetainedScoped
class HybridReaderOcrEngine @Inject constructor(
	@LocalizedAppContext private val context: Context,
	private val settings: AppSettings,
	private val paddleModelManager: PaddleModelManager,
	private val tfliteModelManager: TfliteModelManager,
) : ReaderOcrService {

	// --- PaddleOCR det-only ---
	private val paddleOcr = OCR(context)
	private val paddleMutex = Mutex()
	private var paddleModelPath: String? = null

	// --- TFLite manga-ocr rec ---
	private var recEngine: FastOcrEngine? = null
	private val recMutex = Mutex()
	private var recModelPath: String? = null

	// Limit GPU inference concurrency to prevent queue congestion
	private val recSemaphore = Semaphore(2)

	// Cross-page OCR reuse: dHash → recognized text
	private val featureCache = LruCache<Long, String>(512)
	private val featureCacheMutex = Mutex()

	override suspend fun recognize(sourceUri: Uri, sourceLang: String): List<OcrTextBlock> {
		log { "hybrid recognize start lang=$sourceLang uri=$sourceUri" }

		// 1. Ensure both models are ready
		val paddlePath = resolvePaddleModelPath()
		val tflitePath = resolveTfliteModelPath()

		ensurePaddleDetReady(paddlePath)
		ensureRecEngineReady(tflitePath)

		// 2. Load the source bitmap
		val bitmap = runInterruptible(Dispatchers.IO) {
			BitmapDecoderCompat.decode(sourceUri.toFile())
		}

		return try {
			// 3. Run PaddleOCR det-only to get bounding boxes
			val boxes = runDetection(bitmap)
			log { "hybrid det done boxes=${boxes.size}" }
			if (boxes.isEmpty()) return emptyList()

			// 4. For each box: crop → rotate if vertical → split lines → rec
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
		}
	}

	/**
	 * Process a single detected text box:
	 * crop → rotate vertical → dHash cache check → split lines → recognize → join text
	 */
	private suspend fun recognizeBox(source: Bitmap, box: Rect): OcrTextBlock {
		val crop = cropBitmap(source, box)
		try {
			// Auto-rotate vertical text (manga is ~70% vertical)
			val isVertical = crop.height > crop.width * 1.2
			val oriented = if (isVertical) rotateBitmap(crop, 90f) else crop

			try {
				// Cross-page reuse: check dHash feature cache
				val hash = dHash(oriented)
				val cached = findInFeatureCache(hash)
				if (cached != null) {
					log { "feature cache hit hash=$hash text=${cached.take(20)}" }
					return OcrTextBlock(text = cached, boundingBox = box)
				}

				// Split into text lines via horizontal projection
				val lines = splitTextLines(oriented)
				val text = if (lines.isEmpty()) {
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

				// Store result in feature cache for cross-page reuse
				if (text.isNotBlank()) {
					featureCacheMutex.withLock {
						featureCache.put(hash, text)
					}
				}

				return OcrTextBlock(text = text, boundingBox = box)
			} finally {
				if (oriented !== crop) oriented.recycle()
			}
		} finally {
			crop.recycle()
		}
	}

	// ---- PaddleOCR det-only ----

	private suspend fun ensurePaddleDetReady(modelPath: String) {
		paddleMutex.withLock {
			if (paddleModelPath != modelPath) {
				val config = OcrConfig(
					modelPath = modelPath,
					detModelFilename = "det.nb",
					recModelFilename = "rec.nb",
					clsModelFilename = "cls.nb",
					isRunDet = true,
					isRunCls = false,
					isRunRec = false,
					isDrwwTextPositionBox = false,
				)
				val result = paddleOcr.initModelSync(config).getOrElse { error ->
					throw IllegalStateException("PaddleOCR det init failed", error)
				}
				check(result) { "PaddleOCR det init failed" }
				paddleModelPath = modelPath
				log { "paddle det model initialized path=$modelPath" }
			}
		}
	}

	private suspend fun runDetection(bitmap: Bitmap): List<Rect> {
		val result = paddleOcr.runSync(bitmap).getOrThrow()
		return result.outputRawResult.mapNotNull { model ->
			val points = model.points
			if (points.isNullOrEmpty()) return@mapNotNull null
			val minX = points.minOf { it.x }
			val minY = points.minOf { it.y }
			val maxX = points.maxOf { it.x }
			val maxY = points.maxOf { it.y }
			if (maxX <= minX || maxY <= minY) return@mapNotNull null
			Rect(minX, minY, maxX, maxY)
		}
	}

	// ---- TFLite manga-ocr rec ----

	private suspend fun ensureRecEngineReady(modelPath: String) {
		recMutex.withLock {
			if (recModelPath != modelPath || recEngine == null) {
				recEngine?.close()

				val encoderFile = File(modelPath, "encoder.tflite").absolutePath
				val decoderFile = File(modelPath, "decoder.tflite").absolutePath

				recEngine = FastOcrEngine(
					encoderModelPath = encoderFile,
					decoderModelPath = decoderFile,
					environment = Environment.create(),
					textPostprocessor = TextPostprocessor(),
				)
				recModelPath = modelPath
				log { "tflite rec model initialized path=$modelPath" }
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

	/**
	 * Rotate a bitmap by the given degrees.
	 */
	private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
		val matrix = Matrix().apply { postRotate(degrees) }
		return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
	}

	/**
	 * Split a text crop into individual lines using horizontal projection profile.
	 *
	 * Algorithm:
	 *   1. Convert to grayscale, threshold to find "text" pixels
	 *   2. For each row, count text pixels → projection array
	 *   3. Find gaps (rows with few text pixels) → split boundaries
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
	 * Downscales to 9×8, computes horizontal gradient → 64 bits.
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
	 * Find a cached OCR result by dHash with Hamming distance tolerance ≤ 5.
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

	private suspend fun resolvePaddleModelPath(): String {
		val customPath = settings.readerTranslationPaddleModelPath.trim()
		if (customPath.isNotBlank()) return customPath
		return paddleModelManager.ensureModelReady(
			version = settings.readerTranslationPaddleModelVersion.trim(),
			zipUrl = settings.readerTranslationPaddleModelUrl.trim(),
			zipSha256 = settings.readerTranslationPaddleModelSha256.trim(),
		)
	}

	private suspend fun resolveTfliteModelPath(): String {
		val customPath = settings.readerTranslationTfliteModelPath.trim()
		if (customPath.isNotBlank()) return customPath
		val modelId = settings.readerTranslationTfliteModelId
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
