package org.skepsun.kototoro.list.ui.adapter

import android.view.View
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentTag

interface ContentDetailsClickListener : OnListItemClickListener<ContentListModel> {

	fun onReadClick(manga: Content, view: View)

	fun onTagClick(manga: Content, tag: ContentTag, view: View)
}
