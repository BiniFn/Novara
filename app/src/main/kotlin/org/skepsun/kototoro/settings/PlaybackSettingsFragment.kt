package org.skepsun.kototoro.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.settings.compose.PlaybackSettingsScreen


@AndroidEntryPoint
class PlaybackSettingsFragment : Fragment() {

    @Inject
    lateinit var settings: AppSettings

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
        (view as ComposeView).setContent {
            KototoroTheme {
                PlaybackSettingsRoute(
                    settings = settings,
                    onMpvConfClick = { org.skepsun.kototoro.video.player.MpvConfigManager.showMpvConfigDialog(requireContext(), view) },
                    onAiSettingsClick = {
                        (activity as? SettingsActivity)?.openDestination(SettingsDestination.AISettings, null, false)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.playback_settings))
    }
}

@Composable
fun PlaybackSettingsRoute(
    settings: AppSettings,
    onMpvConfClick: () -> Unit,
    onAiSettingsClick: () -> Unit,
) {
    PlaybackSettingsScreen(
        settings = settings,
        onMpvConfClick = onMpvConfClick,
        onAiSettingsClick = onAiSettingsClick,
    )
}
