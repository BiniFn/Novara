package org.skepsun.kototoro.favourites.ui.compose

import android.os.Build
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.FavouriteCategory.Companion.NO_ID
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.glass.LocalHazeState
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.favourites.ui.container.FavouriteTabModel
import org.skepsun.kototoro.favourites.ui.container.FavouritesContainerViewModel
import org.skepsun.kototoro.favourites.domain.GlobalFavoritesState
import org.skepsun.kototoro.favourites.ui.migration.compose.SourceMigrationPanel
import org.skepsun.kototoro.main.ui.MainActivity
import org.skepsun.kototoro.main.ui.SearchBarFilterViewController
import org.skepsun.kototoro.main.ui.compose.CompactFilterRailOverrideState
import org.skepsun.kototoro.main.ui.compose.FavoritesTopBarOverrideState
import org.skepsun.kototoro.main.ui.compose.CompactTabsTopBarOverrideState
import org.skepsun.kototoro.main.ui.compose.CompactTopBarTabItem
import org.skepsun.kototoro.main.ui.compose.TopBarOverrideState
import org.skepsun.kototoro.parsers.model.Content

private const val FavoritesAutofilterLogTag = "FavoritesAutofilter"
private const val MainRouteFlickerLogTag = "MainRouteFlicker"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KototoroFavoritesHostRoute(
    appRouter: AppRouter,
    contentPadding: PaddingValues,
    initialCategoryId: Long = NO_ID,
    initialCategoryTitle: String? = null,
    onNavigateToDetails: ((Content, String?) -> Unit)? = null,
    registerFilterCallback: Boolean = true,
    onTopBarOverrideChanged: (TopBarOverrideState?) -> Unit = {},
    viewModel: FavouritesContainerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showMigrationPanel by viewModel.showMigrationPanel.collectAsStateWithLifecycle()

    val mainActivity = LocalContext.current as? MainActivity
    val globalState = viewModel.globalFavoritesState
    val selectedGroupTab by globalState.selectedGroupTab.collectAsStateWithLifecycle()
    val selectedSourceTags by globalState.selectedSourceTags.collectAsStateWithLifecycle()

    DisposableEffect(mainActivity, globalState, selectedGroupTab, selectedSourceTags, registerFilterCallback) {
        if (!registerFilterCallback) { onDispose { } }
        else {
            val callback = object : SearchBarFilterViewController.Callback {
                override fun isSourceTagFilterVisible() = true
                override fun getSourceTagEntries() = SourceTag.quickFilterEntries
                override fun getSelectedContentType() = selectedGroupTab
                override fun onContentTypeSelected(tab: BrowseGroupTab) {
                    globalState.setSelectedGroupTab(if (selectedGroupTab == tab) BrowseGroupTab.All else tab)
                }
                override fun getSelectedSourceTags() = selectedSourceTags
                override fun onSourceTagSelected(tag: SourceTag?) {
                    when {
                        tag == null -> globalState.clearSourceTags()
                        tag in selectedSourceTags -> globalState.setSelectedSourceTags(selectedSourceTags - tag)
                        else -> globalState.setSelectedSourceTags(selectedSourceTags + tag)
                    }
                }
            }
            mainActivity?.setActiveFilterCallback(callback)
            onDispose { mainActivity?.clearActiveFilterCallback(callback) }
        }
    }

    SideEffect {
        if (!registerFilterCallback) return@SideEffect
        mainActivity?.refreshFilters()
    }

    val displayCategories = remember(uiState.categories, initialCategoryId, initialCategoryTitle) {
        val categories = buildList {
            add(FavouriteTabModel(id = NO_ID, title = null))
            uiState.categories.filterTo(this) { it.id != NO_ID }
        }
        if (initialCategoryId == NO_ID || categories.any { it.id == initialCategoryId }) {
            categories
        } else {
            categories + FavouriteTabModel(id = initialCategoryId, title = initialCategoryTitle)
        }
    }
    val initialPage = remember(displayCategories, initialCategoryId) {
        displayCategories.indexOfFirst { it.id == initialCategoryId }.takeIf { it >= 0 } ?: 0
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { displayCategories.size },
    )
    val coroutineScope = rememberCoroutineScope()
    var initialSelectionApplied by rememberSaveable(initialCategoryId) { mutableStateOf(false) }
    var childTopBarOverrideState by remember { mutableStateOf<TopBarOverrideState?>(null) }
    var childFilterRailOverrideState by remember { mutableStateOf<CompactFilterRailOverrideState?>(null) }
    var childTopBarOverrideGeneration by remember { mutableIntStateOf(-1) }
    var childFilterRailOverrideGeneration by remember { mutableIntStateOf(-1) }
    var activeChildOverrideGeneration by remember { mutableIntStateOf(0) }
    var lastActiveCategoryId by remember { mutableStateOf<Long?>(null) }
    var lastStableChildFilterRailOverrideState by remember { mutableStateOf<CompactFilterRailOverrideState?>(null) }
    val allFavouritesLabel = stringResource(R.string.all_favourites)
    val activePage = pagerState.settledPage.coerceIn(0, (displayCategories.size - 1).coerceAtLeast(0))
    val selectedTabsPage = pagerState.targetPage.coerceIn(0, (displayCategories.size - 1).coerceAtLeast(0))
    val activeCategoryId = displayCategories.getOrNull(activePage)?.id

    val innerPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
        end = contentPadding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
        top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding(),
    )

    LaunchedEffect(
        uiState.isLoading,
        uiState.isEmpty,
        uiState.categories.size,
        displayCategories.size,
        activeCategoryId,
        contentPadding,
    ) {
        Log.d(
            MainRouteFlickerLogTag,
            "favorites host state loading=${uiState.isLoading} empty=${uiState.isEmpty} " +
                "categories=${uiState.categories.size} displayCategories=${displayCategories.size} " +
                "active=$activeCategoryId currentPage=${pagerState.currentPage} settledPage=${pagerState.settledPage} " +
                "paddingTop=${contentPadding.calculateTopPadding()} " +
                "paddingBottom=${contentPadding.calculateBottomPadding()}",
        )
    }
    LaunchedEffect(displayCategories, initialCategoryId, initialSelectionApplied) {
        if (initialSelectionApplied || displayCategories.isEmpty()) {
            return@LaunchedEffect
        }
        val targetPage = displayCategories.indexOfFirst { it.id == initialCategoryId }.takeIf { it >= 0 } ?: 0
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
        initialSelectionApplied = true
    }

    LaunchedEffect(activeCategoryId) {
        val previousActiveCategoryId = lastActiveCategoryId
        if (previousActiveCategoryId == activeCategoryId) {
            return@LaunchedEffect
        }
        lastActiveCategoryId = activeCategoryId
        if (previousActiveCategoryId == null) {
            Log.d(
                FavoritesAutofilterLogTag,
                "host activeCategoryInitial active=$activeCategoryId generation=$activeChildOverrideGeneration",
            )
            return@LaunchedEffect
        }
        val nextGeneration = activeChildOverrideGeneration + 1
        Log.d(
            FavoritesAutofilterLogTag,
            "host activeCategoryChanged previous=$previousActiveCategoryId active=$activeCategoryId generation=$nextGeneration",
        )
        activeChildOverrideGeneration = nextGeneration
        childTopBarOverrideState = null
        childFilterRailOverrideState = null
    }

    val compactTabsState = remember(displayCategories, selectedTabsPage) {
        CompactTabsTopBarOverrideState(
            items = displayCategories.map {
                CompactTopBarTabItem(
                    id = it.id,
                    title = if (it.id == NO_ID) allFavouritesLabel else (it.title ?: ""),
                )
            },
            selectedItemId = displayCategories.getOrNull(selectedTabsPage)?.id ?: NO_ID,
            onItemSelected = { categoryId ->
                val targetPage = displayCategories.indexOfFirst { it.id == categoryId }
                if (targetPage >= 0) {
                    coroutineScope.launch { pagerState.animateScrollToPage(targetPage) }
                }
            },
        )
    }
    LaunchedEffect(
        pagerState.currentPage,
        pagerState.settledPage,
        pagerState.targetPage,
        activePage,
        selectedTabsPage,
        activeCategoryId,
        compactTabsState.selectedItemId,
    ) {
        Log.d(
            FavoritesAutofilterLogTag,
            "host pager current=${pagerState.currentPage} settled=${pagerState.settledPage} target=${pagerState.targetPage} " +
                "activePage=$activePage selectedTabsPage=$selectedTabsPage " +
                "activeCategory=$activeCategoryId selectedTab=${compactTabsState.selectedItemId} " +
                "tabs=${compactTabsState.items.size}",
        )
    }

    val effectiveChildTopBarOverrideState = childTopBarOverrideState.takeIf {
        !uiState.isLoading &&
            !uiState.isEmpty &&
            childTopBarOverrideGeneration == activeChildOverrideGeneration &&
            (it as? org.skepsun.kototoro.main.ui.compose.ContentSelectionTopBarOverrideState) != null
    }
    val effectiveChildFilterRailOverrideState = childFilterRailOverrideState.takeIf {
        !uiState.isLoading &&
            !uiState.isEmpty &&
            childFilterRailOverrideGeneration == activeChildOverrideGeneration
    }
    if (effectiveChildFilterRailOverrideState != null) {
        lastStableChildFilterRailOverrideState = effectiveChildFilterRailOverrideState
    }
    val topBarFilterRailOverrideState = effectiveChildFilterRailOverrideState ?: lastStableChildFilterRailOverrideState

    LaunchedEffect(
        activeCategoryId,
        uiState.isLoading,
        uiState.isEmpty,
        childFilterRailOverrideState,
        effectiveChildFilterRailOverrideState,
        childFilterRailOverrideGeneration,
        activeChildOverrideGeneration,
    ) {
        Log.d(
            FavoritesAutofilterLogTag,
            "host resolve active=$activeCategoryId loading=${uiState.isLoading} empty=${uiState.isEmpty} " +
                "rawRailItems=${childFilterRailOverrideState?.items?.size ?: -1} " +
                "effectiveRailItems=${effectiveChildFilterRailOverrideState?.items?.size ?: -1} " +
                "childGen=$childFilterRailOverrideGeneration activeGen=$activeChildOverrideGeneration",
        )
    }

    val favoritesTopBarOverrideState = remember(
        compactTabsState,
        effectiveChildTopBarOverrideState,
    ) {
        FavoritesTopBarOverrideState(
            tabsState = compactTabsState,
            contextualOverrideState = effectiveChildTopBarOverrideState,
        )
    }

    SideEffect {
        if (uiState.isLoading) {
            Log.d(
                FavoritesAutofilterLogTag,
                "host skipEmitLoading active=$activeCategoryId tabs=${favoritesTopBarOverrideState.tabsState.items.size} " +
                    "railItems=${favoritesTopBarOverrideState.filterRailState?.items?.size ?: -1}",
            )
            return@SideEffect
        }
        Log.d(
            FavoritesAutofilterLogTag,
            "host emit active=$activeCategoryId tabs=${favoritesTopBarOverrideState.tabsState.items.size} " +
                "railItems=${favoritesTopBarOverrideState.filterRailState?.items?.size ?: -1} " +
                "rawRailItems=${effectiveChildFilterRailOverrideState?.items?.size ?: -1} " +
                "contextual=${favoritesTopBarOverrideState.contextualOverrideState?.javaClass?.simpleName}",
        )
        onTopBarOverrideChanged(favoritesTopBarOverrideState)
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d(FavoritesAutofilterLogTag, "host dispose keep routeScopedState active=$activeCategoryId")
        }
    }

    val hazeState = remember { HazeState() }
    val useBackgroundHaze = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.isEmpty) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_empty_favourites), null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.text_empty_holder_primary), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.you_have_not_favourites_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (useBackgroundHaze) Modifier.haze(hazeState) else Modifier),
            ) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val category = displayCategories.getOrNull(page)
                    if (category != null) {
                        val enabled = page == activePage
                        KototoroFavoritesListScreen(
                            categoryId = category.id,
                            appRouter = appRouter,
                            contentPadding = innerPadding,
                            onNavigateToDetails = onNavigateToDetails,
                            sharedTransitionEnabled = enabled,
                            isActivePage = enabled,
                            onTopBarOverrideChanged = { overrideState ->
                                val accepted = enabled && category.id == activeCategoryId
                                Log.d(
                                    FavoritesAutofilterLogTag,
                                    "host childTopBar category=${category.id} active=$activeCategoryId enabled=$enabled " +
                                        "accepted=$accepted state=${overrideState?.javaClass?.simpleName} " +
                                        "generation=$activeChildOverrideGeneration",
                                )
                                if (accepted) {
                                    childTopBarOverrideState = overrideState
                                    childTopBarOverrideGeneration = activeChildOverrideGeneration
                                }
                            },
                            onFilterRailOverrideChanged = { overrideState ->
                                Log.d(
                                    FavoritesAutofilterLogTag,
                                    "host childRail category=${category.id} active=$activeCategoryId enabled=$enabled " +
                                        "accepted=false inline=true railItems=${overrideState?.items?.size ?: -1} " +
                                        "generation=$activeChildOverrideGeneration",
                                )
                            },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showMigrationPanel,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                SourceMigrationPanel(
                    onDismiss = { viewModel.hideMigrationPanel() },
                )
            }
        }
    }
}
