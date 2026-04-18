package org.skepsun.kototoro.scrobbling.common.ui.selector.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.exceptions.resolve.ExceptionResolver
import org.skepsun.kototoro.core.util.ext.getDisplayMessage
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingFooter
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContent
import org.skepsun.kototoro.scrobbling.common.ui.selector.ScrobblingSelectorViewModel
import org.skepsun.kototoro.scrobbling.common.ui.selector.model.ScrobblerHint
import androidx.compose.foundation.lazy.rememberLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrobblingSelectorDialog(
    viewModel: ScrobblingSelectorViewModel,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.9f) // Take up mos of the screen like a standard sheet
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header / Search
            ScrobblerHeader(
                viewModel = viewModel,
                onClose = {
                    coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            onDismissRequest()
                        }
                    }
                }
            )

            // Content List
            ScrobblerListContent(viewModel = viewModel)
        }
    }

    // Observe onClose events from view model
    LaunchedEffect(viewModel) {
        viewModel.onClose.collect {
            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) {
                    onDismissRequest()
                }
            }
        }
    }
}

@Composable
private fun ScrobblerHeader(
    viewModel: ScrobblingSelectorViewModel,
    onClose: () -> Unit
) {
    val selectedIndex by viewModel.selectedScrobblerIndex.collectAsState()
    val scrobblers = viewModel.availableScrobblers
    var searchQuery by remember { mutableStateOf(viewModel.manga.title) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.search)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { viewModel.search(searchQuery) }) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
        }

        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            edgePadding = 16.dp,
            divider = {}
        ) {
            scrobblers.forEachIndexed { index, scrobbler ->
                val isAuth = remember(index) { viewModel.isScrobblerAuthorized(index) }
                val titleRes = scrobbler.scrobblerService.titleResId
                val iconRes = scrobbler.scrobblerService.iconResId
                
                Tab(
                    selected = selectedIndex == index,
                    onClick = { viewModel.setScrobblerIndex(index) },
                    text = { 
                        Text(
                            text = if (isAuth) stringResource(titleRes) else stringResource(R.string.scrobbler_search_requires_login_label, stringResource(titleRes)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    icon = { Icon(painterResource(iconRes), contentDescription = null) }
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.ScrobblerListContent(
    viewModel: ScrobblingSelectorViewModel
) {
    val items by viewModel.content.collectAsState()
    val selectedItemId by viewModel.selectedItemId.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val listState = rememberLazyListState()

    // Trigger pagination when reaching the end
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 2
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadNextPage()
        }
    }

    Box(modifier = Modifier.weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(
                items = items,
                key = { item -> 
                    when (item) {
                        is ScrobblerContent -> item.id
                        is ScrobblerHint -> "hint_${item.textPrimary}"
                        is LoadingState -> "loading"
                        is LoadingFooter -> "loading_footer"
                        else -> item.hashCode()
                    }
                }
            ) { item ->
                when (item) {
                    is ScrobblerContent -> {
                        ScrobblerContentItem(
                            item = item,
                            isSelected = item.id == selectedItemId,
                            onClick = { viewModel.selectItem(item.id) }
                        )
                    }
                    is ScrobblerHint -> {
                        HintItem(item = item, onRetry = { 
                            if (item.error != null) {
                                viewModel.retry()
                            } else {
                                // Empty state search trigger
                            }
                        })
                    }
                    is LoadingState -> {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is LoadingFooter -> {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }

    // Bottom Action Bar
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = { viewModel.onDoneClick() },
                enabled = !isLoading && selectedItemId != androidx.recyclerview.widget.RecyclerView.NO_ID
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun ScrobblerContentItem(
    item: ScrobblerContent,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.cover)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.mediaType.isNullOrEmpty()) {
                    Text(
                        text = item.mediaType,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun HintItem(
    item: ScrobblerHint,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(item.textPrimary),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        if (item.error != null) {
            Text(
                text = item.error.getDisplayMessage(context.resources),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (item.actionStringRes != 0) {
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(stringResource(item.actionStringRes))
            }
        }
    }
}
