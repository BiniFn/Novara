package org.skepsun.kototoro.core.ui.compose

fun contentCoverSharedKey(
    sourceName: String,
    url: String,
    instanceKey: String? = null,
): String {
    return buildString {
        append("cover|")
        append(sourceName)
        append('|')
        append(url)
        if (!instanceKey.isNullOrBlank()) {
            append('|')
            append(instanceKey)
        }
    }
}
