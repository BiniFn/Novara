package org.skepsun.kototoro.explore.ui.adapter

import org.skepsun.kototoro.core.ui.BaseListAdapter
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.explore.ui.model.MangaSourceItem
import org.skepsun.kototoro.list.ui.adapter.ListItemType
import org.skepsun.kototoro.list.ui.adapter.emptyHintAD
import org.skepsun.kototoro.list.ui.adapter.listHeaderAD
import org.skepsun.kototoro.list.ui.adapter.loadingStateAD
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.parsers.model.Manga

class ExploreAdapter(
	listener: ExploreListEventListener,
	clickListener: OnListItemClickListener<MangaSourceItem>,
	mangaClickListener: OnListItemClickListener<Manga>,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.EXPLORE_BUTTONS, exploreButtonsAD(listener))
		addDelegate(
			ListItemType.EXPLORE_SUGGESTION,
			exploreRecommendationItemAD(mangaClickListener),
		)
		addDelegate(ListItemType.HEADER, listHeaderAD(listener))
		addDelegate(ListItemType.EXPLORE_SOURCE_LIST, exploreSourceListItemAD(clickListener))
		addDelegate(ListItemType.EXPLORE_SOURCE_GRID, exploreSourceGridItemAD(clickListener))
		addDelegate(ListItemType.HINT_EMPTY, emptyHintAD(listener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
	}
}
