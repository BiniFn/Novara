package org.skepsun.kototoro.discover.ui

import org.skepsun.kototoro.core.ui.BaseListAdapter
import org.skepsun.kototoro.discover.ui.model.DiscoverItem
import org.skepsun.kototoro.list.ui.adapter.ListItemType
import org.skepsun.kototoro.list.ui.adapter.ListStateHolderListener
import org.skepsun.kototoro.list.ui.adapter.emptyStateListAD
import org.skepsun.kototoro.list.ui.adapter.errorStateListAD
import org.skepsun.kototoro.list.ui.adapter.loadingStateAD
import org.skepsun.kototoro.list.ui.model.EmptyState
import org.skepsun.kototoro.list.ui.model.ErrorState
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingState

class DiscoverAdapter(
	listener: ListStateHolderListener,
	onItemClick: (DiscoverItem) -> Unit,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.INFO, discoverItemAD(onItemClick))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(listener))
		addDelegate(ListItemType.STATE_ERROR, errorStateListAD(listener))
	}

	override fun getItemViewType(position: Int): Int {
		return when (items?.getOrNull(position)) {
			is DiscoverItem -> ListItemType.INFO.ordinal
			is LoadingState -> ListItemType.STATE_LOADING.ordinal
			is EmptyState -> ListItemType.STATE_EMPTY.ordinal
			is ErrorState -> ListItemType.STATE_ERROR.ordinal
			else -> super.getItemViewType(position)
		}
	}
}
