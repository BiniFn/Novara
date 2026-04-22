package org.skepsun.kototoro.main.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.backups.domain.BackupStartupCoordinator
import org.skepsun.kototoro.browser.AdListUpdateService
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.os.VoiceInputContract
import org.skepsun.kototoro.core.parser.ContentLinkResolver
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsFlow
import org.skepsun.kototoro.core.ui.BaseActivity
import org.skepsun.kototoro.core.ui.widgets.BottomNavState
import org.skepsun.kototoro.core.util.FoldableUtils
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.databinding.ActivityMainBinding
import org.skepsun.kototoro.details.service.ContentPrefetchService
import org.skepsun.kototoro.explore.data.SourcePresetsRepository
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.local.ui.LocalIndexUpdateService
import org.skepsun.kototoro.local.ui.LocalStorageCleanupWorker
import org.skepsun.kototoro.main.ui.compose.ComposeAppNavBarDelegator
import org.skepsun.kototoro.main.ui.compose.KototoroApp
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.search.domain.ALL_SEARCH_CONTENT_KINDS
import org.skepsun.kototoro.search.domain.SearchContentKind
import org.skepsun.kototoro.search.domain.SearchKind
import org.skepsun.kototoro.search.domain.sourceTypesFromTags
import org.skepsun.kototoro.search.ui.suggestion.SearchSuggestionViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>() {
    override fun onApplyWindowInsets(v: android.view.View, insets: androidx.core.view.WindowInsetsCompat): androidx.core.view.WindowInsetsCompat {
        val typeMask = androidx.core.view.WindowInsetsCompat.Type.systemBars()
        return insets.consume(v, typeMask, start = false)
    }

    @Inject
    lateinit var settings: AppSettings

    @Inject
    lateinit var backupStartupCoordinator: BackupStartupCoordinator

    @Inject
    lateinit var sourcePresetsRepository: SourcePresetsRepository

    private val viewModel by viewModels<MainViewModel>()
    private val searchSuggestionViewModel by viewModels<SearchSuggestionViewModel>()
    private val voiceInputLauncher = registerForActivityResult(VoiceInputContract()) { result ->
        val query = result?.trim().orEmpty()
        if (query.isNotEmpty()) {
            updateSearchQuery(query)
        }
    }

    private var isFoldUnfolded = false
    private val navStateFlow = MutableStateFlow(BottomNavState())
    private lateinit var composeNavBarDelegator: ComposeAppNavBarDelegator

    private var topBarHeightPx = 0
    private var bottomNavHeightPx = 0
    private var containerTopInsetPx = 0
    private var containerBottomInsetPx = 0
    private var searchQuery by mutableStateOf("")
    private var isResumeEnabledState by androidx.compose.runtime.mutableStateOf(false)

    private var currentFilterCallback: SearchBarFilterViewController.Callback? = null
    private var activeFilterContentType by mutableStateOf<ContentType?>(null)
    private var activeFilterSourceTags by mutableStateOf<Set<SourceTag>>(emptySet())
    private var isLanguagePresetFilterVisible by mutableStateOf(false)
    private var isContentTypeFilterVisible by mutableStateOf(true)
    private var isSourceTagFilterVisible by mutableStateOf(true)
    private var availableSourceTags by mutableStateOf(SourceTag.quickFilterEntries)
    private var enabledSourceTags by mutableStateOf(SourceTag.quickFilterEntries.toSet())
    private var enabledContentTypes by mutableStateOf(allTopBarContentTypes())

    fun setActiveFilterCallback(callback: SearchBarFilterViewController.Callback) {
        currentFilterCallback = callback
        refreshFilters()
    }

    fun clearActiveFilterCallback(callback: SearchBarFilterViewController.Callback) {
        if (currentFilterCallback == callback) {
            clearActiveFilters()
        }
    }

    fun refreshFilters() {
        val callback = currentFilterCallback ?: return
        val selectedTab = callback.getSelectedContentType()
        activeFilterContentType = when (selectedTab) {
            BrowseGroupTab.Novel -> ContentType.NOVEL
            BrowseGroupTab.Video -> ContentType.VIDEO
            BrowseGroupTab.Content -> ContentType.MANGA
            else -> null
        }
        activeFilterSourceTags = callback.getSelectedSourceTags()
        isLanguagePresetFilterVisible = callback.isLanguagePresetFilterVisible()
        isContentTypeFilterVisible = callback.isContentTypeFilterVisible()
        val sourceTagEntries = callback.getSourceTagEntries()
        availableSourceTags = sourceTagEntries
        isSourceTagFilterVisible = callback.isSourceTagFilterVisible() && sourceTagEntries.isNotEmpty()
        enabledSourceTags = sourceTagEntries.filterTo(linkedSetOf()) { tag ->
            callback.isSourceTagEnabled(tag)
        }
        enabledContentTypes = buildSet {
            if (callback.isContentTypeEnabled(BrowseGroupTab.Content)) {
                add(ContentType.MANGA)
            }
            if (callback.isContentTypeEnabled(BrowseGroupTab.Novel)) {
                add(ContentType.NOVEL)
            }
            if (callback.isContentTypeEnabled(BrowseGroupTab.Video)) {
                add(ContentType.VIDEO)
            }
        }
        syncSearchSuggestionFilters()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        searchQuery = savedInstanceState?.getString(STATE_TOP_BAR_QUERY).orEmpty()

        composeNavBarDelegator = ComposeAppNavBarDelegator(this, navStateFlow)

        lifecycleScope.launch {
            settings.observeAsFlow(AppSettings.KEY_NAV_MAIN) { mainNavItems }
                .collect { items ->
                    composeNavBarDelegator.setupMenu(items)
                }
        }

        viewModel.isResumeEnabled.observe(this) { isEnabled ->
            isResumeEnabledState = isEnabled
        }

        if (!setContentViewWebViewSafe { ActivityMainBinding.inflate(layoutInflater) }) {
            return
        }

        viewBinding.composeRoot.setContent {
            val suggestions by searchSuggestionViewModel.suggestion.collectAsState(initial = emptyList())
            val appUpdate by viewModel.appUpdate.collectAsState(initial = null)
            val isIncognitoModeEnabled by viewModel.isIncognitoModeEnabled.collectAsState()
            val sourcePresets by sourcePresetsRepository.observeAll().collectAsState(initial = emptyList())

            KototoroApp(
                appSettings = settings,
                navStateFlow = navStateFlow,
                suggestions = suggestions,
                onQueryChanged = ::updateSearchQuery,
                onSearch = { query -> submitSearch(query) },
                query = searchQuery,
                isResumeEnabled = isResumeEnabledState,
                onResumeClick = viewModel::openLastReader,
                onContentSuggestionClick = router::openDetails,
                onTagSuggestionClick = { tag ->
                    submitSearch(tag.title, SearchKind.TAG)
                },
                onSourceSuggestionClick = { source ->
                    this.router.openList(source, null, null)
                },
                onAuthorSuggestionClick = { author ->
                    submitSearch(author, SearchKind.AUTHOR)
                },
                onDeleteQuery = searchSuggestionViewModel::deleteQuery,
                onVoiceInput = {
                    try {
                        voiceInputLauncher.launch(null)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(this@MainActivity, R.string.voice_search, android.widget.Toast.LENGTH_SHORT).show()
                        e.printStackTrace()
                    }
                },
                onOpenListOptions = {
                    this.router.showListConfigSheet(org.skepsun.kototoro.list.ui.config.ListConfigSection.General)
                },
                onSettingsClick = {
                    this.router.openSettings()
                },
                isAppUpdateAvailable = appUpdate != null,
                onAppUpdateClick = {
                    this.router.openAppUpdate()
                },
                isIncognitoModeEnabled = isIncognitoModeEnabled,
                onIncognitoToggle = {
                    viewModel.setIncognitoMode(!isIncognitoModeEnabled)
                },
                onTopBarHeightChanged = { height ->
                    if (topBarHeightPx != height) {
                        topBarHeightPx = height
                        viewModel.setTopBarHeightPx(height)
                    }
                },
                onBottomNavHeightChanged = { height ->
                    if (bottomNavHeightPx != height) {
                        bottomNavHeightPx = height
                        viewModel.setBottomNavHeightPx(height)
                    }
                },
                onContentInsetsChanged = { topInset, bottomInset ->
                    if (containerTopInsetPx != topInset || containerBottomInsetPx != bottomInset) {
                        containerTopInsetPx = topInset
                        containerBottomInsetPx = bottomInset
                        viewModel.setContentInsetsPx(topInset, bottomInset)
                    }
                },
                onNavDestinationChanged = { itemId ->
                    composeNavBarDelegator.handleItemSelected(itemId)
                },
                isLanguagePresetFilterVisible = isLanguagePresetFilterVisible,
                languagePresetEntries = sourcePresets,
                onLanguagePresetSelected = { presetId ->
                    settings.activeSourcePresetId = presetId
                },
                onManageLanguagePresets = router::openSourcePresets,
                selectedContentType = activeFilterContentType,
                enabledContentTypes = enabledContentTypes,
                isContentTypeFilterVisible = isContentTypeFilterVisible,
                onContentTypeSelected = { type ->
                    if (type == null || type in enabledContentTypes) {
                        activeFilterContentType = type
                        syncSearchSuggestionFilters()
                        val tab = when (type) {
                            ContentType.NOVEL -> BrowseGroupTab.Novel
                            ContentType.VIDEO -> BrowseGroupTab.Video
                            ContentType.MANGA -> BrowseGroupTab.Content
                            else -> BrowseGroupTab.All
                        }
                        currentFilterCallback?.onContentTypeSelected(tab)
                    }
                },
                selectedSourceTags = activeFilterSourceTags,
                sourceTagEntries = availableSourceTags,
                enabledSourceTags = enabledSourceTags,
                isSourceTagFilterVisible = isSourceTagFilterVisible,
                onSourceTagFilterClick = ::onSourceTagFilterClick,
                onSourceTagSelected = { tag ->
                    if (tag == null || tag in enabledSourceTags) {
                        activeFilterSourceTags = if (tag == null) {
                            emptySet()
                        } else {
                            activeFilterSourceTags.toMutableSet().apply {
                                if (!add(tag)) {
                                    remove(tag)
                                }
                            }
                        }
                        syncSearchSuggestionFilters()
                        currentFilterCallback?.onSourceTagSelected(tag)
                    }
                },
            )
        }
        viewModel.onFirstStart.observeEvent(this) { this.router.showWelcomeSheet() }
        viewModel.isBottomNavPinned.observe(this, ::setNavbarPinned)

        if (savedInstanceState == null) {
            onFirstStart()
        }

        observeFoldableState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TOP_BAR_QUERY, searchQuery)
    }

    private fun submitSearch(query: String, kind: SearchKind = SearchKind.SIMPLE) {
        if (query.isEmpty()) {
            return
        }
        updateSearchQuery(query)
        if (kind == SearchKind.SIMPLE && ContentLinkResolver.isValidLink(query)) {
            this.router.openDetails(query.toUri())
            return
        }
        this.router.openSearch(
            query = query,
            kind = kind,
            sourceTypes = searchSuggestionViewModel.getSourceTypes(),
            contentKinds = searchSuggestionViewModel.getContentKinds(),
        )
        if (kind != SearchKind.TAG) {
            searchSuggestionViewModel.saveQuery(query)
        }
    }

    private fun syncSearchSuggestionFilters() {
        searchSuggestionViewModel.setSourceTypes(sourceTypesFromTags(activeFilterSourceTags))
        searchSuggestionViewModel.setContentKinds(activeFilterContentType.toSearchContentKinds())
    }

    private fun updateSearchQuery(query: String) {
        if (searchQuery != query) {
            searchQuery = query
        }
        searchSuggestionViewModel.onQueryChanged(query)
    }

    private fun clearActiveFilters() {
        currentFilterCallback = null
        activeFilterContentType = null
        activeFilterSourceTags = emptySet()
        isLanguagePresetFilterVisible = true
        isContentTypeFilterVisible = true
        isSourceTagFilterVisible = true
        availableSourceTags = SourceTag.quickFilterEntries
        enabledSourceTags = SourceTag.quickFilterEntries.toSet()
        enabledContentTypes = allTopBarContentTypes()
        syncSearchSuggestionFilters()
    }

    private fun onFirstStart() = try {
        lifecycleScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.Default) {
                LocalStorageCleanupWorker.enqueue(applicationContext)
            }
            lifecycle.withResumed {
                ContentPrefetchService.prefetchLast(this@MainActivity)
                requestNotificationsPermission()
                startService(Intent(this@MainActivity, LocalIndexUpdateService::class.java))
                backupStartupCoordinator.startOnFirstLaunch(lifecycleScope)
                if (settings.isAdBlockEnabled) {
                    startService(Intent(this@MainActivity, AdListUpdateService::class.java))
                }
            }
        }
    } catch (e: IllegalStateException) {
        e.printStackTrace()
    }

    private fun requestNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1,
            )
        }
    }

    private fun setNavbarPinned(isPinned: Boolean) = Unit

    private fun onSourceTagFilterClick(anchorView: View?): Boolean {
        val anchor = anchorView ?: viewBinding.composeRoot
        return currentFilterCallback?.onFilterIconClicked(anchor) == true
    }

    private fun observeFoldableState() {
        val foldableState = FoldableUtils.observeFoldableState(this, this)

        lifecycleScope.launch {
            foldableState.collect { unfolded ->
                if (unfolded != isFoldUnfolded) {
                    isFoldUnfolded = unfolded
                    adjustLayoutForFoldableState()
                }
            }
        }
    }

    private fun adjustLayoutForFoldableState() { }
}

private fun ContentType?.toSearchContentKinds(): Set<SearchContentKind> = when (this) {
    ContentType.MANGA -> setOf(SearchContentKind.MANGA)
    ContentType.NOVEL, ContentType.HENTAI_NOVEL -> setOf(SearchContentKind.NOVEL)
    ContentType.VIDEO, ContentType.HENTAI_VIDEO -> setOf(SearchContentKind.VIDEO)
    else -> ALL_SEARCH_CONTENT_KINDS
}

private fun allTopBarContentTypes(): Set<ContentType> = setOf(
    ContentType.MANGA,
    ContentType.NOVEL,
    ContentType.VIDEO,
)

private const val STATE_TOP_BAR_QUERY = "main_activity.top_bar_query"
