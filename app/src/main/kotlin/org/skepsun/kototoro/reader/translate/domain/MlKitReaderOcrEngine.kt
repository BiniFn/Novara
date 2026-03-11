package org.skepsun.kototoro.reader.translate.domain

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.scopes.ActivityRetainedScoped
import org.skepsun.kototoro.core.LocalizedAppContext
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.util.ext.awaitCancellable
import javax.inject.Inject

@ActivityRetainedScoped
class MlKitReaderOcrEngine @Inject constructor(
	@LocalizedAppContext private val context: Context,
	private val settings: AppSettings,
) : ReaderOcrService {

	override suspend fun recognize(sourceUri: Uri, sourceLang: String, pageId: Long?): List<OcrTextBlock> {
		log { "recognize start lang=$sourceLang uri=$sourceUri" }
		val image = InputImage.fromFilePath(context, sourceUri)
		val recognizer = when (sourceLang) {
			"ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
			"zh", "zh-cn", "zh-hans", "zh-tw", "zh-hant" -> {
				TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
			}

			else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
		}
		return try {
			val blocks = recognizer.process(image).awaitCancellable().textBlocks.map {
				OcrTextBlock(
					text = it.text,
					boundingBox = it.boundingBox,
				)
			}
			log { "recognize done blocks=${blocks.size}" }
			blocks
		} finally {
			recognizer.close()
		}
	}

	private inline fun log(message: () -> String) {
		if (settings.isReaderTranslationDebugLogsEnabled) {
			Log.d(LOG_TAG, message())
		}
	}

	private companion object {

		const val LOG_TAG = "ReaderOcrMlKit"
	}
}
