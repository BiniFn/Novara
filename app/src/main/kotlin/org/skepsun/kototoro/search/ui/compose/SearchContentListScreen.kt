package org.skepsun.kototoro.search.ui.compose

import android.app.Activity
import android.content.res.Configuration
import android.view.inputmethod.EditorInfo
import androidx.core.text.HtmlCompat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.titleResId
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.dialog.buildAlertDialog
import org.skepsun.kototoro.core.ui.dialog.setEditText
import org.skepsun.kototoro.core.ui.compose.rememberResolvedSourceTitle
import org.skepsun.kototoro.core.ui.model.titleRes
import org.skepsun.kototoro.core.util.ShareHelper
import org.skepsun.kototoro.list.ui.compose.KototoroSelectionTopBar
import org.skepsun.kototoro.list.ui.compose.SelectionAction

import org.skepsun.kototoro.filter.ui.model.UiTagGroup
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.list.ui.compose.KototoroContentListScreen
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.QuickFilter
import org.skepsun.kototoro.remotelist.ui.RemoteListViewModel
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentState
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.SortOrder
import java.util.Locale

private val SearchTopActionsHeight = 56.dp

private enum class SearchSidePaneMode {
    Filter,
    Preview,
}

private data class SearchContentPreparedItems(
    val quickFilter: QuickFilter?,
    val contentItems: List<ListModel>,
    val contentListItems: List<ContentListModel>,
)

