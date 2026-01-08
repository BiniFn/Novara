package org.skepsun.kototoro.core.parser.legado

import android.util.Log
import android.text.Html
import org.json.JSONObject
import org.skepsun.kototoro.core.util.splitNotBlank
import java.net.URL
import java.util.regex.Pattern
import org.jsoup.nodes.Element
import org.skepsun.kototoro.core.parser.legado.sandbox.LegadoSandbox

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
    }

    private var analyzeByJSoup: AnalyzeByJSoup? = null
    private var analyzeByJsonPath: AnalyzeByJsonPath? = null
    private var analyzeByXPath: AnalyzeByXPath? = null

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
                    result = when (sourceRule.mode) {
                        Mode.Js -> evalJS(rule, result)
                        Mode.Json -> getAnalyzeByJsonPath(result).getStringList(rule)
                        Mode.XPath -> getAnalyzeByXPath(result).getStringList(rule)
                        Mode.Default -> getAnalyzeByJSoup(result).getStringList(rule)
                        else -> rule
                    }
                }
                if (sourceRule.replaceRegex.isNotEmpty() && result is List<*>) {
                    result = result.map { item -> replaceRegex(item.toString(), sourceRule) }
                } else if (sourceRule.replaceRegex.isNotEmpty() && result != null) {
                    result = replaceRegex(result.toString(), sourceRule)
                }
            }
        }
        if (result == null) return null
        if (result is String) {
            result = result.split("\n")
        }
        if (isUrl) {
            val urlList = ArrayList<String>()
            if (result is List<*>) {
                for (url in result) {
                    val absoluteURL = resolveUrl(baseUrl, url.toString())
                    if (absoluteURL.isNotEmpty() && !urlList.contains(absoluteURL)) {
                        urlList.add(absoluteURL)
                    }
                }
            }
            return urlList
        }
        @Suppress("UNCHECKED_CAST")
        return result as? List<String>
    }

    /**
     * 获取文本
     */
    fun getString(ruleStr: String?, mContent: Any? = null, isUrl: Boolean = false): String {
        val result = getStringList(ruleStr, mContent, isUrl)
        if (!result.isNullOrEmpty()) {
            return if (isUrl) result[0] else result.joinToString("\n") { it }
        }
        return ""
    }

    fun getString(ruleStr: String?, unescape: Boolean): String {
        if (ruleStr.isNullOrBlank()) return ""
        val ruleList = splitSourceRuleCacheString(ruleStr)
        return getString(ruleList, unescape = unescape)
    }

    private fun getString(ruleList: List<SourceRule>, unescape: Boolean = false): String {
        val result = getStringList(ruleList)
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
    private fun getStringList(ruleList: List<SourceRule>): List<String>? {
        var result: Any? = null
        val content = this.content
        if (content != null && ruleList.isNotEmpty()) {
            result = content
            if (ruleList.size == 1) {
                val sourceRule = ruleList.first()
                putRule(sourceRule.putMap)
                sourceRule.makeUpRule(result)
                result = if (sourceRule.getParamSize() > 1) {
                    sourceRule.rule
                } else {
                    resolveIndexed(result, sourceRule.rule)
                }
                result?.let {
                    if (sourceRule.replaceRegex.isNotEmpty() && it is List<*>) {
                        result = it.map { o ->
                            replaceRegex(o.toString(), sourceRule)
                        }
                    } else if (sourceRule.replaceRegex.isNotEmpty()) {
                        result = replaceRegex(result.toString(), sourceRule)
                    }
                }
            } else {
                for (sourceRule in ruleList) {
                    putRule(sourceRule.putMap)
                    sourceRule.makeUpRule(result)
                    result ?: continue
                    val rule = sourceRule.rule
                    if (rule.isNotEmpty()) {
                        result = when (sourceRule.mode) {
                            Mode.Js -> evalJS(rule, result)
                            Mode.Json -> getAnalyzeByJsonPath(result).getStringList(rule)
                            Mode.XPath -> getAnalyzeByXPath(result).getStringList(rule)
                            Mode.Default -> getAnalyzeByJSoup(result).getStringList(rule)
                            else -> rule
                        }
                    }
                    if (sourceRule.replaceRegex.isNotEmpty() && result is List<*>) {
                        result = result.map { item -> replaceRegex(item.toString(), sourceRule) }
                    } else if (sourceRule.replaceRegex.isNotEmpty()) {
                        result = replaceRegex(result.toString(), sourceRule)
                    }
                }
            }
        }
        if (result == null) return null
        if (result is String) {
            result = result.split("\n")
        }
        @Suppress("UNCHECKED_CAST")
        return result as? List<String>
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
            val putJsonStr = putMatcher.group(1)
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
    private fun evalJS(rule: String, result: Any?): Any? {
        sandbox.putVariable("baseUrl", baseUrl)
        sandbox.setResult(result)
        val jsResult = sandbox.eval(rule)
        Log.d(TAG, "[evalJS] input=${result.toString().take(100)}, output=${jsResult.toString().take(100)}")
        return jsResult
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
     * 分离规则
     */
    private fun splitSourceRule(ruleStr: String, isElements: Boolean): List<SourceRule> {
        val ruleList = ArrayList<SourceRule>()
        val ruleAnalyzes = RuleAnalyzer(ruleStr, true)
        val ruleStrS = ruleAnalyzes.splitRule("&&", "||", "%%")
        val mode = when {
            ruleAnalyzes.elementsType == "||" -> Mode.XPath //用不到
            ruleAnalyzes.elementsType == "%%" -> Mode.Json //用不到
            else -> Mode.Default
        }
        for (rs in ruleStrS) {
            ruleList.addAll(splitJSToSourceRule(rs, isElements, mode))
        }
        return ruleList
    }

    /**
     * 拆分 js 到 SourceRule 列表
     */
    private fun splitJSToSourceRule(ruleStr: String, isElements: Boolean, mMode: Mode): List<SourceRule> {
        val ruleList = ArrayList<SourceRule>()
        var start = 0
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

                ruleStr.startsWith("$.") || ruleStr.startsWith("$[") -> {
                    mode = Mode.Json
                    ruleStr
                }

                ruleStr.startsWith("/") -> {
                    mode = Mode.XPath
                    ruleStr
                }

                else -> ruleStr
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
                            if (isRule(ruleParam[index])) {
                                val ruleList = splitSourceRuleCacheString(ruleParam[index])
                                getString(ruleList).let {
                                    infoVal.insert(0, it)
                                }
                            } else {
                                val jsEval: Any? = evalJS(ruleParam[index], result)
                                when {
                                    jsEval == null -> Unit
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

        private fun isRule(ruleStr: String): Boolean {
            return ruleStr.startsWith('@')
                    || ruleStr.startsWith("$.")
                    || ruleStr.startsWith("$[")
                    || ruleStr.startsWith("//")
        }

        fun getParamSize(): Int {
            return ruleParam.size
        }
    }

    enum class Mode {
        XPath, Json, Default, Js, Regex
    }

    private fun resolveIndexed(holder: Any?, key: String): Any? {
        return when (holder) {
            is Map<*, *> -> holder[key]
            is List<*> -> key.toIntOrNull()?.let { idx -> holder.getOrNull(idx) }
            else -> null
        }
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
