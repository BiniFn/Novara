package org.skepsun.kototoro.favourites.ui.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.favourites.ui.list.FavouritesListViewModel
import org.skepsun.kototoro.list.ui.compose.AppContentListRoute
import org.skepsun.kototoro.list.ui.compose.SelectionAction

@Composable
fun KototoroFavoritesListScreen(
    categoryId: Long,
    appRouter: AppRouter,
    contentPadding: PaddingValues,
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
        onRemoveSelection = { ids ->
            viewModel.removeFromFavourites(ids)
        }
    )
}
