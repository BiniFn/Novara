package org.skepsun.kototoro.reader.translate.domain

import android.content.Context
import android.graphics.Rect
import android.net.Uri
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

	override suspend fun recognize(sourceUri: Uri, sourceLang: String): List<OcrTextBlock> {
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
		
		val bitmap = runInterruptible(Dispatchers.IO) {
			BitmapDecoderCompat.decode(sourceUri.toFile())
		}
		
		return try {
			val recognizedText = engine?.recognizeText(bitmap) ?: ""
			log { "recognize done text length=${recognizedText.length}" }
			
			// We return a single block containing the full text since Yomihon runs end-to-end
			listOf(
				OcrTextBlock(
					text = recognizedText,
					boundingBox = null
				)
			)
		} finally {
			bitmap.recycle()
		}
	}
	private suspend fun resolveModelPath(): String {
		val customPath = settings.readerTranslationTfliteModelPath.trim()
		if (customPath.isNotBlank()) {
			return customPath
		}
		val modelId = settings.readerTranslationTfliteModelId
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
