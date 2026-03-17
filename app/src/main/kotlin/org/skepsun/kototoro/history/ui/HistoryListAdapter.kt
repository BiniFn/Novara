package org.skepsun.kototoro.history.ui

import android.content.Context
import org.skepsun.kototoro.core.ui.list.fastscroll.FastScroller
import org.skepsun.kototoro.list.ui.adapter.ContentListAdapter
import org.skepsun.kototoro.list.ui.adapter.ContentListListener
import org.skepsun.kototoro.list.ui.size.ItemSizeResolver

class HistoryListAdapter(
	listener: ContentListListener,
	sizeResolver: ItemSizeResolver,
) : ContentListAdapter(listener, sizeResolver), FastScroller.SectionIndexer {

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return findHeader(position)?.getText(context)
	}
}
