package org.skepsun.kototoro.core.parser.legado

import android.util.Log
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.ReadContext

/**
 * JSONPath analyzer for API responses.
 * Supports standard JSONPath syntax with && || %% rule combination.
 * 
 * Aligned with legado-with-MD3 AnalyzeByJSonPath pattern.
 * 
 * 解决阅读"&&"、"||"与jsonPath支持的"&&"、"||"之间的冲突
 * 解决{$.rule}形式规则可能匹配错误的问题
 * 
 * Syntax: `$.data.list[*].name` or `@json:$.data.list[*].name`
 */
class AnalyzeByJsonPath(content: Any) {
    
    companion object {
        private const val TAG = "AnalyzeByJsonPath"
        
        fun parse(json: Any): ReadContext {
            return when (json) {
                is ReadContext -> json
                is String -> JsonPath.parse(json)
                else -> JsonPath.parse(json.toString())
            }
        }
    }
    
    private val ctx: ReadContext? = try {
        parse(content)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse JSON content", e)
        null
    }
    
    /**
     * 改进解析方法
     * 解决阅读"&&"、"||"与jsonPath支持的"&&"、"||"之间的冲突
     * 解决{$.rule}形式规则可能匹配错误的问题
     */
    fun getString(rule: String): String? {
        if (rule.isEmpty() || ctx == null) return null
        var result: String
        val ruleAnalyzes = RuleAnalyzer(rule, true) //设置平衡组为代码平衡
        val rules = ruleAnalyzes.splitRule("&&", "||")

        if (rules.size == 1) {
            ruleAnalyzes.reSetPos() //将pos重置为0，复用解析器
            result = ruleAnalyzes.innerRule("{$.") { getString(it) } //替换所有{$.rule...}

            if (result.isEmpty()) { //st为空，表明无成功替换的内嵌规则
                try {
                    val ob = ctx.read<Any>(rule)
                    result = if (ob is List<*>) {
                        ob.joinToString("\n")
                    } else {
                        ob.toString()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading JSONPath: $rule", e)
                    return null
                }
            }
            return result
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
     * Get string list from JSONPath rule with && || %% support
     */
    fun getStringList(rule: String): List<String> {
        val result = ArrayList<String>()
        if (rule.isEmpty() || ctx == null) return result
        val ruleAnalyzes = RuleAnalyzer(rule, true) //设置平衡组为代码平衡
        val rules = ruleAnalyzes.splitRule("&&", "||", "%%")

        if (rules.size == 1) {
            ruleAnalyzes.reSetPos() //将pos重置为0，复用解析器
            val st = ruleAnalyzes.innerRule("{$.") { getString(it) } //替换所有{$.rule...}
            if (st.isEmpty()) { //st为空，表明无成功替换的内嵌规则
                try {
                    val obj = ctx.read<Any>(rule)
                    if (obj is List<*>) {
                        for (o in obj) result.add(o.toString())
                    } else {
                        result.add(obj.toString())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading JSONPath list: $rule", e)
                }
            } else {
                result.add(st)
            }
            return result
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
            return result
        }
    }
    
    /**
     * Get object from JSONPath
     */
    fun getObject(rule: String): Any? {
        if (ctx == null || rule.isBlank()) return null
        return try {
            ctx.read(rule)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting object from JSONPath: $rule", e)
            null
        }
    }
    
    /**
     * Get list from JSONPath with && || %% support
     */
    fun getList(rule: String): ArrayList<Any>? {
        val result = ArrayList<Any>()
        if (rule.isEmpty() || ctx == null) return result
        val ruleAnalyzes = RuleAnalyzer(rule, true) //设置平衡组为代码平衡
        val rules = ruleAnalyzes.splitRule("&&", "||", "%%")
        if (rules.size == 1) {
            ctx.let {
                try {
                    return it.read<ArrayList<Any>>(rules[0])
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting list from JSONPath: $rule", e)
                }
            }
        } else {
            val results = ArrayList<ArrayList<*>>()
            for (rl in rules) {
                val temp = getList(rl)
                if (!temp.isNullOrEmpty()) {
                    results.add(temp)
                    if (temp.isNotEmpty() && ruleAnalyzes.elementsType == "||") {
                        break
                    }
                }
            }
            if (results.size > 0) {
                if ("%%" == ruleAnalyzes.elementsType) {
                    for (i in 0 until results[0].size) {
                        for (temp in results) {
                            if (i < temp.size) {
                                temp[i]?.let { result.add(it) }
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
}
