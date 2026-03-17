package org.skepsun.kototoro.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.core.ui.list.AdapterDelegateClickListenerAdapter
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.core.util.ext.setTooltipCompat
import org.skepsun.kototoro.core.util.ext.textAndVisible
import org.skepsun.kototoro.databinding.ItemMangaListBinding
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.MangaCompactListModel
import org.skepsun.kototoro.list.ui.model.MangaListModel

fun mangaListItemAD(
	clickListener: OnListItemClickListener<MangaListModel>,
) = adapterDelegateViewBinding<MangaCompactListModel, ListModel, ItemMangaListBinding>(
	{ inflater, parent -> ItemMangaListBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)

	bind {
		itemView.setTooltipCompat(item.getSummary(context))
		binding.textViewTitle.text = item.title
		binding.textViewSubtitle.textAndVisible = item.subtitle
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
		binding.badge.number = item.counter
		binding.badge.isVisible = item.counter > 0
	}
}
