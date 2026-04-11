package org.skepsun.kototoro.details.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChaptersPagesTabsContent(
	viewModel: ChaptersPagesViewModel,
	pagesViewModel: PagesViewModel,
	bookmarksViewModel: BookmarksViewModel,
	settings: AppSettings,
	pageSaveHelper: PageSaveHelper,
	initialPage: Int = 0
) {
	val mangaDetails by viewModel.mangaDetails.collectAsState()
	val source = mangaDetails?.toContent()?.source
	val contentType = source?.getContentType()
	
	val isNovel = contentType == ContentType.NOVEL || contentType == ContentType.HENTAI_NOVEL
	val isVideo = contentType == ContentType.VIDEO || contentType == ContentType.HENTAI_VIDEO

	val isPagesTabEnabled = settings.isPagesTabEnabled && !isNovel && !isVideo
	val isBookmarksTabEnabled = !isVideo

	val tabsList = remember(isPagesTabEnabled, isBookmarksTabEnabled) {
		buildList {
			add(R.string.chapters)
			if (isPagesTabEnabled) add(R.string.pages)
			if (isBookmarksTabEnabled) add(R.string.bookmarks)
		}
	}

	val context = LocalContext.current
	val activity = context as? BaseActivity<*>
	val router = activity?.router
	val viewForSnackbar = LocalView.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val coroutineScope = rememberCoroutineScope()

	Surface(modifier = Modifier.fillMaxSize()) {
		Column(modifier = Modifier.fillMaxSize()) {
			if (tabsList.isEmpty()) return@Column

			val validInitialPage = initialPage.coerceIn(0, tabsList.lastIndex)
			val pagerState = rememberPagerState(
				initialPage = validInitialPage,
				pageCount = { tabsList.size }
			)

			if (tabsList.size > 1) {
				TabRow(
					selectedTabIndex = pagerState.currentPage,
					containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
				) {
					tabsList.forEachIndexed { index, titleRes ->
						Tab(
							selected = pagerState.currentPage == index,
							onClick = {
								coroutineScope.launch {
									pagerState.animateScrollToPage(index)
								}
							},
							text = { Text(stringResource(id = titleRes)) }
						)
					}
				}
			}

			HorizontalPager(
				state = pagerState,
				modifier = Modifier.weight(1f).fillMaxWidth()
			) { page ->
				if (router == null) return@HorizontalPager

				when (tabsList[page]) {
					R.string.chapters -> ChaptersScreenRoot(
						viewModel = viewModel,
						router = router,
						context = context,
						viewForSnackbar = viewForSnackbar,
						lifecycleOwner = lifecycleOwner
					)
					R.string.pages -> PagesScreenRoot(
						activityViewModel = viewModel,
						router = router,
						context = context,
						pageSaveHelper = pageSaveHelper,
						viewForSnackbar = viewForSnackbar,
						lifecycleOwner = lifecycleOwner,
						viewModel = pagesViewModel
					)
					R.string.bookmarks -> BookmarksScreenRoot(
						activityViewModel = viewModel,
						router = router,
						context = context,
						viewModel = bookmarksViewModel
					)
				}
			}
		}
	}
}
