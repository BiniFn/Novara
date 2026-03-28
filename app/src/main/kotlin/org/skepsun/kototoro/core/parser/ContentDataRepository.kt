package org.skepsun.kototoro.core.parser

import androidx.collection.LongObjectMap
import androidx.collection.MutableLongObjectMap
import androidx.core.net.toUri
import androidx.room.withTransaction
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.TABLE_FAVOURITES
import org.skepsun.kototoro.core.db.TABLE_FAVOURITE_CATEGORIES
import org.skepsun.kototoro.core.db.TABLE_PREFERENCES
import org.skepsun.kototoro.core.db.entity.ContentRating
import org.skepsun.kototoro.core.db.entity.MangaPrefsEntity
import org.skepsun.kototoro.core.db.entity.toEntities
import org.skepsun.kototoro.core.db.entity.toEntity
import org.skepsun.kototoro.core.db.entity.toContent
import org.skepsun.kototoro.core.db.entity.toContentChapters
import org.skepsun.kototoro.core.db.entity.toContentTags
import org.skepsun.kototoro.core.model.LocalMangaSource
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.core.nav.ContentIntent
import org.skepsun.kototoro.core.os.AppShortcutManager
import org.skepsun.kototoro.core.prefs.ReaderMode
import org.skepsun.kototoro.core.ui.model.ContentOverride
import org.skepsun.kototoro.core.util.ext.toFileOrNull
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.parsers.util.nullIfEmpty
import org.skepsun.kototoro.reader.domain.ReaderColorFilter
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class ContentDataRepository @Inject constructor(
	private val db: MangaDatabase,
	private val resolverProvider: Provider<ContentLinkResolver>,
	private val appShortcutManagerProvider: Provider<AppShortcutManager>,
) {

	suspend fun saveReaderMode(manga: Content, mode: ReaderMode) {
		db.withTransaction {
			storeContent(manga, replaceExisting = false)
			val entity = db.getPreferencesDao().find(manga.id) ?: newEntity(manga.id)
			db.getPreferencesDao().upsert(entity.copy(mode = mode.id))
		}
	}

	suspend fun saveColorFilter(manga: Content, colorFilter: ReaderColorFilter?) {
		db.withTransaction {
			storeContent(manga, replaceExisting = false)
			val entity = db.getPreferencesDao().find(manga.id) ?: newEntity(manga.id)
			db.getPreferencesDao().upsert(
				entity.copy(
					cfBrightness = colorFilter?.brightness ?: 0f,
					cfContrast = colorFilter?.contrast ?: 0f,
					cfInvert = colorFilter?.isInverted == true,
					cfGrayscale = colorFilter?.isGrayscale == true,
				),
			)
		}
	}

	suspend fun resetColorFilters() {
		db.getPreferencesDao().resetColorFilters()
	}

	suspend fun getReaderMode(mangaId: Long): ReaderMode? {
		return db.getPreferencesDao().find(mangaId)?.let { ReaderMode.valueOf(it.mode) }
	}

	suspend fun getColorFilter(mangaId: Long): ReaderColorFilter? {
		return db.getPreferencesDao().find(mangaId)?.getColorFilterOrNull()
	}

	suspend fun getOverride(mangaId: Long): ContentOverride? {
		return db.getPreferencesDao().find(mangaId)?.getOverrideOrNull()
	}

	suspend fun getOverrides(): LongObjectMap<ContentOverride> {
		val entities = db.getPreferencesDao().getOverrides()
		val map = MutableLongObjectMap<ContentOverride>(entities.size)
		for (entity in entities) {
			map[entity.mangaId] = entity.getOverrideOrNull() ?: continue
		}
		return map
	}

	suspend fun setOverride(manga: Content, override: ContentOverride?) {
		db.withTransaction {
			storeContent(manga, replaceExisting = false)
			val dao = db.getPreferencesDao()
			val entity = dao.find(manga.id) ?: newEntity(manga.id)
			dao.upsert(
				entity.copy(
					titleOverride = override?.title?.nullIfEmpty(),
					coverUrlOverride = override?.coverUrl?.nullIfEmpty(),
					contentRatingOverride = override?.contentRating?.name,
				),
			)
		}
	}

	fun observeColorFilter(mangaId: Long): Flow<ReaderColorFilter?> {
		return db.getPreferencesDao().observe(mangaId)
			.map { it?.getColorFilterOrNull() }
			.distinctUntilChanged()
	}

	suspend fun findContentById(mangaId: Long, withChapters: Boolean): Content? {
		val chapters = if (withChapters) {
			db.getChaptersDao().findAll(mangaId).takeUnless { it.isEmpty() }
		} else {
			null
		}
		return db.getMangaDao().find(mangaId)?.toContent(chapters)
	}

	suspend fun findContentByPublicUrl(publicUrl: String): Content? {
		return db.getMangaDao().findByPublicUrl(publicUrl)?.toContent()
	}

	suspend fun resolveIntent(intent: ContentIntent, withChapters: Boolean): Content? = when {
		intent.manga != null -> intent.manga.withCachedChaptersIfNeeded(withChapters)
		intent.mangaId != 0L -> findContentById(intent.mangaId, withChapters)
		intent.uri != null -> resolverProvider.get().resolve(intent.uri).withCachedChaptersIfNeeded(withChapters)
		else -> null
	}

	suspend fun storeContent(manga: Content, replaceExisting: Boolean) {
		if (!replaceExisting && db.getMangaDao().find(manga.id) != null) {
			return
		}
		db.withTransaction {
			// avoid storing local manga if remote one is already stored
			val existing = if (manga.isLocal) {
				db.getMangaDao().find(manga.id)?.manga
			} else {
				null
			}
			if (existing == null || existing.source == manga.source.name) {
				val tags = manga.tags.toEntities()
				db.getTagsDao().upsert(tags)
				db.getMangaDao().upsert(manga.toEntity(), tags)
				if (!manga.isLocal) {
					manga.chapters?.let { chapters ->
						db.getChaptersDao().replaceAll(manga.id, chapters.withIndex().toEntities(manga.id))
					}
				}
			}
		}
	}

	suspend fun updateChapters(manga: Content) {
		val chapters = manga.chapters
		if (!chapters.isNullOrEmpty() && manga.id in db.getMangaDao()) {
			db.getChaptersDao().replaceAll(manga.id, chapters.withIndex().toEntities(manga.id))
		}
	}

	suspend fun gcChaptersCache() {
		db.getChaptersDao().gc()
	}

	suspend fun findTags(source: ContentSource): Set<ContentTag> {
		return db.getTagsDao().findTags(source.name).toContentTags()
	}

	suspend fun cleanupLocalContent() {
		val dao = db.getMangaDao()
		val broken = listOf(LocalMangaSource.name, org.skepsun.kototoro.core.model.LocalNovelSource.name, org.skepsun.kototoro.core.model.LocalVideoSource.name)
			.flatMap { dao.findAllBySource(it) }
			.filter { x -> x.manga.url.toUri().toFileOrNull()?.exists() == false }
		if (broken.isNotEmpty()) {
			dao.delete(broken.map { it.manga })
		}
	}

	suspend fun cleanupDatabase() {
		db.withTransaction {
			gcChaptersCache()
			val idsFromShortcuts = appShortcutManagerProvider.get().getContentShortcuts()
			db.getMangaDao().cleanup(idsFromShortcuts)
		}
	}

	fun observeOverridesTrigger(emitInitialState: Boolean) = db.invalidationTracker.createFlow(
		tables = arrayOf(TABLE_PREFERENCES),
		emitInitialState = emitInitialState,
	)

	fun observeFavoritesTrigger(emitInitialState: Boolean) = db.invalidationTracker.createFlow(
		tables = arrayOf(TABLE_FAVOURITES, TABLE_FAVOURITE_CATEGORIES),
		emitInitialState = emitInitialState,
	)

	private suspend fun Content.withCachedChaptersIfNeeded(flag: Boolean): Content = if (flag && !isLocal && chapters.isNullOrEmpty()) {
		val cachedChapters = db.getChaptersDao().findAll(id)
		if (cachedChapters.isEmpty()) {
			this
		} else {
			copy(chapters = cachedChapters.toContentChapters())
		}
	} else {
		this
	}

	private fun MangaPrefsEntity.getColorFilterOrNull(): ReaderColorFilter? {
		return if (cfBrightness != 0f || cfContrast != 0f || cfInvert || cfGrayscale || cfBookEffect) {
			ReaderColorFilter(
				brightness = cfBrightness,
				contrast = cfContrast,
				isInverted = cfInvert,
				isGrayscale = cfGrayscale,
				isBookBackground = cfBookEffect
			)
		} else {
			null
		}
	}

	private fun MangaPrefsEntity.getOverrideOrNull(): ContentOverride? {
		return if (titleOverride.isNullOrEmpty() && coverUrlOverride.isNullOrEmpty() && contentRatingOverride.isNullOrEmpty()) {
			null
		} else {
			ContentOverride(
				coverUrl = coverUrlOverride?.nullIfEmpty(),
				title = titleOverride?.nullIfEmpty(),
				contentRating = ContentRating(contentRatingOverride),
			)
		}
	}

	private fun newEntity(mangaId: Long) = MangaPrefsEntity(
		mangaId = mangaId,
		mode = -1,
		cfBrightness = ReaderColorFilter.EMPTY.brightness,
		cfContrast = ReaderColorFilter.EMPTY.contrast,
		cfInvert = ReaderColorFilter.EMPTY.isInverted,
		cfGrayscale = ReaderColorFilter.EMPTY.isGrayscale,
		cfBookEffect = ReaderColorFilter.EMPTY.isBookBackground,
		titleOverride = null,
		coverUrlOverride = null,
		contentRatingOverride = null,
	)
}
