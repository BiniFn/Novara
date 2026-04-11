package org.skepsun.kototoro.main.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.flow.StateFlow
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.ui.widgets.BottomNavState
import org.skepsun.kototoro.core.ui.widgets.KototoroBottomNav
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.search.ui.suggestion.model.SearchSuggestionItem

@Composable
fun KototoroApp(
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
    topBarOffset: Float = 0f,
    bottomNavOffset: Float = 0f,
) {
    KototoroTheme {
        Box(modifier = Modifier.fillMaxSize()) {
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
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { androidx.compose.ui.unit.IntOffset(0, topBarOffset.toInt()) }
                    .onGloballyPositioned { coords ->
                        onTopBarHeightChanged(coords.size.height)
                    },
            )

            // BottomNav at the bottom — measure its height for content padding
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { androidx.compose.ui.unit.IntOffset(0, bottomNavOffset.toInt()) }
                    .onGloballyPositioned { coords ->
                        onBottomNavHeightChanged(coords.size.height)
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
