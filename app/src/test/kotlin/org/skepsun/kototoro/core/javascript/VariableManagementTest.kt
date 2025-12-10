package org.skepsun.kototoro.core.javascript

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.collections.shouldContain
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource

/**
 * 变量管理测试
 * 
 * 测试 JavaScriptContext 的变量管理功能
 * 验证需求 20.1-20.8
 */
class VariableManagementTest : FunSpec({
    
    test("set and get variable") {
        val context = JavaScriptContext()
        
        context.setVariable("test", "value")
        
        context.getVariable("test") shouldBe "value"
    }
    
    test("get standard variables") {
        val book = BookInfo(bookUrl = "http://example.com/book/1", name = "Test Book")
        val chapter = ChapterInfo(chapterUrl = "http://example.com/chapter/1", name = "Chapter 1", index = 0)
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "http://example.com"
        )
        
        val context = JavaScriptContext(
            baseUrl = "http://example.com",
            book = book,
            chapter = chapter,
            source = source,
            key = "search",
            page = 1,
            result = "test result"
        )
        
        // 验证需求 20.1-20.6: 所有标准变量都可以访问
        context.getVariable("baseUrl") shouldBe "http://example.com"
        context.getVariable("book") shouldBe book
        context.getVariable("chapter") shouldBe chapter
        context.getVariable("source") shouldBe source
        context.getVariable("key") shouldBe "search"
        context.getVariable("page") shouldBe 1
        context.getVariable("result") shouldBe "test result"
    }
    
    test("nested property access - book name") {
        val book = BookInfo(bookUrl = "http://example.com/book/1", name = "Test Book")
        val context = JavaScriptContext(book = book)
        
        // 验证需求 20.7: 支持嵌套变量访问
        context.getVariable("book.name") shouldBe "Test Book"
    }
    
    test("nested property access - book author") {
        val book = BookInfo(
            bookUrl = "http://example.com/book/1",
            name = "Test Book",
            author = "Test Author"
        )
        val context = JavaScriptContext(book = book)
        
        context.getVariable("book.author") shouldBe "Test Author"
    }
    
    test("nested property access - chapter name") {
        val chapter = ChapterInfo(
            chapterUrl = "http://example.com/chapter/1",
            name = "Chapter 1",
            index = 0
        )
        val context = JavaScriptContext(chapter = chapter)
        
        context.getVariable("chapter.name") shouldBe "Chapter 1"
    }
    
    test("custom properties on book") {
        val book = BookInfo(bookUrl = "http://example.com/book/1")
        book.customProperties["customField"] = "customValue"
        
        val context = JavaScriptContext(book = book)
        
        // 验证需求 20.6: 支持自定义属性
        context.getVariable("book.customField") shouldBe "customValue"
    }
    
    test("book setProperty method") {
        val book = BookInfo(bookUrl = "http://example.com/book/1")
        
        // 验证需求 20.6: JavaScript 可以设置自定义属性
        book.setProperty("type", "2")
        book.setProperty("customField", "customValue")
        
        book.type shouldBe 2
        book.customProperties["customField"] shouldBe "customValue"
    }
    
    test("book getProperty method") {
        val book = BookInfo(
            bookUrl = "http://example.com/book/1",
            name = "Test Book",
            type = 2
        )
        book.customProperties["customField"] = "customValue"
        
        book.getProperty("bookUrl") shouldBe "http://example.com/book/1"
        book.getProperty("name") shouldBe "Test Book"
        book.getProperty("type") shouldBe 2
        book.getProperty("customField") shouldBe "customValue"
    }
    
    test("getAllVariables includes all standard variables") {
        val book = BookInfo(bookUrl = "http://example.com/book/1", name = "Test Book")
        val context = JavaScriptContext(
            baseUrl = "http://example.com",
            book = book,
            key = "search",
            page = 1
        )
        context.setVariable("custom", "value")
        
        val allVars = context.getAllVariables()
        
        allVars.keys shouldContain "baseUrl"
        allVars.keys shouldContain "book"
        allVars.keys shouldContain "key"
        allVars.keys shouldContain "page"
        allVars.keys shouldContain "custom"
        
        allVars["baseUrl"] shouldBe "http://example.com"
        allVars["book"] shouldBe book
        allVars["key"] shouldBe "search"
        allVars["page"] shouldBe 1
        allVars["custom"] shouldBe "value"
    }
    
    test("forSearch factory method") {
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "http://example.com"
        )
        
        // 验证需求 20.1: 搜索上下文包含 key 和 page
        val context = JavaScriptContext.forSearch("test query", 1, source)
        
        context.key shouldBe "test query"
        context.page shouldBe 1
        context.source shouldBe source
    }
    
    test("forBookInfo factory method") {
        val book = BookInfo(bookUrl = "http://example.com/book/1")
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "http://example.com"
        )
        
        // 验证需求 20.2: 详情上下文包含 book 和 baseUrl
        val context = JavaScriptContext.forBookInfo(book, source, "http://example.com")
        
        context.book shouldBe book
        context.source shouldBe source
        context.baseUrl shouldBe "http://example.com"
    }
    
    test("forChapterList factory method") {
        val book = BookInfo(bookUrl = "http://example.com/book/1")
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "http://example.com"
        )
        
        // 验证需求 20.3: 章节列表上下文包含 book
        val context = JavaScriptContext.forChapterList(book, source, "http://example.com")
        
        context.book shouldBe book
        context.source shouldBe source
        context.baseUrl shouldBe "http://example.com"
    }
    
    test("forContent factory method") {
        val book = BookInfo(bookUrl = "http://example.com/book/1")
        val chapter = ChapterInfo(
            chapterUrl = "http://example.com/chapter/1",
            name = "Chapter 1",
            index = 0
        )
        val source = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "http://example.com"
        )
        
        // 验证需求 20.4: 内容上下文包含 book 和 chapter
        val context = JavaScriptContext.forContent(book, chapter, source, "http://example.com")
        
        context.book shouldBe book
        context.chapter shouldBe chapter
        context.source shouldBe source
        context.baseUrl shouldBe "http://example.com"
    }
    
    test("null variable returns null") {
        val context = JavaScriptContext()
        
        context.getVariable("nonexistent").shouldBeNull()
    }
    
    test("nested property on null object returns null") {
        val context = JavaScriptContext()
        
        context.getVariable("book.name").shouldBeNull()
    }
})
