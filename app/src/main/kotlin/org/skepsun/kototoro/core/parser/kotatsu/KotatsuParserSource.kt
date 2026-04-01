package org.skepsun.kototoro.core.parser.kotatsu

import org.koitharu.kotatsu.parsers.model.MangaSource as KTMangaSource
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.ContentSource

/**
 * Kotatsu 解析器源的包装，暴露为 Kototoro 的 ContentSource。
 */
data class KotatsuParserSource(
    val delegate: KTMangaSource,
) : ContentSource {
    override val name: String = delegate.name
    val title: String = try {
        val underlying = if (delegate is org.skepsun.kototoro.core.extensions.PluginMangaSource) delegate.originalSource else delegate
        underlying.javaClass.getMethod("getTitle").invoke(underlying) as? String
    } catch (_: Exception) {
        null
    } ?: delegate.name.lowercase().replaceFirstChar { it.uppercase() }
    override val locale: String = delegate.locale
    override val contentType: ContentType = delegate.contentType.toKototoro()
    val isBroken: Boolean
        get() = org.skepsun.kototoro.core.extensions.GlobalExtensionManager.mangaSources.value
            .find { it.originalSource.name == delegate.name }?.isBroken == true
}
