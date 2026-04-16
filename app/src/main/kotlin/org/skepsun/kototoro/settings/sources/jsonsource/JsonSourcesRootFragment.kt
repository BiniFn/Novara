package org.skepsun.kototoro.settings.sources.jsonsource

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.settings.SettingsActivity
import org.skepsun.kototoro.settings.compose.SettingsTabFragmentPage
import org.skepsun.kototoro.settings.compose.SettingsTabbedFragmentsScreen

class JsonSourcesRootFragment : Fragment() {

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
                        title = getString(R.string.source_type_legado),
                        tag = "json_sources_legado",
                        createFragment = { createJsonSourcesFragment(JsonSourceType.LEGADO) },
                    ),
                    SettingsTabFragmentPage(
                        title = getString(R.string.source_type_tvbox),
                        tag = "json_sources_tvbox",
                        createFragment = { createJsonSourcesFragment(JsonSourceType.TVBOX) },
                    ),
                    SettingsTabFragmentPage(
                        title = getString(R.string.source_type_lnreader),
                        tag = "json_sources_lnreader",
                        createFragment = { LNReaderRepoFragment() },
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
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.json_sources_directory))
    }

    private fun createJsonSourcesFragment(type: JsonSourceType): Fragment {
        return JsonSourcesFragment().apply {
            arguments = Bundle(1).apply {
                putString(JsonSourcesFragment.ARG_SOURCE_TYPE, type.name)
            }
        }
    }
}
