package org.skepsun.kototoro.reader.translate.domain

import android.graphics.Bitmap

internal class ReaderBubbleRenderCoordinator(
	private val isLikelyGarbledText: (String) -> Boolean,
	private val shouldSuppressRenderedBubble: (String, String, String) -> Boolean,
	private val isLikelySpeechBubbleRegion: (Bitmap, android.graphics.Rect) -> Boolean,
	private val prepareTranslatedBubble: (BubbleInput, String, Int, Int, Boolean) -> PreparedBubble?,
	private val qualityFilterEnabled: () -> Boolean,
	private val log: (() -> String) -> Unit,
	private val oneLine: (String, Int) -> String,
) {

	fun prepareBubbles(
		bubbleInputs: List<BubbleInput>,
		translatedMap: Map<String, String>,
		targetLang: String,
		bitmap: Bitmap,
	): BubbleRenderPreparationResult {
		val preparedBubbles = mutableListOf<PreparedBubble>()
		var nonEmptyTranslatedCount = 0
		for (bubble in bubbleInputs) {
			val translated = translatedMap[bubble.sourceText].orEmpty().trim()
			log {
				"bubble translate src=${oneLine(bubble.sourceText, 140)} out=${oneLine(translated, 140)} box=${bubble.rect}"
			}
			if (translated.isBlank()) continue
			nonEmptyTranslatedCount++
			if (qualityFilterEnabled() && isLikelyGarbledText(translated)) {
				log {
					"bubble render skipped_garbled src=${oneLine(bubble.sourceText, 140)} out=${oneLine(translated, 140)} box=${bubble.rect}"
				}
				continue
			}
			if (shouldSuppressRenderedBubble(bubble.sourceText, translated, targetLang)) {
				log {
					"bubble render suppressed src=${oneLine(bubble.sourceText, 140)} out=${oneLine(translated, 140)} box=${bubble.rect}"
				}
				continue
			}
			val bubbleLikeRegion = if (bubble.detectorAnchored) {
				true
			} else {
				when (bubble.classId) {
					2 -> false // RT-DETR text_free
					1 -> true  // RT-DETR text_bubble
					else -> isLikelySpeechBubbleRegion(bitmap, bubble.rect) // Legacy Model fallback (classId=0)
				}
			}
			val prepared = prepareTranslatedBubble(
				bubble,
				translated,
				bitmap.width,
				bitmap.height,
				bubbleLikeRegion,
			)
			if (prepared == null) {
				log {
					"bubble render skipped_layout src=${oneLine(bubble.sourceText, 140)} out=${oneLine(translated, 140)} box=${bubble.rect} verticalPreferred=${bubble.verticalPreferred} bubbleLike=$bubbleLikeRegion"
				}
				continue
			}
			prepared.debugOverlay?.let { overlay ->
				log {
					"bubble debug diagnosis=${overlay.diagnosis} detectorAnchored=${overlay.detectorAnchored} vertical=${overlay.verticalPreferred} " +
						"source=${overlay.sourceRect} content=${overlay.contentRect} prepared=${overlay.preparedRect} contentArea=${overlay.contentAreaRect}"
				}
			}
			preparedBubbles.add(prepared)
		}
		return BubbleRenderPreparationResult(
			preparedBubbles = preparedBubbles,
			nonEmptyTranslatedCount = nonEmptyTranslatedCount,
		)
	}
}
