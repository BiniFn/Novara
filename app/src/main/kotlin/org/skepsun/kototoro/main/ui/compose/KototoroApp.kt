package org.skepsun.kototoro.main.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.flow.StateFlow
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.ui.widgets.BottomNavState
import org.skepsun.kototoro.core.ui.widgets.KototoroBottomNav
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.search.ui.suggestion.model.SearchSuggestionItem

import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.shape.CircleShape
import org.skepsun.kototoro.core.prefs.observeAsState

@Composable
fun KototoroApp(
    appSettings: AppSettings,
    navStateFlow: StateFlow<BottomNavState>,
    onNavItemSelected: (Int) -> Unit,
    onNavItemReselected: (Int) -> Unit,
    suggestions: List<SearchSuggestionItem> = emptyList(),
    onQueryChanged: (String) -> Unit = {},
    onSearch: (String) -> Unit = {},
    onSuggestionClick: (SearchSuggestionItem) -> Unit = {},
    onDeleteQuery: (String) -> Unit = {},
    onVoiceInput: () -> Unit = {},
    onMoreClick: (android.view.View?) -> Unit = {},
    selectedContentType: ContentType? = null,
    onContentTypeSelected: (ContentType?) -> Unit = {},
    selectedSourceTags: Set<SourceTag> = emptySet(),
    onSourceTagSelected: (SourceTag?) -> Unit = {},
    onTopBarHeightChanged: (Int) -> Unit = {},
    onBottomNavHeightChanged: (Int) -> Unit = {},
    nestedScrollDeltaYFlow: kotlinx.coroutines.flow.SharedFlow<Float> = MutableSharedFlow(),
    isResumeEnabled: Boolean = false,
    onResumeClick: () -> Unit = {},
    onContainerReady: (androidx.fragment.app.FragmentContainerView) -> Unit = {}
) {
    val isNavBarPinned by appSettings.observeAsState(AppSettings.KEY_NAV_PINNED) { isNavBarPinned }

    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var bottomNavHeightPx by remember { mutableIntStateOf(0) }
    var topBarOffset by remember { mutableFloatStateOf(0f) }
    var bottomNavOffset by remember { mutableFloatStateOf(0f) }
    
    val nestedScrollConnection = remember(isNavBarPinned, topBarHeightPx, bottomNavHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (!isNavBarPinned && dy != 0f) {
                    topBarOffset = (topBarOffset + dy).coerceIn(-topBarHeightPx.toFloat(), 0f)
                    bottomNavOffset = (bottomNavOffset - dy).coerceIn(0f, bottomNavHeightPx.toFloat())
                } else if (isNavBarPinned) {
                    topBarOffset = 0f
                    bottomNavOffset = 0f
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(isNavBarPinned, topBarHeightPx, bottomNavHeightPx) {
        nestedScrollDeltaYFlow.collect { dy ->
            if (!isNavBarPinned && dy != 0f) {
                topBarOffset = (topBarOffset - dy).coerceIn(-topBarHeightPx.toFloat(), 0f)
                bottomNavOffset = (bottomNavOffset + dy).coerceIn(0f, bottomNavHeightPx.toFloat())
            } else if (isNavBarPinned) {
                topBarOffset = 0f
                bottomNavOffset = 0f
            }
        }
    }

    KototoroTheme {
        Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
            // Native Fragment Container layer beneath all Compose UI bars
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { context ->
                    androidx.fragment.app.FragmentContainerView(context).apply {
                        id = org.skepsun.kototoro.R.id.container
                        post { onContainerReady(this) }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )


            // TopBar at the top — measure its height for content padding
            KototoroTopBar(
                suggestions = suggestions,
                onQueryChanged = onQueryChanged,
                onSearch = onSearch,
                onSuggestionClick = onSuggestionClick,
                onDeleteQuery = onDeleteQuery,
                onVoiceInput = onVoiceInput,
                onMoreClick = onMoreClick,
                selectedContentType = selectedContentType,
                onContentTypeSelected = onContentTypeSelected,
                selectedSourceTags = selectedSourceTags,
                onSourceTagSelected = onSourceTagSelected,
                isSearchBarFilterHidden = false,
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

