package org.skepsun.kototoro.core.parser.legado.book

import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.parser.legado.*
import org.skepsun.kototoro.core.parser.legado.sandbox.LegadoSandbox
import org.skepsun.kototoro.parsers.model.MangaChapter
import org.skepsun.kototoro.parsers.model.MangaSource

/**
 * Handles Table of Contents (TOC) parsing using ruleToc.
 */
object BookChapterList {

    private const val TAG = "LegadoRepository"

    data class ParseResult(

        val chapters: List<MangaChapter>,
        val nextPageUrls: List<String>,
        val reverse: Boolean
    )

    /**
     * Parse chapter list from HTML/JSON content
     */
    fun parse(
        content: String,
        baseUrl: String,
        source: MangaSource,
        config: LegadoBookSource,
        sandbox: LegadoSandbox
    ): ParseResult {
        android.util.Log.d(TAG, "===== BookChapterList.parse START =====")
        android.util.Log.d(TAG, "baseUrl=$baseUrl")
        
        val rule = config.ruleToc
        if (rule == null) {
            android.util.Log.w(TAG, "ruleToc is null!")
            return ParseResult(emptyList(), emptyList(), reverse = false)
        }
        
        android.util.Log.d(TAG, "ruleToc.chapterList=${rule.chapterList}")
        android.util.Log.d(TAG, "ruleToc.chapterName=${rule.chapterName}")
        android.util.Log.d(TAG, "ruleToc.chapterUrl=${rule.chapterUrl}")
        android.util.Log.d(TAG, "ruleToc.nextTocUrl=${rule.nextTocUrl}")
        
        if (rule.chapterList.isNullOrBlank()) {
            android.util.Log.w(TAG, "chapterList rule is blank!")
            return ParseResult(emptyList(), emptyList(), reverse = false)
        }

        var listRule = rule.chapterList
        var reverse = false
        if (listRule.startsWith("-")) {
            reverse = true
            listRule = listRule.removePrefix("-")
        } else if (listRule.startsWith("+")) {
            listRule = listRule.removePrefix("+")
        }
        android.util.Log.d(TAG, "listRule (after prefix processing)=$listRule, reverse=$reverse")

        val analyzeRule = AnalyzeRule(content, sandbox, baseUrl)
        val items = analyzeRule.getElements(listRule)
        android.util.Log.d(TAG, "Found ${items.size} elements with listRule")
        
        val nextPageUrls = rule.nextTocUrl
            ?.let { 
                android.util.Log.d(TAG, "Evaluating nextTocUrl rule: $it")
                analyzeRule.getStringList(it) 
            }
            ?.mapNotNull { it.takeIf { url -> url.isNotBlank() } }
            ?.map { resolveUrl(baseUrl, it) }
            ?: emptyList()
        android.util.Log.d(TAG, "nextPageUrls=$nextPageUrls")

        val chapters = items.mapIndexedNotNull { index, item ->
            val itemAnalyzer = AnalyzeRule(item, sandbox, baseUrl)
            
            val name = itemAnalyzer.getString(rule.chapterName)
            val url = itemAnalyzer.getString(rule.chapterUrl)
            
            val absoluteUrl = resolveUrl(baseUrl, url)

            if (index < 5) {
                android.util.Log.d(TAG, "[TOC] Chapter[$index] name=\"$name\", url=\"$url\" -> resolved=\"$absoluteUrl\"")
            }
            
            if (name.isBlank() || url.isBlank()) {
                android.util.Log.w(TAG, "Skipping chapter due to blank name or url: name=\"$name\", url=\"$url\"")
                return@mapIndexedNotNull null
            }
            
            val normalizedUrl = AnalyzeUrl.normalizeUrl(absoluteUrl)
            val stableId = (source.name.hashCode().toLong() shl 32) + (normalizedUrl.hashCode().toLong() and 0xFFFFFFFFL)
            
            if (index < 5) {
                android.util.Log.d(TAG, "[TOC] Chapter[$index] name=\"$name\", stableId=$stableId, sourceName=${source.name}(hash=${source.name.hashCode()}), url=\"$absoluteUrl\", normalized=\"$normalizedUrl\"(hash=${normalizedUrl.hashCode()})")
            }
            
            MangaChapter(
                id = stableId,
                title = name,
                number = index.toFloat() + 1f,
                volume = 0,
                url = absoluteUrl,
                scanlator = null,
                uploadDate = 0L,
                branch = null,
                source = source
            )
        }
        
        android.util.Log.d(TAG, "Parsed ${chapters.size} chapters")
        android.util.Log.d(TAG, "===== BookChapterList.parse END =====")

        return ParseResult(chapters, nextPageUrls, reverse)
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
