package org.skepsun.kototoro.details.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.BaseActivity
import org.skepsun.kototoro.details.ui.pager.ChaptersPagesViewModel
import org.skepsun.kototoro.details.ui.pager.bookmarks.BookmarksViewModel
import org.skepsun.kototoro.details.ui.pager.bookmarks.compose.BookmarksScreenRoot
import org.skepsun.kototoro.details.ui.pager.chapters.compose.ChaptersScreenRoot
import org.skepsun.kototoro.details.ui.pager.pages.PagesViewModel
import org.skepsun.kototoro.details.ui.pager.pages.compose.PagesScreenRoot
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.reader.ui.PageSaveHelper

const val DETAILS_TAB_CHAPTERS = 0
const val DETAILS_TAB_PAGES = 1
const val DETAILS_TAB_BOOKMARKS = 2

private data class DetailsTabSpec(
	val tabId: Int,
	val titleResId: Int,
	val iconResId: Int,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChaptersPagesTabsContent(
	viewModel: ChaptersPagesViewModel,
	pagesViewModel: PagesViewModel,
	bookmarksViewModel: BookmarksViewModel,
	settings: AppSettings,
	pageSaveHelper: PageSaveHelper,
	initialPage: Int = 0,
	selectedTabId: Int? = null,
	onSelectedTabIdChange: ((Int) -> Unit)? = null,
) {
	val mangaDetails by viewModel.mangaDetails.collectAsState()
	val source = mangaDetails?.toContent()?.source
	val contentType = source?.getContentType()
	val isChaptersReversed by viewModel.isChaptersReversed.collectAsState(initial = false)
	val isChaptersInGridView by viewModel.isChaptersInGridView.collectAsState(initial = false)
	val isDownloadedOnly by viewModel.isDownloadedOnly.collectAsState(initial = false)
	val emptyReason by viewModel.emptyReason.collectAsState(initial = null)
	val pagesGridScale by pagesViewModel.gridScale.collectAsState(initial = settings.gridSizePages / 100f)

	val isNovel = contentType == ContentType.NOVEL || contentType == ContentType.HENTAI_NOVEL
	val isVideo = contentType == ContentType.VIDEO || contentType == ContentType.HENTAI_VIDEO

	val isPagesTabEnabled = settings.isPagesTabEnabled && !isNovel && !isVideo
	val isBookmarksTabEnabled = !isVideo
	val isDownloadedFilterVisible = mangaDetails?.local != null

	val tabsList = remember(isPagesTabEnabled, isBookmarksTabEnabled) {
		buildList {
			add(DetailsTabSpec(tabId = DETAILS_TAB_CHAPTERS, titleResId = R.string.chapters, iconResId = R.drawable.ic_list))
			if (isPagesTabEnabled) {
				add(DetailsTabSpec(tabId = DETAILS_TAB_PAGES, titleResId = R.string.pages, iconResId = R.drawable.ic_grid))
			}
			if (isBookmarksTabEnabled) {
				add(DetailsTabSpec(tabId = DETAILS_TAB_BOOKMARKS, titleResId = R.string.bookmarks, iconResId = R.drawable.ic_bookmark))
			}
		}
	}

	val context = LocalContext.current
	val activity = context as? BaseActivity<*>
	val router = activity?.router
	val viewForSnackbar = LocalView.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val coroutineScope = rememberCoroutineScope()
	var chapterQuery by rememberSaveable { mutableStateOf("") }
	var gridSizeValue by remember { mutableFloatStateOf(settings.gridSizePages.toFloat()) }

	LaunchedEffect(pagesGridScale) {
		gridSizeValue = (pagesGridScale * 100f).coerceIn(50f, 150f)
	}

	Surface(modifier = Modifier.fillMaxSize()) {
		Column(modifier = Modifier.fillMaxSize()) {
			if (tabsList.isEmpty()) return@Column

			val requestedIndex = selectedTabId?.let { requestedId ->
				tabsList.indexOfFirst { it.tabId == requestedId }.takeIf { it >= 0 }
			}
			val validInitialPage = (requestedIndex ?: initialPage).coerceIn(0, tabsList.lastIndex)
			val pagerState = rememberPagerState(
				initialPage = validInitialPage,
				pageCount = { tabsList.size },
			)
			val currentTabId = tabsList[pagerState.currentPage].tabId

			LaunchedEffect(selectedTabId, tabsList) {
				if (selectedTabId == null) {
					return@LaunchedEffect
				}
				val targetIndex = tabsList.indexOfFirst { it.tabId == selectedTabId }
				if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
					pagerState.scrollToPage(targetIndex)
				}
			}

			LaunchedEffect(pagerState.currentPage, tabsList) {
				onSelectedTabIdChange?.invoke(tabsList[pagerState.currentPage].tabId)
			}

			LaunchedEffect(currentTabId) {
				if (currentTabId != DETAILS_TAB_CHAPTERS && chapterQuery.isNotEmpty()) {
					chapterQuery = ""
					viewModel.performChapterSearch(null)
				}
			}

			if (tabsList.size > 1) {
				TabRow(
					selectedTabIndex = pagerState.currentPage,
					containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.72f),
				) {
					tabsList.forEachIndexed { index, tab ->
						Tab(
							selected = pagerState.currentPage == index,
							onClick = {
								coroutineScope.launch {
									pagerState.animateScrollToPage(index)
								}
							},
							icon = {
								Icon(
									painter = painterResource(tab.iconResId),
									contentDescription = stringResource(tab.titleResId),
								)
							},
						)
					}
				}
			}

			ChaptersPagesToolbar(
				currentTabId = currentTabId,
				chapterQuery = chapterQuery,
				onChapterQueryChange = { query ->
					chapterQuery = query
					viewModel.performChapterSearch(query.ifBlank { null })
				},
				isSearchVisible = emptyReason == null,
				isChaptersReversed = isChaptersReversed,
				onToggleReversed = { viewModel.setChaptersReversed(!isChaptersReversed) },
				isChaptersInGridView = isChaptersInGridView,
				onToggleGridView = { viewModel.setChaptersInGridView(!isChaptersInGridView) },
				isDownloadedFilterVisible = isDownloadedFilterVisible,
				isDownloadedOnly = isDownloadedOnly,
				onToggleDownloadedOnly = { viewModel.isDownloadedOnly.value = !isDownloadedOnly },
				gridSizeValue = gridSizeValue,
				onGridSizeChange = { value ->
					gridSizeValue = value
					settings.gridSizePages = value.toInt()
				},
			)

			HorizontalPager(
				state = pagerState,
				modifier = Modifier.weight(1f).fillMaxWidth(),
			) { page ->
				if (router == null) return@HorizontalPager

				when (tabsList[page].tabId) {
					DETAILS_TAB_CHAPTERS -> ChaptersScreenRoot(
						viewModel = viewModel,
						router = router,
						context = context,
						viewForSnackbar = viewForSnackbar,
						lifecycleOwner = lifecycleOwner,
					)
					DETAILS_TAB_PAGES -> PagesScreenRoot(
						activityViewModel = viewModel,
						router = router,
						context = context,
						pageSaveHelper = pageSaveHelper,
						viewForSnackbar = viewForSnackbar,
						lifecycleOwner = lifecycleOwner,
						viewModel = pagesViewModel,
					)
					DETAILS_TAB_BOOKMARKS -> BookmarksScreenRoot(
						activityViewModel = viewModel,
						router = router,
						context = context,
						viewModel = bookmarksViewModel,
					)
				}
			}
		}
	}
}

