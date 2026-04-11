package org.skepsun.kototoro.tracker.ui.updates

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem

import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.AppRouter

import org.skepsun.kototoro.databinding.FragmentContentListBinding
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.list.ui.ContentListFragment

@AndroidEntryPoint
class UpdatesFragment : ContentListFragment() {

	override val viewModel by viewModels<UpdatesViewModel>()
	override val isSwipeRefreshEnabled = false
	override val showSelectionRemoveOption = true

	override fun onViewBindingCreated(binding: FragmentContentListBinding, savedInstanceState: Bundle?) {
		arguments?.getString(AppRouter.KEY_GROUP_TAB)
			?.let(BrowseGroupTab::fromId)
			?.let(viewModel::setSelectedGroupTab)
		super.onViewBindingCreated(binding, savedInstanceState)
	}

	override fun onScrolledToEnd() = Unit

	override fun onSelectionAction(action: org.skepsun.kototoro.list.ui.compose.SelectionAction, ids: Set<Long>): Boolean {
		return when (action) {
			org.skepsun.kototoro.list.ui.compose.SelectionAction.REMOVE -> {
				viewModel.remove(ids)
				true
			}
			else -> super.onSelectionAction(action, ids)
		}
	}
}
