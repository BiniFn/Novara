package org.skepsun.kototoro.list.ui.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.BackHandler
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.main.ui.SearchBarFilterViewController
import org.skepsun.kototoro.list.ui.ContentListViewModel
import org.skepsun.kototoro.main.ui.MainActivity
import androidx.compose.runtime.saveable.rememberSaveable
import org.skepsun.kototoro.core.ui.dialog.buildAlertDialog
import org.skepsun.kototoro.alternatives.ui.AutoFixService
import org.skepsun.kototoro.core.util.ShareHelper
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.core.ui.compose.contentCoverSharedKey
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.main.ui.compose.ContentSelectionTopBarOverrideState
import org.skepsun.kototoro.main.ui.compose.TopBarOverrideState

private data class ContentSelectionModels(
    val allContentIds: Set<Long>,
    val selectedModels: List<ContentListModel>,
)

private fun prepareContentSelectionModels(
    items: List<ListModel>,
    selectedIds: Set<Long>,
): ContentSelectionModels {
    val allContentIds = linkedSetOf<Long>()
    val selectedModels = ArrayList<ContentListModel>()
    items.forEach { item ->
        if (item is ContentListModel) {
            allContentIds += item.id
            if (item.id in selectedIds) {
                selectedModels += item
            }
        }
    }
    return ContentSelectionModels(
        allContentIds = allContentIds,
        selectedModels = selectedModels,
    )
}

