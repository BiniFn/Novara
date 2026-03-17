package org.skepsun.kototoro.scrobbling.common.ui.config.adapter

import org.skepsun.kototoro.core.ui.BaseListAdapter
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.list.ui.adapter.ListItemType
import org.skepsun.kototoro.list.ui.adapter.emptyStateListAD
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblingInfo

class ScrobblingContentAdapter(
	clickListener: OnListItemClickListener<ScrobblingInfo>,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.HEADER, scrobblingHeaderAD())
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
		addDelegate(ListItemType.MANGA_SCROBBLING, scrobblingContentAD(clickListener))
	}
}
