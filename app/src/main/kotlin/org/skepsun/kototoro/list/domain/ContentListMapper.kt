package org.skepsun.kototoro.list.domain

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.IntDef
import androidx.collection.MutableScatterSet
import androidx.collection.ScatterSet
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.parser.ContentDataRepository
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.ListMode
import org.skepsun.kototoro.core.ui.model.ContentOverride
import org.skepsun.kototoro.core.ui.widgets.ChipsView
import org.skepsun.kototoro.favourites.domain.FavouritesRepository
import org.skepsun.kototoro.history.data.HistoryRepository
import org.skepsun.kototoro.list.ui.model.ContentCompactListModel
import org.skepsun.kototoro.list.ui.model.ContentDetailedListModel
import org.skepsun.kototoro.list.ui.model.ContentGridModel
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.local.data.index.LocalContentIndex
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import org.skepsun.kototoro.tracker.domain.TrackingRepository
import org.skepsun.kototoro.tracker.domain.model.TrackingLogItem
import org.skepsun.kototoro.tracker.ui.feed.model.FeedItem
import javax.inject.Inject

@Reusable
class ContentListMapper @Inject constructor(
	@ApplicationContext context: Context,
	private val settings: AppSettings,
	private val trackingRepository: TrackingRepository,
	private val historyRepository: HistoryRepository,
	private val favouritesRepository: FavouritesRepository,
	private val localContentIndex: LocalContentIndex,
	private val dataRepository: ContentDataRepository,
) {

	private val dict by lazy { readTagsDict(context) }

	suspend fun toListModelList(
		manga: Collection<Content>,
		mode: ListMode,
		@Flags flags: Int = DEFAULTS,
	): List<ContentListModel> = ArrayList<ContentListModel>(manga.size).apply {
		toListModelList(
			destination = this,
			manga = manga,
			mode = mode,
			flags = flags,
		)
	}

	suspend fun toListModelList(
		destination: MutableCollection<in ContentListModel>,
		manga: Collection<Content>,
		mode: ListMode,
		@Flags flags: Int = DEFAULTS,
	) {
		val options = getOptions(flags)
		val overrides = dataRepository.getOverrides()
		manga.mapTo(destination) {
			toListModelImpl(it, mode, options, overrides[it.id])
		}
	}

	suspend fun toListModel(
		manga: Content,
		mode: ListMode,
		@Flags flags: Int = DEFAULTS,
	): ContentListModel = toListModelImpl(
		manga = manga,
		mode = mode,
		options = getOptions(flags),
		override = dataRepository.getOverride(manga.id),
	)

	suspend fun toFeedItem(logItem: TrackingLogItem) = FeedItem(
		id = logItem.id,
		override = dataRepository.getOverride(logItem.manga.id),
		count = logItem.chapters.size,
		manga = logItem.manga,
		isNew = logItem.isNew,
	)

	fun mapTags(tags: Collection<ContentTag>) = tags.map {
		ChipsView.ChipModel(
			tint = getTagTint(it),
			title = it.title,
			data = it,
		)
	}

	private suspend fun toCompactListModel(
		manga: Content,
		@Options options: Int,
		override: ContentOverride?,
	) = ContentCompactListModel(
		manga = manga,
		override = override,
		subtitle = manga.tags.joinToString(", ") { it.title },
		counter = getCounter(manga.id, options),
		isPinned = isPinned(manga.id, options),
	)

	private suspend fun toDetailedListModel(
		manga: Content,
		@Options options: Int,
		override: ContentOverride?,
	) = ContentDetailedListModel(
		subtitle = manga.altTitles.firstOrNull(),
		manga = manga,
		override = override,
		counter = getCounter(manga.id, options),
		progress = getProgress(manga.id, options),
		isFavorite = isFavorite(manga.id, options),
		isSaved = isSaved(manga.id, options),
		tags = mapTags(manga.tags),
		isPinned = isPinned(manga.id, options),
	)

	private suspend fun toGridModel(
		manga: Content,
		@Options options: Int,
		override: ContentOverride?
	) = ContentGridModel(
		manga = manga,
		override = override,
		counter = getCounter(manga.id, options),
		progress = getProgress(manga.id, options),
		isFavorite = isFavorite(manga.id, options),
		isSaved = isSaved(manga.id, options),
		isPinned = isPinned(manga.id, options),
		metadataTrackingService = getMetadataTrackingService(manga.id),
	)

	private suspend fun toListModelImpl(
		manga: Content,
		mode: ListMode,
		@Options options: Int,
		override: ContentOverride?,
	): ContentListModel = when (mode) {
		ListMode.LIST -> toCompactListModel(manga, options, override)
		ListMode.DETAILED_LIST -> toDetailedListModel(manga, options, override)
		ListMode.GRID -> toGridModel(manga, options, override)
	}

	private suspend fun getCounter(mangaId: Long, @Options options: Int): Int {
		return if (settings.isTrackerEnabled) {
			trackingRepository.getNewChaptersCount(mangaId)
		} else {
			0
		}
	}

	private suspend fun getProgress(mangaId: Long, @Options options: Int): ReadingProgress? {
		return if (options.isBadgeEnabled(PROGRESS)) {
			historyRepository.getProgress(mangaId, settings.progressIndicatorMode)
		} else {
			null
		}
	}

	private suspend fun isFavorite(mangaId: Long, @Options options: Int): Boolean {
		return options.isBadgeEnabled(FAVORITE) && favouritesRepository.isFavorite(mangaId)
	}

	private suspend fun isPinned(mangaId: Long, @Options options: Int): Boolean {
		return favouritesRepository.isPinned(listOf(mangaId))
	}

	private suspend fun isSaved(mangaId: Long, @Options options: Int): Boolean {
		return options.isBadgeEnabled(SAVED) && mangaId in localContentIndex
	}

	private suspend fun getMetadataTrackingService(mangaId: Long): ScrobblerService? {
		val selection = dataRepository.getMetadataSourceSelection(mangaId)
			as? ContentDataRepository.MetadataSourceSelection.Tracking
			?: return null
		return ScrobblerService.entries.firstOrNull { it.id == selection.serviceId }
	}

	@ColorRes
	private fun getTagTint(tag: ContentTag): Int {
		return if (settings.isTagsWarningsEnabled && tag.title.lowercase() in dict) {
			R.color.warning
		} else {
			0
		}
	}

	private fun readTagsDict(context: Context): ScatterSet<String> =
		context.resources.openRawResource(R.raw.tags_warnlist).use {
			val set = MutableScatterSet<String>()
			it.bufferedReader().forEachLine { x ->
				val line = x.trim()
				if (line.isNotEmpty()) {
					set.add(line)
				}
			}
			set.trim()
			set
		}

	private fun Int.isBadgeEnabled(@Options badge: Int) = this and badge == badge

	@Options
	@SuppressLint("WrongConstant")
	private fun getOptions(@Flags flags: Int): Int {
		var options = settings.getContentListBadges() or PROGRESS
		options = options and flags.inv()
		return options
	}

	@IntDef(DEFAULTS, NO_SAVED, NO_PROGRESS, NO_FAVORITE, flag = true)
	@Retention(AnnotationRetention.SOURCE)
	annotation class Flags

	@IntDef(NONE, SAVED, FAVORITE, PROGRESS)
	@Retention(AnnotationRetention.SOURCE)
	private annotation class Options

	companion object {

		private const val NONE = 0
		private const val SAVED = 1
		private const val PROGRESS = 2
		private const val FAVORITE = 4

		const val DEFAULTS = NONE
		const val NO_SAVED = SAVED
		const val NO_PROGRESS = PROGRESS
		const val NO_FAVORITE = FAVORITE
	}
}
