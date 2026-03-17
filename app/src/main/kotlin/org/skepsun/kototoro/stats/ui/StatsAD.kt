package org.skepsun.kototoro.stats.ui

import android.content.res.ColorStateList
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.core.util.KototoroColors
import org.skepsun.kototoro.databinding.ItemStatsBinding
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.stats.domain.StatsRecord

fun statsAD(
	listener: OnListItemClickListener<Content>,
) = adapterDelegateViewBinding<StatsRecord, StatsRecord, ItemStatsBinding>(
	{ layoutInflater, parent -> ItemStatsBinding.inflate(layoutInflater, parent, false) },
) {

	binding.root.setOnClickListener { v ->
		listener.onItemClick(item.manga ?: return@setOnClickListener, v)
	}

	bind {
		binding.textViewTitle.text = item.manga?.title ?: getString(R.string.other_manga)
		binding.textViewSummary.text = item.time.format(context.resources)
		binding.imageViewBadge.imageTintList = ColorStateList.valueOf(KototoroColors.ofContent(context, item.manga))
		binding.root.isClickable = item.manga != null
	}
}
