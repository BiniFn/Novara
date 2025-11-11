package org.skepsun.kototoro.scrobbling.common.ui.selector.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.core.util.ext.getDisplayMessage
import org.skepsun.kototoro.core.util.ext.setTextAndVisible
import org.skepsun.kototoro.core.util.ext.textAndVisible
import org.skepsun.kototoro.databinding.ItemEmptyHintBinding
import org.skepsun.kototoro.list.ui.adapter.ListStateHolderListener
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.scrobbling.common.ui.selector.model.ScrobblerHint

fun scrobblerHintAD(
	listener: ListStateHolderListener,
) = adapterDelegateViewBinding<ScrobblerHint, ListModel, ItemEmptyHintBinding>(
	{ inflater, parent -> ItemEmptyHintBinding.inflate(inflater, parent, false) },
) {

	binding.buttonRetry.setOnClickListener {
		val e = item.error
		if (e != null) {
			listener.onRetryClick(e)
		} else {
			listener.onEmptyActionClick()
		}
	}

	bind {
		binding.icon.setImageResource(item.icon)
		binding.textPrimary.setText(item.textPrimary)
		if (item.error != null) {
			binding.textSecondary.textAndVisible = item.error?.getDisplayMessage(context.resources)
		} else {
			binding.textSecondary.setTextAndVisible(item.textSecondary)
		}
		binding.buttonRetry.setTextAndVisible(item.actionStringRes)
	}
}
