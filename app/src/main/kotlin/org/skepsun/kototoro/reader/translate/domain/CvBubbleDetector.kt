package org.skepsun.kototoro.reader.translate.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@ActivityRetainedScoped
class CvBubbleDetector @Inject constructor() {

	data class BubbleGroup(
		val rect: Rect,
		val fragmentIndices: List<Int>,
	)

	data class Result(
		val groups: List<BubbleGroup>,
		val candidateCount: Int,
		val matchedFragmentCount: Int,
	)

	fun detect(bitmap: Bitmap, fragmentRects: List<Rect>): Result {
		if (fragmentRects.isEmpty() || bitmap.width <= 1 || bitmap.height <= 1) {
			return Result(
				groups = emptyList(),
				candidateCount = 0,
				matchedFragmentCount = 0,
			)
		}
		val cellSize = chooseCellSize(bitmap)
		val cols = ((bitmap.width + cellSize - 1) / cellSize).coerceAtLeast(1)
		val rows = ((bitmap.height + cellSize - 1) / cellSize).coerceAtLeast(1)
		val brightMask = BooleanArray(cols * rows)
		for (row in 0 until rows) {
			for (col in 0 until cols) {
				brightMask[row * cols + col] = isBrightCell(bitmap, col, row, cellSize)
			}
		}

		val rawComponents = extractComponents(
			brightMask = brightMask,
			cols = cols,
			rows = rows,
			cellSize = cellSize,
			bitmapWidth = bitmap.width,
			bitmapHeight = bitmap.height,
		)
		if (rawComponents.isEmpty()) {
			return Result(
				groups = emptyList(),
				candidateCount = 0,
				matchedFragmentCount = 0,
			)
		}

		val bitmapArea = (bitmap.width * bitmap.height).toFloat().coerceAtLeast(1f)
		val uniqueCandidates = linkedMapOf<String, RankedCandidate>()
		for (component in rawComponents) {
			val matched = fragmentRects.indices.filter { index ->
				matchesCandidate(component.rect, fragmentRects[index], cellSize)
			}
			if (matched.isEmpty()) continue
			val unionRect = mergeRects(matched.map { fragmentRects[it] }) ?: continue
			val candidate = buildCandidate(
				component = component,
				unionRect = unionRect,
				fragmentRects = fragmentRects,
				matchedIndices = matched,
				cellSize = cellSize,
				bitmapArea = bitmapArea,
			) ?: continue
			val key = candidate.fragmentIndices.joinToString(",")
			val existing = uniqueCandidates[key]
			if (existing == null || candidate.isBetterThan(existing)) {
				uniqueCandidates[key] = candidate
			}
		}

		if (uniqueCandidates.isEmpty()) {
			return Result(
				groups = emptyList(),
				candidateCount = 0,
				matchedFragmentCount = 0,
			)
		}

		val claimed = linkedSetOf<Int>()
		val groups = uniqueCandidates.values
			.sortedWith(
				compareByDescending<RankedCandidate> { it.fragmentIndices.size }
					.thenByDescending { it.score }
					.thenBy { rectArea(it.rect) }
			)
			.mapNotNull { candidate ->
				val available = candidate.fragmentIndices.filterNot { it in claimed }
				if (available.isEmpty()) {
					return@mapNotNull null
				}
				val unionRect = mergeRects(available.map { fragmentRects[it] }) ?: return@mapNotNull null
				val tightenedRect = tightenCandidateRect(candidate.rect, unionRect, cellSize)
				claimed += available
				BubbleGroup(
					rect = tightenedRect,
					fragmentIndices = available.sorted(),
				)
			}

		return Result(
			groups = groups,
			candidateCount = uniqueCandidates.size,
			matchedFragmentCount = claimed.size,
		)
	}

	private fun chooseCellSize(bitmap: Bitmap): Int {
		val shortestEdge = min(bitmap.width, bitmap.height)
		return (shortestEdge / 180).coerceIn(6, 10)
	}

