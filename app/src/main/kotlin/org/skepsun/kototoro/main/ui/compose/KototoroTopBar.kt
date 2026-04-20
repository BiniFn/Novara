package org.skepsun.kototoro.main.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.ui.glass.GlassDefaults
import org.skepsun.kototoro.core.ui.glass.GlassSurface
import org.skepsun.kototoro.explore.data.SourcePreset
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.search.ui.suggestion.model.SearchSuggestionItem

private val CollapsedSearchBarHeight = 48.dp
private val ExpandedSearchBarHeight = 52.dp
private val CompactTopBarActionSize = 40.dp
private val CompactTopBarIconSize = 20.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KototoroTopBar(
    query: String,
    suggestions: List<SearchSuggestionItem>,
    onQueryChanged: (String) -> Unit,
    onSearch: (String) -> Unit = {},
    onContentSuggestionClick: (Content) -> Unit = {},
    onTagSuggestionClick: (ContentTag) -> Unit = {},
    onSourceSuggestionClick: (ContentSource) -> Unit = {},
    onAuthorSuggestionClick: (String) -> Unit = {},
    onDeleteQuery: (String) -> Unit = {},
    onVoiceInput: () -> Unit = {},
    onOpenListOptions: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    isAppUpdateAvailable: Boolean = false,
    onAppUpdateClick: () -> Unit = {},
    isLanguagePresetFilterVisible: Boolean = false,
    languagePresetEntries: List<SourcePreset> = emptyList(),
    activeLanguagePresetId: Long = -1L,
    onLanguagePresetSelected: (Long) -> Unit = {},
    selectedContentType: ContentType? = null,
    enabledContentTypes: Set<ContentType> = setOf(ContentType.MANGA, ContentType.NOVEL, ContentType.VIDEO),
    isContentTypeFilterVisible: Boolean = true,
    onContentTypeSelected: (ContentType?) -> Unit = {},
    selectedSourceTags: Set<SourceTag> = emptySet(),
    sourceTagEntries: List<SourceTag> = SourceTag.quickFilterEntries,
    enabledSourceTags: Set<SourceTag> = sourceTagEntries.toSet(),
    isSourceTagFilterVisible: Boolean = true,
    onSourceTagFilterClick: (android.view.View?) -> Boolean = { false },
    onSourceTagSelected: (SourceTag?) -> Unit = {},
    supportsDisplayModeMenu: Boolean = false,
    currentListMode: ListMode = ListMode.GRID,
    onListModeSelected: (ListMode) -> Unit = {},
    supportsGridSizeSlider: Boolean = false,
    gridSize: Int = 100,
    onGridSizeChange: (Int) -> Unit = {},
    isIncognitoModeEnabled: Boolean = false,
    onIncognitoToggle: () -> Unit = {},
    isCollapsedFullyTransparent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var isMoreMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()
    val searchBarHeight = if (expanded) ExpandedSearchBarHeight else CollapsedSearchBarHeight
    val collapsedContainerAlpha by animateFloatAsState(
        targetValue = if (expanded) 0.94f else if (isCollapsedFullyTransparent) 0f else 0.84f,
        label = "top_bar_container_alpha",
    )
    val collapsedBorderAlpha by animateFloatAsState(
        targetValue = if (expanded) 0.12f else if (isCollapsedFullyTransparent) 0f else 0.10f,
        label = "top_bar_border_alpha",
    )
    val showMoreActions = true

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBarPadding.calculateTopPadding())
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (expanded) 0.dp else 10.dp),
            shape = RoundedCornerShape(if (expanded) 0.dp else 24.dp),
            style = if (expanded) {
                GlassDefaults.regularStyle().copy(containerAlpha = collapsedContainerAlpha, borderAlpha = collapsedBorderAlpha)
            } else {
                GlassDefaults.prominentStyle().copy(
                    containerAlpha = collapsedContainerAlpha,
                    borderAlpha = collapsedBorderAlpha,
                    shadowElevation = 0.dp,
                )
            },
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides CompactTopBarActionSize) {
                DockedSearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = query,
                            onQueryChange = { newQuery ->
                                onQueryChanged(newQuery)
                            },
                            onSearch = { searchQuery ->
                                onSearch(searchQuery)
                                expanded = false
                            },
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(searchBarHeight),
                            placeholder = { Text(stringResource(R.string.search_content)) },
                            leadingIcon = {
                                if (expanded) {
                                    IconButton(
                                        onClick = {
                                            expanded = false
                                            onQueryChanged("")
                                        },
                                        modifier = Modifier.size(CompactTopBarActionSize),
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.back),
                                            modifier = Modifier.size(CompactTopBarIconSize),
                                        )
                                    }
                                } else {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = stringResource(R.string.search),
                                        modifier = Modifier.size(CompactTopBarIconSize),
                                    )
                                }
                            },
                            trailingIcon = {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (expanded && query.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    onQueryChanged("")
                                                },
                                                modifier = Modifier.size(CompactTopBarActionSize),
                                            ) {
                                                Icon(
                                                    Icons.Filled.Clear,
                                                    contentDescription = stringResource(R.string.clear),
                                                    modifier = Modifier.size(CompactTopBarIconSize),
                                                )
                                            }
                                        }
                                        if (!expanded && isLanguagePresetFilterVisible) {
                                            LanguagePresetDropdownButton(
                                                presets = languagePresetEntries,
                                                activePresetId = activeLanguagePresetId,
                                                onPresetSelected = onLanguagePresetSelected,
                                            )
                                        }
                                        if (!expanded && isContentTypeFilterVisible) {
                                            SwipeableFilterChip(
                                                selectedType = selectedContentType,
                                                enabledTypes = enabledContentTypes,
                                                onTypeSelected = onContentTypeSelected,
                                                modifier = Modifier.zIndex(1f)
                                            )
                                        }
                                        if (!expanded && isSourceTagFilterVisible) {
                                            SourceTagDropdown(
                                                selectedTags = selectedSourceTags,
                                                entries = sourceTagEntries,
                                                enabledTags = enabledSourceTags,
                                                onButtonClickIntercept = onSourceTagFilterClick,
                                                onTagSelected = onSourceTagSelected,
                                            )
                                        }
                                        if (expanded) {
                                            IconButton(
                                                onClick = onVoiceInput,
                                                modifier = Modifier.size(CompactTopBarActionSize),
                                            ) {
                                                Icon(
                                                    painterResource(R.drawable.ic_voice_input),
                                                    contentDescription = stringResource(R.string.voice_search),
                                                    modifier = Modifier.size(CompactTopBarIconSize),
                                                )
                                            }
                                        }
                                        if (showMoreActions) {
                                            Box {
                                                IconButton(
                                                    onClick = { isMoreMenuExpanded = true },
                                                    modifier = Modifier.size(CompactTopBarActionSize),
                                                ) {
                                                    Icon(
                                                        painterResource(R.drawable.ic_more_vert),
                                                        contentDescription = stringResource(R.string.more),
                                                        modifier = Modifier.size(CompactTopBarIconSize),
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = isMoreMenuExpanded,
                                                    onDismissRequest = { isMoreMenuExpanded = false },
                                                ) {
                                                    if (supportsDisplayModeMenu) {
                                                        TopBarMenuSectionLabel(
                                                            text = stringResource(R.string.list_mode),
                                                        )
                                                        TopBarListModeItem(
                                                            iconRes = R.drawable.ic_list,
                                                            label = stringResource(R.string.list),
                                                            selected = currentListMode == ListMode.LIST,
                                                            onClick = {
                                                                isMoreMenuExpanded = false
                                                                onListModeSelected(ListMode.LIST)
                                                            },
                                                        )
                                                        TopBarListModeItem(
                                                            iconRes = R.drawable.ic_list_detailed,
                                                            label = stringResource(R.string.detailed_list),
                                                            selected = currentListMode == ListMode.DETAILED_LIST,
                                                            onClick = {
                                                                isMoreMenuExpanded = false
                                                                onListModeSelected(ListMode.DETAILED_LIST)
                                                            },
                                                        )
                                                        TopBarListModeItem(
                                                            iconRes = R.drawable.ic_grid,
                                                            label = stringResource(R.string.grid),
                                                            selected = currentListMode == ListMode.GRID,
                                                            onClick = {
                                                                isMoreMenuExpanded = false
                                                                onListModeSelected(ListMode.GRID)
                                                            },
                                                        )
                                                    }
                                                    if (supportsGridSizeSlider) {
                                                        if (supportsDisplayModeMenu) {
                                                            HorizontalDivider()
                                                        }
                                                        TopBarGridSizeItem(
                                                            title = stringResource(R.string.grid_size),
                                                            value = gridSize,
                                                            onValueChange = onGridSizeChange,
                                                        )
                                                    }
                                                    if (supportsDisplayModeMenu || supportsGridSizeSlider) {
                                                        HorizontalDivider()
                                                    }
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.settings)) },
                                                        onClick = {
                                                            isMoreMenuExpanded = false
                                                            onSettingsClick()
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.incognito_mode)) },
                                                        trailingIcon = {
                                                            Checkbox(
                                                                checked = isIncognitoModeEnabled,
                                                                onCheckedChange = null,
                                                            )
                                                        },
                                                        onClick = {
                                                            isMoreMenuExpanded = false
                                                            onIncognitoToggle()
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(if (expanded) 0.dp else 24.dp),
                    colors = SearchBarDefaults.colors(
                        containerColor = Color.Transparent,
                        dividerColor = Color.Transparent,
                    ),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    SuggestionList(
                        suggestions = suggestions,
                        bottomPadding = navigationBarPadding.calculateBottomPadding(),
                        onRecentQueryClick = { recentQuery ->
                            onQueryChanged(recentQuery)
                            onSearch(recentQuery)
                            expanded = false
                        },
                        onHintClick = { hint ->
                            onQueryChanged(hint)
                            onSearch(hint)
                            expanded = false
                        },
                        onAuthorSuggestionClick = { author ->
                            onQueryChanged(author)
                            onAuthorSuggestionClick(author)
                            expanded = false
                        },
                        onContentSuggestionClick = { content ->
                            onContentSuggestionClick(content)
                            expanded = false
                        },
                        onTagSuggestionClick = { tag ->
                            onQueryChanged(tag.title)
                            onTagSuggestionClick(tag)
                            expanded = false
                        },
                        onSourceSuggestionClick = { source ->
                            onSourceSuggestionClick(source)
                            expanded = false
                        },
                        onDeleteQuery = onDeleteQuery,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBarMenuSectionLabel(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun TopBarListModeItem(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                )
            }
        },
    )
}

