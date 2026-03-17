package org.skepsun.kototoro.explore.ui.adapter

import org.skepsun.kototoro.core.ui.BaseListAdapter
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.explore.ui.model.ExploreButtons
import org.skepsun.kototoro.explore.ui.model.ContentSourceItem
import org.skepsun.kototoro.explore.ui.model.RecommendationsItem
import org.skepsun.kototoro.list.ui.adapter.ListItemType
import org.skepsun.kototoro.list.ui.adapter.emptyHintAD
import org.skepsun.kototoro.list.ui.adapter.listHeaderAD
import org.skepsun.kototoro.list.ui.adapter.loadingStateAD
import org.skepsun.kototoro.list.ui.model.EmptyHint
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.LoadingState
import org.skepsun.kototoro.parsers.model.Content

class ExploreAdapter(
    private val eventListener: ExploreListEventListener,
    private val sourceClickListener: OnListItemClickListener<ContentSourceItem>,
    private val recommendationClickListener: OnListItemClickListener<Content>,
) : BaseListAdapter<ListModel>() {

    init {
        addDelegate(ListItemType.EXPLORE_BUTTONS, exploreButtonsAD(eventListener))
        addDelegate(ListItemType.EXPLORE_SUGGESTION, exploreRecommendationItemAD(recommendationClickListener))
        addDelegate(ListItemType.EXPLORE_SOURCE_LIST, exploreSourceListItemAD(sourceClickListener))
        addDelegate(ListItemType.EXPLORE_SOURCE_GRID, exploreSourceGridItemAD(sourceClickListener))
        addDelegate(ListItemType.HEADER, listHeaderAD(eventListener))
        addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
        addDelegate(ListItemType.HINT_EMPTY, emptyHintAD(eventListener))
    }

    override fun getItemViewType(position: Int): Int {
        val item = items?.getOrNull(position)
        return when (item) {
            is ExploreButtons -> ListItemType.EXPLORE_BUTTONS.ordinal
            is RecommendationsItem -> ListItemType.EXPLORE_SUGGESTION.ordinal
            is ContentSourceItem -> if (item.isGrid) {
                ListItemType.EXPLORE_SOURCE_GRID.ordinal
            } else {
                ListItemType.EXPLORE_SOURCE_LIST.ordinal
            }
            is ListHeader -> ListItemType.HEADER.ordinal
            is LoadingState -> ListItemType.STATE_LOADING.ordinal
            is EmptyHint -> ListItemType.HINT_EMPTY.ordinal
            else -> super.getItemViewType(position)
        }
    }
}
