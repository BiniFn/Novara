package org.skepsun.kototoro.settings.discord

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.scrobbling.discord.ui.DiscordAuthActivity
import org.skepsun.kototoro.settings.SettingsActivity
import org.skepsun.kototoro.settings.compose.DiscordSettingsScreen
import javax.inject.Inject

@AndroidEntryPoint
class DiscordSettingsFragment : Fragment() {

    private val viewModel by viewModels<DiscordSettingsViewModel>()

    @Inject
    lateinit var appSettings: AppSettings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val tokenStatePair by viewModel.tokenState.collectAsState(initial = Pair(TokenState.EMPTY, null))
                val (state, token) = tokenStatePair
                
                val tokenSummary = when (state) {
                    TokenState.EMPTY -> getString(R.string.discord_token_summary)
                    TokenState.REQUIRED -> getString(R.string.discord_token_summary)
                    TokenState.INVALID -> getString(R.string.invalid_token, token)
                    TokenState.VALID -> token
                    TokenState.CHECKING -> getString(R.string.loading_)
                }

                KototoroTheme {
                    DiscordSettingsScreen(
                        settings = appSettings,
                        tokenSummary = tokenSummary,
                        isLogoutVisible = appSettings.isDiscordRpcEnabled && state == TokenState.VALID,
                        onTokenClick = {
                            openSignIn()
                        },
                        onLogoutClick = {
                            logoutDiscord()
                        },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.discord))
    }

    private fun logoutDiscord() {
        appSettings.discordToken = null
        val webStorage = WebStorage.getInstance()
        runCatching { webStorage.deleteOrigin(DISCORD_ORIGIN) }
        runCatching { webStorage.deleteOrigin(DISCORD_WWW_ORIGIN) }

        val cookieManager = CookieManager.getInstance()
        cookieManager.removeSessionCookies(null)
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }

    private fun openSignIn() {
        activity?.run {
            startActivity(Intent(this, DiscordAuthActivity::class.java))
        }
    }

    private companion object {
        private const val KEY_DISCORD_LOGOUT = "discord_logout"
        private const val DISCORD_ORIGIN = "https://discord.com"
        private const val DISCORD_WWW_ORIGIN = "https://www.discord.com"
    }
}
