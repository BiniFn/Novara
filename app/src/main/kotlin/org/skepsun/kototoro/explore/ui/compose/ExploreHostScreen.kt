package org.skepsun.kototoro.explore.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.discover.ui.DiscoverViewModel
import org.skepsun.kototoro.discover.ui.compose.DiscoverScreen
import org.skepsun.kototoro.core.prefs.AppSettings
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KototoroExploreHostRoute(
    appRouter: AppRouter,
    contentPadding: PaddingValues,
    exploreViewModel: org.skepsun.kototoro.explore.ui.ExploreViewModel = hiltViewModel(),
    discoverViewModel: DiscoverViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    val titles = listOf(
        stringResource(id = R.string.explore_tab_sources),
        stringResource(id = R.string.explore_tab_tracking_sites)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(text = title) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> {
                    KototoroExploreSourcesScreen(
                        viewModel = exploreViewModel,
                        contentPadding = contentPadding,
                        appRouter = appRouter
                    )
                }
                1 -> {
                    val items by discoverViewModel.content.collectAsState(initial = emptyList())
                    val isLoading by discoverViewModel.isLoading.collectAsState(initial = false)
                    val query by discoverViewModel.query.collectAsState(initial = "")
                    
                    val isLoadingOnly = items.size == 1 && items.first() is org.skepsun.kototoro.list.ui.model.LoadingState
                    val isCarousel = items.firstOrNull() is org.skepsun.kototoro.discover.ui.model.DiscoverCarouselRow || (items.firstOrNull() is org.skepsun.kototoro.list.ui.model.EmptyState && query.isBlank())
                    
                    DiscoverScreen(
                        contentPadding = contentPadding,
                        items = items,
                        isRefreshing = isLoading && !isLoadingOnly,
                        isCarousel = isCarousel,
                        isLoadingOnly = isLoadingOnly,
                        gridSpanCount = (discoverViewModel.settings.gridSize / 100f * 3).toInt().coerceAtLeast(1),
                        onRefresh = { discoverViewModel.refresh() },
                        onLoadMore = { discoverViewModel.loadNextPage() },
                        onItemClick = { item ->
                            val serviceName = item.manga.source.name.removePrefix("TRACKING_")
                            val trackingService = discoverViewModel.availableServices.value.find { it.name == serviceName } ?: return@DiscoverScreen
                            if (discoverViewModel.supportsDetails(trackingService)) {
                                appRouter.openTrackingSiteDetails(trackingService, item.manga.id, item.manga.publicUrl)
                            } else {
                                val url = item.manga.url ?: item.manga.publicUrl
                                if (!url.isNullOrBlank()) {
                                    appRouter.openExternalBrowser(url)
                                }
                            }
                        },
                        onCategoryMoreClick = { category ->
                            val service = discoverViewModel.activeService.value
                            appRouter.openTrackingDiscoveryCategory(service, category.id, category.nameResId)
                        }
                    )
                }
            }
        }
    }
}
