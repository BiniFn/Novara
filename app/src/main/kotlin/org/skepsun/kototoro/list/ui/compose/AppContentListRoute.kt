package org.skepsun.kototoro.list.ui.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.main.ui.SearchBarFilterViewController
import org.skepsun.kototoro.list.ui.ContentListViewModel
import org.skepsun.kototoro.main.ui.MainActivity
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun <VM : ContentListViewModel> AppContentListRoute(
    viewModel: VM,
    contentPadding: PaddingValues,
    appRouter: AppRouter,
    showRemoveOption: Boolean = false,
    isContentTypeFilterVisible: Boolean = true,
    isSourceTagFilterVisible: Boolean = true,
    onRemoveSelection: ((Set<Long>) -> Unit)? = null,
    onShareSelection: ((Set<Long>) -> Unit)? = null,
    onEmptyActionClick: (() -> Unit)? = null,
    onAddMenuProvider: ((androidx.activity.ComponentActivity, VM, androidx.lifecycle.LifecycleOwner) -> androidx.core.view.MenuProvider?)? = null
) {
    val items by viewModel.content.collectAsStateWithLifecycle(initialValue = emptyList())
    val listMode by viewModel.listMode.collectAsStateWithLifecycle(initialValue = org.skepsun.kototoro.core.prefs.ListMode.GRID)
    val gridScale by viewModel.gridScale.collectAsStateWithLifecycle(initialValue = 1f)
    val isRefreshing by viewModel.isLoading.collectAsStateWithLifecycle(initialValue = false)

    var composeSelectionIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }

    val activity = LocalContext.current as? androidx.activity.ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    // Error observation
    LaunchedEffect(viewModel.onError) {
        val host = activity?.window?.decorView?.rootView ?: return@LaunchedEffect
        val observer = SnackbarErrorObserver(host, null)
        viewModel.onError.collect { event ->
            event?.consume(observer)
        }
    }

    // Menu Provider
    if (onAddMenuProvider != null) {
        DisposableEffect(viewModel, activity, lifecycleOwner) {
            val menuProvider = onAddMenuProvider(activity ?: return@DisposableEffect onDispose {}, viewModel, lifecycleOwner)
            if (menuProvider != null) {
                activity.addMenuProvider(menuProvider, lifecycleOwner, androidx.lifecycle.Lifecycle.State.RESUMED)
            }
            onDispose {
                if (menuProvider != null) {
                    activity?.removeMenuProvider(menuProvider)
                }
            }
        }
    }

    // Filter Coordinator integration via MainActivity callback
    val mainActivity = activity as? MainActivity
    DisposableEffect(mainActivity, viewModel) {
        val callback = object : SearchBarFilterViewController.Callback {
            override fun isContentTypeFilterVisible(): Boolean = isContentTypeFilterVisible
            override fun isSourceTagFilterVisible(): Boolean = isSourceTagFilterVisible
            
            override fun getSelectedContentType(): org.skepsun.kototoro.explore.ui.model.BrowseGroupTab {
                return viewModel.currentGroupTab.value ?: org.skepsun.kototoro.explore.ui.model.BrowseGroupTab.All
            }
            
            override fun onContentTypeSelected(tab: org.skepsun.kototoro.explore.ui.model.BrowseGroupTab) {
                viewModel.setSelectedGroupTab(if (viewModel.currentGroupTab.value == tab) org.skepsun.kototoro.explore.ui.model.BrowseGroupTab.All else tab)
            }
            
            override fun getSelectedSourceTags(): Set<org.skepsun.kototoro.explore.ui.model.SourceTag> {
                return viewModel.currentSourceTags.value ?: emptySet()
            }
            
            override fun onSourceTagSelected(tag: org.skepsun.kototoro.explore.ui.model.SourceTag?) {
                if (tag != null) {
                    val current = viewModel.currentSourceTags.value ?: emptySet()
                    viewModel.setSelectedSourceTags(if (tag in current) current - tag else current + tag)
                }
            }
            
            override fun getSourceTagEntries(): List<org.skepsun.kototoro.explore.ui.model.SourceTag> {
                return org.skepsun.kototoro.explore.ui.model.SourceTag.quickFilterEntries
            }
        }
        
        mainActivity?.setActiveFilterCallback(callback)
        onDispose {
            mainActivity?.clearActiveFilterCallback(callback)
        }
    }

    KototoroContentListScreen(
        contentPadding = contentPadding,
        items = items,
        listMode = listMode,
        isRefreshing = isRefreshing,
        showRemoveOption = showRemoveOption,
        onRefresh = { viewModel.onRefresh() },
        onLoadMore = { },
        gridScale = gridScale,
        selectedItemsIds = composeSelectionIds,
        onItemClick = { item ->
            if (composeSelectionIds.isNotEmpty()) {
                composeSelectionIds = if (item.id in composeSelectionIds) composeSelectionIds - item.id else composeSelectionIds + item.id
            } else {
                appRouter.openDetails(item.toContentWithOverride(), null)
            }
        },
        onItemLongClick = { item ->
            if (composeSelectionIds.isEmpty()) {
                composeSelectionIds = setOf(item.id)
            } else {
                composeSelectionIds = if (item.id in composeSelectionIds) composeSelectionIds - item.id else composeSelectionIds + item.id
            }
        },
        onClearSelection = { composeSelectionIds = emptySet() },
        onSelectionAction = { action ->
            when (action) {
                SelectionAction.SELECT_ALL -> {
                    val allIds = viewModel.content.value.mapNotNull { (it as? org.skepsun.kototoro.list.ui.model.ContentListModel)?.id }.toSet()
                    composeSelectionIds = allIds
                }
                SelectionAction.REMOVE -> {
                    onRemoveSelection?.invoke(composeSelectionIds)
                    composeSelectionIds = emptySet()
                }
                SelectionAction.SHARE -> {
                    onShareSelection?.invoke(composeSelectionIds)
                    composeSelectionIds = emptySet()
                }
                else -> {}
            }
        }
    )
}
