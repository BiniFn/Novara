package org.skepsun.kototoro.core.parser.legado.sandbox

import android.util.Log
import kotlinx.coroutines.runBlocking
import org.skepsun.kototoro.core.javascript.JavaScriptContext
import org.skepsun.kototoro.core.javascript.JavaScriptEngine
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.core.parser.legado.RuleData
import org.skepsun.kototoro.core.parser.legado.RuleDataInterface
import org.skepsun.kototoro.core.javascript.BookInfo as JsBookInfo
import org.skepsun.kototoro.core.javascript.ChapterInfo as JsChapterInfo

/**
 * Sandboxed JavaScript execution environment for Legado sources.
 * Provides isolated variable scope and secure API bindings.
 */
class LegadoSandbox(
    private val jsEngine: JavaScriptEngine,
    private val httpClient: LegadoHttpClient,
    private val source: LegadoBookSource,
    private val cookieManager: android.webkit.CookieManager? = null
) {
    
    private val cache = mutableMapOf<String, String>()
    
    inner class CacheBinding {
        fun put(key: String, value: String?) {
            Log.d(TAG, "cache.put(key=$key, value=${value?.take(50)})")
            if (value != null) cache[key] = value
            else cache.remove(key)
        }
        
        // Overload with TTL (time-to-live in seconds) - for Legado compatibility
        // Currently ignores TTL, stores indefinitely
        fun put(key: String, value: String?, ttl: Int) {
            Log.d(TAG, "cache.put(key=$key, value=${value?.take(50)}, ttl=$ttl)")
            if (value != null) cache[key] = value
            else cache.remove(key)
        }
        
        // Overload with TTL as Long
        fun put(key: String, value: String?, ttl: Long) {
            put(key, value, ttl.toInt())
        }
        
        fun get(key: String): String? {
            val result = cache[key]
            Log.d(TAG, "cache.get(key=$key) = ${result?.take(50) ?: "null"}")
            return result
        }
        
        fun delete(key: String) {
            Log.d(TAG, "cache.delete(key=$key)")
            cache.remove(key)
        }
    }
    
    companion object {
        private const val TAG = "LegadoSandbox"
    }

    private val ruleData: RuleData = RuleData()

    private var context: JavaScriptContext = JavaScriptContext(
        baseUrl = source.bookSourceUrl,
        source = source
    )

    
    fun getRuleData(): RuleDataInterface = ruleData
    
    fun putVariable(key: String, value: String?) {
        ruleData.putVariable(key, value)
        context.setVariable(key, value ?: "")
    }
    
    fun getVariable(key: String): String? {
        return ruleData.getVariable(key)
    }
    
    fun setResult(result: Any?) {
        Log.d(TAG, "setResult: type=${result?.javaClass?.simpleName}, isList=${result is List<*>}, size=${(result as? List<*>)?.size}")
        context.result = result
        context.setVariable("result", result)
    }
    
    fun setBook(book: BookContext) {
        val jsBook = JsBookInfo(
            bookUrl = book.url,
            name = book.name,
            author = book.author,
            coverUrl = book.coverUrl,
            intro = book.intro,
            kind = book.kind,
            tocUrl = book.tocUrl
        )
        context = context.copy(book = jsBook)
        putVariable("bookName", book.name)
        putVariable("bookAuthor", book.author)
        putVariable("bookUrl", book.url)
    }
    
    fun setChapter(chapter: ChapterContext) {
        val jsChapter = JsChapterInfo(
            chapterUrl = chapter.url,
            name = chapter.title,
            index = chapter.index
        )
        context = context.copy(chapter = jsChapter)
        putVariable("chapterName", chapter.title)
        putVariable("chapterUrl", chapter.url)
        putVariable("chapterIndex", chapter.index.toString())
    }
    
    fun eval(script: String): Any? {
        if (script.isBlank()) return null
        
        return try {
            ruleData.getVariableMap().forEach { (k, v) ->
                context.setVariable(k, v)
            }
            context.setVariable("cache", CacheBinding())
            jsEngine.evaluate(script, context)
        } catch (e: Exception) {
            Log.e(TAG, "JavaScript evaluation failed: ${e.message}", e)
            null
        }
    }
    
    fun execute(script: String): Any? {
        if (script.isBlank()) return null
        
        return try {
            ruleData.getVariableMap().forEach { (k, v) ->
                context.setVariable(k, v)
            }
            context.setVariable("cache", CacheBinding())
            jsEngine.execute(script, context)
        } catch (e: Exception) {
            Log.e(TAG, "JavaScript execution failed: ${e.message}", e)
            null
        }
    }
    
    fun reset() {
        ruleData.clearVariables()
        context = JavaScriptContext(
            baseUrl = source.bookSourceUrl,
            source = source
        )
    }
    
    data class BookContext(
        val name: String = "",
        val author: String = "",
        val url: String = "",
        val coverUrl: String = "",
        val intro: String = "",
        val kind: String = "",
        val tocUrl: String = ""
    )
    
    data class ChapterContext(
        val title: String = "",
        val url: String = "",
        val index: Int = 0,
        val isVip: Boolean = false,
        val isPay: Boolean = false
    )
}
