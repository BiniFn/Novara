package org.skepsun.kototoro.details.ui.pager.chapters.compose

import android.content.Context
import android.view.View
import androidx.activity.compose.BackHandler
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
import androidx.lifecycle.LifecycleOwner
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.ReaderIntent
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.details.ui.model.ChapterListItem
import org.skepsun.kototoro.details.ui.pager.ChaptersPagesViewModel
import org.skepsun.kototoro.details.ui.pager.chapters.ChapterGroupsManager
import org.skepsun.kototoro.details.ui.withVolumeHeaders
import org.skepsun.kototoro.parsers.model.ContentType

@Composable
fun ChaptersScreenRoot(
	viewModel: ChaptersPagesViewModel,
	router: AppRouter,
	context: Context,
	viewForSnackbar: View,
	lifecycleOwner: LifecycleOwner,
	isScrollEnabled: Boolean = true,
    handleSelectionBackPressInternally: Boolean = true,
    onSelectionStateChange: (ChapterSelectionUiState?) -> Unit = {},
) {
	val isGridView by viewModel.isChaptersInGridView.collectAsState(initial = false)
	val isLoading by viewModel.isLoading.collectAsState(initial = false)
	val quickFilter by viewModel.quickFilter.collectAsState(initial = emptyList())
	val emptyReason by viewModel.emptyReason.collectAsState(initial = null)
	val chapters by viewModel.chapters.collectAsState(initial = emptyList())
	val selectedBranch by viewModel.selectedBranch.collectAsState(initial = null)
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
    val selectedIds = remember(selectedItemIds.toList()) {
        selectedItemIds.toSet()
    }
    val visibleChapterIds = remember(chapters) {
        chapters.mapTo(linkedSetOf()) { it.chapter.id }
    }
    val visibleSelectableIds = remember(collapsedChapters) {
        collapsedChapters
            .filterIsInstance<ChapterListItem>()
            .map { it.chapter.id }
    }
    val selectedItems = remember(chapters, selectedIds) {
        chapters.filter { it.chapter.id in selectedIds }
    }

    BackHandler(enabled = selectedIds.isNotEmpty() && handleSelectionBackPressInternally) {
        selectedItemIds.clear()
    }

	DisposableEffect(Unit) {
		viewModel.onShowVideoQualityDialog.observeEvent(lifecycleOwner) { result ->
			qualityProbeResult = result
		}
        onDispose {
            onSelectionStateChange(null)
        }
	}

	LaunchedEffect(chapters, quickFilter, selectedBranch) {
		if (chapters.isNotEmpty()) {
			return@LaunchedEffect
		}
		val branches = quickFilter.mapNotNull { chip ->
			(chip.data as? org.skepsun.kototoro.list.domain.ListFilterOption.Branch)?.titleText
		}
		if (branches.isNotEmpty() && selectedBranch !in branches) {
			viewModel.setSelectedBranch(branches.first())
		}
	}
    LaunchedEffect(visibleChapterIds) {
        selectedItemIds.retainAll(visibleChapterIds)
    }
    val handleSelectionAction: (Int) -> Unit = remember(
        context,
        router,
        selectedItemIds,
        viewForSnackbar,
        viewModel,
    ) {
        { actionId ->
            if (selectedIds.isEmpty()) {
                Unit
            } else {
                when (actionId) {
                    R.id.action_save -> {
                        val manga = viewModel.mangaDetails.value?.toContent()
                        if (manga?.source?.getContentType() == ContentType.VIDEO) {
                            viewModel.probeAndDownload(selectedIds)
                        } else {
                            router.askForDownloadOverMeteredNetwork { allow ->
                                viewModel.download(selectedIds, allow)
                            }
                        }
                    }

                    R.id.action_mark_current -> {
                        if (selectedIds.size == 1) {
                            viewModel.markChapterAsCurrent(selectedIds.first())
                        }
                    }
                }
                selectedItemIds.clear()
            }
        }
    }
    val selectionState = remember(selectedIds, selectedItems, visibleSelectableIds, handleSelectionAction) {
        if (selectedIds.isEmpty()) {
            null
        } else {
            ChapterSelectionUiState(
                selectedCount = selectedIds.size,
                canSelectAll = selectedIds.size < visibleSelectableIds.size,
                canDownload = selectedItems.any { !it.isDownloaded && !it.chapter.source.isLocal },
                isSingleSelection = selectedIds.size == 1,
                onClearSelection = { selectedItemIds.clear() },
                onSelectAll = {
                    selectedItemIds.clear()
                    selectedItemIds.addAll(visibleSelectableIds)
                },
                onDownload = { handleSelectionAction(R.id.action_save) },
                onMarkCurrent = { handleSelectionAction(R.id.action_mark_current) },
            )
        }
    }

    SideEffect {
        onSelectionStateChange(selectionState)
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
        isScrollEnabled = isScrollEnabled,
		gridSpanCount = 2,
		selectedItemIds = selectedIds,
		filterChips = emptyList(),
		isLoading = isLoading,
		emptyMessageResId = emptyReason?.msgResId,
		onItemClick = { item ->
			if (selectedIds.isNotEmpty()) {
				if (selectedIds.contains(item.chapter.id)) {
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
		onSelectionActionClick = handleSelectionAction,
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
