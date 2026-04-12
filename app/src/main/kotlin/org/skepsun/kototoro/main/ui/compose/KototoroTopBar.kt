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
import androidx.compose.material3.MaterialTheme
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
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.search.ui.suggestion.model.SearchSuggestionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KototoroTopBar(
    suggestions: List<SearchSuggestionItem>,
    onQueryChanged: (String) -> Unit,
    onSearch: (String) -> Unit = {},
    onSuggestionClick: (SearchSuggestionItem) -> Unit = {},
    onDeleteQuery: (String) -> Unit = {},
    onVoiceInput: () -> Unit = {},
    onMoreClick: (android.view.View?) -> Unit = {},
    selectedContentType: ContentType? = null,
    onContentTypeSelected: (ContentType?) -> Unit = {},
    selectedSourceTags: Set<SourceTag> = emptySet(),
    onSourceTagSelected: (SourceTag?) -> Unit = {},
    isSearchBarFilterHidden: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBarPadding.calculateTopPadding())
    ) {
        DockedSearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { newQuery ->
                        query = newQuery
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
                                query = ""
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
                                    query = ""
                                    onQueryChanged("")
                                }) {
                                    Icon(
                                        Icons.Filled.Clear,
                                        contentDescription = stringResource(R.string.clear)
                                    )
                                }
                            }
                            if (!expanded && !isSearchBarFilterHidden) {
                                // Content type filter (swipeable pill)
                                SwipeableFilterChip(
                                    selectedType = selectedContentType,
                                    onTypeSelected = onContentTypeSelected,
                                    modifier = Modifier.zIndex(1f)
                                )
                                // Source tag filter (dropdown)
                                SourceTagDropdown(
                                    selectedTags = selectedSourceTags,
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
                            Box {
                                var anchorView by remember { androidx.compose.runtime.mutableStateOf<android.view.View?>(null) }
                                androidx.compose.ui.viewinterop.AndroidView(
                                    factory = { context -> 
                                        android.view.View(context).apply { 
                                            layoutParams = android.view.ViewGroup.LayoutParams(1, 1) 
                                        } 
                                    },
                                    update = { anchorView = it }
                                )
                                IconButton(onClick = { onMoreClick(anchorView) }) {
                                    Icon(
                                        painterResource(R.drawable.ic_more_vert),
                                        contentDescription = stringResource(R.string.more)
                                    )
                                }
                            }
                        }
                    },
                )
            },
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (expanded) 0.dp else 16.dp),
            colors = SearchBarDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            // Suggestion content
            SuggestionList(
                suggestions = suggestions,
                onSuggestionClick = { item ->
                    when (item) {
                        is SearchSuggestionItem.RecentQuery -> {
                            query = item.query
                            onQueryChanged(item.query)
                            onSearch(item.query)
                            expanded = false
                        }
                        is SearchSuggestionItem.Hint -> {
                            query = item.query
                            onQueryChanged(item.query)
                            onSearch(item.query)
                            expanded = false
                        }
                        is SearchSuggestionItem.Author -> {
                            query = item.name
                            onQueryChanged(item.name)
                            onSearch(item.name)
                            expanded = false
                        }
                        else -> onSuggestionClick(item)
                    }
                },
                onDeleteQuery = onDeleteQuery,
            )
        }
    }
}

@Composable
private fun SuggestionList(
    suggestions: List<SearchSuggestionItem>,
    onSuggestionClick: (SearchSuggestionItem) -> Unit,
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
                        modifier = Modifier.clickable { onSuggestionClick(item) },
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
                        modifier = Modifier.clickable { onSuggestionClick(item) },
                    )
                }

                is SearchSuggestionItem.Author -> {
                    ListItem(
                        headlineContent = { Text(item.name) },
                        leadingContent = {
                            Icon(Icons.Filled.Person, contentDescription = null)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onSuggestionClick(item) },
                    )
                }

                is SearchSuggestionItem.Tags -> {
                    LazyRow(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(item.tags) { chip ->
                            AssistChip(
                                onClick = { /* TODO: navigate to tag search */ },
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
                        modifier = Modifier.clickable { onSuggestionClick(item) },
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
                        modifier = Modifier.clickable { onSuggestionClick(item) },
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
                                modifier = Modifier.clickable { onSuggestionClick(item) },
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
