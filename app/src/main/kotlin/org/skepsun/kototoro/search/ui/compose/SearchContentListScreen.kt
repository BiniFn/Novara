package org.skepsun.kototoro.search.ui.compose

import android.app.Activity
import android.content.res.Configuration
import androidx.core.text.HtmlCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.titleResId
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.compose.rememberResolvedSourceTitle
import org.skepsun.kototoro.core.ui.model.titleRes
import org.skepsun.kototoro.filter.ui.model.UiTagGroup
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.list.ui.compose.KototoroContentListScreen
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.QuickFilter
import org.skepsun.kototoro.remotelist.ui.RemoteListViewModel
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentState
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.SortOrder
import java.util.Locale

private val SearchTopActionsHeight = 56.dp
private val SearchTitleRowHeight = 72.dp

private enum class SearchSidePaneMode {
    Filter,
    Preview,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSearchContentListRoute(
    appRouter: AppRouter,
    viewModel: RemoteListViewModel = hiltViewModel(),
) {
    val items by viewModel.content.collectAsStateWithLifecycle(emptyList())
    val isRandomLoading by viewModel.isRandomLoading.collectAsStateWithLifecycle(false)
    val filterSnapshot by viewModel.filterCoordinator.observe()
        .collectAsStateWithLifecycle(viewModel.filterCoordinator.snapshot())
    val listMode by viewModel.listMode.collectAsStateWithLifecycle(ListMode.GRID)
    val resolvedSourceTitle = rememberResolvedSourceTitle(viewModel.source)
    val sortOrderProperty by viewModel.filterCoordinator.sortOrder.collectAsStateWithLifecycle()
    val tagsProperty by viewModel.filterCoordinator.tags.collectAsStateWithLifecycle()
    val contentTypesProperty by viewModel.filterCoordinator.contentTypes.collectAsStateWithLifecycle()
    val statesProperty by viewModel.filterCoordinator.states.collectAsStateWithLifecycle()
    val localeProperty by viewModel.filterCoordinator.locale.collectAsStateWithLifecycle()
    val authorsProperty by viewModel.filterCoordinator.authors.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val settings = remember(context.applicationContext) { AppSettings(context.applicationContext) }
    val gridSize = settings.observeAsState(AppSettings.KEY_GRID_SIZE) { gridSize }.value
    val gridScale = gridSize / 100f
    val isWideAdaptiveLayout = remember(configuration.orientation, configuration.screenWidthDp) {
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 720
    }

    val quickFilter = remember(items) {
        items.filterIsInstance<QuickFilter>().firstOrNull()
    }
    val contentItems = remember(items) {
        items.filterNot { it is QuickFilter }
    }

    var searchMode by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf(filterSnapshot.listFilter.query.orEmpty()) }
    var collapseOffsetPx by rememberSaveable { mutableStateOf(0f) }
    var showFilterPanel by rememberSaveable(isWideAdaptiveLayout) { mutableStateOf(false) }
    var sidePaneMode by rememberSaveable(isWideAdaptiveLayout) { mutableStateOf(SearchSidePaneMode.Filter) }
    var previewContent by remember { mutableStateOf<Content?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(filterSnapshot.listFilter.query, searchMode) {
        if (!searchMode) {
            searchQuery = filterSnapshot.listFilter.query.orEmpty()
        }
    }

    LaunchedEffect(viewModel.onOpenContent) {
        viewModel.onOpenContent.collect { event ->
            event?.consume { content ->
                appRouter.openDetails(content, null)
            }
        }
    }

    LaunchedEffect(isWideAdaptiveLayout) {
        if (isWideAdaptiveLayout) {
            sidePaneMode = SearchSidePaneMode.Filter
        } else {
            previewContent = null
            sidePaneMode = SearchSidePaneMode.Filter
            showFilterPanel = false
        }
    }

    LaunchedEffect(contentItems) {
        val previewId = previewContent?.id ?: return@LaunchedEffect
        if (contentItems.filterIsInstance<ContentListModel>().none { it.id == previewId }) {
            previewContent = null
            sidePaneMode = SearchSidePaneMode.Filter
        }
    }

    val topActionsHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        SearchTopActionsHeight.toPx()
    }
    val titleRowHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        SearchTitleRowHeight.toPx()
    }
    val maxCollapsePx = topActionsHeightPx + titleRowHeightPx
    val nestedScrollConnection = remember(maxCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (searchMode) return Offset.Zero
                val delta = -available.y
                if (delta == 0f) return Offset.Zero
                val newOffset = (collapseOffsetPx + delta).coerceIn(0f, maxCollapsePx)
                val consumed = newOffset - collapseOffsetPx
                collapseOffsetPx = newOffset
                return Offset(x = 0f, y = -consumed)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            SearchContentTopBar(
                searchMode = searchMode,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchOpen = { searchMode = true },
                onSearchClose = { searchMode = false },
                onSearchSubmit = {
                    viewModel.filterCoordinator.setQuery(searchQuery.takeIf { it.isNotBlank() })
                    searchMode = false
                },
                focusRequester = focusRequester,
                sourceTitle = resolvedSourceTitle,
                activeQuery = filterSnapshot.listFilter.query,
                currentSortLabel = stringResource(filterSnapshot.sortOrder.titleRes),
                isFilterApplied = viewModel.filterCoordinator.isFilterApplied,
                quickFilter = quickFilter,
                contentItems = contentItems.filterIsInstance<ContentListModel>(),
                selectedTags = filterSnapshot.listFilter.tags,
                availableTags = tagsProperty.availableItems.flatMap { it.tags },
                listMode = listMode,
                gridSize = gridSize,
                topActionsHeight = SearchTopActionsHeight,
                titleRowHeight = SearchTitleRowHeight,
                collapseOffsetPx = collapseOffsetPx,
                isRandomLoading = isRandomLoading,
                onBackClick = { (context as? Activity)?.finish() },
                onRandomClick = viewModel::openRandom,
                onFilterClick = {
                    if (isWideAdaptiveLayout) {
                        sidePaneMode = SearchSidePaneMode.Filter
                    } else {
                        showFilterPanel = !showFilterPanel
                    }
                },
                onResetFilterClick = viewModel.filterCoordinator::reset,
                onSettingsClick = { appRouter.openSourceSettings(viewModel.source) },
                onListModeChange = { settings.listMode = it },
                onGridSizeChange = { delta ->
                    settings.gridSize = (settings.gridSize + delta).coerceIn(50, 150)
                },
                onQuickFilterOptionClick = { option ->
                    (viewModel as? org.skepsun.kototoro.list.domain.QuickFilterListener)?.toggleFilterOption(option)
                },
                onToggleTag = { tag, selected -> viewModel.filterCoordinator.toggleTag(tag, selected) },
            )
        },
    ) { paddingValues ->
        if (isWideAdaptiveLayout && showFilterPanel) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(paddingValues),
            ) {
                KototoroContentListScreen(
                    items = contentItems,
                    gridScale = gridScale,
                    listMode = listMode,
                    isRefreshing = false,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(nestedScrollConnection),
                    onItemClick = { item ->
                        previewContent = item.toContentWithOverride()
                        sidePaneMode = SearchSidePaneMode.Preview
                    },
                    onItemLongClick = { },
                    onLoadMore = { viewModel.loadNextPage() },
                    onRefresh = { viewModel.onRefresh() },
                    onClearSelection = { },
                    onSelectionAction = { false },
                    selectedItemsIds = emptySet(),
                    onRetry = viewModel::onRetry,
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .padding(vertical = 12.dp)
                        .alpha(0.7f)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Box(
                    modifier = Modifier.widthIn(min = 320.dp, max = 380.dp),
                ) {
                    if (sidePaneMode == SearchSidePaneMode.Preview && previewContent != null) {
                        SearchPreviewPane(
                            content = requireNotNull(previewContent),
                            onBackToFilters = { sidePaneMode = SearchSidePaneMode.Filter },
                            onOpenDetails = { appRouter.openDetails(requireNotNull(previewContent), null) },
                        )
                    } else {
                        SearchFilterPanel(
                            sortOrders = sortOrderProperty.availableItems,
                            selectedSortOrder = sortOrderProperty.selectedItems.firstOrNull(),
                            tagGroups = tagsProperty.availableItems,
                            contentTypes = contentTypesProperty.availableItems,
                            selectedContentTypes = contentTypesProperty.selectedItems,
                            states = statesProperty.availableItems,
                            selectedStates = statesProperty.selectedItems,
                            locales = localeProperty.availableItems,
                            selectedLocale = localeProperty.selectedItems.firstOrNull(),
                            authors = authorsProperty.availableItems,
                            selectedAuthor = authorsProperty.selectedItems.firstOrNull(),
                            onSortOrderChange = viewModel.filterCoordinator::setSortOrder,
                            onToggleTag = { tag, selected -> viewModel.filterCoordinator.toggleTag(tag, selected) },
                            onToggleContentType = { type, selected -> viewModel.filterCoordinator.toggleContentType(type, selected) },
                            onToggleState = { state, selected -> viewModel.filterCoordinator.toggleState(state, selected) },
                            onLocaleChange = viewModel.filterCoordinator::setLocale,
                            onAuthorChange = viewModel.filterCoordinator::setAuthor,
                            onReset = viewModel.filterCoordinator::reset,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                }
            }
        } else {
            KototoroContentListScreen(
                items = contentItems,
                gridScale = gridScale,
                listMode = listMode,
                isRefreshing = false,
                contentPadding = paddingValues,
                modifier = Modifier.nestedScroll(nestedScrollConnection),
                onItemClick = { item -> appRouter.openDetails(item.manga, null) },
                onItemLongClick = { },
                onLoadMore = { viewModel.loadNextPage() },
                onRefresh = { viewModel.onRefresh() },
                onClearSelection = { },
                onSelectionAction = { false },
                selectedItemsIds = emptySet(),
                onRetry = viewModel::onRetry,
            )
        }

        if (!isWideAdaptiveLayout && showFilterPanel) {
            ModalBottomSheet(
                onDismissRequest = { showFilterPanel = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                SearchFilterPanel(
                    sortOrders = sortOrderProperty.availableItems,
                    selectedSortOrder = sortOrderProperty.selectedItems.firstOrNull(),
                    tagGroups = tagsProperty.availableItems,
                    contentTypes = contentTypesProperty.availableItems,
                    selectedContentTypes = contentTypesProperty.selectedItems,
                    states = statesProperty.availableItems,
                    selectedStates = statesProperty.selectedItems,
                    locales = localeProperty.availableItems,
                    selectedLocale = localeProperty.selectedItems.firstOrNull(),
                    authors = authorsProperty.availableItems,
                    selectedAuthor = authorsProperty.selectedItems.firstOrNull(),
                    onSortOrderChange = viewModel.filterCoordinator::setSortOrder,
                    onToggleTag = { tag, selected -> viewModel.filterCoordinator.toggleTag(tag, selected) },
                    onToggleContentType = { type, selected -> viewModel.filterCoordinator.toggleContentType(type, selected) },
                    onToggleState = { state, selected -> viewModel.filterCoordinator.toggleState(state, selected) },
                    onLocaleChange = viewModel.filterCoordinator::setLocale,
                    onAuthorChange = viewModel.filterCoordinator::setAuthor,
                    onReset = viewModel.filterCoordinator::reset,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchPreviewPane(
    content: Content,
    onBackToFilters: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val sourceTitle = rememberResolvedSourceTitle(content.source)
    val description = remember(content.description) {
        HtmlCompat.fromHtml(content.description.orEmpty(), HtmlCompat.FROM_HTML_MODE_COMPACT)
            .toString()
            .trim()
    }
    val primaryAuthor = content.authors.firstOrNull().orEmpty()

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.details),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = sourceTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onBackToFilters) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = stringResource(R.string.filter),
                )
            }
        }

        AsyncImage(
            model = content.largeCoverUrl ?: content.coverUrl,
            contentDescription = content.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = content.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (primaryAuthor.isNotBlank()) {
                Text(
                    text = primaryAuthor,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content.state?.let { state ->
                Text(
                    text = stringResource(state.titleResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        OutlinedButton(
            onClick = onOpenDetails,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_expand),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.details),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (description.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.description),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (content.tags.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.genres),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    content.tags.forEach { tag ->
                        FilterChip(
                            selected = false,
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    text = tag.title,
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchContentTopBar(
    searchMode: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchSubmit: () -> Unit,
    focusRequester: FocusRequester,
    sourceTitle: String,
    activeQuery: String?,
    currentSortLabel: String,
    isFilterApplied: Boolean,
    quickFilter: QuickFilter?,
    contentItems: List<ContentListModel>,
    selectedTags: Set<ContentTag>,
    availableTags: List<ContentTag>,
    listMode: ListMode,
    gridSize: Int,
    topActionsHeight: Dp,
    titleRowHeight: Dp,
    collapseOffsetPx: Float,
    isRandomLoading: Boolean,
    onBackClick: () -> Unit,
    onRandomClick: () -> Unit,
    onFilterClick: () -> Unit,
    onResetFilterClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onListModeChange: (ListMode) -> Unit,
    onGridSizeChange: (Int) -> Unit,
    onQuickFilterOptionClick: (ListFilterOption) -> Unit,
    onToggleTag: (ContentTag, Boolean) -> Unit,
) {
    val extractedTags = remember(contentItems, selectedTags, availableTags) {
        buildSourcePinnedTags(
            contentItems = contentItems,
            selectedTags = selectedTags,
            availableTags = availableTags,
        )
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val titleRowHeightPx = with(density) { titleRowHeight.toPx() }
    val topActionsHeightPx = with(density) { topActionsHeight.toPx() }
    val titleCollapsedPx = collapseOffsetPx.coerceIn(0f, titleRowHeightPx)
    val topActionsCollapsedPx = (collapseOffsetPx - titleRowHeightPx).coerceIn(0f, topActionsHeightPx)
    val titleVisibleHeight = with(density) { (titleRowHeightPx - titleCollapsedPx).coerceAtLeast(0f).toDp() }
    val topActionsVisibleHeight = with(density) { (topActionsHeightPx - topActionsCollapsedPx).coerceAtLeast(0f).toDp() }
    val compactTitleAlpha = (titleCollapsedPx / titleRowHeightPx).coerceIn(0f, 1f)
    val compactTopBarAlpha = if (topActionsHeightPx == 0f) 1f else {
        ((topActionsHeightPx - topActionsCollapsedPx) / topActionsHeightPx).coerceIn(0f, 1f)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .clipToBounds()
                .padding(top = 4.dp)
                .alpha(0.998f),
        ) {
            if (searchMode) {
                SearchInputRow(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    onClose = onSearchClose,
                    onSubmit = onSearchSubmit,
                    focusRequester = focusRequester,
                )
            } else {
                CollapsingBarSlot(
                    visibleHeight = topActionsVisibleHeight,
                    fullHeight = topActionsHeight,
                ) {
                    SourceListTopActionsRow(
                        title = sourceTitle,
                        compactTitleAlpha = compactTitleAlpha * compactTopBarAlpha,
                        listMode = listMode,
                        gridSize = gridSize,
                        isFilterApplied = isFilterApplied,
                        isRandomLoading = isRandomLoading,
                        onBackClick = onBackClick,
                        onSearchClick = onSearchOpen,
                        onRandomClick = onRandomClick,
                        onResetFilterClick = onResetFilterClick,
                        onSettingsClick = onSettingsClick,
                        onListModeChange = onListModeChange,
                        onGridSizeChange = onGridSizeChange,
                    )
                }
                CollapsingBarSlot(
                    visibleHeight = titleVisibleHeight,
                    fullHeight = titleRowHeight,
                ) {
                    SourceListTitleRow(
                        sourceTitle = sourceTitle,
                        activeQuery = activeQuery,
                        currentSortLabel = currentSortLabel,
                        isFilterApplied = isFilterApplied,
                        onFilterClick = onFilterClick,
                    )
                }
            }

            if (quickFilter != null) {
                QuickFilterPinnedRow(
                    quickFilter = quickFilter,
                    onQuickFilterOptionClick = onQuickFilterOptionClick,
                )
            } else {
                SourceTagsPinnedRow(
                    tags = extractedTags,
                    selectedTags = selectedTags,
                    onToggleTag = onToggleTag,
                )
            }

            if (!activeQuery.isNullOrBlank()) {
                ActiveQueryRow(query = activeQuery)
            }
        }
    }
}

private fun buildSourcePinnedTags(
    contentItems: List<ContentListModel>,
    selectedTags: Set<ContentTag>,
    availableTags: List<ContentTag>,
    limit: Int = 16,
): List<ContentTag> {
    val counts = LinkedHashMap<ContentTag, Int>()
    contentItems.forEach { item ->
        item.manga.tags.forEach { tag ->
            counts[tag] = (counts[tag] ?: 0) + 1
        }
    }
    val frequencyOrdered = counts.entries
        .sortedWith(compareByDescending<Map.Entry<ContentTag, Int>> { it.value }.thenBy { it.key.title })
        .map { it.key }
    val fallbackTags = availableTags
        .asSequence()
        .distinct()
        .filterNot { counts.containsKey(it) }
        .sortedBy { it.title }
        .toList()
    return buildList(limit) {
        selectedTags
            .sortedBy { it.title }
            .forEach { tag ->
                if (tag !in this) {
                    add(tag)
                }
            }
        frequencyOrdered.forEach { tag ->
            if (size >= limit) return@forEach
            if (tag !in this) {
                add(tag)
            }
        }
        fallbackTags.forEach { tag ->
            if (size >= limit) return@forEach
            if (tag !in this) {
                add(tag)
            }
        }
    }
}

@Composable
private fun SearchInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
) {
    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SearchTopActionsHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.close),
            )
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.search)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = stringResource(R.string.clear),
                )
            }
        }
    }
}

@Composable
private fun SourceListTopActionsRow(
    title: String,
    compactTitleAlpha: Float,
    listMode: ListMode,
    gridSize: Int,
    isFilterApplied: Boolean,
    isRandomLoading: Boolean,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onRandomClick: () -> Unit,
    onResetFilterClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onListModeChange: (ListMode) -> Unit,
    onGridSizeChange: (Int) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val maxWidthDp = maxWidth.value
        val showRandomDirect = maxWidthDp >= 356f
        val showDisplayDirect = maxWidthDp >= 412f
        val showSettingsDirect = maxWidthDp >= 468f
        val shouldShowOverflow = !showRandomDirect || !showDisplayDirect || !showSettingsDirect || isFilterApplied

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SearchTopActionsHeight)
                .padding(start = 4.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .alpha(compactTitleAlpha),
            )

            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search),
                )
            }

            if (showRandomDirect) {
                IconButton(onClick = onRandomClick, enabled = !isRandomLoading) {
                    Icon(
                        painter = painterResource(R.drawable.ic_dice),
                        contentDescription = stringResource(R.string.random),
                    )
                }
            }

            if (showDisplayDirect) {
                DisplayOptionsButton(
                    listMode = listMode,
                    gridSize = gridSize,
                    onListModeChange = onListModeChange,
                    onGridSizeChange = onGridSizeChange,
                )
            }

            if (showSettingsDirect) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.settings),
                    )
                }
            }

            if (shouldShowOverflow) {
                MoreActionsButton(
                    showRandomAction = !showRandomDirect,
                    showDisplayActions = !showDisplayDirect,
                    showSettingsAction = !showSettingsDirect,
                    listMode = listMode,
                    gridSize = gridSize,
                    isFilterApplied = isFilterApplied,
                    isRandomLoading = isRandomLoading,
                    onRandomClick = onRandomClick,
                    onResetFilterClick = onResetFilterClick,
                    onSettingsClick = onSettingsClick,
                    onListModeChange = onListModeChange,
                    onGridSizeChange = onGridSizeChange,
                )
            }
        }
    }
}

