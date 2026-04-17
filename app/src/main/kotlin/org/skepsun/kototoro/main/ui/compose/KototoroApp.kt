package org.skepsun.kototoro.main.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import kotlinx.coroutines.flow.StateFlow
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.ui.widgets.BottomNavState
import org.skepsun.kototoro.core.ui.widgets.KototoroBottomNav
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.search.ui.suggestion.model.SearchSuggestionItem

import kotlinx.coroutines.flow.MutableSharedFlow
import org.skepsun.kototoro.core.prefs.observeAsState
import androidx.navigation.compose.rememberNavController
import org.skepsun.kototoro.main.ui.compose.AppNavGraph

@Composable
fun KototoroApp(
    appSettings: AppSettings,
    navStateFlow: StateFlow<BottomNavState>,
    query: String = "",
    
    suggestions: List<SearchSuggestionItem> = emptyList(),
    onQueryChanged: (String) -> Unit = {},
    onSearch: (String) -> Unit = {},
    onContentSuggestionClick: (Content) -> Unit = {},
    onTagSuggestionClick: (ContentTag) -> Unit = {},
    onSourceSuggestionClick: (ContentSource) -> Unit = {},
    onAuthorSuggestionClick: (String) -> Unit = {},
    onDeleteQuery: (String) -> Unit = {},
    onVoiceInput: () -> Unit = {},
    onMoreClick: (android.view.View?) -> Unit = {},
    isLanguagePresetFilterVisible: Boolean = false,
    onLanguagePresetFilterClick: (android.view.View?) -> Boolean = { false },
    selectedContentType: ContentType? = null,
    enabledContentTypes: Set<ContentType> = setOf(ContentType.MANGA, ContentType.NOVEL, ContentType.VIDEO),
    isContentTypeFilterVisible: Boolean = true,
    onContentTypeSelected: (ContentType?) -> Unit = {},
    selectedSourceTags: Set<SourceTag> = emptySet(),
    sourceTagEntries: List<SourceTag> = SourceTag.quickFilterEntries,
    enabledSourceTags: Set<SourceTag> = sourceTagEntries.toSet(),
    isSourceTagFilterVisible: Boolean = true,
    onSourceTagFilterClick: (android.view.View?) -> Boolean = { false },
    onSourceTagSelected: (SourceTag?) -> Unit = {},
    onTopBarHeightChanged: (Int) -> Unit = {},
    onBottomNavHeightChanged: (Int) -> Unit = {},
    onContentInsetsChanged: (Int, Int) -> Unit = { _, _ -> },
    isResumeEnabled: Boolean = false,
    onResumeClick: () -> Unit = {},
    
) {
    val context = LocalContext.current
    val isNavBarPinned by appSettings.observeAsState(AppSettings.KEY_NAV_PINNED) { isNavBarPinned }
    val isFloating by appSettings.observeAsState(AppSettings.KEY_NAV_FLOATING) { isNavFloating }
    val activeSourcePresetId by appSettings.observeAsState(AppSettings.KEY_ACTIVE_SOURCE_PRESET_ID) { activeSourcePresetId }

    ,
                android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }
    

    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var bottomNavHeightPx by remember { mutableIntStateOf(0) }
    var topBarOffset by remember { mutableFloatStateOf(0f) }
    var bottomNavOffset by remember { mutableFloatStateOf(0f) }
    
    val nestedScrollConnection = remember(isNavBarPinned, topBarHeightPx, bottomNavHeightPx) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                val dy = available.y
                if (!isNavBarPinned && dy != 0f) {
                    topBarOffset = (topBarOffset + dy).coerceIn(-topBarHeightPx.toFloat(), 0f)
                    bottomNavOffset = (bottomNavOffset - dy).coerceIn(0f, bottomNavHeightPx.toFloat())
                } else if (isNavBarPinned) {
                    topBarOffset = 0f
                    bottomNavOffset = 0f
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    
    val visibleTopInsetPx = (topBarHeightPx + topBarOffset).coerceAtLeast(0f).toInt()
    val visibleBottomInsetPx = if (isFloating) {
        0
    } else {
        (bottomNavHeightPx - bottomNavOffset).coerceAtLeast(0f).toInt()
    }

    LaunchedEffect(visibleTopInsetPx, visibleBottomInsetPx) {
        onContentInsetsChanged(visibleTopInsetPx, visibleBottomInsetPx)
    }

    DisposableEffect(fragmentHostView, isNavBarPinned, topBarHeightPx, bottomNavHeightPx) {
        fragmentHostView.onNestedScrollDeltaY = { dy ->
            if (!isNavBarPinned && dy != 0f) {
                topBarOffset = (topBarOffset - dy).coerceIn(-topBarHeightPx.toFloat(), 0f)
                bottomNavOffset = (bottomNavOffset + dy).coerceIn(0f, bottomNavHeightPx.toFloat())
            } else if (isNavBarPinned) {
                topBarOffset = 0f
                bottomNavOffset = 0f
            }
        }
        onDispose {
            fragmentHostView.onNestedScrollDeltaY = null
        }
    }

    val navController = rememberNavController()

    KototoroTheme {
        Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
            AppNavGraph(
                navController = navController,
                modifier = Modifier.fillMaxSize()
            )
            



            // TopBar at the top — measure its height for content padding
            KototoroTopBar(
                query = query,
                suggestions = suggestions,
                onQueryChanged = onQueryChanged,
                onSearch = onSearch,
                onContentSuggestionClick = onContentSuggestionClick,
                onTagSuggestionClick = onTagSuggestionClick,
                onSourceSuggestionClick = onSourceSuggestionClick,
                onAuthorSuggestionClick = onAuthorSuggestionClick,
                onDeleteQuery = onDeleteQuery,
                onVoiceInput = onVoiceInput,
                onMoreClick = onMoreClick,
                isLanguagePresetFilterVisible = isLanguagePresetFilterVisible,
                hasActiveLanguagePreset = activeSourcePresetId > 0L,
                onLanguagePresetFilterClick = onLanguagePresetFilterClick,
                selectedContentType = selectedContentType,
                enabledContentTypes = enabledContentTypes,
                isContentTypeFilterVisible = isContentTypeFilterVisible,
                onContentTypeSelected = onContentTypeSelected,
                selectedSourceTags = selectedSourceTags,
                sourceTagEntries = sourceTagEntries,
                enabledSourceTags = enabledSourceTags,
                isSourceTagFilterVisible = isSourceTagFilterVisible,
                onSourceTagFilterClick = onSourceTagFilterClick,
                onSourceTagSelected = onSourceTagSelected,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { androidx.compose.ui.unit.IntOffset(0, topBarOffset.toInt()) }
                    .onGloballyPositioned { coords ->
                        val newHeight = coords.size.height
                        if (topBarHeightPx != newHeight) {
                            topBarHeightPx = newHeight
                            onTopBarHeightChanged(newHeight)
                        }
                    },
            )


            // BottomNav at the bottom — measure its height for content padding
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { androidx.compose.ui.unit.IntOffset(0, bottomNavOffset.toInt()) }
                    .onGloballyPositioned { coords ->
                        val newHeight = coords.size.height
                        if (bottomNavHeightPx != newHeight) {
                            bottomNavHeightPx = newHeight
                            onBottomNavHeightChanged(newHeight)
                        }
                    },
            ) {
                KototoroBottomNav(
                    state = navStateFlow,
                    onItemSelected = onNavItemSelected,
                    onItemReselected = onNavItemReselected,
                )
        }
    }
}
}
