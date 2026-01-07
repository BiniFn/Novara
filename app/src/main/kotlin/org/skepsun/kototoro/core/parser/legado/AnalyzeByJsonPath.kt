package org.skepsun.kototoro.core.parser.legado

import android.util.Log
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.ReadContext

/**
 * JSONPath analyzer for API responses.
 * Supports standard JSONPath syntax.
 * 
 * Based on legado-with-MD3 AnalyzeByJSonPath pattern.
 * 
 * Syntax: `$.data.list[*].name` or `@json:$.data.list[*].name`
 */
class AnalyzeByJsonPath(content: Any) {
    
    companion object {
        private const val TAG = "AnalyzeByJsonPath"
        
        private val jsonPathConfig: Configuration = Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS)
            .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
            .build()
    }
    
    private val ctx: ReadContext? = try {
        when (content) {
            is String -> JsonPath.using(jsonPathConfig).parse(content)
            is ReadContext -> content
            else -> JsonPath.using(jsonPathConfig).parse(content.toString())
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse JSON content", e)
        null
    }
    
    /**
     * Get string result from JSONPath rule
     */
    fun getString(rule: String): String {
        return getStringList(rule).joinToString("\n")
    }
    
    /**
     * Get string list from JSONPath rule
     */
    fun getStringList(rule: String): List<String> {
        if (ctx == null || rule.isBlank()) return emptyList()
        
        val jsonPath = rule
            .removePrefix("@json:")
            .removePrefix("@Json:")
            .trim()
        
        return try {
            val result = ctx.read<Any>(jsonPath)
            when (result) {
                null -> emptyList()
                is List<*> -> result.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
                is String -> if (result.isNotBlank()) listOf(result) else emptyList()
                else -> listOf(result.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSONPath: $jsonPath", e)
            emptyList()
        }
    }
    
    /**
     * Get objects matching JSONPath
     */
    fun getList(rule: String): List<Any> {
        if (ctx == null || rule.isBlank()) return emptyList()
        
        val jsonPath = rule
            .removePrefix("@json:")
            .removePrefix("@Json:")
            .trim()
        
        return try {
            val result = ctx.read<Any>(jsonPath)
            when (result) {
                null -> emptyList()
                is List<*> -> result.filterNotNull()
                else -> listOf(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting list from JSONPath: $jsonPath", e)
            emptyList()
        }
    }
    
    /**
     * Get single object from JSONPath
     */
    fun getObject(rule: String): Any? {
        if (ctx == null || rule.isBlank()) return null
        
        val jsonPath = rule
            .removePrefix("@json:")
            .removePrefix("@Json:")
            .trim()
        
        return try {
            ctx.read(jsonPath)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting object from JSONPath: $jsonPath", e)
            null
        }
    }
}