@Composable
private fun SourceListTitleRow(
    sourceTitle: String,
    activeQuery: String?,
    currentSortLabel: String,
    isFilterApplied: Boolean,
    onFilterClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SearchTitleRowHeight)
            .padding(start = 16.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = sourceTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!activeQuery.isNullOrBlank()) {
                Text(
                    text = activeQuery,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        BadgedBox(
            badge = {
                if (isFilterApplied) {
                    Badge()
                }
            },
        ) {
            TextButton(
                onClick = onFilterClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = currentSortLabel,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_filter_menu),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DisplayOptionsButton(
    listMode: ListMode,
    gridSize: Int,
    onListModeChange: (ListMode) -> Unit,
    onGridSizeChange: (Int) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(listMode.iconRes()),
                contentDescription = stringResource(R.string.list_options),
            )
        }
        DisplayOptionsMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            listMode = listMode,
            gridSize = gridSize,
            onListModeChange = {
                expanded = false
                onListModeChange(it)
            },
            onGridSizeChange = onGridSizeChange,
        )
    }
}

@Composable
private fun MoreActionsButton(
    showRandomAction: Boolean,
    showDisplayActions: Boolean,
    showSettingsAction: Boolean,
    listMode: ListMode,
    gridSize: Int,
    isFilterApplied: Boolean,
    isRandomLoading: Boolean,
    onRandomClick: () -> Unit,
    onResetFilterClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onListModeChange: (ListMode) -> Unit,
    onGridSizeChange: (Int) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (showRandomAction) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.random)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_dice),
                            contentDescription = null,
                        )
                    },
                    enabled = !isRandomLoading,
                    onClick = {
                        expanded = false
                        onRandomClick()
                    },
                )
            }

            if (showSettingsAction) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSettingsClick()
                    },
                )
            }

            if (showDisplayActions) {
                HorizontalDivider()
                DisplayOptionsMenuContent(
                    listMode = listMode,
                    gridSize = gridSize,
                    onListModeChange = {
                        expanded = false
                        onListModeChange(it)
                    },
                    onGridSizeChange = onGridSizeChange,
                )
            }

            if (isFilterApplied) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reset_filter)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onResetFilterClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun DisplayOptionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    listMode: ListMode,
    gridSize: Int,
    onListModeChange: (ListMode) -> Unit,
    onGridSizeChange: (Int) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DisplayOptionsMenuContent(
            listMode = listMode,
            gridSize = gridSize,
            onListModeChange = onListModeChange,
            onGridSizeChange = onGridSizeChange,
        )
    }
}

