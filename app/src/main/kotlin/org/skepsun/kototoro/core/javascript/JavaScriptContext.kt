package org.skepsun.kototoro.core.javascript

import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource

/**
 * JavaScript 执行上下文
 * 
 * 管理 JavaScript 执行时的变量和状态
 * 与 Legado 的 AnalyzeRule.kt 保持一致
 */
data class JavaScriptContext(
    private val variables: MutableMap<String, Any?> = mutableMapOf(),
    val baseUrl: String? = null,
    val book: BookInfo? = null,
    val chapter: ChapterInfo? = null,
    val source: LegadoBookSource? = null,
    val key: String? = null,
    val page: Int? = null,
    var result: Any? = null  // 当前规则的输入/输出数据
) {
    /**
     * 设置变量
     */
    fun setVariable(name: String, value: Any?) {
        when (name) {
            "result", "src" -> result = value
            else -> variables[name] = value
        }
    }
    
    /**
     * 获取变量
     * 支持嵌套属性访问，如 "book.name"
     */
    fun getVariable(name: String): Any? {
        // 首先检查直接变量
        if (variables.containsKey(name)) {
            return variables[name]
        }
        
        // 处理嵌套属性访问
        if (name.contains('.')) {
            val parts = name.split('.', limit = 2)
            val objectName = parts[0]
            val propertyName = parts[1]
            
            val obj = when (objectName) {
                "book" -> book
                "chapter" -> chapter
                "source" -> source
                else -> variables[objectName]
            }
            
            return getNestedProperty(obj, propertyName)
        }
        
        // 检查上下文中的标准变量
        return when (name) {
            "baseUrl" -> baseUrl
            "book" -> book
            "chapter" -> chapter
            "source" -> source
            "key" -> key
            "page" -> page
            "result", "src" -> result
            else -> null
        }
    }
    
    /**
     * 获取嵌套属性
     * 支持多级访问，如 "book.name" 或 "book.customProperties.type"
     */
    private fun getNestedProperty(obj: Any?, propertyName: String): Any? {
        if (obj == null) return null
        
        val parts = propertyName.split('.', limit = 2)
        val currentProperty = parts[0]
        
        val value = when (obj) {
            is BookInfo -> when (currentProperty) {
                "bookUrl" -> obj.bookUrl
                "name" -> obj.name
                "author" -> obj.author
                "coverUrl" -> obj.coverUrl
                "intro" -> obj.intro
                "kind" -> obj.kind
                "lastChapter" -> obj.lastChapter
                "tocUrl" -> obj.tocUrl
                "wordCount" -> obj.wordCount
                "type" -> obj.type
                else -> obj.customProperties[currentProperty]
            }
            is ChapterInfo -> when (currentProperty) {
                "chapterUrl" -> obj.chapterUrl
                "name" -> obj.name
                "index" -> obj.index
                else -> null
            }
            is LegadoBookSource -> when (currentProperty) {
                "bookSourceName" -> obj.bookSourceName
                "bookSourceUrl" -> obj.bookSourceUrl
                "bookSourceType" -> obj.bookSourceType
                else -> null
            }
            is Map<*, *> -> obj[currentProperty]
            else -> null
        }
        
        // 如果还有更多层级，递归获取
        return if (parts.size > 1) {
            getNestedProperty(value, parts[1])
        } else {
            value
        }
    }
    
    /**
     * 获取所有变量（用于 JavaScript 引擎）
     */
    fun getAllVariables(): Map<String, Any?> {
        val allVars = mutableMapOf<String, Any?>()
        allVars.putAll(variables)
        
        // 添加标准变量
        baseUrl?.let { allVars["baseUrl"] = it }
        book?.let { allVars["book"] = it }
        chapter?.let { allVars["chapter"] = it }
        source?.let { allVars["source"] = it }
        key?.let { allVars["key"] = it }
        page?.let { allVars["page"] = it }
        result?.let { 
            allVars["result"] = it
            allVars["src"] = it
        }
        
        // java 绑定在 Sandbox.setResult 时注入，以便可替换为具备方法的绑定对象
        variables["java"]?.let { allVars["java"] = it }
        // cookie 和 cache 将在 Sandbox 中实际注入
        
        return allVars
    }
    
    companion object {
        /**
         * 创建搜索上下文
         */
        fun forSearch(key: String, page: Int, source: LegadoBookSource): JavaScriptContext {
            return JavaScriptContext(
                key = key,
                page = page,
                source = source
            )
        }
        
        /**
         * 创建书籍信息上下文
         */
        fun forBookInfo(book: BookInfo, source: LegadoBookSource, baseUrl: String): JavaScriptContext {
            return JavaScriptContext(
                book = book,
                source = source,
                baseUrl = baseUrl
            )
        }
        
        /**
         * 创建章节列表上下文
         */
        fun forChapterList(book: BookInfo, source: LegadoBookSource, baseUrl: String): JavaScriptContext {
            return JavaScriptContext(
                book = book,
                source = source,
                baseUrl = baseUrl
            )
        }
        
        /**
         * 创建章节内容上下文
         */
        fun forContent(
            book: BookInfo,
            chapter: ChapterInfo,
            source: LegadoBookSource,
            baseUrl: String
        ): JavaScriptContext {
            return JavaScriptContext(
                book = book,
                chapter = chapter,
                source = source,
                baseUrl = baseUrl
            )
        }
    }
}

/**
 * 书籍信息
 * 与 Legado 的 Book 对象保持一致
 */
data class BookInfo(
    val bookUrl: String,
    var name: String? = null,
    var author: String? = null,
    var coverUrl: String? = null,
    var intro: String? = null,
    var kind: String? = null,
    var lastChapter: String? = null,
    var tocUrl: String? = null,
    var wordCount: String? = null,
    var type: Int? = null, // 自定义属性，可被 JavaScript 设置
    val customProperties: MutableMap<String, Any?> = mutableMapOf()
) {
    /**
     * 设置自定义属性
     */
    fun setProperty(name: String, value: Any?) {
        when (name) {
            "name" -> this.name = value?.toString()
            "author" -> this.author = value?.toString()
            "coverUrl" -> this.coverUrl = value?.toString()
            "intro" -> this.intro = value?.toString()
            "kind" -> this.kind = value?.toString()
            "lastChapter" -> this.lastChapter = value?.toString()
            "tocUrl" -> this.tocUrl = value?.toString()
            "wordCount" -> this.wordCount = value?.toString()
            "type" -> this.type = value?.toString()?.toIntOrNull()
            else -> customProperties[name] = value
        }
    }
    
    /**
     * 获取属性
     */
    fun getProperty(propertyName: String): Any? {
        return when (propertyName) {
            "bookUrl" -> bookUrl
            "name" -> this.name
            "author" -> this.author
            "coverUrl" -> this.coverUrl
            "intro" -> this.intro
            "kind" -> this.kind
            "lastChapter" -> this.lastChapter
            "tocUrl" -> this.tocUrl
            "wordCount" -> this.wordCount
            "type" -> this.type
            else -> customProperties[propertyName]
        }
    }
}

/**
 * 章节信息
 */
data class ChapterInfo(
    val chapterUrl: String,
    val name: String,
    val index: Int
)
