package org.skepsun.kototoro.main.ui.compose

import androidx.annotation.IdRes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.skepsun.kototoro.R

object AppRouteNames {
    const val HOME = "home"
    const val DISCOVER = "discover"
    const val HISTORY = "history"
    const val FAVORITES = "favorites"
    const val EXPLORE = "explore"
    const val FEED = "feed"
    const val LOCAL = "local"
    const val SUGGESTIONS = "suggestions"
    const val BOOKMARKS = "bookmarks"
    const val UPDATED = "updated"
    const val SEARCH = "search"
    const val DETAILS = "details"
}

@Serializable
@SerialName(AppRouteNames.HOME)
data object HomeRoute

@Serializable
@SerialName(AppRouteNames.DISCOVER)
data object DiscoverRoute

@Serializable
@SerialName(AppRouteNames.HISTORY)
data object HistoryRoute

@Serializable
@SerialName(AppRouteNames.FAVORITES)
data object FavoritesRoute

@Serializable
@SerialName(AppRouteNames.EXPLORE)
data object ExploreRoute

@Serializable
@SerialName(AppRouteNames.FEED)
data object FeedRoute

@Serializable
@SerialName(AppRouteNames.LOCAL)
data object LocalRoute

@Serializable
@SerialName(AppRouteNames.SUGGESTIONS)
data object SuggestionsRoute

@Serializable
@SerialName(AppRouteNames.BOOKMARKS)
data object BookmarksRoute

@Serializable
@SerialName(AppRouteNames.UPDATED)
data object UpdatedRoute

@Serializable
@SerialName(AppRouteNames.DETAILS)
data object DetailsRoute

fun routeForBottomNavItem(@IdRes itemId: Int): Any = when (itemId) {
    R.id.nav_home -> HomeRoute
    R.id.nav_history -> HistoryRoute
    R.id.nav_favorites -> FavoritesRoute
    R.id.nav_explore -> ExploreRoute
    R.id.nav_discover -> DiscoverRoute
    R.id.nav_feed -> FeedRoute
    R.id.nav_local -> LocalRoute
    R.id.nav_suggestions -> SuggestionsRoute
    R.id.nav_bookmarks -> BookmarksRoute
    R.id.nav_updated -> UpdatedRoute
    else -> HomeRoute
}
