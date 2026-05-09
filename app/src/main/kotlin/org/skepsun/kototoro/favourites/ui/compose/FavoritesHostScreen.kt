package org.skepsun.kototoro.favourites.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.FavouriteCategory.Companion.NO_ID
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.favourites.ui.container.FavouriteTabModel
import org.skepsun.kototoro.favourites.ui.container.FavouritesContainerViewModel
import org.skepsun.kototoro.favourites.domain.GlobalFavoritesState
import org.skepsun.kototoro.main.ui.MainActivity
import org.skepsun.kototoro.main.ui.SearchBarFilterViewController
import org.skepsun.kototoro.main.ui.compose.TopBarOverrideState
import org.skepsun.kototoro.parsers.model.Content

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
    val categories by viewModel.categories.collectAsStateWithLifecycle(emptyList())
    val isEmpty by viewModel.isEmpty.collectAsStateWithLifecycle(false)

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

    if (categories.isEmpty() && !isEmpty) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (isEmpty) {
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

    val displayCategories = remember(categories, initialCategoryId, initialCategoryTitle) {
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

    val innerPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
        end = contentPadding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
        top = 0.dp,
        bottom = contentPadding.calculateBottomPadding(),
    )

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

    Column(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            edgePadding = 12.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            displayCategories.forEachIndexed { index, tabModel ->
                val title = if (tabModel.id == NO_ID) stringResource(R.string.all_favourites) else tabModel.title ?: ""
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title) }
                )
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val category = displayCategories.getOrNull(page)
            if (category != null) {
                val enabled = page == pagerState.currentPage && !pagerState.isScrollInProgress
                KototoroFavoritesListScreen(
                    categoryId = category.id,
                    appRouter = appRouter,
                    contentPadding = innerPadding,
                    onNavigateToDetails = onNavigateToDetails,
                    sharedTransitionEnabled = enabled,
                    onTopBarOverrideChanged = onTopBarOverrideChanged,
                )
            }
        }
    }
}
