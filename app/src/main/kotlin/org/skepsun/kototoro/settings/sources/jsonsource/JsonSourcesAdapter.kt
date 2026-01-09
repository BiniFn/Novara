package org.skepsun.kototoro.settings.sources.jsonsource

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.skepsun.kototoro.R
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
			binding.textViewName.text = source.name.ifBlank { source.id }
			binding.textViewUrl.text = extractBaseUrl(source.config)
			
			// Hide checkbox for simple adapter
			binding.checkboxSelect.visibility = View.GONE
			
			// Set enabled switch
			binding.switchEnabled.setOnCheckedChangeListener(null)
			binding.switchEnabled.isChecked = source.enabled
			binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
				listener.onToggleEnabled(source.id, isChecked)
			}
			
			// Set more button for popup menu
			binding.buttonMore.setOnClickListener { view ->
				showPopupMenu(view, source.id)
			}
			
			// Hide badges in simple adapter
			binding.layoutBadges.visibility = View.GONE
		}
		
		private fun showPopupMenu(anchor: View, sourceId: String) {
			val popup = PopupMenu(anchor.context, anchor)
			popup.menuInflater.inflate(R.menu.menu_source_item, popup.menu)
			popup.setOnMenuItemClickListener { menuItem ->
				when (menuItem.itemId) {
					R.id.menu_test -> {
						listener.onTestSource(sourceId)
						true
					}
					R.id.menu_delete -> {
						listener.onDeleteSource(sourceId)
						true
					}
					else -> false
				}
			}
			popup.show()
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
