package org.skepsun.kototoro.history.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.dialog.buildAlertDialog
import org.skepsun.kototoro.core.ui.list.ListSelectionController
import org.skepsun.kototoro.core.ui.list.RecyclerScrollKeeper
import org.skepsun.kototoro.core.ui.util.MenuInvalidator
import org.skepsun.kototoro.core.util.ext.addMenuProvider
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.databinding.FragmentContentListBinding
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.list.ui.ContentListFragment
import org.skepsun.kototoro.list.ui.size.DynamicItemSizeResolver

@AndroidEntryPoint
class HistoryListFragment : ContentListFragment() {

	override val viewModel by viewModels<HistoryListViewModel>()
	override val isSwipeRefreshEnabled = false
	override fun sourceTagChipEntries(): List<SourceTag> = SourceTag.quickFilterEntries

	override fun onViewBindingCreated(binding: FragmentContentListBinding, savedInstanceState: Bundle?) {
		arguments?.getString(AppRouter.KEY_GROUP_TAB)
			?.let(BrowseGroupTab::fromId)
			?.let(viewModel::setSelectedGroupTab)
		super.onViewBindingCreated(binding, savedInstanceState)

		addMenuProvider(HistoryListMenuProvider(binding.root.context, router, viewModel))
		viewModel.isStatsEnabled.observe(viewLifecycleOwner, MenuInvalidator(requireActivity()))
	}

	override fun onScrolledToEnd() = viewModel.requestMoreItems()

	override fun onEmptyActionClick() = viewModel.clearFilter()

	override val showSelectionRemoveOption = true

	override fun onSelectionAction(action: org.skepsun.kototoro.list.ui.compose.SelectionAction, ids: Set<Long>): Boolean {
		return when (action) {
			org.skepsun.kototoro.list.ui.compose.SelectionAction.REMOVE -> {
				viewModel.removeFromHistory(ids)
				true
			}
			/* Note: MARK_AS_CURRENT isn't natively supported by SelectionAction yet, we will map it if needed or migrate it out. */
			else -> super.onSelectionAction(action, ids)
		}
	}
}
