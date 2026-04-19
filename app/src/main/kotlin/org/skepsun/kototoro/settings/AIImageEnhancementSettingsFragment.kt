package org.skepsun.kototoro.settings


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.util.ext.viewLifecycleScope
import org.skepsun.kototoro.reader.translate.data.OnnxModelCategory
import org.skepsun.kototoro.reader.translate.data.OnnxModelManager
import org.skepsun.kototoro.reader.translate.data.OnnxOfficialModelCatalog
import org.skepsun.kototoro.settings.compose.AIImageEnhancementSettingsScreen
import org.skepsun.kototoro.settings.compose.SettingsChoiceOption
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import javax.inject.Inject

@AndroidEntryPoint
class AIImageEnhancementSettingsFragment : Fragment() {

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

        val ncnnModels = buildList {
            addAll(OnnxOfficialModelCatalog.models.filter { it.category == OnnxModelCategory.IMAGE_SUPER_RESOLUTION }.map {
                val suffix = if (onnxModelManager.isModelDownloaded(it.id)) "" else getString(R.string.reader_translation_ocr_model_selection_not_downloaded_suffix)
                SettingsChoiceOption(it.id, it.title + suffix)
            })
        }

        (view as ComposeView).setContent {
            KototoroTheme {
                AIImageEnhancementSettingsScreen(
                    settings = settings,
                    ncnnModels = ncnnModels,
                    onClearCacheClick = { clearSrCache() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.ai_image_enhancement_settings))
    }

    private fun clearSrCache() {
        viewLifecycleScope.launch(Dispatchers.IO) {
            val srCacheDir = java.io.File(requireContext().cacheDir, "sr_cache")
            var deletedCount = 0
            if (srCacheDir.exists() && srCacheDir.isDirectory) {
                srCacheDir.listFiles()?.forEach { file ->
                    if (file.delete()) deletedCount++
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.reader_super_resolution_cache_cleared) + " ($deletedCount files)",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}
