package org.skepsun.kototoro.settings


import android.os.Bundle
import android.view.View
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.settings.compose.AISettingsScreen
import org.skepsun.kototoro.core.ui.theme.KototoroTheme

@AndroidEntryPoint
class AISettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as ComposeView).setContent {
            KototoroTheme {
                AISettingsScreen(
                    onOpenOcrModels = { (activity as? SettingsActivity)?.openFragment(org.skepsun.kototoro.settings.OcrModelsFragment::class.java, null, false) },
                    onOpenApiSettings = { (activity as? SettingsActivity)?.openFragment(org.skepsun.kototoro.settings.TranslationApiSettingsFragment::class.java, null, false) },
                    onOpenE2eApiSettings = { (activity as? SettingsActivity)?.openFragment(org.skepsun.kototoro.settings.TranslationEndToEndApiSettingsFragment::class.java, null, false) },
                    onOpenTranslationSettings = { (activity as? SettingsActivity)?.openFragment(org.skepsun.kototoro.settings.TranslationSettingsFragment::class.java, null, false) },
                    onOpenImageEnhancementSettings = { (activity as? SettingsActivity)?.openFragment(org.skepsun.kototoro.settings.AIImageEnhancementSettingsFragment::class.java, null, false) },
                    onOpenTtsSettings = { (activity as? SettingsActivity)?.openFragment(org.skepsun.kototoro.settings.TtsSettingsFragment::class.java, null, false) },
                    onOpenVideoEnhancementSettings = { (activity as? SettingsActivity)?.openFragment(org.skepsun.kototoro.settings.AIVideoEnhancementSettingsFragment::class.java, null, false) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.ai_settings))
    }
}
