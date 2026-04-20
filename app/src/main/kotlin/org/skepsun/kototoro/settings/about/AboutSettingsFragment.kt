package org.skepsun.kototoro.settings.about

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.github.AppVersion
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.settings.SettingsActivity
import org.skepsun.kototoro.settings.compose.AboutSettingsScreen
import org.skepsun.kototoro.settings.about.changelog.ChangelogFragment
import javax.inject.Inject

@AndroidEntryPoint
class AboutSettingsFragment : Fragment() {

    private val viewModel by viewModels<AboutSettingsViewModel>()

    @Inject
    lateinit var appSettings: AppSettings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val isUpdateSupported by viewModel.isUpdateSupported.collectAsState(initial = false)
                val isLoading by viewModel.isLoading.collectAsState(initial = false)

                KototoroTheme {
                    AboutSettingsScreen(
                        settings = appSettings,
                        isUpdateSupported = isUpdateSupported,
                        isLoading = isLoading,
                        onCheckUpdate = {
                            viewModel.checkForUpdates()
                        },
                        onChangelogClick = {
                            (activity as? SettingsActivity)?.openFragment(ChangelogFragment::class.java, null, false)
                        },
                        onLinkClick = { key ->
                            when (key) {
                                AppSettings.KEY_LINK_WEBLATE -> openLink(R.string.url_weblate, getString(R.string.about_app_translation_summary))
                                AppSettings.KEY_LINK_GITHUB -> openLink(R.string.url_github, getString(R.string.source_code))
                                AppSettings.KEY_LINK_DONATE -> openLink(R.string.url_donate, getString(R.string.about_donate))
                                AppSettings.KEY_LINK_MANUAL -> openLink(R.string.url_user_manual, getString(R.string.user_manual))
                                AppSettings.KEY_LINK_DISCORD -> openLink(R.string.url_discord, getString(R.string.about_discord))
                            }
                        },
                        onCrashLogsClick = {
                            startActivity(org.skepsun.kototoro.settings.about.crashlog.CrashLogActivity.newIntent(requireContext()))
                        },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.about))
        viewModel.onUpdateAvailable.observeEvent(viewLifecycleOwner, ::onUpdateAvailable)
    }

    private fun onUpdateAvailable(version: AppVersion?) {
        if (version == null) {
            view?.let { Snackbar.make(it, R.string.no_update_available, Snackbar.LENGTH_SHORT).show() }
        } else {
            startActivity(Intent(requireContext(), AppUpdateActivity::class.java))
        }
    }

    private fun openLink(
        @StringRes url: Int,
        title: CharSequence?
    ): Boolean = if (router.openExternalBrowser(getString(url), title)) {
        true
    } else {
        view?.let { Snackbar.make(it, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show() }
        false
    }
}
