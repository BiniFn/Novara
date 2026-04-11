package org.skepsun.kototoro.details.ui.pager.pages.compose

import android.content.Context
import android.view.View
import androidx.compose.runtime.*

import com.google.android.material.snackbar.Snackbar
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.details.ui.pager.ChaptersPagesViewModel
import org.skepsun.kototoro.details.ui.pager.pages.PagesViewModel
import org.skepsun.kototoro.reader.ui.PageSaveHelper
import org.skepsun.kototoro.reader.ui.ReaderNavigationCallback
import androidx.lifecycle.LifecycleOwner

@Composable
fun PagesScreenRoot(
	activityViewModel: ChaptersPagesViewModel,
	router: AppRouter,
	context: Context,
	pageSaveHelper: PageSaveHelper,
	viewForSnackbar: View,
	lifecycleOwner: LifecycleOwner,
	viewModel: PagesViewModel
) {
	val thumbnails by viewModel.thumbnails.collectAsState(initial = emptyList())
	val isLoading by viewModel.isLoading.collectAsState(initial = false)
	val selectedItemIds = remember { mutableStateListOf<Long>() }

	val mangaDetails by activityViewModel.mangaDetails.collectAsState(initial = null)
	val readingState by activityViewModel.readingState.collectAsState(initial = null)
	val selectedBranch by activityViewModel.selectedBranch.collectAsState(initial = null)
	
	LaunchedEffect(mangaDetails, readingState, selectedBranch) {
		if (mangaDetails != null) {
			viewModel.updateState(PagesViewModel.State(mangaDetails!!, readingState, selectedBranch))
		}
	}

	DisposableEffect(Unit) {
		val observer = viewModel.onPageSaved.observeEvent(lifecycleOwner) { uris ->
			if (uris.isEmpty()) return@observeEvent
			if (uris.size == 1) {
				Snackbar.make(viewForSnackbar, R.string.page_saved, Snackbar.LENGTH_LONG).show()
			} else {
				Snackbar.make(
					viewForSnackbar,
					context.getString(R.string.pages_saved), // Fallback since pages_saved is a simple string, not plural
					Snackbar.LENGTH_LONG
				).show()
			}
		}
		onDispose {}
	}

	PagesScreen(
		items = thumbnails,
		selectedItemIds = selectedItemIds.toSet(),
		gridSpanCount = 3,
		emptyMessageResId = null,
		isLoading = isLoading,
		onItemClick = { item ->
			val thumbnail = item as org.skepsun.kototoro.details.ui.pager.pages.PageThumbnail
			if (selectedItemIds.isNotEmpty()) {
				if (selectedItemIds.contains(thumbnail.page.id)) {
					selectedItemIds.remove(thumbnail.page.id)
				} else {
					selectedItemIds.add(thumbnail.page.id)
				}
			} else {
				val manga = activityViewModel.getContentOrNull() ?: return@PagesScreen
				router.openReader(
					org.skepsun.kototoro.core.nav.ReaderIntent.Builder(context)
						.manga(manga)
						.state(org.skepsun.kototoro.reader.ui.ReaderState(thumbnail.page.chapterId, thumbnail.page.index, 0))
						.build()
				)
			}
		},
		onItemLongClick = { item ->
			val thumbnail = item as org.skepsun.kototoro.details.ui.pager.pages.PageThumbnail
			if (selectedItemIds.contains(thumbnail.page.id)) {
				selectedItemIds.remove(thumbnail.page.id)
			} else {
				selectedItemIds.add(thumbnail.page.id)
			}
		},
		onSelectionActionClick = { actionId ->
			if (selectedItemIds.isEmpty()) return@PagesScreen
			when (actionId) {
				R.id.action_save -> {
					val snapshot = thumbnails.filterIsInstance<org.skepsun.kototoro.details.ui.pager.pages.PageThumbnail>()
						.filter { it.page.id in selectedItemIds }.map { it.page }.toSet()
					viewModel.savePages(pageSaveHelper, snapshot)
				}
			}
			selectedItemIds.clear()
		},
		onClearSelection = { selectedItemIds.clear() }
	)
}
