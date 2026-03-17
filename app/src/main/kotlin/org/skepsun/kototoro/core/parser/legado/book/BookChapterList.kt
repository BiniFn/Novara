package org.skepsun.kototoro.core.parser.legado.book

import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.parser.legado.*
import org.skepsun.kototoro.core.parser.legado.sandbox.LegadoSandbox
import org.skepsun.kototoro.parsers.model.ContentChapter
import org.skepsun.kototoro.parsers.model.ContentSource

/**
 * Handles Table of Contents (TOC) parsing using ruleToc.
 */
object BookChapterList {

    private const val TAG = "LegadoRepository"

    data class ParseResult(

        val chapters: List<ContentChapter>,
        val nextPageUrls: List<String>,
        /**
         * 是否需要对当前页解析出的章节列表做反转。
         *
         * legado 规则约定：`chapterList` 以 '-' 开头表示“反转章节列表”。
         * `chapterList` 以 '+' 开头表示“显式不反转”（等同于无前缀，但可用于自解释）。
         */
        val shouldReverse: Boolean
    )

    /**
     * Parse chapter list from HTML/JSON content
     */
    fun parse(
        content: String,
        baseUrl: String,
        source: ContentSource,
        config: LegadoBookSource,
        sandbox: LegadoSandbox
    ): ParseResult {
        android.util.Log.d(TAG, "===== BookChapterList.parse START =====")
        android.util.Log.d(TAG, "baseUrl=$baseUrl")
        
        val rule = config.ruleToc
        if (rule == null) {
            android.util.Log.w(TAG, "ruleToc is null!")
            return ParseResult(emptyList(), emptyList(), shouldReverse = false)
        }
        
        android.util.Log.d(TAG, "ruleToc.chapterList=${rule.chapterList}")
        android.util.Log.d(TAG, "ruleToc.chapterName=${rule.chapterName}")
        android.util.Log.d(TAG, "ruleToc.chapterUrl=${rule.chapterUrl}")
        android.util.Log.d(TAG, "ruleToc.nextTocUrl=${rule.nextTocUrl}")
        
        if (rule.chapterList.isNullOrBlank()) {
            android.util.Log.w(TAG, "chapterList rule is blank!")
            return ParseResult(emptyList(), emptyList(), shouldReverse = false)
        }

        var listRule = rule.chapterList
        var shouldReverse = false
        if (listRule.startsWith("-")) {
            shouldReverse = true
            listRule = listRule.removePrefix("-")
        } else if (listRule.startsWith("+")) {
            listRule = listRule.removePrefix("+")
        }
        android.util.Log.d(TAG, "listRule (after prefix processing)=$listRule, shouldReverse=$shouldReverse")

        val analyzeRule = AnalyzeRule(content, sandbox, baseUrl)
        
        var currentContent: Any = content

        // legado 规则的 init 通常定义在 ruleBookInfo 上，但很多 JSON API 源会依赖它来“缩小根对象”（例如 `.data`）。
        // 这里在 TOC 解析前先应用一次全局 init（若存在），以对齐 legado-with-MD3 行为并保持通用。
        val globalInit = config.ruleBookInfo?.init?.trim()
        val canApplyGlobalInitAsJsonPath = !globalInit.isNullOrBlank() && (
            globalInit.startsWith("$") ||
                globalInit.startsWith(".") ||
                globalInit.startsWith("@Json:", ignoreCase = true)
            )
        if (canApplyGlobalInitAsJsonPath) {
            val initResult = LegadoInitEvaluator.applyInitIfPresent(analyzeRule, globalInit, currentContent)
            if (initResult != null) {
                currentContent = initResult
                analyzeRule.setContent(currentContent)
            }
        }

        // Execute preUpdateJs if present (equivalent to init for TOC)
        if (!rule.preUpdateJs.isNullOrBlank()) {
            val preUpdateResult = analyzeRule.evalJS(rule.preUpdateJs!!, currentContent)
            if (preUpdateResult != null) {
                currentContent = preUpdateResult
                analyzeRule.setContent(currentContent)
            }
        }
        val items = analyzeRule.getElements(listRule)
        android.util.Log.d(TAG, "Found ${items.size} elements with listRule (raw)")

        val normalizedItems = flattenNestedItems(items)
        android.util.Log.d(TAG, "Found ${normalizedItems.size} elements with listRule (flattened)")
        
        val nextPageUrls = rule.nextTocUrl
            ?.let { 
                android.util.Log.d(TAG, "Evaluating nextTocUrl rule: $it")
                analyzeRule.getStringList(it) 
            }
            ?.mapNotNull { it.takeIf { url -> url.isNotBlank() } }
            ?.map { resolveUrl(baseUrl, it) }
            ?: emptyList()
        android.util.Log.d(TAG, "nextPageUrls=$nextPageUrls")

        val chapters = normalizedItems.mapIndexedNotNull { index, item ->
            val itemAnalyzer = AnalyzeRule(item, sandbox, baseUrl)
            
            val name = itemAnalyzer.getString(rule.chapterName)
            var url = itemAnalyzer.getString(rule.chapterUrl, isUrl = true)
            
            // 仅当源未提供 chapterUrl 规则时，才回退到 baseUrl（单页内容源：书籍页面本身就是内容页）。
            // 若 chapterUrl 规则存在但解析失败，不应回退，否则会导致所有章节 url/id 相同。
            if (rule.chapterUrl.isNullOrBlank() && url.isBlank() && name.isNotBlank()) {
                url = baseUrl
                android.util.Log.d(TAG, "[TOC] Chapter[$index] chapterUrl empty, falling back to baseUrl")
            }
            
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
            
            var finalName = name
            var finalUrl = absoluteUrl
            
            if (!rule.formatJs.isNullOrBlank()) {
                sandbox.setResult(item)
                val formatResult = sandbox.eval(rule.formatJs!!)
                if (formatResult is String && formatResult.isNotEmpty()) {
                    finalName = formatResult
                }
            }

            val uploadDate = 0L // TODO: Parse updateTime if present

            ContentChapter(
                id = stableId,
                title = finalName,
                number = index.toFloat() + 1f,
                volume = 0,
                url = finalUrl,
                scanlator = null,
                uploadDate = uploadDate,
                branch = null,
                source = source
            )
        }
        
        android.util.Log.d(TAG, "Parsed ${chapters.size} chapters")
        android.util.Log.d(TAG, "===== BookChapterList.parse END =====")

        return ParseResult(chapters, nextPageUrls, shouldReverse)
    }

    private fun flattenNestedItems(items: List<Any>): List<Any> {
        if (items.isEmpty()) return items
        val out = ArrayList<Any>(items.size)
        val stack = ArrayDeque<Any>(items.size)
        for (item in items) stack.addLast(item)

        // Only flatten arrays/lists; keep primitive items as-is (they may be valid for some sources).
        // Limit the recursion depth implicitly by using an explicit stack with element-wise expansion.
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            when (current) {
                is List<*> -> {
                    for (child in current) {
                        if (child != null) stack.addFirst(child)
                    }
                }
                else -> out.add(current)
            }
        }
        return out
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
