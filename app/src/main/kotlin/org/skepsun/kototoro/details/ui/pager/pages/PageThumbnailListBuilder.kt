package org.skepsun.kototoro.details.ui.pager.pages

import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.list.ui.model.ListModel
import org.skepsun.kototoro.reader.domain.ChaptersLoader
import org.skepsun.kototoro.reader.ui.ReaderState

internal fun ChaptersLoader.buildPageThumbnailList(readerState: ReaderState? = null): List<ListModel> {
	val snapshot = snapshot()
	return buildList(snapshot.size + size + 2) {
		var previousChapterId = 0L
		for (page in snapshot) {
			if (page.chapterId != previousChapterId) {
				peekChapter(page.chapterId)?.let {
					add(ListHeader(it))
				}
				previousChapterId = page.chapterId
			}
			this += PageThumbnail(
				isCurrent = readerState?.let {
					page.chapterId == it.chapterId && page.index == it.page
				} == true,
				page = page,
			)
		}
	}
}
