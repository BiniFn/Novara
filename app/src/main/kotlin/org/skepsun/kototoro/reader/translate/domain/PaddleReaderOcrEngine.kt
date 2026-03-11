package org.skepsun.kototoro.reader.translate.domain

import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

@ActivityRetainedScoped
class PaddleReaderOcrEngine @Inject constructor(
	private val ncnnReaderOcrEngine: NcnnReaderOcrEngine,
) : ReaderOcrService {

	override suspend fun recognize(request: OcrRequest): List<OcrTextBlock> {
		return ncnnReaderOcrEngine.recognize(request)
	}
}
