package org.skepsun.kototoro.settings


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.settings.compose.TranslationSettingsScreen
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.reader.translate.data.OnnxModelManager
import org.skepsun.kototoro.reader.translate.data.OnnxModelCategory
import org.skepsun.kototoro.reader.translate.data.OnnxOfficialModelCatalog
import org.skepsun.kototoro.settings.compose.SettingsChoiceOption
import javax.inject.Inject

@AndroidEntryPoint
class TranslationSettingsFragment : Fragment() {

    @Inject
    lateinit var onnxModelManager: OnnxModelManager

    private val settings: AppSettings by lazy { AppSettings(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val onnxModels = buildList {
            add(SettingsChoiceOption("", getString(R.string.reader_translation_local_model_mlkit)))
            addAll(OnnxOfficialModelCatalog.models.filter { it.category == OnnxModelCategory.CLASSIC_TRANSLATION }.map {
                val suffix = if (onnxModelManager.isModelDownloaded(it.id)) "" else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
                SettingsChoiceOption(it.id, it.title + suffix)
            })
        }
        
        val paddleDetModels = buildList {
            add(SettingsChoiceOption("MLKIT", getString(R.string.reader_translation_ocr_det_mlkit)))
            addAll(OnnxOfficialModelCatalog.models.filter { it.category == OnnxModelCategory.OCR_DETECTOR }.map {
                val suffix = if (onnxModelManager.isModelDownloaded(it.id)) "" else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
                SettingsChoiceOption(it.id, it.title + suffix)
            })
        }
        
        val paddleOfficialModels = buildList {
            val isMangaOcrDownloaded = onnxModelManager.isModelDownloaded("mangaocr_2025_onnx")
            val mangaOcrSuffix = if (isMangaOcrDownloaded) "" else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
            add(SettingsChoiceOption("AUTO", getString(R.string.reader_translation_ocr_rec_model_auto)))
            add(SettingsChoiceOption("MLKIT", getString(R.string.reader_translation_ocr_det_mlkit)))
            add(SettingsChoiceOption("mangaocr_2025_onnx", "MangaOCR 2025"))
            addAll(OnnxOfficialModelCatalog.models.filter { it.category == OnnxModelCategory.OCR_RECOGNIZER && !it.id.startsWith("mangaocr") }.map {
                val suffix = if (onnxModelManager.isModelDownloaded(it.id)) "" else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
                SettingsChoiceOption(it.id, it.title + suffix)
            })
        }
        
        val onnxBubbleModels = buildList {
            add(SettingsChoiceOption("AUTO", getString(R.string.reader_translation_ocr_model_onnx_automatic)))
            addAll(OnnxOfficialModelCatalog.models.filter { it.category == OnnxModelCategory.BUBBLE_DETECTION }.map {
                val suffix = if (onnxModelManager.isModelDownloaded(it.id)) "" else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
                SettingsChoiceOption(it.id, it.title + suffix)
            })
        }

        (view as ComposeView).setContent {
            KototoroTheme {
                TranslationSettingsScreen(
                    settings = settings,
                    onnxModels = onnxModels,
                    paddleDetModels = paddleDetModels,
                    paddleOfficialModels = paddleOfficialModels,
                    onnxBubbleModels = onnxBubbleModels,
                    onOpenOcrModels = { (activity as? SettingsActivity)?.openFragment(org.skepsun.kototoro.settings.OcrModelsFragment::class.java, null, false) },
                    onOpenApiSettings = { (activity as? SettingsActivity)?.openFragment(org.skepsun.kototoro.settings.TranslationApiSettingsFragment::class.java, null, false) },
                    onOpenE2eApiSettings = { (activity as? SettingsActivity)?.openFragment(org.skepsun.kototoro.settings.TranslationEndToEndApiSettingsFragment::class.java, null, false) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.translation_settings))
    }
}
