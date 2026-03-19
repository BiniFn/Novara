package org.skepsun.kototoro.details.ui.scrobbling

import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.BaseListAdapter
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService

class ScrollingInfoAdapter(
	router: AppRouter,
	preferredScrobblerProvider: () -> ScrobblerService,
) : BaseListAdapter<ListModel>() {

	init {
		delegatesManager.addDelegate(scrobblingInfoAD(router, preferredScrobblerProvider))
	}
}
