package org.skepsun.kototoro.settings.sources.jsonsource

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.core.jsonsource.SourceGroup
import org.skepsun.kototoro.core.jsonsource.SourceGroupInfo
import org.skepsun.kototoro.core.model.MangaSourceInfo
import org.skepsun.kototoro.databinding.ItemJsonSourceBinding
import org.skepsun.kototoro.databinding.ItemSourceGroupHeaderBinding

/**
 * Adapter for displaying grouped JSON sources with collapsible headers.
 * 
 * This adapter displays:
 * - Group headers with source counts and expand/collapse icons
 * - Source items within each group
 */
class GroupedJsonSourcesAdapter(
	private val listener: GroupedSourceListener,
) : ListAdapter<GroupedSourceItem, RecyclerView.ViewHolder>(DiffCallback()) {
	
	// Validation state map: sourceId -> true/false
	var validationStates: Map<String, Boolean?> = emptyMap()
	// Selection state
	var selectedIds: Set<String> = emptySet()
	
	companion object {
		private const val VIEW_TYPE_HEADER = 0
		private const val VIEW_TYPE_SOURCE = 1
	}
	
	override fun getItemViewType(position: Int): Int {
		return when (getItem(position)) {
			is GroupedSourceItem.Header -> VIEW_TYPE_HEADER
			is GroupedSourceItem.Source -> VIEW_TYPE_SOURCE
		}
	}
	
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		return when (viewType) {
			VIEW_TYPE_HEADER -> {
				val binding = ItemSourceGroupHeaderBinding.inflate(
					LayoutInflater.from(parent.context),
					parent,
					false
				)
				GroupHeaderViewHolder(binding, listener)
			}
			VIEW_TYPE_SOURCE -> {
				val binding = ItemJsonSourceBinding.inflate(
					LayoutInflater.from(parent.context),
					parent,
					false
				)
				SourceViewHolder(binding, listener)
			}
			else -> throw IllegalArgumentException("Unknown view type: $viewType")
		}
	}
	
	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (val item = getItem(position)) {
			is GroupedSourceItem.Header -> (holder as GroupHeaderViewHolder).bind(item)
			is GroupedSourceItem.Source -> (holder as SourceViewHolder).bind(item, validationStates, selectedIds)
		}
	}
	
	/**
	 * ViewHolder for group headers.
	 */
	class GroupHeaderViewHolder(
		private val binding: ItemSourceGroupHeaderBinding,
		private val listener: GroupedSourceListener,
	) : RecyclerView.ViewHolder(binding.root) {
		
		fun bind(item: GroupedSourceItem.Header) {
			val groupInfo = item.groupInfo
			
			// Set title
			binding.textViewTitle.text = groupInfo.getGroupTypeLabel()
			
			// Set count
			binding.textViewCount.text = "(${groupInfo.count})"
			
			// Set expand/collapse icon rotation
			val rotation = if (groupInfo.isCollapsed) 0f else 180f
			binding.imageViewExpand.rotation = rotation
			
			// Set click listener
			binding.root.setOnClickListener {
				listener.onGroupHeaderClick(groupInfo.group)
			}
		}
	}
	
	/**
	 * ViewHolder for source items.
	 */
	class SourceViewHolder(
		private val binding: ItemJsonSourceBinding,
		private val listener: GroupedSourceListener,
	) : RecyclerView.ViewHolder(binding.root) {
		
		fun bind(item: GroupedSourceItem.Source, validationStates: Map<String, Boolean?>, selectedIds: Set<String>) {
			val sourceInfo = item.sourceInfo
			
			// Set icon based on content type
			binding.textViewIcon.text = getContentTypeIcon(sourceInfo)
			
			// Set source name (use the name property from MangaSource)
			val mangaSource = sourceInfo.mangaSource
			binding.textViewName.text = when (mangaSource) {
				is org.skepsun.kototoro.core.jsonsource.JsonMangaSource -> mangaSource.displayName.ifBlank { mangaSource.name }
				else -> sourceInfo.name
			}
			
			// Set type label with content type
			val contentTypeLabel = getContentTypeLabel(sourceInfo)
			val originTypeLabel = getOriginTypeLabel(sourceInfo.name)
			binding.textViewType.text = "$contentTypeLabel · $originTypeLabel"
			
			if (mangaSource is org.skepsun.kototoro.core.jsonsource.JsonMangaSource) {
				// Selection
				binding.checkboxSelect.visibility = View.VISIBLE
				binding.checkboxSelect.isChecked = selectedIds.contains(mangaSource.entity.id)
				binding.checkboxSelect.setOnCheckedChangeListener { _, isChecked ->
					listener.onSelectSource(mangaSource.entity.id, isChecked)
				}
				
				// Parse config to extract base URL and explore rule status (Legado only)
				val legado = if (mangaSource.entity.type == org.skepsun.kototoro.core.db.entity.JsonSourceType.LEGADO) {
					runCatching {
						kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
							.decodeFromString<org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource>(mangaSource.entity.config)
					}.getOrNull()
				} else {
					null
				}
				binding.textViewUrl.text = legado?.bookSourceUrl ?: mangaSource.entity.id
				
				// Enabled switch/buttons visible
				binding.switchEnabled.visibility = View.VISIBLE
				binding.switchEnabled.isChecked = sourceInfo.isEnabled
				binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
					listener.onToggleEnabled(mangaSource.entity.id, isChecked)
				}
				binding.buttonTest.visibility = View.VISIBLE
				binding.buttonDelete.visibility = View.VISIBLE
				binding.buttonTest.setOnClickListener { listener.onTestSource(mangaSource.entity.id) }
				binding.buttonDelete.setOnClickListener { listener.onDeleteSource(mangaSource.entity.id) }
				
				// Badges
				var badgesVisible = false
				val hasExplore = if (mangaSource.entity.type == org.skepsun.kototoro.core.db.entity.JsonSourceType.LEGADO) {
					!(legado?.ruleExplore?.bookList.isNullOrBlank())
				} else {
					true
				}
				if (!hasExplore) {
					binding.chipSearchOnly.visibility = View.VISIBLE
					binding.chipSearchOnly.text = binding.root.context.getString(R.string.badge_search_only)
					badgesVisible = true
				} else {
					binding.chipSearchOnly.visibility = View.GONE
				}
				val valid = validationStates[mangaSource.entity.id]
				when (valid) {
					true -> {
						binding.chipValid.visibility = View.VISIBLE
						binding.chipValid.text = binding.root.context.getString(R.string.badge_valid)
						binding.chipInvalid.visibility = View.GONE
						badgesVisible = true
					}
					false -> {
						binding.chipValid.visibility = View.GONE
						binding.chipInvalid.visibility = View.VISIBLE
						binding.chipInvalid.text = binding.root.context.getString(R.string.badge_invalid)
						badgesVisible = true
					}
					else -> {
						binding.chipValid.visibility = View.GONE
						binding.chipInvalid.visibility = View.GONE
					}
				}
				binding.layoutBadges.visibility = if (badgesVisible) View.VISIBLE else View.GONE
			} else {
				// Native sources: hide badges and controls
				binding.textViewUrl.text = sourceInfo.name
				binding.checkboxSelect.visibility = View.GONE
				binding.switchEnabled.visibility = View.GONE
				binding.buttonTest.visibility = View.GONE
				binding.buttonDelete.visibility = View.GONE
				binding.layoutBadges.visibility = View.GONE
				binding.chipSearchOnly.visibility = View.GONE
				binding.chipValid.visibility = View.GONE
				binding.chipInvalid.visibility = View.GONE
			}
		}
		
		private fun getContentTypeLabel(sourceInfo: MangaSourceInfo): String {
			// Check if it's a MangaParserSource (which has contentType)
			val source = sourceInfo.mangaSource
			if (source is org.skepsun.kototoro.parsers.model.MangaParserSource) {
				return when (source.contentType) {
					org.skepsun.kototoro.parsers.model.ContentType.MANGA,
					org.skepsun.kototoro.parsers.model.ContentType.MANHWA,
					org.skepsun.kototoro.parsers.model.ContentType.MANHUA,
					org.skepsun.kototoro.parsers.model.ContentType.HENTAI_MANGA,
					org.skepsun.kototoro.parsers.model.ContentType.COMICS,
					org.skepsun.kototoro.parsers.model.ContentType.ONE_SHOT,
					org.skepsun.kototoro.parsers.model.ContentType.DOUJINSHI,
					org.skepsun.kototoro.parsers.model.ContentType.IMAGE_SET,
					org.skepsun.kototoro.parsers.model.ContentType.ARTIST_CG,
					org.skepsun.kototoro.parsers.model.ContentType.GAME_CG -> "漫画"
					
					org.skepsun.kototoro.parsers.model.ContentType.NOVEL,
					org.skepsun.kototoro.parsers.model.ContentType.HENTAI_NOVEL -> "小说"
					org.skepsun.kototoro.parsers.model.ContentType.VIDEO,
					org.skepsun.kototoro.parsers.model.ContentType.HENTAI_VIDEO -> "视频"
					org.skepsun.kototoro.parsers.model.ContentType.OTHER -> "其他"
				}
			}
			return "其他"
		}
		
		private fun getOriginTypeLabel(sourceName: String): String {
			return when {
				sourceName.startsWith("JSON_LEGADO") -> "Legado"
				sourceName.startsWith("JSON_TVBOX") -> "TVBox"
				sourceName.startsWith("JSON_") -> "JSON"
				else -> "原生"
			}
		}
		
		private fun getContentTypeIcon(sourceInfo: MangaSourceInfo): String {
			// Check if it's a MangaParserSource (which has contentType)
			val source = sourceInfo.mangaSource
			if (source is org.skepsun.kototoro.parsers.model.MangaParserSource) {
				return when (source.contentType) {
					org.skepsun.kototoro.parsers.model.ContentType.MANGA,
					org.skepsun.kototoro.parsers.model.ContentType.MANHWA,
					org.skepsun.kototoro.parsers.model.ContentType.MANHUA,
					org.skepsun.kototoro.parsers.model.ContentType.HENTAI_MANGA,
					org.skepsun.kototoro.parsers.model.ContentType.COMICS,
					org.skepsun.kototoro.parsers.model.ContentType.ONE_SHOT,
					org.skepsun.kototoro.parsers.model.ContentType.DOUJINSHI,
					org.skepsun.kototoro.parsers.model.ContentType.IMAGE_SET,
					org.skepsun.kototoro.parsers.model.ContentType.ARTIST_CG,
					org.skepsun.kototoro.parsers.model.ContentType.GAME_CG -> "📚"
					
					org.skepsun.kototoro.parsers.model.ContentType.NOVEL,
					org.skepsun.kototoro.parsers.model.ContentType.HENTAI_NOVEL -> "📖"
					org.skepsun.kototoro.parsers.model.ContentType.VIDEO,
					org.skepsun.kototoro.parsers.model.ContentType.HENTAI_VIDEO -> "🎬"
					org.skepsun.kototoro.parsers.model.ContentType.OTHER -> "📄"
				}
			}
			
			// For JSON sources, use JSON icon
			return if (sourceInfo.name.startsWith("JSON_")) "📄" else "📚"
		}
	}
	
	/**
	 * DiffUtil callback for efficient list updates.
	 */
	private class DiffCallback : DiffUtil.ItemCallback<GroupedSourceItem>() {
		override fun areItemsTheSame(
			oldItem: GroupedSourceItem,
			newItem: GroupedSourceItem
		): Boolean {
			return when {
				oldItem is GroupedSourceItem.Header && newItem is GroupedSourceItem.Header ->
					oldItem.groupInfo.group == newItem.groupInfo.group
				oldItem is GroupedSourceItem.Source && newItem is GroupedSourceItem.Source ->
					oldItem.sourceInfo.name == newItem.sourceInfo.name
				else -> false
			}
		}
		
		override fun areContentsTheSame(
			oldItem: GroupedSourceItem,
			newItem: GroupedSourceItem
		): Boolean {
			return oldItem == newItem
		}
		
		override fun getChangePayload(
			oldItem: GroupedSourceItem,
			newItem: GroupedSourceItem
		): Any? {
			if (oldItem is GroupedSourceItem.Header && newItem is GroupedSourceItem.Header) {
				if (oldItem.groupInfo.isCollapsed != newItem.groupInfo.isCollapsed) {
					return newItem.groupInfo.isCollapsed
				}
			}
			return null
		}
	}
	
	/**
	 * Listener interface for grouped source interactions.
	 */
	interface GroupedSourceListener {
		fun onGroupHeaderClick(group: SourceGroup)
		fun onToggleEnabled(sourceId: String, enabled: Boolean)
		fun onTestSource(sourceId: String)
		fun onDeleteSource(sourceId: String)
		fun onSelectSource(sourceId: String, selected: Boolean)
	}
}

/**
 * Sealed class representing items in the grouped list.
 */
sealed class GroupedSourceItem {
	/**
	 * A group header item.
	 */
	data class Header(val groupInfo: SourceGroupInfo) : GroupedSourceItem()
	
	/**
	 * A source item.
	 */
	data class Source(val sourceInfo: MangaSourceInfo) : GroupedSourceItem()
}

/**
 * Extension function to convert GroupedSourceList to a flat list of items.
 */
fun org.skepsun.kototoro.core.jsonsource.GroupedSourceList.toFlatList(): List<GroupedSourceItem> {
	val items = mutableListOf<GroupedSourceItem>()
	
	for (groupInfo in groups) {
		// Add header
		items.add(GroupedSourceItem.Header(groupInfo))
		
		// Add sources if group is not collapsed
		if (!groupInfo.isCollapsed) {
			items.addAll(groupInfo.sources.map { GroupedSourceItem.Source(it) })
		}
	}
	
	return items
}
