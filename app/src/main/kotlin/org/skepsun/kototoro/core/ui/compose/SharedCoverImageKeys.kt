package org.skepsun.kototoro.core.ui.compose

fun sharedCoverMemoryCacheKey(
    sourceName: String?,
    ownerKey: String?,
    url: String?,
): String? {
    val normalizedUrl = url?.takeIf { it.isNotBlank() } ?: return null
    return buildString {
        append("shared-cover")
        append('#')
        append(sourceName.orEmpty())
        append('#')
        append(ownerKey.orEmpty())
        append('#')
        append(normalizedUrl)
    }
}
