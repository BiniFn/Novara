package org.skepsun.kototoro.main.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.home.ui.compose.HomeScreen
import androidx.hilt.navigation.compose.hiltViewModel
import org.skepsun.kototoro.home.ui.HomeViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.discover.ui.compose.DiscoverScreen
import org.skepsun.kototoro.discover.ui.DiscoverViewModel
import org.skepsun.kototoro.history.ui.HistoryListFragment
import org.skepsun.kototoro.favourites.ui.container.FavouritesContainerFragment
import org.skepsun.kototoro.explore.ui.ExploreFragment
import org.skepsun.kototoro.tracker.ui.feed.FeedFragment
import org.skepsun.kototoro.local.ui.LocalListFragment
import org.skepsun.kototoro.suggestions.ui.SuggestionsFragment
import org.skepsun.kototoro.bookmarks.ui.AllBookmarksFragment
import org.skepsun.kototoro.tracker.ui.updates.UpdatesFragment

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
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
                onContentClick = { content -> router.openDetails(content, null) },
                onSettingsClick = { router.openSettings() },
                onReaderSettingsClick = { router.openReaderSettings() },
                onSyncSettingsClick = { router.openSyncSettings() },
                onViewAllRecentClick = { router.openHistory(BrowseGroupTab.Content) },
                onViewAllUpdatesClick = { router.openMangaUpdates(BrowseGroupTab.Content) },
                onViewAllRecommendationsClick = { router.openSuggestions(BrowseGroupTab.Content) },
                onSourceSettingsClick = { router.openSourcesSettings() },
                onLibraryOpenClick = { router.openFavorites() },
                onBookmarksClick = { router.openBookmarks() },
                onLocalClick = { router.openList(org.skepsun.kototoro.core.model.LocalMangaSource, null, null) },
                onDownloadsClick = { router.openDownloads() },
                onRandomClick = { viewModel.openRandom() },
                onAutoTranslateClick = { router.openTranslationSettings() },
                onTrackingItemClick = { item -> router.openDetails(item.content, null) },
                onTrackingSectionMoreClick = { section -> router.openTrackingSection(section, null) },
                isRandomLoading = isRandomLoading
            )
        }
        composable("discover") {
            val viewModel = hiltViewModel<DiscoverViewModel>()
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            DiscoverScreen(
                state = state,
                searchQuery = "", 
                onCardClick = { content, _ -> router.openDetails(content, null) },
                onSourceTrackingClick = { content, _ -> router.openDetails(content, null) },
            )
        }
        composable("history") { FragmentHostRoute(HistoryListFragment::class.java) }
        composable("favorites") { FragmentHostRoute(FavouritesContainerFragment::class.java) }
        composable("explore") { FragmentHostRoute(ExploreFragment::class.java) }
        composable("feed") { FragmentHostRoute(FeedFragment::class.java) }
        composable("local") { FragmentHostRoute(LocalListFragment::class.java) }
        composable("suggestions") { FragmentHostRoute(SuggestionsFragment::class.java) }
        composable("bookmarks") { FragmentHostRoute(AllBookmarksFragment::class.java) }
        composable("updated") { FragmentHostRoute(UpdatesFragment::class.java) }
    }
}
