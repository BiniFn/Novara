package org.skepsun.kototoro.details.ui.scrobbling

import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.BaseListAdapter
import org.skepsun.kototoro.list.ui.model.ListModel

class ScrollingInfoAdapter(
	router: AppRouter,
) : BaseListAdapter<ListModel>() {

	init {
		delegatesManager.addDelegate(scrobblingInfoAD(router))
	}
}
