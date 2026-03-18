package org.skepsun.kototoro.reader.translate.domain

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderOcrPipelineCoordinatorTest {

	private val testUri = mockk<Uri>(relaxed = true)

	@Test
	fun `ROI-first succeeds without loading page OCR`() = runTest {
		val roiRectA = Rect(10, 10, 80, 80)
		val roiRectB = Rect(100, 20, 180, 90)
		var loadPageTextCalls = 0
		var groupingCalls = 0
		var roiCalls = 0
		val coordinator = ReaderOcrPipelineCoordinator(
			loadPageText = { _, _, _ ->
				loadPageTextCalls += 1
				PageOcrLoadResult(emptyList(), cacheHit = false, durationMs = 1L)
			},
			detectBubbleRects = {
				OnnxBubbleDetectorEngine.DetectionAttempt(
					status = OnnxBubbleDetectorEngine.AttemptStatus.SUCCESS,
					result = OnnxBubbleDetectorEngine.DetectionResult(
						boxes = listOf(roiRectA, roiRectB),
						modelId = "bubble-v1",
						backend = "cpu",
						parser = "generic_yolo",
						rawBoxCount = 2,
						decodedBoxCount = 2,
						finalBoxCount = 2,
						totalMs = 15L,
					),
				)
			},
			groupFragmentsForTranslation = { _, _ ->
				groupingCalls += 1
				emptyGroupingResult()
			},
			recognizeBubbleTextsByRoi = { groups, _, _, _, _ ->
				roiCalls += 1
				assertEquals(2, groups.size)
				assertRectEquals(roiRectA, groups[0].bubbleRect)
				assertRectEquals(roiRectB, groups[1].bubbleRect)
				BubbleRoiOcrResult(
					textsByGroupIndex = mapOf(0 to "a", 1 to "b"),
					requestCount = 2,
					successCount = 2,
					fallbackCount = 0,
					totalMs = 9L,
					coverageArea = 0.9f,
				)
			},
		)

		val result = coordinator.execute(
			sourceUri = testUri,
			sourceLang = "ja",
			pageId = 1L,
			bitmap = testBitmap(),
			strategy = OcrPipelineStrategy.ROI_FIRST_FALLBACK,
		)

		assertEquals(OcrPipelineStrategy.ROI_FIRST_FALLBACK, result.strategy)
		assertTrue(result.pageTextBlocks.isEmpty())
		assertNull(result.pageOcr)
		assertEquals(0, loadPageTextCalls)
		assertEquals(0, groupingCalls)
		assertEquals(1, roiCalls)
		assertNotNull(result.groupingResult)
		assertEquals(2, result.groupingResult?.groups?.size)
		assertEquals("onnx_cpu", result.groupingResult?.detectorEngine)
		assertEquals("", result.fallbackReason)
		assertEquals(2, result.roiFirstDetectedBoxCount)
	}

	@Test
	fun `ROI-first falls back to page-first when detector returns no boxes`() = runTest {
		var loadPageTextCalls = 0
		var groupingCalls = 0
		var roiCalls = 0
		val pageBlocks = listOf(
			OcrTextBlock("hello", Rect(5, 5, 40, 30)),
		)
		val expectedGrouping = BubbleGroupingResult(
			groups = listOf(
				GroupedBubbleSource(
					fragments = listOf(TextFragment(Rect(5, 5, 40, 30), "hello")),
					bubbleRect = Rect(0, 0, 60, 40),
				)
			),
			detectorCandidateCount = 1,
			detectorMatchedFragmentCount = 1,
			detectorUsedGroupCount = 1,
			detectorSubdividedGroupCount = 0,
			detectorSubdividedFragmentCount = 0,
			detectorCoverageRate = 1f,
			detectorEngine = "cv",
			detectorModelId = "",
			detectorRawBoxCount = 1,
			detectorTotalMs = 8L,
			detectorFallbackReason = "onnx_no_boxes",
			fallbackFragmentCount = 0,
			fallbackGroupCount = 0,
			fallbackMode = "none",
		)
		val coordinator = ReaderOcrPipelineCoordinator(
			loadPageText = { _, _, _ ->
				loadPageTextCalls += 1
				PageOcrLoadResult(pageBlocks, cacheHit = true, durationMs = 5L)
			},
			detectBubbleRects = {
				OnnxBubbleDetectorEngine.DetectionAttempt(
					status = OnnxBubbleDetectorEngine.AttemptStatus.NO_BOXES,
					result = OnnxBubbleDetectorEngine.DetectionResult(
						boxes = emptyList(),
						modelId = "bubble-v1",
						backend = "cpu",
						parser = "generic_yolo",
						rawBoxCount = 0,
						decodedBoxCount = 0,
						finalBoxCount = 0,
						totalMs = 11L,
					),
				)
			},
			groupFragmentsForTranslation = { fragments, _ ->
				groupingCalls += 1
				assertEquals(1, fragments.size)
				expectedGrouping
			},
			recognizeBubbleTextsByRoi = { groups, _, _, _, _ ->
				roiCalls += 1
				assertEquals(expectedGrouping.groups, groups)
				BubbleRoiOcrResult(
					textsByGroupIndex = mapOf(0 to "hello"),
					requestCount = 1,
					successCount = 1,
					fallbackCount = 0,
					totalMs = 4L,
					coverageArea = 1f,
				)
			},
		)

		val result = coordinator.execute(
			sourceUri = testUri,
			sourceLang = "ja",
			pageId = 2L,
			bitmap = testBitmap(),
			strategy = OcrPipelineStrategy.ROI_FIRST_FALLBACK,
		)

		assertEquals(OcrPipelineStrategy.PAGE_FIRST, result.strategy)
		assertEquals(pageBlocks, result.pageTextBlocks)
		assertNotNull(result.pageOcr)
		assertEquals(true, result.pageOcr?.cacheHit)
		assertEquals(1, loadPageTextCalls)
		assertEquals(1, groupingCalls)
		assertEquals(1, roiCalls)
		assertSame(expectedGrouping, result.groupingResult)
		assertEquals("roi_first_no_boxes", result.fallbackReason)
		assertEquals(0, result.roiFirstDetectedBoxCount)
	}

	@Test
	fun `ROI-first falls back to page-first when ROI coverage is too low`() = runTest {
		var loadPageTextCalls = 0
		var groupingCalls = 0
		var roiCalls = 0
		val roiRect = Rect(20, 20, 120, 100)
		val pageBlocks = listOf(
			OcrTextBlock("fallback", Rect(25, 25, 90, 60)),
		)
		val fallbackGrouping = BubbleGroupingResult(
			groups = listOf(
				GroupedBubbleSource(
					fragments = listOf(TextFragment(Rect(25, 25, 90, 60), "fallback")),
					bubbleRect = Rect(20, 20, 120, 100),
				)
			),
			detectorCandidateCount = 1,
			detectorMatchedFragmentCount = 1,
			detectorUsedGroupCount = 1,
			detectorSubdividedGroupCount = 0,
			detectorSubdividedFragmentCount = 0,
			detectorCoverageRate = 1f,
			detectorEngine = "cv",
			detectorModelId = "",
			detectorRawBoxCount = 1,
			detectorTotalMs = 6L,
			detectorFallbackReason = "",
			fallbackFragmentCount = 0,
			fallbackGroupCount = 0,
			fallbackMode = "none",
		)
		val coordinator = ReaderOcrPipelineCoordinator(
			loadPageText = { _, _, _ ->
				loadPageTextCalls += 1
				PageOcrLoadResult(pageBlocks, cacheHit = false, durationMs = 7L)
			},
			detectBubbleRects = {
				OnnxBubbleDetectorEngine.DetectionAttempt(
					status = OnnxBubbleDetectorEngine.AttemptStatus.SUCCESS,
					result = OnnxBubbleDetectorEngine.DetectionResult(
						boxes = listOf(roiRect),
						modelId = "bubble-v1",
						backend = "cpu",
						parser = "generic_yolo",
						rawBoxCount = 1,
						decodedBoxCount = 1,
						finalBoxCount = 1,
						totalMs = 12L,
					),
				)
			},
			groupFragmentsForTranslation = { _, _ ->
				groupingCalls += 1
				fallbackGrouping
			},
			recognizeBubbleTextsByRoi = { groups, _, _, _, _ ->
				roiCalls += 1
				if (groups.firstOrNull()?.fragments?.isEmpty() == true) {
					BubbleRoiOcrResult(
						textsByGroupIndex = mapOf(0 to "weak"),
						requestCount = 1,
						successCount = 1,
						fallbackCount = 0,
						totalMs = 3L,
						coverageArea = 0.2f,
					)
				} else {
					BubbleRoiOcrResult(
						textsByGroupIndex = mapOf(0 to "fallback"),
						requestCount = 1,
						successCount = 1,
						fallbackCount = 0,
						totalMs = 4L,
						coverageArea = 1f,
					)
				}
			},
		)

		val result = coordinator.execute(
			sourceUri = testUri,
			sourceLang = "ja",
			pageId = 3L,
			bitmap = testBitmap(),
			strategy = OcrPipelineStrategy.ROI_FIRST_FALLBACK,
		)

		assertEquals(OcrPipelineStrategy.PAGE_FIRST, result.strategy)
		assertEquals(1, loadPageTextCalls)
		assertEquals(1, groupingCalls)
		assertEquals(2, roiCalls)
		assertEquals(pageBlocks, result.pageTextBlocks)
		assertNotNull(result.pageOcr)
		assertSame(fallbackGrouping, result.groupingResult)
		assertEquals(1f, result.roiResult.coverageArea)
		assertEquals("roi_first_low_coverage", result.fallbackReason)
		assertEquals(1, result.roiFirstDetectedBoxCount)
	}

	private fun emptyGroupingResult() = BubbleGroupingResult(
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
		fallbackMode = "none",
	)

	private fun assertRectEquals(expected: Rect, actual: Rect?) {
		assertNotNull(actual)
		assertEquals(expected.left, actual?.left)
		assertEquals(expected.top, actual?.top)
		assertEquals(expected.right, actual?.right)
		assertEquals(expected.bottom, actual?.bottom)
	}

	private fun testBitmap(): Bitmap = mockk(relaxed = true)
}
