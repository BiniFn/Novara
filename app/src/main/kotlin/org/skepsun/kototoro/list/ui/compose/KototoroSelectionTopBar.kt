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
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R

enum class SelectionAction {
    SELECT_ALL,
    SHARE,
    FAVOURITE,
    SAVE,
    EDIT_OVERRIDE,
    FIX,
    REMOVE,
    PIN,
    MARK_AS_COMPLETED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KototoroSelectionTopBar(
    selectedCount: Int,
    isAllNonLocal: Boolean,
    isSingleSelection: Boolean,
    showRemoveOption: Boolean = false,
    supportedActions: Set<SelectionAction>? = null,
    allPinned: Boolean = false,
    onClearSelection: () -> Unit,
    onActionClick: (SelectionAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    val allActions = supportedActions
    val inlineActions = allActions?.take(4).orEmpty()
    val overflowActions = allActions?.drop(4).orEmpty().toMutableSet()
    if (isAllNonLocal) overflowActions += SelectionAction.FIX
    if (isSingleSelection) overflowActions += SelectionAction.EDIT_OVERRIDE
    overflowActions += SelectionAction.FAVOURITE

    TopAppBar(
        title = { Text(text = selectedCount.toString()) },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = "Clear Selection")
            }
        },
        actions = {
            if (supportedActions == null || SelectionAction.SELECT_ALL in inlineActions) {
                IconButton(onClick = { onActionClick(SelectionAction.SELECT_ALL) }) {
                    Icon(painter = painterResource(id = R.drawable.ic_select_all), contentDescription = "Select All")
                }
            }
            if (showRemoveOption || (supportedActions != null && SelectionAction.REMOVE in inlineActions)) {
                IconButton(onClick = { onActionClick(SelectionAction.REMOVE) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }
            if (isAllNonLocal || (supportedActions != null && SelectionAction.SAVE in inlineActions)) {
                IconButton(onClick = { onActionClick(SelectionAction.SAVE) }) {
                    Icon(painter = painterResource(id = R.drawable.ic_download), contentDescription = "Download/Save")
                }
            }
            if (supportedActions == null || SelectionAction.SHARE in inlineActions) {
                IconButton(onClick = { onActionClick(SelectionAction.SHARE) }) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
            }
            if (supportedActions == null || SelectionAction.PIN in inlineActions) {
                IconButton(onClick = { onActionClick(SelectionAction.PIN) }) {
                    Icon(
                        painter = painterResource(id = if (allPinned) R.drawable.ic_unpin else R.drawable.ic_pin),
                        contentDescription = if (allPinned) stringResource(R.string.unpin) else stringResource(R.string.pin),
                    )
                }
            }
            if (supportedActions == null || SelectionAction.MARK_AS_COMPLETED in inlineActions) {
                IconButton(onClick = { onActionClick(SelectionAction.MARK_AS_COMPLETED) }) {
                    Icon(painter = painterResource(id = R.drawable.ic_eye_check), contentDescription = "Mark as Completed")
                }
            }

            // Overflow menu - shows actions beyond the first 4 inline, plus FIX/EDIT_OVERRIDE/FAVOURITE
            val hasOverflow = (isAllNonLocal && !isSingleSelection) ||
                isSingleSelection ||
                overflowActions.any { it != SelectionAction.FIX && it != SelectionAction.EDIT_OVERRIDE && it != SelectionAction.FAVOURITE }
            if (hasOverflow) {
                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        shape = MaterialTheme.shapes.extraSmall,
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        if (SelectionAction.SHARE in overflowActions) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share)) },
                                onClick = { showOverflowMenu = false; onActionClick(SelectionAction.SHARE) }
                            )
                        }
                        if (SelectionAction.PIN in overflowActions) {
                            DropdownMenuItem(
                                text = { Text(if (allPinned) stringResource(R.string.unpin) else stringResource(R.string.pin)) },
                                onClick = { showOverflowMenu = false; onActionClick(SelectionAction.PIN) }
                            )
                        }
                        if (SelectionAction.MARK_AS_COMPLETED in overflowActions) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mark_as_completed)) },
                                onClick = { showOverflowMenu = false; onActionClick(SelectionAction.MARK_AS_COMPLETED) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.categories)) },
                            onClick = { showOverflowMenu = false; onActionClick(SelectionAction.FAVOURITE) }
                        )
                        if (isSingleSelection) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit)) },
                                onClick = { showOverflowMenu = false; onActionClick(SelectionAction.EDIT_OVERRIDE) }
                            )
                        }
                        if (isAllNonLocal) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fix)) },
                                onClick = { showOverflowMenu = false; onActionClick(SelectionAction.FIX) }
                            )
                        }
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
