package org.skepsun.kototoro.search.ui.compose

import org.skepsun.kototoro.core.jsonsource.SourceType
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.main.ui.compose.AppRouteNames
import org.skepsun.kototoro.search.domain.AdvancedSearchParams
import org.skepsun.kototoro.search.domain.SearchContentKind
import org.skepsun.kototoro.search.domain.SearchKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

@Serializable
@SerialName(AppRouteNames.SEARCH)
data class SearchRoute(
    @SerialName(AppRouter.KEY_QUERY)
    val query: String = "",
    @SerialName(AppRouter.KEY_KIND)
    val kind: String = SearchKind.SIMPLE.name,
    @SerialName(AppRouter.KEY_SOURCE_TYPES)
    val sourceTypes: String = "",
    @SerialName(AppRouter.KEY_CONTENT_KINDS)
    val contentKinds: String = "",
    @SerialName(AppRouter.KEY_ADVANCED_TITLE)
    val advancedTitle: String = "",
    @SerialName(AppRouter.KEY_ADVANCED_TAGS)
    val advancedTags: String = "",
    @SerialName(AppRouter.KEY_ADVANCED_AUTHOR)
    val advancedAuthor: String = "",
    @SerialName(AppRouter.KEY_PINNED_ONLY)
    val pinnedOnly: Boolean = false,
    @SerialName(AppRouter.KEY_HIDE_EMPTY)
    val hideEmpty: Boolean = false,
)

object SearchNavigation {
    const val baseRoute = AppRouteNames.SEARCH

    fun createRoute(request: SearchNavigationRequest): SearchRoute {
        return SearchRoute(
            query = request.query,
            kind = request.kind.name,
            sourceTypes = request.sourceTypes.joinToString(",") { it.name },
            contentKinds = request.contentKinds.joinToString(",") { it.name },
            advancedTitle = request.advancedQuery?.title.orEmpty(),
            advancedTags = request.advancedQuery?.tags.orEmpty(),
            advancedAuthor = request.advancedQuery?.author.orEmpty(),
            pinnedOnly = request.pinnedOnly,
            hideEmpty = request.hideEmpty,
        )
    }
}
