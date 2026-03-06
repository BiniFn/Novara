package org.skepsun.kototoro.settings

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.reader.translate.data.PaddleModelManager
import org.skepsun.kototoro.reader.translate.data.PaddleOfficialModelCatalog
import org.skepsun.kototoro.reader.translate.data.NcnnModelManager
import org.skepsun.kototoro.reader.translate.data.NcnnOfficialModel
import org.skepsun.kototoro.reader.translate.data.NcnnOfficialModelCatalog
import org.skepsun.kototoro.reader.translate.data.TfliteModelManager
import org.skepsun.kototoro.reader.translate.data.TfliteOfficialModel
import org.skepsun.kototoro.reader.translate.data.TfliteOfficialModelCatalog
import javax.inject.Inject

@AndroidEntryPoint
class OcrModelsFragment : BasePreferenceFragment(R.string.reader_translation_ocr_models_title) {

    @Inject
    lateinit var tfliteModelManager: TfliteModelManager

    @Inject
    lateinit var paddleModelManager: PaddleModelManager

    @Inject
    lateinit var ncnnModelManager: NcnnModelManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val screen = preferenceManager.createPreferenceScreen(requireContext())
        preferenceScreen = screen

        // Recognition Models (e.g. MangaOCR)
        val recCategory = PreferenceCategory(requireContext()).apply {
            title = getString(R.string.reader_translation_ocr_model_mangaocr_title)
        }
        screen.addPreference(recCategory)

        TfliteOfficialModelCatalog.models.forEach { model ->
            val pref = Preference(requireContext()).apply {
                title = model.title
                summary = model.version
                key = "mangaocr_${model.id}"
            }
            recCategory.addPreference(pref)
            updateMangaOcrStatus(pref, model)
        }

        // General OCR Models (PaddleOCR Presets)
        val generalCategory = PreferenceCategory(requireContext()).apply {
            title = "PaddleOCR Presets"
        }
        screen.addPreference(generalCategory)

        PaddleOfficialModelCatalog.ocrModels.forEach { model ->
            val pref = Preference(requireContext()).apply {
                title = model.title
                summary = "Version: ${model.version}"
                key = "paddle_${model.id}"
            }
            generalCategory.addPreference(pref)
            updatePaddleStatus(pref, model.id, model.version)
        }

        val ncnnCategory = PreferenceCategory(requireContext()).apply {
            title = getString(R.string.reader_translation_ocr_model_ncnn_title)
        }
        screen.addPreference(ncnnCategory)

