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

fun routeNameForBottomNavItem(@IdRes itemId: Int): String = when (itemId) {
    R.id.nav_home -> AppRouteNames.HOME
    R.id.nav_history -> AppRouteNames.HISTORY
    R.id.nav_favorites -> AppRouteNames.FAVORITES
    R.id.nav_explore -> AppRouteNames.EXPLORE
    R.id.nav_discover -> AppRouteNames.DISCOVER
    R.id.nav_feed -> AppRouteNames.FEED
    R.id.nav_local -> AppRouteNames.LOCAL
    R.id.nav_suggestions -> AppRouteNames.SUGGESTIONS
    R.id.nav_bookmarks -> AppRouteNames.BOOKMARKS
    R.id.nav_updated -> AppRouteNames.UPDATED
    else -> AppRouteNames.HOME
}

fun bottomNavItemForRouteName(route: String?): Int = when (route) {
    AppRouteNames.HOME -> R.id.nav_home
    AppRouteNames.HISTORY -> R.id.nav_history
    AppRouteNames.FAVORITES -> R.id.nav_favorites
    AppRouteNames.EXPLORE -> R.id.nav_explore
    AppRouteNames.DISCOVER -> R.id.nav_discover
    AppRouteNames.FEED -> R.id.nav_feed
    AppRouteNames.LOCAL -> R.id.nav_local
    AppRouteNames.SUGGESTIONS -> R.id.nav_suggestions
    AppRouteNames.BOOKMARKS -> R.id.nav_bookmarks
    AppRouteNames.UPDATED -> R.id.nav_updated
    else -> -1
}
