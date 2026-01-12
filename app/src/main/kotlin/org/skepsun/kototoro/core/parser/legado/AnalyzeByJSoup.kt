package org.skepsun.kototoro.core.parser.legado

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Collector
import org.jsoup.select.Elements
import org.jsoup.select.Evaluator
import org.skepsun.kototoro.core.util.splitNotBlank

/**
 * JSoup 解析（对齐 legado-with-MD3 的核心选择能力，精简日志与依赖）
 */
class AnalyzeByJSoup(private val element: Element) {

    constructor(content: Any) : this(
        when (content) {
            is Element -> content
            is String -> Jsoup.parse(content)
            else -> Jsoup.parse(content.toString())
        }
    )

    internal fun getElements(rule: String) = getElements(element, rule)

    internal fun getString(rule: String): List<String> {
        val textS = arrayListOf<String>()
        val sourceRule = SourceRule(rule)
        val ruleAnalyzes = RuleAnalyzer(sourceRule.elementsRule)
        val ruleStrS = ruleAnalyzes.splitRule("&&", "||", "%%")
        if (ruleStrS.isEmpty()) return textS

        val results = ArrayList<List<String>>()
        for (ruleStrX in ruleStrS) {

            val temp: ArrayList<String>? =
                if (sourceRule.isCss) {
                    val lastIndex = ruleStrX.lastIndexOf('@')
                    getResultLast(
                        element.select(ruleStrX.substring(0, lastIndex)),
                        ruleStrX.substring(lastIndex + 1)
                    )
                } else {
                    getResultList(ruleStrX)
                }

            if (!temp.isNullOrEmpty()) {
                results.add(temp)
                if (ruleAnalyzes.elementsType == "||") break
            }
        }
        if (results.size > 0) {
            if ("%%" == ruleAnalyzes.elementsType) {
                for (i in results[0].indices) {
                    for (temp in results) {
                        if (i < temp.size) {
                            textS.add(temp[i])
                        }
                    }
                }
            } else {
                for (temp in results) {
                    textS.addAll(temp)
                }
            }
        }
        return textS
    }
    
    /**
     * 合并内容列表,得到内容
     */
    internal fun getStringResult(ruleStr: String): String? {
        if (ruleStr.isEmpty()) {
            return null
        }
        val list = getString(ruleStr)
        if (list.isEmpty()) {
            return null
        }
        if (list.size == 1) {
            return list.first()
        }
        return list.joinToString("\n")
    }

    /**
     * 获取一个字符串
     */
    internal fun getString0(ruleStr: String) =
        getString(ruleStr).let { if (it.isEmpty()) "" else it[0] }
    
    /**
     * 获取string list别名（兼容老代码）
     */
    internal fun getStringList(rule: String): List<String> = getString(rule)

    /**
     * 获取Elements
     */
    internal fun getElements(temp: Element?, rule: String): Elements {

        if (temp == null || rule.isEmpty()) return Elements()

        val elements = Elements()

        val sourceRule = SourceRule(rule)
        val ruleAnalyzes = RuleAnalyzer(sourceRule.elementsRule)
        val ruleStrS = ruleAnalyzes.splitRule("&&", "||", "%%")

        val elementsList = ArrayList<Elements>()
        if (sourceRule.isCss) {
            for (ruleStr in ruleStrS) {
                val tempS = temp.select(ruleStr)
                elementsList.add(tempS)
                if (tempS.size > 0 && ruleAnalyzes.elementsType == "||") {
                    break
                }
            }
        } else {
            for (ruleStr in ruleStrS) {

                val rsRule = RuleAnalyzer(ruleStr)

                rsRule.trim()  // 修剪当前规则之前的"@"或者空白符

                val rs = rsRule.splitRule("@")

                val el = if (rs.size > 1) {
                    val el = Elements()
                    el.add(temp)
                    for (rl in rs) {
                        val es = Elements()
                        for (et in el) {
                            es.addAll(getElements(et, rl))
                        }
                        el.clear()
                        el.addAll(es)
                    }
                    el
                } else ElementsSingle().getElementsSingle(temp, ruleStr)

                elementsList.add(el)
                if (el.size > 0 && ruleAnalyzes.elementsType == "||") {
                    break
                }
            }
        }
        if (elementsList.size > 0) {
            if ("%%" == ruleAnalyzes.elementsType) {
                for (i in 0 until elementsList[0].size) {
                    for (es in elementsList) {
                        if (i < es.size) {
                            elements.add(es[i])
                        }
                    }
                }
            } else {
                for (es in elementsList) {
                    elements.addAll(es)
                }
            }
        }
        return elements
    }

