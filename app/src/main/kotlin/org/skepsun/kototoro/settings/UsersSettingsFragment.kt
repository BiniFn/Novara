package org.skepsun.kototoro.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.util.ext.getDisplayMessage
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.settings.compose.SettingsChoiceOption
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerUserProfile
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerUserStats
import org.skepsun.kototoro.scrobbling.common.ui.ScrobblerAuthHelper
import org.skepsun.kototoro.settings.compose.UserTrackingAccountItem
import org.skepsun.kototoro.settings.compose.UsersSettingsScreen
import org.skepsun.kototoro.settings.compose.UsersSettingsUiState
import org.skepsun.kototoro.settings.users.TrackingUserAccountSummaryProvider
import org.skepsun.kototoro.settings.userdata.BackupsSettingsFragment
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteDiscoveryService

@AndroidEntryPoint
class UsersSettingsFragment : Fragment() {

    @Inject
    lateinit var settings: AppSettings

    @Inject
    lateinit var scrobblerAuthHelper: ScrobblerAuthHelper

    @Inject
    lateinit var trackingUserAccountSummaryProvider: TrackingUserAccountSummaryProvider

    @Inject
    lateinit var trackingDiscoveryService: TrackingSiteDiscoveryService

    private val resumeTick = MutableStateFlow(0)

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
            val refreshKey = resumeTick.collectAsStateWithLifecycle().value
            val preferredTrackingSite = settings.observeAsState(AppSettings.KEY_PREFERRED_TRACKING_SITE) {
                preferredTrackingSite
            }.value
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()
            var pendingAuthService by remember { mutableStateOf<ScrobblerService?>(null) }
            val preferredTrackingSiteOptions = remember {
                buildPreferredTrackingSiteOptions()
            }
            val accounts = produceState(
                initialValue = buildPlaceholderItems(),
                key1 = refreshKey,
            ) {
                value = loadAccountItems()
            }.value

            KototoroTheme {
                UsersSettingsScreen(
                    trackingAccountsTitle = getString(R.string.tracking_accounts),
                    state = UsersSettingsUiState(
                        accounts = accounts,
                        preferredTrackingSite = preferredTrackingSite,
                        preferredTrackingSiteOptions = preferredTrackingSiteOptions,
                    ),
                    snackbarHostState = snackbarHostState,
                    pendingAuthService = pendingAuthService,
                    onDismissAuthPrompt = { pendingAuthService = null },
                    onConfirmAuthPrompt = { service ->
                        pendingAuthService = null
                        scrobblerAuthHelper.startAuth(requireContext(), service).onFailure {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(it.getDisplayMessage(resources))
                            }
                        }
                    },
                    onPreferredTrackingSiteChange = { service ->
                        settings.preferredTrackingSite = service
                    },
                    onSyncSettingsClick = {
                        (activity as? SettingsActivity)?.openFragment(BackupsSettingsFragment::class.java, null, false)
                    },
                    onTrackingServiceClick = { service ->
                        if (scrobblerAuthHelper.isAuthorized(service)) {
                            requireActivity().router.openScrobblerSettings(service)
                        } else {
                            pendingAuthService = service
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.users))
        resumeTick.update { it + 1 }
    }

    private fun buildPlaceholderItems(): List<UserTrackingAccountItem> {
        return TRACKING_SERVICES.map { service ->
            val cachedUser = scrobblerAuthHelper.getCachedUser(service)
            UserTrackingAccountItem(
                service = service,
                title = getString(service.titleResId),
                summary = when {
                    !scrobblerAuthHelper.isAuthorized(service) -> getString(R.string.not_signed_in)
                    cachedUser?.nickname.isNullOrBlank() -> getString(R.string.loading_)
                    else -> cachedUser?.nickname.orEmpty()
                },
                statsSummary = if (scrobblerAuthHelper.isAuthorized(service)) {
                    getString(R.string.statistics_pending_short)
                } else {
                    null
                },
                avatarUrl = cachedUser?.avatar,
                iconRes = service.iconResId,
            )
        }
    }

