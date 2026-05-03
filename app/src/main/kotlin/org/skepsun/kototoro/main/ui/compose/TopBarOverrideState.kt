package org.skepsun.kototoro.main.ui.compose

import org.skepsun.kototoro.list.ui.compose.SelectionAction

interface TopBarOverrideState

data class ContentSelectionTopBarOverrideState(
    val selectedCount: Int,
    val isAllNonLocal: Boolean,
    val isSingleSelection: Boolean,
    val showRemoveOption: Boolean = false,
    val supportedActions: Set<SelectionAction>,
    val allPinned: Boolean = false,
    val onClearSelection: () -> Unit,
    val onActionClick: (SelectionAction) -> Unit,
) : TopBarOverrideState
