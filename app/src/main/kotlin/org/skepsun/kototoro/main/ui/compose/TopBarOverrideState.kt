package org.skepsun.kototoro.main.ui.compose

import org.skepsun.kototoro.list.ui.compose.SelectionAction

interface TopBarOverrideState

data class CompactTopBarTabItem(
    val id: Long,
    val title: String,
)

data class CompactTabsTopBarOverrideState(
    val items: List<CompactTopBarTabItem>,
    val selectedItemId: Long,
    val onItemSelected: (Long) -> Unit,
) : TopBarOverrideState

data class CompactFilterRailItem(
    val id: String,
    val title: String,
    val isSelected: Boolean,
    val onClick: () -> Unit,
)

data class CompactFilterRailOverrideState(
    val items: List<CompactFilterRailItem>,
) : TopBarOverrideState

data class FavoritesTopBarOverrideState(
    val tabsState: CompactTabsTopBarOverrideState,
    val filterRailState: CompactFilterRailOverrideState? = null,
    val contextualOverrideState: TopBarOverrideState? = null,
) : TopBarOverrideState

data class LayeredTopBarOverrideState(
    val tabsState: CompactTabsTopBarOverrideState? = null,
    val filterRailState: CompactFilterRailOverrideState? = null,
    val contextualOverrideState: TopBarOverrideState? = null,
) : TopBarOverrideState

data class RouteScopedTopBarOverrideState(
    val ownerRoute: String,
    val state: TopBarOverrideState?,
) : TopBarOverrideState

data class ContentSelectionTopBarOverrideState(
    val selectedCount: Int,
    val isAllNonLocal: Boolean,
    val isSingleSelection: Boolean,
    val showRemoveOption: Boolean = false,
    val supportedActions: Set<SelectionAction>,
    val allPinned: Boolean = false,
    val preferredInlineActions: List<SelectionAction>? = null,
    val removeActionIconRes: Int? = null,
    val removeActionTitleRes: Int? = null,
    val onClearSelection: () -> Unit,
    val onActionClick: (SelectionAction) -> Unit,
) : TopBarOverrideState
