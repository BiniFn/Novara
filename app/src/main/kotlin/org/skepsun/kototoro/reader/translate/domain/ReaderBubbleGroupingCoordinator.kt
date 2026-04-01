package org.skepsun.kototoro.reader.translate.domain

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug

internal class ReaderBubbleGroupingCoordinator(
	private val settings: AppSettings,
	private val onnxBubbleDetectorEngine: OnnxBubbleDetectorEngine,
	private val heuristicGroupFragments: (List<TextFragment>, Bitmap) -> List<List<TextFragment>>,
	private val shouldMergeFragments: (TextFragment, TextFragment, Bitmap) -> Boolean,
	private val mergeRects: (List<Rect>) -> Rect?,
	private val rectArea: (Rect) -> Float,
	private val dp: (Float) -> Int,
	private val log: (() -> String) -> Unit,
	private val formatError: (String, Int) -> String,
	private val maxIndividualFallbackFragments: Int,
	private val maxIndividualFallbackRatio: Float,
	private val maxDetectedGroupFragments: Int,
) {

	suspend fun groupFragmentsForTranslation(
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
			heuristicGroupFragments(fallbackFragments, bitmap).map { group ->
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
		if (fallbackFragmentCount >= maxIndividualFallbackFragments) return true
		if (totalFragmentCount <= 0) return false
		return fallbackFragmentCount.toFloat() / totalFragmentCount.toFloat() >= maxIndividualFallbackRatio
	}

	private suspend fun detectBubbleGroups(
		bitmap: Bitmap,
		fragments: List<TextFragment>,
	): BubbleDetectorOutcome {
		if (!settings.isReaderTranslationBubbleDetectorEnabled) {
			return BubbleDetectorOutcome(
				groups = emptyList(),
				matchedFragmentIndices = emptySet(),
				candidateCount = 0,
				matchedFragmentCount = 0,
				subdividedGroupCount = 0,
				subdividedFragmentCount = 0,
				engine = "disabled",
				modelId = "",
				rawBoxCount = 0,
				totalMs = 0L,
				fallbackReason = "detector_disabled",
			)
		}
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
				log { "bubble detector onnx error=${formatError(onnxAttempt.error, 400)}" }
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

		return BubbleDetectorOutcome(
			groups = emptyList(),
			matchedFragmentIndices = emptySet(),
			candidateCount = 0,
			matchedFragmentCount = 0,
			subdividedGroupCount = 0,
			subdividedFragmentCount = 0,
			engine = "onnx",
			modelId = attemptedModelId,
			rawBoxCount = 0,
			totalMs = 0L,
			fallbackReason = fallbackReason,
		)
	}

	private fun groupFragmentsByDetectedRects(
		fragments: List<TextFragment>,
		detectedRects: List<OnnxBubbleDetectorEngine.DetectedBox>,
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
		for (detectedBox in detectedRects) {
			val matched = fragments.indices.filter { index ->
				matchesDetectedBubbleRect(detectedBox.rect, fragments[index].rect)
			}
			if (matched.isEmpty()) continue
			val unionRect = mergeRects(matched.map { fragments[it].rect }) ?: continue
			val candidate = buildDetectedBubbleCandidate(
				detectedBox = detectedBox,
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
							classId = candidate.classId,
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
		detectedBox: OnnxBubbleDetectorEngine.DetectedBox,
		unionRect: Rect,
		fragmentRects: List<Rect>,
		matchedIndices: List<Int>,
		bitmapArea: Float,
		bitmapWidth: Int,
		bitmapHeight: Int,
	): DetectedBubbleCandidate? {
		val candidateArea = rectArea(detectedBox.rect).coerceAtLeast(1f)
		if (candidateArea > bitmapArea * 0.45f) return null
		val touchesEdge = detectedBox.rect.left <= 0 || detectedBox.rect.top <= 0 ||
			detectedBox.rect.right >= bitmapWidth || detectedBox.rect.bottom >= bitmapHeight
		if (touchesEdge && candidateArea > bitmapArea * 0.24f) return null
		val fragmentsArea = matchedIndices.sumOf { rectArea(fragmentRects[it]).toDouble() }.toFloat()
		val unionArea = rectArea(unionRect).coerceAtLeast(1f)
		val inflation = candidateArea / unionArea
		val textCoverage = fragmentsArea / candidateArea
		val matchedCount = matchedIndices.size
		if (matchedCount > maxDetectedGroupFragments) {
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
		val tightenedRect = tightenDetectedBubbleRect(detectedBox.rect, unionRect)
		if (tightenedRect.width() <= dp(8f) || tightenedRect.height() <= dp(8f)) {
			return null
		}
		val score = matchedCount * 4f + textCoverage * 120f - inflation - if (touchesEdge) 2f else 0f
		return DetectedBubbleCandidate(
			rect = tightenedRect,
			fragmentIndices = matchedIndices.sorted(),
			score = score,
			classId = detectedBox.classId,
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
}
