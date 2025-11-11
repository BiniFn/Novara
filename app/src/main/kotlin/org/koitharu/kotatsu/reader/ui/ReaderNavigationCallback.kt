package org.skepsun.kototoro.reader.ui

import org.skepsun.kototoro.bookmarks.domain.Bookmark
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.reader.ui.pager.ReaderPage

interface ReaderNavigationCallback {

	fun onPageSelected(page: ReaderPage): Boolean

	fun onChapterSelected(chapter: MangaChapter): Boolean

	fun onBookmarkSelected(bookmark: Bookmark): Boolean = onPageSelected(
		ReaderPage(bookmark.toMangaPage(), bookmark.page, bookmark.chapterId),
	)
}