        NcnnOfficialModelCatalog.models.forEach { model ->
            val pref = Preference(requireContext()).apply {
                title = model.title
                summary = "Version: ${model.version}"
                key = "ncnn_${model.id}"
            }
            ncnnCategory.addPreference(pref)
            updateNcnnStatus(pref, model)
        }
    }

    private fun updateMangaOcrStatus(pref: Preference, model: TfliteOfficialModel) {
        val downloaded = tfliteModelManager.isModelDownloaded(model.version)
        pref.widgetLayoutResource = if (downloaded) 0 else R.layout.preference_widget_download
        pref.summary = if (downloaded) {
            "${getString(R.string.reader_translation_ocr_model_status_downloaded)} (Version: ${model.version})"
        } else {
            "${getString(R.string.reader_translation_ocr_model_status_not_downloaded)} (Version: ${model.version})"
        }

        pref.setOnPreferenceClickListener {
            downloadMangaOcr(pref, model)
            true
        }
    }

    private fun downloadMangaOcr(pref: Preference, model: TfliteOfficialModel) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                pref.isEnabled = false
                pref.summary = getString(R.string.loading_)
                tfliteModelManager.ensureModelReady(
                    version = model.version,
                    encoderUrl = model.encoderUrl,
                    decoderUrl = model.decoderUrl,
                    vocabUrl = model.vocabUrl,
                    embeddingsUrl = model.embeddingsUrl,
                    onProgress = { component, progress ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            val percent = if (progress.totalBytes > 0) {
                                (progress.downloadedBytes * 100 / progress.totalBytes).toInt()
                            } else -1
                            pref.summary = if (percent >= 0) {
                                "Downloading $component... $percent%"
                            } else {
                                "Downloading $component... ${progress.downloadedBytes / 1024} KB"
                            }
                        }
                    }
                )
                updateMangaOcrStatus(pref, model)
                Toast.makeText(context, R.string.reader_translation_tflite_download_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                pref.summary = "Error: ${e.message}"
            } finally {
                pref.isEnabled = true
            }
        }
    }

    private fun updatePaddleStatus(pref: Preference, id: String, version: String) {
        val downloaded = paddleModelManager.isModelDownloaded(version)
        pref.summary = if (downloaded) {
            "${getString(R.string.reader_translation_ocr_model_status_downloaded)} (Version: $version)"
        } else {
            "${getString(R.string.reader_translation_ocr_model_status_not_downloaded)} (Version: $version)"
        }

        pref.setOnPreferenceClickListener {
            val model = PaddleOfficialModelCatalog.findById(id) ?: return@setOnPreferenceClickListener true
            downloadPaddleModel(pref, model)
            true
        }
    }

    private fun downloadPaddleModel(pref: Preference, model: org.skepsun.kototoro.reader.translate.data.PaddleOfficialModel) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                pref.isEnabled = false
                pref.summary = getString(R.string.loading_)
                paddleModelManager.ensureModelReady(
                    version = model.version,
                    zipUrl = model.downloadUrl,
                    zipSha256 = "",
                    onProgress = { progress ->
                        viewLifecycleOwner.lifecycleScope.launch {
                             val percent = if (progress.totalBytes > 0) {
                                (progress.downloadedBytes * 100 / progress.totalBytes).toInt()
                            } else -1
                            pref.summary = if (percent >= 0) {
                                "Downloading... $percent%"
                            } else {
                                "Downloading... ${progress.downloadedBytes / 1024} KB"
                            }
                        }
                    }
                )
                updatePaddleStatus(pref, model.id, model.version)
                Toast.makeText(context, R.string.reader_translation_paddle_download_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                pref.summary = "Error: ${e.message}"
            } finally {
                pref.isEnabled = true
            }
        }
    }

    private fun updateNcnnStatus(pref: Preference, model: NcnnOfficialModel) {
        val downloaded = ncnnModelManager.isModelDownloaded(model.version)
        pref.summary = if (downloaded) {
            "${getString(R.string.reader_translation_ocr_model_status_downloaded)} (Version: ${model.version})"
        } else {
            "${getString(R.string.reader_translation_ocr_model_status_not_downloaded)} (Version: ${model.version})"
        }

        pref.setOnPreferenceClickListener {
            downloadNcnnModel(pref, model)
            true
        }
    }

    private fun downloadNcnnModel(pref: Preference, model: NcnnOfficialModel) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                pref.isEnabled = false
                pref.summary = getString(R.string.loading_)
                ncnnModelManager.ensureModelReady(
                    version = model.version,
                    detParamUrl = model.detParamUrl,
                    detBinUrl = model.detBinUrl,
                    recParamUrl = model.recParamUrl,
                    recBinUrl = model.recBinUrl,
                    onProgress = { component, progress ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            val percent = if (progress.totalBytes > 0) {
                                (progress.downloadedBytes * 100 / progress.totalBytes).toInt()
                            } else -1
                            pref.summary = if (percent >= 0) {
                                "Downloading $component... $percent%"
                            } else {
                                "Downloading $component... ${progress.downloadedBytes / 1024} KB"
                            }
                        }
                    },
                )
                updateNcnnStatus(pref, model)
                Toast.makeText(context, R.string.reader_translation_ncnn_download_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                pref.summary = "Error: ${e.message}"
            } finally {
                pref.isEnabled = true
            }
        }
    }
}
