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
		val isTrackingItem = item.metadataTrackingService != null
		binding.textViewAuthor.textAndVisible = if (isTrackingItem) {
			item.subtitle
		} else {
			item.manga.authors.joinToString(", ")
		}
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

		androidx.core.view.ViewCompat.setTransitionName(binding.imageViewCover, "cover_${item.manga.source.name}_${item.manga.url}")
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
		binding.textViewTags.textAndVisible = if (isTrackingItem) {
			item.supportingText
		} else {
			item.tags.joinToString(separator = ", ") { it.title ?: "" }.ifBlank { null }
		}
		binding.badge.labelText = item.scoreText.takeIf { isTrackingItem && !it.isNullOrBlank() }
		binding.badge.number = if (binding.badge.labelText == null) item.counter else 0
		binding.badge.isVisible = binding.badge.labelText != null || item.counter > 0
	}
}
