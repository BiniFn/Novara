package org.skepsun.kototoro.explore.ui.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.jsonsource.ContentGroup
import org.skepsun.kototoro.core.jsonsource.OriginGroup

/**
 * Single-select source tags shown in the secondary filter bar.
 *
 * BUILTIN: filter native sources
 * Mihon : filter Mihon-origin sources
 * Aniyomi: filter Aniyomi-origin sources
 * JSON  : filter JSON-origin sources (Legado/TVBox/JS)
 */
enum class SourceTag(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val id: String,
) {
    BUILTIN(R.string.built_in_sources, R.drawable.ic_source_builtin, "builtin"),
    MIHON(R.string.mihon_sources, R.drawable.ic_source_mihon, "mihon"),
    ANIYOMI(R.string.aniyomi_sources, R.drawable.ic_source_aniyomi, "aniyomi"),
    LEGADO(R.string.source_type_legado, R.drawable.ic_source_legado, "legado"),
    TVBOX(R.string.source_type_tvbox, R.drawable.ic_source_tvbox, "tvbox");

    /**
     * Whether this tag matches the given content and origin group.
     */
    fun matches(contentGroup: ContentGroup, originGroup: OriginGroup): Boolean = when (this) {
        BUILTIN -> originGroup == OriginGroup.NATIVE
        MIHON -> originGroup == OriginGroup.MIHON
        ANIYOMI -> originGroup == OriginGroup.ANIYOMI
        LEGADO -> originGroup == OriginGroup.LEGADO_JSON
        TVBOX -> originGroup == OriginGroup.TVBOX_JSON
    }

    /**
     * Check if this tag supports the given content tab.
     */
    fun supportsContentTab(tab: BrowseGroupTab): Boolean = when (this) {
        BUILTIN -> true
        MIHON -> tab == BrowseGroupTab.Manga || tab == BrowseGroupTab.All
        ANIYOMI -> tab == BrowseGroupTab.Video || tab == BrowseGroupTab.All
        LEGADO -> tab == BrowseGroupTab.Manga || tab == BrowseGroupTab.Novel || tab == BrowseGroupTab.All
        TVBOX -> tab == BrowseGroupTab.Video || tab == BrowseGroupTab.All
    }

    companion object {
        fun fromIds(ids: Collection<String>): Set<SourceTag> =
            ids.mapNotNull { id ->
                when (id) {
                    "json" -> LEGADO
                    else -> entries.find { it.id == id }
                }
            }.toSet()
    }
}
