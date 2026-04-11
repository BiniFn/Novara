package org.skepsun.kototoro.list.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.skepsun.kototoro.R

enum class SelectionAction {
    SELECT_ALL,
    SHARE,
    FAVOURITE,
    SAVE,
    EDIT_OVERRIDE,
    FIX,
    REMOVE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KototoroSelectionTopBar(
    selectedCount: Int,
    isAllNonLocal: Boolean,
    isSingleSelection: Boolean,
    showRemoveOption: Boolean = false,
    onClearSelection: () -> Unit,
    onActionClick: (SelectionAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(text = selectedCount.toString()) },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = "Clear Selection")
            }
        },
        actions = {
            IconButton(onClick = { onActionClick(SelectionAction.SELECT_ALL) }) {
                Icon(painter = painterResource(id = R.drawable.ic_select_all), contentDescription = "Select All")
            }
            if (showRemoveOption) {
                IconButton(onClick = { onActionClick(SelectionAction.REMOVE) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }
            if (isAllNonLocal) {
                IconButton(onClick = { onActionClick(SelectionAction.SAVE) }) {
                    Icon(painter = painterResource(id = R.drawable.ic_download), contentDescription = "Download")
                }
            }
            IconButton(onClick = { onActionClick(SelectionAction.SHARE) }) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
            
            // Overflow menu
            Box {
                IconButton(onClick = { showOverflowMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Favourite") },
                        onClick = {
                            showOverflowMenu = false
                            onActionClick(SelectionAction.FAVOURITE)
                        }
                    )
                    if (isSingleSelection) {
                        DropdownMenuItem(
                            text = { Text("Edit Override") },
                            onClick = {
                                showOverflowMenu = false
                                onActionClick(SelectionAction.EDIT_OVERRIDE)
                            }
                        )
                    }
                    if (isAllNonLocal) {
                        DropdownMenuItem(
                            text = { Text("Fix") },
                            onClick = {
                                showOverflowMenu = false
                                onActionClick(SelectionAction.FIX)
                            }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier.fillMaxWidth()
    )
}
