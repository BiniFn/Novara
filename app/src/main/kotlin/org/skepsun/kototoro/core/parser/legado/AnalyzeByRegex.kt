package org.skepsun.kototoro.core.parser.legado

object AnalyzeByRegex {
    fun getElements(text: String, patterns: List<String>): List<Any> {
        val results = mutableListOf<String>()
        patterns.forEach { pat ->
            val regex = Regex(pat)
            regex.findAll(text).forEach { match ->
                results.add(match.value)
            }
        }
        return results
    }
}
