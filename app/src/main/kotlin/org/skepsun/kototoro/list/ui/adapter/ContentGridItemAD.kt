package org.skepsun.kototoro.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.list.AdapterDelegateClickListenerAdapter
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.core.util.ext.setTooltipCompat
import org.skepsun.kototoro.databinding.ItemContentGridBinding
import org.skepsun.kototoro.list.ui.ListModelDiffCallback.Companion.PAYLOAD_PROGRESS_CHANGED
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.ContentGridModel
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.size.ItemSizeResolver

import androidx.core.content.ContextCompat
import org.skepsun.kototoro.core.util.ext.drawableStart
import org.skepsun.kototoro.core.model.getLocale

fun mangaGridItemAD(
	sizeResolver: ItemSizeResolver,
	clickListener: OnListItemClickListener<ContentListModel>,
) = adapterDelegateViewBinding<ContentGridModel, ListModel, ItemContentGridBinding>(
	{ inflater, parent -> ItemContentGridBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)
	sizeResolver.attachToView(itemView, binding.textViewTitle, binding.progressView)
	val iconPinned = ContextCompat.getDrawable(context, org.skepsun.kototoro.R.drawable.ic_pin_small)

	bind { payloads ->
		itemView.setTooltipCompat(item.getSummary(context))
		binding.textViewTitle.text = item.title
		binding.textViewTitle.drawableStart = if (item.isPinned) iconPinned else null
		binding.progressView.setProgress(item.progress, PAYLOAD_PROGRESS_CHANGED in payloads)
		with(binding.iconsView) {
			clearIcons()
			if (item.isSaved) addIcon(R.drawable.ic_storage)
			if (item.isFavorite) addIcon(R.drawable.ic_heart_outline)
			isVisible = iconsCount > 0
		}
		
		val prefs = context.getSharedPreferences("${context.packageName}_preferences", android.content.Context.MODE_PRIVATE)
		val showSourceInfo = prefs.getBoolean("show_source_on_cards", false)
		
		if (showSourceInfo) {
			val locale = item.manga.source.getLocale()
			val langText = locale?.language?.uppercase()?.takeIf { it.isNotBlank() }
			binding.textViewSourceLang.text = langText
			binding.textViewSourceLang.isVisible = langText != null
			binding.imageViewSourceIcon.setImageAsync(item.manga.source)
			binding.sourceInfoLayout.isVisible = true
		} else {
			binding.sourceInfoLayout.isVisible = false
		}
		
		binding.tagsLayout.isVisible = binding.iconsView.isVisible || binding.sourceInfoLayout.isVisible

		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
		binding.badge.number = item.counter
		binding.badge.isVisible = item.counter > 0
	}
}
