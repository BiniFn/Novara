package org.skepsun.kototoro.home.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.skepsun.kototoro.R
import org.skepsun.kototoro.databinding.ItemHomeCoverBinding
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.util.ifNullOrEmpty
import org.skepsun.kototoro.core.model.getLocale
import androidx.core.view.isVisible

data class HomeCoverItem(
	val content: Content? = null,
)

class HomeCoverAdapter(
	private val onContentClick: (Content, android.view.View) -> Unit,
) : ListAdapter<HomeCoverItem, HomeCoverAdapter.ViewHolder>(DiffCallback()) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val binding = ItemHomeCoverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ViewHolder(binding)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(getItem(position), onContentClick)
	}

	fun submitContents(contents: List<Content>) {
		submitList(contents.map { HomeCoverItem(it) })
	}

	class ViewHolder(
		private val binding: ItemHomeCoverBinding,
	) : RecyclerView.ViewHolder(binding.root) {

	fun bind(
			item: HomeCoverItem,
			onContentClick: (Content, android.view.View) -> Unit,
		) {
			val content = item.content
			val isEnabled = content != null
			binding.root.isEnabled = isEnabled
			binding.root.alpha = if (isEnabled) 1f else 0.35f
			binding.root.contentDescription = content?.title ?: binding.root.context.getString(R.string.history_is_empty)
			
			if (content != null) {
				androidx.core.view.ViewCompat.setTransitionName(binding.imageViewCover, "cover_${content.source.name}_${content.url}")
			}
			binding.imageViewCover.setImageAsync(
				content?.coverUrl,
				content,
			)
			binding.textViewTitle.text = content?.title ?: ""

			val context = binding.root.context
			val prefs = context.getSharedPreferences("${context.packageName}_preferences", android.content.Context.MODE_PRIVATE)
			val showSourceInfo = prefs.getBoolean("show_source_on_cards", false)
			if (showSourceInfo && content != null) {
				val locale = content.source.getLocale()
				val langText = locale?.language?.uppercase()?.takeIf { it.isNotBlank() }
				binding.textViewSourceLang.text = langText
				binding.textViewSourceLang.isVisible = langText != null
				binding.imageViewSourceIcon.setImageAsync(content.source)
				binding.sourceInfoLayout.isVisible = true
			} else {
				binding.sourceInfoLayout.isVisible = false
			}

			binding.root.setOnClickListener {
				content?.let { onContentClick(it, binding.imageViewCover) }
			}
		}
	}

	private class DiffCallback : DiffUtil.ItemCallback<HomeCoverItem>() {
		override fun areItemsTheSame(oldItem: HomeCoverItem, newItem: HomeCoverItem): Boolean {
			return oldItem.content?.id == newItem.content?.id
		}

		override fun areContentsTheSame(oldItem: HomeCoverItem, newItem: HomeCoverItem): Boolean {
			return oldItem == newItem
		}
	}
}
