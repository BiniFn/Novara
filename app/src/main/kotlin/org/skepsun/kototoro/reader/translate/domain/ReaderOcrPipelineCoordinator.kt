package org.skepsun.kototoro.reader.translate.domain

import android.graphics.Bitmap
import android.net.Uri

internal class ReaderOcrPipelineCoordinator(
	private val loadPageText: suspend (Uri, String, Long) -> PageOcrLoadResult,
	private val mergePageTextBlocks: (List<OcrTextBlock>, Bitmap, String) -> List<TextFragment>,
	private val groupFragmentsForTranslation: suspend (List<TextFragment>, Bitmap) -> BubbleGroupingResult,
) {

	suspend fun execute(
		sourceUri: Uri,
		sourceLang: String,
		pageId: Long,
		bitmap: Bitmap,
	): OcrPipelineResult {
		return executePageFirst(
			sourceUri = sourceUri,
			sourceLang = sourceLang,
			pageId = pageId,
			bitmap = bitmap,
		)
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
				mergedTextFragments = emptyList(),
				pageOcr = pageOcr,
				groupingResult = null,
			)
		}
		val mergedTextFragments = mergePageTextBlocks(pageOcr.textBlocks, bitmap, sourceLang)
		val groupingResult = groupFragmentsForTranslation(mergedTextFragments, bitmap)
		return OcrPipelineResult(
			pageTextBlocks = pageOcr.textBlocks,
			mergedTextFragments = mergedTextFragments,
			pageOcr = pageOcr,
			groupingResult = groupingResult,
		)
	}
}

internal data class OcrPipelineResult(
	val pageTextBlocks: List<OcrTextBlock>,
	val mergedTextFragments: List<TextFragment>,
	val pageOcr: PageOcrLoadResult?,
	val groupingResult: BubbleGroupingResult?,
)

internal data class PageOcrLoadResult(
	val textBlocks: List<OcrTextBlock>,
	val cacheHit: Boolean,
	val durationMs: Long,
)