    private suspend fun loadAccountItems(): List<UserTrackingAccountItem> = withContext(Dispatchers.IO) {
        TRACKING_SERVICES.map { service ->
            if (!scrobblerAuthHelper.isAuthorized(service)) {
                return@map UserTrackingAccountItem(
                    service = service,
                    title = getString(service.titleResId),
                    summary = getString(R.string.not_signed_in),
                    statsSummary = null,
                    avatarUrl = null,
                    iconRes = service.iconResId,
                )
            }
            runCatching {
                val profile = trackingUserAccountSummaryProvider.load(service)
                buildAccountItem(service, profile)
            }.getOrElse {
                it.printStackTraceDebug()
                val cachedUser = scrobblerAuthHelper.getCachedUser(service)
                UserTrackingAccountItem(
                    service = service,
                    title = getString(service.titleResId),
                    summary = cachedUser?.nickname ?: it.getDisplayMessage(resources),
                    statsSummary = getString(R.string.statistics_pending_short),
                    avatarUrl = cachedUser?.avatar,
                    iconRes = service.iconResId,
                )
            }
        }
    }

    private fun buildAccountItem(
        service: ScrobblerService,
        profile: ScrobblerUserProfile,
    ): UserTrackingAccountItem {
        return UserTrackingAccountItem(
            service = service,
            title = getString(service.titleResId),
            summary = profile.user.nickname,
            statsSummary = formatStatsSummary(service, profile.stats),
            avatarUrl = profile.user.avatar,
            iconRes = service.iconResId,
        )
    }

    private fun formatStatsSummary(
        service: ScrobblerService,
        stats: ScrobblerUserStats?,
    ): String {
        if (stats == null) {
            return getString(R.string.statistics_pending_short)
        }
        val parts = when (service) {
            ScrobblerService.ANILIST,
            ScrobblerService.MAL,
            ScrobblerService.KITSU,
            ScrobblerService.BANGUMI,
            ScrobblerService.MANGAUPDATES,
            ScrobblerService.SHIKIMORI,
            -> listOfNotNull(
                stats.animeCount?.let { getString(R.string.anime_count_short, it) },
                stats.mangaCount?.let { getString(R.string.manga_count_short, it) },
                stats.episodesWatched?.let { getString(R.string.episodes_watched_short, it) },
                stats.chaptersRead?.let { getString(R.string.chapters_read_short, it) },
                stats.animeMeanScore?.let { getString(R.string.mean_score_short, formatScore(it)) },
                stats.mangaMeanScore?.let { getString(R.string.mean_score_short, formatScore(it)) },
            )
            ScrobblerService.SIMKL -> listOfNotNull(
                stats.animeCount?.let { getString(R.string.anime_count_short, it) },
                stats.tvCount?.let { getString(R.string.tv_count_short, it) },
                stats.movieCount?.let { getString(R.string.movie_count_short, it) },
                stats.episodesWatched?.let { getString(R.string.episodes_watched_short, it) },
                stats.tvEpisodesWatched?.let { getString(R.string.tv_episodes_watched_short, it) },
            )
        }
        return parts.joinToString(separator = " · ").ifBlank {
            getString(R.string.statistics_pending_short)
        }
    }

    private fun formatScore(score: Double): String {
        return if (score % 1.0 == 0.0) {
            score.toInt().toString()
        } else {
            String.format("%.1f", score)
        }
    }

    private fun buildPreferredTrackingSiteOptions(): List<SettingsChoiceOption<ScrobblerService>> {
        return TRACKING_SERVICES
            .filter { service -> trackingDiscoveryService.getCapabilities(service).supportsTrending }
            .map { service ->
                SettingsChoiceOption(
                    value = service,
                    label = getString(service.titleResId),
                )
            }
    }

    private companion object {
        val TRACKING_SERVICES = listOf(
            ScrobblerService.ANILIST,
            ScrobblerService.KITSU,
            ScrobblerService.MAL,
            ScrobblerService.SHIKIMORI,
            ScrobblerService.BANGUMI,
            ScrobblerService.MANGAUPDATES,
            ScrobblerService.SIMKL,
        )
    }
}