@Composable
fun <VM : ContentListViewModel> AppContentListRoute(
    viewModel: VM,
    contentPadding: PaddingValues,
    appRouter: AppRouter,
    onTopBarOverrideChanged: (TopBarOverrideState?) -> Unit = {},
    showRemoveOption: Boolean = false,
    sharedTransitionEnabled: Boolean = true,
    isContentTypeFilterVisible: Boolean = true,
    isSourceTagFilterVisible: Boolean = true,
    registerFilterCallback: Boolean = true,
    onRemoveSelection: ((Set<Long>) -> Unit)? = null,
    onShareSelection: ((Set<Long>) -> Unit)? = null,
    onPinSelection: ((Set<Long>) -> Unit)? = null,
    onMarkAsCompletedSelection: ((List<ContentListModel>) -> Unit)? = null,
    onEmptyActionClick: (() -> Unit)? = null,
    onLoadMore: () -> Unit = {},
    onNavigateToDetails: ((org.skepsun.kototoro.parsers.model.Content, String?) -> Unit)? = null,
    onAddMenuProvider: ((androidx.activity.ComponentActivity, VM, androidx.lifecycle.LifecycleOwner) -> androidx.core.view.MenuProvider?)? = null,
    listHeader: (@Composable () -> Unit)? = null,
) {
    val items by viewModel.content.collectAsStateWithLifecycle(initialValue = emptyList())
    val listMode by viewModel.listMode.collectAsStateWithLifecycle(initialValue = org.skepsun.kototoro.core.prefs.ListMode.GRID)
    val gridScale by viewModel.gridScale.collectAsStateWithLifecycle(initialValue = 1f)
    val isRefreshing by viewModel.isLoading.collectAsStateWithLifecycle(initialValue = false)

    var composeSelectionIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }

    val activity = LocalContext.current as? androidx.activity.ComponentActivity
    val context = LocalContext.current
    val rootView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val selectionModels = remember(items, composeSelectionIds) {
        prepareContentSelectionModels(items, composeSelectionIds)
    }
    val selectedModels = selectionModels.selectedModels

    BackHandler(enabled = composeSelectionIds.isNotEmpty()) {
        composeSelectionIds = emptySet()
    }

    SideEffect {
        if (composeSelectionIds.isNotEmpty()) {
            val supportedActions = buildSet {
                add(SelectionAction.SELECT_ALL)
                add(SelectionAction.SHARE)
                add(SelectionAction.FAVOURITE)
                add(SelectionAction.SAVE)
                if (showRemoveOption || onRemoveSelection != null) {
                    add(SelectionAction.REMOVE)
                }
                if (onPinSelection != null) {
                    add(SelectionAction.PIN)
                }
                if (onMarkAsCompletedSelection != null) {
                    add(SelectionAction.MARK_AS_COMPLETED)
                }
            }
            onTopBarOverrideChanged(
                ContentSelectionTopBarOverrideState(
                    selectedCount = composeSelectionIds.size,
                    isAllNonLocal = selectedModels.none { it.manga.isLocal },
                    isSingleSelection = composeSelectionIds.size == 1,
                    showRemoveOption = showRemoveOption,
                    supportedActions = supportedActions,
                    onClearSelection = { composeSelectionIds = emptySet() },
                    onActionClick = { action ->
                        when (action) {
                            SelectionAction.SELECT_ALL -> {
                                composeSelectionIds = selectionModels.allContentIds
                            }

                            SelectionAction.REMOVE -> {
                                onRemoveSelection?.invoke(composeSelectionIds)
                                composeSelectionIds = emptySet()
                            }

                            SelectionAction.SHARE -> {
                                if (onShareSelection != null) {
                                    onShareSelection(composeSelectionIds)
                                } else {
                                    ShareHelper(context).shareContentLinks(selectedModels.map { it.manga })
                                }
                                composeSelectionIds = emptySet()
                            }

                            SelectionAction.FAVOURITE -> {
                                appRouter.showFavoriteDialog(selectedModels.map { it.manga })
                                composeSelectionIds = emptySet()
                            }

                            SelectionAction.SAVE -> {
                                appRouter.showDownloadDialog(selectedModels.map { it.manga }, rootView)
                                composeSelectionIds = emptySet()
                            }

                            SelectionAction.EDIT_OVERRIDE -> {
                                selectedModels.singleOrNull()?.manga?.let(appRouter::openContentOverrideConfig)
                                composeSelectionIds = emptySet()
                            }

                            SelectionAction.FIX -> {
                                buildAlertDialog(context, isCentered = true) {
                                    setTitle(org.skepsun.kototoro.R.string.fix)
                                    setMessage(org.skepsun.kototoro.R.string.manga_fix_prompt)
                                    setNegativeButton(android.R.string.cancel, null)
                                    setPositiveButton(org.skepsun.kototoro.R.string.fix) { _, _ ->
                                        AutoFixService.start(context, composeSelectionIds)
                                    }
                                }.show()
                            }

                            SelectionAction.PIN -> {
                                onPinSelection?.invoke(composeSelectionIds)
                                composeSelectionIds = emptySet()
                            }

                            SelectionAction.MARK_AS_COMPLETED -> {
                                val itemsToMark = selectedModels
                                buildAlertDialog(context, isCentered = true) {
                                    setTitle(org.skepsun.kototoro.R.string.mark_as_completed)
                                    setMessage(org.skepsun.kototoro.R.string.mark_as_completed_prompt)
                                    setNegativeButton(android.R.string.cancel, null)
                                    setPositiveButton(android.R.string.ok) { _, _ ->
                                        onMarkAsCompletedSelection?.invoke(itemsToMark)
                                    }
                                }.show()
                                composeSelectionIds = emptySet()
                            }
                        }
                    },
                ),
            )
        } else {
            onTopBarOverrideChanged(null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onTopBarOverrideChanged(null)
        }
    }

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
    // When registerFilterCallback is false, the parent composable manages the callback
    // (e.g. FavoritesHostScreen centralizes it to avoid HorizontalPager contention)
    if (registerFilterCallback) {
        val mainActivity = activity as? MainActivity
        val selectedGroupTab by viewModel.currentGroupTab.collectAsStateWithLifecycle()
        val selectedSourceTags by viewModel.currentSourceTags.collectAsStateWithLifecycle()

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
                    val current = viewModel.currentSourceTags.value ?: emptySet()
                    viewModel.setSelectedSourceTags(
                        if (tag == null) {
                            emptySet()
                        } else if (tag in current) {
                            current - tag
                        } else {
                            current + tag
                        }
                    )
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

        // 每次过滤状态变化时刷新胶囊栏的选中状态
        SideEffect {
            mainActivity?.refreshFilters()
        }
    }

    KototoroContentListScreen(
        contentPadding = contentPadding,
        items = items,
        listMode = listMode,
        isRefreshing = isRefreshing,
        showRemoveOption = showRemoveOption,
        sharedTransitionEnabled = sharedTransitionEnabled,
        onRefresh = { viewModel.onRefresh() },
        onLoadMore = onLoadMore,
        gridScale = gridScale,
        selectedItemsIds = composeSelectionIds,
        onPrepareItemTransition = { item, coverBounds ->
        },
        onItemClick = { item ->
            if (composeSelectionIds.isNotEmpty()) {
                composeSelectionIds = if (item.id in composeSelectionIds) composeSelectionIds - item.id else composeSelectionIds + item.id
            } else {
                val content = item.toContentWithOverride()
                val sharedElementKey = contentCoverSharedKey(item.source.name, item.coverUrl.orEmpty())
                if (onNavigateToDetails != null) {
                    onNavigateToDetails(content, sharedElementKey)
                } else {
                    appRouter.openDetails(content, rootView)
                }
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
        },
        onQuickFilterOptionClick = { option ->
            (viewModel as? org.skepsun.kototoro.list.domain.QuickFilterListener)?.toggleFilterOption(option)
        },
        onEmptyActionClick = {
            onEmptyActionClick?.invoke() ?: viewModel.onRetry()
        },
        onRetry = viewModel::onRetry,
        showInlineSelectionTopBar = false,
        listHeader = listHeader,
    )
}
