package org.skepsun.kototoro.core.jsonsource

import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.ContentParserSource
import org.skepsun.kototoro.parsers.model.ContentSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource

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
	private val json: Json,
) {
	
	/**
	 * Gets the content group for a source based on its content type.
	 * 
	 * @param source The manga source
	 * @return The ContentGroup enum value
	 */
	fun getContentGroup(source: ContentSource): ContentGroup {
		val isNsfw = source.isNsfw()
		
		// Priority 1: Check native ContentParserSource content type
		if (source is ContentParserSource) {
			return when (source.contentType) {
				ContentType.NOVEL, ContentType.HENTAI_NOVEL -> if (isNsfw) ContentGroup.HENTAI_NOVEL else ContentGroup.NOVEL
				ContentType.VIDEO, ContentType.HENTAI_VIDEO -> if (isNsfw) ContentGroup.HENTAI_VIDEO else ContentGroup.VIDEO
				else -> if (isNsfw) ContentGroup.HENTAI_MANGA else ContentGroup.MANGA
			}
		}
		
		// Priority 2: Check for known source wrappers
		if (source is org.skepsun.kototoro.mihon.model.MihonMangaSource) {
			return if (isNsfw) ContentGroup.HENTAI_MANGA else ContentGroup.MANGA
		}
		
		if (source is org.skepsun.kototoro.aniyomi.model.AniyomiAnimeSource) {
			return if (isNsfw) ContentGroup.HENTAI_VIDEO else ContentGroup.VIDEO
		}
		
		if (source is org.skepsun.kototoro.ireader.model.IReaderMangaSource) {
			return if (isNsfw) ContentGroup.HENTAI_MANGA else ContentGroup.MANGA
		}
		
		if (source is org.skepsun.kototoro.core.jsonsource.JsonContentSource) {
			return try {
				when (source.entity.type) {
					org.skepsun.kototoro.core.db.entity.JsonSourceType.LEGADO -> {
						val legacyConfig = runCatching { 
							json.decodeFromString<LegadoBookSource>(source.entity.config)
						}.getOrNull()
						
						if (legacyConfig?.bookSourceType == 2) {
							if (isNsfw) ContentGroup.HENTAI_MANGA else ContentGroup.MANGA
						} else {
							if (isNsfw) ContentGroup.HENTAI_NOVEL else ContentGroup.NOVEL
						}
					}
					org.skepsun.kototoro.core.db.entity.JsonSourceType.TVBOX -> {
						if (isNsfw) ContentGroup.HENTAI_VIDEO else ContentGroup.VIDEO
					}
					org.skepsun.kototoro.core.db.entity.JsonSourceType.JS -> {
						if (isNsfw) ContentGroup.HENTAI_MANGA else ContentGroup.MANGA
					}
				}
			} catch (e: Exception) {
				android.util.Log.e("SourceGroupManager", "Failed to parse JSON source config for ${source.name}", e)
				if (isNsfw) ContentGroup.HENTAI_MANGA else ContentGroup.MANGA
			}
		}

		// Priority 3: Prefix-based fallback for anonymous sources
		val name = source.name
		return when {
			name.startsWith("ANIYOMI_") -> if (isNsfw) ContentGroup.HENTAI_VIDEO else ContentGroup.VIDEO
			name.startsWith("JSON_TVBOX_") -> if (isNsfw) ContentGroup.HENTAI_VIDEO else ContentGroup.VIDEO
			name.startsWith("JSON_LEGADO_M_") -> if (isNsfw) ContentGroup.HENTAI_MANGA else ContentGroup.MANGA
			name.startsWith("JSON_LEGADO_") -> if (isNsfw) ContentGroup.HENTAI_NOVEL else ContentGroup.NOVEL
			else -> if (isNsfw) ContentGroup.HENTAI_MANGA else ContentGroup.MANGA
		}
	}
	
	/**
	 * Gets the origin group for a source based on its identifier.
	 * 
	 * @param source The manga source
	 * @return The OriginGroup enum value
	 */
	fun getOriginGroup(source: ContentSource): OriginGroup {
		val sourceType = sourceTypeIdentifier.getSourceType(source.name)
		return when (sourceType) {
			SourceType.NATIVE -> OriginGroup.NATIVE
			SourceType.JSON_LEGADO -> OriginGroup.LEGADO_JSON
			SourceType.JSON_TVBOX -> OriginGroup.TVBOX_JSON
			SourceType.JSON_JS -> OriginGroup.JS_JSON
			SourceType.EXTERNAL -> OriginGroup.EXTERNAL
			SourceType.MIHON -> OriginGroup.MIHON
			SourceType.ANIYOMI -> OriginGroup.ANIYOMI
			SourceType.IREADER -> OriginGroup.IREADER
		}
	}
	
	/**
	 * Gets sources filtered by a specific group.
	 * 
	 * @param sources The list of all sources to filter
	 * @param group The group to filter by
	 * @return List of sources in the specified group
	 */
	fun getSourcesByGroup(sources: List<ContentSource>, group: SourceGroup): List<ContentSource> {
		return when (group) {
			is SourceGroup.Content -> sources.filter { getContentGroup(it) == group.type }
			is SourceGroup.Origin -> sources.filter { getOriginGroup(it) == group.type }
			is SourceGroup.TvBoxRepository -> sources.filter { source ->
				val jsonSource = source as? org.skepsun.kototoro.core.jsonsource.JsonContentSource
					?: return@filter false
				if (jsonSource.entity.type != org.skepsun.kototoro.core.db.entity.JsonSourceType.TVBOX) {
					return@filter group.locator == null
				}
				extractTvBoxSourceLocator(jsonSource.entity.config) == group.locator
			}
		}
	}
	
	/**
	 * Gets counts of sources in each group.
	 * 
	 * @param sources The list of all sources to count
	 * @return Map of SourceGroup to count
	 */
	fun getGroupCounts(sources: List<ContentSource>): Map<SourceGroup, Int> {
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
	 * Content, manhwa, manhua, comics, and related visual content
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
	 * Hentai manga
	 */
	HENTAI_MANGA,

	/**
	 * Hentai novel
	 */
	HENTAI_NOVEL,

	/**
	 * Hentai video
	 */
	HENTAI_VIDEO,
	
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
	MIHON,
	
	/**
	 * Aniyomi extension sources
	 */
	ANIYOMI,

	/**
	 * IReader extension sources
	 */
	IREADER
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

	/**
	 * Group by imported TVBox repository locator.
	 * When locator is null, this group represents non-TVBox JSON sources.
	 */
	data class TvBoxRepository(val locator: String?, val title: String) : SourceGroup()
}

private fun extractTvBoxSourceLocator(rawConfig: String): String? {
	return runCatching {
		org.skepsun.kototoro.core.model.jsonsource.TVBoxStoredConfig.parse(rawConfig)
			.meta
			.sourceLocator
			?.trim()
			?.ifBlank { null }
	}.getOrNull()
}
