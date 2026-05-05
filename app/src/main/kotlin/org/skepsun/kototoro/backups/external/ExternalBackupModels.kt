package org.skepsun.kototoro.backups.external

import org.skepsun.kototoro.parsers.model.ContentType

data class ExternalBackupPayload(
    val records: List<ExternalBackupContentRecord>,
    val favoriteCategories: List<ExternalBackupFavoriteCategoryRecord> = emptyList(),
)

data class ExternalBackupFavoriteCategoryRecord(
    val name: String,
    val order: Long,
    val id: Long,
    val flags: Long = 0,
)

data class ExternalBackupContentRecord(
    val app: ExternalBackupApp,
    val sourceName: String,
    val contentType: ContentType,
    val url: String,
    val title: String,
    val authors: String?,
    val description: String?,
    val tags: List<String>,
    val coverUrl: String?,
    val publicUrl: String,
    val state: String?,
    val isFavorite: Boolean,
    val favoriteTimestamp: Long?,
    val favoriteCategoryOrders: List<Long>,
    val chaptersCount: Int,
    val readEntriesCount: Int,
    val progressPercent: Float?,
    val historyChapterUrl: String?,
    val historyTimestamp: Long?,
)

data class ExternalBackupImportSummary(
    val favoritesImported: Int,
    val historyImported: Int,
)
