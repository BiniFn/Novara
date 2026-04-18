package org.skepsun.kototoro.search.ui.compose

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.list.ui.compose.KototoroContentListScreen
import org.skepsun.kototoro.remotelist.ui.RemoteListViewModel
import kotlin.math.absoluteValue
import org.skepsun.kototoro.core.prefs.ListMode
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSearchContentListRoute(
    appRouter: AppRouter,
    viewModel: RemoteListViewModel = hiltViewModel()
) {
    val items by viewModel.content.collectAsStateWithLifecycle(emptyList())
    val isRandomLoading by viewModel.isRandomLoading.collectAsStateWithLifecycle(false)
    val filterSnapshot by viewModel.filterCoordinator.observe().collectAsStateWithLifecycle(viewModel.filterCoordinator.snapshot())
    val listMode by viewModel.listMode.collectAsStateWithLifecycle(ListMode.GRID)

    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf(filterSnapshot.listFilter.query.orEmpty()) }
    val focusRequester = remember { FocusRequester() }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        topBar = {
            if (searchMode) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = { Text(stringResource(R.string.search)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    viewModel.filterCoordinator.setQuery(searchQuery.takeIf { it.isNotEmpty() })
                                    searchMode = false
                                }
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { searchMode = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Search")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(viewModel.source.name) // Use the source name as title
                            if (!filterSnapshot.listFilter.query.isNullOrEmpty()) {
                                Text(
                                    text = filterSnapshot.listFilter.query!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        val context = LocalContext.current
                        IconButton(onClick = { (context as? android.app.Activity)?.finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchMode = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(
                            onClick = { viewModel.openRandom() },
                            enabled = !isRandomLoading
                        ) {
                            Icon(painterResource(R.drawable.ic_dice), contentDescription = "Random")
                        }
                        Box {
                            IconButton(onClick = {
                                appRouter.showFilterSheet()
                            }) {
                                Icon(painterResource(R.drawable.ic_filter_menu), contentDescription = "Filter")
                            }
                            if (filterSnapshot.listFilter.hasNonSearchOptions()) {
                                Badge(
                                    modifier = Modifier.padding(top = 8.dp, end = 8.dp)
                                )
                            }
                        }
                        if (viewModel.filterCoordinator.isFilterApplied) {
                            IconButton(onClick = { viewModel.filterCoordinator.reset() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Reset Filter")
                            }
                        }
                        IconButton(onClick = {
                            appRouter.openSourceSettings(viewModel.source)
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { paddingValues ->
        KototoroContentListScreen(
            items = items,
            gridScale = 1.0f,
            listMode = listMode,
            isRefreshing = false,
            contentPadding = paddingValues,
            onItemClick = { item -> appRouter.openDetails(item.manga) },
            onItemLongClick = { },
            onLoadMore = { viewModel.loadNextPage() },
            onRefresh = { viewModel.onRefresh() },
            onClearSelection = { },
            onSelectionAction = { false },
            selectedItemsIds = emptySet()
        )
    }
}
