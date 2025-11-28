package org.skepsun.kototoro.reader.novel

import org.skepsun.kototoro.parsers.model.MangaChapter

/**
 * 小说阅读器状态
 */
data class NovelReaderState(
    val mangaName: String,
    val currentChapter: MangaChapter?,
    val currentChapterIndex: Int,
    val totalChapters: Int,
    val currentPage: Int,
    val totalPages: Int,
    val scrollProgress: Float, // 0-100
    val incognito: Boolean = false,
) {
    fun getChapterTitle(): String {
        return currentChapter?.title ?: ""
    }

    fun hasNextChapter(): Boolean = currentChapterIndex < totalChapters - 1

    fun hasPreviousChapter(): Boolean = currentChapterIndex > 0

    fun isSliderAvailable(): Boolean = totalPages > 1
}
