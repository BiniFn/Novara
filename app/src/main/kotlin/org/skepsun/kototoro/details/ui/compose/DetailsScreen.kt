package org.skepsun.kototoro.details.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutManagerCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.LocalMangaSource
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.model.isLocal
import org.skepsun.kototoro.core.model.isNsfw
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.util.ext.isHttpUrl
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.details.ui.DetailsViewModel
import org.skepsun.kototoro.details.ui.model.ContentBranch
import org.skepsun.kototoro.details.ui.model.HistoryInfo
import org.skepsun.kototoro.details.ui.pager.bookmarks.BookmarksViewModel
import org.skepsun.kototoro.details.ui.pager.pages.PagesViewModel
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.reader.ui.PageSaveHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    pagesViewModel: PagesViewModel,
    bookmarksViewModel: BookmarksViewModel,
    settings: AppSettings,
    pageSaveHelper: PageSaveHelper,
    onBackClick: () -> Unit,
    onCoverBoundsSync: (Rect) -> Unit,
    onActionClick: (DetailsAction) -> Unit = {},
) {
    val mangaDetails by viewModel.mangaDetails.collectAsState()
    val remoteContent by viewModel.remoteContent.collectAsState()
    val favouriteCategories by viewModel.favouriteCategories.collectAsState()
    val historyInfo by viewModel.historyInfo.collectAsState()
    val branches by viewModel.branches.collectAsState()
    val translatedTitle by viewModel.translatedTitle.collectAsState()
    val translatedDescription by viewModel.translatedDescription.collectAsState()
    val isShowingTranslation by viewModel.isShowingTranslation.collectAsState()
    val hasTranslationCache by viewModel.hasTranslationCache.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()
    val isStatsAvailable by viewModel.isStatsAvailable.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val context = LocalContext.current
    val panoramaBlur by settings.observeAsState(AppSettings.KEY_PANORAMA_BLUR) { panoramaCoverBlur }
    val panoramaExtraHeight by settings.observeAsState(AppSettings.KEY_PANORAMA_EXTRA_HEIGHT) { panoramaCoverExtraHeight }
    val panoramaBottomAlpha by settings.observeAsState(AppSettings.KEY_PANORAMA_BOTTOM_GRADIENT_ALPHA) {
        panoramaBottomGradientAlpha
    }
    val content = mangaDetails?.toContent()
    val isShortcutSupported = remember(context) { ShortcutManagerCompat.isRequestPinShortcutSupported(context) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (settings.isPanoramaCoverEnabled) {
            val request = remember(mangaDetails) {
                ImageRequest.Builder(context)
                    .data(mangaDetails?.toContent()?.coverUrl)
                    .apply { mangaDetails?.toContent()?.let { mangaExtra(it) } }
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp + (panoramaExtraHeight ?: 50).dp)
                    .blur(radius = (((panoramaBlur ?: 35) / 100f) * 20f).dp)
                    .alpha(0.6f),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp + (panoramaExtraHeight ?: 50).dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(
                                    alpha = ((panoramaBottomAlpha ?: 100) / 100f).coerceIn(0f, 1f),
                                ),
                            ),
                        ),
                    ),
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                DetailsBottomBar(
                    contentType = content?.source?.getContentType(),
                    historyInfo = historyInfo,
                    branches = branches,
                    isLoading = isLoading,
                    onActionClick = onActionClick,
                )
            },
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onActionClick(DetailsAction.Share) }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.share),
                            )
                        }
                        IconButton(onClick = { onActionClick(DetailsAction.Download) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = stringResource(R.string.download),
                            )
                        }
                        DetailsOverflowMenu(
                            hasTranslationCache = hasTranslationCache,
                            isShowingTranslation = isShowingTranslation,
                            isTranslating = isTranslating,
                            isScrobblingAvailable = viewModel.isScrobblingAvailable,
                            isStatsAvailable = isStatsAvailable,
                            isBrowserAvailable = content?.publicUrl?.isHttpUrl() == true,
                            isAlternativesAvailable = content?.isLocal == false,
                            hasOnlineVariant = remoteContent != null,
                            isDeleteLocalAvailable = content?.source == LocalMangaSource,
                            isEditOverrideAvailable = content != null,
                            isShortcutSupported = isShortcutSupported && content != null,
                            isNsfw = content?.isNsfw() == true,
                            onActionClick = onActionClick,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { paddingValues ->
            val scrollState = rememberScrollState()
            val source = content?.source

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState),
            ) {
                DetailsHeader(
                    mangaDetails = mangaDetails,
                    favouriteCategories = favouriteCategories,
                    translatedTitle = translatedTitle,
                    translatedDescription = translatedDescription,
                    isShowingTranslation = isShowingTranslation,
                    hasTranslationCache = hasTranslationCache,
                    isTranslating = isTranslating,
                    onCoverBoundsSync = onCoverBoundsSync,
                    onFavoriteClick = { onActionClick(DetailsAction.Favorite) },
                    onSourceClick = { onActionClick(DetailsAction.OpenSource(it)) },
                    onAuthorClick = { author ->
                        source?.let { currentSource ->
                            onActionClick(DetailsAction.AuthorClick(author, currentSource))
                        }
                    },
                    onTagClick = { onActionClick(DetailsAction.TagClick(it)) },
                    onTranslateClick = { onActionClick(DetailsAction.Translate) },
                    onToggleTranslationClick = { onActionClick(DetailsAction.ToggleTranslation) },
                )
                Spacer(modifier = Modifier.height(240.dp))
            }
        }
    }
}