	private fun isBrightCell(bitmap: Bitmap, col: Int, row: Int, cellSize: Int): Boolean {
		val left = col * cellSize
		val top = row * cellSize
		val right = min(bitmap.width, left + cellSize)
		val bottom = min(bitmap.height, top + cellSize)
		if (left >= right || top >= bottom) return false
		val points = arrayOf(
			intArrayOf((left + right) / 2, (top + bottom) / 2),
			intArrayOf((left * 3 + right) / 4, (top + bottom) / 2),
			intArrayOf((left + right * 3) / 4, (top + bottom) / 2),
			intArrayOf((left + right) / 2, (top * 3 + bottom) / 4),
			intArrayOf((left + right) / 2, (top + bottom * 3) / 4),
		)
		var brightSamples = 0
		for (point in points) {
			val pixel = bitmap.getPixel(
				point[0].coerceIn(0, bitmap.width - 1),
				point[1].coerceIn(0, bitmap.height - 1),
			)
			if (isLikelyWhite(pixel)) {
				brightSamples++
			}
		}
		return brightSamples >= 3
	}

	private fun isLikelyWhite(pixel: Int): Boolean {
		val red = Color.red(pixel)
		val green = Color.green(pixel)
		val blue = Color.blue(pixel)
		val lum = (red * 299 + green * 587 + blue * 114) / 1000
		val chroma = max(red, max(green, blue)) - min(red, min(green, blue))
		return lum >= 214 && (chroma <= 40 || lum >= 238)
	}

	private fun extractComponents(
		brightMask: BooleanArray,
		cols: Int,
		rows: Int,
		cellSize: Int,
		bitmapWidth: Int,
		bitmapHeight: Int,
	): List<Component> {
		val visited = BooleanArray(brightMask.size)
		val queue = IntArray(brightMask.size)
		val result = mutableListOf<Component>()
		for (index in brightMask.indices) {
			if (!brightMask[index] || visited[index]) continue
			var head = 0
			var tail = 0
			queue[tail++] = index
			visited[index] = true
			var count = 0
			var minCol = cols
			var minRow = rows
			var maxCol = -1
			var maxRow = -1
			var touchesEdge = false
			while (head < tail) {
				val current = queue[head++]
				val row = current / cols
				val col = current % cols
				count++
				minCol = min(minCol, col)
				minRow = min(minRow, row)
				maxCol = max(maxCol, col)
				maxRow = max(maxRow, row)
				if (col == 0 || row == 0 || col == cols - 1 || row == rows - 1) {
					touchesEdge = true
				}
				for (dy in -1..1) {
					for (dx in -1..1) {
						if (dx == 0 && dy == 0) continue
						val nextCol = col + dx
						val nextRow = row + dy
						if (nextCol !in 0 until cols || nextRow !in 0 until rows) continue
						val nextIndex = nextRow * cols + nextCol
						if (!brightMask[nextIndex] || visited[nextIndex]) continue
						visited[nextIndex] = true
						queue[tail++] = nextIndex
					}
				}
			}
			if (count < 6 || maxCol < minCol || maxRow < minRow) continue
			val rect = Rect(
				(minCol * cellSize).coerceIn(0, bitmapWidth - 1),
				(minRow * cellSize).coerceIn(0, bitmapHeight - 1),
				((maxCol + 1) * cellSize).coerceIn(1, bitmapWidth),
				((maxRow + 1) * cellSize).coerceIn(1, bitmapHeight),
			)
			if (rect.width() < cellSize * 3 || rect.height() < cellSize * 3) continue
			result += Component(
				rect = rect,
				cellCount = count,
				touchesEdge = touchesEdge,
			)
		}
		return result
	}

