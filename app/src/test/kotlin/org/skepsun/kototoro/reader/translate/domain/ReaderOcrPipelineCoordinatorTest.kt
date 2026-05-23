package org.skepsun.kototoro.reader.translate.domain

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ReaderOcrPipelineCoordinatorTest {

    private val testUri = mockk<Uri>(relaxed = true)

    @Test
    fun `execute returns empty grouping when page OCR has no text`() = runTest {
        val coordinator = ReaderOcrPipelineCoordinator(
            loadPageText = { _, _, _ ->
                PageOcrLoadResult(
                    textBlocks = emptyList(),
                    cacheHit = false,
                    durationMs = 1L,
                )
            },
            mergePageTextBlocks = { _, _, _ -> emptyList() },
            groupFragmentsForTranslation = { _, _ ->
                BubbleGroupingResult(
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
            },
        )

        val result = coordinator.execute(
            sourceUri = testUri,
            sourceLang = "ja",
            pageId = 1L,
            bitmap = mockk<Bitmap>(relaxed = true),
        )

        assertEquals(emptyList<OcrTextBlock>(), result.pageTextBlocks)
        assertEquals(emptyList<TextFragment>(), result.mergedTextFragments)
        assertNotNull(result.pageOcr)
        assertEquals(null, result.groupingResult)
    }

    @Test
    fun `execute merges page OCR blocks before grouping`() = runTest {
        val block = OcrTextBlock("hello", Rect(0, 0, 10, 10))
        val fragment = TextFragment(Rect(0, 0, 10, 10), "hello")
        val grouping = BubbleGroupingResult(
            groups = listOf(
                GroupedBubbleSource(
                    fragments = listOf(fragment),
                    bubbleRect = Rect(0, 0, 12, 12),
                ),
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
            detectorTotalMs = 1L,
            detectorFallbackReason = "",
            fallbackFragmentCount = 0,
            fallbackGroupCount = 0,
            fallbackMode = "none",
        )

        val coordinator = ReaderOcrPipelineCoordinator(
            loadPageText = { _, _, _ ->
                PageOcrLoadResult(
                    textBlocks = listOf(block),
                    cacheHit = true,
                    durationMs = 5L,
                )
            },
            mergePageTextBlocks = { blocks, _, _ ->
                assertEquals(listOf(block), blocks)
                listOf(fragment)
            },
            groupFragmentsForTranslation = { fragments, _ ->
                assertEquals(listOf(fragment), fragments)
                grouping
            },
        )

        val result = coordinator.execute(
            sourceUri = testUri,
            sourceLang = "ja",
            pageId = 2L,
            bitmap = mockk<Bitmap>(relaxed = true),
        )

        assertEquals(listOf(block), result.pageTextBlocks)
        assertEquals(listOf(fragment), result.mergedTextFragments)
        assertEquals(grouping, result.groupingResult)
        assertEquals(true, result.pageOcr?.cacheHit)
    }
}
