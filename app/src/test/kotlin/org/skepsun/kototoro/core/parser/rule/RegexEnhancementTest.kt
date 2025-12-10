package org.skepsun.kototoro.core.parser.rule

import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.junit.Before
import org.junit.Test
import org.skepsun.kototoro.core.javascript.JavaScriptContext
import org.skepsun.kototoro.core.javascript.JavaScriptEngine
import org.skepsun.kototoro.core.javascript.JavaScriptRuleParser

/**
 * 正则表达式增强功能测试
 * 
 * 测试任务 44: 正则表达式增强
 * - 44.1: 正则替换 (##pattern##replacement)
 * - 44.2: 正则提取 (##pattern 或 @regex:pattern)
 * - 44.3: 捕获组支持
 * 
 * 参考 Legado 书源中的真实正则表达式使用场景
 */
class RegexEnhancementTest {
    
    private lateinit var enhancedRuleEngine: EnhancedRuleEngine
    
    @Before
    fun setup() {
        val cache = RuleCache()
        val baseRuleEngine = DefaultRuleEngine(cache)
        
        // Create a simple mock JavaScript engine
        val mockJsEngine = object : JavaScriptEngine {
            override fun execute(script: String, context: JavaScriptContext): Any? {
                return "mock"
            }
            
            override fun evaluate(expression: String, context: JavaScriptContext): Any? {
                return "mock"
            }
            
            override fun registerGlobalObject(name: String, obj: Any) {}
            override fun dispose() {}
        }
        
        val jsRuleParser = JavaScriptRuleParser(mockJsEngine)
        val jsonPathParser = JsonPathParser()
        val enhancedCssSelector = EnhancedCssSelector()
        enhancedRuleEngine = EnhancedRuleEngine(baseRuleEngine, jsRuleParser, jsonPathParser, enhancedCssSelector)
    }
    
    // ========== 任务 44.1: 正则替换测试 ==========
    
    @Test
    fun `test regex replacement with simple pattern`() {
        val html = """<div class="title">Book Title 123</div>"""
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则替换：替换数字为 "XXX"
        val result = enhancedRuleEngine.parseField(element, "div.title@text####\\d+##XXX")
        
        result shouldBe "Book Title XXX"
    }
    
    @Test
    fun `test regex replacement with capture group reference`() {
        val html = """<div class="name">John Doe</div>"""
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则替换：交换名字顺序
        val result = enhancedRuleEngine.parseField(element, "div.name@text####(\\w+)\\s+(\\w+)##$2 $1")
        
        result shouldBe "Doe John"
    }
    
    @Test
    fun `test regex replacement with multiple capture groups`() {
        val html = """<div class="date">2024-01-15</div>"""
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则替换：重新格式化日期
        val result = enhancedRuleEngine.parseField(element, "div.date@text####(\\d{4})-(\\d{2})-(\\d{2})##$3/$2/$1")
        
        result shouldBe "15/01/2024"
    }
    
    @Test
    fun `test regex replacement with dollar zero reference`() {
        val html = """<div class="text">price: 100</div>"""
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则替换：使用 $0 引用整个匹配
        val result = enhancedRuleEngine.parseField(element, "div.text@text####\\d+##[$0]")
        
        result shouldBe "price: [100]"
    }
    
    @Test
    fun `test regex replacement in rule chain`() {
        val html = """
            <div class="book">
                <a href="/book/id-12345">Book Title</a>
            </div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试规则链中的正则替换：提取 href，然后替换格式
        val result = enhancedRuleEngine.parseField(element, "a@href####/book/id-(\\d+)##book_$1.html")
        
        result shouldBe "book_12345.html"
    }
    
    // ========== 任务 44.2: 正则提取测试 ==========
    
    @Test
    fun `test regex extraction with single capture group`() {
        val html = """
            <div class="book">
                <a href="/book/12345">Book Title</a>
            </div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则提取：提取 URL 中的 ID
        val result = enhancedRuleEngine.parseField(element, "a@href##@regex:(\\d+)")
        
        result shouldBe "12345"
    }
    
