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

    fun decode(uri: Uri, app: ExternalBackupApp): List<ExternalBackupContentRecord> {
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
    ): List<ExternalBackupContentRecord>? {
        return runCatching {
            parser.decodeFromByteArray(MihonBackup.serializer(), bytes).backupManga.mapNotNull { manga ->
                val favoriteTimestamp = manga.favoriteModifiedAt ?: manga.dateAdded.takeIf { it > 0L }
                val history = manga.history.maxByOrNull { it.lastRead }
                val progressPercent = calculateProgressPercent(
                    totalCount = manga.chapters.size,
                    completedCount = manga.chapters.count { it.read },
                )
                if (!manga.favorite && history == null) {
                    null
                } else {
                    ExternalBackupContentRecord(
                        app = app,
                        sourceName = "MIHON_${manga.source}",
                        contentType = ContentType.MANGA,
                        url = manga.url,
                        title = manga.title,
                        authors = listOfNotNull(manga.author, manga.artist)
                            .distinct()
                            .joinToString(", ")
                            .ifBlank { null },
                        description = manga.description,
                        tags = manga.genre,
                        coverUrl = manga.thumbnailUrl,
                        publicUrl = manga.url,
                        state = manga.status.toString(),
                        isFavorite = manga.favorite,
                        favoriteTimestamp = favoriteTimestamp,
                        chaptersCount = manga.chapters.size,
                        readEntriesCount = manga.chapters.count { it.read },
                        progressPercent = progressPercent,
                        historyChapterUrl = history?.url,
                        historyTimestamp = history?.lastRead?.takeIf { it > 0L },
                    )
                }
            }
        }.getOrNull()
    }

    private fun tryDecodeAnimeBackup(
        bytes: ByteArray,
        app: ExternalBackupApp,
    ): List<ExternalBackupContentRecord>? {
        return runCatching {
            parser.decodeFromByteArray(AniyomiBackup.serializer(), bytes)
        }.getOrNull()?.let { backup ->
            val mangaRecords = backup.backupManga.mapNotNull { manga ->
                val favoriteTimestamp = manga.favoriteModifiedAt ?: manga.dateAdded.takeIf { it > 0L }
                val history = manga.history.maxByOrNull { it.lastRead }
                val progressPercent = calculateProgressPercent(
                    totalCount = manga.chapters.size,
                    completedCount = manga.chapters.count { it.read },
                )
                if (!manga.favorite && history == null) {
                    null
                } else {
                    ExternalBackupContentRecord(
                        app = app,
                        sourceName = "MIHON_${manga.source}",
                        contentType = ContentType.MANGA,
                        url = manga.url,
                        title = manga.title,
                        authors = listOfNotNull(manga.author, manga.artist)
                            .distinct()
                            .joinToString(", ")
                            .ifBlank { null },
                        description = manga.description,
                        tags = manga.genre,
                        coverUrl = manga.thumbnailUrl,
                        publicUrl = manga.url,
                        state = manga.status.toString(),
                        isFavorite = manga.favorite,
                        favoriteTimestamp = favoriteTimestamp,
                        chaptersCount = manga.chapters.size,
                        readEntriesCount = manga.chapters.count { it.read },
                        progressPercent = progressPercent,
                        historyChapterUrl = history?.url,
                        historyTimestamp = history?.lastRead?.takeIf { it > 0L },
                    )
                }
            }
            val animeRecords = backup.backupAnime.mapNotNull { anime ->
                val favoriteTimestamp = anime.favoriteModifiedAt ?: anime.dateAdded.takeIf { it > 0L }
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
                        chaptersCount = anime.episodes.size,
                        readEntriesCount = anime.episodes.count { it.seen },
                        progressPercent = progressPercent,
                        historyChapterUrl = history?.url,
                        historyTimestamp = history?.lastRead?.takeIf { it > 0L },
                    )
                }
            }
            mangaRecords + animeRecords
        }
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
        @ProtoNumber(104) val history: List<MihonBackupHistory> = emptyList(),
        @ProtoNumber(107) val favoriteModifiedAt: Long? = null,
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
        @ProtoNumber(3) val backupAnime: List<AniyomiBackupAnime> = emptyList(),
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
        @ProtoNumber(104) val history: List<MihonBackupHistory> = emptyList(),
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
        @ProtoNumber(104) val history: List<AniyomiBackupHistory> = emptyList(),
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
}