	private fun matchesCandidate(candidateRect: Rect, fragmentRect: Rect, cellSize: Int): Boolean {
		val expanded = Rect(
			(candidateRect.left - cellSize * 2).coerceAtLeast(0),
			(candidateRect.top - cellSize * 2).coerceAtLeast(0),
			candidateRect.right + cellSize * 2,
			candidateRect.bottom + cellSize * 2,
		)
		if (expanded.contains(fragmentRect.centerX(), fragmentRect.centerY())) {
			return true
		}
		val overlapArea = overlapArea(expanded, fragmentRect)
		val fragmentArea = rectArea(fragmentRect).coerceAtLeast(1f)
		return overlapArea / fragmentArea >= 0.45f
	}

	private fun buildCandidate(
		component: Component,
		unionRect: Rect,
		fragmentRects: List<Rect>,
		matchedIndices: List<Int>,
		cellSize: Int,
		bitmapArea: Float,
	): RankedCandidate? {
		val candidateArea = rectArea(component.rect).coerceAtLeast(1f)
		if (candidateArea > bitmapArea * 0.35f) return null
		if (component.touchesEdge && candidateArea > bitmapArea * 0.18f) return null

		val fragmentsArea = matchedIndices.sumOf { rectArea(fragmentRects[it]).toDouble() }.toFloat()
		val unionArea = rectArea(unionRect).coerceAtLeast(1f)
		val inflation = candidateArea / unionArea
		val textCoverage = fragmentsArea / candidateArea
		val matchedCount = matchedIndices.size
		val maxInflation = when {
			matchedCount >= 3 -> 14f
			matchedCount == 2 -> 18f
			else -> 24f
		}
		val minCoverage = when {
			matchedCount >= 3 -> 0.008f
			matchedCount == 2 -> 0.012f
			else -> 0.018f
		}
		if (inflation > maxInflation || textCoverage < minCoverage) {
			return null
		}
		if (
			component.rect.width() > unionRect.width() * 6 &&
			component.rect.height() > unionRect.height() * 6
		) {
			return null
		}
		val tightenedRect = tightenCandidateRect(component.rect, unionRect, cellSize)
		if (tightenedRect.width() <= cellSize * 2 || tightenedRect.height() <= cellSize * 2) {
			return null
		}
		val score = matchedCount * 4f + textCoverage * 120f - inflation - if (component.touchesEdge) 2f else 0f
		return RankedCandidate(
			rect = tightenedRect,
			fragmentIndices = matchedIndices.sorted(),
			score = score,
		)
	}

	private fun tightenCandidateRect(candidateRect: Rect, unionRect: Rect, cellSize: Int): Rect {
		val padX = max(cellSize * 2, unionRect.width() / 5)
		val padY = max(cellSize * 2, unionRect.height() / 5)
		val left = max(candidateRect.left, unionRect.left - padX)
		val top = max(candidateRect.top, unionRect.top - padY)
		val right = min(candidateRect.right, unionRect.right + padX)
		val bottom = min(candidateRect.bottom, unionRect.bottom + padY)
		return Rect(
			left,
			top,
			max(left + cellSize * 2, right),
			max(top + cellSize * 2, bottom),
		)
	}

	private fun overlapArea(a: Rect, b: Rect): Float {
		val width = (min(a.right, b.right) - max(a.left, b.left)).coerceAtLeast(0)
		val height = (min(a.bottom, b.bottom) - max(a.top, b.top)).coerceAtLeast(0)
		return (width * height).toFloat()
	}

	private fun rectArea(rect: Rect): Float {
		return (rect.width().coerceAtLeast(0) * rect.height().coerceAtLeast(0)).toFloat()
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

	private data class Component(
		val rect: Rect,
		val cellCount: Int,
		val touchesEdge: Boolean,
	)

	private data class RankedCandidate(
		val rect: Rect,
		val fragmentIndices: List<Int>,
		val score: Float,
	) {
		fun isBetterThan(other: RankedCandidate): Boolean {
			if (score != other.score) return score > other.score
			return rectArea(rect) < rectArea(other.rect)
		}

		private fun rectArea(rect: Rect): Float {
			return (rect.width().coerceAtLeast(0) * rect.height().coerceAtLeast(0)).toFloat()
		}
	}
}
