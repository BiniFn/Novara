package org.skepsun.kototoro.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.settings.compose.AIVideoEnhancementSettingsScreen
import org.skepsun.kototoro.video.ui.VideoSuperResolutionAdvancedSheet
import javax.inject.Inject

@AndroidEntryPoint
class AIVideoEnhancementSettingsFragment : Fragment() {

    @Inject
    lateinit var appSettings: AppSettings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                KototoroTheme {
                    AIVideoEnhancementSettingsScreen(
                        settings = appSettings,
                        onAdvancedSettingsClick = {
                            VideoSuperResolutionAdvancedSheet().show(
                                parentFragmentManager,
                                "VideoSuperResolutionAdvancedSheet",
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.ai_video_enhancement_settings))
    }
}