@Composable
private fun DetailsBottomBar(
    contentType: ContentType?,
    historyInfo: HistoryInfo,
    branches: List<ContentBranch>,
    isLoading: Boolean,
    onActionClick: (DetailsAction) -> Unit,
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        IconButton(onClick = { onActionClick(DetailsAction.ToggleList) }) {
            Icon(
                painter = painterResource(R.drawable.ic_list),
                contentDescription = stringResource(R.string.chapters),
            )
        }
        IconButton(onClick = { onActionClick(DetailsAction.ToggleGrid) }) {
            Icon(
                painter = painterResource(R.drawable.ic_grid),
                contentDescription = stringResource(R.string.pages),
            )
        }
        IconButton(onClick = { onActionClick(DetailsAction.ToggleBookmarkView) }) {
            Icon(
                painter = painterResource(R.drawable.ic_bookmark),
                contentDescription = stringResource(R.string.bookmarks),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        ReadDock(
            readLabel = resolveReadActionLabel(
                contentType = contentType,
                historyInfo = historyInfo,
                isLoading = isLoading,
            ),
            branches = branches,
            historyInfo = historyInfo,
            isDownloadAvailable = historyInfo.canDownload,
            isEnabled = !isLoading && historyInfo.isValid,
            onReadClick = { onActionClick(DetailsAction.Resume) },
            onIncognitoClick = { onActionClick(DetailsAction.ResumeIncognito) },
            onForgetClick = { onActionClick(DetailsAction.ForgetHistory) },
            onDownloadClick = { onActionClick(DetailsAction.Download) },
            onBranchSelected = { onActionClick(DetailsAction.SelectBranch(it)) },
        )
    }
}

sealed interface DetailsAction {
    data class OpenSource(val source: ContentSource) : DetailsAction
    data class AuthorClick(val author: String, val source: ContentSource) : DetailsAction
    data class TagClick(val tag: ContentTag) : DetailsAction
    data class SelectBranch(val branch: String?) : DetailsAction
    data object Favorite : DetailsAction
    data object Share : DetailsAction
    data object Download : DetailsAction
    data object Resume : DetailsAction
    data object ResumeIncognito : DetailsAction
    data object ForgetHistory : DetailsAction
    data object DeleteLocal : DetailsAction
    data object EditOverride : DetailsAction
    data object CreateShortcut : DetailsAction
    data object Translate : DetailsAction
    data object ToggleTranslation : DetailsAction
    data object FindSimilar : DetailsAction
    data object OpenAlternatives : DetailsAction
    data object OpenOnlineVariant : DetailsAction
    data object OpenInBrowser : DetailsAction
    data object OpenTracking : DetailsAction
    data object OpenStatistics : DetailsAction
    data object ToggleSafe : DetailsAction
    data object ToggleList : DetailsAction
    data object ToggleGrid : DetailsAction
    data object ToggleBookmarkView : DetailsAction
}

@Composable
private fun ReadDock(
    readLabel: String,
    branches: List<ContentBranch>,
    historyInfo: HistoryInfo,
    isDownloadAvailable: Boolean,
    isEnabled: Boolean,
    onReadClick: () -> Unit,
    onIncognitoClick: () -> Unit,
    onForgetClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onBranchSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedBranch = branches.firstOrNull { it.isSelected } ?: branches.firstOrNull()
    val hasBranchOptions = branches.size > 1
    val canOpenIncognito = !historyInfo.isIncognitoMode
    val canForgetHistory = historyInfo.history != null
    val hasQuickActions = canOpenIncognito || canForgetHistory || isDownloadAvailable
    val hasMenuActions = hasQuickActions || hasBranchOptions
    val menuLabel = if (hasBranchOptions) {
        selectedBranch?.name ?: stringResource(R.string.system_default)
    } else {
        stringResource(R.string.options)
    }

    Surface(
        modifier = Modifier.height(52.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 3.dp,
    ) {
        Box {
            Row {
                TextButton(
                    onClick = onReadClick,
                    enabled = isEnabled,
                    modifier = Modifier.height(52.dp),
                ) {
                    Text(
                        text = readLabel,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(1.dp)
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)),
                )
                TextButton(
                    onClick = { expanded = true },
                    enabled = hasMenuActions,
                    modifier = Modifier.height(52.dp),
                ) {
                    Text(
                        text = menuLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.options),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                if (canOpenIncognito) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.incognito_mode)) },
                        onClick = {
                            expanded = false
                            onIncognitoClick()
                        },
                    )
                }
                if (canForgetHistory) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_from_history)) },
                        onClick = {
                            expanded = false
                            onForgetClick()
                        },
                    )
                }
                if (isDownloadAvailable) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.download)) },
                        onClick = {
                            expanded = false
                            onDownloadClick()
                        },
                    )
                }
                if (hasQuickActions && hasBranchOptions) {
                    HorizontalDivider()
                }
                branches.forEach { branch ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = buildString {
                                    append(branch.name ?: stringResource(R.string.system_default))
                                    append(" / ")
                                    append(branch.count)
                                },
                            )
                        },
                        leadingIcon = {
                            if (branch.isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onBranchSelected(branch.name)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun resolveReadActionLabel(
    contentType: ContentType?,
    historyInfo: HistoryInfo,
    isLoading: Boolean,
): String {
    val isChaptersLoading = isLoading && (historyInfo.totalChapters <= 0 || historyInfo.isChapterMissing)
    val defaultReadRes = when (contentType) {
        ContentType.VIDEO, ContentType.HENTAI_VIDEO -> R.string.play
        else -> R.string.read
    }
    val continueRes = when (contentType) {
        ContentType.VIDEO, ContentType.HENTAI_VIDEO -> R.string._continue_play
        else -> R.string._continue
    }
    return stringResource(
        when {
            isChaptersLoading -> R.string.loading_
            historyInfo.isIncognitoMode -> R.string.incognito
            historyInfo.canContinue -> continueRes
            else -> defaultReadRes
        },
    )
}

@Composable
private fun DetailsOverflowMenu(
    hasTranslationCache: Boolean,
    isShowingTranslation: Boolean,
    isTranslating: Boolean,
    isScrobblingAvailable: Boolean,
    isStatsAvailable: Boolean,
    isBrowserAvailable: Boolean,
    isAlternativesAvailable: Boolean,
    hasOnlineVariant: Boolean,
    isDeleteLocalAvailable: Boolean,
    isEditOverrideAvailable: Boolean,
    isShortcutSupported: Boolean,
    isNsfw: Boolean,
    onActionClick: (DetailsAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (hasTranslationCache && isShowingTranslation) {
                                R.string.details_show_original
                            } else if (hasTranslationCache) {
                                R.string.details_show_translation
                            } else {
                                R.string.translate_title
                            },
                        ),
                    )
                },
                enabled = !isTranslating,
                onClick = {
                    expanded = false
                    onActionClick(
                        if (hasTranslationCache) {
                            DetailsAction.ToggleTranslation
                        } else {
                            DetailsAction.Translate
                        },
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (isNsfw) R.string.mark_as_safe else R.string.mark_as_nsfw)) },
                onClick = {
                    expanded = false
                    onActionClick(DetailsAction.ToggleSafe)
                },
            )
            if (isDeleteLocalAvailable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.DeleteLocal)
                    },
                )
            }
            if (isEditOverrideAvailable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.EditOverride)
                    },
                )
            }
            if (isShortcutSupported) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.create_shortcut)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.CreateShortcut)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.find_similar)) },
                onClick = {
                    expanded = false
                    onActionClick(DetailsAction.FindSimilar)
                },
            )
            if (isAlternativesAvailable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.alternatives)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.OpenAlternatives)
                    },
                )
            }
            if (hasOnlineVariant) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.online_variant)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.OpenOnlineVariant)
                    },
                )
            }
            if (isBrowserAvailable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_in_browser)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.OpenInBrowser)
                    },
                )
            }
            if (isScrobblingAvailable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tracking)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.OpenTracking)
                    },
                )
            }
            if (isStatsAvailable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.statistics)) },
                    onClick = {
                        expanded = false
                        onActionClick(DetailsAction.OpenStatistics)
                    },
                )
            }
        }
    }
}
