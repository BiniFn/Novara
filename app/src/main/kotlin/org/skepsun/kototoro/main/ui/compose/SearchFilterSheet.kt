package org.skepsun.kototoro.main.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.jsonsource.SourceType
import org.skepsun.kototoro.search.domain.ALL_SEARCH_CONTENT_KINDS
import org.skepsun.kototoro.search.domain.ALL_SOURCE_TYPES
import org.skepsun.kototoro.search.domain.SEARCH_CONTENT_KIND_OPTIONS
import org.skepsun.kototoro.search.domain.SOURCE_TYPE_OPTIONS
import org.skepsun.kototoro.search.domain.SearchContentKind
import org.skepsun.kototoro.search.domain.SearchKind

private val searchKindOptions = SearchKind.entries.filter { it != SearchKind.ADVANCED }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterSheet(
    searchKind: SearchKind,
    sourceTypes: Set<SourceType>,
    contentKinds: Set<SearchContentKind>,
    pinnedOnly: Boolean,
    hideEmpty: Boolean,
    onSearchKindChange: (SearchKind) -> Unit,
    onSourceTypeToggle: (SourceType) -> Unit,
    onContentKindToggle: (SearchContentKind) -> Unit,
    onPinnedOnlyChange: (Boolean) -> Unit,
    onHideEmptyChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
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
                    searchKindOptions.forEach { kind ->
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
                            selected = option.type in sourceTypes,
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
                            selected = option.kind in contentKinds,
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

internal fun <T> Set<T>.toggleOrAll(item: T, allItems: Set<T>): Set<T> {
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