    @Test
    fun `test regex extraction with multiple capture groups`() {
        val html = """<div class="info">John Doe - 25 years old</div>"""
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则提取：提取第一个捕获组（名字）
        val result = enhancedRuleEngine.parseField(element, "div.info@text##@regex:(\\w+)\\s+(\\w+)")
        
        result shouldBe "John"
    }
    
    @Test
    fun `test regex extraction without capture group`() {
        val html = """<div class="text">The price is 99.99 dollars</div>"""
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则提取：没有捕获组，返回整个匹配
        val result = enhancedRuleEngine.parseField(element, "div.text@text##@regex:\\d+\\.\\d+")
        
        result shouldBe "99.99"
    }
    
    @Test
    fun `test regex extraction with hash prefix`() {
        val html = """<div class="url">/chapter/ch-001</div>"""
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试 ## 前缀的正则提取
        val result = enhancedRuleEngine.parseField(element, "div.url@text####ch-(\\d+)")
        
        result shouldBe "001"
    }
    
    @Test
    fun `test regex extraction with no match returns empty`() {
        val html = """<div class="text">No numbers here</div>"""
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则提取：没有匹配，返回空字符串
        val result = enhancedRuleEngine.parseField(element, "div.text@text##@regex:(\\d+)")
        
        result shouldBe ""
    }
    
    // ========== Legado 书源真实场景测试 ==========
    
    @Test
    fun `test Legado book source pattern - extract chapter ID`() {
        val html = """
            <div class="chapter">
                <a href="https://example.com/book/123/chapter/456">Chapter 456</a>
            </div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试 Legado 书源中常见的模式：提取章节 ID
        val result = enhancedRuleEngine.parseField(element, "a@href##@regex:chapter/(\\d+)")
        
        result shouldBe "456"
    }
    
    @Test
    fun `test Legado book source pattern - extract URL parameter`() {
        val html = """
            <div class="link">
                <a href="/read?id=abc123&amp;page=1">Read</a>
            </div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试提取 URL 参数
        val result = enhancedRuleEngine.parseField(element, "a@href##@regex:id=([^&]+)")
        
        result shouldBe "abc123"
    }
    
    @Test
    fun `test Legado book source pattern - Chinese chapter number`() {
        val html = """<div class="title">第123章 标题</div>"""
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试提取中文文本中的数字
        val result = enhancedRuleEngine.parseField(element, "div.title@text##@regex:第(\\d+)章")
        
        result shouldBe "123"
    }
    
    @Test
    fun `test Legado book source pattern - format chapter number`() {
        val html = """<div class="chapter">第001章</div>"""
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则替换：格式化章节号
        val result = enhancedRuleEngine.parseField(element, "div.chapter@text####第(\\d+)章##Chapter $1")
        
        result shouldBe "Chapter 001"
    }
    
    @Test
    fun `test Legado book source pattern - remove special characters`() {
        val html = """<div class="text">Price: $100.00</div>"""
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则替换：移除货币符号
        val result = enhancedRuleEngine.parseField(element, "div.text@text####\\$##")
        
        result shouldBe "Price: 100.00"
    }
    
    @Test
    fun `test complex regex with multiple operations`() {
        val html = """
            <div class="info">
                <a href="/book/id-12345/chapter-67">Chapter Title</a>
            </div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试多个正则操作：提取 book ID
        val result = enhancedRuleEngine.parseField(element, "a@href##@regex:id-(\\d+)")
        
        result shouldBe "12345"
    }
    
    @Test
    fun `test regex with empty capture group`() {
        val html = """<div class="text">Value: </div>"""
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试空捕获组的处理
        val result = enhancedRuleEngine.parseField(element, "div.text@text##@regex:Value:\\s*(\\w*)")
        
        result shouldBe ""
    }
}
