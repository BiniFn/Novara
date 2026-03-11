package org.skepsun.kototoro.reader.translate.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
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
class OnnxBubbleDetectorEngine @Inject constructor(
	private val settings: AppSettings,
	private val onnxModelManager: OnnxModelManager,
) {

	enum class AttemptStatus {
		NO_MODEL_DOWNLOADED,
		RUNTIME_UNAVAILABLE,
		NO_BOXES,
		SUCCESS,
	}

	data class DetectionResult(
		val boxes: List<Rect>,
		val modelId: String,
		val backend: String,
		val parser: String,
		val rawBoxCount: Int,
		val decodedBoxCount: Int,
		val finalBoxCount: Int,
		val totalMs: Long,
	)

	data class DetectionAttempt(
		val status: AttemptStatus,
		val result: DetectionResult? = null,
		val modelId: String = "",
		val backend: String = "",
		val parser: String = "",
		val stage: String = "",
		val inputName: String = "",
		val inputShape: String = "",
		val outputNames: String = "",
		val error: String = "",
	)

	private data class RuntimeAttempt(
		val runtime: Runtime?,
		val backend: String = "",
		val parser: String = "",
		val stage: String = "",
		val inputName: String = "",
		val inputShape: String = "",
		val outputNames: String = "",
		val error: String = "",
	)

	private data class Runtime(
		val modelId: String,
		val modelFileName: String,
		val backend: String,
		val parser: ParserKind,
		val inputName: String,
		val inputWidth: Int?,
		val inputHeight: Int?,
		val inputShape: String,
		val outputName: String,
		val session: OrtSession,
	) {
		fun close() {
			runCatching { session.close() }
		}
	}

	private data class LetterboxBitmap(
		val bitmap: Bitmap,
		val inputWidth: Int,
		val inputHeight: Int,
		val scale: Float,
		val padX: Float,
		val padY: Float,
	)

	private data class ScoredBox(
		val rect: Rect,
		val score: Float,
	)

	private data class DecodedDetections(
		val boxes: List<Rect>,
		val parser: String,
		val rawBoxCount: Int,
		val decodedBoxCount: Int,
	)

	private enum class ParserKind(val wireName: String) {
		GENERIC_YOLO("generic_yolo"),
		YOLO26_E2E("yolo26_e2e"),
	}

	private val runtimeLock = Mutex()
	@Volatile
	private var runtime: Runtime? = null

	suspend fun detect(bitmap: Bitmap): DetectionResult? {
		return detectAttempt(bitmap).result
	}

	suspend fun detectAttempt(bitmap: Bitmap): DetectionAttempt {
		val model = resolveActiveModel() ?: return DetectionAttempt(
			status = AttemptStatus.NO_MODEL_DOWNLOADED,
			stage = "resolve_model",
		)
		val runtimeAttempt = ensureRuntime(model.id)
		val runtime = runtimeAttempt.runtime ?: return DetectionAttempt(
			status = AttemptStatus.RUNTIME_UNAVAILABLE,
			modelId = model.id,
			backend = runtimeAttempt.backend,
			parser = runtimeAttempt.parser,
			stage = runtimeAttempt.stage,
			inputName = runtimeAttempt.inputName,
			inputShape = runtimeAttempt.inputShape,
			outputNames = runtimeAttempt.outputNames,
			error = runtimeAttempt.error,
		)
		val startMs = System.currentTimeMillis()
		return runCatching {
			runInterruptible(Dispatchers.Default) {
				val decoded = detectInternal(bitmap, runtime)
				val result = DetectionResult(
					boxes = decoded.boxes,
					modelId = runtime.modelId,
					backend = runtime.backend,
					parser = decoded.parser,
					rawBoxCount = decoded.rawBoxCount,
					decodedBoxCount = decoded.decodedBoxCount,
					finalBoxCount = decoded.boxes.size,
					totalMs = System.currentTimeMillis() - startMs,
				)
				if (result.boxes.isEmpty()) {
					DetectionAttempt(
						status = AttemptStatus.NO_BOXES,
						result = result,
						modelId = result.modelId,
						backend = result.backend,
						parser = result.parser,
						stage = "decode_output",
						inputName = runtime.inputName,
						inputShape = runtime.inputShape,
						outputNames = runtime.outputName,
					)
				} else {
					DetectionAttempt(
						status = AttemptStatus.SUCCESS,
						result = result,
						modelId = result.modelId,
						backend = result.backend,
						parser = result.parser,
						stage = "success",
						inputName = runtime.inputName,
						inputShape = runtime.inputShape,
						outputNames = runtime.outputName,
					)
				}
			}
		}.onFailure {
			it.printStackTraceDebug()
			log { "onnx bubble detect failed model=${runtime.modelId} err=${it.message.orEmpty()}" }
		}.getOrElse {
			DetectionAttempt(
				status = AttemptStatus.RUNTIME_UNAVAILABLE,
				modelId = runtime.modelId,
				backend = runtime.backend,
				parser = runtime.parser.wireName,
				stage = "detect_internal",
				inputName = runtime.inputName,
				inputShape = runtime.inputShape,
				outputNames = runtime.outputName,
				error = it.message.orEmpty(),
			)
		}
	}

	private fun resolveActiveModel() =
		OnnxOfficialModelCatalog.models.firstOrNull { model ->
			model.category == OnnxModelCategory.BUBBLE_DETECTION &&
				onnxModelManager.isModelDownloaded(model.id)
		}

	private suspend fun ensureRuntime(modelId: String): RuntimeAttempt {
		val current = runtime
		if (current != null && current.modelId == modelId) {
			return RuntimeAttempt(
				runtime = current,
				backend = current.backend,
				parser = current.parser.wireName,
				stage = "cache_hit",
				inputName = current.inputName,
				inputShape = current.inputShape,
				outputNames = current.outputName,
			)
		}
		return runtimeLock.withLock {
			val again = runtime
			if (again != null && again.modelId == modelId) {
				return@withLock RuntimeAttempt(
					runtime = again,
					backend = again.backend,
					parser = again.parser.wireName,
					stage = "cache_hit",
					inputName = again.inputName,
					inputShape = again.inputShape,
					outputNames = again.outputName,
				)
			}
			runtime?.close()
			runtime = null
			val modelDir = onnxModelManager.getModelDir(modelId)
			val attempt = createRuntimeWithFallback(modelId, modelDir)
			runtime = attempt.runtime
			attempt
		}
	}

	private fun createRuntimeWithFallback(modelId: String, modelDir: File): RuntimeAttempt {
		val modelPath = modelDir.walkTopDown().firstOrNull { file ->
			file.isFile && file.extension.equals("onnx", ignoreCase = true)
		} ?: return RuntimeAttempt(
			runtime = null,
			parser = resolveParserKind(modelId, "").wireName,
			stage = "model_file_missing",
			error = modelDir.absolutePath,
		)
		val parserKind = resolveParserKind(modelId, modelPath.name)
		var lastFailure = RuntimeAttempt(
			runtime = null,
			parser = parserKind.wireName,
			stage = "session_not_attempted",
			error = modelPath.absolutePath,
		)
		for (useGpu in listOf(true, false)) {
			val backend = if (useGpu) "NNAPI" else "CPU"
			val options = createSessionOptions(useGpu)
			try {
				val env = OrtEnvironment.getEnvironment()
				val session = env.createSession(modelPath.absolutePath, options)
				val inputEntry = session.inputInfo.entries.firstOrNull()
				if (inputEntry == null) {
					lastFailure = RuntimeAttempt(
						runtime = null,
						backend = backend,
						parser = parserKind.wireName,
						stage = "missing_input",
						outputNames = session.outputNames.joinToString(","),
						error = modelPath.name,
					)
					session.close()
					continue
				}
				val outputName = session.outputNames.firstOrNull()
				if (outputName == null) {
					lastFailure = RuntimeAttempt(
						runtime = null,
						backend = backend,
						parser = parserKind.wireName,
						stage = "missing_output",
						inputName = inputEntry.key,
						inputShape = (inputEntry.value.info as? TensorInfo)?.shape?.joinToString("x").orEmpty(),
						error = modelPath.name,
					)
					session.close()
					continue
				}
				val inputInfo = inputEntry.value.info as? TensorInfo
				if (inputInfo == null) {
					lastFailure = RuntimeAttempt(
						runtime = null,
						backend = backend,
						parser = parserKind.wireName,
						stage = "invalid_input_info",
						inputName = inputEntry.key,
						outputNames = session.outputNames.joinToString(","),
						error = inputEntry.value.info.toString(),
					)
					session.close()
					continue
				}
				val shape = inputInfo.shape
				val shapeText = shape.joinToString("x")
				if (shape.size < 4) {
					lastFailure = RuntimeAttempt(
						runtime = null,
						backend = backend,
						parser = parserKind.wireName,
						stage = "invalid_input_shape_rank",
						inputName = inputEntry.key,
						inputShape = shapeText,
						outputNames = session.outputNames.joinToString(","),
						error = modelPath.name,
					)
					session.close()
					continue
				}
				val inputHeight = resolveDim(shape[shape.size - 2])
				val inputWidth = resolveDim(shape[shape.size - 1])
				val dynamicSpatialInput = inputHeight == null || inputWidth == null
				log {
					"onnx bubble runtime ready model=$modelId file=${modelPath.name} backend=$backend " +
						"parser=${parserKind.wireName} inputShape=$shapeText " +
						if (dynamicSpatialInput) "dynamicSpatial=true" else "input=${inputWidth}x$inputHeight"
				}
				return RuntimeAttempt(
					runtime = Runtime(
						modelId = modelId,
						modelFileName = modelPath.name,
						backend = backend,
						parser = parserKind,
						inputName = inputEntry.key,
						inputWidth = inputWidth,
						inputHeight = inputHeight,
						inputShape = shapeText,
						outputName = outputName,
						session = session,
					),
					backend = backend,
					parser = parserKind.wireName,
					stage = if (dynamicSpatialInput) "ready_dynamic_input" else "ready",
					inputName = inputEntry.key,
					inputShape = shapeText,
					outputNames = session.outputNames.joinToString(","),
				)
			} catch (e: Throwable) {
				e.printStackTraceDebug()
				lastFailure = RuntimeAttempt(
					runtime = null,
					backend = backend,
					parser = parserKind.wireName,
					stage = "session_create_failed",
					error = e.message.orEmpty(),
				)
				log { "onnx bubble runtime init failed model=$modelId backend=$backend err=${e.message.orEmpty()}" }
			} finally {
				runCatching { options.close() }
			}
		}
		return lastFailure
	}

	private fun createSessionOptions(useGpu: Boolean): OrtSession.SessionOptions {
		val options = OrtSession.SessionOptions()
		options.setMemoryPatternOptimization(false)
		options.setCPUArenaAllocator(false)
		options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
		if (useGpu) {
			runCatching { options.addNnapi() }
		}
		return options
	}

	private fun detectInternal(source: Bitmap, runtime: Runtime): DecodedDetections {
		val letterboxed = createLetterboxBitmap(
			source = source,
			targetWidth = runtime.inputWidth,
			targetHeight = runtime.inputHeight,
		)
		val inputTensor = createInputTensor(letterboxed.bitmap, letterboxed.inputWidth, letterboxed.inputHeight)
		var sessionResult: OrtSession.Result? = null
		try {
			sessionResult = runtime.session.run(mapOf(runtime.inputName to inputTensor))
			val outputTensor = sessionResult.get(runtime.outputName).orElse(null) as? OnnxTensor
				?: return DecodedDetections(
					boxes = emptyList(),
					parser = runtime.parser.wireName,
					rawBoxCount = 0,
					decodedBoxCount = 0,
				)
			return decodeOutputTensor(
				parser = runtime.parser,
				tensor = outputTensor,
				sourceWidth = source.width,
				sourceHeight = source.height,
				inputWidth = letterboxed.inputWidth,
				inputHeight = letterboxed.inputHeight,
				scale = letterboxed.scale,
				padX = letterboxed.padX,
				padY = letterboxed.padY,
			)
		} finally {
			runCatching { inputTensor.close() }
			runCatching { sessionResult?.close() }
			if (letterboxed.bitmap !== source) {
				letterboxed.bitmap.recycle()
			}
		}
	}

	private fun createLetterboxBitmap(source: Bitmap, targetWidth: Int?, targetHeight: Int?): LetterboxBitmap {
		val resolvedWidth = targetWidth ?: chooseDynamicInputSize(source.width, source.height)
		val resolvedHeight = targetHeight ?: chooseDynamicInputSize(source.height, source.width)
		val scale = min(
			resolvedWidth / source.width.toFloat(),
			resolvedHeight / source.height.toFloat(),
		)
		val scaledWidth = max(1, (source.width * scale).roundToInt())
		val scaledHeight = max(1, (source.height * scale).roundToInt())
		val padX = ((resolvedWidth - scaledWidth) / 2f).coerceAtLeast(0f)
		val padY = ((resolvedHeight - scaledHeight) / 2f).coerceAtLeast(0f)
		val output = Bitmap.createBitmap(resolvedWidth, resolvedHeight, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(output)
		canvas.drawColor(0xFFFFFFFF.toInt())
		val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
		canvas.drawBitmap(
			source,
			null,
			android.graphics.Rect(
				padX.roundToInt(),
				padY.roundToInt(),
				(padX + scaledWidth).roundToInt(),
				(padY + scaledHeight).roundToInt(),
			),
			paint,
		)
		return LetterboxBitmap(
			bitmap = output,
			inputWidth = resolvedWidth,
			inputHeight = resolvedHeight,
			scale = scale,
			padX = padX,
			padY = padY,
		)
	}

	private fun chooseDynamicInputSize(primary: Int, secondary: Int): Int {
		val longest = max(primary, secondary).coerceAtLeast(MIN_DYNAMIC_INPUT_SIZE)
		val capped = longest.coerceAtMost(MAX_DYNAMIC_INPUT_SIZE)
		val aligned = ((capped + MODEL_STRIDE - 1) / MODEL_STRIDE) * MODEL_STRIDE
		return aligned.coerceIn(MIN_DYNAMIC_INPUT_SIZE, MAX_DYNAMIC_INPUT_SIZE)
	}

	private fun createInputTensor(bitmap: Bitmap, width: Int, height: Int): OnnxTensor {
		val pixels = IntArray(width * height)
		bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
		val channels = FloatArray(width * height * 3)
		val planeSize = width * height
		for (index in pixels.indices) {
			val pixel = pixels[index]
			channels[index] = ((pixel shr 16) and 0xFF) / 255f
			channels[planeSize + index] = ((pixel shr 8) and 0xFF) / 255f
			channels[planeSize * 2 + index] = (pixel and 0xFF) / 255f
		}
		return OnnxTensor.createTensor(
			OrtEnvironment.getEnvironment(),
			FloatBuffer.wrap(channels),
			longArrayOf(1L, 3L, height.toLong(), width.toLong()),
		)
	}

	private fun decodeOutputTensor(
		parser: ParserKind,
		tensor: OnnxTensor,
		sourceWidth: Int,
		sourceHeight: Int,
		inputWidth: Int,
		inputHeight: Int,
		scale: Float,
		padX: Float,
		padY: Float,
	): DecodedDetections {
		val info = tensor.info as? TensorInfo ?: return DecodedDetections(
			boxes = emptyList(),
			parser = parser.wireName,
			rawBoxCount = 0,
			decodedBoxCount = 0,
		)
		val shape = info.shape
		val flat = ArrayList<Float>(estimateElementCount(shape))
		flattenNumericTensor(tensor.value, flat)
		if (flat.isEmpty()) {
			return DecodedDetections(
				boxes = emptyList(),
				parser = parser.wireName,
				rawBoxCount = 0,
				decodedBoxCount = 0,
			)
		}
		val layout = resolveOutputLayout(shape) ?: return DecodedDetections(
			boxes = emptyList(),
			parser = parser.wireName,
			rawBoxCount = 0,
			decodedBoxCount = 0,
		)
		val scored = when (parser) {
			ParserKind.YOLO26_E2E -> decodeYolo26Boxes(
				layout = layout,
				flat = flat,
				sourceWidth = sourceWidth,
				sourceHeight = sourceHeight,
				inputWidth = inputWidth,
				inputHeight = inputHeight,
				scale = scale,
				padX = padX,
				padY = padY,
			)
			ParserKind.GENERIC_YOLO -> decodeGenericYoloBoxes(
				layout = layout,
				flat = flat,
				sourceWidth = sourceWidth,
				sourceHeight = sourceHeight,
				inputWidth = inputWidth,
				inputHeight = inputHeight,
				scale = scale,
				padX = padX,
				padY = padY,
			)
		}
		val finalBoxes = when (parser) {
			ParserKind.YOLO26_E2E -> scored.sortedByDescending { it.score }.take(MAX_OUTPUT_BOXES)
			ParserKind.GENERIC_YOLO -> applyNms(scored)
		}
			return DecodedDetections(
				boxes = finalBoxes.map { it.rect },
				parser = parser.wireName,
				rawBoxCount = layout.count,
				decodedBoxCount = scored.size,
			)
		}

	private fun decodeGenericYoloBoxes(
		layout: OutputLayout,
		flat: List<Float>,
		sourceWidth: Int,
		sourceHeight: Int,
		inputWidth: Int,
		inputHeight: Int,
		scale: Float,
		padX: Float,
		padY: Float,
	): List<ScoredBox> {
		val scored = ArrayList<ScoredBox>(layout.count)
		for (index in 0 until layout.count) {
			val cxRaw = layout.read(flat, index, 0)
			val cyRaw = layout.read(flat, index, 1)
			val wRaw = layout.read(flat, index, 2)
			val hRaw = layout.read(flat, index, 3)
			val score = layout.readConfidence(flat, index)
			if (score < MIN_SCORE_THRESHOLD) continue
			val normalized = max(maxOf(cxRaw, cyRaw), maxOf(wRaw, hRaw)) <= 2.5f
			val cx = if (normalized) cxRaw * inputWidth else cxRaw
			val cy = if (normalized) cyRaw * inputHeight else cyRaw
			val width = if (normalized) wRaw * inputWidth else wRaw
			val height = if (normalized) hRaw * inputHeight else hRaw
			if (width <= 1f || height <= 1f) continue
			val left = ((cx - width / 2f) - padX) / scale
			val top = ((cy - height / 2f) - padY) / scale
			val right = ((cx + width / 2f) - padX) / scale
			val bottom = ((cy + height / 2f) - padY) / scale
			val rect = Rect(
				left.roundToInt().coerceIn(0, sourceWidth - 1),
				top.roundToInt().coerceIn(0, sourceHeight - 1),
				right.roundToInt().coerceIn(1, sourceWidth),
				bottom.roundToInt().coerceIn(1, sourceHeight),
			)
			if (rect.width() < MIN_BOX_SIDE || rect.height() < MIN_BOX_SIDE) continue
			val areaRatio = rect.width().toFloat() * rect.height().toFloat() / (sourceWidth * sourceHeight).toFloat().coerceAtLeast(1f)
			if (areaRatio !in MIN_AREA_RATIO..MAX_AREA_RATIO) continue
			scored += ScoredBox(rect = rect, score = score)
		}
		return scored
	}

	private fun decodeYolo26Boxes(
		layout: OutputLayout,
		flat: List<Float>,
		sourceWidth: Int,
		sourceHeight: Int,
		inputWidth: Int,
		inputHeight: Int,
		scale: Float,
		padX: Float,
		padY: Float,
	): List<ScoredBox> {
		val scored = ArrayList<ScoredBox>(layout.count)
		for (index in 0 until layout.count) {
			val x1Raw = layout.read(flat, index, 0)
			val y1Raw = layout.read(flat, index, 1)
			val x2Raw = layout.read(flat, index, 2)
			val y2Raw = layout.read(flat, index, 3)
			val score = layout.read(flat, index, 4)
			if (score < MIN_SCORE_THRESHOLD) continue
			val normalized = max(maxOf(x1Raw, y1Raw), maxOf(x2Raw, y2Raw)) <= 2.5f
			val x1 = if (normalized) x1Raw * inputWidth else x1Raw
			val y1 = if (normalized) y1Raw * inputHeight else y1Raw
			val x2 = if (normalized) x2Raw * inputWidth else x2Raw
			val y2 = if (normalized) y2Raw * inputHeight else y2Raw
			val left = ((min(x1, x2)) - padX) / scale
			val top = ((min(y1, y2)) - padY) / scale
			val right = ((max(x1, x2)) - padX) / scale
			val bottom = ((max(y1, y2)) - padY) / scale
			val rect = Rect(
				left.roundToInt().coerceIn(0, sourceWidth - 1),
				top.roundToInt().coerceIn(0, sourceHeight - 1),
				right.roundToInt().coerceIn(1, sourceWidth),
				bottom.roundToInt().coerceIn(1, sourceHeight),
			)
			if (rect.width() < MIN_BOX_SIDE || rect.height() < MIN_BOX_SIDE) continue
			val areaRatio = rect.width().toFloat() * rect.height().toFloat() / (sourceWidth * sourceHeight).toFloat().coerceAtLeast(1f)
			if (areaRatio !in MIN_AREA_RATIO..MAX_AREA_RATIO) continue
			scored += ScoredBox(rect = rect, score = score)
		}
		return scored
	}

	private fun applyNms(boxes: List<ScoredBox>): List<ScoredBox> {
		if (boxes.isEmpty()) return emptyList()
		val sorted = boxes.sortedByDescending { it.score }.toMutableList()
		val selected = mutableListOf<ScoredBox>()
		while (sorted.isNotEmpty() && selected.size < MAX_OUTPUT_BOXES) {
			val head = sorted.removeAt(0)
			selected += head
			sorted.removeAll { candidate ->
				computeIoU(head.rect, candidate.rect) >= NMS_IOU_THRESHOLD
			}
		}
		return selected
	}

	private fun computeIoU(a: Rect, b: Rect): Float {
		val left = max(a.left, b.left)
		val top = max(a.top, b.top)
		val right = min(a.right, b.right)
		val bottom = min(a.bottom, b.bottom)
		val width = (right - left).coerceAtLeast(0)
		val height = (bottom - top).coerceAtLeast(0)
		val intersection = width * height
		if (intersection <= 0) return 0f
		val union = a.width() * a.height() + b.width() * b.height() - intersection
		if (union <= 0) return 0f
		return intersection.toFloat() / union.toFloat()
	}

	private fun flattenNumericTensor(value: Any?, out: MutableList<Float>) {
		when (value) {
			null -> Unit
			is Number -> out += value.toFloat()
			is FloatArray -> value.forEach { out += it }
			is DoubleArray -> value.forEach { out += it.toFloat() }
			is IntArray -> value.forEach { out += it.toFloat() }
			is LongArray -> value.forEach { out += it.toFloat() }
			is Array<*> -> value.forEach { flattenNumericTensor(it, out) }
		}
	}

	private fun estimateElementCount(shape: LongArray): Int {
		var total = 1L
		for (dim in shape) {
			if (dim <= 0L) continue
			total *= dim
			if (total >= 1_000_000L) break
		}
		return total.coerceAtMost(1_000_000L).toInt()
	}

	private fun resolveDim(value: Long): Int? {
		if (value <= 0L || value > 4096L) return null
		return value.toInt()
	}

	private fun resolveOutputLayout(shape: LongArray): OutputLayout? {
		if (shape.size == 3 && shape[0] == 1L) {
			val d1 = shape[1].toInt()
			val d2 = shape[2].toInt()
			if (d1 > 4 && d2 > 0) {
				return OutputLayout(count = d2, attributes = d1, transposed = true)
			}
			if (d2 > 4 && d1 > 0) {
				return OutputLayout(count = d1, attributes = d2, transposed = false)
			}
		}
		if (shape.size == 2) {
			val d0 = shape[0].toInt()
			val d1 = shape[1].toInt()
			if (d1 > 4 && d0 > 0) {
				return OutputLayout(count = d0, attributes = d1, transposed = false)
			}
			if (d0 > 4 && d1 > 0) {
				return OutputLayout(count = d1, attributes = d0, transposed = true)
			}
		}
		return null
	}

	private fun resolveParserKind(modelId: String, modelFileName: String): ParserKind {
		val fingerprint = "$modelId $modelFileName".lowercase()
		return if ("yolo26" in fingerprint) ParserKind.YOLO26_E2E else ParserKind.GENERIC_YOLO
	}

	private data class OutputLayout(
		val count: Int,
		val attributes: Int,
		val transposed: Boolean,
	) {
		fun read(flat: List<Float>, index: Int, attribute: Int): Float {
			val flatIndex = if (transposed) {
				attribute * count + index
			} else {
				index * attributes + attribute
			}
			return flat.getOrElse(flatIndex) { 0f }
		}

		fun readConfidence(flat: List<Float>, index: Int): Float {
			if (attributes <= 4) return 0f
			if (attributes == 5) return read(flat, index, 4)
			var best = 0f
			for (attribute in 4 until attributes) {
				best = max(best, read(flat, index, attribute))
			}
			return best
		}
	}

	private inline fun log(message: () -> String) {
		if (settings.isReaderTranslationDebugLogsEnabled) {
			android.util.Log.d(LOG_TAG, message())
		}
	}

	private companion object {
		const val LOG_TAG = "ReaderTranslate"
		const val MODEL_STRIDE = 32
		const val MIN_DYNAMIC_INPUT_SIZE = 640
		const val MAX_DYNAMIC_INPUT_SIZE = 1280
		const val MIN_BOX_SIDE = 24
		const val MAX_OUTPUT_BOXES = 24
		const val MIN_SCORE_THRESHOLD = 0.20f
		const val MIN_AREA_RATIO = 0.0008f
		const val MAX_AREA_RATIO = 0.45f
		const val NMS_IOU_THRESHOLD = 0.45f
	}
}
