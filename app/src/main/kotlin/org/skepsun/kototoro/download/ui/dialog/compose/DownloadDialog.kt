package org.skepsun.kototoro.download.ui.dialog.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.model.getSaveTitleResId
import org.skepsun.kototoro.core.model.getWholeWorkOptionResId
import org.skepsun.kototoro.core.prefs.DownloadFormat
import org.skepsun.kototoro.core.util.ext.joinToStringWithLimit
import org.skepsun.kototoro.download.ui.dialog.ChapterSelectOptions
import org.skepsun.kototoro.download.ui.dialog.ChaptersSelectMacro
import org.skepsun.kototoro.download.ui.dialog.DownloadDialogViewModel
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.util.format
import org.skepsun.kototoro.settings.storage.DirectoryModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDialog(
    viewModel: DownloadDialogViewModel,
    onDismissRequest: () -> Unit,
    onConfirm: (Boolean) -> Unit, // passes allowMeteredNetwork
) {
    val manga = viewModel.manga
    val contentType = manga.firstOrNull()?.source?.getContentType() ?: ContentType.MANGA
    val options by viewModel.chaptersSelectOptions.collectAsState()
    val isOptionsLoading by viewModel.isOptionsLoading.collectAsState()
    val videoQualities by viewModel.videoQualities.collectAsState()
    val isVideoContent by viewModel.isVideoContent.collectAsState()
    val isVideoQualitiesLoading by viewModel.isVideoQualitiesLoading.collectAsState()
    val availableDestinations by viewModel.availableDestinations.collectAsState()
    val defaultFormat by viewModel.defaultFormat.collectAsState()

    var selectedOption by remember { mutableStateOf<Int>(0) } // 0: Whole, 1: Branch, 2: First, 3: Unread
    var showMoreOptions by remember { mutableStateOf(false) }
    var startNow by remember { mutableStateOf(true) }
    
    var selectedFormatIndex by remember(defaultFormat) { 
        mutableIntStateOf(defaultFormat?.ordinal ?: 0) 
    }
    var selectedDestinationIndex by remember(availableDestinations) {
        mutableIntStateOf(availableDestinations.indexOfFirst { it.isChecked }.coerceAtLeast(0))
    }
    var selectedVideoQualityIndex by remember { mutableIntStateOf(0) }

    // Advanced settings state
    var alignReader by remember { mutableStateOf(viewModel.isDownloadAlignedWithReader()) }
    var autoRetry by remember { mutableStateOf(viewModel.isDownloadAutoRetryEnabled()) }
    var delayValue by remember { mutableIntStateOf(viewModel.getChapterDownloadDelay()) }
    var threadsValue by remember { mutableIntStateOf(viewModel.getDownloadThreads()) }
    var requestDelayValue by remember { mutableIntStateOf(viewModel.getDownloadRequestDelayMs()) }
    var retryCountValue by remember { mutableIntStateOf(viewModel.getDownloadRetryCount()) }
    var retryDelayValue by remember { mutableIntStateOf(viewModel.getDownloadRetryDelayMs()) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        title = {
            Text(stringResource(contentType.getSaveTitleResId()))
        },
        text = {
            val context = LocalContext.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = manga.joinToStringWithLimit(context, 120) { it.title },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (isOptionsLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                }

                // Options
                DownloadOptionItem(
                    title = stringResource(contentType.getWholeWorkOptionResId()),
                    subtitle = if (options.wholeContent.chaptersCount > 0) {
                        contextResource().getQuantityString(
                            R.plurals.chapters,
                            options.wholeContent.chaptersCount,
                            options.wholeContent.chaptersCount
                        )
                    } else null,
                    selected = selectedOption == 0,
                    onClick = { selectedOption = 0 }
                )

                options.wholeBranch?.let { branchOption ->
                    var showBranchMenu by remember { mutableStateOf(false) }
                    DownloadOptionItem(
                        title = stringResource(R.string.download_option_all_chapters, branchOption.selectedBranch ?: ""),
                        subtitle = if (branchOption.chaptersCount > 0) {
                            contextResource().getQuantityString(
                                R.plurals.chapters,
                                branchOption.chaptersCount,
                                branchOption.chaptersCount
                            )
                        } else null,
                        selected = selectedOption == 1,
                        hasAction = branchOption.branches.size > 1,
                        onActionClick = { showBranchMenu = true },
                        onClick = { selectedOption = 1 }
                    )
                    
                    if (showBranchMenu) {
                        val branches = branchOption.branches.keys.toList()
                        DropdownMenu(
                            expanded = showBranchMenu,
                            onDismissRequest = { showBranchMenu = false }
                        ) {
                            branches.forEach { branch ->
                                DropdownMenuItem(
                                    text = { Text(branch ?: stringResource(R.string.unknown)) },
                                    onClick = {
                                        viewModel.setSelectedBranch(branch)
                                        showBranchMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                options.firstChapters?.let { firstOption ->
                    var showCountMenu by remember { mutableStateOf(false) }
                    DownloadOptionItem(
                        title = stringResource(
                            R.string.download_option_first_n_chapters,
                            contextResource().getQuantityString(
                                R.plurals.chapters,
                                firstOption.chaptersCount,
                                firstOption.chaptersCount
                            )
                        ),
                        subtitle = firstOption.branch,
                        selected = selectedOption == 2,
                        hasAction = true,
                        onActionClick = { showCountMenu = true },
                        onClick = { selectedOption = 2 }
                    )

                    if (showCountMenu) {
                        DropdownMenu(
                            expanded = showCountMenu,
                            onDismissRequest = { showCountMenu = false }
                        ) {
                            generateChapterCountSequence(firstOption.maxAvailableCount).forEach { count ->
                                DropdownMenuItem(
                                    text = { Text(count.toString()) },
                                    onClick = {
                                        viewModel.setFirstChaptersCount(count)
                                        showCountMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                options.unreadChapters?.let { unreadOption ->
                    var showCountMenu by remember { mutableStateOf(false) }
                    DownloadOptionItem(
                        title = if (unreadOption.chaptersCount == Int.MAX_VALUE) {
                            stringResource(R.string.download_option_all_unread)
                        } else {
                            stringResource(
                                R.string.download_option_next_unread_n_chapters,
                                contextResource().getQuantityString(
                                    R.plurals.chapters,
                                    unreadOption.chaptersCount,
                                    unreadOption.chaptersCount
                                )
                            )
                        },
                        subtitle = null,
                        selected = selectedOption == 3,
                        hasAction = true,
                        onActionClick = { showCountMenu = true },
                        onClick = { selectedOption = 3 }
                    )

                    if (showCountMenu) {
                        DropdownMenu(
                            expanded = showCountMenu,
                            onDismissRequest = { showCountMenu = false }
                        ) {
                            generateChapterCountSequence(unreadOption.maxAvailableCount).forEach { count ->
                                DropdownMenuItem(
                                    text = { Text(count.toString()) },
                                    onClick = {
                                        viewModel.setUnreadChaptersCount(count)
                                        showCountMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chapters_all)) },
                                onClick = {
                                    viewModel.setUnreadChaptersCount(Int.MAX_VALUE)
                                    showCountMenu = false
                                }
                            )
                        }
                    }
                }

                if (manga.size == 1) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_tap),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.chapter_selection_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { startNow = !startNow }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.start_download),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = startNow, onCheckedChange = { startNow = it })
                }

                if (isVideoContent) {
                    Text(
                        text = stringResource(R.string.video_quality),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    VideoQualitySpinner(
                        isLoading = isVideoQualitiesLoading,
                        qualities = videoQualities,
                        selectedIndex = selectedVideoQualityIndex,
                        onSelectedIndexChange = { selectedVideoQualityIndex = it }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMoreOptions = !showMoreOptions }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.more_options),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (showMoreOptions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                }

                AnimatedVisibility(visible = showMoreOptions) {
                    Column {
                        // Destination
                        Text(
                            text = stringResource(R.string.destination_directory),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        ExposedDropdownMenuBox(
                            expanded = false, // Simplified for now
                            onExpandedChange = {}
                        ) {
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = availableDestinations.getOrNull(selectedDestinationIndex)?.let {
                                        it.title ?: if (it.titleRes != 0) stringResource(it.titleRes) else it.file?.name
                                    } ?: "",
                                    modifier = Modifier.padding(12.dp).fillMaxWidth()
                                )
                            }
                        }

                        // Format
                        Text(
                            text = stringResource(R.string.preferred_download_format),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.array.download_formats).split(",").getOrNull(selectedFormatIndex) ?: "",
                                modifier = Modifier.padding(12.dp).fillMaxWidth()
                            )
                        }

                        // Advanced sliders and toggles
                        AdvancedSettings(
                            alignReader = alignReader,
                            onAlignReaderChange = { 
                                alignReader = it
                                viewModel.setDownloadAlignedWithReader(it)
                            },
                            autoRetry = autoRetry,
                            onAutoRetryChange = {
                                autoRetry = it
                                viewModel.setDownloadAutoRetryEnabled(it)
                            },
                            delayValue = delayValue,
                            onDelayChange = {
                                delayValue = it
                                viewModel.setChapterDownloadDelay(it)
                            },
                            threadsValue = threadsValue,
                            onThreadsChange = {
                                threadsValue = it
                                viewModel.setDownloadThreads(it)
                            },
                            requestDelayValue = requestDelayValue,
                            onRequestDelayChange = {
                                requestDelayValue = it
                                viewModel.setDownloadRequestDelayMs(it)
                            },
                            retryCountValue = retryCountValue,
                            onRetryCountChange = {
                                retryCountValue = it
                                viewModel.setDownloadRetryCount(it)
                            },
                            retryDelayValue = retryDelayValue,
                            onRetryDelayChange = {
                                retryDelayValue = it
                                viewModel.setDownloadRetryDelayMs(it)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val macro = when (selectedOption) {
                    0 -> options.wholeContent
                    1 -> options.wholeBranch ?: options.wholeContent
                    2 -> options.firstChapters ?: options.wholeContent
                    3 -> options.unreadChapters ?: options.wholeContent
                    else -> options.wholeContent
                }
                val preferredQuality = if (isVideoContent && selectedVideoQualityIndex > 0) {
                    videoQualities?.getOrNull(selectedVideoQualityIndex - 1)
                } else null

                // Here we usually ask for metered network, but let's assume it's handled or passed
                onConfirm(true) // Temp: always allow for now, logic can be added
                
                viewModel.confirm(
                    startNow = startNow,
                    chaptersMacro = macro,
                    format = DownloadFormat.entries.getOrNull(selectedFormatIndex),
                    destination = availableDestinations.getOrNull(selectedDestinationIndex),
                    allowMetered = true, // To be refined
                    preferredQuality = preferredQuality
                )
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun DownloadOptionItem(
    title: String,
    subtitle: String?,
    selected: Boolean,
    hasAction: Boolean = false,
    onActionClick: () -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (hasAction) {
            IconButton(onClick = onActionClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more), // Use appropriate icon
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoQualitySpinner(
    isLoading: Boolean,
    qualities: List<String>?,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (isLoading) {
            Text(
                text = stringResource(R.string.fetching_video_quality),
                modifier = Modifier.padding(12.dp)
            )
        } else {
            val entries = remember(qualities) {
                listOf(null) + (qualities ?: emptyList())
            }
            // Simplified spinner logic
            Text(
                text = entries.getOrNull(selectedIndex) ?: stringResource(R.string.system_default),
                modifier = Modifier.padding(12.dp).fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AdvancedSettings(
    alignReader: Boolean,
    onAlignReaderChange: (Boolean) -> Unit,
    autoRetry: Boolean,
    onAutoRetryChange: (Boolean) -> Unit,
    delayValue: Int,
    onDelayChange: (Int) -> Unit,
    threadsValue: Int,
    onThreadsChange: (Int) -> Unit,
    requestDelayValue: Int,
    onRequestDelayChange: (Int) -> Unit,
    retryCountValue: Int,
    onRetryCountChange: (Int) -> Unit,
    retryDelayValue: Int,
    onRetryDelayChange: (Int) -> Unit
) {
    Column {
        SettingSwitch(
            title = stringResource(R.string.download_align_reader),
            checked = alignReader,
            onCheckedChange = onAlignReaderChange
        )
        SettingSwitch(
            title = stringResource(R.string.download_auto_retry_summary),
            checked = autoRetry,
            onCheckedChange = onAutoRetryChange
        )
        
        SettingSlider(
            title = stringResource(R.string.download_threads),
            value = threadsValue.toFloat(),
            range = 1f..10f,
            steps = 9,
            displayValue = threadsValue.toString(),
            enabled = !alignReader,
            onValueChange = { onThreadsChange(it.toInt()) }
        )

        SettingSlider(
            title = stringResource(R.string.download_request_delay),
            value = requestDelayValue.toFloat(),
            range = 0f..5000f,
            steps = 49,
            displayValue = "${requestDelayValue}ms",
            enabled = !alignReader,
            onValueChange = { onRequestDelayChange(it.toInt()) }
        )

        SettingSlider(
            title = stringResource(R.string.download_retry_count),
            value = retryCountValue.toFloat(),
            range = 1f..10f,
            steps = 9,
            displayValue = retryCountValue.toString(),
            onValueChange = { onRetryCountChange(it.toInt()) }
        )

        SettingSlider(
            title = stringResource(R.string.download_retry_delay),
            value = retryDelayValue.toFloat(),
            range = 500f..10000f,
            steps = 19,
            displayValue = "${retryDelayValue}ms",
            onValueChange = { onRetryDelayChange(it.toInt()) }
        )

        SettingSlider(
            title = stringResource(R.string.chapter_download_delay),
            value = delayValue.toFloat(),
            range = 0f..10f,
            steps = 10,
            displayValue = "${delayValue}s",
            onValueChange = { onDelayChange(it.toInt()) }
        )
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    displayValue: String,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).alpha(if (enabled) 1f else 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(displayValue, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            enabled = enabled
        )
    }
}

private fun generateChapterCountSequence(max: Int) = sequence {
    yield(1)
    var seed = 5
    var step = 5
    while (seed + step <= max) {
        yield(seed)
        step = when {
            seed < 20 -> 5
            seed < 60 -> 10
            else -> 20
        }
        seed += step
    }
    if (seed < max) {
        yield(max)
    }
}.toList()

@Composable
private fun contextResource() = androidx.compose.ui.platform.LocalContext.current.resources

// Helper to use alpha from modifier context
// Removed custom alpha extension
