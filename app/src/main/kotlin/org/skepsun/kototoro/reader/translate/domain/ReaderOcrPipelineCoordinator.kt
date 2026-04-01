package org.skepsun.kototoro.reader.translate.domain

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri

internal class ReaderOcrPipelineCoordinator(
	private val loadPageText: suspend (Uri, String, Long) -> PageOcrLoadResult,
	private val detectBubbleRects: suspend (Bitmap) -> OnnxBubbleDetectorEngine.DetectionAttempt,
	private val groupFragmentsForTranslation: suspend (List<TextFragment>, Bitmap) -> BubbleGroupingResult,
	private val recognizeBubbleTextsByRoi: suspend (List<GroupedBubbleSource>, Uri, String, Long, Bitmap) -> BubbleRoiOcrResult,
) {

	suspend fun execute(
		sourceUri: Uri,
		sourceLang: String,
		pageId: Long,
		bitmap: Bitmap,
		strategy: OcrPipelineStrategy = OcrPipelineStrategy.PAGE_FIRST,
	): OcrPipelineResult {
		return when (strategy) {
			OcrPipelineStrategy.PAGE_FIRST -> executePageFirst(
				sourceUri = sourceUri,
				sourceLang = sourceLang,
				pageId = pageId,
				bitmap = bitmap,
			)
			OcrPipelineStrategy.ROI_FIRST_FALLBACK -> executeRoiFirstFallback(
				sourceUri = sourceUri,
				sourceLang = sourceLang,
				pageId = pageId,
				bitmap = bitmap,
			)
		}
	}

	private suspend fun executePageFirst(
		sourceUri: Uri,
		sourceLang: String,
		pageId: Long,
		bitmap: Bitmap,
	): OcrPipelineResult {
		val pageOcr = loadPageText(sourceUri, sourceLang, pageId)
		if (pageOcr.textBlocks.isEmpty()) {
			return OcrPipelineResult(
				pageTextBlocks = emptyList(),
				pageOcr = pageOcr,
				groupingResult = null,
				roiResult = BubbleRoiOcrResult(emptyMap(), 0, 0, 0, 0L, 0f),
				strategy = OcrPipelineStrategy.PAGE_FIRST,
				fallbackReason = "",
				roiFirstDetectedBoxCount = 0,
			)
		}
		val drawableBlocks = pageOcr.textBlocks.filter { it.boundingBox != null && it.text.trim().isNotBlank() }
		val sourceFragments = drawableBlocks.map {
			TextFragment(
				rect = it.boundingBox!!,
				text = it.text.trim(),
			)
		}
		val groupingResult = groupFragmentsForTranslation(sourceFragments, bitmap)
		val roiResult = recognizeBubbleTextsByRoi(
			groupingResult.groups,
			sourceUri,
			sourceLang,
			pageId,
			bitmap,
		)
		return OcrPipelineResult(
			pageTextBlocks = pageOcr.textBlocks,
			pageOcr = pageOcr,
			groupingResult = groupingResult,
			roiResult = roiResult,
			strategy = OcrPipelineStrategy.PAGE_FIRST,
			fallbackReason = "",
			roiFirstDetectedBoxCount = 0,
		)
	}

	private suspend fun executeRoiFirstFallback(
		sourceUri: Uri,
		sourceLang: String,
		pageId: Long,
		bitmap: Bitmap,
	): OcrPipelineResult {
		val detectionAttempt = detectBubbleRects(bitmap)
		val detectedRects = detectionAttempt.result?.boxes.orEmpty()
		if (detectionAttempt.status != OnnxBubbleDetectorEngine.AttemptStatus.SUCCESS || detectedRects.isEmpty()) {
			return executePageFirst(sourceUri, sourceLang, pageId, bitmap).copy(
				fallbackReason = buildDetectionFallbackReason(detectionAttempt, detectedRects),
				roiFirstDetectedBoxCount = detectedRects.size,
			)
		}
		val groupingResult = buildRoiFirstGroupingResult(detectionAttempt, detectedRects)
		val roiResult = recognizeBubbleTextsByRoi(
			groupingResult.groups,
			sourceUri,
			sourceLang,
			pageId,
			bitmap,
		)
		if (shouldFallbackToPageFirst(groupingResult, roiResult)) {
			return executePageFirst(sourceUri, sourceLang, pageId, bitmap).copy(
				fallbackReason = buildCoverageFallbackReason(roiResult),
				roiFirstDetectedBoxCount = detectedRects.size,
			)
		}
		return OcrPipelineResult(
			pageTextBlocks = emptyList(),
			pageOcr = null,
			groupingResult = groupingResult,
			roiResult = roiResult,
			strategy = OcrPipelineStrategy.ROI_FIRST_FALLBACK,
			fallbackReason = "",
			roiFirstDetectedBoxCount = detectedRects.size,
		)
	}

	private fun buildRoiFirstGroupingResult(
		detectionAttempt: OnnxBubbleDetectorEngine.DetectionAttempt,
		detectedRects: List<OnnxBubbleDetectorEngine.DetectedBox>,
	): BubbleGroupingResult {
		val detectionResult = detectionAttempt.result
		val groups = detectedRects.map { box ->
			GroupedBubbleSource(
				fragments = emptyList(),
				bubbleRect = box.rect,
				classId = box.classId,
			)
		}
		return BubbleGroupingResult(
			groups = groups,
			detectorCandidateCount = groups.size,
			detectorMatchedFragmentCount = 0,
			detectorUsedGroupCount = groups.size,
			detectorSubdividedGroupCount = 0,
			detectorSubdividedFragmentCount = 0,
			detectorCoverageRate = if (groups.isNotEmpty()) 1f else 0f,
			detectorEngine = buildString {
				append("onnx_")
				append(detectionResult?.backend?.lowercase().orEmpty().ifBlank { "unknown" })
			},
			detectorModelId = detectionResult?.modelId.orEmpty(),
			detectorRawBoxCount = detectionResult?.rawBoxCount ?: detectedRects.size,
			detectorTotalMs = detectionResult?.totalMs ?: 0L,
			detectorFallbackReason = "",
			fallbackFragmentCount = 0,
			fallbackGroupCount = 0,
			fallbackMode = "none",
		)
	}

	private fun shouldFallbackToPageFirst(
		groupingResult: BubbleGroupingResult,
		roiResult: BubbleRoiOcrResult,
	): Boolean {
		if (groupingResult.groups.isEmpty()) return true
		if (roiResult.requestCount == 0 || roiResult.successCount == 0) return true
		if (roiResult.coverageArea < MIN_ROI_SUCCESS_COVERAGE) return true
		return roiResult.successCount.toFloat() / roiResult.requestCount.toFloat() < MIN_ROI_SUCCESS_RATIO
	}

	private fun buildDetectionFallbackReason(
		detectionAttempt: OnnxBubbleDetectorEngine.DetectionAttempt,
		detectedRects: List<OnnxBubbleDetectorEngine.DetectedBox>,
	): String {
		return when {
			detectionAttempt.status == OnnxBubbleDetectorEngine.AttemptStatus.NO_MODEL_DOWNLOADED -> "roi_first_no_model"
			detectionAttempt.status == OnnxBubbleDetectorEngine.AttemptStatus.RUNTIME_UNAVAILABLE -> "roi_first_runtime_unavailable"
			detectionAttempt.status == OnnxBubbleDetectorEngine.AttemptStatus.NO_BOXES -> "roi_first_no_boxes"
			detectionAttempt.status == OnnxBubbleDetectorEngine.AttemptStatus.SUCCESS && detectedRects.isEmpty() -> "roi_first_no_boxes"
			else -> "roi_first_detector_failed"
		}
	}

	private fun buildCoverageFallbackReason(roiResult: BubbleRoiOcrResult): String {
		return when {
			roiResult.requestCount == 0 -> "roi_first_no_roi_requests"
			roiResult.successCount == 0 -> "roi_first_no_roi_hits"
			roiResult.coverageArea < MIN_ROI_SUCCESS_COVERAGE -> "roi_first_low_coverage"
			roiResult.successCount.toFloat() / roiResult.requestCount.toFloat() < MIN_ROI_SUCCESS_RATIO -> "roi_first_low_success_ratio"
			else -> "roi_first_unknown"
		}
	}

	private companion object {
		const val MIN_ROI_SUCCESS_COVERAGE = 0.45f
		const val MIN_ROI_SUCCESS_RATIO = 0.5f
	}
}

internal enum class OcrPipelineStrategy {
	PAGE_FIRST,
	ROI_FIRST_FALLBACK,
}

internal data class OcrPipelineResult(
	val pageTextBlocks: List<OcrTextBlock>,
	val pageOcr: PageOcrLoadResult?,
	val groupingResult: BubbleGroupingResult?,
	val roiResult: BubbleRoiOcrResult,
	val strategy: OcrPipelineStrategy,
	val fallbackReason: String,
	val roiFirstDetectedBoxCount: Int,
)

internal data class PageOcrLoadResult(
	val textBlocks: List<OcrTextBlock>,
	val cacheHit: Boolean,
	val durationMs: Long,
)
