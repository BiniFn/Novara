package org.skepsun.kototoro.favourites.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.favourites.ui.container.FavouritesContainerViewModel
import org.skepsun.kototoro.core.model.FavouriteCategory.Companion.NO_ID
import org.skepsun.kototoro.list.ui.compose.AppContentListRoute
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KototoroFavoritesHostRoute(
    appRouter: AppRouter,
    contentPadding: PaddingValues,
    viewModel: FavouritesContainerViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle(emptyList())
    val isEmpty by viewModel.isEmpty.collectAsStateWithLifecycle(false)

    if (categories.isEmpty() && !isEmpty) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (isEmpty) {
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_empty_favourites),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(id = R.string.text_empty_holder_primary),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.you_have_not_favourites_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { categories.size })
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            edgePadding = 8.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            categories.forEachIndexed { index, tabModel ->
                val title = if (tabModel.id == NO_ID) stringResource(id = R.string.all_favourites) else tabModel.title ?: ""
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
            val category = categories.getOrNull(page)
            if (category != null) {
                KototoroFavoritesListScreen(
                    categoryId = category.id,
                    appRouter = appRouter,
                    contentPadding = contentPadding
                )
            }
        }
    }
}
