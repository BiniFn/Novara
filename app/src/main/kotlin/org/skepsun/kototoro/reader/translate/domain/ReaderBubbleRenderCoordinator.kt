package org.skepsun.kototoro.reader.translate.domain

import android.graphics.Bitmap

internal class ReaderBubbleRenderCoordinator(
	private val isLikelyGarbledText: (String) -> Boolean,
	private val shouldSuppressRenderedBubble: (String, String, String) -> Boolean,
	private val isLikelySpeechBubbleRegion: (Bitmap, android.graphics.Rect) -> Boolean,
	private val prepareTranslatedBubble: (android.graphics.Rect, String, Int, Int, Boolean, Boolean, Boolean, android.graphics.Rect?) -> PreparedBubble?,
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
				bubble.rect,
				translated,
				bitmap.width,
				bitmap.height,
				bubble.verticalPreferred,
				bubbleLikeRegion,
				bubble.detectorAnchored,
				bubble.sourceContentRect,
			)
			if (prepared == null) {
				log {
					"bubble render skipped_layout src=${oneLine(bubble.sourceText, 140)} out=${oneLine(translated, 140)} box=${bubble.rect} verticalPreferred=${bubble.verticalPreferred} bubbleLike=$bubbleLikeRegion"
				}
				continue
			}
			preparedBubbles.add(prepared)
		}
		return BubbleRenderPreparationResult(
			preparedBubbles = preparedBubbles,
			nonEmptyTranslatedCount = nonEmptyTranslatedCount,
		)
	}
}
