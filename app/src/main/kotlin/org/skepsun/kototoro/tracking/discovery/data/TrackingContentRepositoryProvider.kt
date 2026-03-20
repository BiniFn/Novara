package org.skepsun.kototoro.tracking.discovery.data

import kotlinx.coroutines.flow.Flow
import org.skepsun.kototoro.core.parser.ContentRepository
import org.skepsun.kototoro.core.parser.ContentRepositoryProvider
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentListFilter
import org.skepsun.kototoro.parsers.model.ContentListFilterCapabilities
import org.skepsun.kototoro.parsers.model.ContentListFilterOptions
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.parsers.model.ContentTagGroup
import org.skepsun.kototoro.parsers.model.SortOrder
import org.skepsun.kototoro.scrobbling.bangumi.data.BangumiRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingContentRepositoryProvider @Inject constructor(
	private val bangumiRepository: BangumiRepository,
) : ContentRepositoryProvider {

	override fun supports(source: ContentSource): Boolean {
		return source.name.startsWith("TRACKING_")
	}

	override fun create(source: ContentSource): ContentRepository? {
		if (!supports(source)) return null
		return TrackingContentRepository(source, bangumiRepository)
	}
}

class TrackingContentRepository(
	override val source: ContentSource,
	private val bangumiRepository: BangumiRepository,
) : ContentRepository {

	override val sortOrders: Set<SortOrder> = setOf(
		SortOrder.POPULARITY, // Rank
		SortOrder.UPDATED,    // Date
		SortOrder.ALPHABETICAL// Title
	)
	
	override var defaultSortOrder: SortOrder = SortOrder.POPULARITY

	override val filterCapabilities = ContentListFilterCapabilities(
		isMultipleTagsSupported = false,
		isSearchWithFiltersSupported = true,
		isAuthorSearchSupported = false,
	)

	override suspend fun getFilterOptions(): ContentListFilterOptions {
		val category = source.name.substringAfter("TRACKING_BANGUMI_").lowercase()
		return when (category) {
			"anime" -> {
				val tagGroups = listOf(
					ContentTagGroup("类型", setOf(
						ContentTag("全部", "", source),
						ContentTag("TV", "/tv", source),
						ContentTag("WEB", "/web", source),
						ContentTag("OVA", "/ova", source),
						ContentTag("剧场版", "/movie", source),
						ContentTag("其他", "/misc", source)
					))
				)
				ContentListFilterOptions(tagGroups = tagGroups)
			}
			"book" -> {
				val tagGroups = listOf(
					ContentTagGroup("类型", setOf(
						ContentTag("全部", "", source),
						ContentTag("漫画", "/comic", source),
						ContentTag("小说", "/novel", source),
						ContentTag("画集", "/illustration", source),
						ContentTag("其他", "/misc", source)
					)),
					ContentTagGroup("系列", setOf(
						ContentTag("全部", "", source),
						ContentTag("系列", "/series", source),
						ContentTag("单行本", "/offprint", source)
					))
				)
				ContentListFilterOptions(tagGroups = tagGroups)
			}
			"music" -> {
				// Music has no extra tag options directly in media.options.json
				ContentListFilterOptions()
			}
			"game" -> ContentListFilterOptions(
				tagGroups = listOf(
					ContentTagGroup("平台", setOf(
						ContentTag("全部", "", source),
						ContentTag("PC", "/pc", source),
						ContentTag("Mac OS", "/mac", source),
						ContentTag("PS5", "/ps5", source),
						ContentTag("Xbox Series X/S", "/xbox_series_xs", source),
						ContentTag("PS4", "/ps4", source),
						ContentTag("Nintendo Switch", "/ns", source),
						ContentTag("iOS", "/iphone", source),
						ContentTag("Android", "/android", source)
					))
				)
			)
			"real" -> ContentListFilterOptions(
				tagGroups = listOf(
					ContentTagGroup("地区", setOf(
						ContentTag("全部", "", source),
						ContentTag("日剧", "/jp", source),
						ContentTag("欧美剧", "/en", source),
						ContentTag("华语剧", "/cn", source),
						ContentTag("其他", "/misc", source)
					))
				)
			)
			else -> ContentListFilterOptions()
		}
	}

	override suspend fun getList(offset: Int, order: SortOrder?, filter: ContentListFilter?): List<Content> = emptyList()

	override suspend fun getDetails(manga: Content): Content = manga
	override suspend fun getPages(chapter: ContentChapter, nextChapterUrl: String?): List<ContentPage> = emptyList()
	override suspend fun getPageUrl(page: ContentPage): String = ""
	override suspend fun getRelated(seed: Content): List<Content> = emptyList()
}
