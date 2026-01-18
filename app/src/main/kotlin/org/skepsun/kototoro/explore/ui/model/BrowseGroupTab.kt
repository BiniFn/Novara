package org.skepsun.kototoro.explore.ui.model

import androidx.annotation.StringRes
import androidx.annotation.DrawableRes
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.jsonsource.ContentGroup
import org.skepsun.kototoro.core.jsonsource.OriginGroup

/**
 * Represents a tab in the browse page for filtering sources by group.
 */
sealed class BrowseGroupTab(
	@StringRes val titleRes: Int,
	@DrawableRes val iconRes: Int,
	val id: String,
) {
	/**
	 * Show all sources without filtering
	 */
	object All : BrowseGroupTab(R.string.all, R.drawable.ic_explore_normal, "all")
	
	/**
	 * Show only manga sources
	 */
	object Manga : BrowseGroupTab(R.string.manga, R.drawable.ic_content_manga, "manga")
	
	/**
	 * Show only novel sources
	 */
	object Novel : BrowseGroupTab(R.string.novel, R.drawable.ic_content_novel, "novel")
	
	/**
	 * Show only video sources
	 */
	object Video : BrowseGroupTab(R.string.video, R.drawable.ic_content_video, "video")
	
	companion object {
		/**
		 * Get all available tabs in order
		 */
		fun getAllTabs(): List<BrowseGroupTab> = listOf(
			All,
			Manga,
			Novel,
			Video,
		)
		
		/**
		 * Find tab by ID
		 */
		fun fromId(id: String): BrowseGroupTab = when (id) {
			"all" -> All
			"manga" -> Manga
			"novel" -> Novel
			"video" -> Video
			// Legacy IDs now fall back to All
			"json", "mihon", "aniyomi" -> All
			else -> All
		}

		/**
		 * Get available tabs based on NSFW setting
		 */
		fun getAvailableTabs(isNsfwEnabled: Boolean): List<BrowseGroupTab> {
			return getAllTabs()
		}
	}
	
	/**
	 * Check if this tab matches a content group
	 */
	fun matchesContentGroup(group: ContentGroup): Boolean = when (this) {
		All -> true
		Manga -> group == ContentGroup.MANGA || group == ContentGroup.HENTAI_MANGA
		Novel -> group == ContentGroup.NOVEL || group == ContentGroup.HENTAI_NOVEL
		Video -> group == ContentGroup.VIDEO || group == ContentGroup.HENTAI_VIDEO
	}
	
	/**
	 * Check if this tab matches an origin group
	 */
	fun matchesOriginGroup(group: OriginGroup): Boolean = when (this) {
		All -> true
		else -> true // Content-based tabs don't filter by origin
	}
}
