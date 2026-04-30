package org.skepsun.kototoro.main.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import org.skepsun.kototoro.home.ui.compose.HomeScreen
import org.skepsun.kototoro.home.ui.compose.HomeScreenActions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.skepsun.kototoro.home.ui.HomeViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.compose.KototoroExploreHostRoute
import org.skepsun.kototoro.explore.ui.compose.ExploreSourceSelectionTopBarState
import org.skepsun.kototoro.favourites.ui.compose.KototoroFavoritesHostRoute
import org.skepsun.kototoro.main.ui.MainActivity
import org.skepsun.kototoro.main.ui.SearchBarFilterViewController
import org.skepsun.kototoro.core.nav.router
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Rect
import androidx.fragment.app.FragmentActivity
import org.skepsun.kototoro.core.util.ShareHelper
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.search.ui.compose.SearchNavigationRequest
import org.skepsun.kototoro.search.ui.compose.SearchResultsRoute
import org.skepsun.kototoro.search.ui.compose.SearchRoute
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.CompositionLocalProvider
import org.skepsun.kototoro.core.nav.PendingDetailsNavigation
import org.skepsun.kototoro.core.ui.compose.LocalNavAnimatedVisibilityScope
import org.skepsun.kototoro.core.ui.compose.contentCoverSharedKey
import org.skepsun.kototoro.details.ui.compose.DetailsScreen
import org.skepsun.kototoro.details.ui.DetailsViewModel
import org.skepsun.kototoro.details.ui.compose.handleDetailsAction
import org.skepsun.kototoro.parsers.model.Content

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavGraph(
    navController: NavHostController,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    pageSaveHelper: org.skepsun.kototoro.reader.ui.PageSaveHelper? = null,
    modifier: Modifier = Modifier,
    onExploreSourceSelectionTopBarChanged: (TopBarOverrideState?) -> Unit = {},
    onOpenSearch: (SearchNavigationRequest) -> Unit = {},
) {
    val activity = LocalContext.current as FragmentActivity
    val appRouter = activity.router
    val mainActivity = activity as? MainActivity
    val rootView = LocalView.current
    val navigateToDetailsWithContent = remember(navController) {
        { content: Content, sharedElementKey: String? ->
            PendingDetailsNavigation.set(content, sharedElementKey)
            navController.navigate(DetailsRoute)
        }
    }
    val navigateToDetailsWithOrigin = remember(navController) {
        { origin: org.skepsun.kototoro.details.ui.model.DetailsOrigin, sharedElementKey: String? ->
            PendingDetailsNavigation.set(origin, sharedElementKey)
            navController.navigate(DetailsRoute)
        }
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier
    ) {
        composable<HomeRoute> {
            val viewModel = hiltViewModel<HomeViewModel>()
            val state by viewModel.summaryState.collectAsStateWithLifecycle()
            val isRandomLoading by viewModel.isRandomLoading.collectAsStateWithLifecycle()

            androidx.compose.runtime.LaunchedEffect(viewModel.onOpenContent, navigateToDetailsWithContent) {
                viewModel.onOpenContent.collect { event ->
                    event?.consume { content ->
                        navigateToDetailsWithContent(content, null)
                    }
                }
            }

            DisposableEffect(mainActivity, viewModel, state.selectedTab, state.selectedSourceTags) {
                val callback = object : SearchBarFilterViewController.Callback {
                    override fun getSelectedContentType(): BrowseGroupTab = when (state.selectedTab) {
                        org.skepsun.kototoro.home.ui.HomeContentTab.MANGA -> BrowseGroupTab.Content
                        org.skepsun.kototoro.home.ui.HomeContentTab.NOVEL -> BrowseGroupTab.Novel
                        org.skepsun.kototoro.home.ui.HomeContentTab.VIDEO -> BrowseGroupTab.Video
                        null -> BrowseGroupTab.All
                    }

                    override fun onContentTypeSelected(tab: BrowseGroupTab) {
                        viewModel.setSelectedTab(
                            when (if (getSelectedContentType() == tab) BrowseGroupTab.All else tab) {
                                BrowseGroupTab.Content -> org.skepsun.kototoro.home.ui.HomeContentTab.MANGA
                                BrowseGroupTab.Novel -> org.skepsun.kototoro.home.ui.HomeContentTab.NOVEL
                                BrowseGroupTab.Video -> org.skepsun.kototoro.home.ui.HomeContentTab.VIDEO
                                else -> null
                            }
                        )
                    }

                    override fun getSelectedSourceTags(): Set<org.skepsun.kototoro.explore.ui.model.SourceTag> = state.selectedSourceTags

                    override fun onSourceTagSelected(tag: org.skepsun.kototoro.explore.ui.model.SourceTag?) {
                        val current = state.selectedSourceTags
                        viewModel.setSelectedSourceTags(
                            when {
                                tag == null -> emptySet()
                                tag in current -> current - tag
                                else -> current + tag
                            }
                        )
                    }
                }
                mainActivity?.setActiveFilterCallback(callback)
                onDispose {
                    mainActivity?.clearActiveFilterCallback(callback)
                }
            }

            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                val onHomeContentClick = remember(navigateToDetailsWithContent) {
                    { content: Content, _: Rect?, sharedElementKey: String? ->
                        navigateToDetailsWithContent(content, sharedElementKey)
                    }
                }
                val onHomeSettingsClick = remember(appRouter) { { appRouter.openSettings() } }
                val onHomeReaderSettingsClick = remember(appRouter) { { appRouter.openReaderSettings() } }
                val onHomeSyncSettingsClick = remember(appRouter) { { appRouter.openSyncSettings() } }
                val onHomeViewAllRecentClick = remember(navController) {
                    {
                        navController.navigate(HistoryRoute) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
                val onHomeViewAllUpdatesClick = remember(navController) { { navController.navigate(UpdatedRoute) } }
                val onHomeViewAllRecommendationsClick = remember(navController) {
                    { navController.navigate(SuggestionsRoute) }
                }
                val onHomeRecentSearchClick = remember(onOpenSearch) {
                    { query: String ->
                        onOpenSearch(
                            SearchNavigationRequest(
                                query = query,
                                kind = org.skepsun.kototoro.search.domain.SearchKind.SIMPLE,
                                sourceTypes = org.skepsun.kototoro.search.domain.ALL_SOURCE_TYPES,
                                contentKinds = org.skepsun.kototoro.search.domain.ALL_SEARCH_CONTENT_KINDS,
                                advancedQuery = null,
                                pinnedOnly = false,
                                hideEmpty = false,
                                requestId = System.nanoTime(),
                            ),
                        )
                    }
                }
                val onHomeSourceSettingsClick = remember(appRouter) { { appRouter.openSourcesSettings() } }
                val onHomeLibraryOpenClick = remember(navController) {
                    {
                        navController.navigate(FavoritesRoute) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
                val onHomeBookmarksClick = remember(navController) { { navController.navigate(BookmarksRoute) } }
                val onHomeLocalClick = remember(navController) {
                    {
                        navController.navigate(LocalRoute) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
                val onHomeDownloadsClick = remember(appRouter) { { appRouter.openDownloads() } }
                val onHomeRandomClick = remember(viewModel) { { viewModel.openRandom() } }
                val onHomeAutoTranslateClick = remember(appRouter) { { appRouter.openTranslationSettings() } }
                val homeActions = remember(
                    onHomeSettingsClick,
                    onHomeReaderSettingsClick,
                    onHomeSyncSettingsClick,
                    onHomeViewAllRecentClick,
                    onHomeViewAllUpdatesClick,
                    onHomeViewAllRecommendationsClick,
                    onHomeRecentSearchClick,
                    onHomeSourceSettingsClick,
                    onHomeLibraryOpenClick,
                    onHomeBookmarksClick,
                    onHomeLocalClick,
                    onHomeDownloadsClick,
                    onHomeRandomClick,
                    onHomeAutoTranslateClick,
                ) {
                    HomeScreenActions(
                        onSettingsClick = onHomeSettingsClick,
                        onReaderSettingsClick = onHomeReaderSettingsClick,
                        onSyncSettingsClick = onHomeSyncSettingsClick,
                        onViewAllRecentClick = onHomeViewAllRecentClick,
                        onViewAllUpdatesClick = onHomeViewAllUpdatesClick,
                        onViewAllRecommendationsClick = onHomeViewAllRecommendationsClick,
                        onRecentSearchClick = onHomeRecentSearchClick,
                        onSourceSettingsClick = onHomeSourceSettingsClick,
                        onLibraryOpenClick = onHomeLibraryOpenClick,
                        onBookmarksClick = onHomeBookmarksClick,
                        onLocalClick = onHomeLocalClick,
                        onDownloadsClick = onHomeDownloadsClick,
                        onRandomClick = onHomeRandomClick,
                        onAutoTranslateClick = onHomeAutoTranslateClick,
                    )
                }
                HomeScreen(
                    contentPadding = contentPadding,
                    state = state,
                    onContentClick = onHomeContentClick,
                    actions = homeActions,
                    isRandomLoading = isRandomLoading,
                )
            }
        }
        composable<DiscoverRoute> {
            val exploreViewModel = hiltViewModel<org.skepsun.kototoro.explore.ui.ExploreViewModel>()
            val selectedGroupTab by exploreViewModel.currentGroupTab.collectAsStateWithLifecycle(initialValue = BrowseGroupTab.All)
            val selectedSourceTags by exploreViewModel.currentSourceTags.collectAsStateWithLifecycle(initialValue = emptySet())

            DisposableEffect(mainActivity, exploreViewModel, selectedGroupTab, selectedSourceTags) {
                val callback = object : SearchBarFilterViewController.Callback {
                    override fun getSelectedContentType(): BrowseGroupTab = selectedGroupTab

                    override fun onContentTypeSelected(tab: BrowseGroupTab) {
                        exploreViewModel.setSelectedGroupTab(if (selectedGroupTab == tab) BrowseGroupTab.All else tab)
                    }

                    override fun getSelectedSourceTags(): Set<org.skepsun.kototoro.explore.ui.model.SourceTag> = selectedSourceTags

                    override fun onSourceTagSelected(tag: org.skepsun.kototoro.explore.ui.model.SourceTag?) {
                        exploreViewModel.setSelectedSourceTags(
                            when {
                                tag == null -> emptySet()
                                tag in selectedSourceTags -> selectedSourceTags - tag
                                else -> selectedSourceTags + tag
                            }
                        )
                    }
                }
                mainActivity?.setActiveFilterCallback(callback)
                onDispose {
                    mainActivity?.clearActiveFilterCallback(callback)
                }
            }

            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                org.skepsun.kototoro.explore.ui.compose.KototoroExploreHostRoute(
                    appRouter = appRouter,
                    contentPadding = contentPadding,
                    exploreViewModel = exploreViewModel,
                    onSourceSelectionTopBarChanged = onExploreSourceSelectionTopBarChanged,
                    onNavigateToDetails = navigateToDetailsWithOrigin,
                )
            }
        }
        composable<HistoryRoute> {
            val viewModel = hiltViewModel<org.skepsun.kototoro.history.ui.HistoryListViewModel>()
            val items by viewModel.content.collectAsStateWithLifecycle(initialValue = emptyList())
            val listMode by viewModel.listMode.collectAsStateWithLifecycle(initialValue = org.skepsun.kototoro.core.prefs.ListMode.GRID)
            val isStatsEnabled by viewModel.isStatsEnabled.collectAsStateWithLifecycle(initialValue = false)
            val gridScale by viewModel.gridScale.collectAsStateWithLifecycle(initialValue = 1f)
            val selectedGroupTab by viewModel.currentGroupTab.collectAsStateWithLifecycle(initialValue = BrowseGroupTab.All)
            val selectedSourceTags by viewModel.currentSourceTags.collectAsStateWithLifecycle(initialValue = emptySet())
            var selectedItemsIds by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptySet<Long>()) }
            var showClearDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            val selectedModels = remember(items, selectedItemsIds) {
                items
                    .filterIsInstance<org.skepsun.kototoro.list.ui.model.ContentListModel>()
                    .filter { it.id in selectedItemsIds }
            }

            BackHandler(enabled = selectedItemsIds.isNotEmpty()) {
                selectedItemsIds = emptySet()
            }

            SideEffect {
                if (selectedItemsIds.isNotEmpty()) {
                    onExploreSourceSelectionTopBarChanged(
                        ContentSelectionTopBarOverrideState(
                            selectedCount = selectedItemsIds.size,
                            isAllNonLocal = selectedModels.none { it.manga.isLocal },
                            isSingleSelection = selectedItemsIds.size == 1,
                            showRemoveOption = true,
                            supportedActions = setOf(
                                org.skepsun.kototoro.list.ui.compose.SelectionAction.SELECT_ALL,
                                org.skepsun.kototoro.list.ui.compose.SelectionAction.REMOVE,
                            ),
                            onClearSelection = { selectedItemsIds = emptySet() },
                            onActionClick = { action ->
                                when (action) {
                                    org.skepsun.kototoro.list.ui.compose.SelectionAction.SELECT_ALL -> {
                                        selectedItemsIds = items
                                            .filterIsInstance<org.skepsun.kototoro.list.ui.model.ContentListModel>()
                                            .mapTo(linkedSetOf()) { it.id }
                                    }

                                    org.skepsun.kototoro.list.ui.compose.SelectionAction.REMOVE -> {
                                        viewModel.removeFromHistory(selectedItemsIds)
                                        selectedItemsIds = emptySet()
                                    }

                                    else -> Unit
                                }
                            },
                        ),
                    )
                } else {
                    onExploreSourceSelectionTopBarChanged(null)
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    onExploreSourceSelectionTopBarChanged(null)
                }
            }

            DisposableEffect(mainActivity, viewModel, selectedGroupTab, selectedSourceTags) {
                val callback = object : SearchBarFilterViewController.Callback {
                    override fun getSelectedContentType(): BrowseGroupTab = selectedGroupTab

                    override fun onContentTypeSelected(tab: BrowseGroupTab) {
                        viewModel.setSelectedGroupTab(if (selectedGroupTab == tab) BrowseGroupTab.All else tab)
                    }

                    override fun getSelectedSourceTags(): Set<org.skepsun.kototoro.explore.ui.model.SourceTag> = selectedSourceTags

                    override fun onSourceTagSelected(tag: org.skepsun.kototoro.explore.ui.model.SourceTag?) {
                        viewModel.setSelectedSourceTags(
                            when {
                                tag == null -> emptySet()
                                tag in selectedSourceTags -> selectedSourceTags - tag
                                else -> selectedSourceTags + tag
                            }
                        )
                    }
                }
                mainActivity?.setActiveFilterCallback(callback)
                onDispose {
                    mainActivity?.clearActiveFilterCallback(callback)
                }
            }

            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                org.skepsun.kototoro.history.ui.compose.HistoryScreen(
                    contentPadding = contentPadding,
                    items = items,
                    listMode = listMode,
                    isRefreshing = false,
                    isStatsEnabled = isStatsEnabled,
                    gridScale = gridScale,
                    selectedItemsIds = selectedItemsIds,
                    onRefresh = { viewModel.onRefresh() },
                    onLoadMore = { viewModel.requestMoreItems() },
                    onPrepareItemTransition = { item, coverBounds ->
                    },
                    onItemClick = { item ->
                        if (selectedItemsIds.isNotEmpty()) {
                            selectedItemsIds = if (item.id in selectedItemsIds) selectedItemsIds - item.id else selectedItemsIds + item.id
                        } else {
                            val content = item.toContentWithOverride()
                            navigateToDetailsWithContent(
                                content,
                                contentCoverSharedKey(item.source.name, item.coverUrl.orEmpty()),
                            )
                        }
                    },
                    onItemLongClick = { item ->
                        selectedItemsIds = if (item.id in selectedItemsIds) selectedItemsIds - item.id else selectedItemsIds + item.id
                    },
                    onClearSelection = { selectedItemsIds = emptySet() },
                    onSelectionAction = { action ->
                        if (action == org.skepsun.kototoro.list.ui.compose.SelectionAction.REMOVE) {
                            viewModel.removeFromHistory(selectedItemsIds)
                            selectedItemsIds = emptySet()
                        }
                    },
                    onClearHistoryClick = { showClearDialog = true },
                    onStatsClick = { appRouter.openStatistic() },
                    showInlineSelectionTopBar = false,
                )

                if (showClearDialog) {
                    org.skepsun.kototoro.history.ui.compose.ClearHistoryDialog(
                        onDismissRequest = { showClearDialog = false },
                        onConfirm = { option ->
                            when(option) {
                                org.skepsun.kototoro.history.ui.compose.ClearHistoryOption.LAST_2_HOURS -> viewModel.clearHistory(java.time.Instant.now().minus(2, java.time.temporal.ChronoUnit.HOURS))
                                org.skepsun.kototoro.history.ui.compose.ClearHistoryOption.TODAY -> viewModel.clearHistory(java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())
                                org.skepsun.kototoro.history.ui.compose.ClearHistoryOption.NOT_IN_FAVORITES -> viewModel.removeNotFavorite()
                                org.skepsun.kototoro.history.ui.compose.ClearHistoryOption.CLEAR_ALL -> viewModel.clearHistory(null)
                            }
                        }
                    )
                }
            }
        }
        composable<FavoritesRoute> {
            val viewModel = hiltViewModel<org.skepsun.kototoro.favourites.ui.container.FavouritesContainerViewModel>()
            val selectedGroupTab by viewModel.globalFavoritesState.selectedGroupTab.collectAsStateWithLifecycle()
            val selectedSourceTags by viewModel.globalFavoritesState.selectedSourceTags.collectAsStateWithLifecycle()

            DisposableEffect(mainActivity, viewModel, selectedGroupTab, selectedSourceTags) {
                val callback = object : SearchBarFilterViewController.Callback {
                    override fun isSourceTagFilterVisible(): Boolean = true

                    override fun getSourceTagEntries(): List<org.skepsun.kototoro.explore.ui.model.SourceTag> =
                        org.skepsun.kototoro.explore.ui.model.SourceTag.quickFilterEntries

                    override fun getSelectedContentType(): BrowseGroupTab = selectedGroupTab

                    override fun onContentTypeSelected(tab: BrowseGroupTab) {
                        viewModel.globalFavoritesState.setSelectedGroupTab(
                            if (selectedGroupTab == tab) BrowseGroupTab.All else tab,
                        )
                    }

                    override fun getSelectedSourceTags(): Set<org.skepsun.kototoro.explore.ui.model.SourceTag> =
                        selectedSourceTags

                    override fun onSourceTagSelected(tag: org.skepsun.kototoro.explore.ui.model.SourceTag?) {
                        when {
                            tag == null -> viewModel.globalFavoritesState.clearSourceTags()
                            tag in selectedSourceTags -> {
                                viewModel.globalFavoritesState.setSelectedSourceTags(selectedSourceTags - tag)
                            }
                            else -> {
                                viewModel.globalFavoritesState.setSelectedSourceTags(selectedSourceTags + tag)
                            }
                        }
                    }
                }
                mainActivity?.setActiveFilterCallback(callback)
                onDispose {
                    mainActivity?.clearActiveFilterCallback(callback)
                }
            }

            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                KototoroFavoritesHostRoute(
                    appRouter = appRouter,
                    contentPadding = contentPadding,
                    onNavigateToDetails = navigateToDetailsWithContent,
                    registerFilterCallback = false,
                    viewModel = viewModel,
                )
            }
        }
        composable<ExploreRoute> {
            val exploreViewModel = hiltViewModel<org.skepsun.kototoro.explore.ui.ExploreViewModel>()
            val selectedGroupTab by exploreViewModel.currentGroupTab.collectAsStateWithLifecycle(initialValue = BrowseGroupTab.All)
            val selectedSourceTags by exploreViewModel.currentSourceTags.collectAsStateWithLifecycle(initialValue = emptySet())

            DisposableEffect(mainActivity, exploreViewModel, selectedGroupTab, selectedSourceTags) {
                val callback = object : SearchBarFilterViewController.Callback {
                    override fun getSelectedContentType(): BrowseGroupTab = selectedGroupTab

                    override fun onContentTypeSelected(tab: BrowseGroupTab) {
                        exploreViewModel.setSelectedGroupTab(if (selectedGroupTab == tab) BrowseGroupTab.All else tab)
                    }

                    override fun getSelectedSourceTags(): Set<org.skepsun.kototoro.explore.ui.model.SourceTag> = selectedSourceTags

                    override fun onSourceTagSelected(tag: org.skepsun.kototoro.explore.ui.model.SourceTag?) {
                        exploreViewModel.setSelectedSourceTags(
                            when {
                                tag == null -> emptySet()
                                tag in selectedSourceTags -> selectedSourceTags - tag
                                else -> selectedSourceTags + tag
                            }
                        )
                    }
                }
                mainActivity?.setActiveFilterCallback(callback)
                onDispose {
                    mainActivity?.clearActiveFilterCallback(callback)
                }
            }

            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                org.skepsun.kototoro.explore.ui.compose.KototoroExploreHostRoute(
                    appRouter = appRouter,
                    contentPadding = contentPadding,
                    exploreViewModel = exploreViewModel,
                    onSourceSelectionTopBarChanged = onExploreSourceSelectionTopBarChanged,
                    onNavigateToDetails = navigateToDetailsWithOrigin,
                )
            }
        }
        composable<FeedRoute> {
            val viewModel = hiltViewModel<org.skepsun.kototoro.tracker.ui.feed.FeedViewModel>()
            val items by viewModel.content.collectAsStateWithLifecycle(initialValue = emptyList())
            val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
            val categories by viewModel.categories.collectAsStateWithLifecycle(initialValue = emptyList())
            val selectedCategoryId by viewModel.currentCategoryId.collectAsStateWithLifecycle(initialValue = org.skepsun.kototoro.core.model.FavouriteCategory.NO_ID)
            val selectedGroupTab by viewModel.currentGroupTab.collectAsStateWithLifecycle(initialValue = BrowseGroupTab.All)
            val selectedSourceTags by viewModel.currentSourceTags.collectAsStateWithLifecycle(initialValue = emptySet())

            val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

            DisposableEffect(mainActivity, viewModel, selectedGroupTab, selectedSourceTags) {
                val callback = object : SearchBarFilterViewController.Callback {
                    override fun getSelectedContentType(): BrowseGroupTab = selectedGroupTab

                    override fun onContentTypeSelected(tab: BrowseGroupTab) {
                        viewModel.setSelectedGroupTab(if (selectedGroupTab == tab) BrowseGroupTab.All else tab)
                    }

                    override fun getSelectedSourceTags(): Set<org.skepsun.kototoro.explore.ui.model.SourceTag> = selectedSourceTags

                    override fun onSourceTagSelected(tag: org.skepsun.kototoro.explore.ui.model.SourceTag?) {
                        if (tag == null) {
                            selectedSourceTags.forEach(viewModel::toggleSourceTag)
                        } else {
                            viewModel.toggleSourceTag(tag)
                        }
                    }
                }
                mainActivity?.setActiveFilterCallback(callback)
                onDispose {
                    mainActivity?.clearActiveFilterCallback(callback)
                }
            }

            androidx.compose.runtime.DisposableEffect(viewModel, activity, lifecycleOwner) {
                val menuProvider = org.skepsun.kototoro.tracker.ui.feed.FeedMenuProvider(
                    snackbarHost = activity?.window?.decorView?.rootView ?: android.view.View(activity),
                    viewModel = viewModel
                )
                activity?.addMenuProvider(menuProvider, lifecycleOwner, androidx.lifecycle.Lifecycle.State.RESUMED)
                onDispose {
                    activity?.removeMenuProvider(menuProvider)
                }
            }

            androidx.compose.runtime.LaunchedEffect(viewModel.onError) {
                val host = activity?.window?.decorView?.rootView ?: return@LaunchedEffect
                val observer = org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver(host, null)
                viewModel.onError.collect { event: org.skepsun.kototoro.core.util.Event<Throwable>? ->
                    event?.consume(observer)
                }
            }

            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                org.skepsun.kototoro.tracker.ui.feed.compose.FeedScreen(
                    contentPadding = contentPadding,
                    items = items,
                    isRefreshing = isRunning,
                    onRefresh = { viewModel.update() },
                    onLoadMore = { viewModel.requestMoreItems() },
                    onFeedItemClick = { item, coverBounds ->
                        viewModel.onItemClick(item)
                        val content = item.toContentWithOverride()
                        navigateToDetailsWithContent(
                            content,
                            contentCoverSharedKey(content.source.name, content.coverUrl.orEmpty()),
                        )
                    },
                    onUpdatedContentItemClick = { contentItem, coverBounds ->
                        val content = contentItem.toContentWithOverride()
                        navigateToDetailsWithContent(
                            content,
                            contentCoverSharedKey(content.source.name, content.coverUrl.orEmpty()),
                        )
                    },
                    onUpdatedContentMoreClick = {
                        navController.navigate(UpdatedRoute)
                    },
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    onCategorySelected = viewModel::selectCategory,
                )
            }
        }
        composable<LocalRoute> {
            val viewModel = hiltViewModel<org.skepsun.kototoro.local.ui.LocalListViewModel>()
            val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                org.skepsun.kototoro.list.ui.compose.AppContentListRoute(
                    viewModel = viewModel,
                    contentPadding = contentPadding,
                    appRouter = appRouter,
                    onTopBarOverrideChanged = onExploreSourceSelectionTopBarChanged,
                    showRemoveOption = true,
                    isContentTypeFilterVisible = true,
                    onNavigateToDetails = navigateToDetailsWithContent,
                    isSourceTagFilterVisible = false,
                    onRemoveSelection = { ids ->
                        if (activity != null) {
                            com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                                .setTitle(org.skepsun.kototoro.R.string.delete_manga)
                                .setMessage(org.skepsun.kototoro.R.string.text_delete_local_manga_batch)
                                .setPositiveButton(org.skepsun.kototoro.R.string.delete) { _, _ -> viewModel.delete(ids) }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        }
                    },
                    onShareSelection = { ids ->
                        if (activity != null) {
                            val files = viewModel.content.value.filter { it is org.skepsun.kototoro.list.ui.model.ContentListModel && it.id in ids }.mapNotNull { (it as? org.skepsun.kototoro.list.ui.model.ContentListModel)?.manga?.url?.let { url -> java.io.File(android.net.Uri.parse(url).path ?: "") } }
                            org.skepsun.kototoro.core.util.ShareHelper(activity).shareCbz(files)
                        }
                    },
                    onAddMenuProvider = { act, _, owner ->
                        org.skepsun.kototoro.local.ui.LocalListMenuProvider(appRouter, { appRouter.showImportDialog() })
                    }
                )
            }
        }
        composable<SuggestionsRoute> {
            val viewModel = hiltViewModel<org.skepsun.kototoro.suggestions.ui.SuggestionsViewModel>()
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                org.skepsun.kototoro.list.ui.compose.AppContentListRoute(
                    viewModel = viewModel,
                    contentPadding = contentPadding,
                    appRouter = appRouter,
                    onTopBarOverrideChanged = onExploreSourceSelectionTopBarChanged,
                    showRemoveOption = false,
                    isContentTypeFilterVisible = true,
                    isSourceTagFilterVisible = true,
                    onNavigateToDetails = navigateToDetailsWithContent,
                    onAddMenuProvider = { act, _, _ ->
                        object : androidx.core.view.MenuProvider {
                            override fun onCreateMenu(menu: android.view.Menu, menuInflater: android.view.MenuInflater) {
                                menuInflater.inflate(org.skepsun.kototoro.R.menu.opt_suggestions, menu)
                                menuInflater.inflate(org.skepsun.kototoro.R.menu.opt_list, menu)
                            }
                            override fun onPrepareMenu(menu: android.view.Menu) {
                                menu.findItem(org.skepsun.kototoro.R.id.action_settings_suggestions)?.isVisible = true
                            }
                            override fun onMenuItemSelected(menuItem: android.view.MenuItem): Boolean = when (menuItem.itemId) {
                                org.skepsun.kototoro.R.id.action_update -> {
                                    viewModel.updateSuggestions()
                                    com.google.android.material.snackbar.Snackbar.make(act.window.decorView.rootView, org.skepsun.kototoro.R.string.suggestions_updating, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
                                    true
                                }
                                org.skepsun.kototoro.R.id.action_list_mode -> {
                                    appRouter.showListConfigSheet(org.skepsun.kototoro.list.ui.config.ListConfigSection.Suggestions)
                                    true
                                }
                                org.skepsun.kototoro.R.id.action_settings_suggestions -> {
                                    appRouter.openSuggestionsSettings()
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                )
            }
        }
        composable<BookmarksRoute> {
            val viewModel = hiltViewModel<org.skepsun.kototoro.bookmarks.ui.AllBookmarksViewModel>()
            org.skepsun.kototoro.bookmarks.ui.compose.AppBookmarksRoute(
                viewModel = viewModel,
                contentPadding = contentPadding,
                appRouter = appRouter,
                pageSaveHelper = requireNotNull(pageSaveHelper) {
                    "BookmarksRoute requires a pre-registered PageSaveHelper"
                },
            )
        }
        composable<UpdatedRoute> {
            val viewModel = hiltViewModel<org.skepsun.kototoro.tracker.ui.updates.UpdatesViewModel>()
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                org.skepsun.kototoro.list.ui.compose.AppContentListRoute(
                    viewModel = viewModel,
                    contentPadding = contentPadding,
                    appRouter = appRouter,
                    onTopBarOverrideChanged = onExploreSourceSelectionTopBarChanged,
                    showRemoveOption = true,
                    isContentTypeFilterVisible = true,
                    isSourceTagFilterVisible = true,
                    onRemoveSelection = { ids -> viewModel.remove(ids) },
                    onNavigateToDetails = navigateToDetailsWithContent,
                    onAddMenuProvider = { _, _, _ ->
                        object : androidx.core.view.MenuProvider {
                            override fun onCreateMenu(menu: android.view.Menu, menuInflater: android.view.MenuInflater) {
                                menuInflater.inflate(org.skepsun.kototoro.R.menu.opt_list, menu)
                            }
                            override fun onMenuItemSelected(menuItem: android.view.MenuItem): Boolean = when (menuItem.itemId) {
                                org.skepsun.kototoro.R.id.action_list_mode -> {
                                    appRouter.showListConfigSheet(org.skepsun.kototoro.list.ui.config.ListConfigSection.Updated)
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                )
            }
        }
        composable<SearchRoute> {
            val viewModel = hiltViewModel<org.skepsun.kototoro.search.ui.multi.SearchViewModel>()
            SearchResultsRoute(
                viewModel = viewModel,
                onBackClick = { navController.navigateUp() },
                onOpenContent = { content ->
                    navigateToDetailsWithContent(content, null)
                },
                onPickContent = { },
                onOpenSourceResults = { item ->
                    if (item.listFilter == null) {
                        appRouter.openSearch(item.source, viewModel.query)
                    } else {
                        appRouter.openList(item.source, item.listFilter, item.sortOrder)
                    }
                },
                onSubmitSearch = { query, kind, sourceTypes, contentKinds, advancedQuery, pinnedOnly, hideEmpty ->
                    onOpenSearch(
                        SearchNavigationRequest(
                            query = query,
                            kind = kind,
                            sourceTypes = sourceTypes,
                            contentKinds = contentKinds,
                            advancedQuery = advancedQuery,
                            pinnedOnly = pinnedOnly,
                            hideEmpty = hideEmpty,
                            requestId = System.nanoTime(),
                        ),
                    )
                },
                onShareSelection = { items ->
                    ShareHelper(activity).shareContentLinks(items)
                },
                onSaveSelection = { items ->
                    appRouter.showDownloadDialog(items, rootView)
                },
                onFavouriteSelection = { items ->
                    appRouter.showFavoriteDialog(items)
                },
                isPickMode = false,
            )
        }

        composable<DetailsRoute>(
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(320, easing = LinearEasing),
                ) + fadeIn(tween(220, easing = LinearEasing))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(320, easing = LinearEasing),
                ) + fadeOut(tween(180, easing = LinearEasing))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(320, easing = LinearEasing),
                ) + fadeIn(tween(180, easing = LinearEasing))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(320, easing = LinearEasing),
                ) + fadeOut(tween(160, easing = LinearEasing))
            },
        ) {
            val detailsViewModel = hiltViewModel<DetailsViewModel>()
            val pagesViewModel = hiltViewModel<org.skepsun.kototoro.details.ui.pager.pages.PagesViewModel>()
            val bookmarksViewModel = hiltViewModel<org.skepsun.kototoro.details.ui.pager.bookmarks.BookmarksViewModel>()
            val detailsCoroutineScope = rememberCoroutineScope()

            val entryPoint = remember(activity) {
                dagger.hilt.android.EntryPointAccessors.fromActivity(
                    activity,
                    DetailsRouteEntryPoint::class.java,
                )
            }
            val effectivePageSaveHelper = pageSaveHelper ?: remember(activity) {
                entryPoint.pageSaveHelperFactory().create(activity)
            }
            val overrideEditLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    detailsViewModel.reload()
                }
            }

            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                val pendingContent = remember { PendingDetailsNavigation.lastContent() }
                val pendingSharedKey = remember { PendingDetailsNavigation.lastSharedElementKey() }
                val mangaDetails by detailsViewModel.mangaDetails.collectAsStateWithLifecycle()
                val sharedKey = remember(pendingSharedKey, mangaDetails, pendingContent) {
                    pendingSharedKey ?: run {
                        val content = mangaDetails?.toContent() ?: pendingContent
                        content?.let { c ->
                            contentCoverSharedKey(c.source.name, c.coverUrl.orEmpty())
                        }
                    }
                }
                DetailsScreen(
                    viewModel = detailsViewModel,
                    pagesViewModel = pagesViewModel,
                    bookmarksViewModel = bookmarksViewModel,
                    settings = entryPoint.settings(),
                    appRouter = appRouter,
                    pageSaveHelper = effectivePageSaveHelper,
                    onBackClick = { navController.popBackStack() },
                    sharedElementKey = sharedKey,
                    onActionClick = { action ->
                        handleDetailsAction(
                            action = action,
                            appRouter = appRouter,
                            viewModel = detailsViewModel,
                            appShortcutManager = entryPoint.appShortcutManager(),
                            coroutineScope = detailsCoroutineScope,
                            snackbarHost = rootView,
                            overrideEditLauncher = overrideEditLauncher,
                            onFinish = { navController.popBackStack() },
                        )
                    },
                )
            }
        }
    }
}
