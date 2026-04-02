package org.skepsun.kototoro.settings

import android.os.Bundle
import android.widget.Toast
import androidx.annotation.Keep
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.BasePreferenceFragment
import org.skepsun.kototoro.reader.translate.data.OnnxModelManager
import org.skepsun.kototoro.reader.translate.data.OnnxModelCategory
import org.skepsun.kototoro.reader.translate.data.OnnxOfficialModel
import org.skepsun.kototoro.reader.translate.data.OnnxOfficialModelCatalog
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class OcrModelsFragment : BasePreferenceFragment(R.string.reader_translation_ocr_models_title) {

    @Inject
    lateinit var onnxModelManager: OnnxModelManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val screen = preferenceManager.createPreferenceScreen(requireContext())
        preferenceScreen = screen

        val onnxCategory = PreferenceCategory(requireContext()).apply {
            title = getString(R.string.reader_translation_onnx_models_title)
        }
        screen.addPreference(onnxCategory)

        OnnxOfficialModelCatalog.models
            .filter { it.category == OnnxModelCategory.CLASSIC_TRANSLATION }
            .forEach { model ->
            val pref = Preference(requireContext()).apply {
                title = model.title
                summary = model.description
                key = "onnx_${model.id}"
            }
            onnxCategory.addPreference(pref)
            updateOnnxStatus(pref, model)
        }

        val onnxOcrCategory = PreferenceCategory(requireContext()).apply {
            title = getString(R.string.reader_translation_paddle_official_model_id)
        }
        screen.addPreference(onnxOcrCategory)

        OnnxOfficialModelCatalog.models
            .filter { it.category == OnnxModelCategory.OCR }
            .forEach { model ->
                val pref = Preference(requireContext()).apply {
                    title = model.title
                    summary = model.description
                    key = "onnx_ocr_${model.id}"
                }
                onnxOcrCategory.addPreference(pref)
                updateOnnxStatus(pref, model)
            }

        val onnxLlmCategory = PreferenceCategory(requireContext()).apply {
            title = getString(R.string.reader_translation_onnx_general_llm_models_title)
        }
        screen.addPreference(onnxLlmCategory)

        OnnxOfficialModelCatalog.models
            .filter { it.category == OnnxModelCategory.GENERAL_LLM }
            .forEach { model ->
                val pref = Preference(requireContext()).apply {
                    title = model.title
                    summary = model.description
                key = "onnx_${model.id}"
            }
            onnxLlmCategory.addPreference(pref)
            updateOnnxStatus(pref, model)
        }

        val onnxBubbleDetectorCategory = PreferenceCategory(requireContext()).apply {
            title = getString(R.string.reader_translation_onnx_bubble_detector_models_title)
        }
        screen.addPreference(onnxBubbleDetectorCategory)

        OnnxOfficialModelCatalog.models
            .filter { it.category == OnnxModelCategory.BUBBLE_DETECTION }
            .forEach { model ->
                val pref = Preference(requireContext()).apply {
                    title = model.title
                    summary = model.description
                    key = "onnx_${model.id}"
                }
                onnxBubbleDetectorCategory.addPreference(pref)
                updateOnnxStatus(pref, model)
            }
    }

    private fun updateOnnxStatus(pref: Preference, model: OnnxOfficialModel) {
        val downloaded = onnxModelManager.isModelDownloaded(model.id)
        pref.summary = if (downloaded) {
            "${getString(R.string.reader_translation_ocr_model_status_downloaded)} (${model.version})"
        } else {
            "${getString(R.string.reader_translation_ocr_model_status_not_downloaded)} (${model.version})"
        }
        pref.setOnPreferenceClickListener {
            downloadOnnxModel(pref, model)
            true
        }
    }

    private fun downloadOnnxModel(pref: Preference, model: OnnxOfficialModel) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                pref.isEnabled = false
                pref.summary = getString(R.string.loading_)
                onnxModelManager.ensureModelReady(
                    model = model,
                    onProgress = { progress ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            val percent = if (progress.totalBytes > 0) {
                                (progress.downloadedBytes * 100 / progress.totalBytes).toInt()
                            } else -1
                            pref.summary = if (percent >= 0) {
                                "Downloading package... $percent%"
                            } else {
                                "Downloading package... ${progress.downloadedBytes / 1024} KB"
                            }
                        }
                    },
                )
                updateOnnxStatus(pref, model)
                Toast.makeText(context, R.string.reader_translation_onnx_download_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                pref.summary = "Error: ${e.message}"
            } finally {
                pref.isEnabled = true
            }
        }
    }
}
