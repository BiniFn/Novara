package org.skepsun.kototoro.reader.translate.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.scopes.ActivityRetainedScoped
import org.skepsun.kototoro.core.LocalizedAppContext
import org.skepsun.kototoro.core.image.BitmapDecoderCompat
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.util.ext.awaitCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import androidx.core.net.toFile
import javax.inject.Inject

@ActivityRetainedScoped
class MlKitReaderOcrEngine @Inject constructor(
	@LocalizedAppContext private val context: Context,
	private val settings: AppSettings,
) : ReaderOcrService {

	override suspend fun recognize(request: OcrRequest): List<OcrTextBlock> {
		val sourceUri = request.sourceUri
		val sourceLang = request.sourceLang
		log { "recognize start lang=$sourceLang uri=$sourceUri" }
		val recognizer = when (sourceLang) {
			"ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
			"zh", "zh-cn", "zh-hans", "zh-tw", "zh-hant" -> {
				TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
			}

			else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
		}
		return try {
			val offsetRect = request.roi
			var decodedBitmap: Bitmap? = null
			var cropBitmap: Bitmap? = null
			try {
				val image = if (offsetRect == null) {
					InputImage.fromFilePath(context, sourceUri)
				} else {
					val pageBitmap = runInterruptible(Dispatchers.IO) {
						BitmapDecoderCompat.decode(sourceUri.toFile())
					}
					decodedBitmap = pageBitmap
					val crop = cropBitmap(pageBitmap, offsetRect)
					cropBitmap = crop
					InputImage.fromBitmap(crop, 0)
				}
				val blocks = recognizer.process(image).awaitCancellable().textBlocks.map {
					val boundingBox = it.boundingBox?.let { box ->
						if (offsetRect == null) {
							box
						} else {
							Rect(
								box.left + offsetRect.left,
								box.top + offsetRect.top,
								box.right + offsetRect.left,
								box.bottom + offsetRect.top,
							)
						}
					}
					OcrTextBlock(
						text = it.text,
						boundingBox = boundingBox,
					)
				}
				log { "recognize done blocks=${blocks.size}" }
				blocks
			} finally {
				cropBitmap?.recycle()
				decodedBitmap?.takeIf { it !== cropBitmap }?.recycle()
			}
		} finally {
			recognizer.close()
		}
	}

	private fun cropBitmap(source: Bitmap, box: Rect): Bitmap {
		val left = box.left.coerceIn(0, source.width - 1)
		val top = box.top.coerceIn(0, source.height - 1)
		val right = box.right.coerceIn(left + 1, source.width)
		val bottom = box.bottom.coerceIn(top + 1, source.height)
		return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
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
