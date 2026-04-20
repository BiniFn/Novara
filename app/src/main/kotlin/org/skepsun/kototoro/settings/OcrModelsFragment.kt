package org.skepsun.kototoro.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.Keep
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.reader.translate.data.OnnxModelCategory
import org.skepsun.kototoro.reader.translate.data.OnnxModelManager
import org.skepsun.kototoro.reader.translate.data.OnnxOfficialModel
import org.skepsun.kototoro.reader.translate.data.OnnxOfficialModelCatalog
import org.skepsun.kototoro.settings.compose.OcrModelItemUiState
import org.skepsun.kototoro.settings.compose.OcrModelSectionUiState
import org.skepsun.kototoro.settings.compose.OcrModelsSettingsScreen
import javax.inject.Inject

@Keep
@AndroidEntryPoint
class OcrModelsFragment : Fragment() {

    @Inject
    lateinit var onnxModelManager: OnnxModelManager

    private val sectionsFlow = MutableStateFlow<List<OcrModelSectionUiState>>(emptyList())
    private val transientStateByModelId = mutableMapOf<String, ModelTransientState>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rebuildSections()
        (view as ComposeView).setContent {
            val sections by sectionsFlow.collectAsState()
            KototoroTheme {
                OcrModelsSettingsScreen(
                    sections = sections,
                    onModelClick = ::handleModelClick,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.reader_translation_ocr_models_title))
    }

    private fun rebuildSections() {
        sectionsFlow.value = listOf(
            buildSection(
                title = getString(R.string.reader_translation_onnx_models_title),
                category = OnnxModelCategory.CLASSIC_TRANSLATION,
            ),
            buildSection(
                title = getString(R.string.reader_translation_ocr_detector_models_title),
                category = OnnxModelCategory.OCR_DETECTOR,
            ),
            buildSection(
                title = getString(R.string.reader_translation_ocr_recognizer_models_title),
                category = OnnxModelCategory.OCR_RECOGNIZER,
            ),
            buildSection(
                title = getString(R.string.reader_translation_onnx_bubble_detector_models_title),
                category = OnnxModelCategory.BUBBLE_DETECTION,
            ),
            buildSection(
                title = getString(R.string.reader_translation_onnx_super_resolution_models_title),
                category = OnnxModelCategory.IMAGE_SUPER_RESOLUTION,
            ),
        )
    }

    private fun buildSection(
        title: String,
        category: OnnxModelCategory,
    ): OcrModelSectionUiState {
        return OcrModelSectionUiState(
            title = title,
            items = OnnxOfficialModelCatalog.models
                .filter { it.category == category }
                .map(::buildItemState),
        )
    }

    private fun buildItemState(model: OnnxOfficialModel): OcrModelItemUiState {
        val transient = transientStateByModelId[model.id]
        val downloaded = onnxModelManager.isModelDownloaded(model.id)
        val statusText = transient?.progressText
            ?: transient?.errorText
            ?: getString(
                if (downloaded) R.string.reader_translation_ocr_model_status_downloaded
                else R.string.reader_translation_ocr_model_status_not_downloaded,
            ) + " (${model.version})"

        return OcrModelItemUiState(
            id = model.id,
            title = model.title,
            summary = "${model.description}\n$statusText",
            enabled = transient?.isBusy != true,
        )
    }

    private fun handleModelClick(modelId: String) {
        val model = OnnxOfficialModelCatalog.findById(modelId) ?: return
        if (onnxModelManager.isModelDownloaded(model.id)) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.reader_translation_model_delete_confirm, model.title))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (onnxModelManager.deleteModel(model.id)) {
                        transientStateByModelId.remove(model.id)
                        rebuildSections()
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.reader_translation_model_deleted, model.title),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            downloadOnnxModel(model)
        }
    }

    private fun downloadOnnxModel(model: OnnxOfficialModel) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                updateTransientState(
                    model.id,
                    ModelTransientState(
                        isBusy = true,
                        progressText = getString(R.string.loading_),
                    ),
                )
                onnxModelManager.ensureModelReady(
                    model = model,
                    onProgress = { progress ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            val percent = if (progress.totalBytes > 0) {
                                (progress.downloadedBytes * 100 / progress.totalBytes).toInt()
                            } else {
                                -1
                            }
                            updateTransientState(
                                model.id,
                                ModelTransientState(
                                    isBusy = true,
                                    progressText = if (percent >= 0) {
                                        getString(R.string.reader_translation_model_downloading_percent, percent)
                                    } else {
                                        getString(
                                            R.string.reader_translation_model_downloading_kb,
                                            progress.downloadedBytes / 1024,
                                        )
                                    },
                                ),
                            )
                        }
                    },
                )
                updateTransientState(model.id, null)
                Toast.makeText(requireContext(), R.string.reader_translation_onnx_download_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                updateTransientState(
                    model.id,
                    ModelTransientState(
                        errorText = getString(
                            R.string.reader_translation_paddle_download_failed,
                            e.message ?: "",
                        ),
                    ),
                )
            }
        }
    }

    private fun updateTransientState(
        modelId: String,
        state: ModelTransientState?,
    ) {
        if (state == null) {
            transientStateByModelId.remove(modelId)
        } else {
            transientStateByModelId[modelId] = state
        }
        rebuildSections()
    }

    private data class ModelTransientState(
        val isBusy: Boolean = false,
        val progressText: String? = null,
        val errorText: String? = null,
    )
}
