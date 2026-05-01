package org.skepsun.kototoro.settings.discord

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
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
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as ComposeView).setContent {
            KototoroTheme {
                DiscordSettingsRoute(
                    settings = appSettings,
                    viewModel = viewModel,
                    onTokenClick = ::openSignIn,
                    onLogoutClick = ::logoutDiscord,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
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
        private const val DISCORD_ORIGIN = "https://discord.com"
        private const val DISCORD_WWW_ORIGIN = "https://www.discord.com"
    }
}

@Composable
fun DiscordSettingsRoute(
    settings: AppSettings,
    viewModel: DiscordSettingsViewModel,
    onTokenClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    val context = LocalContext.current
    val tokenStatePair by viewModel.tokenState.collectAsStateWithLifecycle()
    val (state, token) = tokenStatePair

    val tokenSummary = when (state) {
        TokenState.EMPTY -> null
        TokenState.REQUIRED -> null
        TokenState.INVALID -> token?.let { context.getString(R.string.invalid_token, it) }
        TokenState.VALID -> token
        TokenState.CHECKING -> context.getString(R.string.loading_)
    }

    DiscordSettingsScreen(
        settings = settings,
        tokenSummary = tokenSummary,
        isLogoutVisible = settings.isDiscordRpcEnabled && state == TokenState.VALID,
        onTokenClick = onTokenClick,
        onLogoutClick = onLogoutClick,
    )
}
