package org.skepsun.kototoro.settings.sources

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.TriStateOption
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.util.ext.getQuantityStringSafe
import org.skepsun.kototoro.explore.data.SourcesSortOrder
import org.skepsun.kototoro.settings.SettingsActivity
import org.skepsun.kototoro.settings.compose.SettingsChoiceOption
import org.skepsun.kototoro.settings.compose.SourcesSettingsScreen
import org.skepsun.kototoro.settings.compose.SourcesSettingsUiState
import org.skepsun.kototoro.settings.sources.extensions.ExtensionsRootFragment
import org.skepsun.kototoro.settings.sources.jsonsource.JsonSourcesRootFragment
import org.skepsun.kototoro.settings.sources.manage.SourcesManageFragment
import javax.inject.Inject

@AndroidEntryPoint
class SourcesSettingsFragment : Fragment() {

    @Inject
    lateinit var settings: AppSettings

    private val viewModel by viewModels<SourcesSettingsViewModel>()

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
            val enabledSourcesCount = viewModel.enabledSourcesCount.collectAsStateWithLifecycle().value
            val builtInSourcesCount = viewModel.builtInSourcesCount.collectAsStateWithLifecycle().value
            val jsonSourcesCount = viewModel.jsonSourcesCount.collectAsStateWithLifecycle().value
            val extensionsSummary = viewModel.extensionsSummary.collectAsStateWithLifecycle().value
            val isLinksEnabled = viewModel.isLinksEnabled.collectAsStateWithLifecycle().value
            val sourcesSortOrder = settings.observeAsState(AppSettings.KEY_SOURCES_ORDER) { sourcesSortOrder }.value
            val isShowSourceOnCards =
                settings.observeAsState(AppSettings.KEY_SHOW_SOURCE_ON_CARDS) { isShowSourceOnCards }.value
            val isSourcesGridMode = settings.observeAsState(AppSettings.KEY_SOURCES_GRID) { isSourcesGridMode }.value
            val isSourcesGroupedByLanguage =
                settings.observeAsState(AppSettings.KEY_SOURCES_GROUPED_BY_LANGUAGE) { isSourcesGroupedByLanguage }.value
            val jarPriorityOrder = settings.observeAsState(AppSettings.KEY_JAR_PRIORITY_ORDER) { jarPriorityOrder }.value
            val isAllSourcesEnabled =
                settings.observeAsState(AppSettings.KEY_SOURCES_ENABLED_ALL) { isAllSourcesEnabled }.value
            val isShowBrokenSources =
                settings.observeAsState(AppSettings.KEY_SHOW_BROKEN_SOURCES) { isShowBrokenSources }.value
            val isNsfwContentDisabled =
                settings.observeAsState(AppSettings.KEY_DISABLE_NSFW) { isNsfwContentDisabled }.value
            val isHistoryExcludeNsfw =
                settings.observeAsState(AppSettings.KEY_HISTORY_EXCLUDE_NSFW) { isHistoryExcludeNsfw }.value
            val isFavouritesExcludeNsfw =
                settings.observeAsState(AppSettings.KEY_FAVOURITES_EXCLUDE_NSFW) { isFavouritesExcludeNsfw }.value
            val isTrackerNsfwDisabled =
                settings.observeAsState(AppSettings.KEY_TRACKER_NO_NSFW) { isTrackerNsfwDisabled }.value
            val isSuggestionsExcludeNsfw =
                settings.observeAsState(AppSettings.KEY_SUGGESTIONS_EXCLUDE_NSFW) { isSuggestionsExcludeNsfw }.value
            val incognitoModeForNsfw =
                settings.observeAsState(AppSettings.KEY_INCOGNITO_NSFW) { incognitoModeForNsfw }.value
            val isTagsWarningsEnabled =
                settings.observeAsState(AppSettings.KEY_TAGS_WARNINGS) { isTagsWarningsEnabled }.value
            val isMirrorSwitchingEnabled =
                settings.observeAsState(AppSettings.KEY_MIRROR_SWITCHING) { isMirrorSwitchingEnabled }.value
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()

            val sortOrderOptions = SourcesSortOrder.entries.map {
                SettingsChoiceOption(it, getString(it.titleResId))
            }
            val incognitoOptions = listOf(
                SettingsChoiceOption(TriStateOption.ENABLED, getString(R.string.enable)),
                SettingsChoiceOption(TriStateOption.ASK, getString(R.string.ask_every_time)),
                SettingsChoiceOption(TriStateOption.DISABLED, getString(R.string.disable)),
            )

