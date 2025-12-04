package org.skepsun.kototoro.details.ui.adapter

import android.content.Context
import org.skepsun.kototoro.core.ui.BaseListAdapter
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.core.ui.list.fastscroll.FastScroller
import org.skepsun.kototoro.details.ui.model.ChapterListItem
import org.skepsun.kototoro.list.ui.adapter.CollapsibleHeaderClickListener
import org.skepsun.kototoro.list.ui.adapter.ListItemType
import org.skepsun.kototoro.list.ui.adapter.collapsibleListHeaderAD
import org.skepsun.kototoro.list.ui.adapter.listHeaderAD
import org.skepsun.kototoro.list.ui.model.CollapsibleListHeader
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.list.ui.model.ListModel

class ChaptersAdapter(
	onItemClickListener: OnListItemClickListener<ChapterListItem>,
	collapsibleHeaderClickListener: CollapsibleHeaderClickListener?,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	private var hasVolumes = false
	private var hasCollapsibleGroups = false

	init {
		addDelegate(ListItemType.HEADER, listHeaderAD(null))
		addDelegate(ListItemType.COLLAPSIBLE_HEADER, collapsibleListHeaderAD(collapsibleHeaderClickListener))
		addDelegate(ListItemType.CHAPTER_LIST, chapterListItemAD(onItemClickListener))
		addDelegate(ListItemType.CHAPTER_GRID, chapterGridItemAD(onItemClickListener))
	}

	override suspend fun emit(value: List<ListModel>?) {
		super.emit(value)
		hasVolumes = value != null && value.any { it is ListHeader }
		hasCollapsibleGroups = value != null && value.any { it is CollapsibleListHeader }
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return if (hasVolumes || hasCollapsibleGroups) {
			val header = findHeader(position)
			if (header != null) {
				header.getText(context)
			} else {
				val collapsibleHeader = findCollapsibleHeader(position)
				collapsibleHeader?.getText(context)
			}
		} else {
			val chapter = (items.getOrNull(position) as? ChapterListItem)?.chapter ?: return null
			chapter.numberString()
		}
	}

	private fun findCollapsibleHeader(position: Int): CollapsibleListHeader? {
		for (i in position downTo 0) {
			val item = items.getOrNull(i)
			if (item is CollapsibleListHeader) {
				return item
			}
		}
		return null
	}
}
