package org.skepsun.kototoro.settings

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.util.ext.getDisplayMessage
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.settings.compose.ServicesSettingsScreen
import org.skepsun.kototoro.settings.compose.ServicesSettingsUiState
import org.skepsun.kototoro.settings.discord.DiscordSettingsFragment
import org.skepsun.kototoro.tracking.animeoffline.data.AnimeOfflineRepository
import org.skepsun.kototoro.tracking.animeoffline.work.AnimeOfflineUpdateWorker

@AndroidEntryPoint
class ServicesSettingsFragment : Fragment() {

    @Inject
    lateinit var settings: AppSettings

    @Inject
    lateinit var animeOfflineRepository: AnimeOfflineRepository

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
            val suggestionsEnabled = settings.observeAsState(AppSettings.KEY_SUGGESTIONS) { isSuggestionsEnabled }.value
            val isRelatedContentEnabled =
                settings.observeAsState(AppSettings.KEY_RELATED_MANGA) { isRelatedContentEnabled }.value
            val isStatsEnabled = settings.observeAsState(AppSettings.KEY_STATS_ENABLED) { isStatsEnabled }.value
            val isReadingTimeEstimationEnabled =
                settings.observeAsState(AppSettings.KEY_READING_TIME) { isReadingTimeEstimationEnabled }.value
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()
            val animeOfflineSummary = rememberAnimeOfflineSummary()

            val state = ServicesSettingsUiState(
                suggestionsSummary = if (suggestionsEnabled) {
                    getString(R.string.enabled)
                } else {
                    getString(R.string.disabled)
                },
                animeOfflineSummary = animeOfflineSummary,
                isRelatedContentEnabled = isRelatedContentEnabled,
                isStatsEnabled = isStatsEnabled,
                isReadingTimeEstimationEnabled = isReadingTimeEstimationEnabled,
            )

            KototoroTheme {
                ServicesSettingsScreen(
                    servicesTitle = getString(R.string.services),
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onAnimeOfflineClick = {
                        val appContext = requireContext().applicationContext
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                AnimeOfflineUpdateWorker.enqueue(appContext, force = true)
                            }
                            snackbarHostState.showSnackbar(getString(R.string.anime_offline_database_update_started))
                        }
                    },
                    onSuggestionsClick = {
                        (activity as? SettingsActivity)?.openFragment(SuggestionsSettingsFragment::class.java, null, false)
                    },
                    onRelatedContentChange = { settings.isRelatedContentEnabled = it },
                    onStatsClick = { router.openStatistic() },
                    onStatsEnabledChange = { settings.isStatsEnabled = it },
                    onReadingTimeChange = { settings.isReadingTimeEstimationEnabled = it },
                    onDiscordSettingsClick = {
                        (activity as? SettingsActivity)?.openFragment(DiscordSettingsFragment::class.java, null, false)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.services))
    }

    @Composable
    private fun rememberAnimeOfflineSummary(): String {
        return produceState(
            initialValue = getString(R.string.loading_),
        ) {
            value = loadAnimeOfflineSummary()
        }.value
    }

    private suspend fun loadAnimeOfflineSummary(): String = withContext(Dispatchers.IO) {
        runCatching {
            val status = animeOfflineRepository.readStatus()
            when {
                status.isInstalled && !status.releaseTag.isNullOrBlank() && status.downloadedAt > 0L -> {
                    getString(
                        R.string.anime_offline_database_summary_installed,
                        status.releaseTag,
                        DateUtils.getRelativeTimeSpanString(status.downloadedAt),
                    )
                }
                status.isInstalled -> {
                    getString(R.string.anime_offline_database_summary_installed_unknown)
                }
                status.lastCheckedAt > 0L -> {
                    getString(
                        R.string.anime_offline_database_summary_not_installed_checked,
                        DateUtils.getRelativeTimeSpanString(status.lastCheckedAt),
                    )
                }
                else -> {
                    getString(R.string.anime_offline_database_summary_not_installed)
                }
            }
        }.getOrElse {
            it.printStackTraceDebug()
            it.getDisplayMessage(resources)
        }
    }
}