@Composable
private fun DisplayOptionsMenuContent(
    listMode: ListMode,
    gridSize: Int,
    onListModeChange: (ListMode) -> Unit,
    onGridSizeChange: (Int) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.compact)) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_list),
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (listMode == ListMode.LIST) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                )
            }
        },
        onClick = { onListModeChange(ListMode.LIST) },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.details)) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_list_detailed),
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (listMode == ListMode.DETAILED_LIST) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                )
            }
        },
        onClick = { onListModeChange(ListMode.DETAILED_LIST) },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.grid)) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_grid),
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (listMode == ListMode.GRID) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                )
            }
        },
        onClick = { onListModeChange(ListMode.GRID) },
    )
    HorizontalDivider()
    Column(
        modifier = Modifier
            .widthIn(min = 240.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${stringResource(R.string.grid_size)} ${gridSize}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = gridSize.toFloat(),
            onValueChange = { value ->
                onGridSizeChange(value.toInt() - gridSize)
            },
            valueRange = 50f..150f,
        )
    }
}

@Composable
private fun CollapsingBarSlot(
    visibleHeight: Dp,
    fullHeight: Dp,
    content: @Composable () -> Unit,
) {
    if (visibleHeight <= 0.dp) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(visibleHeight),
        contentAlignment = Alignment.BottomStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(fullHeight),
        ) {
            content()
        }
    }
}

