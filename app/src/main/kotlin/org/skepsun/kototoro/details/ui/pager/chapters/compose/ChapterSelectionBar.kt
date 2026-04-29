package org.skepsun.kototoro.details.ui.pager.chapters.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R

@Composable
fun ChapterSelectionBar(
    state: ChapterSelectionUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = state.onClearSelection) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.close),
            )
        }
        Text(
            text = stringResource(R.string.selected_count, state.selectedCount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (state.canSelectAll) {
            IconButton(onClick = state.onSelectAll) {
                Icon(
                    imageVector = Icons.Filled.SelectAll,
                    contentDescription = stringResource(R.string.select_all),
                )
            }
        }
        if (state.canDownload) {
            IconButton(onClick = state.onDownload) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = stringResource(R.string.download),
                )
            }
        }
        if (state.canDelete) {
            IconButton(onClick = state.onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete),
                )
            }
        }
        if (state.canBookmark) {
            IconButton(onClick = state.onBookmark) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = stringResource(R.string.bookmark_add),
                )
            }
        }
        if (state.canMarkCurrent) {
            IconButton(onClick = state.onMarkCurrent) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.mark_as_current),
                )
            }
        }
    }
}
