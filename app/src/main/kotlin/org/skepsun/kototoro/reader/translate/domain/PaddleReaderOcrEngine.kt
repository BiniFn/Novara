package org.skepsun.kototoro.reader.translate.domain

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.skepsun.kototoro.core.image.BitmapDecoderCompat
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.reader.translate.data.OnnxModelCategory
import org.skepsun.kototoro.reader.translate.data.OnnxModelManager
import org.skepsun.kototoro.reader.translate.data.OnnxOfficialModelCatalog
import java.io.File
import java.nio.FloatBuffer
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@ActivityRetainedScoped
class PaddleReaderOcrEngine @Inject constructor(
	private val settings: AppSettings,
	private val onnxModelManager: OnnxModelManager,
) : ReaderOcrService, ReaderTextDetector, ReaderTextRecognizer {

	private data class Runtime(
		val modelId: String,
		val detSession: OrtSession,
		val recSession: OrtSession,
		val recDict: List<String>,
	) {
		fun close() {
			runCatching { detSession.close() }
			runCatching { recSession.close() }
		}
	}

	private data class DetectionResize(
		val width: Int,
		val height: Int,
		val scaleX: Float,
		val scaleY: Float,
	)

	private val runtimeLock = Mutex()
	@Volatile
	private var runtime: Runtime? = null
	private val textDetector = PaddleTextDetector()
	private val textRecognizer = PaddleTextRecognizer()

	override suspend fun recognize(request: OcrRequest): List<OcrTextBlock> {
		val model = resolveActiveModel() ?: run {
			log { "paddle onnx model unavailable" }
			return emptyList()
		}
		val runtime = ensureRuntime(model.id) ?: return emptyList()
		val decodedBitmap = runInterruptible(Dispatchers.IO) {
			BitmapDecoderCompat.decode(request.sourceUri.toFile())
		}
		return try {
			val roi = request.roi
			if (roi != null) {
				recognizeSingleRegion(decodedBitmap, roi, runtime)?.let(::listOf).orEmpty()
			} else {
				recognizeRegions(
					bitmap = decodedBitmap,
					regions = detectTextRegions(decodedBitmap, runtime),
					runtime = runtime,
				)
			}
		} finally {
			decodedBitmap.recycle()
		}
	}

	override suspend fun detect(sourceUri: Uri): List<TextRegion> {
		val decodedBitmap = runInterruptible(Dispatchers.IO) {
			BitmapDecoderCompat.decode(sourceUri.toFile())
		}
		return try {
			detect(decodedBitmap)
		} finally {
			decodedBitmap.recycle()
		}
	}

	override suspend fun detect(bitmap: Bitmap): List<TextRegion> {
		val model = resolveActiveModel() ?: run {
			log { "paddle onnx detector unavailable" }
			return emptyList()
		}
		val runtime = ensureRuntime(model.id) ?: return emptyList()
		return detectTextRegions(bitmap, runtime)
	}

	override suspend fun recognize(sourceUri: Uri, regions: List<TextRegion>): List<OcrTextBlock> {
		val decodedBitmap = runInterruptible(Dispatchers.IO) {
			BitmapDecoderCompat.decode(sourceUri.toFile())
		}
		return try {
			recognize(decodedBitmap, regions)
		} finally {
			decodedBitmap.recycle()
		}
	}

	override suspend fun recognize(bitmap: Bitmap, regions: List<TextRegion>): List<OcrTextBlock> {
		if (regions.isEmpty()) return emptyList()
		val model = resolveActiveModel() ?: run {
			log { "paddle onnx recognizer unavailable" }
			return emptyList()
		}
		val runtime = ensureRuntime(model.id) ?: return emptyList()
		return recognizeRegions(bitmap, regions, runtime)
	}

	private fun resolveActiveModel() = OnnxOfficialModelCatalog.models.firstOrNull {
		it.category == OnnxModelCategory.OCR &&
			(it.id == settings.readerTranslationPaddleOfficialModelId || settings.readerTranslationPaddleOfficialModelId.isBlank())
	} ?: OnnxOfficialModelCatalog.models.firstOrNull { it.category == OnnxModelCategory.OCR }

	private suspend fun ensureRuntime(modelId: String): Runtime? {
		val current = runtime
		if (current != null && current.modelId == modelId) {
			return current
		}
		return runtimeLock.withLock {
			val again = runtime
			if (again != null && again.modelId == modelId) {
				return@withLock again
			}
			runtime?.close()
			runtime = null
			val model = OnnxOfficialModelCatalog.findById(modelId)?.takeIf { it.category == OnnxModelCategory.OCR } ?: return@withLock null
			val modelDir = File(onnxModelManager.ensureModelReady(model))
			val detFile = File(modelDir, "ppocrv5_det.onnx")
			val recFile = File(modelDir, "ppocrv5_rec.onnx")
			val dictFile = File(modelDir, "ppocrv5_dict.txt")
			check(detFile.isFile) { "Missing Paddle OCR det model: ${detFile.absolutePath}" }
			check(recFile.isFile) { "Missing Paddle OCR rec model: ${recFile.absolutePath}" }
			check(dictFile.isFile) { "Missing Paddle OCR dict: ${dictFile.absolutePath}" }
			val env = OrtEnvironment.getEnvironment()
			val options = OrtSession.SessionOptions().apply {
				setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
				setIntraOpNumThreads(2)
			}
			val created = Runtime(
				modelId = modelId,
				detSession = env.createSession(detFile.absolutePath, options),
				recSession = env.createSession(recFile.absolutePath, options),
				recDict = buildRecDictionary(dictFile),
			)
			runtime = created
			created
		}
	}

	private fun buildRecDictionary(dictFile: File): List<String> {
		val entries = dictFile.readLines()
			.map { it.trimEnd('\r') }
			.filter { it.isNotEmpty() }
		return entries + " "
	}

	private fun detectTextRegions(bitmap: Bitmap, runtime: Runtime): List<TextRegion> {
		return textDetector.detectTextRegions(bitmap, runtime).map { rect ->
			TextRegion(
				rect = rect,
				confidence = 1f,
				detectorId = PADDLE_DETECTOR_ID,
				directionHint = inferTextDirectionHint(rect),
				angleHintDegrees = inferTextAngleHintDegrees(rect),
				isAxisAligned = true,
				quadPoints = rectToTextQuad(rect),
			)
		}
	}

	private fun recognizeRegions(
		bitmap: Bitmap,
		regions: List<TextRegion>,
		runtime: Runtime,
	): List<OcrTextBlock> {
		if (regions.isEmpty()) return emptyList()
		return regions.mapNotNull { region ->
			recognizeSingleRegion(bitmap, region, runtime)
		}
	}

	private fun recognizeSingleRegion(bitmap: Bitmap, region: Rect, runtime: Runtime): OcrTextBlock? {
		return recognizeSingleRegion(
			bitmap = bitmap,
			region = TextRegion(
				rect = region,
				directionHint = inferTextDirectionHint(region),
				angleHintDegrees = inferTextAngleHintDegrees(region),
				isAxisAligned = true,
				quadPoints = rectToTextQuad(region),
			),
			runtime = runtime,
		)
	}

	private fun recognizeSingleRegion(bitmap: Bitmap, region: TextRegion, runtime: Runtime): OcrTextBlock? {
		val crop = cropRegionBitmap(bitmap, region)
		val normalized = textRecognizer.normalizeRecognitionOrientation(crop)
		return try {
			val (text, confidence) = textRecognizer.recognizeCrop(normalized, runtime)
			if (text.isBlank()) {
				null
			} else {
				OcrTextBlock(
					text = text,
					boundingBox = region.rect,
					confidence = confidence,
					directionHint = region.directionHint,
					angleHintDegrees = region.angleHintDegrees,
					isAxisAligned = region.isAxisAligned,
					quadPoints = region.quadPoints,
				)
			}
		} finally {
			normalized.recycle()
			if (normalized !== crop) {
				crop.recycle()
			}
		}
	}

	private fun createImageTensor(
		bitmap: Bitmap,
		height: Int,
		width: Int,
		normalizeToSigned: Boolean,
	): OnnxTensor {
		val readableBitmap = ensureReadableBitmap(bitmap)
		val pixels = IntArray(width * height)
		try {
			readableBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
			val data = FloatArray(3 * width * height)
			val channelStride = width * height
			for (y in 0 until height) {
				for (x in 0 until width) {
					val pixel = pixels[y * width + x]
					val r = ((pixel shr 16) and 0xFF) / 255f
					val g = ((pixel shr 8) and 0xFF) / 255f
					val b = (pixel and 0xFF) / 255f
					val base = y * width + x
					if (normalizeToSigned) {
						data[base] = (r - 0.5f) / 0.5f
						data[channelStride + base] = (g - 0.5f) / 0.5f
						data[channelStride * 2 + base] = (b - 0.5f) / 0.5f
					} else {
						data[base] = r
						data[channelStride + base] = g
						data[channelStride * 2 + base] = b
					}
				}
			}
			return OnnxTensor.createTensor(
				OrtEnvironment.getEnvironment(),
				FloatBuffer.wrap(data),
				longArrayOf(1, 3, height.toLong(), width.toLong()),
			)
		} finally {
			if (readableBitmap !== bitmap) {
				readableBitmap.recycle()
			}
		}
	}

	private fun decodeRecognitionTensor(
		tensor: OnnxTensor,
		dictionary: List<String>,
	): Pair<String, Float> {
		val shape = tensor.info.shape
		if (shape.size != 3) return "" to 0f
		val timeSteps = shape[1].toInt()
		val classes = shape[2].toInt()
		val values = FloatArray(timeSteps * classes)
		tensor.floatBuffer.get(values)
		val text = StringBuilder()
		var previousIndex = -1
		var confidenceSum = 0f
		var confidenceCount = 0
		for (t in 0 until timeSteps) {
			var bestIndex = 0
			var bestValue = Float.NEGATIVE_INFINITY
			val offset = t * classes
			for (i in 0 until classes) {
				val v = values[offset + i]
				if (v > bestValue) {
					bestValue = v
					bestIndex = i
				}
			}
			if (bestIndex == 0 || bestIndex == previousIndex) {
				previousIndex = bestIndex
				continue
			}
			val charIndex = bestIndex - 1
			if (charIndex in dictionary.indices) {
				text.append(dictionary[charIndex])
				confidenceSum += bestValue
				confidenceCount++
			}
			previousIndex = bestIndex
		}
		return text.toString().trim() to if (confidenceCount > 0) confidenceSum / confidenceCount else 0f
	}

	private fun cropBitmap(source: Bitmap, box: Rect): Bitmap {
		val left = box.left.coerceIn(0, source.width - 1)
		val top = box.top.coerceIn(0, source.height - 1)
		val right = box.right.coerceIn(left + 1, source.width)
		val bottom = box.bottom.coerceIn(top + 1, source.height)
		return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
	}

	private fun ensureReadableBitmap(bitmap: Bitmap): Bitmap {
		if (bitmap.config != Bitmap.Config.HARDWARE) {
			return bitmap
		}
		return bitmap.copy(Bitmap.Config.ARGB_8888, false)
	}

	private fun cropRegionBitmap(source: Bitmap, region: TextRegion): Bitmap {
		val quad = region.quadPoints
		val quadRect = textQuadToBoundingRect(quad)
		if (!region.isAxisAligned || !isAxisAlignedQuad(quad)) {
			warpRegionBitmap(source, quad)?.let { return it }
		}
		val cropRect = if (region.isAxisAligned && isAxisAlignedQuad(quad)) {
			Rect(
				max(region.rect.left, quadRect.left),
				max(region.rect.top, quadRect.top),
				min(region.rect.right, quadRect.right),
				min(region.rect.bottom, quadRect.bottom),
			)
		} else {
			quadRect
		}
		return cropBitmap(source, cropRect)
	}

	private fun warpRegionBitmap(source: Bitmap, quad: TextQuad): Bitmap? {
		val src = quadToFloatArray(quad)
		val targetWidth = estimateQuadWidth(quad).roundToInt().coerceAtLeast(1)
		val targetHeight = estimateQuadHeight(quad).roundToInt().coerceAtLeast(1)
		if (targetWidth <= 1 || targetHeight <= 1) return null
		val dst = floatArrayOf(
			0f, 0f,
			targetWidth.toFloat(), 0f,
			targetWidth.toFloat(), targetHeight.toFloat(),
			0f, targetHeight.toFloat(),
		)
		val matrix = Matrix()
		if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
			return null
		}
		return runCatching {
			Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { output ->
				val canvas = Canvas(output)
				canvas.drawBitmap(source, matrix, TRANSFORM_PAINT)
			}
		}.getOrNull()
	}

	private fun quadToFloatArray(quad: TextQuad): FloatArray {
		return floatArrayOf(
			quad.points[0].first, quad.points[0].second,
			quad.points[1].first, quad.points[1].second,
			quad.points[2].first, quad.points[2].second,
			quad.points[3].first, quad.points[3].second,
		)
	}

	private fun estimateQuadWidth(quad: TextQuad): Float {
		val top = distance(quad.points[0], quad.points[1])
		val bottom = distance(quad.points[3], quad.points[2])
		return ((top + bottom) * 0.5f).coerceAtLeast(1f)
	}

	private fun estimateQuadHeight(quad: TextQuad): Float {
		val left = distance(quad.points[0], quad.points[3])
		val right = distance(quad.points[1], quad.points[2])
		return ((left + right) * 0.5f).coerceAtLeast(1f)
	}

	private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
		val dx = a.first - b.first
		val dy = a.second - b.second
		return kotlin.math.sqrt(dx * dx + dy * dy)
	}

	private inline fun log(message: () -> String) {
		if (settings.isReaderTranslationDebugLogsEnabled) {
			Log.d(LOG_TAG, message())
		}
	}

	private companion object {
		const val LOG_TAG = "ReaderOcrPaddleOrt"
		const val PADDLE_DETECTOR_ID = "paddle_onnx_ppocrv5_det"
		const val DET_MAX_SIDE = 960
		const val DET_BIN_THRESHOLD = 0.30f
		const val DET_MIN_COMPONENT_PIXELS = 8
		const val DET_MIN_BOX_SIZE = 6
		const val REC_INPUT_HEIGHT = 48
		const val REC_MIN_WIDTH = 32
		const val REC_MAX_WIDTH = 512
		val TRANSFORM_PAINT = Paint(Paint.FILTER_BITMAP_FLAG)
		val NEIGHBOR_OFFSETS = arrayOf(
			1 to 0,
			-1 to 0,
			0 to 1,
			0 to -1,
		)
	}

	private inner class PaddleTextDetector {

		fun detectTextRegions(bitmap: Bitmap, runtime: Runtime): List<Rect> {
			val resize = computeDetectionResize(bitmap.width, bitmap.height)
			val scaled = Bitmap.createScaledBitmap(bitmap, resize.width, resize.height, true)
			var inputTensor: OnnxTensor? = null
			var result: OrtSession.Result? = null
			return try {
				inputTensor = createImageTensor(
					bitmap = scaled,
					height = resize.height,
					width = resize.width,
					normalizeToSigned = false,
				)
				val inputName = runtime.detSession.inputNames.first()
				result = runtime.detSession.run(mapOf(inputName to inputTensor))
				val outputName = runtime.detSession.outputNames.first()
				val tensor = result.get(outputName).orElse(null) as? OnnxTensor ?: return emptyList()
				decodeDetectionMap(
					tensor = tensor,
					sourceWidth = bitmap.width,
					sourceHeight = bitmap.height,
					scaleX = resize.scaleX,
					scaleY = resize.scaleY,
				)
			} finally {
				runCatching { result?.close() }
				runCatching { inputTensor?.close() }
				scaled.recycle()
			}
		}

		private fun computeDetectionResize(width: Int, height: Int): DetectionResize {
			val maxSide = max(width, height).coerceAtLeast(1)
			val scale = min(1f, DET_MAX_SIDE.toFloat() / maxSide.toFloat())
			val scaledW = max(32, ((width * scale).roundToInt() / 32).coerceAtLeast(1) * 32)
			val scaledH = max(32, ((height * scale).roundToInt() / 32).coerceAtLeast(1) * 32)
			return DetectionResize(
				width = scaledW,
				height = scaledH,
				scaleX = scaledW.toFloat() / width.toFloat().coerceAtLeast(1f),
				scaleY = scaledH.toFloat() / height.toFloat().coerceAtLeast(1f),
			)
		}

		private fun decodeDetectionMap(
			tensor: OnnxTensor,
			sourceWidth: Int,
			sourceHeight: Int,
			scaleX: Float,
			scaleY: Float,
		): List<Rect> {
			val shape = tensor.info.shape
			if (shape.size != 4) return emptyList()
			val height = shape[2].toInt()
			val width = shape[3].toInt()
			val values = FloatArray(height * width)
			tensor.floatBuffer.get(values)
			val visited = BooleanArray(values.size)
			val regions = mutableListOf<Rect>()
			val queue = IntArray(values.size)
			for (y in 0 until height) {
				for (x in 0 until width) {
					val idx = y * width + x
					if (visited[idx] || values[idx] < DET_BIN_THRESHOLD) continue
					var head = 0
					var tail = 0
					queue[tail++] = idx
					visited[idx] = true
					var minX = x
					var minY = y
					var maxX = x
					var maxY = y
					var count = 0
					while (head < tail) {
						val current = queue[head++]
						val cy = current / width
						val cx = current % width
						count++
						if (cx < minX) minX = cx
						if (cy < minY) minY = cy
						if (cx > maxX) maxX = cx
						if (cy > maxY) maxY = cy
						for (offset in NEIGHBOR_OFFSETS) {
							val nx = cx + offset.first
							val ny = cy + offset.second
							if (nx !in 0 until width || ny !in 0 until height) continue
							val nIdx = ny * width + nx
							if (visited[nIdx] || values[nIdx] < DET_BIN_THRESHOLD) continue
							visited[nIdx] = true
							queue[tail++] = nIdx
						}
					}
					if (count < DET_MIN_COMPONENT_PIXELS) continue
					val rect = Rect(
						(minX / scaleX).roundToInt().coerceIn(0, sourceWidth - 1),
						(minY / scaleY).roundToInt().coerceIn(0, sourceHeight - 1),
						((maxX + 1) / scaleX).roundToInt().coerceIn(1, sourceWidth),
						((maxY + 1) / scaleY).roundToInt().coerceIn(1, sourceHeight),
					)
					if (rect.width() < DET_MIN_BOX_SIZE || rect.height() < DET_MIN_BOX_SIZE) continue
					regions += rect
				}
			}
			return regions.sortedWith(compareBy<Rect> { it.top }.thenBy { it.left })
		}
	}

	private inner class PaddleTextRecognizer {

		fun normalizeRecognitionOrientation(bitmap: Bitmap): Bitmap {
			if (bitmap.height <= bitmap.width * 13 / 10) {
				return bitmap
			}
			val matrix = Matrix().apply { postRotate(90f) }
			return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
		}

		fun recognizeCrop(bitmap: Bitmap, runtime: Runtime): Pair<String, Float> {
			val targetHeight = REC_INPUT_HEIGHT
			val targetWidth = (bitmap.width * (targetHeight.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)))
				.roundToInt()
				.coerceIn(REC_MIN_WIDTH, REC_MAX_WIDTH)
			val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
			var inputTensor: OnnxTensor? = null
			var result: OrtSession.Result? = null
			return try {
				inputTensor = createImageTensor(
					bitmap = scaled,
					height = targetHeight,
					width = targetWidth,
					normalizeToSigned = true,
				)
				val inputName = runtime.recSession.inputNames.first()
				result = runtime.recSession.run(mapOf(inputName to inputTensor))
				val outputName = runtime.recSession.outputNames.first()
				val tensor = result.get(outputName).orElse(null) as? OnnxTensor ?: return "" to 0f
				decodeRecognitionTensor(tensor, runtime.recDict)
			} finally {
				runCatching { result?.close() }
				runCatching { inputTensor?.close() }
				scaled.recycle()
			}
		}
	}
}
