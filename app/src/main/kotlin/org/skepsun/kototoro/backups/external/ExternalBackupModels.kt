package org.skepsun.kototoro.backups.external

import org.skepsun.kototoro.parsers.model.ContentType

data class ExternalBackupContentRecord(
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
    val favoriteTimestamp: Long?,
    val historyChapterUrl: String?,
    val historyTimestamp: Long?,
)

data class ExternalBackupImportSummary(
    val favoritesImported: Int,
    val historyImported: Int,
)
