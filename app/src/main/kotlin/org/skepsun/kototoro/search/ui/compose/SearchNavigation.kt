package org.skepsun.kototoro.search.ui.compose

import android.net.Uri
import org.skepsun.kototoro.core.jsonsource.SourceType
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.search.domain.AdvancedSearchParams
import org.skepsun.kototoro.search.domain.SearchContentKind
import org.skepsun.kototoro.search.domain.SearchKind

data class SearchNavigationRequest(
    val query: String,
    val kind: SearchKind,
    val sourceTypes: Set<SourceType>,
    val contentKinds: Set<SearchContentKind>,
    val advancedQuery: AdvancedSearchParams?,
    val pinnedOnly: Boolean,
    val hideEmpty: Boolean,
    val requestId: Long,
)

object SearchNavigation {
    const val baseRoute = "search"
    const val routePattern =
        "$baseRoute?" +
            "${AppRouter.KEY_QUERY}={${AppRouter.KEY_QUERY}}&" +
            "${AppRouter.KEY_KIND}={${AppRouter.KEY_KIND}}&" +
            "${AppRouter.KEY_SOURCE_TYPES}={${AppRouter.KEY_SOURCE_TYPES}}&" +
            "${AppRouter.KEY_CONTENT_KINDS}={${AppRouter.KEY_CONTENT_KINDS}}&" +
            "${AppRouter.KEY_ADVANCED_TITLE}={${AppRouter.KEY_ADVANCED_TITLE}}&" +
            "${AppRouter.KEY_ADVANCED_TAGS}={${AppRouter.KEY_ADVANCED_TAGS}}&" +
            "${AppRouter.KEY_ADVANCED_AUTHOR}={${AppRouter.KEY_ADVANCED_AUTHOR}}&" +
            "${AppRouter.KEY_PINNED_ONLY}={${AppRouter.KEY_PINNED_ONLY}}&" +
            "${AppRouter.KEY_HIDE_EMPTY}={${AppRouter.KEY_HIDE_EMPTY}}"

    fun createRoute(request: SearchNavigationRequest): String {
        return buildString {
            append(baseRoute)
            append("?${AppRouter.KEY_QUERY}=${request.query.encodeNavArg()}")
            append("&${AppRouter.KEY_KIND}=${request.kind.name.encodeNavArg()}")
            append("&${AppRouter.KEY_SOURCE_TYPES}=${request.sourceTypes.joinToString(",") { it.name }.encodeNavArg()}")
            append("&${AppRouter.KEY_CONTENT_KINDS}=${request.contentKinds.joinToString(",") { it.name }.encodeNavArg()}")
            append("&${AppRouter.KEY_ADVANCED_TITLE}=${request.advancedQuery?.title.orEmpty().encodeNavArg()}")
            append("&${AppRouter.KEY_ADVANCED_TAGS}=${request.advancedQuery?.tags.orEmpty().encodeNavArg()}")
            append("&${AppRouter.KEY_ADVANCED_AUTHOR}=${request.advancedQuery?.author.orEmpty().encodeNavArg()}")
            append("&${AppRouter.KEY_PINNED_ONLY}=${request.pinnedOnly}")
            append("&${AppRouter.KEY_HIDE_EMPTY}=${request.hideEmpty}")
        }
    }
}

private fun String.encodeNavArg(): String = Uri.encode(this)
