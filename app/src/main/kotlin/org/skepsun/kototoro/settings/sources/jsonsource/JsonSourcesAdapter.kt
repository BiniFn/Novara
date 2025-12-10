package org.skepsun.kototoro.settings.sources.jsonsource

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.databinding.ItemJsonSourceBinding

/**
 * Adapter for displaying JSON sources in a RecyclerView.
 */
class JsonSourcesAdapter(
	private val listener: JsonSourceListener,
) : ListAdapter<JsonSourceEntity, JsonSourcesAdapter.JsonSourceViewHolder>(DiffCallback()) {
	
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JsonSourceViewHolder {
		val binding = ItemJsonSourceBinding.inflate(
			LayoutInflater.from(parent.context),
			parent,
			false
		)
		return JsonSourceViewHolder(binding, listener)
	}
	
	override fun onBindViewHolder(holder: JsonSourceViewHolder, position: Int) {
		holder.bind(getItem(position))
	}
	
	/**
	 * ViewHolder for JSON source items.
	 */
	class JsonSourceViewHolder(
		private val binding: ItemJsonSourceBinding,
		private val listener: JsonSourceListener,
	) : RecyclerView.ViewHolder(binding.root) {
		
		fun bind(source: JsonSourceEntity) {
			binding.textViewName.text = source.name
			binding.textViewType.text = source.type.name
			binding.textViewUrl.text = extractBaseUrl(source.config)
			
			// Set enabled switch
			binding.switchEnabled.isChecked = source.enabled
			binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
				listener.onToggleEnabled(source.id, isChecked)
			}
			
			// Set test button
			binding.buttonTest.setOnClickListener {
				listener.onTestSource(source.id)
			}
			
			// Set delete button
			binding.buttonDelete.setOnClickListener {
				listener.onDeleteSource(source.id)
			}
		}
		
		/**
		 * Extracts the base URL from the JSON configuration.
		 * This is a simple implementation that looks for common URL fields.
		 */
		private fun extractBaseUrl(config: String): String {
			return try {
				// Try to extract bookSourceUrl from Legado config
				val urlPattern = """"bookSourceUrl"\s*:\s*"([^"]+)"""".toRegex()
				val match = urlPattern.find(config)
				match?.groupValues?.getOrNull(1) ?: "N/A"
			} catch (e: Exception) {
				"N/A"
			}
		}
	}
	
	/**
	 * DiffUtil callback for efficient list updates.
	 */
	private class DiffCallback : DiffUtil.ItemCallback<JsonSourceEntity>() {
		override fun areItemsTheSame(oldItem: JsonSourceEntity, newItem: JsonSourceEntity): Boolean {
			return oldItem.id == newItem.id
		}
		
		override fun areContentsTheSame(oldItem: JsonSourceEntity, newItem: JsonSourceEntity): Boolean {
			return oldItem == newItem
		}
	}
	
	/**
	 * Listener interface for JSON source item interactions.
	 */
	interface JsonSourceListener {
		fun onToggleEnabled(sourceId: String, enabled: Boolean)
		fun onTestSource(sourceId: String)
		fun onDeleteSource(sourceId: String)
	}
}
