package org.skepsun.kototoro.main.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.jsonsource.SourceType
import org.skepsun.kototoro.core.model.titleResId
import org.skepsun.kototoro.core.ui.compose.ContentSourceIcon
import org.skepsun.kototoro.core.ui.compose.rememberResolvedSourceTitle
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.search.domain.ALL_SEARCH_CONTENT_KINDS
import org.skepsun.kototoro.search.domain.ALL_SOURCE_TYPES
import org.skepsun.kototoro.search.domain.AdvancedSearchParams
import org.skepsun.kototoro.search.domain.SEARCH_CONTENT_KIND_OPTIONS
import org.skepsun.kototoro.search.domain.SOURCE_TYPE_OPTIONS
import org.skepsun.kototoro.search.domain.SearchContentKind
import org.skepsun.kototoro.search.domain.SearchKind
import org.skepsun.kototoro.search.ui.suggestion.model.SearchSuggestionItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KototoroSearchOverlay(
    query: String,
    suggestions: List<SearchSuggestionItem>,
    initialSearchKind: SearchKind,
    initialSourceTypes: Set<SourceType>,
    initialContentKinds: Set<SearchContentKind>,
    onQueryChanged: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSearchWithOptions: (
        query: String,
        kind: SearchKind,
        sourceTypes: Set<SourceType>,
        contentKinds: Set<SearchContentKind>,
        advancedQuery: AdvancedSearchParams?,
        pinnedOnly: Boolean,
        hideEmpty: Boolean,
    ) -> Unit,
    onDismissRequest: () -> Unit,
    onSourceTypesChange: (Set<SourceType>) -> Unit,
    onContentKindsChange: (Set<SearchContentKind>) -> Unit,
    onContentSuggestionClick: (Content) -> Unit,
    onTagSuggestionClick: (ContentTag) -> Unit,
    onSourceSuggestionClick: (ContentSource) -> Unit,
    onAuthorSuggestionClick: (String) -> Unit,
    onDeleteQuery: (String) -> Unit,
    onVoiceInput: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()
    val focusRequester = remember { FocusRequester() }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var searchKind by remember { mutableStateOf(initialSearchKind) }
    var selectedSourceTypes by remember(initialSourceTypes) { mutableStateOf(initialSourceTypes.ifEmpty { ALL_SOURCE_TYPES }) }
    var selectedContentKinds by remember(initialContentKinds) { mutableStateOf(initialContentKinds.ifEmpty { ALL_SEARCH_CONTENT_KINDS }) }
    var pinnedOnly by remember { mutableStateOf(false) }
    var hideEmpty by remember { mutableStateOf(false) }
    var advancedTitle by remember { mutableStateOf("") }
    var advancedTags by remember { mutableStateOf("") }
    var advancedAuthor by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(selectedSourceTypes) {
        onSourceTypesChange(selectedSourceTypes)
    }

    LaunchedEffect(selectedContentKinds) {
        onContentKindsChange(selectedContentKinds)
    }

    fun submitSearch(searchQuery: String) {
        val advancedQuery = AdvancedSearchParams(
            query = searchQuery.trim(),
            title = advancedTitle.trim(),
            tags = advancedTags.trim(),
            author = advancedAuthor.trim(),
        ).takeIf {
            it.title.isNotBlank() || it.tags.isNotBlank() || it.author.isNotBlank()
        }
        onSearchWithOptions(
            searchQuery,
            searchKind,
            selectedSourceTypes,
            selectedContentKinds,
            advancedQuery,
            pinnedOnly,
            hideEmpty,
        )
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.52f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
                        ),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        TextField(
                            value = query,
                            onValueChange = onQueryChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .focusRequester(focusRequester),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.search_content)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = stringResource(R.string.search),
                                )
                            },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { onQueryChanged("") }) {
                                        Icon(
                                            imageVector = Icons.Filled.Clear,
                                            contentDescription = stringResource(R.string.clear),
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    submitSearch(query)
                                },
                            ),
                            shape = RoundedCornerShape(18.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                errorContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                errorIndicatorColor = Color.Transparent,
                            ),
                        )
                    }
                    IconButton(onClick = { showOptionsSheet = true }) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_filter_menu),
                            contentDescription = stringResource(R.string.display_options),
                        )
                    }
                    IconButton(onClick = onVoiceInput) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_voice_input),
                            contentDescription = stringResource(R.string.voice_search),
                        )
                    }
                }
                HorizontalDivider()
                SuggestionList(
                    suggestions = suggestions,
                    bottomPadding = navigationBarPadding.calculateBottomPadding(),
                    onRecentQueryClick = { recentQuery ->
                        onQueryChanged(recentQuery)
                        submitSearch(recentQuery)
                    },
                    onHintClick = { hint ->
                        onQueryChanged(hint)
                        submitSearch(hint)
                    },
                    onAuthorSuggestionClick = { author ->
                        onQueryChanged(author)
                        submitSearch(author)
                    },
                    onContentSuggestionClick = onContentSuggestionClick,
                    onTagSuggestionClick = { tag ->
                        onQueryChanged(tag.title)
                        onTagSuggestionClick(tag)
                    },
                    onSourceSuggestionClick = onSourceSuggestionClick,
                    onDeleteQuery = onDeleteQuery,
                )
            }
        }
    }

    if (showOptionsSheet) {
        SearchOptionsSheet(
            searchKind = searchKind,
            onSearchKindChange = { searchKind = it },
            selectedSourceTypes = selectedSourceTypes,
            onSourceTypeToggle = { type ->
                selectedSourceTypes = selectedSourceTypes.toggleOrAll(type, ALL_SOURCE_TYPES)
            },
            selectedContentKinds = selectedContentKinds,
            onContentKindToggle = { kind ->
                selectedContentKinds = selectedContentKinds.toggleOrAll(kind, ALL_SEARCH_CONTENT_KINDS)
            },
            pinnedOnly = pinnedOnly,
            onPinnedOnlyChange = { pinnedOnly = it },
            hideEmpty = hideEmpty,
            onHideEmptyChange = { hideEmpty = it },
            advancedTitle = advancedTitle,
            onAdvancedTitleChange = { advancedTitle = it },
            advancedTags = advancedTags,
            onAdvancedTagsChange = { advancedTags = it },
            advancedAuthor = advancedAuthor,
            onAdvancedAuthorChange = { advancedAuthor = it },
            onDismissRequest = { showOptionsSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SearchOptionsSheet(
    searchKind: SearchKind,
    onSearchKindChange: (SearchKind) -> Unit,
    selectedSourceTypes: Set<SourceType>,
    onSourceTypeToggle: (SourceType) -> Unit,
    selectedContentKinds: Set<SearchContentKind>,
    onContentKindToggle: (SearchContentKind) -> Unit,
    pinnedOnly: Boolean,
    onPinnedOnlyChange: (Boolean) -> Unit,
    hideEmpty: Boolean,
    onHideEmptyChange: (Boolean) -> Unit,
    advancedTitle: String,
    onAdvancedTitleChange: (String) -> Unit,
    advancedTags: String,
    onAdvancedTagsChange: (String) -> Unit,
    advancedAuthor: String,
    onAdvancedAuthorChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.type),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchKind.entries.forEach { kind ->
                        FilterChip(
                            selected = searchKind == kind,
                            onClick = { onSearchKindChange(kind) },
                            label = { Text(stringResource(kind.titleResId)) },
                        )
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.source_type),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SOURCE_TYPE_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = option.type in selectedSourceTypes,
                            onClick = { onSourceTypeToggle(option.type) },
                            label = { Text(stringResource(option.titleRes)) },
                        )
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.type),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SEARCH_CONTENT_KIND_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = option.kind in selectedContentKinds,
                            onClick = { onContentKindToggle(option.kind) },
                            label = { Text(stringResource(option.titleRes)) },
                        )
                    }
                }
            }
            item {
                SearchOptionSwitchRow(
                    title = stringResource(R.string.pinned_sources_only),
                    checked = pinnedOnly,
                    onCheckedChange = onPinnedOnlyChange,
                )
            }
            item {
                SearchOptionSwitchRow(
                    title = stringResource(R.string.hide_empty_sources),
                    checked = hideEmpty,
                    onCheckedChange = onHideEmptyChange,
                )
            }
            if (searchKind == SearchKind.ADVANCED) {
                item {
                    Text(
                        text = stringResource(R.string.advanced_search),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                item {
                    TextField(
                        value = advancedTitle,
                        onValueChange = onAdvancedTitleChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.name)) },
                    )
                }
                item {
                    TextField(
                        value = advancedTags,
                        onValueChange = onAdvancedTagsChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.genres)) },
                    )
                }
                item {
                    TextField(
                        value = advancedAuthor,
                        onValueChange = onAdvancedAuthorChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.author)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchOptionSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun <T> Set<T>.toggleOrAll(item: T, allItems: Set<T>): Set<T> {
    val updated = toMutableSet().apply {
        if (!add(item)) {
            remove(item)
        }
    }
    return updated.ifEmpty { allItems }
}

private val SearchKind.titleResId: Int
    get() = when (this) {
        SearchKind.SIMPLE -> R.string.simple
        SearchKind.TITLE -> R.string.name
        SearchKind.AUTHOR -> R.string.author
        SearchKind.TAG -> R.string.genre
        SearchKind.ADVANCED -> R.string.advanced_search
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
            contentType = { item ->
                when (item) {
                    is SearchSuggestionItem.RecentQuery -> "recent_query"
                    is SearchSuggestionItem.Hint -> "hint"
                    is SearchSuggestionItem.Author -> "author"
                    is SearchSuggestionItem.Source -> "source"
                    is SearchSuggestionItem.SourceTip -> "source_tip"
                    is SearchSuggestionItem.Tags -> "tags"
                    is SearchSuggestionItem.ContentList -> "content_list"
                    is SearchSuggestionItem.Text -> "text"
                }
            },
        ) { item ->
            when (item) {
                is SearchSuggestionItem.RecentQuery -> {
                    ListItem(
                        headlineContent = { Text(item.query) },
                        leadingContent = {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_history),
                                contentDescription = null,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onDeleteQuery(item.query) }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.remove),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.clickable { onRecentQueryClick(item.query) },
                    )
                }

                is SearchSuggestionItem.Hint -> {
                    ListItem(
                        headlineContent = { Text(item.query) },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.clickable { onHintClick(item.query) },
                    )
                }

                is SearchSuggestionItem.Author -> {
                    ListItem(
                        headlineContent = { Text(item.name) },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.clickable { onAuthorSuggestionClick(item.name) },
                    )
                }

                is SearchSuggestionItem.Tags -> {
                    LazyRow(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(item.tags, contentType = { "tag_chip" }) { chip ->
                            val tag = chip.data as? ContentTag
                            AssistChip(
                                onClick = { tag?.let(onTagSuggestionClick) },
                                label = { Text(chip.title?.toString().orEmpty(), maxLines = 1) },
                            )
                        }
                    }
                }

                is SearchSuggestionItem.Source -> {
                    SourceSuggestionRow(
                        source = item.source,
                        onClick = { onSourceSuggestionClick(item.source) },
                    )
                }

                is SearchSuggestionItem.SourceTip -> {
                    SourceSuggestionRow(
                        source = item.source,
                        onClick = { onSourceSuggestionClick(item.source) },
                    )
                }

                is SearchSuggestionItem.ContentList -> {
                    Column {
                        item.items.forEach { content ->
                            ContentSuggestionRow(
                                content = content,
                                onClick = { onContentSuggestionClick(content) },
                            )
                        }
                    }
                }

                is SearchSuggestionItem.Text -> {
                    if (item.textResId != 0) {
                        ListItem(
                            headlineContent = { Text(stringResource(item.textResId)) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceSuggestionRow(
    source: ContentSource,
    onClick: () -> Unit,
) {
    val sourceTitle = rememberResolvedSourceTitle(source)
    ListItem(
        headlineContent = {
            Text(
                text = sourceTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(source.contentType.titleResId),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                ContentSourceIcon(
                    source = source,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ContentSuggestionRow(
    content: Content,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val sourceTitle = rememberResolvedSourceTitle(content.source)
    val imageRequest = remember(content.id, content.coverUrl) {
        ImageRequest.Builder(context)
            .data(content.coverUrl)
            .crossfade(true)
            .apply { mangaExtra(content) }
            .build()
    }

    ListItem(
        headlineContent = {
            Text(
                text = content.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = sourceTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            AsyncImage(
                model = imageRequest,
                contentDescription = content.title,
                modifier = Modifier
                    .width(40.dp)
                    .size(width = 40.dp, height = 56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
