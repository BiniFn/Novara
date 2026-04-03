package org.skepsun.kototoro.reader.translate.domain

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
class ComicTextDetectorOnnx @Inject constructor(
	private val settings: AppSettings,
	private val onnxModelManager: OnnxModelManager,
) : ReaderTextDetector {

	private data class Runtime(
		val modelId: String,
		val inputName: String,
		val outputNames: Set<String>,
		val session: OrtSession,
	) {
		fun close() {
			runCatching { session.close() }
		}
	}

	private data class LetterboxedBitmap(
		val bitmap: Bitmap,
		val scale: Float,
		val resizedWidth: Int,
		val resizedHeight: Int,
	)

	private data class ScoredRect(
		val left: Float,
		val top: Float,
		val right: Float,
		val bottom: Float,
		val score: Float,
	)

	private data class ScoredRegion(
		val rect: Rect,
		val quad: TextQuad,
		val score: Float,
	)

	private val runtimeLock = Mutex()
	@Volatile
	private var runtime: Runtime? = null

	override suspend fun detect(sourceUri: Uri): List<TextRegion> {
		val bitmap = runInterruptible(Dispatchers.IO) {
			BitmapDecoderCompat.decode(sourceUri.toFile())
		}
		return try {
			detect(bitmap)
		} finally {
			bitmap.recycle()
		}
	}

	override suspend fun detect(bitmap: Bitmap): List<TextRegion> {
		val model = OnnxOfficialModelCatalog.findById(MODEL_ID)
			?.takeIf { it.category == OnnxModelCategory.OCR_DETECTOR }
			?: return emptyList()
		val currentRuntime = ensureRuntime(model.id) ?: return emptyList()
		val prepared = letterboxBitmap(bitmap, INPUT_SIZE)
		var inputTensor: OnnxTensor? = null
		var result: OrtSession.Result? = null
		return try {
			inputTensor = createImageTensor(prepared.bitmap)
			result = currentRuntime.session.run(mapOf(currentRuntime.inputName to inputTensor))
			val lineMapTensor = findLineMapTensor(result, currentRuntime.outputNames)
			val segTensor = findSegmentationTensor(result, currentRuntime.outputNames)
			val blkTensor = findBlockTensor(result, currentRuntime.outputNames)
			val lineRegions = lineMapTensor?.let {
				decodeScoreMapRegions(
					tensor = it,
					channelIndex = 0,
					threshold = LINE_THRESHOLD,
					minComponentPixels = MIN_LINE_COMPONENT_PIXELS,
					scale = prepared.scale,
					sourceWidth = bitmap.width,
					sourceHeight = bitmap.height,
				)
			}.orEmpty()
			val segRegions = segTensor?.let {
				decodeScoreMapRegions(
					tensor = it,
					channelIndex = 0,
					threshold = SEG_THRESHOLD,
					minComponentPixels = MIN_SEG_COMPONENT_PIXELS,
					scale = prepared.scale,
					sourceWidth = bitmap.width,
					sourceHeight = bitmap.height,
				)
			}.orEmpty()
			val blockRegions = blkTensor?.let {
				decodeBlockRegions(
					tensor = it,
					scale = prepared.scale,
					sourceWidth = bitmap.width,
					sourceHeight = bitmap.height,
				)
			}.orEmpty()
			mergeCandidateRegions(
				primaryRegions = lineRegions,
				secondaryRegions = segRegions,
				blockRegions = blockRegions,
			).map { region ->
				TextRegion(
					rect = region.rect,
					confidence = region.score,
					detectorId = MODEL_ID,
					isAxisAligned = isAxisAlignedQuad(region.quad),
					quadPoints = region.quad,
				)
			}
		} finally {
			runCatching { result?.close() }
			runCatching { inputTensor?.close() }
			prepared.bitmap.recycle()
		}
	}

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
			val model = OnnxOfficialModelCatalog.findById(modelId) ?: return@withLock null
			val modelDir = File(onnxModelManager.ensureModelReady(model))
			val modelFile = modelDir.walkTopDown().firstOrNull { file ->
				file.isFile && file.extension.equals("onnx", ignoreCase = true)
			} ?: return@withLock null
			val env = OrtEnvironment.getEnvironment()
			val options = OrtSession.SessionOptions().apply {
				setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
				setIntraOpNumThreads(2)
			}
			val session = env.createSession(modelFile.absolutePath, options)
			val inputName = session.inputNames.firstOrNull() ?: return@withLock null
			Runtime(
				modelId = modelId,
				inputName = inputName,
				outputNames = session.outputNames,
				session = session,
			).also {
				runtime = it
			}
		}
	}

	private fun letterboxBitmap(source: Bitmap, targetSize: Int): LetterboxedBitmap {
		val readable = ensureReadableBitmap(source)
		val scale = min(
			targetSize.toFloat() / readable.width.coerceAtLeast(1).toFloat(),
			targetSize.toFloat() / readable.height.coerceAtLeast(1).toFloat(),
		)
		val resizedWidth = (readable.width * scale).roundToInt().coerceIn(1, targetSize)
		val resizedHeight = (readable.height * scale).roundToInt().coerceIn(1, targetSize)
		val resized = Bitmap.createScaledBitmap(readable, resizedWidth, resizedHeight, true)
		val canvasBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(canvasBitmap)
		canvas.drawColor(Color.BLACK)
		canvas.drawBitmap(resized, 0f, 0f, null)
		if (resized !== readable) {
			resized.recycle()
		}
		if (readable !== source) {
			readable.recycle()
		}
		return LetterboxedBitmap(
			bitmap = canvasBitmap,
			scale = scale,
			resizedWidth = resizedWidth,
			resizedHeight = resizedHeight,
		)
	}

	private fun createImageTensor(bitmap: Bitmap): OnnxTensor {
		val width = bitmap.width
		val height = bitmap.height
		val pixels = IntArray(width * height)
		bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
		val data = FloatArray(3 * width * height)
		val channelStride = width * height
		for (y in 0 until height) {
			for (x in 0 until width) {
				val pixel = pixels[y * width + x]
				val base = y * width + x
				data[base] = ((pixel shr 16) and 0xFF) / 255f
				data[channelStride + base] = ((pixel shr 8) and 0xFF) / 255f
				data[channelStride * 2 + base] = (pixel and 0xFF) / 255f
			}
		}
		return OnnxTensor.createTensor(
			OrtEnvironment.getEnvironment(),
			FloatBuffer.wrap(data),
			longArrayOf(1, 3, height.toLong(), width.toLong()),
		)
	}

	private fun findSegmentationTensor(
		result: OrtSession.Result,
		outputNames: Set<String>,
	): OnnxTensor? {
		for (name in outputNames) {
			val tensor = result.get(name).orElse(null) as? OnnxTensor ?: continue
			val info = tensor.info as? TensorInfo ?: continue
			val shape = info.shape
			if (shape.size == 4 && shape[1] == 1L) {
				return tensor
			}
		}
		return null
	}

	private fun findLineMapTensor(
		result: OrtSession.Result,
		outputNames: Set<String>,
	): OnnxTensor? {
		for (name in outputNames) {
			val tensor = result.get(name).orElse(null) as? OnnxTensor ?: continue
			val info = tensor.info as? TensorInfo ?: continue
			val shape = info.shape
			if (shape.size == 4 && shape[1] == 2L) {
				return tensor
			}
		}
		return null
	}

	private fun findBlockTensor(
		result: OrtSession.Result,
		outputNames: Set<String>,
	): OnnxTensor? {
		for (name in outputNames) {
			val tensor = result.get(name).orElse(null) as? OnnxTensor ?: continue
			val info = tensor.info as? TensorInfo ?: continue
			val shape = info.shape
			if (shape.size == 3 && shape.lastOrNull() == 7L) {
				return tensor
			}
		}
		return null
	}

	private fun decodeScoreMapRegions(
		tensor: OnnxTensor,
		channelIndex: Int,
		threshold: Float,
		minComponentPixels: Int,
		scale: Float,
		sourceWidth: Int,
		sourceHeight: Int,
	): List<ScoredRegion> {
		val info = tensor.info as? TensorInfo ?: return emptyList()
		val shape = info.shape
		if (shape.size != 4) return emptyList()
		val channels = shape[1].toInt()
		if (channelIndex !in 0 until channels) return emptyList()
		val height = shape[2].toInt()
		val width = shape[3].toInt()
		val values = FloatArray(channels * width * height)
		tensor.floatBuffer.get(values)
		val planeOffset = channelIndex * width * height
		val visited = BooleanArray(width * height)
		val queue = IntArray(width * height)
		val regions = ArrayList<ScoredRegion>()
		for (y in 0 until height) {
			for (x in 0 until width) {
				val start = y * width + x
				if (visited[start] || values[planeOffset + start] < threshold) continue
				var head = 0
				var tail = 0
				queue[tail++] = start
				visited[start] = true
				var minX = x
				var minY = y
				var maxX = x
				var maxY = y
				var area = 0
				var scoreSum = 0f
				while (head < tail) {
					val index = queue[head++]
					val cx = index % width
					val cy = index / width
					area += 1
					scoreSum += values[planeOffset + index]
					minX = min(minX, cx)
					minY = min(minY, cy)
					maxX = max(maxX, cx)
					maxY = max(maxY, cy)
					for ((dx, dy) in NEIGHBOR_OFFSETS) {
						val nx = cx + dx
						val ny = cy + dy
						if (nx !in 0 until width || ny !in 0 until height) continue
						val next = ny * width + nx
						if (visited[next] || values[planeOffset + next] < threshold) continue
						visited[next] = true
						queue[tail++] = next
					}
				}
				if (area < minComponentPixels) continue
				val mapped = mapComponentToSource(
					componentPixels = queue,
					componentSize = tail,
					componentWidth = width,
					left = minX.toFloat(),
					top = minY.toFloat(),
					right = (maxX + 1).toFloat(),
					bottom = (maxY + 1).toFloat(),
					scale = scale,
					sourceWidth = sourceWidth,
					sourceHeight = sourceHeight,
					score = scoreSum / area.toFloat(),
				) ?: continue
				if (mapped.rect.width() < MIN_BOX_SIZE || mapped.rect.height() < MIN_BOX_SIZE) continue
				regions += mapped
			}
		}
		return regions
	}

	private fun decodeBlockRegions(
		tensor: OnnxTensor,
		scale: Float,
		sourceWidth: Int,
		sourceHeight: Int,
	): List<ScoredRegion> {
		val info = tensor.info as? TensorInfo ?: return emptyList()
		val shape = info.shape
		if (shape.size != 3) return emptyList()
		val rows = shape[1].toInt()
		val cols = shape[2].toInt()
		if (cols < 7) return emptyList()
		val values = FloatArray(rows * cols)
		tensor.floatBuffer.get(values)
		val candidates = ArrayList<ScoredRegion>()
		for (row in 0 until rows) {
			val offset = row * cols
			val cx = values[offset]
			val cy = values[offset + 1]
			val w = values[offset + 2]
			val h = values[offset + 3]
			val objectness = values[offset + 4]
			val cls0 = values[offset + 5]
			val cls1 = values[offset + 6]
			val confidence = objectness * max(cls0, cls1)
			if (confidence < BLOCK_CONF_THRESHOLD || w <= 1f || h <= 1f) continue
			val mapped = mapRectToSource(
				left = cx - (w * 0.5f),
				top = cy - (h * 0.5f),
				right = cx + (w * 0.5f),
				bottom = cy + (h * 0.5f),
				scale = scale,
				sourceWidth = sourceWidth,
				sourceHeight = sourceHeight,
				score = confidence,
			) ?: continue
			if (mapped.rect.width() < MIN_BOX_SIZE || mapped.rect.height() < MIN_BOX_SIZE) continue
			candidates += mapped
		}
		return nonMaxSuppress(candidates, BLOCK_NMS_THRESHOLD)
	}

	private fun mapRectToSource(
		left: Float,
		top: Float,
		right: Float,
		bottom: Float,
		scale: Float,
		sourceWidth: Int,
		sourceHeight: Int,
		score: Float,
	): ScoredRegion? {
		if (scale <= 0f) return null
		val mappedLeft = (left / scale).coerceIn(0f, sourceWidth.toFloat())
		val mappedTop = (top / scale).coerceIn(0f, sourceHeight.toFloat())
		val mappedRight = (right / scale).coerceIn(0f, sourceWidth.toFloat())
		val mappedBottom = (bottom / scale).coerceIn(0f, sourceHeight.toFloat())
		if (mappedRight <= mappedLeft || mappedBottom <= mappedTop) return null
		val rect = Rect(
			mappedLeft.roundToInt(),
			mappedTop.roundToInt(),
			mappedRight.roundToInt(),
			mappedBottom.roundToInt(),
		)
		return ScoredRegion(
			rect = rect,
			quad = rectToTextQuad(rect),
			score = score.coerceIn(0f, 1f),
		)
	}

	private fun mapComponentToSource(
		componentPixels: IntArray,
		componentSize: Int,
		componentWidth: Int,
		left: Float,
		top: Float,
		right: Float,
		bottom: Float,
		scale: Float,
		sourceWidth: Int,
		sourceHeight: Int,
		score: Float,
	): ScoredRegion? {
		val fallback = mapRectToSource(
			left = left,
			top = top,
			right = right,
			bottom = bottom,
			scale = scale,
			sourceWidth = sourceWidth,
			sourceHeight = sourceHeight,
			score = score,
		) ?: return null
		if (componentSize <= 2) return fallback
		var sumX = 0.0
		var sumY = 0.0
		for (indexPosition in 0 until componentSize) {
			val pixelIndex = componentPixels[indexPosition]
			sumX += (pixelIndex % componentWidth).toDouble() + 0.5
			sumY += (pixelIndex / componentWidth).toDouble() + 0.5
		}
		val meanX = sumX / componentSize.toDouble()
		val meanY = sumY / componentSize.toDouble()
		var covXX = 0.0
		var covXY = 0.0
		var covYY = 0.0
		for (indexPosition in 0 until componentSize) {
			val pixelIndex = componentPixels[indexPosition]
			val x = (pixelIndex % componentWidth).toDouble() + 0.5 - meanX
			val y = (pixelIndex / componentWidth).toDouble() + 0.5 - meanY
			covXX += x * x
			covXY += x * y
			covYY += y * y
		}
		val angle = 0.5 * kotlin.math.atan2(2.0 * covXY, covXX - covYY)
		val axisX = kotlin.math.cos(angle)
		val axisY = kotlin.math.sin(angle)
		val orthoX = -axisY
		val orthoY = axisX
		var minMajor = Double.POSITIVE_INFINITY
		var maxMajor = Double.NEGATIVE_INFINITY
		var minMinor = Double.POSITIVE_INFINITY
		var maxMinor = Double.NEGATIVE_INFINITY
		for (indexPosition in 0 until componentSize) {
			val pixelIndex = componentPixels[indexPosition]
			val x = (pixelIndex % componentWidth).toDouble() + 0.5 - meanX
			val y = (pixelIndex / componentWidth).toDouble() + 0.5 - meanY
			val major = x * axisX + y * axisY
			val minor = x * orthoX + y * orthoY
			minMajor = min(minMajor, major)
			maxMajor = max(maxMajor, major)
			minMinor = min(minMinor, minor)
			maxMinor = max(maxMinor, minor)
		}
		val margin = 0.6
		minMajor -= margin
		maxMajor += margin
		minMinor -= margin
		maxMinor += margin
		val quad = listOf(
			majorMinorToPoint(meanX, meanY, minMajor, minMinor, axisX, axisY, orthoX, orthoY),
			majorMinorToPoint(meanX, meanY, maxMajor, minMinor, axisX, axisY, orthoX, orthoY),
			majorMinorToPoint(meanX, meanY, maxMajor, maxMinor, axisX, axisY, orthoX, orthoY),
			majorMinorToPoint(meanX, meanY, minMajor, maxMinor, axisX, axisY, orthoX, orthoY),
		).map { (x, y) ->
			(x / scale).toFloat().coerceIn(0f, sourceWidth.toFloat()) to
				(y / scale).toFloat().coerceIn(0f, sourceHeight.toFloat())
		}
		val textQuad = TextQuad(points = quad)
		val rect = textQuadToBoundingRect(textQuad)
		if (rect.width() < MIN_BOX_SIZE || rect.height() < MIN_BOX_SIZE) {
			return fallback
		}
		return ScoredRegion(
			rect = rect,
			quad = textQuad,
			score = score.coerceIn(0f, 1f),
		)
	}

	private fun majorMinorToPoint(
		meanX: Double,
		meanY: Double,
		major: Double,
		minor: Double,
		axisX: Double,
		axisY: Double,
		orthoX: Double,
		orthoY: Double,
	): Pair<Double, Double> {
		return (
			meanX + major * axisX + minor * orthoX
			) to (
			meanY + major * axisY + minor * orthoY
			)
	}

	private fun mergeCandidateRegions(
		primaryRegions: List<ScoredRegion>,
		secondaryRegions: List<ScoredRegion>,
		blockRegions: List<ScoredRegion>,
	): List<ScoredRegion> {
		val merged = ArrayList<ScoredRegion>(primaryRegions.size + secondaryRegions.size + blockRegions.size)
		merged += primaryRegions
		for (candidate in secondaryRegions) {
			val duplicate = merged.any { existing ->
				iou(existing, candidate) >= DUPLICATE_IOU_THRESHOLD ||
					contains(existing, candidate) ||
					contains(candidate, existing)
			}
			if (!duplicate) {
				merged += candidate
			}
		}
		for (candidate in blockRegions) {
			val duplicate = merged.any { existing ->
				iou(existing, candidate) >= DUPLICATE_IOU_THRESHOLD ||
					contains(existing, candidate) ||
					contains(candidate, existing)
			}
			if (!duplicate) {
				merged += candidate
			}
		}
		return merged.sortedWith(
			compareBy<ScoredRegion> { it.rect.top / READ_ORDER_ROW_BUCKET }
				.thenBy { it.rect.left }
				.thenByDescending { it.score }
		)
	}

	private fun nonMaxSuppress(
		candidates: List<ScoredRegion>,
		iouThreshold: Float,
	): List<ScoredRegion> {
		if (candidates.isEmpty()) return emptyList()
		val sorted = candidates.sortedByDescending { it.score }.toMutableList()
		val kept = ArrayList<ScoredRegion>(sorted.size)
		while (sorted.isNotEmpty()) {
			val current = sorted.removeAt(0)
			kept += current
			val iterator = sorted.iterator()
			while (iterator.hasNext()) {
				val candidate = iterator.next()
				if (iou(current, candidate) >= iouThreshold) {
					iterator.remove()
				}
			}
		}
		return kept
	}

	private fun iou(a: ScoredRegion, b: ScoredRegion): Float {
		val interLeft = max(a.rect.left, b.rect.left)
		val interTop = max(a.rect.top, b.rect.top)
		val interRight = min(a.rect.right, b.rect.right)
		val interBottom = min(a.rect.bottom, b.rect.bottom)
		if (interRight <= interLeft || interBottom <= interTop) return 0f
		val interArea = (interRight - interLeft).toFloat() * (interBottom - interTop).toFloat()
		val areaA = a.rect.width().toFloat() * a.rect.height().toFloat()
		val areaB = b.rect.width().toFloat() * b.rect.height().toFloat()
		val union = areaA + areaB - interArea
		return if (union <= 0f) 0f else interArea / union
	}

	private fun contains(outer: ScoredRegion, inner: ScoredRegion): Boolean {
		return inner.rect.left >= outer.rect.left &&
			inner.rect.top >= outer.rect.top &&
			inner.rect.right <= outer.rect.right &&
			inner.rect.bottom <= outer.rect.bottom
	}

	private fun ensureReadableBitmap(bitmap: Bitmap): Bitmap {
		if (bitmap.config != Bitmap.Config.HARDWARE) return bitmap
		return bitmap.copy(Bitmap.Config.ARGB_8888, false)
	}

	private inline fun log(message: () -> String) {
		if (settings.isReaderTranslationDebugLogsEnabled) {
			Log.d(LOG_TAG, message())
		}
	}

	companion object {
		const val LOG_TAG = "ReaderOcrCtdOrt"
		const val MODEL_ID = "comic_text_detector_onnx"
		const val INPUT_SIZE = 1024
		const val LINE_THRESHOLD = 0.30f
		const val SEG_THRESHOLD = 0.30f
		const val BLOCK_CONF_THRESHOLD = 0.40f
		const val BLOCK_NMS_THRESHOLD = 0.35f
		const val DUPLICATE_IOU_THRESHOLD = 0.60f
		const val MIN_LINE_COMPONENT_PIXELS = 4
		const val MIN_SEG_COMPONENT_PIXELS = 8
		const val MIN_BOX_SIZE = 6
		const val READ_ORDER_ROW_BUCKET = 48
		val NEIGHBOR_OFFSETS = arrayOf(
			1 to 0,
			-1 to 0,
			0 to 1,
			0 to -1,
		)
	}
}
