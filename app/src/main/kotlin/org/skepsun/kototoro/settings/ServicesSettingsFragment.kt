package org.skepsun.kototoro.settings

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
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
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.scrobbling.common.ui.ScrobblerAuthHelper
import org.skepsun.kototoro.settings.compose.ServicesSettingsScreen
import org.skepsun.kototoro.settings.compose.ServicesSettingsUiState
import org.skepsun.kototoro.settings.compose.ServicesTrackingItem
import org.skepsun.kototoro.settings.discord.DiscordSettingsFragment
import org.skepsun.kototoro.settings.userdata.BackupsSettingsFragment
import org.skepsun.kototoro.tracking.animeoffline.data.AnimeOfflineRepository
import org.skepsun.kototoro.tracking.animeoffline.work.AnimeOfflineUpdateWorker

@AndroidEntryPoint
class ServicesSettingsFragment : Fragment() {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var scrobblerAuthHelper: ScrobblerAuthHelper

	@Inject
	lateinit var animeOfflineRepository: AnimeOfflineRepository

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
			val suggestionsEnabled = settings.observeAsState(AppSettings.KEY_SUGGESTIONS) { isSuggestionsEnabled }.value
			val isRelatedContentEnabled =
				settings.observeAsState(AppSettings.KEY_RELATED_MANGA) { isRelatedContentEnabled }.value
			val isStatsEnabled = settings.observeAsState(AppSettings.KEY_STATS_ENABLED) { isStatsEnabled }.value
			val isReadingTimeEstimationEnabled =
				settings.observeAsState(AppSettings.KEY_READING_TIME) { isReadingTimeEstimationEnabled }.value
			val refreshKey = resumeTick.collectAsStateWithLifecycle().value
			val snackbarHostState = remember { SnackbarHostState() }
			val coroutineScope = rememberCoroutineScope()
			var pendingAuthService by remember { mutableStateOf<ScrobblerService?>(null) }

			val shikimoriSummary = rememberScrobblerSummary(ScrobblerService.SHIKIMORI, refreshKey)
			val anilistSummary = rememberScrobblerSummary(ScrobblerService.ANILIST, refreshKey)
			val malSummary = rememberScrobblerSummary(ScrobblerService.MAL, refreshKey)
			val kitsuSummary = rememberScrobblerSummary(ScrobblerService.KITSU, refreshKey)
			val bangumiSummary = rememberScrobblerSummary(ScrobblerService.BANGUMI, refreshKey)
			val mangaUpdatesSummary = rememberScrobblerSummary(ScrobblerService.MANGAUPDATES, refreshKey)
			val animeOfflineSummary = rememberAnimeOfflineSummary(refreshKey)

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
				trackingItems = listOf(
					ServicesTrackingItem(
						service = ScrobblerService.ANILIST,
						title = getString(ScrobblerService.ANILIST.titleResId),
						summary = anilistSummary,
						iconRes = ScrobblerService.ANILIST.iconResId,
					),
					ServicesTrackingItem(
						service = ScrobblerService.KITSU,
						title = getString(ScrobblerService.KITSU.titleResId),
						summary = kitsuSummary,
						iconRes = ScrobblerService.KITSU.iconResId,
					),
					ServicesTrackingItem(
						service = ScrobblerService.MAL,
						title = getString(ScrobblerService.MAL.titleResId),
						summary = malSummary,
						iconRes = ScrobblerService.MAL.iconResId,
					),
					ServicesTrackingItem(
						service = ScrobblerService.SHIKIMORI,
						title = getString(ScrobblerService.SHIKIMORI.titleResId),
						summary = shikimoriSummary,
						iconRes = ScrobblerService.SHIKIMORI.iconResId,
					),
					ServicesTrackingItem(
						service = ScrobblerService.BANGUMI,
						title = getString(ScrobblerService.BANGUMI.titleResId),
						summary = bangumiSummary,
						iconRes = ScrobblerService.BANGUMI.iconResId,
					),
					ServicesTrackingItem(
						service = ScrobblerService.MANGAUPDATES,
						title = getString(ScrobblerService.MANGAUPDATES.titleResId),
						summary = mangaUpdatesSummary,
						iconRes = ScrobblerService.MANGAUPDATES.iconResId,
					),
				),
			)

			KototoroTheme {
				ServicesSettingsScreen(
					servicesTitle = getString(R.string.services),
					trackingTitle = getString(R.string.tracking),
					state = state,
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
					onSyncSettingsClick = {
						(activity as? SettingsActivity)?.openFragment(BackupsSettingsFragment::class.java, null, false)
					},
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
					onTrackingServiceClick = { service ->
						if (scrobblerAuthHelper.isAuthorized(service)) {
							router.openScrobblerSettings(service)
						} else {
							pendingAuthService = service
						}
					},
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
		resumeTick.update { it + 1 }
	}

	@Composable
	private fun rememberScrobblerSummary(
		service: ScrobblerService,
		refreshKey: Int,
	): String {
		return produceState(
			initialValue = getScrobblerSummaryPlaceholder(service),
			key1 = refreshKey,
			key2 = service,
		) {
			value = loadScrobblerSummary(service)
		}.value
	}

	private fun getScrobblerSummaryPlaceholder(service: ScrobblerService): String {
		if (!scrobblerAuthHelper.isAuthorized(service)) {
			return getString(R.string.disabled)
		}
		val username = scrobblerAuthHelper.getCachedUser(service)?.nickname
		return if (username.isNullOrEmpty()) {
			getString(R.string.loading_)
		} else {
			getString(R.string.logged_in_as, username)
		}
	}

	@Composable
	private fun rememberAnimeOfflineSummary(refreshKey: Int): String {
		return produceState(
			initialValue = getString(R.string.loading_),
			key1 = refreshKey,
		) {
			value = loadAnimeOfflineSummary()
		}.value
	}

	private suspend fun loadScrobblerSummary(service: ScrobblerService): String {
		if (!scrobblerAuthHelper.isAuthorized(service)) {
			return getString(R.string.disabled)
		}
		val username = scrobblerAuthHelper.getCachedUser(service)?.nickname
		if (!username.isNullOrEmpty()) {
			return getString(R.string.logged_in_as, username)
		}
		return withContext(Dispatchers.Default) {
			runCatching {
				val user = scrobblerAuthHelper.getUser(service)
				getString(R.string.logged_in_as, user.nickname)
			}.getOrElse {
				it.printStackTraceDebug()
				it.getDisplayMessage(resources)
			}
		}
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
