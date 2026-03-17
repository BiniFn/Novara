package org.skepsun.kototoro.reader.data

import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentChapter

fun Content.filterChapters(branch: String?): Content {
	if (chapters.isNullOrEmpty()) return this
	return withChapters(chapters = chapters?.filter { it.branch == branch })
}

private fun Content.withChapters(chapters: List<ContentChapter>?) = copy(
	chapters = chapters,
)
