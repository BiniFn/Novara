package org.skepsun.kototoro.settings.sources.extensions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import org.skepsun.kototoro.settings.SettingsActivity
import org.skepsun.kototoro.settings.compose.SettingsTabFragmentPage
import org.skepsun.kototoro.settings.compose.SettingsTabbedFragmentsScreen

class ExtensionsRootFragment : Fragment() {

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
            val pages = remember {
                listOf(
                    SettingsTabFragmentPage(
                        title = getString(R.string.mihon_sources),
                        tag = "extensions_mihon",
                        createFragment = { createExtensionsBrowserFragment(ExternalExtensionType.MIHON) },
                    ),
                    SettingsTabFragmentPage(
                        title = getString(R.string.aniyomi_sources),
                        tag = "extensions_aniyomi",
                        createFragment = { createExtensionsBrowserFragment(ExternalExtensionType.ANIYOMI) },
                    ),
                    SettingsTabFragmentPage(
                        title = getString(R.string.ireader_sources),
                        tag = "extensions_ireader",
                        createFragment = { createExtensionsBrowserFragment(ExternalExtensionType.IREADER) },
                    ),
                )
            }

            KototoroTheme {
                SettingsTabbedFragmentsScreen(
                    pages = pages,
                    fragmentManager = childFragmentManager,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.extensions))
    }

    private fun createExtensionsBrowserFragment(type: ExternalExtensionType): Fragment {
        return ExtensionsBrowserFragment().apply {
            arguments = Bundle(1).apply {
                putString(ARG_EXTENSION_TYPE, type.name)
            }
        }
    }
}
