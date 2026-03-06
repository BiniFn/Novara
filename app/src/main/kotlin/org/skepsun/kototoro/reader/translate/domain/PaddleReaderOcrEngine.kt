package org.skepsun.kototoro.reader.translate.domain

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import com.equationl.paddleocr4android.OCR
import com.equationl.paddleocr4android.OcrConfig
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.skepsun.kototoro.core.LocalizedAppContext
import org.skepsun.kototoro.core.image.BitmapDecoderCompat
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.reader.translate.data.PaddleModelManager
import javax.inject.Inject

@ActivityRetainedScoped
class PaddleReaderOcrEngine @Inject constructor(
	@LocalizedAppContext context: Context,
	private val settings: AppSettings,
	private val modelManager: PaddleModelManager,
) : ReaderOcrService {

	private val ocr = OCR(context)
	private val mutex = Mutex()
	private var modelPathInitialized: String? = null

	override suspend fun recognize(sourceUri: Uri, sourceLang: String): List<OcrTextBlock> {
		log { "recognize start lang=$sourceLang uri=$sourceUri" }
		val modelPath = resolveModelPath()
		mutex.withLock {
			if (modelPathInitialized != modelPath) {
				initModel(modelPath)
				modelPathInitialized = modelPath
				log { "model initialized path=$modelPath" }
			}
		}
		val bitmap = runInterruptible(Dispatchers.IO) {
			BitmapDecoderCompat.decode(sourceUri.toFile())
		}
		return try {
			val result = ocr.runSync(bitmap).getOrThrow()
			val blocks = result.outputRawResult.map {
				OcrTextBlock(
					text = it.label.orEmpty(),
					boundingBox = it.points.takeIf { points -> points.isNotEmpty() }?.let { points ->
						val minX = points.minOf { point -> point.x }
						val minY = points.minOf { point -> point.y }
						val maxX = points.maxOf { point -> point.x }
						val maxY = points.maxOf { point -> point.y }
						Rect(minX, minY, maxX, maxY)
					},
				)
			}
			log { "recognize done blocks=${blocks.size}" }
			blocks
		} finally {
			bitmap.recycle()
		}
	}

	private suspend fun resolveModelPath(): String {
		val customPath = settings.readerTranslationPaddleModelPath.trim()
		if (customPath.isNotBlank()) {
			return customPath
		}
		return modelManager.ensureModelReady(
			version = settings.readerTranslationPaddleModelVersion.trim(),
			zipUrl = settings.readerTranslationPaddleModelUrl.trim(),
			zipSha256 = settings.readerTranslationPaddleModelSha256.trim(),
		)
	}

	private fun initModel(modelPath: String) {
		val config = OcrConfig(
			modelPath = modelPath,
			detModelFilename = "det.nb",
			recModelFilename = "rec.nb",
			clsModelFilename = "cls.nb",
			isRunDet = true,
			isRunCls = true,
			isRunRec = true,
			isDrwwTextPositionBox = false,
		)
		val result = ocr.initModelSync(config).getOrElse { error ->
			throw IllegalStateException("PaddleOCR init failed", error)
		}
		check(result) { "PaddleOCR init failed" }
	}

	private inline fun log(message: () -> String) {
		if (settings.isReaderTranslationDebugLogsEnabled) {
			Log.d(LOG_TAG, message())
		}
	}

	private companion object {

		const val LOG_TAG = "ReaderOcrPaddle"
	}
}
