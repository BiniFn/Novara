package org.skepsun.kototoro.details.ui.pager.chapters.compose

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.ReaderIntent
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.details.ui.pager.ChaptersPagesViewModel
import org.skepsun.kototoro.details.ui.pager.chapters.ChapterGroupsManager
import org.skepsun.kototoro.details.ui.withVolumeHeaders
import org.skepsun.kototoro.local.ui.LocalChaptersRemoveService
import org.skepsun.kototoro.parsers.model.ContentType
import androidx.lifecycle.LifecycleOwner
import org.skepsun.kototoro.details.ui.pager.chapters.compose.ChaptersScreen

@Composable
fun ChaptersScreenRoot(
	viewModel: ChaptersPagesViewModel,
	router: AppRouter,
	context: Context,
	viewForSnackbar: View,
	lifecycleOwner: LifecycleOwner
) {
	val isGridView by viewModel.isChaptersInGridView.collectAsState(initial = false)
	val isLoading by viewModel.isLoading.collectAsState(initial = false)
	val quickFilter by viewModel.quickFilter.collectAsState(initial = emptyList())
	val emptyReason by viewModel.emptyReason.collectAsState(initial = null)
	val chapters by viewModel.chapters.collectAsState(initial = emptyList())
	
	val chaptersWithHeaders = remember(chapters) {
		chapters.withVolumeHeaders(context)
	}
	
	var groupsVersion by remember { mutableIntStateOf(0) }
	
	val groupsManager = remember { ChapterGroupsManager() }
	
	val collapsedChapters = remember(chaptersWithHeaders, groupsVersion) {
		groupsManager.applyCollapsedState(chaptersWithHeaders)
	}

	val selectedItemIds = remember { mutableStateListOf<Long>() }

	DisposableEffect(Unit) {
		val observer = viewModel.onShowVideoQualityDialog.observeEvent(lifecycleOwner) { result ->
			val options = listOf(context.getString(R.string.system_default)) + result.qualities
			MaterialAlertDialogBuilder(context)
				.setTitle(R.string.video_quality)
				.setItems(options.toTypedArray()) { _, which ->
					val quality = if (which == 0) null else options[which]
					router.askForDownloadOverMeteredNetwork { allow ->
						viewModel.download(result.snapshot, allow, quality)
					}
				}
				.show()
		}
		onDispose {}
	}

	ChaptersScreen(
		items = collapsedChapters,
		isGridView = isGridView,
		gridSpanCount = 2,
		selectedItemIds = selectedItemIds.toSet(),
		filterChips = quickFilter,
		isLoading = isLoading,
		emptyMessageResId = emptyReason?.msgResId,
		onItemClick = { item ->
			if (selectedItemIds.isNotEmpty()) {
				if (selectedItemIds.contains(item.chapter.id)) {
					selectedItemIds.remove(item.chapter.id)
				} else {
					selectedItemIds.add(item.chapter.id)
				}
			} else {
				// ChaptersPagesViewModel delegates chapter selection to reader here
				router.openReader(
					ReaderIntent.Builder(context)
						.manga(viewModel.getContentOrNull() ?: return@ChaptersScreen)
						.state(org.skepsun.kototoro.reader.ui.ReaderState(item.chapter.id, 0, 0))
						.build()
				)
			}
		},
		onItemLongClick = { item ->
			if (selectedItemIds.contains(item.chapter.id)) {
				selectedItemIds.remove(item.chapter.id)
			} else {
				selectedItemIds.add(item.chapter.id)
			}
		},
		onHeaderClick = { header ->
			if (header.isCollapsible) {
				groupsManager.toggleGroup(header.groupId)
				groupsVersion++
			}
		},
		onFilterChipClick = { chip ->
			val branch = chip.data as? org.skepsun.kototoro.list.domain.ListFilterOption.Branch
			if (branch != null) {
				viewModel.setSelectedBranch(branch.titleText)
			}
		},
		onSelectionActionClick = { actionId ->
			if (selectedItemIds.isEmpty()) return@ChaptersScreen
			when (actionId) {
				R.id.action_save -> {
					val manga = viewModel.mangaDetails.value?.toContent()
					if (manga?.source?.getContentType() == ContentType.VIDEO) {
						viewModel.probeAndDownload(selectedItemIds.toSet())
					} else {
						router.askForDownloadOverMeteredNetwork { allow ->
							viewModel.download(selectedItemIds.toSet(), allow)
						}
					}
				}
				R.id.action_delete -> {
					val manga = viewModel.getContentOrNull() ?: return@ChaptersScreen
					if (selectedItemIds.size == viewModel.chapters.value.size) {
						viewModel.deleteLocal()
					} else {
						LocalChaptersRemoveService.start(context, manga, selectedItemIds.toSet())
						try {
							Snackbar.make(viewForSnackbar, R.string.chapters_will_removed_background, Snackbar.LENGTH_LONG).show()
						} catch (e: IllegalArgumentException) {
							e.printStackTraceDebug()
							Toast.makeText(context, R.string.chapters_will_removed_background, Toast.LENGTH_SHORT).show()
						}
					}
				}
				R.id.action_mark_current -> {
					if (selectedItemIds.size == 1) {
						viewModel.markChapterAsCurrent(selectedItemIds.first())
					}
				}
			}
			selectedItemIds.clear()
		},
		onClearSelection = { selectedItemIds.clear() }
	)
}
