package org.skepsun.kototoro.core.parser.legado

import android.util.Log
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.select.Elements
import us.codecraft.xsoup.Xsoup

/**
 * XPath analyzer for complex HTML queries.
 * 
 * Aligned with legado-with-MD3 AnalyzeByXPath pattern.
 * Supports && || %% rule combination.
 * Uses Xsoup for XPath evaluation.
 * 
 * Syntax: `//meta[@property='og:title']/@content`
 */
class AnalyzeByXPath(doc: Any) {
    
    companion object {
        private const val TAG = "AnalyzeByXPath"
    }
    
    private var document: Document = parse(doc)
    
    private fun parse(doc: Any): Document {
        return when (doc) {
            is Document -> doc
            is Element -> Jsoup.parse(doc.outerHtml())
            is Elements -> Jsoup.parse(doc.outerHtml())
            is String -> strToDocument(doc)
            else -> strToDocument(doc.toString())
        }
    }
    
    private fun strToDocument(html: String): Document {
        var html1 = html
        if (html1.endsWith("</td>")) {
            html1 = "<tr>${html1}</tr>"
        }
        if (html1.endsWith("</tr>") || html1.endsWith("</tbody>")) {
            html1 = "<table>${html1}</table>"
        }
        kotlin.runCatching {
            if (html1.trim().startsWith("<?xml", true)) {
                return Jsoup.parse(html1, Parser.xmlParser())
            }
        }
        return Jsoup.parse(html1)
    }
    
    private fun getResult(xPath: String): List<String> {
        return try {
            Xsoup.compile(xPath).evaluate(document).list() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing XPath: $xPath", e)
            emptyList()
        }
    }
    
    private fun getElements0(xPath: String): Elements {
        return try {
            Xsoup.compile(xPath).evaluate(document).elements ?: Elements()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting elements from XPath: $xPath", e)
            Elements()
        }
    }
    
    /**
     * Get elements matching XPath with && || %% support
     */
    fun getElements(xPath: String): List<Element> {
        if (xPath.isEmpty()) return emptyList()

        val elements = ArrayList<Element>()
        val ruleAnalyzes = RuleAnalyzer(xPath)
        val rules = ruleAnalyzes.splitRule("&&", "||", "%%")

        if (rules.size == 1) {
            return getElements0(rules[0]).toList()
        } else {
            val results = ArrayList<List<Element>>()
            for (rl in rules) {
                val temp = getElements(rl)
                if (temp.isNotEmpty()) {
                    results.add(temp)
                    if (temp.isNotEmpty() && ruleAnalyzes.elementsType == "||") {
                        break
                    }
                }
            }
            if (results.size > 0) {
                if ("%%" == ruleAnalyzes.elementsType) {
                    for (i in results[0].indices) {
                        for (temp in results) {
                            if (i < temp.size) {
                                elements.add(temp[i])
                            }
                        }
                    }
                } else {
                    for (temp in results) {
                        elements.addAll(temp)
                    }
                }
            }
        }
        return elements
    }
    
    /**
     * Get string list from XPath with && || %% support
     */
    fun getStringList(xPath: String): List<String> {
        val result = ArrayList<String>()
        val ruleAnalyzes = RuleAnalyzer(xPath)
        val rules = ruleAnalyzes.splitRule("&&", "||", "%%")

        if (rules.size == 1) {
            return getResult(xPath)
        } else {
            val results = ArrayList<List<String>>()
            for (rl in rules) {
                val temp = getStringList(rl)
                if (temp.isNotEmpty()) {
                    results.add(temp)
                    if (temp.isNotEmpty() && ruleAnalyzes.elementsType == "||") {
                        break
                    }
                }
            }
            if (results.size > 0) {
                if ("%%" == ruleAnalyzes.elementsType) {
                    for (i in results[0].indices) {
                        for (temp in results) {
                            if (i < temp.size) {
                                result.add(temp[i])
                            }
                        }
                    }
                } else {
                    for (temp in results) {
                        result.addAll(temp)
                    }
                }
            }
        }
        return result
    }
    
    /**
     * Get string from XPath with && || support
     */
    fun getString(rule: String): String? {
        val ruleAnalyzes = RuleAnalyzer(rule)
        val rules = ruleAnalyzes.splitRule("&&", "||")
        if (rules.size == 1) {
            val result = getResult(rule)
            return if (result.isNotEmpty()) result.joinToString("\n") else null
        } else {
            val textList = arrayListOf<String>()
            for (rl in rules) {
                val temp = getString(rl)
                if (!temp.isNullOrEmpty()) {
                    textList.add(temp)
                    if (ruleAnalyzes.elementsType == "||") {
                        break
                    }
                }
            }
            return textList.joinToString("\n")
        }
    }
    
    /**
     * Get single string from XPath
     */
    fun getStringFirst(rule: String): String? {
        return getStringList(rule).firstOrNull()
    }
}
