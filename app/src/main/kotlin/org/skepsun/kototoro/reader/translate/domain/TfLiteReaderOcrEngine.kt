package org.skepsun.kototoro.reader.translate.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.core.net.toFile
import com.google.ai.edge.litert.Environment
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.skepsun.kototoro.core.LocalizedAppContext
import org.skepsun.kototoro.core.image.BitmapDecoderCompat
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.reader.translate.data.TfliteModelManager
import org.skepsun.kototoro.reader.translate.data.TfliteOfficialModelCatalog
import java.io.File
import javax.inject.Inject

@ActivityRetainedScoped
class TfLiteReaderOcrEngine @Inject constructor(
	@LocalizedAppContext private val context: Context,
	private val settings: AppSettings,
	private val modelManager: TfliteModelManager,
) : ReaderOcrService {

	private var engine: FastOcrEngine? = null
	private val mutex = Mutex()
	private var modelPathInitialized: String? = null

	override suspend fun recognize(request: OcrRequest): List<OcrTextBlock> {
		val sourceUri = request.sourceUri
		val sourceLang = request.sourceLang
		val roi = request.roi
		log { "recognize start lang=$sourceLang uri=$sourceUri" }
		
		val modelPath = resolveModelPath()
		mutex.withLock {
			if (modelPathInitialized != modelPath || engine == null) {
				engine?.close()
				
				// Assumes the modelManager extracted these exact file names
				val encoderModelPath = File(modelPath, "encoder.tflite").absolutePath
				val decoderModelPath = File(modelPath, "decoder.tflite").absolutePath
				
				engine = FastOcrEngine(
					encoderModelPath = encoderModelPath,
					decoderModelPath = decoderModelPath,
					environment = Environment.create(),
					textPostprocessor = TextPostprocessor()
				)
				modelPathInitialized = modelPath
				log { "model initialized path=$modelPath" }
			}
		}
		
		val decodedBitmap = runInterruptible(Dispatchers.IO) {
			BitmapDecoderCompat.decode(sourceUri.toFile())
		}
		val bitmap = roi?.let { cropBitmap(decodedBitmap, it) } ?: decodedBitmap
		
		return try {
			val recognizedText = engine?.recognizeText(bitmap) ?: ""
			log { "recognize done text length=${recognizedText.length}" }
			
			// We return a single block containing the full text since Yomihon runs end-to-end
			listOf(
				OcrTextBlock(
					text = recognizedText,
					boundingBox = roi
				)
			)
		} finally {
			bitmap.recycle()
			if (bitmap !== decodedBitmap) {
				decodedBitmap.recycle()
			}
		}
	}

	private fun cropBitmap(source: Bitmap, box: Rect): Bitmap {
		val left = box.left.coerceIn(0, source.width - 1)
		val top = box.top.coerceIn(0, source.height - 1)
		val right = box.right.coerceIn(left + 1, source.width)
		val bottom = box.bottom.coerceIn(top + 1, source.height)
		return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
	}
	private suspend fun resolveModelPath(): String {
		val customPath = settings.readerTranslationRecModelPath.trim()
		if (customPath.isNotBlank()) {
			return customPath
		}
		val modelId = settings.readerTranslationRecModelId
		val model = TfliteOfficialModelCatalog.findById(modelId) ?: TfliteOfficialModelCatalog.models.first()
		
		return modelManager.ensureModelReady(
			version = model.version,
			encoderUrl = model.encoderUrl,
			decoderUrl = model.decoderUrl,
			vocabUrl = model.vocabUrl,
			embeddingsUrl = model.embeddingsUrl,
		)
	}

	private inline fun log(message: () -> String) {
		if (settings.isReaderTranslationDebugLogsEnabled) {
			Log.d(LOG_TAG, message())
		}
	}

	private companion object {
		const val LOG_TAG = "ReaderOcrTfLite"
	}
}
