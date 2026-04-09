package org.skepsun.kototoro.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.list.AdapterDelegateClickListenerAdapter
import org.skepsun.kototoro.core.util.ext.textAndVisible
import org.skepsun.kototoro.databinding.ItemContentListDetailsBinding
import org.skepsun.kototoro.list.ui.ListModelDiffCallback
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.list.ui.model.ContentDetailedListModel

import androidx.core.content.ContextCompat
import org.skepsun.kototoro.core.util.ext.drawableStart
import org.skepsun.kototoro.core.model.getLocale

fun mangaListDetailedItemAD(
	clickListener: ContentDetailsClickListener,
) = adapterDelegateViewBinding<ContentDetailedListModel, ListModel, ItemContentListDetailsBinding>(
	{ inflater, parent -> ItemContentListDetailsBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener)
		.attach(itemView)
	val iconPinned = ContextCompat.getDrawable(context, org.skepsun.kototoro.R.drawable.ic_pin_small)

	bind { payloads ->
		binding.textViewTitle.text = item.title
		binding.textViewTitle.drawableStart = if (item.isPinned) iconPinned else null
		binding.textViewAuthor.textAndVisible = item.manga.authors.joinToString(", ")
		binding.progressView.setProgress(
			value = item.progress,
			animate = ListModelDiffCallback.PAYLOAD_PROGRESS_CHANGED in payloads,
		)
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
		binding.textViewTags.text = item.tags.joinToString(separator = ", ") { it.title ?: "" }
		binding.badge.number = item.counter
		binding.badge.isVisible = item.counter > 0
	}
}
