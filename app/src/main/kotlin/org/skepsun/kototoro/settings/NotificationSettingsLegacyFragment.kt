package org.skepsun.kototoro.settings

import android.media.RingtoneManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.settings.compose.NotificationSettingsScreen
import org.skepsun.kototoro.settings.compose.NotificationSettingsUiState
import org.skepsun.kototoro.settings.utils.RingtonePickContract
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NotificationSettingsLegacyFragment : Fragment() {

    @Inject
    lateinit var settings: AppSettings

    private val ringtonePickContract = registerForActivityResult(
        RingtonePickContract(R.string.notification_sound),
    ) { uri ->
        settings.notificationSound = uri ?: return@registerForActivityResult
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
            val isTrackerNotificationsEnabled = settings.observeAsState(
                AppSettings.KEY_TRACKER_NOTIFICATIONS,
            ) { isTrackerNotificationsEnabled }.value
            val notificationSound = settings.observeAsState(
                AppSettings.KEY_NOTIFICATIONS_SOUND,
            ) { notificationSound }.value
            val notificationVibrate = settings.observeAsState(
                AppSettings.KEY_NOTIFICATIONS_VIBRATE,
            ) { notificationVibrate }.value
            val notificationLight = settings.observeAsState(
                AppSettings.KEY_NOTIFICATIONS_LIGHT,
            ) { notificationLight }.value
            val snackbarHostState = remember { SnackbarHostState() }
            val ringtoneSummary = RingtoneManager.getRingtone(requireContext(), notificationSound)
                ?.getTitle(requireContext())
                ?: getString(R.string.silent)

            val state = NotificationSettingsUiState(
                isTrackerNotificationsEnabled = isTrackerNotificationsEnabled,
                ringtoneSummary = ringtoneSummary,
                isNotificationVibrateEnabled = notificationVibrate,
                isNotificationLightEnabled = notificationLight,
                isNotificationsInfoVisible = !isTrackerNotificationsEnabled,
            )

            KototoroTheme {
                NotificationSettingsScreen(
                    notificationsTitle = getString(R.string.notifications),
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onTrackerNotificationsEnabledChange = { settings.isTrackerNotificationsEnabled = it },
                    onNotificationSoundClick = {
                        ringtonePickContract.launch(settings.notificationSound)
                    },
                    onNotificationVibrateChange = { settings.notificationVibrate = it },
                    onNotificationLightChange = { settings.notificationLight = it },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.notifications))
    }
}
