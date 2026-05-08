package org.skepsun.kototoro.list.ui.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.skepsun.kototoro.list.ui.compose.DisplayOptionsSheet

@Composable
fun ListConfigRoute(
    section: ListConfigSection,
    onDismissRequest: () -> Unit,
    viewModel: ListConfigViewModel = hiltViewModel(key = "list-config-${section.hashCode()}"),
) {
    LaunchedEffect(section) {
        viewModel.initialize(section)
    }

    DisplayOptionsSheet(
        supportsDisplayModeMenu = true,
        currentListMode = viewModel.listMode,
        onListModeSelected = { viewModel.listMode = it },
        supportsGridSizeSlider = viewModel.listMode == org.skepsun.kototoro.core.prefs.ListMode.GRID,
        gridSize = viewModel.gridSize,
        onGridSizeChange = { viewModel.gridSize = it },
        sortOrders = viewModel.getSortOrders().orEmpty(),
        selectedSortOrder = viewModel.getSelectedSortOrder(),
        onSortOrderSelected = viewModel::setSortOrder,
        supportsGrouping = viewModel.isGroupingSupported,
        isGroupingAvailable = viewModel.isGroupingAvailable,
        isGroupingEnabled = viewModel.isGroupingEnabled,
        onGroupingEnabledChange = { viewModel.isGroupingEnabled = it },
        onDismissRequest = onDismissRequest,
    )
}
