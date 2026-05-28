package org.skepsun.kototoro.core.parser.legado

import org.skepsun.kototoro.core.javascript.BookInfo
import org.skepsun.kototoro.core.javascript.ChapterInfo
import org.skepsun.kototoro.core.javascript.JavaScriptContext
import org.skepsun.kototoro.core.parser.legado.runtime.LegadoHttpExecutor
import org.skepsun.kototoro.core.parser.legado.runtime.LegadoRuleRuntimeContext

internal class TestLegadoRuleRuntimeContext(
    private val jsEvaluator: ((script: String, context: JavaScriptContext) -> Any?)? = null,
) : LegadoRuleRuntimeContext {

    private val variables = linkedMapOf<String, Any?>()
    private val sourceVariables = linkedMapOf<String, String>()
    private val ruleData = RuleData()
    private var book: BookInfo? = null
    private var chapter: ChapterInfo? = null
    private var sourceObject: Any? = null
    private var sourceTag: String? = null
    private var javaBridge: Any? = null
    private var httpExecutor: LegadoHttpExecutor? = null

    override fun evalJs(script: String, result: Any?, baseUrl: String): Any? {
        val evaluator = jsEvaluator ?: return result
        return evaluator(
            script,
            JavaScriptContext(
                baseUrl = baseUrl,
                book = book,
                chapter = chapter,
                runtimeContext = this,
                result = result,
                src = result,
            ).also { context ->
                variables.forEach { (key, value) ->
                    context.setVariable(key, value)
                }
                context.javaBridge = javaBridge
            },
        ).also { evaluated ->
            variables["result"] = evaluated
            variables["src"] = evaluated
        }
    }

    override fun executeJs(script: String, result: Any?, baseUrl: String): Any? {
        return evalJs(script, result, baseUrl)
    }

    override fun reGetBook() = Unit

    override fun refreshTocUrl() = Unit

    override fun putVariable(key: String, value: String) {
        variables[key] = value
        ruleData.putVariable(key, value)
    }

    override fun putVariableAny(key: String, value: Any?) {
        variables[key] = value
        ruleData.putVariable(key, value?.toString())
    }

    override fun getVariable(key: String): String? = variables[key]?.toString()

    override fun getVariableAny(key: String): Any? = variables[key]

    override fun putSourceVariable(key: String, value: String): String {
        sourceVariables[key] = value
        return value
    }

    override fun getSourceVariable(key: String): String = sourceVariables[key].orEmpty()

    override fun setBook(book: BookInfo?) {
        this.book = book
    }

    override fun getBook(): BookInfo? = book

    override fun setChapter(chapter: ChapterInfo?) {
        this.chapter = chapter
    }

    override fun getChapter(): ChapterInfo? = chapter

    override fun getRuleData(): RuleData = ruleData

    override fun getHttpExecutor(): LegadoHttpExecutor? = httpExecutor

    override fun getSourceObject(): Any? = sourceObject

    override fun getSourceTag(): String? = sourceTag

    override fun <T> withJavaBridge(javaBridge: Any?, block: () -> T): T {
        val previousBridge = this.javaBridge
        this.javaBridge = javaBridge
        return try {
            block()
        } finally {
            this.javaBridge = previousBridge
        }
    }

    fun setSource(sourceObject: Any?, sourceTag: String?) {
        this.sourceObject = sourceObject
        this.sourceTag = sourceTag
    }

    fun setHttpExecutor(httpExecutor: LegadoHttpExecutor?) {
        this.httpExecutor = httpExecutor
    }
}