            val state = SourcesSettingsUiState(
                sourcesSortOrder = sourcesSortOrder,
                isShowSourceOnCards = isShowSourceOnCards,
                isSourcesGridMode = isSourcesGridMode,
                isSourcesGroupedByLanguage = isSourcesGroupedByLanguage,
                remoteSourcesSummary = if (enabledSourcesCount >= 0) {
                    resources.getQuantityStringSafe(R.plurals.items, enabledSourcesCount, enabledSourcesCount)
                } else {
                    getString(R.string.loading_)
                },
                builtInSourcesSummary = getString(R.string.available_sources_count, builtInSourcesCount),
                jsonSourcesSummary = getString(R.string.available_sources_count, jsonSourcesCount),
                extensionsSummary = extensionsSummary,
                jarPriorityOrder = jarPriorityOrder,
                isAllSourcesEnabled = isAllSourcesEnabled,
                isShowBrokenSources = isShowBrokenSources,
                isNsfwContentDisabled = isNsfwContentDisabled,
                isHistoryExcludeNsfw = isHistoryExcludeNsfw,
                isFavouritesExcludeNsfw = isFavouritesExcludeNsfw,
                isTrackerNsfwDisabled = isTrackerNsfwDisabled,
                isSuggestionsExcludeNsfw = isSuggestionsExcludeNsfw,
                incognitoModeForNsfw = incognitoModeForNsfw,
                isTagsWarningsEnabled = isTagsWarningsEnabled,
                isMirrorSwitchingEnabled = isMirrorSwitchingEnabled,
                isHandleLinksEnabled = isLinksEnabled,
            )

            KototoroTheme {
                SourcesSettingsScreen(
                    overviewTitle = getString(R.string.remote_sources),
                    remoteSourcesTitle = getString(R.string.remote_sources),
                    adultFilteringTitle = getString(R.string.adult_content_filtering),
                    moreTitle = getString(R.string.more),
                    state = state,
                    snackbarHostState = snackbarHostState,
                    sortOrderOptions = sortOrderOptions,
                    incognitoOptions = incognitoOptions,
                    onSourcesSortOrderChange = { settings.sourcesSortOrder = it },
                    onShowSourceOnCardsChange = { settings.isShowSourceOnCards = it },
                    onSourcesGridModeChange = { settings.isSourcesGridMode = it },
                    onSourcesGroupedByLanguageChange = { settings.isSourcesGroupedByLanguage = it },
                    onSetupWizardClick = { router.showWelcomeSheet() },
                    onManageSourcesClick = {
                        (activity as? SettingsActivity)?.openFragment(SourcesManageFragment::class.java, null, false)
                    },
                    onBuiltInSourcesClick = { router.openSourcesCatalog() },
                    onJsonSourcesClick = {
                        (activity as? SettingsActivity)?.openFragment(JsonSourcesRootFragment::class.java, null, false)
                    },
                    onExtensionsClick = {
                        (activity as? SettingsActivity)?.openFragment(ExtensionsRootFragment::class.java, null, false)
                    },
                    onJarPriorityOrderChange = { settings.jarPriorityOrder = it },
                    onAllSourcesEnabledChange = { settings.isAllSourcesEnabled = it },
                    onShowBrokenSourcesChange = { settings.isShowBrokenSources = it },
                    onNsfwContentDisabledChange = { settings.isNsfwContentDisabled = it },
                    onHistoryExcludeNsfwChange = { settings.isHistoryExcludeNsfw = it },
                    onFavouritesExcludeNsfwChange = { settings.isFavouritesExcludeNsfw = it },
                    onTrackerNsfwDisabledChange = { settings.isTrackerNsfwDisabled = it },
                    onSuggestionsExcludeNsfwChange = { settings.isSuggestionsExcludeNsfw = it },
                    onIncognitoModeForNsfwChange = { settings.incognitoModeForNsfw = it },
                    onTagsWarningsEnabledChange = { settings.isTagsWarningsEnabled = it },
                    onMirrorSwitchingChange = { settings.isMirrorSwitchingEnabled = it },
                    onHandleLinksEnabledChange = { enabled ->
                        runCatching {
                            viewModel.setLinksEnabled(enabled)
                        }.onFailure {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(getString(R.string.operation_not_supported))
                            }
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.remote_sources))
        viewModel.refreshLinksEnabled()
    }
}