@Composable
private fun TopBarGridSizeItem(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderValue by remember(value) { mutableStateOf(value.toFloat()) }
    val currentValue = sliderValue.toInt().coerceIn(50, 150)

    Column(
        modifier = Modifier
            .width(264.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "$currentValue%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                onValueChange(it.toInt().coerceIn(50, 150))
            },
            valueRange = 50f..150f,
            steps = 19,
        )
    }
}

@Composable
private fun LanguagePresetDropdownButton(
    presets: List<SourcePreset>,
    activePresetId: Long,
    onPresetSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(CompactTopBarActionSize),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_language),
                contentDescription = stringResource(R.string.show_language_preset_filter),
                modifier = Modifier.size(CompactTopBarIconSize),
                tint = if (activePresetId > 0L) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(x = 0.dp, y = 4.dp),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.all)) },
                onClick = {
                    onPresetSelected(-1L)
                    expanded = false
                },
                leadingIcon = {
                    Checkbox(
                        checked = activePresetId <= 0L,
                        onCheckedChange = null,
                    )
                },
            )
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.title) },
                    onClick = {
                        onPresetSelected(preset.id)
                        expanded = false
                    },
                    leadingIcon = {
                        Checkbox(
                            checked = activePresetId == preset.id,
                            onCheckedChange = null,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SuggestionList(
    suggestions: List<SearchSuggestionItem>,
    bottomPadding: Dp,
    onRecentQueryClick: (String) -> Unit,
    onHintClick: (String) -> Unit,
    onAuthorSuggestionClick: (String) -> Unit,
    onContentSuggestionClick: (Content) -> Unit,
    onTagSuggestionClick: (ContentTag) -> Unit,
    onSourceSuggestionClick: (ContentSource) -> Unit,
    onDeleteQuery: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 8.dp),
    ) {
        items(
            items = suggestions,
            key = { item ->
                when (item) {
                    is SearchSuggestionItem.RecentQuery -> "rq_${item.query}"
                    is SearchSuggestionItem.Hint -> "hint_${item.query}"
                    is SearchSuggestionItem.Author -> "author_${item.name}"
                    is SearchSuggestionItem.Source -> "src_${item.source.name}"
                    is SearchSuggestionItem.SourceTip -> "srctip_${item.source.name}"
                    is SearchSuggestionItem.Tags -> "tags"
                    is SearchSuggestionItem.ContentList -> "content"
                    is SearchSuggestionItem.Text -> "text_${item.textResId}"
                }
            },
        ) { item ->
            when (item) {
                is SearchSuggestionItem.RecentQuery -> {
                    ListItem(
                        headlineContent = { Text(item.query) },
                        leadingContent = {
                            Icon(
                                painterResource(R.drawable.ic_history),
                                contentDescription = null,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onDeleteQuery(item.query) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.remove),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onRecentQueryClick(item.query) },
                    )
                }

                is SearchSuggestionItem.Hint -> {
                    ListItem(
                        headlineContent = { Text(item.query) },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onHintClick(item.query) },
                    )
                }

                is SearchSuggestionItem.Author -> {
                    ListItem(
                        headlineContent = { Text(item.name) },
                        leadingContent = {
                            Icon(Icons.Filled.Person, contentDescription = null)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onAuthorSuggestionClick(item.name) },
                    )
                }

                is SearchSuggestionItem.Tags -> {
                    LazyRow(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(item.tags) { chip ->
                            val tag = chip.data as? ContentTag
                            AssistChip(
                                onClick = { tag?.let(onTagSuggestionClick) },
                                label = { Text(chip.title?.toString().orEmpty(), maxLines = 1) },
                            )
                        }
                    }
                }

                is SearchSuggestionItem.Source -> {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = item.source.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onSourceSuggestionClick(item.source) },
                    )
                }

                is SearchSuggestionItem.SourceTip -> {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = item.source.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onSourceSuggestionClick(item.source) },
                    )
                }

                is SearchSuggestionItem.ContentList -> {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        item.items.forEach { content ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = content.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { onContentSuggestionClick(content) },
                            )
                        }
                    }
                }

                is SearchSuggestionItem.Text -> {
                    if (item.textResId != 0) {
                        ListItem(
                            headlineContent = { Text(stringResource(item.textResId)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}
