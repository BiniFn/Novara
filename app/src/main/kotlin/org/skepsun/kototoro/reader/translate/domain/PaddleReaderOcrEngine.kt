package org.skepsun.kototoro.reader.translate.domain

import android.net.Uri
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

@ActivityRetainedScoped
class PaddleReaderOcrEngine @Inject constructor(
	private val ncnnReaderOcrEngine: NcnnReaderOcrEngine,
) : ReaderOcrService {

	override suspend fun recognize(sourceUri: Uri, sourceLang: String, pageId: Long?): List<OcrTextBlock> {
		return ncnnReaderOcrEngine.recognize(sourceUri, sourceLang, pageId)
	}
}
