package org.skepsun.kototoro.core.jsonsource

import org.skepsun.kototoro.core.model.MangaSourceInfo

/**
 * Data class representing information about a source group.
 * 
 * This class contains:
 * - The group type (content or origin)
 * - A display name for the group
 * - The list of sources in this group
 * - The count of sources
 * - Whether the group is currently collapsed in the UI
 * 
 * @property group The source group (Content or Origin)
 * @property name The display name for this group
 * @property sources The list of sources in this group
 * @property count The number of sources in this group
 * @property isCollapsed Whether this group is collapsed in the UI
 */
data class SourceGroupInfo(
	val group: SourceGroup,
	val name: String,
	val sources: List<MangaSourceInfo>,
	val count: Int = sources.size,
	val isCollapsed: Boolean = false,
) {
	/**
	 * Creates a copy of this group with the collapsed state toggled.
	 */
	fun toggleCollapsed(): SourceGroupInfo {
		return copy(isCollapsed = !isCollapsed)
	}
	
	/**
	 * Creates a copy of this group with a new collapsed state.
	 */
	fun withCollapsed(collapsed: Boolean): SourceGroupInfo {
		return copy(isCollapsed = collapsed)
	}
	
	/**
	 * Gets a display label for the group type.
	 */
	fun getGroupTypeLabel(): String {
		return when (group) {
			is SourceGroup.Content -> when (group.type) {
				ContentGroup.MANGA -> "漫画源"
				ContentGroup.NOVEL -> "小说源"
				ContentGroup.VIDEO -> "视频源"
				ContentGroup.HENTAI_MANGA -> "成人漫画源"
				ContentGroup.HENTAI_NOVEL -> "成人小说源"
				ContentGroup.HENTAI_VIDEO -> "成人视频源"
				ContentGroup.OTHER -> "其他源"
			}
			is SourceGroup.Origin -> when (group.type) {
				OriginGroup.NATIVE -> "原生源"
				OriginGroup.LEGADO_JSON -> "Legado 源"
				OriginGroup.TVBOX_JSON -> "TVBox 源"
				OriginGroup.JS_JSON -> "JavaScript 源"
				OriginGroup.EXTERNAL -> "外部源"
				OriginGroup.MIHON -> "Mihon 扩展"
				OriginGroup.ANIYOMI -> "Aniyomi 扩展"
			}
		}
	}
}

/**
 * Data class representing a complete list of grouped sources.
 * 
 * This class organizes sources into multiple groups and provides
 * methods to manipulate the grouping structure.
 * 
 * @property groups The list of all source groups
 */
data class GroupedSourceList(
	val groups: List<SourceGroupInfo>,
) {
	/**
	 * Gets the total count of all sources across all groups.
	 */
	val totalCount: Int
		get() = groups.sumOf { it.count }
	
	/**
	 * Toggles the collapsed state of a specific group.
	 * 
	 * @param groupIndex The index of the group to toggle
	 * @return A new GroupedSourceList with the updated group
	 */
	fun toggleGroup(groupIndex: Int): GroupedSourceList {
		if (groupIndex !in groups.indices) {
			return this
		}
		
		val updatedGroups = groups.toMutableList()
		updatedGroups[groupIndex] = updatedGroups[groupIndex].toggleCollapsed()
		
		return copy(groups = updatedGroups)
	}
	
	/**
	 * Toggles the collapsed state of a group by its SourceGroup.
	 * 
	 * @param group The SourceGroup to toggle
	 * @return A new GroupedSourceList with the updated group
	 */
	fun toggleGroup(group: SourceGroup): GroupedSourceList {
		val index = groups.indexOfFirst { it.group == group }
		return if (index >= 0) {
			toggleGroup(index)
		} else {
			this
		}
	}
	
	/**
	 * Collapses all groups.
	 * 
	 * @return A new GroupedSourceList with all groups collapsed
	 */
	fun collapseAll(): GroupedSourceList {
		return copy(groups = groups.map { it.withCollapsed(true) })
	}
	
	/**
	 * Expands all groups.
	 * 
	 * @return A new GroupedSourceList with all groups expanded
	 */
	fun expandAll(): GroupedSourceList {
		return copy(groups = groups.map { it.withCollapsed(false) })
	}
	
	/**
	 * Filters groups to only include those with sources.
	 * 
	 * @return A new GroupedSourceList with only non-empty groups
	 */
	fun filterNonEmpty(): GroupedSourceList {
		return copy(groups = groups.filter { it.count > 0 })
	}
	
	/**
	 * Gets all sources as a flat list (ignoring grouping).
	 * 
	 * @return List of all MangaSourceInfo across all groups
	 */
	fun getAllSources(): List<MangaSourceInfo> {
		return groups.flatMap { it.sources }
	}
	
	companion object {
		/**
		 * Creates an empty GroupedSourceList.
		 */
		fun empty(): GroupedSourceList {
			return GroupedSourceList(emptyList())
		}
		
		/**
		 * Creates a GroupedSourceList from a list of sources by grouping them.
		 * 
		 * @param sources The list of sources to group
		 * @param groupBy The grouping strategy (content or origin)
		 * @param sourceGroupManager The manager to use for grouping
		 * @return A new GroupedSourceList with sources organized into groups
		 */
		fun fromSources(
			sources: List<MangaSourceInfo>,
			groupBy: GroupingStrategy,
			sourceGroupManager: SourceGroupManager,
		): GroupedSourceList {
			val groups = when (groupBy) {
				GroupingStrategy.BY_CONTENT -> {
					ContentGroup.entries.map { contentGroup ->
						val groupSources = sources.filter { sourceInfo ->
							sourceGroupManager.getContentGroup(sourceInfo.mangaSource) == contentGroup
						}
						SourceGroupInfo(
							group = SourceGroup.Content(contentGroup),
							name = contentGroup.name,
							sources = groupSources,
						)
					}
				}
				GroupingStrategy.BY_ORIGIN -> {
					OriginGroup.entries.map { originGroup ->
						val groupSources = sources.filter { sourceInfo ->
							sourceGroupManager.getOriginGroup(sourceInfo.mangaSource) == originGroup
						}
						SourceGroupInfo(
							group = SourceGroup.Origin(originGroup),
							name = originGroup.name,
							sources = groupSources,
						)
					}
				}
			}
			
			return GroupedSourceList(groups)
		}
	}
}

/**
 * Enum representing different strategies for grouping sources.
 */
enum class GroupingStrategy {
	/**
	 * Group sources by content type (manga, novel, video)
	 */
	BY_CONTENT,
	
	/**
	 * Group sources by origin type (native, JSON Legado, JSON TVBox)
	 */
	BY_ORIGIN,
}
