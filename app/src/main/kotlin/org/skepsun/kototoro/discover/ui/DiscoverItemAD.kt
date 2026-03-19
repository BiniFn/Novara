package org.skepsun.kototoro.discover.ui

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.R
import org.skepsun.kototoro.databinding.ItemDiscoverSiteBinding
import org.skepsun.kototoro.discover.ui.model.DiscoverItem
import org.skepsun.kototoro.list.ui.model.ListModel

fun discoverItemAD(
	onItemClick: (DiscoverItem) -> Unit,
) = adapterDelegateViewBinding<DiscoverItem, ListModel, ItemDiscoverSiteBinding>(
	{ inflater, parent -> ItemDiscoverSiteBinding.inflate(inflater, parent, false) },
) {
	binding.root.setOnClickListener {
		onItemClick(item)
	}

	bind {
		val model = item.item
		binding.imageViewCover.setImageAsync(model.coverUrl)
		binding.textViewTitle.text = model.title
		binding.textViewSubtitle.text = model.altTitle ?: model.subtitle.orEmpty()
		binding.textViewSubtitle.isVisible = !binding.textViewSubtitle.text.isNullOrBlank()
		binding.textViewSummary.text = model.subtitle ?: context.getString(R.string.discover_no_summary)
		binding.textViewSite.setText(model.service.titleResId)
		binding.textViewScore.isVisible = model.score != null
		binding.textViewScore.text = model.score?.let { score ->
			context.getString(R.string.discover_score, score)
		}
		binding.root.isEnabled = !model.url.isNullOrBlank()
		binding.root.alpha = if (binding.root.isEnabled) 1f else 0.72f
	}
}
