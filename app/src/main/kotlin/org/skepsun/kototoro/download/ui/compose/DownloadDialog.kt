package org.skepsun.kototoro.download.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.model.getSaveTitleResId
import org.skepsun.kototoro.core.model.getWholeWorkOptionResId
import org.skepsun.kototoro.core.prefs.DownloadFormat
import org.skepsun.kototoro.core.util.ext.joinToStringWithLimit
import org.skepsun.kototoro.download.ui.dialog.ChapterSelectOptions
import org.skepsun.kototoro.download.ui.dialog.DownloadDialogViewModel
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.util.format

private enum class SelectedMode {
    WHOLE_MANGA, WHOLE_BRANCH, FIRST_CHAPTERS, UNREAD_CHAPTERS
}

@Composable
fun DownloadDialog(
    mangaList: List<Content>,
    snackbarHostState: SnackbarHostState? = null,
    viewModel: DownloadDialogViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(mangaList) {
        viewModel.initialize(mangaList)
    }
    
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        viewModel.onScheduled.collect { event ->
            event?.consume { isStarted ->
                if (snackbarHostState != null) {
                    scope.launch {
                        val msg = if (isStarted) R.string.download_started else R.string.download_added
                        snackbarHostState.showSnackbar(context.getString(msg))
                    }
                }
                onDismiss()
            }
        }
    }

    val isOptionsLoading by viewModel.isOptionsLoading.collectAsState()
    val chaptersSelectOptions by viewModel.chaptersSelectOptions.collectAsState()
    val isVideoContent by viewModel.isVideoContent.collectAsState()
    val isVideoQualitiesLoading by viewModel.isVideoQualitiesLoading.collectAsState()
    val videoQualities by viewModel.videoQualities.collectAsState()
    val defaultFormat by viewModel.defaultFormat.collectAsState()
    val availableDestinations by viewModel.availableDestinations.collectAsState()

    var showMoreOptions by remember { mutableStateOf(false) }
    var startNow by remember { mutableStateOf(true) }
    var selectedMode by remember { mutableStateOf(SelectedMode.WHOLE_MANGA) }
    var isAlignReader by remember { mutableStateOf(viewModel.isDownloadAlignedWithReader()) }

    val firstManga = mangaList.firstOrNull()
    val contentType = firstManga?.source?.getContentType() ?: ContentType.MANGA

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(id = contentType.getSaveTitleResId()))
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                Text(
                    text = mangaList.joinToStringWithLimit(context, 120) { it.title },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Whole Manga
                SelectableListItem(
                    title = stringResource(id = contentType.getWholeWorkOptionResId()),
                    subtitle = if (chaptersSelectOptions.wholeContent.chaptersCount > 0) {
                        pluralStringResource(id = R.plurals.chapters, count = chaptersSelectOptions.wholeContent.chaptersCount, chaptersSelectOptions.wholeContent.chaptersCount)
                    } else null,
                    selected = selectedMode == SelectedMode.WHOLE_MANGA,
                    onClick = { selectedMode = SelectedMode.WHOLE_MANGA }
                )

                // Whole Branch
                if (chaptersSelectOptions.wholeBranch != null) {
                    val branchOptions = chaptersSelectOptions.wholeBranch!!
                    SelectableDropdownItem(
                        title = stringResource(id = R.string.download_option_all_chapters, branchOptions.selectedBranch ?: ""),
                        subtitle = if (branchOptions.chaptersCount > 0) {
                            pluralStringResource(id = R.plurals.chapters, count = branchOptions.chaptersCount, branchOptions.chaptersCount)
                        } else null,
                        selected = selectedMode == SelectedMode.WHOLE_BRANCH,
                        onClick = { selectedMode = SelectedMode.WHOLE_BRANCH },
                        onDropdownClick = {
                            val branches = branchOptions.branches.keys.toList()
                            if (branches.size > 1) {
                                // Real implementation would show dropdown
                            }
                        }
                    )
                }

                // First N Chapters
                if (chaptersSelectOptions.firstChapters != null) {
                    val firstOptions = chaptersSelectOptions.firstChapters!!
                    SelectableDropdownItem(
                        title = stringResource(id = R.string.download_option_first_n_chapters, pluralStringResource(id = R.plurals.chapters, count = firstOptions.chaptersCount, firstOptions.chaptersCount)),
                        subtitle = firstOptions.branch,
                        selected = selectedMode == SelectedMode.FIRST_CHAPTERS,
                        onClick = { selectedMode = SelectedMode.FIRST_CHAPTERS },
                        onDropdownClick = {
                            // Show counts menu
                        }
                    )
                }

                // Unread Chapters
                if (chaptersSelectOptions.unreadChapters != null) {
                    val unreadOptions = chaptersSelectOptions.unreadChapters!!
                    val title = if (unreadOptions.chaptersCount == Int.MAX_VALUE) {
                        stringResource(id = R.string.download_option_all_unread)
                    } else {
                        stringResource(id = R.string.download_option_next_unread_n_chapters, pluralStringResource(id = R.plurals.chapters, count = unreadOptions.chaptersCount, unreadOptions.chaptersCount))
                    }
                    SelectableDropdownItem(
                        title = title,
                        subtitle = null,
                        selected = selectedMode == SelectedMode.UNREAD_CHAPTERS,
                        onClick = { selectedMode = SelectedMode.UNREAD_CHAPTERS },
                        onDropdownClick = {
                            // Show counts
                        }
                    )
                }

                if (isOptionsLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                }

                if (mangaList.size == 1) {
                    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(id = R.drawable.ic_tap), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(text = stringResource(id = R.string.chapter_selection_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Start Switch
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(id = R.string.start_download), modifier = Modifier.weight(1f))
                    Switch(checked = startNow, onCheckedChange = { startNow = it })
                }

                // Video Quality
                if (isVideoContent) {
                    Text(text = stringResource(id = R.string.video_quality), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(
                            text = if (isVideoQualitiesLoading) stringResource(id = R.string.fetching_video_quality) else "System Default",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // More Options Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showMoreOptions = !showMoreOptions }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(id = R.string.more_options), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(
                        imageVector = if (showMoreOptions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, 
                        contentDescription = null
                    )
                }

                AnimatedVisibility(visible = showMoreOptions) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Destinations
                        Text(text = stringResource(id = R.string.destination_directory), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                        OutlinedCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(text = availableDestinations.firstOrNull { it.isChecked }?.title ?: "Default", modifier = Modifier.padding(16.dp))
                        }

                        // Format
                        Text(text = stringResource(id = R.string.preferred_download_format), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                        OutlinedCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(text = defaultFormat?.name ?: "Default", modifier = Modifier.padding(16.dp))
                        }

                        // Align reader
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(id = R.string.download_align_reader), style = MaterialTheme.typography.titleSmall)
                                Text(text = stringResource(id = R.string.download_align_reader_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isAlignReader,
                                onCheckedChange = { 
                                    isAlignReader = it 
                                    viewModel.setDownloadAlignedWithReader(it)
                                }
                            )
                        }

                        // Auto retry
                        var isAutoRetry by remember { mutableStateOf(viewModel.isDownloadAutoRetryEnabled()) }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = stringResource(id = R.string.download_auto_retry_summary), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = isAutoRetry,
                                onCheckedChange = { 
                                    isAutoRetry = it
                                    viewModel.setDownloadAutoRetryEnabled(it)
                                }
                            )
                        }

                        // Threads
                        var threads by remember { mutableStateOf(viewModel.getDownloadThreads().toFloat()) }
                        Text(text = stringResource(id = R.string.download_threads), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = threads,
                                onValueChange = { threads = it; viewModel.setDownloadThreads(it.toInt()) },
                                valueRange = 1f..10f,
                                steps = 9,
                                enabled = !isAlignReader,
                                modifier = Modifier.weight(1f)
                            )
                            Text(text = threads.toInt().toString(), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isOptionsLoading,
                onClick = {
                    viewModel.confirm(
                        startNow = startNow,
                        chaptersMacro = when (selectedMode) {
                            SelectedMode.WHOLE_MANGA -> chaptersSelectOptions.wholeContent
                            SelectedMode.WHOLE_BRANCH -> chaptersSelectOptions.wholeBranch!!
                            SelectedMode.FIRST_CHAPTERS -> chaptersSelectOptions.firstChapters!!
                            SelectedMode.UNREAD_CHAPTERS -> chaptersSelectOptions.unreadChapters!!
                        },
                        format = DownloadFormat.entries.getOrNull(0), // Needs fix for real mapping
                        destination = availableDestinations.firstOrNull { it.isChecked },
                        allowMetered = true,
                        preferredQuality = null
                    )
                }
            ) {
                Text(text = stringResource(id = R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun SelectableListItem(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SelectableDropdownItem(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onDropdownClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_expand_more),
            contentDescription = null,
            modifier = Modifier.clickable(onClick = onDropdownClick).padding(8.dp)
        )
    }
}
