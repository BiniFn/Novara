package org.skepsun.kototoro.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.core.ui.list.AdapterDelegateClickListenerAdapter
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.core.util.ext.setTooltipCompat
import org.skepsun.kototoro.core.util.ext.textAndVisible
import org.skepsun.kototoro.databinding.ItemContentListBinding
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.ContentCompactListModel
import androidx.core.content.ContextCompat
import org.skepsun.kototoro.core.util.ext.drawableStart
import org.skepsun.kototoro.list.ui.model.ContentListModel

fun mangaListItemAD(
	clickListener: OnListItemClickListener<ContentListModel>,
) = adapterDelegateViewBinding<ContentCompactListModel, ListModel, ItemContentListBinding>(
	{ inflater, parent -> ItemContentListBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)
	val iconPinned = ContextCompat.getDrawable(context, org.skepsun.kototoro.R.drawable.ic_pin_small)

	bind {
		itemView.setTooltipCompat(item.getSummary(context))
		binding.textViewTitle.text = item.title
		binding.textViewTitle.drawableStart = if (item.isPinned) iconPinned else null
		binding.textViewSubtitle.textAndVisible = item.subtitle
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
		binding.badge.number = item.counter
		binding.badge.isVisible = item.counter > 0
	}
}