    /**
     * 获取内容列表
     */
    private fun getResultList(ruleStr: String): ArrayList<String>? {

        if (ruleStr.isEmpty()) return null

        var elements = Elements()

        elements.add(element)

        // 预处理规则：将 "a.href" 或 "img.src" 等格式转换为 "a@href" 或 "img@src"
        val processedRuleStr = preprocessRule(ruleStr)

        val rule = RuleAnalyzer(processedRuleStr) //创建解析

        rule.trim() //修建前置赘余符号

        val rules = rule.splitRule("@") // 切割成列表
        
        android.util.Log.d("AnalyzeByJSoup", "[getResultList] ruleStr=$ruleStr, processedRuleStr=$processedRuleStr, rules=${rules.joinToString(", ")}")

        val last = rules.size - 1
        for (i in 0 until last) {
            val es = Elements()
            for (elt in elements) {
                val found = ElementsSingle().getElementsSingle(elt, rules[i])
                android.util.Log.v("AnalyzeByJSoup", "[getResultList] elt[${elt.tagName()}] sub-rule='${rules[i]}' found=${found.size}")
                es.addAll(found)
            }
            android.util.Log.d("AnalyzeByJSoup", "[getResultList] After rule[${i}]='${rules[i]}', found ${es.size} elements")
            elements = es
        }
        android.util.Log.d("AnalyzeByJSoup", "[getResultList] Final elements count=${elements.size}, lastRule=${rules[last]}")
        return if (elements.isEmpty()) null else getResultLast(elements, rules[last])
    }

    /**
     * 根据最后一个规则获取内容
     */
    private fun getResultLast(elements: Elements, lastRule: String): ArrayList<String> {
        val textS = ArrayList<String>()
        when (lastRule) {
            "text" -> for (element in elements) {
                val text = element.text()
                if (text.isNotEmpty()) {
                    textS.add(text)
                }
            }

            "textNodes" -> for (element in elements) {
                val tn = arrayListOf<String>()
                val contentEs = element.textNodes()
                for (item in contentEs) {
                    val text = item.text().trim { it <= ' ' }
                    if (text.isNotEmpty()) {
                        tn.add(text)
                    }
                }
                if (tn.isNotEmpty()) {
                    textS.add(tn.joinToString("\n"))
                }
            }

            "ownText" -> for (element in elements) {
                val text = element.ownText()
                if (text.isNotEmpty()) {
                    textS.add(text)
                }
            }

            "html" -> {
                // 与 legado-with-MD3 保持一致：移除 script 和 style，然后获取外部 HTML
                elements.select("script").remove()
                elements.select("style").remove()
                val html = elements.outerHtml()
                if (html.isNotEmpty()) {
                    textS.add(html)
                }
            }

            "outerHtml" -> {
                for (element in elements) {
                    val html = element.outerHtml()
                    if (html.isNotEmpty()) {
                        textS.add(html)
                    }
                }
            }
            
            "all" -> {
                val html = elements.outerHtml()
                if (html.isNotEmpty()) {
                    textS.add(html)
                }
            }

            else -> if (lastRule.isNotEmpty()) for (element in elements) {
                val text = element.attr(lastRule)
                if (text.isNotEmpty()) {
                    textS.add(text)
                }
            }
        }
        return textS
    }

