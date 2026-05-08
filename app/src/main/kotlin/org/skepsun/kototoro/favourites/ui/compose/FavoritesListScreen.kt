package org.skepsun.kototoro.favourites.ui.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.favourites.ui.list.FavouritesListViewModel
import org.skepsun.kototoro.list.ui.compose.AppContentListRoute
import org.skepsun.kototoro.list.ui.compose.SelectionAction
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.R
import org.skepsun.kototoro.main.ui.compose.TopBarOverrideState
import org.skepsun.kototoro.parsers.model.Content

@Composable
fun KototoroFavoritesListScreen(
    categoryId: Long,
    appRouter: AppRouter,
    contentPadding: PaddingValues,
    onNavigateToDetails: ((Content, String?) -> Unit)? = null,
    sharedTransitionEnabled: Boolean = true,
    onTopBarOverrideChanged: (TopBarOverrideState?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val viewModel = hiltViewModel<FavouritesListViewModel, FavouritesListViewModel.Factory>(
        key = categoryId.toString()
    ) { factory ->
        factory.create(categoryId)
    }

    AppContentListRoute(
        viewModel = viewModel,
        contentPadding = contentPadding,
        appRouter = appRouter,
        showRemoveOption = true,
        preferredSelectionInlineActions = listOf(
            SelectionAction.PIN,
            SelectionAction.REMOVE,
            SelectionAction.SAVE,
        ),
        removeSelectionActionIconRes = R.drawable.ic_heart_outline,
        removeSelectionActionTitleRes = R.string.remove_from_favourites,
        onTopBarOverrideChanged = onTopBarOverrideChanged,
        sharedTransitionEnabled = sharedTransitionEnabled,
        registerFilterCallback = false, // Parent FavoritesHostScreen manages the centralized callback
        pullRefreshEnabled = false,
        onLoadMore = { viewModel.requestMoreItems() },
        onNavigateToDetails = onNavigateToDetails,
        onRemoveSelection = { ids ->
            viewModel.removeFromFavourites(ids)
        },
        onPinSelection = { ids ->
            viewModel.togglePinned(ids)
        },
        onMarkAsCompletedSelection = { items ->
            viewModel.markAsRead(items.map { it.manga }.toSet())
        }
    )
}
