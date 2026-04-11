package org.skepsun.kototoro.favourites.ui.list

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.list.ListSelectionController
import org.skepsun.kototoro.core.util.ext.sortedByOrdinal
import org.skepsun.kototoro.core.util.ext.withArgs
import org.skepsun.kototoro.databinding.FragmentContentListBinding
import org.skepsun.kototoro.list.domain.ListSortOrder
import org.skepsun.kototoro.list.ui.ContentListFragment

@AndroidEntryPoint
class FavouritesListFragment : ContentListFragment(), PopupMenu.OnMenuItemClickListener {

	override val viewModel by viewModels<FavouritesListViewModel>()

	override val isSwipeRefreshEnabled = false

	val categoryId
		get() = viewModel.categoryId

	override fun onViewBindingCreated(binding: FragmentContentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
	}

	override fun onScrolledToEnd() = viewModel.requestMoreItems()

	override fun onEmptyActionClick() = viewModel.clearFilter()

	override fun onFilterClick(view: View?) {
		val menu = PopupMenu(view?.context ?: return, view)
		menu.setOnMenuItemClickListener(this)
		val orders = ListSortOrder.FAVORITES.sortedByOrdinal()
		for ((i, item) in orders.withIndex()) {
			menu.menu.add(Menu.NONE, Menu.NONE, i, item.titleResId)
		}
		menu.show()
	}

	override fun onMenuItemClick(item: MenuItem): Boolean {
		val order = ListSortOrder.FAVORITES.sortedByOrdinal().getOrNull(item.order) ?: return false
		viewModel.setSortOrder(order)
		return true
	}


	override val showSelectionRemoveOption = true

	override fun onSelectionAction(action: org.skepsun.kototoro.list.ui.compose.SelectionAction, ids: Set<Long>): Boolean {
		return when (action) {
			org.skepsun.kototoro.list.ui.compose.SelectionAction.REMOVE -> {
				viewModel.removeFromFavourites(ids)
				true
			}
			else -> super.onSelectionAction(action, ids)
		}
	}

	companion object {

		const val NO_ID = 0L

		fun newInstance(categoryId: Long) = FavouritesListFragment().withArgs(1) {
			putLong(AppRouter.KEY_ID, categoryId)
		}
	}
}
