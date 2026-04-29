package org.skepsun.kototoro.settings

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.settings.compose.ReaderSettingsScreen
import org.skepsun.kototoro.core.ui.theme.KototoroTheme

@AndroidEntryPoint
class ReaderSettingsFragment : Fragment() {

    private val settings: AppSettings by lazy { AppSettings(requireContext()) }

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
                ReaderSettingsRoute(
                    settings = settings,
                    onReaderTapActionsClick = {
                        startActivity(
                            android.content.Intent(
                                requireContext(),
                                org.skepsun.kototoro.settings.reader.ReaderTapGridConfigActivity::class.java,
                            ),
                        )
                    },
                    onReaderAiSettingsEntryClick = {
                        (activity as? SettingsActivity)?.openDestination(
                            SettingsDestination.AISettings,
                            null,
                            false,
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.reader_settings))
    }
}

@Composable
fun ReaderSettingsRoute(
    settings: AppSettings,
    onReaderTapActionsClick: () -> Unit,
    onReaderAiSettingsEntryClick: () -> Unit,
) {
    ReaderSettingsScreen(
        settings = settings,
        onReaderTapActionsClick = onReaderTapActionsClick,
        onReaderAiSettingsEntryClick = onReaderAiSettingsEntryClick,
    )
}
