package org.skepsun.kototoro.reader.translate.domain

import android.graphics.Rect

data class OcrTextBlock(
	val text: String,
	val boundingBox: Rect?,
)
