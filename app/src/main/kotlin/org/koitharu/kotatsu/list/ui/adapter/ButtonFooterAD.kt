package org.skepsun.kototoro.list.ui.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.databinding.ItemButtonFooterBinding
import org.skepsun.kototoro.list.ui.model.ButtonFooter
import org.skepsun.kototoro.list.ui.model.ListModel

fun buttonFooterAD(
	listener: ListStateHolderListener,
) = adapterDelegateViewBinding<ButtonFooter, ListModel, ItemButtonFooterBinding>(
	{ inflater, parent -> ItemButtonFooterBinding.inflate(inflater, parent, false) },
) {

	binding.button.setOnClickListener {
		listener.onFooterButtonClick()
	}

	bind {
		binding.button.setText(item.textResId)
	}
}
