package org.skepsun.kototoro.main.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.glass.GlassDefaults
import org.skepsun.kototoro.core.ui.glass.GlassTopBarContainer
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.search.ui.suggestion.model.SearchSuggestionItem

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
    onMoreClick: (android.view.View?) -> Unit = {},
    isLanguagePresetFilterVisible: Boolean = false,
    hasActiveLanguagePreset: Boolean = false,
    onLanguagePresetFilterClick: (android.view.View?) -> Boolean = { false },
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
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBarPadding.calculateTopPadding())
    ) {
        GlassTopBarContainer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (expanded) 0.dp else 16.dp),
            style = if (expanded) GlassDefaults.regularStyle() else GlassDefaults.prominentStyle(),
        ) {
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
                        placeholder = { Text(stringResource(R.string.search_content)) },
                        leadingIcon = {
                            if (expanded) {
                                IconButton(onClick = {
                                    expanded = false
                                    onQueryChanged("")
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back)
                                    )
                                }
                            } else {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = stringResource(R.string.search)
                                )
                            }
                        },
                        trailingIcon = {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                if (expanded && query.isNotEmpty()) {
                                    IconButton(onClick = {
                                        onQueryChanged("")
                                    }) {
                                        Icon(
                                            Icons.Filled.Clear,
                                            contentDescription = stringResource(R.string.clear)
                                        )
                                    }
                                }
                                if (!expanded && isLanguagePresetFilterVisible) {
                                    TopBarAnchorIconButton(onClick = onLanguagePresetFilterClick) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_language),
                                            contentDescription = stringResource(R.string.show_language_preset_filter),
                                            tint = if (hasActiveLanguagePreset) {
                                                androidx.compose.material3.MaterialTheme.colorScheme.primary
                                            } else {
                                                androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
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
                                    IconButton(onClick = onVoiceInput) {
                                        Icon(
                                            painterResource(R.drawable.ic_voice_input),
                                            contentDescription = stringResource(R.string.voice_search)
                                        )
                                    }
                                }
                                TopBarAnchorIconButton(
                                    onClick = { anchorView ->
                                        onMoreClick(anchorView)
                                        true
                                    },
                                ) {
                                        Icon(
                                            painterResource(R.drawable.ic_more_vert),
                                            contentDescription = stringResource(R.string.more)
                                        )
                                }
                            }
                        },
                    )
                },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth(),
                colors = SearchBarDefaults.colors(
                    containerColor = Color.Transparent,
                    dividerColor = Color.Transparent,
                ),
            ) {
                SuggestionList(
                    suggestions = suggestions,
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

@Composable
private fun TopBarAnchorIconButton(
    onClick: (android.view.View?) -> Boolean,
    content: @Composable () -> Unit,
) {
    val anchor = androidx.compose.ui.platform.LocalView.current
    IconButton(onClick = { onClick(anchor) }) {
        content()
    }
}

@Composable
private fun SuggestionList(
    suggestions: List<SearchSuggestionItem>,
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
        contentPadding = PaddingValues(vertical = 8.dp),
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
