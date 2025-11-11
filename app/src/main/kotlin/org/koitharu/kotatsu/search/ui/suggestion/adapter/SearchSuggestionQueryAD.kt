package org.skepsun.kototoro.search.ui.suggestion.adapter

import android.view.View
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.R
import org.skepsun.kototoro.databinding.ItemSearchSuggestionQueryBinding
import org.skepsun.kototoro.search.domain.SearchKind
import org.skepsun.kototoro.search.ui.suggestion.SearchSuggestionListener
import org.skepsun.kototoro.search.ui.suggestion.model.SearchSuggestionItem

fun searchSuggestionQueryAD(
	listener: SearchSuggestionListener,
) =
	adapterDelegateViewBinding<SearchSuggestionItem.RecentQuery, SearchSuggestionItem, ItemSearchSuggestionQueryBinding>(
		{ inflater, parent -> ItemSearchSuggestionQueryBinding.inflate(inflater, parent, false) },
	) {

		val viewClickListener = View.OnClickListener { v ->
			listener.onQueryClick(item.query, SearchKind.SIMPLE, v.id != R.id.button_complete)
		}

		binding.root.setOnClickListener(viewClickListener)
		binding.buttonComplete.setOnClickListener(viewClickListener)

		bind {
			binding.textViewTitle.text = item.query
		}
	}
