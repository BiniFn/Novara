package org.skepsun.kototoro.details.ui.pager.chapters.compose

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
	var qualityProbeResult by remember { mutableStateOf<ChaptersPagesViewModel.QualityProbeResult?>(null) }
	
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
		viewModel.onShowVideoQualityDialog.observeEvent(lifecycleOwner) { result ->
			qualityProbeResult = result
		}
		onDispose {}
	}

	qualityProbeResult?.let { result ->
		VideoQualityDialog(
			qualities = result.qualities,
			onDismissRequest = { qualityProbeResult = null },
			onConfirm = { quality ->
				qualityProbeResult = null
				router.askForDownloadOverMeteredNetwork { allow ->
					viewModel.download(result.snapshot, allow, quality)
				}
			},
		)
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

@Composable
private fun VideoQualityDialog(
	qualities: List<String>,
	onDismissRequest: () -> Unit,
	onConfirm: (String?) -> Unit,
) {
	val options = remember(qualities) { listOf<String?>(null) + qualities }
	var selectedIndex by remember(options) { mutableIntStateOf(0) }

	AlertDialog(
		onDismissRequest = onDismissRequest,
		title = { Text(text = stringResource(R.string.video_quality)) },
		text = {
			Column(modifier = Modifier.selectableGroup()) {
				options.forEachIndexed { index, option ->
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.selectable(
								selected = index == selectedIndex,
								onClick = { selectedIndex = index },
								role = Role.RadioButton,
							)
							.padding(vertical = 8.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						RadioButton(
							selected = index == selectedIndex,
							onClick = null,
						)
						Text(
							text = option ?: stringResource(R.string.system_default),
							style = MaterialTheme.typography.bodyLarge,
							modifier = Modifier.padding(start = 16.dp),
						)
					}
				}
			}
		},
		confirmButton = {
			TextButton(
				onClick = {
					onConfirm(options[selectedIndex])
				},
			) {
				Text(text = stringResource(R.string.download))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismissRequest) {
				Text(text = stringResource(android.R.string.cancel))
			}
		},
	)
}
