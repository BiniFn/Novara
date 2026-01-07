package org.skepsun.kototoro.core.parser.legado

import android.util.Log
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import us.codecraft.xsoup.Xsoup

/**
 * XPath analyzer for complex HTML queries.
 * 
 * Based on legado-with-MD3 AnalyzeByXPath pattern.
 * Uses Xsoup for XPath evaluation.
 * 
 * Syntax: `//meta[@property='og:title']/@content`
 */
class AnalyzeByXPath(val content: Any) {
    
    companion object {
        private const val TAG = "AnalyzeByXPath"
    }
    
    private val doc = when (content) {
        is Element -> Jsoup.parse(content.outerHtml())
        is String -> Jsoup.parse(content)
        else -> Jsoup.parse("")
    }
    
    /**
     * Get string result from XPath rule
     */
    fun getString(rule: String): String {
        return getStringList(rule).joinToString("\n")
    }
    
    /**
     * Get string list from XPath rule
     */
    fun getStringList(rule: String): List<String> {
        if (rule.isBlank()) return emptyList()
        
        val xpath = rule.trim()
        
        return try {
            Xsoup.compile(xpath).evaluate(doc).list() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing XPath: $xpath", e)
            emptyList()
        }
    }
    
    /**
     * Get elements matching XPath
     */
    fun getElements(rule: String): List<Element> {
        if (rule.isBlank()) return emptyList()
        
        return try {
            Xsoup.compile(rule.trim()).evaluate(doc).elements
        } catch (e: Exception) {
            Log.e(TAG, "Error getting elements from XPath: $rule", e)
            emptyList()
        }
    }
    
    /**
     * Get single string from XPath
     */
    fun getStringFirst(rule: String): String? {
        return getStringList(rule).firstOrNull()
    }
}
