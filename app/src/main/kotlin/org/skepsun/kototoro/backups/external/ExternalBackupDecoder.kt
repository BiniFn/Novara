package org.skepsun.kototoro.backups.external

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import okio.buffer
import okio.gzip
import okio.source
import org.skepsun.kototoro.parsers.model.ContentType
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class ExternalBackupDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val parser = ProtoBuf

    fun decode(uri: Uri, app: ExternalBackupApp): ExternalBackupPayload {
        val bytes = readBackupBytes(uri)
        return when (app.family) {
            ExternalBackupFamily.MANGA -> tryDecodeMangaBackup(bytes, app)
            ExternalBackupFamily.ANIME -> tryDecodeAnimeBackup(bytes, app)
        } ?: throw UnsupportedExternalBackupException("Unsupported external backup format")
    }

    private fun readBackupBytes(uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val source = inputStream.source().buffer()
            val peeked = source.peek().apply { require(2) }
            val magic = peeked.readShort().toInt()
            val decoded = when (magic) {
                0x1f8b -> source.gzip().buffer()
                MAGIC_JSON_SIGNATURE1, MAGIC_JSON_SIGNATURE2, MAGIC_JSON_SIGNATURE3 -> {
                    throw UnsupportedExternalBackupException("JSON backups are not supported")
                }
                else -> source
            }
            decoded.use { it.readByteArray() }
        } ?: throw IOException("Unable to open backup file")
    }

    private fun tryDecodeMangaBackup(
        bytes: ByteArray,
        app: ExternalBackupApp,
    ): ExternalBackupPayload? {
        return runCatching {
            parser.decodeFromByteArray(MihonBackup.serializer(), bytes).toPayload(app)
        }.getOrNull()
    }

    private fun tryDecodeAnimeBackup(
        bytes: ByteArray,
        app: ExternalBackupApp,
    ): ExternalBackupPayload? {
        return runCatching {
            parser.decodeFromByteArray(AniyomiBackup.serializer(), bytes)
        }.getOrNull()?.toPayload(app)
    }

    private fun normalizeTimestamp(ts: Long): Long {
        if (ts <= 0L) return 0L
        // timestamps before 2000-01-01 in milliseconds are likely in seconds
        return if (ts < 946684800000L) ts * 1000L else ts
    }

    private fun resolveFavoriteTimestamp(
        favoriteModifiedAt: Long?,
        dateAdded: Long,
        lastModifiedAt: Long,
    ): Long? {
        val candidates = listOfNotNull(
            dateAdded.takeIf { it > 0L },
            favoriteModifiedAt?.takeIf { it > 0L },
            lastModifiedAt.takeIf { it > 0L },
        )
        return candidates.firstOrNull()?.let { normalizeTimestamp(it) }
    }

    private fun calculateProgressPercent(
        totalCount: Int,
        completedCount: Int,
    ): Float? {
        if (totalCount <= 0) return null
        val safeCompletedCount = completedCount.coerceIn(0, totalCount)
        return max(0f, safeCompletedCount.toFloat() / totalCount.toFloat())
    }

    @Serializable
    private data class MihonBackup(
        @ProtoNumber(1) val backupManga: List<MihonBackupManga> = emptyList(),
        @ProtoNumber(2) val backupCategories: List<MihonBackupCategory> = emptyList(),
    )

    @Serializable
    private data class MihonBackupManga(
        @ProtoNumber(1) val source: Long,
        @ProtoNumber(2) val url: String,
        @ProtoNumber(3) val title: String = "",
        @ProtoNumber(4) val artist: String? = null,
        @ProtoNumber(5) val author: String? = null,
        @ProtoNumber(6) val description: String? = null,
        @ProtoNumber(7) val genre: List<String> = emptyList(),
        @ProtoNumber(8) val status: Int = 0,
        @ProtoNumber(9) val thumbnailUrl: String? = null,
        @ProtoNumber(13) val dateAdded: Long = 0,
        @ProtoNumber(100) val favorite: Boolean = true,
        @ProtoNumber(16) val chapters: List<MihonBackupChapter> = emptyList(),
        @ProtoNumber(17) val categories: List<Long> = emptyList(),
        @ProtoNumber(104) val history: List<MihonBackupHistory> = emptyList(),
        @ProtoNumber(106) val lastModifiedAt: Long = 0,
        @ProtoNumber(107) val favoriteModifiedAt: Long? = null,
    )

    @Serializable
    private data class MihonBackupCategory(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val order: Long = 0,
        @ProtoNumber(3) val id: Long = 0,
        @ProtoNumber(100) val flags: Long = 0,
    )

    @Serializable
    private data class MihonBackupChapter(
        @ProtoNumber(1) val url: String,
        @ProtoNumber(2) val name: String = "",
        @ProtoNumber(4) val read: Boolean = false,
    )

    @Serializable
    private data class MihonBackupHistory(
        @ProtoNumber(1) val url: String,
        @ProtoNumber(2) val lastRead: Long,
    )

    @Serializable
    private data class AniyomiBackup(
        @ProtoNumber(1) val backupManga: List<AniyomiBackupManga> = emptyList(),
        @ProtoNumber(2) val backupCategories: List<MihonBackupCategory> = emptyList(),
        @ProtoNumber(501) val backupAnime: List<AniyomiBackupAnime> = emptyList(),
    )

    @Serializable
    private data class AniyomiBackupManga(
        @ProtoNumber(1) val source: Long,
        @ProtoNumber(2) val url: String,
        @ProtoNumber(3) val title: String = "",
        @ProtoNumber(4) val artist: String? = null,
        @ProtoNumber(5) val author: String? = null,
        @ProtoNumber(6) val description: String? = null,
        @ProtoNumber(7) val genre: List<String> = emptyList(),
        @ProtoNumber(8) val status: Int = 0,
        @ProtoNumber(9) val thumbnailUrl: String? = null,
        @ProtoNumber(13) val dateAdded: Long = 0,
        @ProtoNumber(100) val favorite: Boolean = true,
        @ProtoNumber(16) val chapters: List<MihonBackupChapter> = emptyList(),
        @ProtoNumber(17) val categories: List<Long> = emptyList(),
        @ProtoNumber(104) val history: List<MihonBackupHistory> = emptyList(),
        @ProtoNumber(106) val lastModifiedAt: Long = 0,
        @ProtoNumber(107) val favoriteModifiedAt: Long? = null,
    )

    @Serializable
    private data class AniyomiBackupAnime(
        @ProtoNumber(1) val source: Long,
        @ProtoNumber(2) val url: String,
        @ProtoNumber(3) val title: String = "",
        @ProtoNumber(4) val artist: String? = null,
        @ProtoNumber(5) val author: String? = null,
        @ProtoNumber(6) val description: String? = null,
        @ProtoNumber(7) val genre: List<String> = emptyList(),
        @ProtoNumber(8) val status: Int = 0,
        @ProtoNumber(9) val thumbnailUrl: String? = null,
        @ProtoNumber(13) val dateAdded: Long = 0,
        @ProtoNumber(100) val favorite: Boolean = true,
        @ProtoNumber(16) val episodes: List<AniyomiBackupEpisode> = emptyList(),
        @ProtoNumber(17) val categories: List<Long> = emptyList(),
        @ProtoNumber(104) val history: List<AniyomiBackupHistory> = emptyList(),
        @ProtoNumber(106) val lastModifiedAt: Long = 0,
        @ProtoNumber(107) val favoriteModifiedAt: Long? = null,
    )

    @Serializable
    private data class AniyomiBackupEpisode(
        @ProtoNumber(1) val url: String,
        @ProtoNumber(2) val name: String = "",
        @ProtoNumber(4) val seen: Boolean = false,
    )

    @Serializable
    private data class AniyomiBackupHistory(
        @ProtoNumber(1) val url: String,
        @ProtoNumber(2) val lastRead: Long,
    )

    private companion object {
        private const val MAGIC_JSON_SIGNATURE1 = 0x7b7d
        private const val MAGIC_JSON_SIGNATURE2 = 0x7b22
        private const val MAGIC_JSON_SIGNATURE3 = 0x7b0a
    }

    private fun MihonBackup.toPayload(app: ExternalBackupApp): ExternalBackupPayload {
        return ExternalBackupPayload(
            records = backupManga.mapNotNull { manga ->
                manga.toRecord(
                    app = app,
                    sourceName = "MIHON_${manga.source}",
                    contentType = ContentType.MANGA,
                    totalCount = manga.chapters.size,
                    completedCount = manga.chapters.count { it.read },
                )
            },
            favoriteCategories = backupCategories.toFavoriteCategoryRecords(),
        )
    }

    private fun AniyomiBackup.toPayload(app: ExternalBackupApp): ExternalBackupPayload {
        val mangaRecords = backupManga.mapNotNull { manga ->
            manga.toRecord(
                app = app,
                sourceName = "MIHON_${manga.source}",
                contentType = ContentType.MANGA,
                totalCount = manga.chapters.size,
                completedCount = manga.chapters.count { it.read },
            )
        }
        val animeRecords = backupAnime.mapNotNull { anime ->
            val favoriteTimestamp = resolveFavoriteTimestamp(anime.favoriteModifiedAt, anime.dateAdded, anime.lastModifiedAt)
            val history = anime.history.maxByOrNull { it.lastRead }
            val progressPercent = calculateProgressPercent(
                totalCount = anime.episodes.size,
                completedCount = anime.episodes.count { it.seen },
            )
            if (!anime.favorite && history == null) {
                null
            } else {
                ExternalBackupContentRecord(
                    app = app,
                    sourceName = "ANIYOMI_${anime.source}",
                    contentType = ContentType.VIDEO,
                    url = anime.url,
                    title = anime.title,
                    authors = listOfNotNull(anime.author, anime.artist)
                        .distinct()
                        .joinToString(", ")
                        .ifBlank { null },
                    description = anime.description,
                    tags = anime.genre,
                    coverUrl = anime.thumbnailUrl,
                    publicUrl = anime.url,
                    state = anime.status.toString(),
                    isFavorite = anime.favorite,
                    favoriteTimestamp = favoriteTimestamp,
                    favoriteCategoryOrders = anime.categories,
                    chaptersCount = anime.episodes.size,
                    readEntriesCount = anime.episodes.count { it.seen },
                    progressPercent = progressPercent,
                    historyChapterUrl = history?.url,
                    historyTimestamp = history?.lastRead?.takeIf { it > 0L },
                )
            }
        }
        return ExternalBackupPayload(
            records = mangaRecords + animeRecords,
            favoriteCategories = backupCategories.toFavoriteCategoryRecords(),
        )
    }

    private fun MihonBackupManga.toRecord(
        app: ExternalBackupApp,
        sourceName: String,
        contentType: ContentType,
        totalCount: Int,
        completedCount: Int,
    ): ExternalBackupContentRecord? {
        val favoriteTimestamp = resolveFavoriteTimestamp(favoriteModifiedAt, dateAdded, lastModifiedAt)
        val history = history.maxByOrNull { it.lastRead }
        val progressPercent = calculateProgressPercent(
            totalCount = totalCount,
            completedCount = completedCount,
        )
        if (!favorite && history == null) {
            return null
        }
        return ExternalBackupContentRecord(
            app = app,
            sourceName = sourceName,
            contentType = contentType,
            url = url,
            title = title,
            authors = listOfNotNull(author, artist)
                .distinct()
                .joinToString(", ")
                .ifBlank { null },
            description = description,
            tags = genre,
            coverUrl = thumbnailUrl,
            publicUrl = url,
            state = status.toString(),
            isFavorite = favorite,
            favoriteTimestamp = favoriteTimestamp,
            favoriteCategoryOrders = categories,
            chaptersCount = totalCount,
            readEntriesCount = completedCount,
            progressPercent = progressPercent,
            historyChapterUrl = history?.url,
            historyTimestamp = history?.lastRead?.takeIf { it > 0L },
        )
    }

    private fun AniyomiBackupManga.toRecord(
        app: ExternalBackupApp,
        sourceName: String,
        contentType: ContentType,
        totalCount: Int,
        completedCount: Int,
    ): ExternalBackupContentRecord? {
        val favoriteTimestamp = resolveFavoriteTimestamp(favoriteModifiedAt, dateAdded, lastModifiedAt)
        val history = history.maxByOrNull { it.lastRead }
        val progressPercent = calculateProgressPercent(
            totalCount = totalCount,
            completedCount = completedCount,
        )
        if (!favorite && history == null) {
            return null
        }
        android.util.Log.d("KototoroBackup", "AniyomiManga ts: favModAt=$favoriteModifiedAt dateAdd=$dateAdded lastMod=$lastModifiedAt resolved=$favoriteTimestamp title=$title")
        return ExternalBackupContentRecord(
            app = app,
            sourceName = sourceName,
            contentType = contentType,
            url = url,
            title = title,
            authors = listOfNotNull(author, artist)
                .distinct()
                .joinToString(", ")
                .ifBlank { null },
            description = description,
            tags = genre,
            coverUrl = thumbnailUrl,
            publicUrl = url,
            state = status.toString(),
            isFavorite = favorite,
            favoriteTimestamp = favoriteTimestamp,
            favoriteCategoryOrders = categories,
            chaptersCount = totalCount,
            readEntriesCount = completedCount,
            progressPercent = progressPercent,
            historyChapterUrl = history?.url,
            historyTimestamp = history?.lastRead?.takeIf { it > 0L },
        )
    }

    private fun List<MihonBackupCategory>.toFavoriteCategoryRecords(): List<ExternalBackupFavoriteCategoryRecord> {
        return filter { it.name.isNotBlank() }
            .distinctBy { it.name }
            .sortedBy { it.order }
            .map { ExternalBackupFavoriteCategoryRecord(name = it.name, order = it.order, id = it.id, flags = it.flags) }
    }
}
