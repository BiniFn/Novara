package org.skepsun.kototoro.explore.ui.model

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
    val id: String,
) {
    BUILTIN(R.string.built_in_sources, "builtin"),
    MIHON(R.string.mihon_sources, "mihon"),
    ANIYOMI(R.string.aniyomi_sources, "aniyomi"),
    JSON(R.string.json_sources, "json");

    /**
     * Whether this tag matches the given content and origin group.
     */
    fun matches(contentGroup: ContentGroup, originGroup: OriginGroup): Boolean = when (this) {
        BUILTIN -> originGroup == OriginGroup.NATIVE
        MIHON -> originGroup == OriginGroup.MIHON
        ANIYOMI -> originGroup == OriginGroup.ANIYOMI
        JSON -> originGroup == OriginGroup.LEGADO_JSON ||
            originGroup == OriginGroup.TVBOX_JSON ||
            originGroup == OriginGroup.JS_JSON
    }

    companion object {
        fun fromIds(ids: Collection<String>): Set<SourceTag> =
            ids.mapNotNull { id -> entries.find { it.id == id } }.toSet()
    }
}
