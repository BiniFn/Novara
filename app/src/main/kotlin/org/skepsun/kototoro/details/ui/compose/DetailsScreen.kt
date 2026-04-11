package org.skepsun.kototoro.details.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import org.skepsun.kototoro.details.ui.DetailsViewModel
import org.skepsun.kototoro.details.ui.pager.bookmarks.BookmarksViewModel
import org.skepsun.kototoro.details.ui.pager.bookmarks.compose.BookmarksScreenRoot
import org.skepsun.kototoro.details.ui.pager.chapters.compose.ChaptersScreenRoot
import org.skepsun.kototoro.details.ui.pager.pages.PagesViewModel
import org.skepsun.kototoro.details.ui.pager.pages.compose.PagesScreenRoot
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.reader.ui.PageSaveHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DetailsScreen(
	viewModel: DetailsViewModel,
	pagesViewModel: PagesViewModel,
	bookmarksViewModel: BookmarksViewModel,
	settings: AppSettings,
	pageSaveHelper: PageSaveHelper,
	onBackClick: () -> Unit,
	onCoverBoundsSync: (Rect) -> Unit,
	onActionClick: (DetailsAction) -> Unit = {}
) {
	val bottomSheetState = rememberStandardBottomSheetState(
		initialValue = SheetValue.PartiallyExpanded,
		skipHiddenState = true
	)
	val scaffoldState = rememberBottomSheetScaffoldState(
		bottomSheetState = bottomSheetState
	)
	
	val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

	val mangaDetails by viewModel.mangaDetails.collectAsState()

	val source = mangaDetails?.toContent()?.source
	val contentType = source?.getContentType()
	
	val isNovel = contentType == ContentType.NOVEL || contentType == ContentType.HENTAI_NOVEL
	val isVideo = contentType == ContentType.VIDEO || contentType == ContentType.HENTAI_VIDEO

	val isPagesTabEnabled = settings.isPagesTabEnabled && !isNovel && !isVideo
	val isBookmarksTabEnabled = !isVideo

	val context = LocalContext.current
	
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
					.height(400.dp)
					.blur(radius = 24.dp)
					.alpha(0.6f)
			)
			// Optional: scrim gradient to fade it out gracefully into surface color
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(400.dp)
					.background(
						Brush.verticalGradient(
							colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface)
						)
					)
			)
		}

		BottomSheetScaffold(
			scaffoldState = scaffoldState,
			containerColor = Color.Transparent, // Let panorama bleed through
			modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
			sheetPeekHeight = 400.dp,
			sheetContent = {
				ChaptersPagesTabsContent(
					viewModel = viewModel,
					pagesViewModel = pagesViewModel,
					bookmarksViewModel = bookmarksViewModel,
					settings = settings,
					pageSaveHelper = pageSaveHelper
				)
			},
			topBar = {
				TopAppBar(
					title = { },
					navigationIcon = {
						IconButton(onClick = onBackClick) {
							Icon(
								imageVector = Icons.AutoMirrored.Filled.ArrowBack,
								contentDescription = "Back"
							)
						}
					},
					colors = TopAppBarDefaults.topAppBarColors(
						containerColor = Color.Transparent
					)
				)
			},
			content = { paddingValues ->
				val scrollState = rememberScrollState()
				Column(
					modifier = Modifier
						.fillMaxSize()
						.padding(paddingValues)
						.verticalScroll(scrollState)
						.padding(bottom = 350.dp) // Padding for sheet overlap
				) {
					DetailsHeader(
						mangaDetails = mangaDetails,
						onCoverBoundsSync = onCoverBoundsSync
					)
				}
			}
		)
	}
}

sealed interface DetailsAction {
	data class OpenSource(val source: org.skepsun.kototoro.parsers.model.ContentSource) : DetailsAction
	data class TagClick(val tag: org.skepsun.kototoro.parsers.model.ContentTag) : DetailsAction
}
