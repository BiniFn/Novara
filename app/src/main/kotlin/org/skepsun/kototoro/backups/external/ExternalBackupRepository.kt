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
import org.skepsun.kototoro.parsers.model.ContentType
import javax.inject.Inject

@Reusable
class ExternalBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MangaDatabase,
) {

    suspend fun import(records: List<ExternalBackupContentRecord>): ExternalBackupImportSummary {
        if (records.isEmpty()) return ExternalBackupImportSummary(0, 0)
        return database.withTransaction {
            val defaultCategoryId = ensureDefaultCategoryId()
            var favoritesImported = 0
            var historyImported = 0
            for (record in records) {
                val mangaId = generateContentId(record.sourceName, record.url)
                upsertContent(mangaId, record)
                if (record.favoriteTimestamp != null) {
                    database.getFavouritesDao().mergeWithTimestamp(
                        FavouriteEntity(
                            mangaId = mangaId,
                            categoryId = defaultCategoryId.toLong(),
                            sortKey = 0,
                            isPinned = false,
                            createdAt = record.favoriteTimestamp,
                            deletedAt = 0L,
                            updatedAt = record.favoriteTimestamp,
                        ),
                    )
                    favoritesImported++
                }
                if (record.historyTimestamp != null && !record.historyChapterUrl.isNullOrBlank()) {
                    val chapterId = generateChapterId(
                        sourceName = record.sourceName,
                        chapterUrl = record.historyChapterUrl,
                        isVideo = record.contentType == ContentType.VIDEO,
                    )
                    database.getHistoryDao().upsert(
                        HistoryEntity(
                            mangaId = mangaId,
                            createdAt = record.historyTimestamp,
                            updatedAt = record.historyTimestamp,
                            chapterId = chapterId,
                            page = 0,
                            scroll = 0f,
                            percent = 0f,
                            deletedAt = 0L,
                            chaptersCount = 0,
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

    private suspend fun ensureDefaultCategoryId(): Int {
        val categories = database.getFavouriteCategoriesDao().findAll()
        categories.firstOrNull()?.let { return it.categoryId }
        val now = System.currentTimeMillis()
        val category = FavouriteCategoryEntity(
            categoryId = 0,
            createdAt = now,
            sortKey = 0,
            title = context.getString(R.string.favourites),
            order = "",
            track = false,
            isVisibleInLibrary = true,
            deletedAt = 0L,
        )
        val insertedId = database.getFavouriteCategoriesDao().insert(category)
        return insertedId.toInt()
    }

    private fun generateContentId(sourceName: String, url: String): Long {
        return "$sourceName|manga|$url".hashCode().toLong() and Long.MAX_VALUE
    }

    private fun generateChapterId(sourceName: String, chapterUrl: String, isVideo: Boolean): Long {
        val type = if (isVideo) "episode" else "chapter"
        return "$sourceName|$type|$chapterUrl".hashCode().toLong() and Long.MAX_VALUE
    }
}