@Composable
private fun QuickFilterPinnedRow(
    quickFilter: QuickFilter,
    onQuickFilterOptionClick: (ListFilterOption) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(quickFilter.items) { chip ->
            val option = chip.data as? ListFilterOption
            FilterChip(
                selected = chip.isChecked,
                onClick = {
                    if (option != null) {
                        onQuickFilterOptionClick(option)
                    }
                },
                enabled = option != null,
                leadingIcon = if (chip.icon != 0) {
                    {
                        Icon(
                            painter = painterResource(chip.icon),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    null
                },
                label = {
                    Text(
                        text = when {
                            chip.titleResId != 0 -> stringResource(chip.titleResId)
                            chip.title != null -> chip.title.toString()
                            else -> ""
                        }.let { title ->
                            if (chip.counter > 0) "$title ${chip.counter}" else title
                        },
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun SourceTagsPinnedRow(
    tags: List<ContentTag>,
    selectedTags: Set<ContentTag>,
    onToggleTag: (ContentTag, Boolean) -> Unit,
) {
    if (tags.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(tags, key = { it.key }) { tag ->
            FilterChip(
                selected = tag in selectedTags,
                onClick = { onToggleTag(tag, tag !in selectedTags) },
                label = {
                    Text(
                        text = tag.title,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun ActiveQueryRow(query: String) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            FilterChip(
                selected = true,
                onClick = {},
                enabled = false,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                label = {
                    Text(
                        text = query,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchFilterPanel(
    sortOrders: List<SortOrder>,
    selectedSortOrder: SortOrder?,
    tagGroups: List<UiTagGroup>,
    contentTypes: List<ContentType>,
    selectedContentTypes: Set<ContentType>,
    states: List<ContentState>,
    selectedStates: Set<ContentState>,
    locales: List<Locale?>,
    selectedLocale: Locale?,
    authors: List<String>,
    selectedAuthor: String?,
    onSortOrderChange: (SortOrder) -> Unit,
    onToggleTag: (ContentTag, Boolean) -> Unit,
    onToggleContentType: (ContentType, Boolean) -> Unit,
    onToggleState: (ContentState, Boolean) -> Unit,
    onLocaleChange: (Locale?) -> Unit,
    onAuthorChange: (String?) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.filter),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(onClick = onReset) {
                Text(stringResource(R.string.reset_filter))
            }
        }

        FilterSection(title = stringResource(R.string.sort_order)) {
            FilterChipFlow {
                sortOrders.forEach { item ->
                    FilterChip(
                        selected = item == selectedSortOrder,
                        onClick = { onSortOrderChange(item) },
                        label = { Text(stringResource(item.titleRes)) },
                    )
                }
            }
        }

        if (contentTypes.isNotEmpty()) {
            FilterSection(title = stringResource(R.string.type)) {
                FilterChipFlow {
                    contentTypes.forEach { type ->
                        val isSelected = type in selectedContentTypes
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggleContentType(type, !isSelected) },
                            label = { Text(stringResource(type.titleResId)) },
                        )
                    }
                }
            }
        }

        if (states.isNotEmpty()) {
            FilterSection(title = stringResource(R.string.state)) {
                FilterChipFlow {
                    states.forEach { state ->
                        val isSelected = state in selectedStates
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggleState(state, !isSelected) },
                            label = { Text(stringResource(state.titleResId)) },
                        )
                    }
                }
            }
        }

        if (locales.isNotEmpty()) {
            FilterSection(title = stringResource(R.string.language)) {
                FilterChipFlow {
                    locales.forEach { locale ->
                        val isSelected = locale == selectedLocale
                        FilterChip(
                            selected = isSelected,
                            onClick = { onLocaleChange(if (isSelected) null else locale) },
                            label = {
                                Text(
                                    if (locale == null) {
                                        stringResource(R.string.all)
                                    } else {
                                        locale.getDisplayName(locale).ifBlank { locale.toLanguageTag() }
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }

        if (authors.isNotEmpty()) {
            FilterSection(title = stringResource(R.string.author)) {
                OutlinedTextField(
                    value = selectedAuthor.orEmpty(),
                    onValueChange = { onAuthorChange(it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.author)) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilterChipFlow {
                    authors.take(12).forEach { author ->
                        val isSelected = author == selectedAuthor
                        FilterChip(
                            selected = isSelected,
                            onClick = { onAuthorChange(if (isSelected) null else author) },
                            label = { Text(author) },
                        )
                    }
                }
            }
        }

        tagGroups.take(6).forEach { group ->
            val orderedTags = remember(group) {
                (group.selected.toList() + group.tags.filterNot { it in group.selected }.sortedBy { it.title })
                    .distinctBy { it.key }
                    .take(24)
            }
            if (orderedTags.isNotEmpty()) {
                FilterSection(title = group.title) {
                    FilterChipFlow {
                        orderedTags.forEach { tag ->
                            val isSelected = tag in group.selected
                            FilterChip(
                                selected = isSelected,
                                onClick = { onToggleTag(tag, !isSelected) },
                                label = { Text(tag.title) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipFlow(
    content: @Composable () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

private fun ListMode.iconRes(): Int = when (this) {
    ListMode.LIST -> R.drawable.ic_list
    ListMode.DETAILED_LIST -> R.drawable.ic_list_detailed
    ListMode.GRID -> R.drawable.ic_grid
}
