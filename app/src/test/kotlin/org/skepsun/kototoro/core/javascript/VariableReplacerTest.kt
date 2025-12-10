package org.skepsun.kototoro.core.javascript

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContain
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource

/**
 * 变量替换测试
 * 
 * 测试 VariableReplacer 的变量替换功能
 * 验证需求 20.7, 20.8
 */
class VariableReplacerTest : FunSpec({
    
    test("replace double brace syntax") {
        val context = JavaScriptContext(key = "test query", page = 1)
        
        // 验证需求 20.7: {{变量名}} 语法替换
        val result = VariableReplacer.replaceVariables("search?q={{key}}&page={{page}}", context)
        
        result shouldBe "search?q=test query&page=1"
    }
    
    test("replace get syntax") {
        val context = JavaScriptContext(key = "test query", page = 1)
        
        // 验证需求 20.8: @get:{变量名} 语法替换
        val result = VariableReplacer.replaceVariables("search?q=@get:{key}&page=@get:{page}", context)
        
        result shouldBe "search?q=test query&page=1"
    }
    
    test("replace nested property with double brace") {
        val book = BookInfo(bookUrl = "http://example.com/book/1", name = "Test Book")
        val context = JavaScriptContext(book = book)
        
        // 验证需求 20.7: 支持嵌套变量访问
        val result = VariableReplacer.replaceVariables("Book: {{book.name}}", context)
        
        result shouldBe "Book: Test Book"
    }
    
    test("replace nested property with get syntax") {
        val book = BookInfo(
            bookUrl = "http://example.com/book/1",
            name = "Test Book",
            author = "Test Author"
        )
        val context = JavaScriptContext(book = book)
        
        val result = VariableReplacer.replaceVariables(
            "Book: @get:{book.name} by @get:{book.author}",
            context
        )
        
        result shouldBe "Book: Test Book by Test Author"
    }
    
    test("replace multiple variables") {
        val book = BookInfo(bookUrl = "http://example.com/book/1", name = "Test Book")
        val context = JavaScriptContext(
            book = book,
            key = "search",
            page = 2
        )
        
        val result = VariableReplacer.replaceVariables(
            "{{book.name}} - search={{key}}, page={{page}}",
            context
        )
        
        result shouldBe "Test Book - search=search, page=2"
    }
    
    test("replace with custom variable") {
        val context = JavaScriptContext()
        context.setVariable("customVar", "customValue")
        
        val result = VariableReplacer.replaceVariables("Value: {{customVar}}", context)
        
        result shouldBe "Value: customValue"
    }
    
    test("replace nonexistent variable keeps original") {
        val context = JavaScriptContext()
        
        val result = VariableReplacer.replaceVariables("Value: {{nonexistent}}", context)
        
        // 不存在的变量保持原样
        result shouldBe "Value: {{nonexistent}}"
    }
    
    test("containsVariables detects double brace") {
        VariableReplacer.containsVariables("{{key}}").shouldBeTrue()
        VariableReplacer.containsVariables("search?q={{key}}").shouldBeTrue()
    }
    
    test("containsVariables detects get syntax") {
        VariableReplacer.containsVariables("@get:{key}").shouldBeTrue()
        VariableReplacer.containsVariables("search?q=@get:{key}").shouldBeTrue()
    }
    
    test("containsVariables returns false for plain text") {
        VariableReplacer.containsVariables("plain text").shouldBeFalse()
        VariableReplacer.containsVariables("search?q=test").shouldBeFalse()
    }
    
    test("extractVariableNames from double brace") {
        val variables = VariableReplacer.extractVariableNames("{{key}} and {{page}}")
        
        variables.size shouldBe 2
        variables shouldContain "key"
        variables shouldContain "page"
    }
    
    test("extractVariableNames from get syntax") {
        val variables = VariableReplacer.extractVariableNames("@get:{key} and @get:{page}")
        
        variables.size shouldBe 2
        variables shouldContain "key"
        variables shouldContain "page"
    }
    
    test("extractVariableNames from mixed syntax") {
        val variables = VariableReplacer.extractVariableNames("{{key}} and @get:{page}")
        
        variables.size shouldBe 2
        variables shouldContain "key"
        variables shouldContain "page"
    }
    
    test("extractVariableNames removes duplicates") {
        val variables = VariableReplacer.extractVariableNames("{{key}} and {{key}} and @get:{key}")
        
        variables.size shouldBe 1
        variables shouldContain "key"
    }
    
    test("extractVariableNames with nested properties") {
        val variables = VariableReplacer.extractVariableNames("{{book.name}} and {{book.author}}")
        
        variables.size shouldBe 2
        variables shouldContain "book.name"
        variables shouldContain "book.author"
    }
    
    test("replace with baseUrl") {
        val context = JavaScriptContext(baseUrl = "http://example.com")
        
        val result = VariableReplacer.replaceVariables("{{baseUrl}}/path", context)
        
        result shouldBe "http://example.com/path"
    }
    
    test("replace with result variable") {
        val context = JavaScriptContext(result = "test result")
        
        val result = VariableReplacer.replaceVariables("Result: {{result}}", context)
        
        result shouldBe "Result: test result"
    }
    
    test("real world Legado search URL pattern") {
        // 模拟真实的 Legado 书源搜索 URL
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "http://example.com"
        )
        val context = JavaScriptContext.forSearch("三体", 1, source)
        
        val searchUrl = "http://example.com/search?q={{key}}&page={{page}}"
        val result = VariableReplacer.replaceVariables(searchUrl, context)
        
        result shouldBe "http://example.com/search?q=三体&page=1"
    }
    
    test("real world Legado chapter URL pattern") {
        // 模拟真实的 Legado 书源章节 URL
        val book = BookInfo(
            bookUrl = "http://example.com/book/123",
            name = "三体"
        )
        val chapter = ChapterInfo(
            chapterUrl = "/chapter/456",
            name = "第一章",
            index = 0
        )
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "http://example.com"
        )
        val context = JavaScriptContext.forContent(book, chapter, source, "http://example.com")
        
        val chapterUrl = "{{baseUrl}}{{chapter.chapterUrl}}"
        val result = VariableReplacer.replaceVariables(chapterUrl, context)
        
        result shouldBe "http://example.com/chapter/456"
    }
})
