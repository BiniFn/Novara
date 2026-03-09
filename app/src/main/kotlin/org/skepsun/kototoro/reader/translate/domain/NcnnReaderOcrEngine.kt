package org.skepsun.kototoro.reader.translate.domain

import android.graphics.Rect
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import com.equationl.ncnnandroidppocr.OCR
import com.equationl.ncnnandroidppocr.bean.Device
import com.equationl.ncnnandroidppocr.bean.DrawModel
import com.equationl.ncnnandroidppocr.bean.ImageSize
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.reader.translate.data.NcnnModelManager
import org.skepsun.kototoro.reader.translate.data.NcnnOfficialModelCatalog
import java.io.File
import javax.inject.Inject

@ActivityRetainedScoped
class NcnnReaderOcrEngine @Inject constructor(
	private val settings: AppSettings,
	private val modelManager: NcnnModelManager,
) : ReaderOcrService {

	private val ocr = OCR()
	private val mutex = Mutex()
	private var modelPathInitialized: String? = null

	override suspend fun recognize(sourceUri: Uri, sourceLang: String): List<OcrTextBlock> {
		log { "recognize start lang=$sourceLang uri=$sourceUri" }
		ensureModelInitialized()
		val result = runInterruptible(Dispatchers.IO) {
			ocr.detectImagePath(sourceUri.toFile().absolutePath, DrawModel.None)
		} ?: return emptyList()
		val blocks = result.textLines.map { line ->
			val points = line.points
			val rect = if (points.isEmpty()) {
				null
			} else {
				val minX = points.minOf { it.x }
				val minY = points.minOf { it.y }
				val maxX = points.maxOf { it.x }
				val maxY = points.maxOf { it.y }
				Rect(minX, minY, maxX, maxY)
			}
			OcrTextBlock(
				text = line.text,
				boundingBox = rect,
			)
		}
		log { "recognize done blocks=${blocks.size}" }
		return blocks
	}

	suspend fun detectBoxes(sourceUri: Uri): List<Rect> {
		ensureModelInitialized()
		val result = runInterruptible(Dispatchers.IO) {
			ocr.detectImagePath(sourceUri.toFile().absolutePath, DrawModel.None)
		} ?: return emptyList()
		return result.textLines.mapNotNull { line ->
			val points = line.points
			if (points.isEmpty()) {
				null
			} else {
				val minX = points.minOf { it.x }
				val minY = points.minOf { it.y }
				val maxX = points.maxOf { it.x }
				val maxY = points.maxOf { it.y }
				if (maxX <= minX || maxY <= minY) {
					null
				} else {
					Rect(minX, minY, maxX, maxY)
				}
			}
		}
	}

	private suspend fun ensureModelInitialized() {
		val modelPath = resolveModelPath()
		mutex.withLock {
			if (modelPathInitialized != modelPath) {
				initModel(modelPath)
				modelPathInitialized = modelPath
				log { "model initialized path=$modelPath" }
			}
		}
	}

	private suspend fun resolveModelPath(): String {
		val model = NcnnOfficialModelCatalog.findById(settings.readerTranslationDetModelId)
			?: NcnnOfficialModelCatalog.models.first()
		return modelManager.ensureModelReady(
			version = model.version,
			detParamUrl = model.detParamUrl,
			detBinUrl = model.detBinUrl,
			recParamUrl = model.recParamUrl,
			recBinUrl = model.recBinUrl,
		)
	}

	private fun initModel(modelPath: String) {
		val modelDir = File(modelPath)
		val result = ocr.initModel(
			detParamPath = File(modelDir, "det.ncnn.param").absolutePath,
			detModelPath = File(modelDir, "det.ncnn.bin").absolutePath,
			recParamPath = File(modelDir, "rec.ncnn.param").absolutePath,
			recModelPath = File(modelDir, "rec.ncnn.bin").absolutePath,
			reSize = ImageSize.Size720,
			useDevice = Device.CPU,
			useFp16 = true,
		)
		check(result) { "NCNN OCR init failed" }
	}

	private inline fun log(message: () -> String) {
		if (settings.isReaderTranslationDebugLogsEnabled) {
			Log.d(LOG_TAG, message())
		}
	}

	private companion object {
		const val LOG_TAG = "ReaderOcrNcnn"
	}
}
