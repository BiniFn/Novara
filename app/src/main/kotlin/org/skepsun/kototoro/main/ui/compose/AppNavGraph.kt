package org.skepsun.kototoro.main.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import org.skepsun.kototoro.home.ui.compose.HomeScreen
import androidx.hilt.navigation.compose.hiltViewModel
import org.skepsun.kototoro.home.ui.HomeViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.discover.ui.compose.DiscoverScreen
import org.skepsun.kototoro.discover.ui.DiscoverViewModel
import org.skepsun.kototoro.favourites.ui.container.FavouritesContainerFragment
import org.skepsun.kototoro.explore.ui.ExploreFragment
import org.skepsun.kototoro.tracker.ui.feed.FeedFragment
import org.skepsun.kototoro.local.ui.LocalListFragment
import org.skepsun.kototoro.suggestions.ui.SuggestionsFragment
import org.skepsun.kototoro.bookmarks.ui.AllBookmarksFragment
import org.skepsun.kototoro.tracker.ui.updates.UpdatesFragment
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.nav.AppRouter
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val activity = LocalContext.current as FragmentActivity
    val appRouter = activity.router

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            val viewModel = hiltViewModel<HomeViewModel>()
            val state by viewModel.summaryState.collectAsStateWithLifecycle()
            val isRandomLoading by viewModel.isRandomLoading.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onContentClick = { content -> appRouter.openDetails(content, null) },
                onSettingsClick = { appRouter.openSettings() },
                onReaderSettingsClick = { appRouter.openReaderSettings() },
                onSyncSettingsClick = { appRouter.openSyncSettings() },
                onViewAllRecentClick = { appRouter.openHistory(BrowseGroupTab.Content) },
                onViewAllUpdatesClick = { appRouter.openMangaUpdates(BrowseGroupTab.Content) },
                onViewAllRecommendationsClick = { appRouter.openSuggestions(BrowseGroupTab.Content) },
                onSourceSettingsClick = { appRouter.openSourcesSettings() },
                onLibraryOpenClick = { appRouter.openFavorites() },
                onBookmarksClick = { appRouter.openBookmarks() },
                onLocalClick = { appRouter.openList(org.skepsun.kototoro.core.model.LocalMangaSource, null, null) },
                onDownloadsClick = { appRouter.openDownloads() },
                onRandomClick = { viewModel.openRandom() },
                onAutoTranslateClick = { appRouter.openTranslationSettings() },
                onTrackingItemClick = { item ->
                    if (item.supportsDetails) {
                        appRouter.openTrackingSiteDetails(item.service, item.remoteId, item.url)
                    } else if (!item.url.isNullOrBlank()) {
                        appRouter.openExternalBrowser(item.url)
                    }
                },
                onTrackingSectionMoreClick = { section ->
                    if (section.categoryId != null) {
                        appRouter.openTrackingDiscoveryCategory(section.service, section.categoryId, section.titleResId)
                    } else {
                        navController.navigate("discover") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                isRandomLoading = isRandomLoading
            )
        }
        composable("discover") {
            val viewModel = hiltViewModel<DiscoverViewModel>()
            val items by viewModel.content.collectAsStateWithLifecycle()
            val isLoading = items.any { it is org.skepsun.kototoro.list.ui.model.LoadingState }
            val isCarousel = true // Default for tracking home
            val isLoadingOnly = isLoading && items.size <= 1
            DiscoverScreen(
                items = items,
                isRefreshing = isLoading && !isLoadingOnly,
                isCarousel = isCarousel,
                isLoadingOnly = isLoadingOnly,
                gridSpanCount = 3,
                onRefresh = { viewModel.refresh() },
                onLoadMore = { viewModel.loadNextPage() },
                onItemClick = { item ->
                    val serviceName = item.manga.source.name.removePrefix("TRACKING_")
                    val trackingService = viewModel.availableServices.value.find { it.name == serviceName } ?: return@DiscoverScreen
                    if (viewModel.supportsDetails(trackingService)) {
                        appRouter.openTrackingSiteDetails(trackingService, item.manga.id, item.manga.publicUrl)
                    } else {
                        val url = item.manga.url ?: item.manga.publicUrl
                        if (!url.isNullOrBlank()) {
                            appRouter.openExternalBrowser(url)
                        }
                    }
                },
                onCategoryMoreClick = { category ->
                    val service = viewModel.activeService.value
                    if (service != null && category != null) {
                        appRouter.openTrackingDiscoveryCategory(service, category.id, category.nameResId)
                    }
                }
            )
        }
        composable("history") {
            val viewModel = hiltViewModel<org.skepsun.kototoro.history.ui.HistoryListViewModel>()
            val items by viewModel.content.collectAsStateWithLifecycle(initialValue = emptyList())
            val listMode by viewModel.listMode.collectAsStateWithLifecycle(initialValue = org.skepsun.kototoro.core.prefs.ListMode.GRID)
            val isStatsEnabled by viewModel.isStatsEnabled.collectAsStateWithLifecycle(initialValue = false)
            val gridScale by viewModel.gridScale.collectAsStateWithLifecycle(initialValue = 1f)
            var selectedItemsIds by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emptySet<Long>()) }
            var showClearDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            val isLoading = items.any { it is org.skepsun.kototoro.list.ui.model.LoadingState }
            
            org.skepsun.kototoro.history.ui.compose.HistoryScreen(
                items = items,
                listMode = listMode,
                isRefreshing = false,
                isStatsEnabled = isStatsEnabled,
                gridScale = gridScale,
                selectedItemsIds = selectedItemsIds,
                onRefresh = { viewModel.onRefresh() },
                onLoadMore = { viewModel.requestMoreItems() },
                onItemClick = { item -> 
                    if (selectedItemsIds.isNotEmpty()) {
                        selectedItemsIds = if (item.id in selectedItemsIds) selectedItemsIds - item.id else selectedItemsIds + item.id
                    } else {
                        appRouter.openDetails(item.toContentWithOverride(), null)
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
                onStatsClick = { appRouter.openStatistic() }
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
        composable("favorites") { FragmentHostRoute(FavouritesContainerFragment::class.java) }
        composable("explore") { FragmentHostRoute(ExploreFragment::class.java) }
        composable("feed") { FragmentHostRoute(FeedFragment::class.java) }
        composable("local") { FragmentHostRoute(LocalListFragment::class.java) }
        composable("suggestions") { FragmentHostRoute(SuggestionsFragment::class.java) }
        composable("bookmarks") { FragmentHostRoute(AllBookmarksFragment::class.java) }
        composable("updated") { FragmentHostRoute(UpdatesFragment::class.java) }
    }
}