@Composable
private fun ChaptersPagesToolbar(
	currentTabId: Int,
	chapterQuery: String,
	onChapterQueryChange: (String) -> Unit,
	isSearchVisible: Boolean,
	isChaptersReversed: Boolean,
	onToggleReversed: () -> Unit,
	isChaptersInGridView: Boolean,
	onToggleGridView: () -> Unit,
	isDownloadedFilterVisible: Boolean,
	isDownloadedOnly: Boolean,
	onToggleDownloadedOnly: () -> Unit,
	gridSizeValue: Float,
	onGridSizeChange: (Float) -> Unit,
) {
	Column(modifier = Modifier.fillMaxWidth()) {
		when (currentTabId) {
			DETAILS_TAB_CHAPTERS -> {
				if (isSearchVisible) {
					OutlinedTextField(
						value = chapterQuery,
						onValueChange = onChapterQueryChange,
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 16.dp, vertical = 12.dp),
						singleLine = true,
						label = { Text(stringResource(R.string.search_chapters)) },
					)
				}
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 8.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					FilterChip(
						selected = isChaptersReversed,
						onClick = onToggleReversed,
						label = { Text(stringResource(R.string.reverse)) },
					)
					FilterChip(
						selected = isChaptersInGridView,
						onClick = onToggleGridView,
						label = { Text(stringResource(R.string.chapters_grid_view)) },
					)
					if (isDownloadedFilterVisible) {
						FilterChip(
							selected = isDownloadedOnly,
							onClick = onToggleDownloadedOnly,
							label = { Text(stringResource(R.string.downloaded)) },
						)
					}
				}
			}

			DETAILS_TAB_PAGES,
			DETAILS_TAB_BOOKMARKS -> {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 12.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Text(
						text = stringResource(R.string.grid_size),
						style = MaterialTheme.typography.labelLarge,
					)
					Slider(
						value = gridSizeValue,
						onValueChange = onGridSizeChange,
						valueRange = 50f..150f,
					)
				}
			}
		}
		HorizontalDivider()
	}
}
