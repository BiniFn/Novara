package org.skepsun.kototoro.backups.external

import android.content.Context
import androidx.room.withTransaction
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.db.entity.MangaEntity
import org.skepsun.kototoro.core.db.entity.TagEntity
import org.skepsun.kototoro.favourites.data.FavouriteCategoryEntity
import org.skepsun.kototoro.favourites.data.FavouriteEntity
import org.skepsun.kototoro.history.data.HistoryEntity
import org.skepsun.kototoro.list.domain.ListSortOrder
import org.skepsun.kototoro.list.domain.ReadingProgress.Companion.PROGRESS_NONE
import org.skepsun.kototoro.parsers.model.ContentType
import javax.inject.Inject

@Reusable
class ExternalBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MangaDatabase,
) {

    suspend fun import(payload: ExternalBackupPayload): ExternalBackupImportSummary {
        if (payload.records.isEmpty()) return ExternalBackupImportSummary(0, 0)
        return database.withTransaction {
            val externalCategories = ensureImportedCategories(payload.favoriteCategories)
            val defaultCategoryId = ensureDefaultCategoryId(externalCategories.values)
            var favoritesImported = 0
            var historyImported = 0
            for (record in payload.records) {
                val mangaId = generateContentId(record)
                upsertContent(mangaId, record)
                if (record.isFavorite) {
                    val favoriteTimestamp = record.favoriteTimestamp ?: System.currentTimeMillis()
                    val targetCategoryIds = record.favoriteCategoryOrders
                        .mapNotNull(externalCategories::get)
                        .ifEmpty { listOf(defaultCategoryId.toLong()) }
                    targetCategoryIds.distinct().forEach { categoryId ->
                        database.getFavouritesDao().mergeWithTimestamp(
                            FavouriteEntity(
                                mangaId = mangaId,
                                categoryId = categoryId,
                                sortKey = 0,
                                isPinned = false,
                                createdAt = favoriteTimestamp,
                                deletedAt = 0L,
                                updatedAt = favoriteTimestamp,
                            ),
                        )
                        favoritesImported++
                    }
                }
                if (record.historyTimestamp != null && !record.historyChapterUrl.isNullOrBlank()) {
                    val chapterId = generateChapterId(record, record.historyChapterUrl)
                    val chaptersCount = record.chaptersCount.coerceAtLeast(0)
                    val percent = record.progressPercent?.coerceIn(PROGRESS_NONE, 1f) ?: PROGRESS_NONE
                    database.getHistoryDao().upsert(
                        HistoryEntity(
                            mangaId = mangaId,
                            createdAt = record.historyTimestamp,
                            updatedAt = record.historyTimestamp,
                            chapterId = chapterId,
                            page = 0,
                            scroll = 0f,
                            percent = percent,
                            deletedAt = 0L,
                            chaptersCount = chaptersCount,
                            parentChapterId = null,
                        ),
                    )
                    historyImported++
                }
            }
            ExternalBackupImportSummary(
                favoritesImported = favoritesImported,
                historyImported = historyImported,
            )
        }
    }

    private suspend fun upsertContent(mangaId: Long, record: ExternalBackupContentRecord) {
        val tags = record.tags
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { tag ->
                TagEntity(
                    id = "${record.sourceName}|tag|${tag.lowercase()}".hashCode().toLong() and Long.MAX_VALUE,
                    title = tag,
                    key = tag.lowercase().replace(" ", "_"),
                    source = record.sourceName,
                    isPinned = false,
                )
            }
        database.getTagsDao().upsert(tags)
        database.getMangaDao().upsert(
            MangaEntity(
                id = mangaId,
                title = record.title.ifBlank { record.url },
                altTitles = null,
                url = record.url,
                publicUrl = record.publicUrl.ifBlank { record.url },
                rating = -1f,
                isNsfw = false,
                contentRating = null,
                coverUrl = record.coverUrl.orEmpty(),
                largeCoverUrl = record.coverUrl,
                state = null,
                authors = record.authors,
                source = record.sourceName,
            ),
            tags = tags,
        )
    }

    private suspend fun ensureImportedCategories(
        categories: List<ExternalBackupFavoriteCategoryRecord>,
    ): Map<Long, Long> {
        if (categories.isEmpty()) return emptyMap()
        val categoriesDao = database.getFavouriteCategoriesDao()
        val existingByTitle = categoriesDao.findAll().associateBy { it.title }
        val importedIds = LinkedHashMap<Long, Long>(categories.size)
        var nextSortKey = categoriesDao.getNextSortKey()
        categories.forEach { category ->
            val localCategoryId = existingByTitle[category.name]?.categoryId?.toLong()
                ?: categoriesDao.insert(
                    FavouriteCategoryEntity(
                        categoryId = 0,
                        createdAt = System.currentTimeMillis(),
                        sortKey = nextSortKey++,
                        title = category.name,
                        order = ListSortOrder.NEWEST.name,
                        track = false,
                        isVisibleInLibrary = true,
                        deletedAt = 0L,
                    ),
                )
            importedIds[category.id] = localCategoryId
        }
        return importedIds
    }

    private suspend fun ensureDefaultCategoryId(existingImportedCategoryIds: Collection<Long>): Int {
        val categories = database.getFavouriteCategoriesDao().findAll()
        categories.firstOrNull { it.categoryId.toLong() !in existingImportedCategoryIds }?.let { return it.categoryId }
        val now = System.currentTimeMillis()
        val category = FavouriteCategoryEntity(
            categoryId = 0,
            createdAt = now,
            sortKey = 0,
            title = context.getString(R.string.favourites),
            order = ListSortOrder.NEWEST.name,
            track = false,
            isVisibleInLibrary = true,
            deletedAt = 0L,
        )
        val insertedId = database.getFavouriteCategoriesDao().insert(category)
        return insertedId.toInt()
    }

    private fun generateContentId(record: ExternalBackupContentRecord): Long {
        return "${record.sourceName}|manga|${record.url}".hashCode().toLong() and Long.MAX_VALUE
    }

    private fun generateChapterId(record: ExternalBackupContentRecord, chapterUrl: String): Long {
        val type = when (record.app.family) {
            ExternalBackupFamily.MANGA -> "chapter"
            ExternalBackupFamily.ANIME -> if (record.contentType == ContentType.VIDEO) "episode" else "chapter"
        }
        return "${record.sourceName}|$type|$chapterUrl".hashCode().toLong() and Long.MAX_VALUE
    }
}
