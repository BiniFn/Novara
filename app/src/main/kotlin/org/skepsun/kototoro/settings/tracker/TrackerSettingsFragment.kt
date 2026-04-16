package org.skepsun.kototoro.settings.tracker

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.TrackerDownloadStrategy
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.util.ext.powerManager
import org.skepsun.kototoro.settings.NotificationSettingsLegacyFragment
import org.skepsun.kototoro.settings.SettingsActivity
import org.skepsun.kototoro.settings.compose.SettingsChoiceOption
import org.skepsun.kototoro.settings.compose.TrackerSettingsScreen
import org.skepsun.kototoro.settings.compose.TrackerSettingsUiState
import org.skepsun.kototoro.tracker.ui.debug.TrackerDebugActivity
import org.skepsun.kototoro.tracker.work.TrackerNotificationHelper

@AndroidEntryPoint
class TrackerSettingsFragment : Fragment() {

    @Inject
    lateinit var settings: AppSettings

    @Inject
    lateinit var notificationHelper: TrackerNotificationHelper

    private val viewModel by viewModels<TrackerSettingsViewModel>()
    private val dozeTick = MutableStateFlow(0)
    private val notificationTick = MutableStateFlow(0)

    private val ignoreDozeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        dozeTick.update { tick -> tick + 1 }
    }

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
            val isTrackerEnabled = settings.observeAsState(AppSettings.KEY_TRACKER_ENABLED) { isTrackerEnabled }.value
            val isTrackerWifiOnly = settings.observeAsState(AppSettings.KEY_TRACKER_WIFI_ONLY) { isTrackerWifiOnly }.value
            val trackerFrequencyFactor =
                settings.observeAsState(AppSettings.KEY_TRACKER_FREQUENCY) { trackerFrequencyFactor }.value
            val trackSources = settings.observeAsState(AppSettings.KEY_TRACK_SOURCES) { trackSources }.value
            val trackerDownloadStrategy =
                settings.observeAsState(AppSettings.KEY_TRACKER_DOWNLOAD) { trackerDownloadStrategy }.value
            val categoriesCount = viewModel.categoriesCount.collectAsStateWithLifecycle().value
            val dozeRefreshKey = dozeTick.collectAsStateWithLifecycle().value
            val notificationRefreshKey = notificationTick.collectAsStateWithLifecycle().value
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()

            val frequencyOptions = listOf(
                SettingsChoiceOption(-1f, getString(R.string.manual)),
                SettingsChoiceOption(0.4f, getString(R.string.less_frequently)),
                SettingsChoiceOption(1f, getString(R.string.system_default)),
                SettingsChoiceOption(2f, getString(R.string.more_frequently)),
            )
            val trackSourcesOptions = listOf(
                SettingsChoiceOption(AppSettings.TRACK_FAVOURITES, getString(R.string.favourites)),
                SettingsChoiceOption(AppSettings.TRACK_HISTORY, getString(R.string.history)),
            )
            val downloadStrategyOptions = listOf(
                SettingsChoiceOption(TrackerDownloadStrategy.DISABLED, getString(R.string.never)),
                SettingsChoiceOption(
                    TrackerDownloadStrategy.DOWNLOADED,
                    getString(R.string.manga_with_downloaded_chapters),
                ),
            )

            val categoriesSummary = categoriesCount?.let {
                getString(R.string.enabled_d_of_d, it[0], it[1])
            } ?: getString(R.string.loading_)
            val notificationsSummary = if (notificationRefreshKey >= 0 && notificationHelper.getAreNotificationsEnabled()) {
                getString(R.string.show_notification_new_chapters_on)
            } else {
                getString(R.string.show_notification_new_chapters_off)
            }

            val state = TrackerSettingsUiState(
                isTrackerEnabled = isTrackerEnabled,
                isTrackerWifiOnly = isTrackerWifiOnly,
                trackerFrequencyFactor = trackerFrequencyFactor,
                trackSources = trackSources,
                categoriesSummary = categoriesSummary,
                isCategoriesEnabled = isTrackerEnabled && AppSettings.TRACK_FAVOURITES in trackSources,
                notificationsSummary = notificationsSummary,
                trackerDownloadStrategy = trackerDownloadStrategy,
                isDozeIgnoreVisible = isDozeIgnoreAvailable(dozeRefreshKey),
            )

            KototoroTheme {
                TrackerSettingsScreen(
                    trackingTitle = getString(R.string.check_for_new_chapters),
                    debugTitle = getString(R.string.debug),
                    state = state,
                    snackbarHostState = snackbarHostState,
                    frequencyOptions = frequencyOptions,
                    trackSourcesOptions = trackSourcesOptions,
                    downloadStrategyOptions = downloadStrategyOptions,
                    emptyTrackSourcesText = getString(R.string.dont_check),
                    onTrackerEnabledChange = { settings.isTrackerEnabled = it },
                    onTrackerWifiOnlyChange = { settings.isTrackerWifiOnly = it },
                    onTrackerFrequencyChange = { settings.trackerFrequencyFactor = it },
                    onTrackSourcesChange = { settings.trackSources = it },
                    onTrackCategoriesClick = { router.showTrackerCategoriesConfigSheet() },
                    onNotificationsSettingsClick = {
                        openNotificationsSettings(
                            onUnsupported = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(getString(R.string.operation_not_supported))
                                }
                            },
                        )
                    },
                    onTrackerDownloadStrategyChange = { settings.trackerDownloadStrategy = it },
                    onTrackerDebugClick = {
                        startActivity(Intent(requireContext(), TrackerDebugActivity::class.java))
                    },
                    onIgnoreDozeClick = {
                        if (!startIgnoreDozeActivity()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(getString(R.string.operation_not_supported))
                            }
                        }
                    },
                    onTrackerWarningClick = {
                        openTrackerWarning(
                            onUnsupported = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(getString(R.string.operation_not_supported))
                                }
                            },
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.check_for_new_chapters))
        dozeTick.update { it + 1 }
        notificationTick.update { it + 1 }
    }

    private fun openNotificationsSettings(onUnsupported: () -> Unit) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                if (!startActivitySafe(intent)) {
                    onUnsupported()
                }
            }

            !notificationHelper.getAreNotificationsEnabled() -> {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", requireContext().packageName, null))
                if (!startActivitySafe(intent)) {
                    onUnsupported()
                }
            }

            else -> {
                (activity as? SettingsActivity)?.openFragment(NotificationSettingsLegacyFragment::class.java, null, false)
            }
        }
    }

    private fun openTrackerWarning(onUnsupported: () -> Unit) {
        val intent = Intent(Intent.ACTION_VIEW, "https://dontkillmyapp.com/".toUri())
        if (!startActivitySafe(intent)) {
            onUnsupported()
        }
    }

    private fun startIgnoreDozeActivity(): Boolean {
        val context = context ?: return false
        val packageName = context.packageName
        val powerManager = context.powerManager ?: return false
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            return false
        }
        return try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:$packageName".toUri(),
            )
            ignoreDozeLauncher.launch(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun isDozeIgnoreAvailable(refreshKey: Int): Boolean {
        refreshKey
        val context = context ?: return false
        val powerManager = context.powerManager ?: return false
        return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun startActivitySafe(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
