package org.skepsun.kototoro.scrobbling.common.ui.selector.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.core.util.ext.textAndVisible
import org.skepsun.kototoro.databinding.ItemContentListBinding
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerContent

fun scrobblingContentAD(
	clickListener: OnListItemClickListener<ScrobblerContent>,
) = adapterDelegateViewBinding<ScrobblerContent, ListModel, ItemContentListBinding>(
	{ inflater, parent -> ItemContentListBinding.inflate(inflater, parent, false) },
) {
	itemView.setOnClickListener {
		clickListener.onItemClick(item, it)
	}

	bind {
		binding.textViewTitle.text = item.name
		val endIcon = if (item.isBestMatch) R.drawable.ic_star_small else 0
		binding.textViewTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, endIcon, 0)
		binding.textViewSubtitle.textAndVisible = item.altName
		binding.imageViewCover.setImageAsync(item.cover)
	}
}
