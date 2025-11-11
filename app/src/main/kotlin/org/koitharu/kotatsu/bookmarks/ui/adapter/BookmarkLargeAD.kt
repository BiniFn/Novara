package org.skepsun.kototoro.bookmarks.ui.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.bookmarks.domain.Bookmark
import org.skepsun.kototoro.core.ui.list.AdapterDelegateClickListenerAdapter
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.databinding.ItemBookmarkLargeBinding
import org.skepsun.kototoro.list.ui.model.ListModel

fun bookmarkLargeAD(
	clickListener: OnListItemClickListener<Bookmark>,
) = adapterDelegateViewBinding<Bookmark, ListModel, ItemBookmarkLargeBinding>(
	{ inflater, parent -> ItemBookmarkLargeBinding.inflate(inflater, parent, false) },
) {
	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)

	bind {
		binding.imageViewThumb.setImageAsync(item)
		binding.progressView.setProgress(item.percent, false)
	}
}
