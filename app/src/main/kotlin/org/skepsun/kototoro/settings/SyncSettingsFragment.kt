package org.skepsun.kototoro.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentResultListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.settings.compose.SyncSettingsScreen
import org.skepsun.kototoro.sync.data.SyncSettings
import org.skepsun.kototoro.sync.ui.SyncHostDialogFragment
import javax.inject.Inject

@AndroidEntryPoint
class SyncSettingsFragment : Fragment(), FragmentResultListener {

    @Inject
    lateinit var appSettings: AppSettings

    @Inject
    lateinit var syncSettings: SyncSettings

    private val syncUrlFlow = MutableStateFlow("")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        syncUrlFlow.value = syncSettings.syncUrl ?: ""
        return ComposeView(requireContext()).apply {
            setContent {
                val syncUrl by syncUrlFlow.collectAsState()
                KototoroTheme {
                    SyncSettingsScreen(
                        settings = appSettings,
                        syncUrl = syncUrl,
                        onSyncUrlClick = {
                            SyncHostDialogFragment.show(childFragmentManager, null)
                        },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.sync_settings))
        childFragmentManager.setFragmentResultListener(SyncHostDialogFragment.REQUEST_KEY, viewLifecycleOwner, this)
    }

    override fun onFragmentResult(requestKey: String, result: Bundle) {
        syncUrlFlow.value = syncSettings.syncUrl ?: ""
    }
}
