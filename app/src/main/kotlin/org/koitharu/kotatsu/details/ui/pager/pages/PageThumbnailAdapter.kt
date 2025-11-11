package org.skepsun.kototoro.details.ui.pager.pages

import android.content.Context
import org.skepsun.kototoro.core.ui.BaseListAdapter
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.core.ui.list.fastscroll.FastScroller
import org.skepsun.kototoro.list.ui.adapter.ListItemType
import org.skepsun.kototoro.list.ui.adapter.listHeaderAD
import org.skepsun.kototoro.list.ui.model.ListModel

class PageThumbnailAdapter(
	clickListener: OnListItemClickListener<PageThumbnail>,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	init {
		addDelegate(ListItemType.PAGE_THUMB, pageThumbnailAD(clickListener))
		addDelegate(ListItemType.HEADER, listHeaderAD(null))
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return findHeader(position)?.getText(context)
	}
}