private fun prepareSearchContentItems(items: List<ListModel>): SearchContentPreparedItems {
    var quickFilter: QuickFilter? = null
    val contentItems = ArrayList<ListModel>()
    val contentListItems = ArrayList<ContentListModel>()
    items.forEach { item ->
        if (item is QuickFilter && quickFilter == null) {
            quickFilter = item
        } else {
            contentItems += item
            if (item is ContentListModel) {
                contentListItems += item
            }
        }
    }
    return SearchContentPreparedItems(
        quickFilter = quickFilter,
        contentItems = contentItems,
        contentListItems = contentListItems,
    )
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
    val tagsExcludedProperty by viewModel.filterCoordinator.tagsExcluded.collectAsStateWithLifecycle()
    val contentTypesProperty by viewModel.filterCoordinator.contentTypes.collectAsStateWithLifecycle()
    val statesProperty by viewModel.filterCoordinator.states.collectAsStateWithLifecycle()
    val localeProperty by viewModel.filterCoordinator.locale.collectAsStateWithLifecycle()
    val authorsProperty by viewModel.filterCoordinator.authors.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val rootView = LocalView.current
    val configuration = LocalConfiguration.current
    val settings = remember(context.applicationContext) { AppSettings(context.applicationContext) }
    val gridSize = settings.observeAsState(AppSettings.KEY_GRID_SIZE) { gridSize }.value
    val gridScale = gridSize / 100f
    val isWideAdaptiveLayout = remember(configuration.orientation, configuration.screenWidthDp) {
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 720
    }

    val preparedItems = remember(items) { prepareSearchContentItems(items) }
    val quickFilter = preparedItems.quickFilter
    val contentItems = preparedItems.contentItems
    val contentListItems = preparedItems.contentListItems

    var searchMode by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf(filterSnapshot.listFilter.query.orEmpty()) }
    var collapseOffsetPx by rememberSaveable { mutableStateOf(0f) }
    var showFilterPanel by rememberSaveable(isWideAdaptiveLayout) { mutableStateOf(isWideAdaptiveLayout) }
    var sidePaneMode by rememberSaveable(isWideAdaptiveLayout) { mutableStateOf(SearchSidePaneMode.Filter) }
    var previewContent by remember { mutableStateOf<Content?>(null) }
    var selectedItemsIds by rememberSaveable { mutableStateOf<Set<Long>>(emptySet()) }
    val focusRequester = remember { FocusRequester() }
    val selectedItems: Set<Content> = remember(selectedItemsIds, contentListItems) {
        contentListItems
            .asSequence()
            .filter { it.id in selectedItemsIds }
            .map { it.manga }
            .toSet()
    }
    val isAllNonLocal = selectedItems.none { it.isLocal }

    BackHandler(enabled = selectedItemsIds.isNotEmpty()) {
        selectedItemsIds = emptySet()
    }

    LaunchedEffect(filterSnapshot.listFilter.query, searchMode) {
        if (!searchMode) {
            searchQuery = filterSnapshot.listFilter.query.orEmpty()
        }
    }

    LaunchedEffect(viewModel.onOpenContent) {
        viewModel.onOpenContent.collect { event ->
            event?.consume { content ->
                appRouter.openDetails(content, rootView)
            }
        }
    }

    LaunchedEffect(isWideAdaptiveLayout) {
        if (isWideAdaptiveLayout) {
            sidePaneMode = SearchSidePaneMode.Filter
            showFilterPanel = true
        } else {
            previewContent = null
            sidePaneMode = SearchSidePaneMode.Filter
            showFilterPanel = false
        }
    }

    LaunchedEffect(contentItems) {
        val previewId = previewContent?.id ?: return@LaunchedEffect
        if (contentListItems.none { it.id == previewId }) {
            previewContent = null
            sidePaneMode = SearchSidePaneMode.Filter
        }
    }

    LaunchedEffect(contentListItems) {
        if (selectedItemsIds.isNotEmpty()) {
            val availableIds = contentListItems.asSequence().map { it.id }.toSet()
            val filteredSelection = selectedItemsIds.filterTo(mutableSetOf()) { it in availableIds }
            if (filteredSelection != selectedItemsIds) {
                selectedItemsIds = filteredSelection
            }
        }
    }

    val topActionsHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        SearchTopActionsHeight.toPx()
    }
    val maxCollapsePx = topActionsHeightPx
    val isWideSplitLayout = isWideAdaptiveLayout && showFilterPanel
    val showSelectionTopBar = selectedItemsIds.isNotEmpty()
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

    val topBarContent: @Composable () -> Unit = {
        if (showSelectionTopBar) {
            KototoroSelectionTopBar(
                selectedCount = selectedItemsIds.size,
                isAllNonLocal = isAllNonLocal,
                isSingleSelection = selectedItemsIds.size == 1,
                supportedActions = buildSet {
                    add(SelectionAction.SHARE)
                    add(SelectionAction.FAVOURITE)
                    if (isAllNonLocal) add(SelectionAction.SAVE)
                },
                onClearSelection = { selectedItemsIds = emptySet() },
                onActionClick = { action ->
                    when (action) {
                        SelectionAction.SHARE -> {
                            ShareHelper(context).shareContentLinks(selectedItems)
                            selectedItemsIds = emptySet()
                        }
                        SelectionAction.FAVOURITE -> {
                            appRouter.showFavoriteDialog(selectedItems)
                            selectedItemsIds = emptySet()
                        }
                        SelectionAction.SAVE -> {
                            if (isAllNonLocal) {
                                appRouter.showDownloadDialog(selectedItems, rootView)
                                selectedItemsIds = emptySet()
                            }
                        }
                        else -> Unit
                    }
                },
            )
        } else {
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
                contentItems = contentListItems,
                selectedTags = filterSnapshot.listFilter.tags,
                availableTags = tagsProperty.availableItems.flatMap { it.tags },
                listMode = listMode,
                gridSize = gridSize,
                topActionsHeight = SearchTopActionsHeight,
                collapseOffsetPx = collapseOffsetPx,
                isRandomLoading = isRandomLoading,
                onBackClick = { (context as? Activity)?.finish() },
                onRandomClick = viewModel::openRandom,
                onFilterClick = {
                    if (isWideAdaptiveLayout) {
                        when {
                            sidePaneMode == SearchSidePaneMode.Preview -> {
                                sidePaneMode = SearchSidePaneMode.Filter
                                showFilterPanel = true
                            }
                            else -> showFilterPanel = !showFilterPanel
                        }
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
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            if (!isWideSplitLayout) {
                topBarContent()
            }
        },
    ) { paddingValues ->
        if (isWideSplitLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    topBarContent()
                    KototoroContentListScreen(
                        items = contentItems,
                        gridScale = gridScale,
                        listMode = listMode,
                        isRefreshing = false,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .nestedScroll(nestedScrollConnection),
                        onPrepareItemTransition = { _, _ -> },
                        onItemClick = { item ->
                            if (selectedItemsIds.isNotEmpty()) {
                                selectedItemsIds = if (item.id in selectedItemsIds) selectedItemsIds - item.id else selectedItemsIds + item.id
                            } else {
                                previewContent = item.toContentWithOverride()
                                sidePaneMode = SearchSidePaneMode.Preview
                            }
                        },
                        onItemLongClick = { item ->
                            selectedItemsIds = if (item.id in selectedItemsIds) selectedItemsIds - item.id else selectedItemsIds + item.id
                        },
                        onLoadMore = { viewModel.loadNextPage() },
                        onRefresh = { viewModel.onRefresh() },
                        onClearSelection = { selectedItemsIds = emptySet() },
                        onSelectionAction = { action ->
                            when (action) {
                                SelectionAction.SHARE -> {
                                    ShareHelper(context).shareContentLinks(selectedItems)
                                    selectedItemsIds = emptySet()
                                    true
                                }

                                SelectionAction.FAVOURITE -> {
                                    appRouter.showFavoriteDialog(selectedItems)
                                    selectedItemsIds = emptySet()
                                    true
                                }

                                SelectionAction.SAVE -> {
                                    if (isAllNonLocal) {
                                        appRouter.showDownloadDialog(selectedItems, rootView)
                                        selectedItemsIds = emptySet()
                                        true
                                    } else {
                                        false
                                    }
                                }

                                else -> false
                            }
                        },
                        selectedItemsIds = selectedItemsIds,
                        showInlineSelectionTopBar = false,
                        onRetry = viewModel::onRetry,
                    )
                }
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
                            onOpenDetails = { appRouter.openDetails(requireNotNull(previewContent), rootView) },
                        )
                    } else {
                        SearchFilterPanel(
                            sourceName = viewModel.source.name,
                            sortOrders = sortOrderProperty.availableItems,
                            selectedSortOrder = sortOrderProperty.selectedItems.firstOrNull(),
                            tagGroups = tagsProperty.availableItems,
                            excludedTagGroups = tagsExcludedProperty.availableItems,
                            contentTypes = contentTypesProperty.availableItems,
                            selectedContentTypes = contentTypesProperty.selectedItems,
                            states = statesProperty.availableItems,
                            selectedStates = statesProperty.selectedItems,
                            locales = localeProperty.availableItems,
                            selectedLocale = localeProperty.selectedItems.firstOrNull(),
                            authors = authorsProperty.availableItems,
                            selectedAuthor = authorsProperty.selectedItems.firstOrNull(),
                            onSortOrderChange = viewModel.filterCoordinator::setSortOrder,
                            onToggleTag = { tag, selected, excludeMode ->
                                if (excludeMode) {
                                    viewModel.filterCoordinator.toggleTagExclude(tag, selected)
                                } else {
                                    viewModel.filterCoordinator.toggleTag(tag, selected)
                                }
                            },
                            onToggleContentType = { type, selected -> viewModel.filterCoordinator.toggleContentType(type, selected) },
                            onToggleState = { state, selected -> viewModel.filterCoordinator.toggleState(state, selected) },
                            onLocaleChange = viewModel.filterCoordinator::setLocale,
                            onAuthorChange = viewModel.filterCoordinator::setAuthor,
                            onReset = viewModel.filterCoordinator::reset,
                            isTextInputTag = viewModel.filterCoordinator::isTextInputTag,
                            textInputValue = viewModel.filterCoordinator::getTextInputValue,
                            textInputLabel = viewModel.filterCoordinator::getTextInputLabel,
                            onSetTextInputValue = viewModel.filterCoordinator::setTextInputValue,
                            onOpenTagCatalog = { groupTitle, excludeMode ->
                                appRouter.showTagsCatalogSheet(
                                    excludeMode = excludeMode,
                                    groupTitle = groupTitle,
                                )
                            },
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
                onPrepareItemTransition = { _, _ -> },
                onItemClick = { item ->
                    if (selectedItemsIds.isNotEmpty()) {
                        selectedItemsIds = if (item.id in selectedItemsIds) selectedItemsIds - item.id else selectedItemsIds + item.id
                    } else {
                        appRouter.openDetails(item.manga, rootView)
                    }
                },
                onItemLongClick = { item ->
                    selectedItemsIds = if (item.id in selectedItemsIds) selectedItemsIds - item.id else selectedItemsIds + item.id
                },
                onLoadMore = { viewModel.loadNextPage() },
                onRefresh = { viewModel.onRefresh() },
                onClearSelection = { selectedItemsIds = emptySet() },
                onSelectionAction = { action ->
                    when (action) {
                        SelectionAction.SHARE -> {
                            ShareHelper(context).shareContentLinks(selectedItems)
                            selectedItemsIds = emptySet()
                            true
                        }

                        SelectionAction.FAVOURITE -> {
                            appRouter.showFavoriteDialog(selectedItems)
                            selectedItemsIds = emptySet()
                            true
                        }

                        SelectionAction.SAVE -> {
                            if (isAllNonLocal) {
                                appRouter.showDownloadDialog(selectedItems, rootView)
                                selectedItemsIds = emptySet()
                                true
                            } else {
                                false
                            }
                        }

                        else -> false
                    }
                },
                selectedItemsIds = selectedItemsIds,
                showInlineSelectionTopBar = false,
                onRetry = viewModel::onRetry,
            )
        }

        if (!isWideAdaptiveLayout && showFilterPanel) {
            ModalBottomSheet(
                onDismissRequest = { showFilterPanel = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                SearchFilterPanel(
                    sourceName = viewModel.source.name,
                    sortOrders = sortOrderProperty.availableItems,
                    selectedSortOrder = sortOrderProperty.selectedItems.firstOrNull(),
                    tagGroups = tagsProperty.availableItems,
                    excludedTagGroups = tagsExcludedProperty.availableItems,
                    contentTypes = contentTypesProperty.availableItems,
                    selectedContentTypes = contentTypesProperty.selectedItems,
                    states = statesProperty.availableItems,
                    selectedStates = statesProperty.selectedItems,
                    locales = localeProperty.availableItems,
                    selectedLocale = localeProperty.selectedItems.firstOrNull(),
                    authors = authorsProperty.availableItems,
                    selectedAuthor = authorsProperty.selectedItems.firstOrNull(),
                    onSortOrderChange = viewModel.filterCoordinator::setSortOrder,
                    onToggleTag = { tag, selected, excludeMode ->
                        if (excludeMode) {
                            viewModel.filterCoordinator.toggleTagExclude(tag, selected)
                        } else {
                            viewModel.filterCoordinator.toggleTag(tag, selected)
                        }
                    },
                    onToggleContentType = { type, selected -> viewModel.filterCoordinator.toggleContentType(type, selected) },
                    onToggleState = { state, selected -> viewModel.filterCoordinator.toggleState(state, selected) },
                    onLocaleChange = viewModel.filterCoordinator::setLocale,
                    onAuthorChange = viewModel.filterCoordinator::setAuthor,
                    onReset = viewModel.filterCoordinator::reset,
                    isTextInputTag = viewModel.filterCoordinator::isTextInputTag,
                    textInputValue = viewModel.filterCoordinator::getTextInputValue,
                    textInputLabel = viewModel.filterCoordinator::getTextInputLabel,
                    onSetTextInputValue = viewModel.filterCoordinator::setTextInputValue,
                    onOpenTagCatalog = { groupTitle, excludeMode ->
                        appRouter.showTagsCatalogSheet(
                            excludeMode = excludeMode,
                            groupTitle = groupTitle,
                        )
                    },
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
    val topActionsHeightPx = with(density) { topActionsHeight.toPx() }
    val topActionsCollapsedPx = collapseOffsetPx.coerceIn(0f, topActionsHeightPx)
    val topActionsVisibleHeight = with(density) { (topActionsHeightPx - topActionsCollapsedPx).coerceAtLeast(0f).toDp() }
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
                    var showDisplayOptionsSheet by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

                    SourceListTopActionsRow(
                        title = sourceTitle,
                        activeQuery = activeQuery,
                        currentSortLabel = currentSortLabel,
                        topBarAlpha = compactTopBarAlpha,
                        listMode = listMode,
                        gridSize = gridSize,
                        isFilterApplied = isFilterApplied,
                        isRandomLoading = isRandomLoading,
                        onBackClick = onBackClick,
                        onSearchClick = onSearchOpen,
                        onRandomClick = onRandomClick,
                        onFilterClick = onFilterClick,
                        onResetFilterClick = onResetFilterClick,
                        onSettingsClick = onSettingsClick,
                        onListModeChange = onListModeChange,
                        onGridSizeChange = onGridSizeChange,
                        onShowDisplayOptionsSheet = { showDisplayOptionsSheet = true }
                    )

                    if (showDisplayOptionsSheet) {
                        org.skepsun.kototoro.list.ui.compose.DisplayOptionsSheet(
                            supportsDisplayModeMenu = true,
                            currentListMode = listMode,
                            onListModeSelected = onListModeChange,
                            supportsGridSizeSlider = true,
                            gridSize = gridSize,
                            onGridSizeChange = onGridSizeChange,
                            onDismissRequest = { showDisplayOptionsSheet = false },
                        )
                    }
                }
            }

            if (!searchMode) {
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
            .height(48.dp)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.close),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .padding(horizontal = 6.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.merge(
                TextStyle(color = MaterialTheme.colorScheme.onSurface),
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
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
    activeQuery: String?,
    currentSortLabel: String,
    topBarAlpha: Float,
    listMode: ListMode,
    gridSize: Int,
    isFilterApplied: Boolean,
    isRandomLoading: Boolean,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onRandomClick: () -> Unit,
    onFilterClick: () -> Unit,
    onResetFilterClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onListModeChange: (ListMode) -> Unit,
    onGridSizeChange: (Int) -> Unit,
    onShowDisplayOptionsSheet: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val maxWidthDp = maxWidth.value
        val showRandomDirect = maxWidthDp >= 420f
        val showDisplayDirect = maxWidthDp >= 476f
        val showSettingsDirect = maxWidthDp >= 532f
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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(topBarAlpha),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!activeQuery.isNullOrBlank()) {
                    Text(
                        text = activeQuery,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            BadgedBox(
                modifier = Modifier.padding(horizontal = 4.dp),
                badge = {
                    if (isFilterApplied) {
                        Badge()
                    }
                },
            ) {
                IconButton(onClick = onFilterClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_filter_menu),
                        contentDescription = currentSortLabel,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

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
                IconButton(onClick = onShowDisplayOptionsSheet) {
                    Icon(
                        painter = painterResource(listMode.iconRes()),
                        contentDescription = stringResource(R.string.list_options),
                    )
                }
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
                    onShowDisplayOptionsSheet = onShowDisplayOptionsSheet,
                )
            }
        }
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
    onShowDisplayOptionsSheet: () -> Unit,
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
            shape = MaterialTheme.shapes.extraSmall,
            offset = androidx.compose.ui.unit.DpOffset(x = 0.dp, y = 4.dp),
        )
 {
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
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.display_options)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_grid),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onShowDisplayOptionsSheet()
                    },
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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
    sourceName: String,
    sortOrders: List<SortOrder>,
    selectedSortOrder: SortOrder?,
    tagGroups: List<UiTagGroup>,
    excludedTagGroups: List<UiTagGroup>,
    contentTypes: List<ContentType>,
    selectedContentTypes: Set<ContentType>,
    states: List<ContentState>,
    selectedStates: Set<ContentState>,
    locales: List<Locale?>,
    selectedLocale: Locale?,
    authors: List<String>,
    selectedAuthor: String?,
    onSortOrderChange: (SortOrder) -> Unit,
    onToggleTag: (ContentTag, Boolean, Boolean) -> Unit,
    onToggleContentType: (ContentType, Boolean) -> Unit,
    onToggleState: (ContentState, Boolean) -> Unit,
    onLocaleChange: (Locale?) -> Unit,
    onAuthorChange: (String?) -> Unit,
    onReset: () -> Unit,
    isTextInputTag: (ContentTag) -> Boolean,
    textInputValue: (ContentTag) -> String?,
    textInputLabel: (ContentTag) -> String,
    onSetTextInputValue: (ContentTag, String) -> Unit,
    onOpenTagCatalog: (String?, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var sortExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
            SortOrderFilterSection(
                sourceName = sourceName,
                sortOrders = sortOrders,
                selectedSortOrder = selectedSortOrder,
                expanded = sortExpanded,
                onExpandedChange = { sortExpanded = it },
                onSortOrderChange = onSortOrderChange,
            )
        }

        if (contentTypes.isNotEmpty()) {
            FilterSection(title = stringResource(R.string.type)) {
                FilterChipFlow {
                    contentTypes.forEach { type ->
                        val isSelected = type in selectedContentTypes
                        SearchPanelChip(
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
                        SearchPanelChip(
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
                        SearchPanelChip(
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
                        SearchPanelChip(
                            selected = isSelected,
                            onClick = { onAuthorChange(if (isSelected) null else author) },
                            label = { Text(author) },
                        )
                    }
                }
            }
        }

        TagGroupsSection(
            title = stringResource(R.string.genres),
            tagGroups = tagGroups,
            excludeMode = false,
            isTextInputTag = isTextInputTag,
            textInputValue = textInputValue,
            textInputLabel = textInputLabel,
            onToggleTag = onToggleTag,
            onTextInputTagClick = { tag ->
                val currentValue = textInputValue(tag).orEmpty()
                buildAlertDialog(context) {
                    val input = setEditText(
                        inputType = EditorInfo.TYPE_CLASS_TEXT,
                        singleLine = true,
                    )
                    input.hint = textInputLabel(tag)
                    input.setText(currentValue)
                    setTitle(textInputLabel(tag))
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        onSetTextInputValue(tag, input.text?.toString()?.trim().orEmpty())
                    }
                    setNegativeButton(android.R.string.cancel, null)
                    setNeutralButton(R.string.clear) { _, _ ->
                        onSetTextInputValue(tag, "")
                    }
                }.show()
            },
            onOpenTagCatalog = onOpenTagCatalog,
        )

        if (excludedTagGroups.any { it.tags.isNotEmpty() }) {
            TagGroupsSection(
                title = stringResource(R.string.genres_exclude),
                tagGroups = excludedTagGroups,
                excludeMode = true,
                isTextInputTag = isTextInputTag,
                textInputValue = textInputValue,
                textInputLabel = textInputLabel,
                onToggleTag = onToggleTag,
                onTextInputTagClick = { tag ->
                    val currentValue = textInputValue(tag).orEmpty()
                    buildAlertDialog(context) {
                        val input = setEditText(
                            inputType = EditorInfo.TYPE_CLASS_TEXT,
                            singleLine = true,
                        )
                        input.hint = textInputLabel(tag)
                        input.setText(currentValue)
                        setTitle(textInputLabel(tag))
                        setPositiveButton(android.R.string.ok) { _, _ ->
                            onSetTextInputValue(tag, input.text?.toString()?.trim().orEmpty())
                        }
                        setNegativeButton(android.R.string.cancel, null)
                        setNeutralButton(R.string.clear) { _, _ ->
                            onSetTextInputValue(tag, "")
                        }
                    }.show()
                },
                onOpenTagCatalog = onOpenTagCatalog,
            )
        }
    }
}

@Composable
private fun SortOrderFilterSection(
    sourceName: String,
    sortOrders: List<SortOrder>,
    selectedSortOrder: SortOrder?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
) {
    val selectedLabel = selectedSortOrder?.let { resolveSortOrderLabel(sourceName, it) }.orEmpty()
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (expanded) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column {
                    sortOrders.forEachIndexed { index, item ->
                        val selected = item == selectedSortOrder
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSortOrderChange(item)
                                    onExpandedChange(false)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = resolveSortOrderLabel(sourceName, item),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (selected) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        if (index != sortOrders.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun resolveSortOrderLabel(sourceName: String, order: SortOrder): String {
    return if (sourceName.startsWith("TRACKING_BANGUMI_")) {
        when (order) {
            SortOrder.RATING -> stringResource(R.string.sort_by_ranking)
            SortOrder.POPULARITY -> stringResource(R.string.sort_by_popularity_label)
            SortOrder.ADDED -> stringResource(R.string.sort_by_collection)
            SortOrder.NEWEST -> stringResource(R.string.sort_by_date_label)
            SortOrder.ALPHABETICAL -> stringResource(R.string.sort_by_name_label)
            else -> stringResource(order.titleRes)
        }
    } else {
        stringResource(order.titleRes)
    }
}

@Composable
private fun TagGroupsSection(
    title: String,
    tagGroups: List<UiTagGroup>,
    excludeMode: Boolean,
    isTextInputTag: (ContentTag) -> Boolean,
    textInputValue: (ContentTag) -> String?,
    textInputLabel: (ContentTag) -> String,
    onToggleTag: (ContentTag, Boolean, Boolean) -> Unit,
    onTextInputTagClick: (ContentTag) -> Unit,
    onOpenTagCatalog: (String?, Boolean) -> Unit,
) {
    val visibleGroups = tagGroups.filter { it.tags.isNotEmpty() }
    if (visibleGroups.isEmpty()) return
    FilterSection(title = title) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            visibleGroups.forEach { group ->
                TagGroupContent(
                    group = group,
                    excludeMode = excludeMode,
                    isTextInputTag = isTextInputTag,
                    textInputValue = textInputValue,
                    textInputLabel = textInputLabel,
                    onToggleTag = onToggleTag,
                    onTextInputTagClick = onTextInputTagClick,
                    onOpenTagCatalog = onOpenTagCatalog,
                )
            }
        }
    }
}

@Composable
private fun TagGroupContent(
    group: UiTagGroup,
    excludeMode: Boolean,
    isTextInputTag: (ContentTag) -> Boolean,
    textInputValue: (ContentTag) -> String?,
    textInputLabel: (ContentTag) -> String,
    onToggleTag: (ContentTag, Boolean, Boolean) -> Unit,
    onTextInputTagClick: (ContentTag) -> Unit,
    onOpenTagCatalog: (String?, Boolean) -> Unit,
) {
    val orderedTags = remember(group) {
        (group.selected.toList() + group.tags.filterNot { it in group.selected }.sortedBy { it.title })
            .distinctBy { it.key }
    }
    val visibleTags = remember(orderedTags) { orderedTags.take(12) }
    val canExpand = orderedTags.size > visibleTags.size

    Column(
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        if (group.title.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (canExpand) {
                    IconButton(
                        onClick = { onOpenTagCatalog(group.title, excludeMode) },
                        modifier = Modifier.size(18.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.show_more),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        FilterChipFlow {
            visibleTags.forEach { tag ->
                val value = textInputValue(tag)
                val textInput = isTextInputTag(tag) || value != null
                val selected = if (textInput) {
                    !value.isNullOrBlank()
                } else {
                    tag in group.selected
                }
                SearchPanelChip(
                    selected = selected,
                    onClick = {
                        if (textInput) {
                            onTextInputTagClick(tag)
                        } else {
                            onToggleTag(tag, !selected, excludeMode)
                        }
                    },
                    label = {
                        Text(
                            text = if (textInput && !value.isNullOrBlank()) {
                                "${textInputLabel(tag)}: $value"
                            } else if (textInput) {
                                textInputLabel(tag)
                            } else {
                                tag.title
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipFlow(
    content: @Composable () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        content()
    }
}

@Composable
private fun SearchPanelChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = 26.dp),
        label = {
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                label()
            }
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = if (selected) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    )
}

private fun ListMode.iconRes(): Int = when (this) {
    ListMode.LIST -> R.drawable.ic_list
    ListMode.DETAILED_LIST -> R.drawable.ic_list_detailed
    ListMode.GRID -> R.drawable.ic_grid
}
