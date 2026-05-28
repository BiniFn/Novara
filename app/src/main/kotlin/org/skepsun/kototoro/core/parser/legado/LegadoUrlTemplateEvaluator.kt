package org.skepsun.kototoro.core.parser.legado

import org.skepsun.kototoro.core.parser.legado.runtime.LegadoJsEvaluator

/**
 * 处理 Legado URL 模板中的 JS、占位符和分页表达式。
 */
internal class LegadoUrlTemplateEvaluator(
    private val key: String?,
    private val page: Int,
    private val baseUrl: String,
    private val jsEvaluator: LegadoJsEvaluator,
) {

    fun evaluate(ruleUrl: String): String {
        if (ruleUrl.isBlank()) return ruleUrl
        val jsApplied = applyTopLevelJs(ruleUrl)
        return applyTemplateVars(jsApplied)
    }

    private fun applyTopLevelJs(ruleUrl: String): String {
        val matches = AnalyzeUrl.JS_PATTERN.findAll(ruleUrl).toList()
        if (matches.isEmpty()) return ruleUrl

        var start = 0
        var result = ruleUrl
        for (match in matches) {
            val matchStart = match.range.first
            if (matchStart > start) {
                val chunk = ruleUrl.substring(start, matchStart).trim()
                if (chunk.isNotEmpty()) {
                    result = chunk.replace("@result", result)
                }
            }
            val script = match.groups[1]?.value ?: match.groups[2]?.value ?: ""
            if (script.isNotBlank()) {
                result = AnalyzeUrl.jsValueToTemplateString(jsEvaluator.evaluate(script, result)) ?: ""
            }
            start = match.range.last + 1
        }

        if (ruleUrl.length > start) {
            val chunk = ruleUrl.substring(start).trim()
            if (chunk.isNotEmpty()) {
                result = chunk.replace("@result", result)
            }
        }
        return result
    }

    private fun applyTemplateVars(input: String): String {
        var result = input

        if (result.contains("{{") && result.contains("}}")) {
            val analyze = RuleAnalyzer(result)
            val resolved = analyze.innerRule("{{", "}}") { expression ->
                AnalyzeUrl.jsValueToTemplateString(jsEvaluator.evaluate(expression, null)) ?: ""
            }
            if (resolved.isNotEmpty()) {
                result = resolved
            }
        }

        val pagePattern = Regex("<(.*?)>")
        result = pagePattern.replace(result) { match ->
            val raw = match.groups[1]?.value?.trim().orEmpty()
            if (raw.isEmpty()) return@replace match.value
            val pages = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (pages.isEmpty()) return@replace match.value
            val index = page - 1
            if (index in pages.indices) pages[index] else pages.last()
        }

        return result
    }
}
