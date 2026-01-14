package org.skepsun.kototoro.core.parser.legado

import android.util.Log
import android.text.Html
import org.json.JSONObject
import org.skepsun.kototoro.core.util.splitNotBlank
import java.net.URL
import java.util.regex.Pattern
import org.jsoup.nodes.Element
import org.skepsun.kototoro.core.parser.legado.sandbox.LegadoSandbox
import java.util.regex.Matcher
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined

/**
 * Unified rule executor that delegates to appropriate analyzer based on rule syntax.
 * 
 * Based on legado-with-MD3 AnalyzeRule pattern.
 * Supports:
 * - JSoup/CSS: Default or @css:
 * - JSONPath: @json: or $.
 * - XPath: //
 * - JavaScript: <js> or @js:
 */
class AnalyzeRule(
    private var content: Any?,
    private val sandbox: LegadoSandbox,
    private var baseUrl: String = ""
) {
    companion object {
        private const val TAG = "AnalyzeRule"

        private val putPattern = Pattern.compile("@put:(\\{[^}]+?\\})", Pattern.CASE_INSENSITIVE)
        private val evalPattern = Pattern.compile("@get:\\{[^}]+?\\}|\\{\\{[\\w\\W]*?\\}\\}", Pattern.CASE_INSENSITIVE)
        private val regexPattern = Pattern.compile("\\$\\d{1,2}")
        private val JS_PATTERN =
            Pattern.compile("<js>([\\s\\S]*?)</js>|@js:([\\s\\S]*)", Pattern.CASE_INSENSITIVE)
        private val singleBracePlaceholderPattern = Pattern.compile("\\{([^{}]+)\\}")

        fun isRule(ruleStr: String): Boolean {
            if (ruleStr.isBlank()) return false
            val trimmed = ruleStr.trim()
            // Identify standard JSoup tags, class selectors, JSONPath, XPath, or script markers
            return trimmed.startsWith("@") || trimmed.startsWith("$") || trimmed.startsWith(".") || trimmed.startsWith("/")
                    || trimmed.startsWith("<js>", ignoreCase = true) || trimmed.startsWith("@js:", ignoreCase = true)
                    || trimmed.contains("@") || trimmed.contains("$") || trimmed.contains(".") || trimmed.contains("##") || trimmed.contains("{{")
                    || (trimmed.contains("//") && !trimmed.startsWith("http"))
                    || trimmed.startsWith("children", ignoreCase = true)
                    || trimmed.startsWith("textNodes", ignoreCase = true)
                    || (trimmed.isNotEmpty() && !trimmed.contains(" ") && trimmed.all { it.isLetterOrDigit() || it == '-' || it == '_' } && (trimmed.length > 3 || trimmed.startsWith("h") || trimmed.startsWith("a") || trimmed.startsWith("p") || trimmed.startsWith("li")))
        }
    }

    private var analyzeByJSoup: AnalyzeByJSoup? = null
    private var analyzeByJsonPath: AnalyzeByJsonPath? = null
    private var analyzeByXPath: AnalyzeByXPath? = null
    private var isRegex: Boolean = false

    private fun looksLikeJsonText(value: Any?): Boolean {
        val text = value as? String ?: return false
        val trimmed = text.trimStart()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    private fun looksLikeDottedJsonRule(rule: String): Boolean {
        val trimmed = rule.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("$") || trimmed.startsWith("@")) return false
        // Typical JSON dotted path like data.xxx.yyy (common in legacy Legado rules).
        // Avoid overly broad heuristics; only accept strict dotted identifiers.
        return trimmed.matches(Regex("[A-Za-z_][\\w-]*(\\.[A-Za-z_][\\w-]*)+"))
    }

    private fun toJsonPath(rule: String): String {
        val trimmed = rule.trim()
        if (trimmed.startsWith("$")) return trimmed
        if (trimmed.startsWith(".")) return "$$trimmed"
        return "$.$trimmed"
    }
    
    private fun previewForLog(value: Any?, limit: Int = 120): String {
        if (value == null) return "null"
        val text = value.toString()
        if (text.length <= limit) return text
        // URLs are the most common source of confusion when truncated; keep both head and tail.
        if (text.startsWith("http", ignoreCase = true) || text.startsWith("//")) {
            val headLen = 80.coerceAtMost(limit - 20)
            val tailLen = (limit - headLen - 1).coerceAtLeast(20)
            return text.take(headLen) + "…" + text.takeLast(tailLen)
        }
        return text.take(limit) + "…"
    }

    private fun previewRuleForLog(rule: String, limit: Int = 160): String {
        val normalized = rule.replace("\r", "").replace("\n", "\\n").trim()
        return previewForLog(normalized, limit)
    }

    init {
        setContent(content)
    }

    fun setContent(content: Any?, baseUrl: String? = null) {
        this.content = content
        if (baseUrl != null) {
            this.baseUrl = baseUrl
        }
        analyzeByJSoup = null
        analyzeByJsonPath = null
        analyzeByXPath = null
    }

    private fun getAnalyzeByJSoup(o: Any): AnalyzeByJSoup {
        return if (o != content) {
            AnalyzeByJSoup(o)
        } else {
            if (analyzeByJSoup == null) {
                analyzeByJSoup = AnalyzeByJSoup(content ?: "")
            }
            analyzeByJSoup!!
        }
    }

    private fun getAnalyzeByJsonPath(o: Any): AnalyzeByJsonPath {
        return if (o != content) {
            AnalyzeByJsonPath(o)
        } else {
            if (analyzeByJsonPath == null) {
                analyzeByJsonPath = AnalyzeByJsonPath(content ?: "")
            }
            analyzeByJsonPath!!
        }
    }

    private fun getAnalyzeByXPath(o: Any): AnalyzeByXPath {
        return if (o != content) {
            AnalyzeByXPath(o)
        } else {
            if (analyzeByXPath == null) {
                analyzeByXPath = AnalyzeByXPath(content ?: "")
            }
            analyzeByXPath!!
        }
    }

    /**
     * 解析文本列表
     */
    @Suppress("UNCHECKED_CAST")
    fun getStringList(ruleStr: String?, mContent: Any? = null, isUrl: Boolean = false): List<String>? {
        if (ruleStr.isNullOrBlank()) return null
        var result: Any? = null
        val ruleList = splitSourceRuleCacheString(ruleStr)
        val content = mContent ?: this.content
        if (content != null && ruleList.isNotEmpty()) {
            result = content
            // 遍历所有规则，根据mode调用对应的解析器
            for (sourceRule in ruleList) {
                putRule(sourceRule.putMap)
                sourceRule.makeUpRule(result)
                result ?: continue
                val rule = sourceRule.rule
                if (rule.isNotEmpty()) {
                    if ((sourceRule.mode == Mode.Default || sourceRule.mode == Mode.Regex) &&
                        (rule.startsWith("http", true) || rule.startsWith("//") || !isRule(rule))
                    ) {
                        result = rule
                        Log.d(TAG, "[getStringList] Rule is literal: $rule")
                    } else {
                        Log.d(
                            TAG,
                            "[getStringList] Executing mode=${sourceRule.mode}, rule=${previewRuleForLog(rule)} on content=${result?.javaClass?.simpleName}"
                        )
                        result = if (result is List<*> && sourceRule.mode != Mode.Js) {
                            val list = ArrayList<Any>()
                            for (item in result) {
                                if (item == null) continue
                                val itemResult = when (sourceRule.mode) {
                                    Mode.Json -> getAnalyzeByJsonPath(item).getStringList(rule)
                                    Mode.XPath -> getAnalyzeByXPath(item).getStringList(rule)
                                    Mode.Regex -> listOf(rule)
                                    else -> getAnalyzeByJSoup(item).getStringList(rule)
                                }
                                if (itemResult != null) list.addAll(itemResult)
                            }
                            list
                        } else {
                            val context = result ?: content
                            
                            // For JS mode, if input is a list, join it to string for legacy compatibility.
                            // Use '\n' to match legado behavior (many rules rely on result.split("\\n")).
                            val jsInput = if (sourceRule.mode == Mode.Js && context is List<*>) {
                                context.joinToString("\n")
                            } else context
                            
                            when (sourceRule.mode) {
                                Mode.Js -> evalJS(rule, jsInput)
                                Mode.Json -> getAnalyzeByJsonPath(context ?: "").getStringList(rule)
                                Mode.XPath -> getAnalyzeByXPath(context ?: "").getStringList(rule)
                                Mode.Regex -> listOf(rule)
                                else -> {
                                    if (context is Map<*, *> || context is List<*>) {
                                        val indexed = resolveIndexed(context, rule)
                                        if (indexed != null) {
                                            listOf(indexed.toString())
                                        } else if (looksLikeJsonText(context) && looksLikeDottedJsonRule(rule)) {
                                            getAnalyzeByJsonPath(context ?: "").getStringList(toJsonPath(rule))
                                        } else {
                                            getAnalyzeByJSoup(context ?: "").getStringList(rule)
                                        }
                                    } else
                                    // legado-with-MD3 兼容：当内容为 JSON 字符串、规则为 "data.xxx" 这类 dotted path 时，
                                    // 即使未显式标注 @json:/$ 前缀，也应按 JsonPath 执行。
                                    if (looksLikeJsonText(context) && looksLikeDottedJsonRule(rule)) {
                                        getAnalyzeByJsonPath(context ?: "").getStringList(toJsonPath(rule))
                                    } else {
                                        getAnalyzeByJSoup(context ?: "").getStringList(rule)
                                    }
                                }
                            }
                        }
                    }
                }
                Log.d(TAG, "[getStringList] Result after rule processing: ${previewForLog(result)}")
                if (sourceRule.replaceRegex.isNotEmpty() && result is List<*>) {
                    result = result.map { item -> replaceRegex(item.toString(), sourceRule) }
                } else if (sourceRule.replaceRegex.isNotEmpty() && result != null) {
                    result = replaceRegex(result.toString(), sourceRule)
                }
            }
        }
        if (result == null) return null
        if (result is Undefined) return null
        if (result is String) {
            result = result.split("\n")
        }
        if (isUrl) {
            val urlList = ArrayList<String>()
            val rawUrls: Iterable<Any?> = when (result) {
                is List<*> -> result
                is NativeArray -> (0 until result.length.toInt()).map { idx -> result.get(idx, result) }
                else -> listOf(result)
            }
            for (url in rawUrls) {
                if (url == null || url is Undefined) continue
                val absoluteURL = resolveUrl(baseUrl, url.toString())
                if (absoluteURL.isNotEmpty() && !urlList.contains(absoluteURL)) {
                    urlList.add(absoluteURL)
                }
            }
            return urlList
        }
        return when (result) {
            is List<*> -> result.mapNotNull { it?.takeIf { v -> v !is Undefined }?.toString() }
            is NativeArray -> (0 until result.length.toInt())
                .mapNotNull { idx -> result.get(idx, result)?.takeIf { v -> v !is Undefined }?.toString() }
            else -> listOf(result.toString())
        }
    }

    /**
     * 获取文本
     */
    fun getString(ruleStr: String?, mContent: Any? = null, isUrl: Boolean = false): String {
        val result = getStringList(ruleStr, mContent, isUrl)
        if (!result.isNullOrEmpty()) {
            val text = if (isUrl) result[0] else result.joinToString("\n") { it.toString() }
            if (text.isNotEmpty()) return text
        }
        val rulePreview = ruleStr?.let { previewRuleForLog(it) } ?: "null"
        Log.d(TAG, "[getString] Returned empty string for rule: $rulePreview")
        return ""
    }

    fun getString(ruleStr: String?, unescape: Boolean): String {
        if (ruleStr.isNullOrBlank()) return ""
        val ruleList = splitSourceRuleCacheString(ruleStr)
        return getString(ruleList, unescape = unescape)
    }

    private fun getString(ruleList: List<SourceRule>, mContent: Any? = null, unescape: Boolean = false): String {
        val result = getStringList(ruleList, mContent)
        var text: String? = null
        if (!result.isNullOrEmpty()) {
            text = result[0]
            if (result.size > 1) {
                for (i in 1 until result.size) {
                    text += if (unescape) {
                        "\n${Html.fromHtml(result[i], Html.FROM_HTML_MODE_LEGACY)}"
                    } else {
                        "\n${result[i]}"
                    }
                }
            }
        }
        return text ?: ""
    }

    /**
     * 获取文本列表
     */
    private fun getStringList(ruleList: List<SourceRule>, mContent: Any? = null): List<String>? {
        var result: Any? = null
        val content = mContent ?: this.content
        if (content != null && ruleList.isNotEmpty()) {
            result = content
            Log.d(TAG, "[getStringList(internal)] starting with content=${content.javaClass.simpleName}")
            
            for (sourceRule in ruleList) {
                putRule(sourceRule.putMap)
                sourceRule.makeUpRule(result)
                result ?: continue
                val rule = sourceRule.rule
                if (rule.isNotEmpty()) {
                    Log.d(
                        TAG,
                        "[getStringList(internal)] Executing mode=${sourceRule.mode}, rule=${previewRuleForLog(rule)} on result=${result?.javaClass?.simpleName}"
                    )
                    
                    // Handle list iteration if not in JavaScript mode
                    result = if (result is List<*> && sourceRule.mode != Mode.Js) {
                        val list = ArrayList<Any>()
                        for (item in result) {
                            if (item == null) continue
                            val itemResult = when (sourceRule.mode) {
                                Mode.Json -> getAnalyzeByJsonPath(item).getStringList(rule)
                                Mode.XPath -> getAnalyzeByXPath(item).getStringList(rule)
                                Mode.Regex -> listOf(rule)
                                else -> {
                                    if (item is Map<*, *>) {
                                        val indexed = resolveIndexed(item, rule)
                                        if (indexed != null) listOf(indexed.toString())
                                        else getAnalyzeByJSoup(item).getStringList(rule)
                                    } else {
                                        getAnalyzeByJSoup(item).getStringList(rule)
                                    }
                                }
                            }
                            if (itemResult != null) list.addAll(itemResult)
                        }
                        list
                    } else {
                        // For JS mode, if input is a list, join it to string for legacy compatibility.
                        // Use '\n' to match legado behavior (many rules rely on result.split("\\n")).
                        val jsInput = if (sourceRule.mode == Mode.Js && result is List<*>) {
                            result.joinToString("\n")
                        } else result
                        
                        when (sourceRule.mode) {
                            Mode.Js -> evalJS(rule, jsInput)
                            Mode.Json -> getAnalyzeByJsonPath(result ?: content!!).getStringList(rule)
                            Mode.XPath -> getAnalyzeByXPath(result ?: content!!).getStringList(rule)
                            Mode.Default -> {
                                if (rule.startsWith("http", true) || rule.startsWith("//")) {
                                    listOf(rule)
                                } else if (result is Map<*, *> || result is List<*>) {
                                    val indexed = resolveIndexed(result, rule)
                                    if (indexed != null) listOf(indexed.toString())
                                    else getAnalyzeByJSoup(result).getStringList(rule)
                                } else {
                                    getAnalyzeByJSoup(result).getStringList(rule)
                                }
                            }
                            Mode.Regex -> listOf(rule)
                            else -> rule
                        }
                    }
                }
                
                // Handle replacements
                if (sourceRule.replaceRegex.isNotEmpty() && result is List<*>) {
                    result = result.map { item -> replaceRegex(item.toString(), sourceRule) }
                } else if (sourceRule.replaceRegex.isNotEmpty() && result != null) {
                    result = replaceRegex(result.toString(), sourceRule)
                }
            }
        }
        
        if (result == null) return null
        if (result is String) {
            return result.split("\n")
        }
        @Suppress("UNCHECKED_CAST")
        return when (result) {
            is List<*> -> result.map { it.toString() }
            else -> listOf(result.toString())
        }
    }

    /**
     * 获取元素对象
     */
    @Suppress("UNCHECKED_CAST")
    fun getElements(ruleStr: String): List<Any> {
        var result: Any? = null
        val content = this.content
        val ruleList = splitSourceRule(ruleStr, true)
        if (content != null && ruleList.isNotEmpty()) {
            result = content
            for (sourceRule in ruleList) {
                putRule(sourceRule.putMap)
                result ?: continue
                val rule = sourceRule.rule
                if (rule.isBlank() && sourceRule.mode != Mode.Regex) continue

                // legado-with-MD3 对齐：getElements 不对 List 做隐式逐项映射，交由具体解析器/规则本身决定。
                result = when (sourceRule.mode) {
                    Mode.Regex -> AnalyzeByRegex.getElements(result.toString(), rule.splitNotBlank("&&"))
                    Mode.Js -> evalJS(rule, result)
                    Mode.Json -> getAnalyzeByJsonPath(result).getList(rule)
                    Mode.XPath -> getAnalyzeByXPath(result).getElements(rule)
                    else -> getAnalyzeByJSoup(result).getElements(rule)
                }
            }
        }
        result?.let {
            return it as List<Any>
        }
        return ArrayList()
    }

    /**
     * 保存变量
     */
    private fun putRule(map: Map<String, String>) {
        for ((key, value) in map) {
            put(key, getString(value))
        }
    }

    /**
     * 分离put规则
     */
    private fun splitPutRule(ruleStr: String, putMap: HashMap<String, String>): String {
        var vRuleStr = ruleStr
        val putMatcher = putPattern.matcher(vRuleStr)
        while (putMatcher.find()) {
            vRuleStr = vRuleStr.replace(putMatcher.group(), "")
            val putJsonStr = putMatcher.group(1) ?: continue
            try {
                val jsonObj = JSONObject(putJsonStr)
                jsonObj.keys().forEach { k ->
                    val keyStr = k.toString()
                    putMap[keyStr] = jsonObj.optString(keyStr)
                }
            } catch (_: Exception) {
            }
        }
        return vRuleStr
    }

    /**
     * 替换正则
     */
    private fun replaceRegex(result: String, sourceRule: SourceRule): String {
        return try {
            if (sourceRule.replaceRegex.isNotEmpty()) {
                if (sourceRule.replacement.isEmpty()) {
                    Regex(sourceRule.replaceRegex).replace(result, "")
                } else if (sourceRule.replaceFirst) {
                    Regex(sourceRule.replaceRegex).replaceFirst(result, sourceRule.replacement)
                } else {
                    Regex(sourceRule.replaceRegex).replace(result, sourceRule.replacement)
                }
            } else {
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Regex replacement failed", e)
            result
        }
    }

    /**
     * eval js
     */
    fun evalJS(rule: String, result: Any?): Any? {
        sandbox.putVariable("baseUrl", normalizeBaseUrlForJavaScript(baseUrl))
        sandbox.setResult(result)
        val jsResult = sandbox.eval(rule)
        Log.d(TAG, "[evalJS] input=${previewForLog(result)}, output=${previewForLog(jsResult)}")
        return jsResult
    }

    private fun normalizeBaseUrlForJavaScript(url: String): String {
        if (url.isBlank()) return url

        val trimmed = url.trim()
        val commaIndex = trimmed.indexOf(",{").let { if (it >= 0) it else trimmed.indexOf(", {") }
        val withoutOptions = if (commaIndex >= 0) trimmed.substring(0, commaIndex).trim() else trimmed

        val splitIndex = withoutOptions.indexOfAny(charArrayOf('?', '#'))
        val head = if (splitIndex >= 0) withoutOptions.substring(0, splitIndex) else withoutOptions
        val tail = if (splitIndex >= 0) withoutOptions.substring(splitIndex) else ""

        if (head.endsWith("/")) return head + tail
        if (head.matches(Regex(".*/\\d+"))) return head + "/" + tail
        return head + tail
    }

    /**
     * 保存变量
     */
    fun put(key: String, value: String): String {
        sandbox.putVariable(key, value)
        return value
    }

    /**
     * 获取保存的数据
     */
    fun get(key: String): String {
        sandbox.getVariable(key)?.let { return it }
        return ""
    }

    /**
     * 分离规则并缓存
     */
    private val stringRuleCache = hashMapOf<String, List<SourceRule>>()

    private fun splitSourceRuleCacheString(rule: String): List<SourceRule> {
        return stringRuleCache.getOrPut(rule) { splitSourceRule(rule, false) }
    }

    /**
     * 分离规则 (仅分离 JS 块，内部逻辑运算符由具体解析器处理)
     */
    private fun splitSourceRule(ruleStr: String, isElements: Boolean): List<SourceRule> {
        val ruleList = ArrayList<SourceRule>()
        var mode: Mode = Mode.Default
        var startIndex = 0
        // 与 legado-with-MD3 对齐：getElements/getElement 下，首字符为 ':' 时启用 AllInOne 正则模式
        if (isElements && ruleStr.startsWith(":")) {
            mode = Mode.Regex
            isRegex = true
            startIndex = 1
        } else if (isRegex) {
            mode = Mode.Regex
        }
        ruleList.addAll(splitJSToSourceRule(ruleStr, startIndex, mode))
        return ruleList
    }

    /**
     * 拆分 js 到 SourceRule 列表
     */
    private fun splitJSToSourceRule(ruleStr: String, startIndex: Int, mMode: Mode): List<SourceRule> {
        val ruleList = ArrayList<SourceRule>()
        var start = startIndex
        var tmp: String
        val jsMatcher = JS_PATTERN.matcher(ruleStr)

        while (jsMatcher.find()) {
            if (jsMatcher.start() > start) {
                tmp = ruleStr.substring(start, jsMatcher.start()).trim { it <= ' ' }
                if (tmp.isNotEmpty()) {
                    ruleList.add(SourceRule(tmp, mMode))
                }
            }
            ruleList.add(SourceRule(jsMatcher.group(2) ?: jsMatcher.group(1), Mode.Js))
            start = jsMatcher.end()
        }

        if (ruleStr.length > start) {
            tmp = ruleStr.substring(start).trim { it <= ' ' }
            if (tmp.isNotEmpty()) {
                ruleList.add(SourceRule(tmp, mMode))
            }
        }

        return ruleList
    }

    /**
     * 规则类（移植自 legado-with-MD3）
     */
    inner class SourceRule internal constructor(
        ruleStr: String,
        internal var mode: Mode = Mode.Default
    ) {
        internal var rule: String
        internal var replaceRegex = ""
        internal var replacement = ""
        internal var replaceFirst = false
        internal val putMap = HashMap<String, String>()
        private val ruleParam = ArrayList<String>()
        private val ruleType = ArrayList<Int>()
        private val getRuleType = -2
        private val jsRuleType = -1
        private val defaultRuleType = 0

        init {
            rule = when {
                mode == Mode.Js || mode == Mode.Regex -> ruleStr
                ruleStr.startsWith("@CSS:", true) -> {
                    mode = Mode.Default
                    ruleStr
                }

                ruleStr.startsWith("@@") -> {
                    mode = Mode.Default
                    ruleStr.substring(2)
                }

                ruleStr.startsWith("@XPath:", true) -> {
                    mode = Mode.XPath
                    ruleStr.substring(7)
                }

                ruleStr.startsWith("@Json:", true) -> {
                    mode = Mode.Json
                    ruleStr.substring(6)
                }

                ruleStr.startsWith("$") -> {
                    mode = Mode.Json
                    ruleStr
                }

                ruleStr.startsWith("/") -> {
                    mode = Mode.XPath
                    ruleStr
                }

	                else -> {
	                    // Detect if content is or looks like JSON
	                    val isJsonLike = content is JSONObject || content is org.json.JSONArray || 
	                                   content is Map<*, *> || content is List<*> ||
	                                   content is NativeObject || content is NativeArray ||
	                                   (content is String && (content.toString().trim().startsWith("{") || content.toString().trim().startsWith("[")))
	                    
	                    when {
	                        // Shorthand JSONPath like .member
	                        ruleStr.startsWith(".") && isJsonLike -> {
	                            mode = Mode.Json
	                            "\$$ruleStr"
	                        }
	                        // JSON dotted path without '$.' prefix, e.g. data.current_chapter.list[0]
	                        // Some Legado rules rely on this shorthand when response body is JSON.
	                        isJsonLike && looksLikeJsonDottedPath(ruleStr) -> {
	                            mode = Mode.Json
	                            "\$.$ruleStr"
	                        }
	                        // Simple key name (alphanumeric only, no special chars) when content is JSON
	                        // This handles rules like "name", "author", "url" when init returns JSON
	                        isJsonLike && ruleStr.isNotEmpty() && 
	                        ruleStr.all { it.isLetterOrDigit() || it == '_' } &&
	                        !ruleStr.contains("@") && !ruleStr.contains(".") -> {
	                            mode = Mode.Json
	                            Log.d(TAG, "[SourceRule] Converting simple key '$ruleStr' to JSONPath for JSON content")
	                            "\$.$ruleStr"
	                        }
	                        else -> ruleStr
	                    }
	                }
	            }
            //分离put
            rule = splitPutRule(rule, putMap)
            //@get,{{ }}, 拆分
            var start = 0
            var tmp: String
            val evalMatcher = evalPattern.matcher(rule)

            if (evalMatcher.find()) {
                tmp = rule.substring(start, evalMatcher.start())
                if (mode != Mode.Js && mode != Mode.Regex &&
                    (evalMatcher.start() == 0 || !tmp.contains("##"))
                ) {
                    mode = Mode.Regex
                }
                do {
                    if (evalMatcher.start() > start) {
                        tmp = rule.substring(start, evalMatcher.start())
                        splitRegex(tmp)
                    }
                    tmp = evalMatcher.group()
                    when {
                        tmp.startsWith("@get:", true) -> {
                            ruleType.add(getRuleType)
                            ruleParam.add(tmp.substring(6, tmp.lastIndex))
                        }

                        tmp.startsWith("{{") -> {
                            ruleType.add(jsRuleType)
                            ruleParam.add(tmp.substring(2, tmp.length - 2))
                        }

                        else -> {
                            splitRegex(tmp)
                        }
                    }
                    start = evalMatcher.end()
                } while (evalMatcher.find())
            }
            if (rule.length > start) {
                tmp = rule.substring(start)
                splitRegex(tmp)
            }
        }

        /**
         * 拆分\$\d{1,2}
         */
        private fun splitRegex(ruleStr: String) {
            var start = 0
            var tmp: String
            val ruleStrArray = ruleStr.split("##")
            val regexMatcher = regexPattern.matcher(ruleStrArray[0])

            if (regexMatcher.find()) {
                if (mode != Mode.Js && mode != Mode.Regex) {
                    mode = Mode.Regex
                }
                do {
                    if (regexMatcher.start() > start) {
                        tmp = ruleStr.substring(start, regexMatcher.start())
                        ruleType.add(defaultRuleType)
                        ruleParam.add(tmp)
                    }
                    tmp = regexMatcher.group()
                    ruleType.add(tmp.substring(1).toInt())
                    ruleParam.add(tmp)
                    start = regexMatcher.end()
                } while (regexMatcher.find())
            }
            if (ruleStr.length > start) {
                tmp = ruleStr.substring(start)
                ruleType.add(defaultRuleType)
                ruleParam.add(tmp)
            }
        }

        /**
         * 替换@get,{{ }}
         */
        fun makeUpRule(result: Any?) {
            val infoVal = StringBuilder()
            if (ruleParam.isNotEmpty()) {
                var index = ruleParam.size
                while (index-- > 0) {
                    val regType = ruleType[index]
                    when {
                        regType > defaultRuleType -> {
                            @Suppress("UNCHECKED_CAST")
                            (result as? List<String?>)?.run {
                                if (this.size > regType) {
                                    this[regType]?.let {
                                        infoVal.insert(0, it)
                                    }
                                }
                            } ?: infoVal.insert(0, ruleParam[index])
                        }

                        regType == jsRuleType -> {
                            val expr = ruleParam[index].trim()
                            val looksLikeRuleExpr =
                                expr.startsWith("$") ||
                                    expr.startsWith(".") ||
                                    expr.startsWith("/") ||
                                    expr.startsWith("@", ignoreCase = true) ||
                                    (looksLikeJsonText(result) && looksLikeDottedJsonRule(expr))

                            if (looksLikeRuleExpr) {
                                val ruleList = splitSourceRuleCacheString(expr)
                                infoVal.insert(0, getString(ruleList, result))
                            } else {
                                val jsEval: Any? = evalJS(expr, result)
                                when {
                                    jsEval == null || jsEval is Undefined -> Unit
                                    jsEval is String -> infoVal.insert(0, jsEval)
                                    jsEval is Double && jsEval % 1.0 == 0.0 -> infoVal.insert(
                                        0,
                                        String.format(java.util.Locale.ROOT, "%.0f", jsEval)
                                    )
                                    else -> infoVal.insert(0, jsEval.toString())
                                }
                            }
                        }

                        regType == getRuleType -> {
                            infoVal.insert(0, get(ruleParam[index]))
                        }

                        else -> infoVal.insert(0, ruleParam[index])
                    }
                }
                rule = infoVal.toString()
            }
            rule = replaceSingleBracePlaceholders(rule, result)
            //分离正则表达式
            val ruleStrS = rule.split("##")
            rule = ruleStrS[0].trim()
            if (ruleStrS.size > 1) {
                replaceRegex = ruleStrS[1]
            }
            if (ruleStrS.size > 2) {
                replacement = ruleStrS[2]
            }
            if (ruleStrS.size > 3) {
                replaceFirst = true
            }
        }



        fun getParamSize(): Int {
            return ruleParam.size
        }
    }

    enum class Mode {
        XPath, Json, Default, Js, Regex
    }

    private fun looksLikeJsonDottedPath(rule: String): Boolean {
        if (rule.isBlank()) return false
        if (rule.startsWith("$") || rule.startsWith("/") || rule.startsWith("@", ignoreCase = true)) return false
        if (rule.contains("##") || rule.contains("{{") || rule.contains("}}")) return false
        if (rule.startsWith("http", ignoreCase = true) || rule.startsWith("//")) return false
        // Allow segments like a_b, a1, list[0], list[*]
        return rule.matches(Regex("^[A-Za-z_][A-Za-z0-9_\\[\\]\\*]*(\\.[A-Za-z_][A-Za-z0-9_\\[\\]\\*]*)*\$"))
    }

    private fun replaceSingleBracePlaceholders(template: String, context: Any?): String {
        if (!template.contains('{') || !template.contains('}')) return template
        if (context !is Map<*, *> && context !is JSONObject) return template

        val matcher = singleBracePlaceholderPattern.matcher(template)
        if (!matcher.find()) return template
        matcher.reset()

        val sb = StringBuffer(template.length)
        while (matcher.find()) {
            val expr = matcher.group(1)?.trim().orEmpty()
            val replacement = when {
                expr.startsWith("$") || expr.startsWith(".") -> {
                    val jsonPath = if (expr.startsWith(".")) "\$$expr" else expr
                    getAnalyzeByJsonPath(context).getStringList(jsonPath)?.firstOrNull().orEmpty()
                }
                context is Map<*, *> -> context[expr]?.toString().orEmpty()
                context is JSONObject -> context.opt(expr)?.toString().orEmpty()
                else -> ""
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun resolveIndexed(holder: Any?, key: String): Any? {
        return when (holder) {
            is Map<*, *> -> holder[key]
            is List<*> -> key.toIntOrNull()?.let { idx -> holder.getOrNull(idx) }
            else -> null
        }
    }

    /**
     * Attempts to unwrap common JSON wrappers like {"code": 200, "data": [...]}
     */
    private fun unwrapJson(content: Any?): Any? {
        if (content == null) return null
        
        // If content is already a JSONObject/JSONArray, we might need to look inside
        val json = when (content) {
            is String -> {
                val trimmed = content.trim()
                if (trimmed.startsWith("{")) JSONObject(trimmed)
                else if (trimmed.startsWith("[")) org.json.JSONArray(trimmed)
                else return content
            }
            else -> content
        }

        if (json is JSONObject) {
            val keys = listOf("data", "result", "list", "items", "book", "chapters", "manga")
            for (key in keys) {
                if (json.has(key) && json.length() <= 10) { 
                    val unwrapped = json.get(key)
                    Log.d(TAG, "unwrapping JSON via key '$key', result type=${unwrapped?.javaClass?.simpleName}")
                    return unwrapped
                }
            }
        }
        
        return content
    }

    private fun resolveUrl(base: String?, relative: String): String {
        if (relative.isBlank()) return relative
        if (relative.startsWith("http")) return relative
        return try {
            URL(URL(base ?: ""), relative).toString()
        } catch (_: Exception) {
            relative
        }
    }
}
