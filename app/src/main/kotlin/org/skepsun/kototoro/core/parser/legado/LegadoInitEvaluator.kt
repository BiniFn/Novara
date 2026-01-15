package org.skepsun.kototoro.core.parser.legado

/**
 * Legado 规则中的 init/preUpdateJs 既可能是 JS，也可能是 JsonPath/CSS/XPath 规则。
 *
 * 为了保持通用性，这里仅做两类最常见能力：
 * - JS：包含 `<js>...</js>` 或 `@js:` 时按脚本执行；
 * - JSON：当输入内容是 JSON（String/Map/List/...）时，将 init 视作 JsonPath 并返回对象结果。
 *
 * 其它类型（CSS/XPath）目前不作为 init 的通用入口，避免与站点 HTML 规则产生歧义。
 */
internal object LegadoInitEvaluator {

    fun applyInitIfPresent(
        analyzeRule: AnalyzeRule,
        initRule: String?,
        input: Any,
    ): Any? {
        val rule = initRule?.trim().orEmpty()
        if (rule.isEmpty()) return null

        // JS init
        if (looksLikeJsRule(rule)) {
            return analyzeRule.evalJS(rule, input)
        }

        // JSON init
        if (looksLikeJson(input)) {
            val jsonPath = normalizeToJsonPath(rule)
            return AnalyzeByJsonPath(input).getObject(jsonPath)
        }

        return null
    }

    private fun looksLikeJsRule(rule: String): Boolean {
        val trimmed = rule.trim()
        return trimmed.startsWith("@js:", ignoreCase = true) ||
            trimmed.startsWith("<js>", ignoreCase = true) ||
            trimmed.contains("</js>", ignoreCase = true)
    }

    private fun looksLikeJson(input: Any): Boolean {
        return when (input) {
            is Map<*, *>,
            is List<*>,
            is org.mozilla.javascript.NativeObject,
            is org.mozilla.javascript.NativeArray,
            -> true
            is String -> {
                val trimmed = input.trimStart()
                trimmed.startsWith("{") || trimmed.startsWith("[")
            }
            else -> {
                val className = input.javaClass.name
                className.contains("org.json.JSONObject") || className.contains("org.json.JSONArray")
            }
        }
    }

    private fun normalizeToJsonPath(rule: String): String {
        val trimmed = rule.trim()
        val withoutPrefix = if (trimmed.startsWith("@Json:", ignoreCase = true)) {
            trimmed.substring(6).trim()
        } else trimmed
        return when {
            withoutPrefix.startsWith("$") -> withoutPrefix
            withoutPrefix.startsWith(".") -> "\$${withoutPrefix}"
            else -> "\$.${withoutPrefix}"
        }
    }
}
