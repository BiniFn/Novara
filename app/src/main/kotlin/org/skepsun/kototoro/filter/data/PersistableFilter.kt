package org.skepsun.kototoro.filter.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import org.skepsun.kototoro.core.model.ContentSourceSerializer
import org.skepsun.kototoro.parsers.model.ContentListFilter
import org.skepsun.kototoro.parsers.model.ContentSource

@Serializable
@JsonIgnoreUnknownKeys
data class PersistableFilter(
    @SerialName("name")
    val name: String,
    @Serializable(with = ContentSourceSerializer::class)
    @SerialName("source")
    val source: ContentSource,
    @Serializable(with = ContentListFilterSerializer::class)
    @SerialName("filter")
    val filter: ContentListFilter,
    @SerialName("auto_enabled")
    val autoEnabled: Boolean = false,
) {

    val id: Int
        get() = name.hashCode()

    companion object {

        const val MAX_TITLE_LENGTH = 18
    }
}
