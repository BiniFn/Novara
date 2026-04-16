package org.skepsun.kototoro.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.BuildConfig
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.util.ext.addSupportMenuProvider
import org.skepsun.kototoro.core.util.ext.getQuantityStringSafe
import org.skepsun.kototoro.settings.compose.SettingsRootItem
import org.skepsun.kototoro.settings.compose.SettingsRootScreen
import org.skepsun.kototoro.settings.compose.SettingsRootSection
import org.skepsun.kototoro.settings.search.SettingsSearchMenuProvider
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
        addSupportMenuProvider(SettingsSearchMenuProvider(activityViewModel))
        (view as ComposeView).setContent {
            val enabledSourcesCount = viewModel.enabledSourcesCount.collectAsStateWithLifecycle().value
            val totalSourcesCount = viewModel.totalSourcesCount

            KototoroTheme {
                SettingsRootScreen(
                    sections = buildSections(
                        enabledSourcesCount = enabledSourcesCount,
                        totalSourcesCount = totalSourcesCount,
                    ),
                    title = getString(R.string.settings),
                    subtitle = getString(R.string.app_version, BuildConfig.VERSION_NAME),
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

    private fun buildSections(
        enabledSourcesCount: Int,
        totalSourcesCount: Int,
    ): List<SettingsRootSection> {
        val contentSummary = if (enabledSourcesCount >= 0) {
            getString(R.string.enabled_d_of_d, enabledSourcesCount, totalSourcesCount)
        } else {
            resources.getQuantityStringSafe(R.plurals.items, totalSourcesCount, totalSourcesCount)
        }

        val coreSection = SettingsRootSection(
            title = getString(R.string.settings),
            items = listOf(
                settingsItem(
                    key = "appearance",
                    iconRes = R.drawable.ic_appearance,
                    title = getString(R.string.appearance),
                    summary = summaryOf(R.string.theme, R.string.list_mode, R.string.language),
                    fragmentClass = AppearanceSettingsFragment::class.java,
                ),
                settingsItem(
                    key = "remote_sources",
                    iconRes = R.drawable.ic_manga_source,
                    title = getString(R.string.remote_sources),
                    summary = contentSummary,
                    fragmentClass = org.skepsun.kototoro.settings.sources.SourcesSettingsFragment::class.java,
                ),
                settingsItem(
                    key = "reader",
                    iconRes = R.drawable.ic_book_page,
                    title = getString(R.string.reader_settings),
                    summary = summaryOf(R.string.read_mode, R.string.scale_mode, R.string.switch_pages),
                    fragmentClass = ReaderSettingsFragment::class.java,
                ),
                settingsItem(
                    key = "ai",
                    iconRes = R.drawable.ic_auto_fix,
                    title = getString(R.string.ai_settings),
                    summary = getString(R.string.ai_settings_entry_summary),
                    fragmentClass = AISettingsFragment::class.java,
                ),
                settingsItem(
                    key = "playback",
                    iconRes = R.drawable.ic_play,
                    title = getString(R.string.playback_settings),
                    summary = summaryOf(R.string.video_decoder_mode, R.string.video_cache_size),
                    fragmentClass = PlaybackSettingsFragment::class.java,
                ),
            ),
        )

        val servicesSection = SettingsRootSection(
            title = getString(R.string.services),
            items = buildList {
                add(
                    settingsItem(
                        key = "network",
                        iconRes = R.drawable.ic_usage,
                        title = getString(R.string.storage_and_network),
                        summary = summaryOf(R.string.storage_usage, R.string.proxy, R.string.prefetch_content),
                        fragmentClass = StorageAndNetworkSettingsFragment::class.java,
                    )
                )
                add(
                    settingsItem(
                        key = "downloads",
                        iconRes = R.drawable.ic_download,
                        title = getString(R.string.downloads),
                        summary = summaryOf(R.string.manga_save_location, R.string.downloads_wifi_only),
                        fragmentClass = DownloadsSettingsFragment::class.java,
                    )
                )
                add(
                    settingsItem(
                        key = "tracker",
                        iconRes = R.drawable.ic_feed,
                        title = getString(R.string.check_for_new_chapters),
                        summary = summaryOf(R.string.track_sources, R.string.notifications_settings),
                        fragmentClass = org.skepsun.kototoro.settings.tracker.TrackerSettingsFragment::class.java,
                    )
                )
                add(
                    settingsItem(
                        key = "services",
                        iconRes = R.drawable.ic_services,
                        title = getString(R.string.services),
                        summary = summaryOf(R.string.sync_settings, R.string.suggestions, R.string.tracking),
                        fragmentClass = ServicesSettingsFragment::class.java,
                    )
                )
                add(
                    settingsItem(
                        key = "about",
                        iconRes = R.drawable.ic_info_outline,
                        title = getString(R.string.about),
                        summary = getString(R.string.app_version, BuildConfig.VERSION_NAME),
                        fragmentClass = org.skepsun.kototoro.settings.about.AboutSettingsFragment::class.java,
                    )
                )
                buildOptionalDebugItem()?.let(::add)
            },
        )

        return listOf(coreSection, servicesSection)
    }

    private fun buildOptionalDebugItem(): SettingsRootItem? {
        val fragmentClass = runCatching {
            FragmentFactory.loadFragmentClass(
                requireActivity().classLoader,
                "org.skepsun.kototoro.settings.DebugSettingsFragment",
            )
        }.getOrNull() ?: return null

        return settingsItem(
            key = "debug",
            iconRes = R.drawable.ic_debug,
            title = getString(R.string.debug),
            summary = getString(R.string.debug),
            fragmentClass = fragmentClass,
        )
    }

    private fun settingsItem(
        key: String,
        iconRes: Int,
        title: String,
        summary: String,
        fragmentClass: Class<out Fragment>,
    ): SettingsRootItem {
        return SettingsRootItem(
            key = key,
            iconRes = iconRes,
            title = title,
            summary = summary,
            onClick = {
                (activity as? SettingsActivity)?.openFragment(fragmentClass, null, true)
            },
        )
    }

    private fun summaryOf(vararg items: Int): String {
        return items.joinToString { getString(it) }
    }
}
