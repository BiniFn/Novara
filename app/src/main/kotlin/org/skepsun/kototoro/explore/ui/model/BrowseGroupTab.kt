package org.skepsun.kototoro.explore.ui.model

import androidx.annotation.StringRes
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.jsonsource.ContentGroup
import org.skepsun.kototoro.core.jsonsource.OriginGroup

/**
 * Represents a tab in the browse page for filtering sources by group.
 */
sealed class BrowseGroupTab(
	@StringRes val titleRes: Int,
	val id: String,
) {
	/**
	 * Show all sources without filtering
	 */
	object All : BrowseGroupTab(R.string.all, "all")
	
	/**
	 * Show only manga sources
	 */
	object Manga : BrowseGroupTab(R.string.manga, "manga")
	
	/**
	 * Show only novel sources
	 */
	object Novel : BrowseGroupTab(R.string.novel, "novel")
	
	/**
	 * Show only video sources
	 */
	object Video : BrowseGroupTab(R.string.video, "video")
	
	/**
	 * Show only JSON sources (Legado + TVBox)
	 */
	object JsonSources : BrowseGroupTab(R.string.json_sources, "json")
	
	companion object {
		/**
		 * Get all available tabs in order
		 */
		fun getAllTabs(): List<BrowseGroupTab> = listOf(
			All,
			Manga,
			Novel,
			Video,
			JsonSources,
		)
		
		/**
		 * Find tab by ID
		 */
		fun fromId(id: String): BrowseGroupTab = when (id) {
			"all" -> All
			"manga" -> Manga
			"novel" -> Novel
			"video" -> Video
			"json" -> JsonSources
			else -> All
		}
	}
	
	/**
	 * Check if this tab matches a content group
	 */
	fun matchesContentGroup(group: ContentGroup): Boolean = when (this) {
		All -> true
		Manga -> group == ContentGroup.MANGA
		Novel -> group == ContentGroup.NOVEL
		Video -> group == ContentGroup.VIDEO
		JsonSources -> true // JSON sources accept all content types, filtered by origin only
	}
	
	/**
	 * Check if this tab matches an origin group
	 */
	fun matchesOriginGroup(group: OriginGroup): Boolean = when (this) {
		All -> true
		JsonSources -> group == OriginGroup.LEGADO_JSON || group == OriginGroup.TVBOX_JSON
		else -> true // Content-based tabs don't filter by origin
	}
}
