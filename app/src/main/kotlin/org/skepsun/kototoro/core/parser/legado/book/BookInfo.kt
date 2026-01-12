package org.skepsun.kototoro.core.parser.legado.book

import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.parser.legado.*
import org.skepsun.kototoro.core.parser.legado.sandbox.LegadoSandbox
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaTag

/**
 * Handles book details parsing using ruleBookInfo.
 */
object BookInfo {

    data class Result(
        val manga: Manga,
        val tocUrl: String?
    )
    
    /**
     * Parse book details from HTML/JSON content
     */
    fun parse(
        manga: Manga,
        content: String,
        baseUrl: String,
        config: LegadoBookSource,
        sandbox: LegadoSandbox
    ): Result {
        val rule = config.ruleBookInfo ?: return Result(manga, null)
        val analyzeRule = AnalyzeRule(content, sandbox, baseUrl)
        
        // Execute init if present (Legado init script can transform content)
        var currentContent: Any = content
        if (!rule.init.isNullOrBlank()) {
            val initResult = analyzeRule.evalJS(rule.init, content)
            if (initResult != null) {
                currentContent = initResult
                analyzeRule.setContent(currentContent)
            }
        }
        
        // Update sandbox context with current book info
        sandbox.setBook(LegadoSandbox.BookContext(
            name = manga.title,
            author = manga.authors.firstOrNull() ?: "",
            url = manga.url
        ))

        val title = analyzeRule.getString(rule.name).takeIf { it.isNotBlank() } ?: manga.title
        val author = analyzeRule.getString(rule.author).takeIf { it.isNotBlank() } ?: (manga.authors.firstOrNull() ?: "")
        val coverUrl = analyzeRule.getString(rule.coverUrl, isUrl = true).let { resolveUrl(baseUrl, it) }.takeIf { it.isNotBlank() } ?: manga.coverUrl
        val intro = analyzeRule.getString(rule.intro).takeIf { it.isNotBlank() } ?: manga.description
        val kind = analyzeRule.getString(rule.kind)
        var tocUrl = analyzeRule.getString(rule.tocUrl, isUrl = true).takeIf { it.isNotBlank() }
        
        // If tocUrl is the same as source base URL (homepage), use the current book detail page instead
        // This handles sources where the tocUrl rule incorrectly returns the site's homepage
        val sourceBaseUrl = config.bookSourceUrl.trimEnd('/')
        if (tocUrl != null && (tocUrl.trimEnd('/') == sourceBaseUrl || tocUrl == sourceBaseUrl + "/")) {
            android.util.Log.w("BookInfo", "tocUrl '$tocUrl' matches source base URL, using baseUrl '$baseUrl' instead")
            tocUrl = baseUrl
        }
        
        val source = manga.source
        val tags = if (kind.isNotBlank()) {
            kind.split(",", " ", "|").map { it.trim() }.filter { it.isNotBlank() }.map { MangaTag(it, it, source) }.toSet()
        } else {
            manga.tags
        }

        val updated = manga.copy(
            title = title,
            authors = setOf(author),
            coverUrl = coverUrl,
            description = intro,
            tags = tags
        )

        // 更新 sandbox 中的目录上下文
        sandbox.setBook(
            LegadoSandbox.BookContext(
                name = updated.title,
                author = updated.authors.firstOrNull() ?: "",
                url = updated.url,
                coverUrl = updated.coverUrl ?: "",
                intro = updated.description ?: "",
                kind = kind,
                tocUrl = tocUrl ?: ""
            )
        )

        return Result(updated, tocUrl)
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.isBlank() || relativeUrl.startsWith("http")) return relativeUrl
        return try {
            java.net.URL(java.net.URL(baseUrl), relativeUrl).toString()
        } catch (e: Exception) {
            relativeUrl
        }
    }
}
