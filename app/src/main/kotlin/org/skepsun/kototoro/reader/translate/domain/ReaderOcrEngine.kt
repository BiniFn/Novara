package org.skepsun.kototoro.reader.translate.domain

import android.net.Uri

interface ReaderOcrService {

	suspend fun recognize(sourceUri: Uri, sourceLang: String): List<OcrTextBlock>
}
