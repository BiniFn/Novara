package org.skepsun.kototoro.main.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.StateFlow
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.ui.widgets.BottomNavState
import org.skepsun.kototoro.core.ui.widgets.KototoroBottomNav
import org.skepsun.kototoro.core.ui.glass.LocalHazeState
import org.skepsun.kototoro.core.ui.glass.supportsRuntimeHaze
import org.skepsun.kototoro.explore.ui.model.SourceTag
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.search.ui.suggestion.model.SearchSuggestionItem
import org.skepsun.kototoro.core.prefs.observeAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

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
    onOpenListOptions: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    isAppUpdateAvailable: Boolean = false,
    onAppUpdateClick: () -> Unit = {},
    isIncognitoModeEnabled: Boolean = false,
    onIncognitoToggle: () -> Unit = {},
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
    onNavDestinationChanged: (Int) -> Unit = {},
    isResumeEnabled: Boolean = false,
    onResumeClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val isNavBarPinned by appSettings.observeAsState(AppSettings.KEY_NAV_PINNED) { isNavBarPinned }
    val isFloating by appSettings.observeAsState(AppSettings.KEY_NAV_FLOATING) { isNavFloating }
    val activeSourcePresetId by appSettings.observeAsState(AppSettings.KEY_ACTIVE_SOURCE_PRESET_ID) { activeSourcePresetId }

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
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(currentRoute) {
        val mappedId = when (currentRoute) {
            "home" -> org.skepsun.kototoro.R.id.nav_home
            "history" -> org.skepsun.kototoro.R.id.nav_history
            "favorites" -> org.skepsun.kototoro.R.id.nav_favorites
            "explore" -> org.skepsun.kototoro.R.id.nav_explore
            "discover" -> org.skepsun.kototoro.R.id.nav_discover
            "feed" -> org.skepsun.kototoro.R.id.nav_feed
            "local" -> org.skepsun.kototoro.R.id.nav_local
            "suggestions" -> org.skepsun.kototoro.R.id.nav_suggestions
            "bookmarks" -> org.skepsun.kototoro.R.id.nav_bookmarks
            "updated" -> org.skepsun.kototoro.R.id.nav_updated
            else -> -1
        }
        if (mappedId != -1) {
            onNavDestinationChanged(mappedId)
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val contentPadding = remember(visibleTopInsetPx, visibleBottomInsetPx, density) {
        with(density) {
            androidx.compose.foundation.layout.PaddingValues(
                top = visibleTopInsetPx.toDp(),
                bottom = visibleBottomInsetPx.toDp()
            )
        }
    }

    KototoroTheme {
        val hazeState = remember { HazeState() }
        val useRuntimeHaze = remember { supportsRuntimeHaze() }
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
                AppNavGraph(
                    navController = navController,
                    contentPadding = contentPadding,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (useRuntimeHaze) Modifier.haze(hazeState) else Modifier)
                )

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
                    onOpenListOptions = onOpenListOptions,
                    onSettingsClick = onSettingsClick,
                    isAppUpdateAvailable = isAppUpdateAvailable,
                    onAppUpdateClick = onAppUpdateClick,
                    isIncognitoModeEnabled = isIncognitoModeEnabled,
                    onIncognitoToggle = onIncognitoToggle,
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
                        onItemSelected = { itemId ->
                            val route = when (itemId) {
                                org.skepsun.kototoro.R.id.nav_home -> "home"
                                org.skepsun.kototoro.R.id.nav_history -> "history"
                                org.skepsun.kototoro.R.id.nav_favorites -> "favorites"
                                org.skepsun.kototoro.R.id.nav_explore -> "explore"
                                org.skepsun.kototoro.R.id.nav_discover -> "discover"
                                org.skepsun.kototoro.R.id.nav_feed -> "feed"
                                org.skepsun.kototoro.R.id.nav_local -> "local"
                                org.skepsun.kototoro.R.id.nav_suggestions -> "suggestions"
                                org.skepsun.kototoro.R.id.nav_bookmarks -> "bookmarks"
                                org.skepsun.kototoro.R.id.nav_updated -> "updated"
                                else -> "home"
                            }
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onItemReselected = { },
                    )
                }
            }
        }
    }
}
