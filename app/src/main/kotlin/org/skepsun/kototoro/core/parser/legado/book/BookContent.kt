package org.skepsun.kototoro.core.parser.legado.book

import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.parser.legado.AnalyzeRule
import org.skepsun.kototoro.core.parser.legado.sandbox.LegadoSandbox
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.MangaSource

/**
 * Handles chapter content parsing and image extraction.
 */
object BookContent {

    private const val TAG = "LegadoRepository"

    data class ParseResult(
        val pages: List<MangaPage>,
        val nextPageUrls: List<String>
    )
    
    fun parse(
        content: String,
        baseUrl: String,
        source: MangaSource,
        config: LegadoBookSource,
        sandbox: LegadoSandbox
    ): ParseResult {
        val rule = config.ruleContent ?: return ParseResult(emptyList(), emptyList())
        val analyzeRule = AnalyzeRule(content, sandbox, baseUrl)
        
        android.util.Log.d(TAG, "[BookContent] Rule: ${rule.content}, HTML length: ${content.length}")
        
        // Legado content rule can return multiple image URLs or text
        val rawContentList = analyzeRule.getStringList(rule.content) ?: emptyList()
        
        android.util.Log.d(TAG, "[BookContent] Extracted ${rawContentList.size} items, first item preview: ${rawContentList.firstOrNull()?.take(100) ?: "EMPTY"}")
        
        val processedContent = applyReplace(rawContentList, rule)
        
        val pages = processedContent.mapIndexed { index, raw ->
            val refined = refineContent(raw, config)
            
            val absoluteUrl = if (isLikelyUrl(refined)) {
                resolveUrl(baseUrl, refined)
            } else {
                // Wrap text content in data URL for NovelContentLoader
                val base64 = android.util.Base64.encodeToString(
                    refined.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )
                "data:text/html;base64,$base64"
            }
            
            if (index == 0) {
                android.util.Log.d(TAG, "[Content] Page[0] starts with: ${refined.take(100).replace("\n", " ")}")
            }

            MangaPage(
                id = (source.name.hashCode().toLong() shl 32) + index,
                url = absoluteUrl,
                preview = if (isLikelyUrl(refined)) absoluteUrl else "TEXT",
                headers = emptyMap(),
                source = source
            )
        }

        val nextPageUrls = rule.nextContentUrl
            ?.let { analyzeRule.getStringList(it) }
            ?.mapNotNull { it.takeIf { url -> url.isNotBlank() } }
            ?.map { resolveUrl(baseUrl, it) }
            ?: emptyList()
        
        if (nextPageUrls.isNotEmpty()) {
            android.util.Log.d(TAG, "[Content] nextPageUrls found: ${nextPageUrls.take(3)}")
        }

        return ParseResult(pages, nextPageUrls)
    }

    private fun refineContent(content: String, config: LegadoBookSource): String {
        return content.trim()
    }

    private fun applyReplace(content: List<String>, rule: org.skepsun.kototoro.core.model.jsonsource.ContentRule): List<String> {
        val replace = rule.replaceRegex
        if (replace.isNullOrBlank()) return content

        // 支持 Legado 常见的 "regex##replacement" 写法
        val parts = replace.split("##", limit = 2)
        if (parts.size != 2) return content
        val pattern = runCatching { Regex(parts[0]) }.getOrNull() ?: return content
        val replacement = parts[1]
        return content.map { text -> text.replace(pattern, replacement) }
    }

    private fun isLikelyUrl(s: String): Boolean {
        val trimmed = s.trim()
        if (trimmed.length > 2048) return false // Too long for a URL
        val lower = trimmed.lowercase()
        return lower.startsWith("http://") || 
               lower.startsWith("https://") || 
               lower.startsWith("data:") || 
               lower.startsWith("file://") ||
               lower.startsWith("ftp://") ||
               (trimmed.contains(".") && !trimmed.contains(" ") && !trimmed.contains("\n"))
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