    /**
     * Rule 选择器处理
     */
    internal inner class SourceRule(ruleStr: String) {
        var isCss = false
        var elementsRule: String = if (ruleStr.startsWith("@CSS:", true)) {
            isCss = true
            ruleStr.substring(5).trim { it <= ' ' }
        } else {
            ruleStr
        }
    }

    /**
     * 单条规则解析（移植 ElementsSingle）
     */
    internal inner class ElementsSingle {
        private var beforeRule: String = ""
        private var split: Char = ' '
        private val indexes = mutableListOf<Any>()
        private val indexDefault = mutableListOf<Int>()

        fun getElementsSingle(temp: Element, rule: String): Elements {
            findIndexSet(rule)
            
            var elements = if (beforeRule.isEmpty()) {
                temp.children()
            } else {
                val splitIndex = beforeRule.indexOfFirst { it == '.' || it == ':' || it == ' ' }
                val (cmd, arg) = if (splitIndex != -1) {
                    beforeRule.substring(0, splitIndex) to beforeRule.substring(splitIndex + 1).trim()
                } else {
                    beforeRule to ""
                }

                when (cmd) {
                    "children" -> temp.children()
                    "class" -> {
                        if (arg.isEmpty()) temp.children()
                        else {
                            val classNames = arg.splitNotBlank(" ")
                            if (classNames.size > 1) {
                                temp.select(classNames.joinToString(separator = ".", prefix = "."))
                            } else {
                                temp.getElementsByClass(arg)
                            }
                        }
                    }
                    "id" -> {
                        if (arg.isEmpty()) temp.children()
                        else {
                            val idNames = arg.splitNotBlank(" ")
                            if (idNames.size > 1) {
                                temp.select(idNames.joinToString(separator = ", #", prefix = "#"))
                            } else {
                                Collector.collect(Evaluator.Id(arg), temp)
                            }
                        }
                    }
                    "tag" -> if (arg.isEmpty()) temp.children() else temp.getElementsByTag(arg)
                    "text" -> if (arg.isEmpty()) temp.children() else temp.getElementsContainingOwnText(arg)
                    else -> temp.select(beforeRule)
                }
            }

            val len = elements.size
            if (len == 0) return elements
            
            // Default to return all if no indexes provided
            if (indexes.isEmpty() && indexDefault.isEmpty()) {
                return if (split == '!') Elements() else elements
            }
            
            val lastIndexes = (indexDefault.size - 1).takeIf { it != -1 } ?: (indexes.size - 1)
            val indexSet = mutableSetOf<Int>()

            if (indexes.isEmpty()) {
                for (ix in lastIndexes downTo 0) {
                    val it = indexDefault[ix]
                    if (it in 0 until len) indexSet.add(it)
                    else if (it < 0 && len >= -it) indexSet.add(it + len)
                }
            } else {
                for (ix in lastIndexes downTo 0) {
                    if (indexes[ix] is Triple<*, *, *>) {
                        val (startX, endX, stepX) = indexes[ix] as Triple<Int?, Int?, Int>
                        var start = startX ?: 0
                        if (start < 0) start += len
                        var end = endX ?: (len - 1)
                        if (end < 0) end += len
                        if ((start < 0 && end < 0) || (start >= len && end >= len)) continue
                        if (start >= len) start = len - 1
                        else if (start < 0) start = 0
                        if (end >= len) end = len - 1
                        else if (end < 0) end = 0
                        if (start == end || stepX >= len) {
                            indexSet.add(start)
                            continue
                        }
                        val step = if (stepX > 0) stepX else if (-stepX < len) stepX + len else 1
                        indexSet.addAll(if (end > start) start..end step step else start downTo end step step)
                    } else {
                        val it = indexes[ix] as Int
                        if (it in 0 until len) indexSet.add(it)
                        else if (it < 0 && len >= -it) indexSet.add(it + len)
                    }
                }
            }

            if (split == '!') {
                val filtered = Elements()
                elements.forEachIndexed { idx, el ->
                    if (!indexSet.contains(idx)) filtered.add(el)
                }
                elements = filtered
            } else if (split == '.') {
                val es = Elements()
                for (pcInt in indexSet) es.add(elements[pcInt])
                elements = es
            }

            return elements
        }

        private fun findIndexSet(rule: String) {
            val rus = rule.trim()
            if (rus.isEmpty()) {
                beforeRule = ""
                split = ' '
                return
            }

            indexes.clear()
            indexDefault.clear()
            
            if (rus.endsWith("]")) {
                val openBracket = rus.lastIndexOf('[')
                if (openBracket != -1) {
                    val indexStr = rus.substring(openBracket + 1, rus.length - 1)
                    val before = rus.substring(0, openBracket).trim()
                    if (before.endsWith('!')) {
                        beforeRule = before.dropLast(1).trim()
                        split = '!'
                    } else {
                        beforeRule = before
                        split = '.'
                    }
                    parseIndexString(indexStr, indexes)
                    return
                }
            }

            val lastDot = rus.lastIndexOf('.')
            val lastExcl = rus.lastIndexOf('!')
            val splitIdx = maxOf(lastDot, lastExcl)
            
            if (splitIdx != -1) {
                val possibleIndex = rus.substring(splitIdx + 1).trim()
                if (possibleIndex.isNotEmpty() && (possibleIndex[0].isDigit() || (possibleIndex[0] == '-' && possibleIndex.length > 1 && possibleIndex[1].isDigit()))) {
                    val idx = possibleIndex.toIntOrNull()
                    if (idx != null) {
                        indexDefault.add(idx)
                        split = rus[splitIdx]
                        beforeRule = rus.substring(0, splitIdx).trim()
                        return
                    }
                }
            }

            split = ' '
            beforeRule = rus
        }

        private fun parseIndexString(s: String, target: MutableList<Any>) {
            s.split(',').forEach { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty()) return@forEach
                if (trimmed.contains(':')) {
                    val range = trimmed.split(':')
                    val start = range.getOrNull(0)?.toIntOrNull()
                    val end = range.getOrNull(1)?.toIntOrNull()
                    val step = range.getOrNull(2)?.toIntOrNull() ?: 1
                    target.add(Triple(start, end, step))
                } else {
                    trimmed.toIntOrNull()?.let { target.add(it) }
                }
            }
        }
    }

    companion object {
        // 常见的 HTML 属性，用于判断 "a.href" 是否应该转换为 "a@href"
        private val COMMON_ATTRS = setOf(
            "href", "src", "data-src", "data-original", "data-lazy-src",
            "alt", "title", "class", "id", "name", "value", "type",
            "content", "rel", "target", "action", "method", "placeholder",
            "style", "width", "height", "data-setbg", "data-bg", "poster"
        )
        
        /**
         * 预处理规则：将 "a.href" 格式转换为 "a@href"
         * 仅当 "." 后面是已知的 HTML 属性时才转换
         */
        private fun preprocessRule(ruleStr: String): String {
            // 如果已经包含 @，不需要转换
            if (ruleStr.contains("@")) return ruleStr
            
            // 检查是否是 "tag.attr" 格式（不含空格，且 . 后面是常见属性）
            val dotIndex = ruleStr.lastIndexOf('.')
            if (dotIndex > 0 && dotIndex < ruleStr.length - 1) {
                val beforeDot = ruleStr.substring(0, dotIndex)
                val afterDot = ruleStr.substring(dotIndex + 1)
                
                // 确保 beforeDot 是简单标签名（不含特殊字符）
                // 且 afterDot 是已知的 HTML 属性
                if (beforeDot.isNotBlank() && 
                    !beforeDot.contains(" ") && 
                    !beforeDot.contains("[") &&
                    COMMON_ATTRS.contains(afterDot.lowercase())) {
                    return "$beforeDot@$afterDot"
                }
            }
            
            return ruleStr
        }
    }

}
