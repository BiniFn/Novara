package org.skepsun.kototoro.core.jsonsource

import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.MangaParserSource
import org.skepsun.kototoro.parsers.model.MangaSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages source grouping and categorization.
 * 
 * This class provides methods to:
 * - Determine content type groups (manga, novel, video)
 * - Determine origin type groups (native, JSON Legado, JSON TVBox)
 * - Query sources by group
 * - Get statistics about source groups
 */
@Singleton
class SourceGroupManager @Inject constructor(
	private val sourceTypeIdentifier: SourceTypeIdentifier,
	private val jsonSourceManager: JsonSourceManager,
) {
	
	/**
	 * Gets the content group for a source based on its content type.
	 * 
	 * @param source The manga source
	 * @return The ContentGroup enum value
	 */
	fun getContentGroup(source: MangaSource): ContentGroup {
		// For JSON sources, parse the config to determine content type
		if (source is JsonMangaSource) {
			return getContentGroupFromJsonSource(source)
		}
		
		// For native sources, check the ContentType
		if (source is MangaParserSource) {
			return when (source.contentType) {
				ContentType.MANGA,
				ContentType.MANHWA,
				ContentType.MANHUA,
				ContentType.HENTAI_MANGA,
				ContentType.COMICS,
				ContentType.ONE_SHOT,
				ContentType.DOUJINSHI,
				ContentType.IMAGE_SET,
				ContentType.ARTIST_CG,
				ContentType.GAME_CG -> ContentGroup.MANGA
				
				ContentType.NOVEL,
				ContentType.HENTAI_NOVEL -> ContentGroup.NOVEL
				
				ContentType.VIDEO,
				ContentType.HENTAI_VIDEO -> ContentGroup.VIDEO
				
				ContentType.OTHER -> ContentGroup.OTHER
			}
		}
		
		// For Mihon sources, most are manga
		if (source is org.skepsun.kototoro.mihon.model.MihonMangaSource) {
			return ContentGroup.MANGA
		}
		
		return ContentGroup.OTHER
	}
	
	/**
	 * Gets the content group from a JSON source by parsing its configuration.
	 * 
	 * @param source The JSON manga source
	 * @return The ContentGroup enum value
	 */
	private fun getContentGroupFromJsonSource(source: JsonMangaSource): ContentGroup {
		return try {
			when (source.entity.type) {
				org.skepsun.kototoro.core.db.entity.JsonSourceType.LEGADO -> {
					// Per convention: all Legado JSON sources are treated as novel sources
					ContentGroup.NOVEL
				}
				org.skepsun.kototoro.core.db.entity.JsonSourceType.TVBOX -> {
					// TVBox sources are typically video
					ContentGroup.VIDEO
				}
				org.skepsun.kototoro.core.db.entity.JsonSourceType.JS -> {
					// JS sources follow Venera comic model
					ContentGroup.MANGA
				}
			}
		} catch (e: Exception) {
			android.util.Log.e("SourceGroupManager", "Failed to parse JSON source config for ${source.name}", e)
			ContentGroup.OTHER
		}
	}
	
	/**
	 * Gets the origin group for a source based on its identifier.
	 * 
	 * @param source The manga source
	 * @return The OriginGroup enum value
	 */
	fun getOriginGroup(source: MangaSource): OriginGroup {
		val sourceType = sourceTypeIdentifier.getSourceType(source.name)
		return when (sourceType) {
			SourceType.NATIVE -> OriginGroup.NATIVE
			SourceType.JSON_LEGADO -> OriginGroup.LEGADO_JSON
			SourceType.JSON_TVBOX -> OriginGroup.TVBOX_JSON
			SourceType.JSON_JS -> OriginGroup.JS_JSON
			SourceType.EXTERNAL -> OriginGroup.EXTERNAL
			SourceType.MIHON -> OriginGroup.MIHON
		}
	}
	
	/**
	 * Gets sources filtered by a specific group.
	 * 
	 * @param sources The list of all sources to filter
	 * @param group The group to filter by
	 * @return List of sources in the specified group
	 */
	fun getSourcesByGroup(sources: List<MangaSource>, group: SourceGroup): List<MangaSource> {
		return when (group) {
			is SourceGroup.Content -> sources.filter { getContentGroup(it) == group.type }
			is SourceGroup.Origin -> sources.filter { getOriginGroup(it) == group.type }
		}
	}
	
	/**
	 * Gets counts of sources in each group.
	 * 
	 * @param sources The list of all sources to count
	 * @return Map of SourceGroup to count
	 */
	fun getGroupCounts(sources: List<MangaSource>): Map<SourceGroup, Int> {
		val counts = mutableMapOf<SourceGroup, Int>()
		
		// Count by content groups
		for (contentGroup in ContentGroup.entries) {
			val group = SourceGroup.Content(contentGroup)
			counts[group] = sources.count { getContentGroup(it) == contentGroup }
		}
		
		// Count by origin groups
		for (originGroup in OriginGroup.entries) {
			val group = SourceGroup.Origin(originGroup)
			counts[group] = sources.count { getOriginGroup(it) == originGroup }
		}
		
		return counts
	}
}

/**
 * Enum representing content type groups for sources.
 */
enum class ContentGroup {
	/**
	 * Manga, manhwa, manhua, comics, and related visual content
	 */
	MANGA,
	
	/**
	 * Novel and text-based content
	 */
	NOVEL,
	
	/**
	 * Video content
	 */
	VIDEO,
	
	/**
	 * Other or unclassified content
	 */
	OTHER
}

/**
 * Enum representing origin type groups for sources.
 */
enum class OriginGroup {
	/**
	 * Native Kotlin sources compiled into the application
	 */
	NATIVE,
	
	/**
	 * JSON sources using Legado format
	 */
	LEGADO_JSON,
	
	/**
	 * JSON sources using TVBox format
	 */
	TVBOX_JSON,
	
	/**
	 * JavaScript sources (Venera style)
	 */
	JS_JSON,
	
	/**
	 * External sources
	 */
	EXTERNAL,
	
	/**
	 * Mihon extension sources
	 */
	MIHON
}

/**
 * Sealed class representing different types of source groups.
 */
sealed class SourceGroup {
	/**
	 * Group by content type (manga, novel, video)
	 */
	data class Content(val type: ContentGroup) : SourceGroup()
	
	/**
	 * Group by origin type (native, JSON Legado, JSON TVBox)
	 */
	data class Origin(val type: OriginGroup) : SourceGroup()
}
