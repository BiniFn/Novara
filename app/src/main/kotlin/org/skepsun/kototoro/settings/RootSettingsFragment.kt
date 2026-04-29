package org.skepsun.kototoro.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.BuildConfig
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.settings.compose.SettingsRootScreen
import org.skepsun.kototoro.settings.compose.buildSettingsRootSections
import org.skepsun.kototoro.settings.search.SettingsSearchViewModel

@AndroidEntryPoint
class RootSettingsFragment : Fragment() {

    private val viewModel: RootSettingsViewModel by viewModels()
    private val activityViewModel: SettingsSearchViewModel by activityViewModels()

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
            val enabledSourcesCount = viewModel.enabledSourcesCount.collectAsStateWithLifecycle().value
            val totalSourcesCount = viewModel.totalSourcesCount
            val searchQuery = activityViewModel.queryText.collectAsStateWithLifecycle().value
            val searchResults = activityViewModel.content.collectAsStateWithLifecycle().value

            KototoroTheme {
                SettingsRootScreen(
                    sections = buildSettingsRootSections(
                        context = requireContext(),
                        enabledSourcesCount = enabledSourcesCount,
                        totalSourcesCount = totalSourcesCount,
                        classLoader = requireActivity().classLoader,
                        onOpenFragment = { fragmentClass ->
                            (activity as? SettingsActivity)?.openFragment(fragmentClass, null, true)
                        },
                        onOpenDestination = { destination ->
                            (activity as? SettingsActivity)?.openDestination(destination, null, true)
                        },
                    ),
                    title = getString(R.string.settings),
                    subtitle = getString(R.string.app_version, BuildConfig.VERSION_NAME),
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    onSearchQueryChange = activityViewModel::setSearchQuery,
                    onSearchResultClick = activityViewModel::navigateToPreference,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!resources.getBoolean(R.bool.is_tablet)) {
            requireActivity().title = getString(R.string.settings)
        }
    }
}
