package org.skepsun.kototoro.reader.data

import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaChapter

fun Manga.filterChapters(branch: String?): Manga {
	if (chapters.isNullOrEmpty()) return this
	return withChapters(chapters = chapters?.filter { it.branch == branch })
}

private fun Manga.withChapters(chapters: List<MangaChapter>?) = copy(
	chapters = chapters,
)
