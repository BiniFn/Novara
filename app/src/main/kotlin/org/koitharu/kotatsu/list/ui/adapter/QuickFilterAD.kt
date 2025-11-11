package org.skepsun.kototoro.list.ui.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.core.ui.widgets.ChipsView
import org.skepsun.kototoro.databinding.ItemQuickFilterBinding
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.QuickFilter

fun quickFilterAD(
	listener: QuickFilterClickListener,
) = adapterDelegateViewBinding<QuickFilter, ListModel, ItemQuickFilterBinding>(
	{ layoutInflater, parent -> ItemQuickFilterBinding.inflate(layoutInflater, parent, false) }
) {

	binding.chipsTags.onChipClickListener = ChipsView.OnChipClickListener { chip, data ->
		if (data is ListFilterOption) {
			listener.onFilterOptionClick(data)
		}
	}

	bind {
		binding.chipsTags.setChips(item.items)
	}
}
