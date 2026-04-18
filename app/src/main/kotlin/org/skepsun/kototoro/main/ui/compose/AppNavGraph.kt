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
import androidx.hilt.navigation.compose.hiltViewModel
import org.skepsun.kototoro.home.ui.HomeViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.compose.KototoroExploreHostRoute
import org.skepsun.kototoro.favourites.ui.compose.KototoroFavoritesHostRoute

import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.nav.AppRouter
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity

@Composable
fun AppNavGraph(
    navController: NavHostController,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
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
                contentPadding = contentPadding,
                state = state,
                onContentClick = { content -> appRouter.openDetails(content, null) },
                onSettingsClick = { appRouter.openSettings() },
                onReaderSettingsClick = { appRouter.openReaderSettings() },
                onSyncSettingsClick = { appRouter.openSyncSettings() },
                onViewAllRecentClick = {
                    navController.navigate("history") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onViewAllUpdatesClick = { navController.navigate("updated") },
                onViewAllRecommendationsClick = { navController.navigate("suggestions") },
                onSourceSettingsClick = { appRouter.openSourcesSettings() },
                onLibraryOpenClick = {
                    navController.navigate("favorites") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onBookmarksClick = { navController.navigate("bookmarks") },
                onLocalClick = {
                    navController.navigate("local") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
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
            org.skepsun.kototoro.explore.ui.compose.KototoroExploreHostRoute(
                appRouter = appRouter,
                contentPadding = contentPadding
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
                contentPadding = contentPadding,
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
        composable("favorites") { 
            KototoroFavoritesHostRoute(
                appRouter = appRouter,
                contentPadding = contentPadding
            )
        }
        composable("explore") { 
            org.skepsun.kototoro.explore.ui.compose.KototoroExploreHostRoute(
                appRouter = appRouter,
                contentPadding = contentPadding
            )
        }
        composable("feed") { 
            val viewModel = hiltViewModel<org.skepsun.kototoro.tracker.ui.feed.FeedViewModel>()
            val items by viewModel.content.collectAsStateWithLifecycle(initialValue = emptyList())
            val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
            
            val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            
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
            
            org.skepsun.kototoro.tracker.ui.feed.compose.FeedScreen(
                contentPadding = contentPadding,
                items = items,
                isRefreshing = isRunning,
                onRefresh = { viewModel.update() },
                onLoadMore = { viewModel.requestMoreItems() },
                onFeedItemClick = { item ->
                    viewModel.onItemClick(item)
                    appRouter.openDetails(item.toContentWithOverride(), null)
                },
                onUpdatedContentItemClick = { contentItem ->
                    appRouter.openDetails(contentItem.toContentWithOverride(), null)
                },
                onUpdatedContentMoreClick = {
                    navController.navigate("updated")
                }
            )
        }
        composable("local") { 
            val viewModel = hiltViewModel<org.skepsun.kototoro.local.ui.LocalListViewModel>()
            val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
            org.skepsun.kototoro.list.ui.compose.AppContentListRoute(
                viewModel = viewModel,
                contentPadding = contentPadding,
                appRouter = appRouter,
                showRemoveOption = true,
                isContentTypeFilterVisible = false,
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
        composable("suggestions") { 
            val viewModel = hiltViewModel<org.skepsun.kototoro.suggestions.ui.SuggestionsViewModel>()
            org.skepsun.kototoro.list.ui.compose.AppContentListRoute(
                viewModel = viewModel,
                contentPadding = contentPadding,
                appRouter = appRouter,
                showRemoveOption = false,
                isContentTypeFilterVisible = true,
                isSourceTagFilterVisible = true,
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
        composable("bookmarks") { 
            val viewModel = hiltViewModel<org.skepsun.kototoro.bookmarks.ui.AllBookmarksViewModel>()
            val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
            val pageSaveHelperFactory = androidx.compose.runtime.remember {
                // To get the Dagger Factory, we can just use an EntryPoint
                dagger.hilt.android.EntryPointAccessors.fromActivity(
                    activity as android.app.Activity,
                    org.skepsun.kototoro.bookmarks.ui.PageSaveHelperEntryPoint::class.java
                ).pageSaveHelperFactory()
            }
            val pageSaveHelper = androidx.compose.runtime.remember {
                pageSaveHelperFactory.create(activity as androidx.activity.ComponentActivity)
            }
            org.skepsun.kototoro.bookmarks.ui.compose.AppBookmarksRoute(
                viewModel = viewModel,
                contentPadding = contentPadding,
                appRouter = appRouter,
                pageSaveHelper = pageSaveHelper
            )
        }
        composable("updated") { 
            val viewModel = hiltViewModel<org.skepsun.kototoro.tracker.ui.updates.UpdatesViewModel>()
            org.skepsun.kototoro.list.ui.compose.AppContentListRoute(
                viewModel = viewModel,
                contentPadding = contentPadding,
                appRouter = appRouter,
                showRemoveOption = true,
                isContentTypeFilterVisible = true,
                isSourceTagFilterVisible = true,
                onRemoveSelection = { ids -> viewModel.remove(ids) },
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
}
