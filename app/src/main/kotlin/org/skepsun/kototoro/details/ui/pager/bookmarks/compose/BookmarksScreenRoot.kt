package org.skepsun.kototoro.details.ui.pager.bookmarks.compose

import android.content.Context
import androidx.compose.runtime.*

import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.ReaderIntent
import org.skepsun.kototoro.details.ui.pager.ChaptersPagesViewModel
import org.skepsun.kototoro.details.ui.pager.bookmarks.BookmarksViewModel
import org.skepsun.kototoro.details.ui.pager.bookmarks.compose.BookmarksScreen

@Composable
fun BookmarksScreenRoot(
	activityViewModel: ChaptersPagesViewModel,
	router: AppRouter,
	context: Context,
	viewModel: BookmarksViewModel
) {
	val contentItems by viewModel.content.collectAsState(initial = emptyList())
	val selectedItemIds = remember { mutableStateListOf<Long>() }
	
	val mangaDetails by activityViewModel.mangaDetails.collectAsState(initial = null)
	LaunchedEffect(mangaDetails) {
		viewModel.emit(mangaDetails)
	}

	BookmarksScreen(
		items = contentItems,
		selectedItemIds = selectedItemIds.toSet(),
		onItemClick = { item ->
			if (selectedItemIds.isNotEmpty()) {
				val bookmark = item as org.skepsun.kototoro.bookmarks.domain.Bookmark
				if (selectedItemIds.contains(bookmark.pageId)) {
					selectedItemIds.remove(bookmark.pageId)
				} else {
					selectedItemIds.add(bookmark.pageId)
				}
			} else {
				val bookmark = item as org.skepsun.kototoro.bookmarks.domain.Bookmark
				// 之前逻辑中直接通过 Parent Callback 或 Router 启动
				router.openReader(
					ReaderIntent.Builder(context)
						.manga(bookmark.manga)
						.state(org.skepsun.kototoro.reader.ui.ReaderState(bookmark.chapterId, bookmark.page, bookmark.scroll))
						.build()
				)
			}
		},
		onItemLongClick = { item ->
			val bookmark = item as org.skepsun.kototoro.bookmarks.domain.Bookmark
			if (selectedItemIds.contains(bookmark.pageId)) {
				selectedItemIds.remove(bookmark.pageId)
			} else {
				selectedItemIds.add(bookmark.pageId)
			}
		},
		onSelectionActionClick = { actionId ->
			if (actionId == R.id.action_delete) {
				viewModel.removeBookmarks(selectedItemIds.toSet())
			}
			selectedItemIds.clear()
		},
		onClearSelection = { selectedItemIds.clear() }
	)
}
