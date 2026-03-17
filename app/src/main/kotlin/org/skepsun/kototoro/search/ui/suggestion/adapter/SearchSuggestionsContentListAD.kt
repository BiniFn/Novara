package org.skepsun.kototoro.search.ui.suggestion.adapter

import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.AsyncListDifferDelegationAdapter
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegate
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.list.decor.SpacingItemDecoration
import org.skepsun.kototoro.core.util.RecyclerViewScrollCallback
import org.skepsun.kototoro.core.util.ext.setTooltipCompat
import org.skepsun.kototoro.databinding.ItemSearchSuggestionContentGridBinding
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.search.ui.suggestion.SearchSuggestionListener
import org.skepsun.kototoro.search.ui.suggestion.model.SearchSuggestionItem

fun searchSuggestionContentListAD(
	listener: SearchSuggestionListener,
) = adapterDelegate<SearchSuggestionItem.ContentList, SearchSuggestionItem>(R.layout.item_search_suggestion_content_list) {
	val adapter = AsyncListDifferDelegationAdapter(
		SuggestionContentDiffCallback(),
		searchSuggestionContentGridAD(listener),
	)
	val recyclerView = itemView as RecyclerView
	recyclerView.adapter = adapter
	val spacing = context.resources.getDimensionPixelOffset(R.dimen.search_suggestions_manga_spacing)
	recyclerView.updatePadding(
		left = recyclerView.paddingLeft - spacing,
		right = recyclerView.paddingRight - spacing,
	)
	recyclerView.addItemDecoration(SpacingItemDecoration(spacing, withBottomPadding = true))
	val scrollResetCallback = RecyclerViewScrollCallback(recyclerView, 0, 0)

	bind {
		adapter.setItems(item.items, scrollResetCallback)
	}
}

private fun searchSuggestionContentGridAD(
	listener: SearchSuggestionListener,
) = adapterDelegateViewBinding<Content, Content, ItemSearchSuggestionContentGridBinding>(
	{ layoutInflater, parent -> ItemSearchSuggestionContentGridBinding.inflate(layoutInflater, parent, false) },
) {
	itemView.setOnClickListener {
		listener.onContentClick(item)
	}

	bind {
		itemView.setTooltipCompat(item.title)
		binding.imageViewCover.setImageAsync(item.coverUrl, item.source)
		binding.textViewTitle.text = item.title
	}
}

private class SuggestionContentDiffCallback : DiffUtil.ItemCallback<Content>() {

	override fun areItemsTheSame(oldItem: Content, newItem: Content): Boolean {
		return oldItem.id == newItem.id
	}

	override fun areContentsTheSame(oldItem: Content, newItem: Content): Boolean {
		return oldItem.title == newItem.title && oldItem.coverUrl == newItem.coverUrl
	}
}
