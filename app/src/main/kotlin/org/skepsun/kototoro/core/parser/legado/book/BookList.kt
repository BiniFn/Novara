package org.skepsun.kototoro.core.parser.legado.book

import android.util.Log
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.parser.legado.*
import org.skepsun.kototoro.core.parser.legado.sandbox.LegadoSandbox
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.model.MangaSource

/**
 * Handles search and explore list parsing using ruleSearch / ruleExplore.
 * 
 * Based on legado-with-MD3 BookList pattern.
 */
object BookList {

    private const val TAG = "LegadoBookList"
    
    /**
     * Parse book list from HTML/JSON content
     */
    fun parse(
        content: String,
        baseUrl: String,
        source: MangaSource,
        config: LegadoBookSource,
        isSearch: Boolean,
        sandbox: LegadoSandbox
    ): List<Manga> {
        // 优先使用搜索规则兜底，避免 explore 为空对象但缺少 bookList 时直接返回空
        val rule = if (isSearch) {
            config.ruleSearch
        } else {
            config.ruleExplore?.takeIf { !it.bookList.isNullOrBlank() } ?: config.ruleSearch
        }
        if (rule == null || rule.bookList.isNullOrBlank()) {
            Log.w(TAG, "bookList rule missing (isSearch=$isSearch) explore=${config.ruleExplore} search=${config.ruleSearch}")
            return emptyList()
        }

        // 支持 Legado 的列表前缀：- 反转，+ 去标记
        var reverse = false
        var listRule = rule.bookList
        if (listRule.startsWith("-")) {
            reverse = true
            listRule = listRule.removePrefix("-")
        } else if (listRule.startsWith("+")) {
            listRule = listRule.removePrefix("+")
        }

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

        val items = analyzeRule.getElements(listRule)
        Log.d(TAG, "bookList rule=$listRule items=${items.size} isSearch=$isSearch")
        if (items.isEmpty()) {
            // Fallback: 尝试将含空格的 class 规则转为 CSS 连写
            val fallbackRule = buildCssFallback(listRule)
            if (fallbackRule != listRule) {
                val fallbackItems = analyzeRule.getElements(fallbackRule)
                Log.d(TAG, "bookList fallback rule=$fallbackRule items=${fallbackItems.size}")
                if (fallbackItems.isNotEmpty()) {
                    return parseItems(fallbackItems, rule, baseUrl, source, sandbox, reverse)
                }
            }

            // 第二层兜底：直接用 Jsoup 解析含 /books/ 的卡片（适配轻小说百科等）
            val doc = org.jsoup.Jsoup.parse(content)
            val anchors = doc.select("a[href*=/books/]")
            if (anchors.isNotEmpty()) {
                val fallbackItems = anchors.map { it as Any }
                Log.d(TAG, "bookList generic anchor fallback items=${fallbackItems.size}")
                return parseItems(fallbackItems, rule, baseUrl, source, sandbox, reverse)
            }
            Log.w(TAG, "bookList parse returned empty list for url=$baseUrl isSearch=$isSearch")
            return emptyList()
        }
        
        return parseItems(items, rule, baseUrl, source, sandbox, reverse)
    }

    private fun parseItems(
        items: List<Any?>,
        rule: org.skepsun.kototoro.core.model.jsonsource.SearchRule,
        baseUrl: String,
        source: MangaSource,
        sandbox: LegadoSandbox,
        reverse: Boolean
    ): List<Manga> {
        val mangas = items.mapIndexedNotNull { index, item ->
            if (item == null) return@mapIndexedNotNull null
            // Create a new analyzer for the specific item
            val itemAnalyzer = AnalyzeRule(item, sandbox, baseUrl)
            
            val title = itemAnalyzer.getString(rule.name)
            val bookUrl = itemAnalyzer.getString(rule.bookUrl, isUrl = true)
            
            if (index < 3) {
                Log.d(TAG, "[parseItem $index] rule.name='${rule.name}' -> title='$title'")
                Log.d(TAG, "[parseItem $index] rule.bookUrl='${rule.bookUrl}' -> bookUrl='$bookUrl'")
                // 打印item的内容预览
                val itemStr = item.toString().take(200)
                Log.d(TAG, "[parseItem $index] item preview: $itemStr")
            }
            
            if (title.isBlank() || bookUrl.isBlank()) return@mapIndexedNotNull null
            
            val absoluteUrl = resolveUrl(baseUrl, bookUrl)
            val author = itemAnalyzer.getString(rule.author)
            val coverUrl = itemAnalyzer.getString(rule.coverUrl, isUrl = true).let { resolveUrl(baseUrl, it) }
            val intro = itemAnalyzer.getString(rule.intro)
            
            Manga(
                id = generateMangaId(absoluteUrl),
                title = title,
                altTitles = emptySet(),
                url = absoluteUrl,
                publicUrl = absoluteUrl,
                rating = -1f,
                contentRating = null,
                coverUrl = coverUrl,
                tags = emptySet(),
                state = null,
                authors = if (author.isNotBlank()) setOf(author) else emptySet(),
                largeCoverUrl = null,
                description = intro,
                chapters = null,
                source = source
            )
        }

        val deduped = LinkedHashSet<Manga>(mangas)
        val result = deduped.toMutableList()
        Log.d(TAG, "bookList parsed=${result.size} reverse=$reverse")
        if (reverse) {
            result.reverse()
        }
        return result
    }

    private fun buildCssFallback(rule: String): String {
        if (!rule.contains("class.") || !rule.contains(" ")) return rule
        val parts = rule.split("@")
        val selectorPart = parts.firstOrNull() ?: return rule
        if (!selectorPart.startsWith("class.")) return rule
        val classes = selectorPart.removePrefix("class.").trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (classes.isEmpty()) return rule
        val css = classes.joinToString(separator = "", prefix = ".")
        val suffix = if (parts.size > 1) "@" + parts.drop(1).joinToString("@") else ""
        return css + suffix
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.isBlank() || relativeUrl.startsWith("http")) return relativeUrl
        return try {
            java.net.URL(java.net.URL(baseUrl), relativeUrl).toString()
        } catch (e: Exception) {
            relativeUrl
        }
    }

    private fun generateMangaId(url: String): Long {
        return url.hashCode().toLong()
    }
}
