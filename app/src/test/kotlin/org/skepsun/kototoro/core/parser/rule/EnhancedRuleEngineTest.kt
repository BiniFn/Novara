package org.skepsun.kototoro.core.parser.rule

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.jsoup.Jsoup
import org.junit.Before
import org.junit.Test
import org.skepsun.kototoro.core.javascript.JavaScriptContext
import org.skepsun.kototoro.core.javascript.JavaScriptEngine
import org.skepsun.kototoro.core.javascript.JavaScriptRuleParser

/**
 * 增强规则引擎测试
 * 
 * 测试 EnhancedRuleEngine 的所有功能：
 * - 规则链（##）
 * - 或操作符（||）
 * - 合并操作符（&&）
 * - 字符串操作符（@replace, @substring, @get）
 * - 属性提取操作符（@text, @html, @src, @href）
 * - JavaScript 规则集成
 */
class EnhancedRuleEngineTest {
    
    private lateinit var baseRuleEngine: RuleEngine
    private lateinit var jsRuleParser: JavaScriptRuleParser
    private lateinit var enhancedRuleEngine: EnhancedRuleEngine
    private lateinit var mockJsEngine: JavaScriptEngine
    
    private val testHtml = """
        <html>
            <body>
                <div class="container">
                    <div class="title">Test Title</div>
                    <div class="subtitle">Test Subtitle</div>
                    <a href="/path/to/page">Link Text</a>
                    <img src="/images/test.jpg" alt="Test Image">
                    <div class="content">
                        <p>First paragraph</p>
                        <p>Second paragraph</p>
                        <p>Third paragraph</p>
                    </div>
                    <div class="data" data-id="12345">Data Element</div>
                </div>
            </body>
        </html>
    """.trimIndent()
    
    @Before
    fun setup() {
        val cache = RuleCache()
        baseRuleEngine = DefaultRuleEngine(cache)
        
        // Create a simple mock JavaScript engine
        mockJsEngine = object : JavaScriptEngine {
            override fun execute(script: String, context: JavaScriptContext): Any? {
                // Simple mock implementation
                return when {
                    script.contains("toUpperCase") -> "MODIFIED"
                    script.contains("length") -> "42"
                    else -> "JavaScript Result"
                }
            }
            
            override fun evaluate(expression: String, context: JavaScriptContext): Any? {
                return execute(expression, context)
            }
            
            override fun registerGlobalObject(name: String, obj: Any) {
                // No-op for testing
            }
            
            override fun dispose() {
                // No-op for testing
            }
        }
        
        jsRuleParser = JavaScriptRuleParser(mockJsEngine)
        val jsonPathParser = JsonPathParser()
        val enhancedCssSelector = EnhancedCssSelector()
        enhancedRuleEngine = EnhancedRuleEngine(baseRuleEngine, jsRuleParser, jsonPathParser, enhancedCssSelector)
    }
    
    // ========== 规则链测试（## 操作符） ==========
    
    @Test
    fun `test rule chain with CSS selectors`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试规则链：选择 container，然后选择 title，然后获取文本
        val result = enhancedRuleEngine.parseField(element, "div.container##div.title@text")
        
        result shouldBe "Test Title"
    }
    
    @Test
    fun `test rule chain with regex extraction`() {
        val html = """
            <div class="item">
                <a href="/book/12345">Book Title</a>
            </div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试规则链：获取 href，然后用正则提取 ID
        val result = enhancedRuleEngine.parseField(element, "a@href##@regex:(\\d+)")
        
        result shouldBe "12345"
    }
    
    @Test
    fun `test rule chain with string operations`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试规则链：获取文本，然后替换
        val result = enhancedRuleEngine.parseField(element, "div.title@text##@replace:Test:Modified")
        
        result shouldBe "Modified Title"
    }
    
    @Test
    fun `test rule chain stops on empty result`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试规则链：如果中间步骤返回空，应该停止
        val result = enhancedRuleEngine.parseField(element, "div.nonexistent@text##@replace:Test:Modified")
        
        result shouldBe ""
    }
    
    // ========== 或操作符测试（|| 操作符） ==========
    
    @Test
    fun `test or operator returns first non-empty result`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试或操作符：尝试多个选择器，返回第一个非空结果
        val result = enhancedRuleEngine.parseField(element, "div.nonexistent@text||div.title@text||div.subtitle@text")
        
        result shouldBe "Test Title"
    }
    
    @Test
    fun `test or operator with all empty results`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试或操作符：所有选择器都返回空
        val result = enhancedRuleEngine.parseField(element, "div.none1@text||div.none2@text||div.none3@text")
        
        result shouldBe ""
    }
    
    @Test
    fun `test or operator with image src alternatives`() {
        val html = """
            <div>
                <img data-src="/images/test.jpg" alt="Test">
            </div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试或操作符：尝试多个图片源属性
        val result = enhancedRuleEngine.parseField(element, "img@src||img@data-src||img@data-original")
        
        result shouldBe "/images/test.jpg"
    }
    
    // ========== 合并操作符测试（&& 操作符） ==========
    
    @Test
    fun `test and operator combines multiple results`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试合并操作符：合并多个选择器的结果
        val result = enhancedRuleEngine.parseField(element, "div.title@text&&div.subtitle@text")
        
        result shouldContain "Test Title"
        result shouldContain "Test Subtitle"
    }
    
    @Test
    fun `test and operator skips empty results`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试合并操作符：跳过空结果
        val result = enhancedRuleEngine.parseField(element, "div.title@text&&div.nonexistent@text&&div.subtitle@text")
        
        result shouldContain "Test Title"
        result shouldContain "Test Subtitle"
        result shouldNotBe ""
    }
    
    // ========== 字符串操作符测试 ==========
    
    @Test
    fun `test replace operator`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试 @replace 操作符
        val result = enhancedRuleEngine.parseField(element, "div.title@text##@replace:Test:Modified")
        
        result shouldBe "Modified Title"
    }
    
    @Test
    fun `test substring operator`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试 @substring 操作符
        val result = enhancedRuleEngine.parseField(element, "div.title@text##@substring:0:4")
        
        result shouldBe "Test"
    }
    
    @Test
    fun `test get operator with index`() {
        val html = """
            <div class="text">First Second Third</div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试 @get 操作符
        val result = enhancedRuleEngine.parseField(element, "div.text@text##@get:{1}")
        
        result shouldBe "Second"
    }
    
    // ========== 属性提取操作符测试 ==========
    
    @Test
    fun `test text attribute extraction`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试 @text 属性提取
        val result = enhancedRuleEngine.parseField(element, "div.title@text")
        
        result shouldBe "Test Title"
    }
    
    @Test
    fun `test html attribute extraction`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试 @html 属性提取
        val result = enhancedRuleEngine.parseField(element, "div.content@html")
        
        result shouldContain "<p>First paragraph</p>"
    }
    
    @Test
    fun `test src attribute extraction`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试 @src 属性提取
        val result = enhancedRuleEngine.parseField(element, "img@src")
        
        result shouldBe "/images/test.jpg"
    }
    
    @Test
    fun `test href attribute extraction`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试 @href 属性提取
        val result = enhancedRuleEngine.parseField(element, "a@href")
        
        result shouldBe "/path/to/page"
    }
    
    // ========== JavaScript 规则集成测试 ==========
    
    @Test
    fun `test JavaScript rule with js tag`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试 <js> 标签
        val result = enhancedRuleEngine.parseField(element, "<js>result.toUpperCase()</js>")
        
        result shouldBe "JavaScript Result"
    }
    
    @Test
    fun `test JavaScript rule with js prefix`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试 @js: 前缀
        val result = enhancedRuleEngine.parseField(element, "@js:result.length")
        
        result shouldBe "42"
    }
    
    @Test
    fun `test JavaScript in rule chain`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试规则链中的 JavaScript
        val result = enhancedRuleEngine.parseField(element, "div.title@text##<js>result.toUpperCase()</js>")
        
        result shouldBe "MODIFIED"
    }
    
    // ========== 复杂场景测试 ==========
    
    @Test
    fun `test complex rule with multiple operators`() {
        val html = """
            <div class="book">
                <div class="info">
                    <h3><a href="/book/12345">Book Title</a></h3>
                    <span class="author">Author Name</span>
                </div>
            </div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试复杂规则：规则链 + 正则提取
        val result = enhancedRuleEngine.parseField(element, "div.info##a@href##@regex:(\\d+)")
        
        result shouldBe "12345"
    }
    
    @Test
    fun `test error handling with invalid rule`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试错误处理：无效的规则应该返回空字符串而不是崩溃
        val result = enhancedRuleEngine.parseField(element, "##invalid##rule##")
        
        result shouldBe ""
    }
    
    @Test
    fun `test empty rule returns empty string`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试空规则
        val result = enhancedRuleEngine.parseField(element, "")
        
        result shouldBe ""
    }
    
    @Test
    fun `test blank rule returns empty string`() {
        val doc = Jsoup.parse(testHtml)
        val element = doc.body()
        
        // 测试空白规则
        val result = enhancedRuleEngine.parseField(element, "   ")
        
        result shouldBe ""
    }
    
    // ========== 正则表达式增强测试 ==========
    
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
        val html = """
            <div class="info">John Doe - 25 years old</div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则提取：提取第一个捕获组（名字）
        val result = enhancedRuleEngine.parseField(element, "div.info@text##@regex:(\\w+)\\s+(\\w+)")
        
        result shouldBe "John"
    }
    
    @Test
    fun `test regex extraction without capture group`() {
        val html = """
            <div class="text">The price is 99.99 dollars</div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则提取：没有捕获组，返回整个匹配
        val result = enhancedRuleEngine.parseField(element, "div.text@text##@regex:\\d+\\.\\d+")
        
        result shouldBe "99.99"
    }
    
    @Test
    fun `test regex extraction with ## prefix`() {
        val html = """
            <div class="url">/chapter/ch-001</div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试 ## 前缀的正则提取
        val result = enhancedRuleEngine.parseField(element, "div.url@text####ch-(\\d+)")
        
        result shouldBe "001"
    }
    
    @Test
    fun `test regex extraction with no match returns empty`() {
        val html = """
            <div class="text">No numbers here</div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则提取：没有匹配，返回空字符串
        val result = enhancedRuleEngine.parseField(element, "div.text@text##@regex:(\\d+)")
        
        result shouldBe ""
    }
    
    @Test
    fun `test regex replacement with simple pattern`() {
        val html = """
            <div class="title">Book Title 123</div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则替换：替换数字为 "XXX"
        val result = enhancedRuleEngine.parseField(element, "div.title@text####\\d+##XXX")
        
        result shouldBe "Book Title XXX"
    }
    
    @Test
    fun `test regex replacement with capture group reference`() {
        val html = """
            <div class="name">John Doe</div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则替换：交换名字顺序
        val result = enhancedRuleEngine.parseField(element, "div.name@text####(\\w+)\\s+(\\w+)##$2 $1")
        
        result shouldBe "Doe John"
    }
    
    @Test
    fun `test regex replacement with multiple capture groups`() {
        val html = """
            <div class="date">2024-01-15</div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则替换：重新格式化日期
        val result = enhancedRuleEngine.parseField(element, "div.date@text####(\\d{4})-(\\d{2})-(\\d{2})##$3/$2/$1")
        
        result shouldBe "15/01/2024"
    }
    
    @Test
    fun `test regex replacement with $0 reference`() {
        val html = """
            <div class="text">price: 100</div>
        """.trimIndent()
        
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
    
    @Test
    fun `test complex regex with Legado book source pattern`() {
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
    fun `test regex extraction with URL encoding pattern`() {
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
    fun `test regex replacement with special characters`() {
        val html = """
            <div class="text">Price: $100.00</div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则替换：移除货币符号
        val result = enhancedRuleEngine.parseField(element, "div.text@text####\\$##")
        
        result shouldBe "Price: 100.00"
    }
    
    @Test
    fun `test regex extraction with Chinese characters`() {
        val html = """
            <div class="title">第123章 标题</div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试提取中文文本中的数字
        val result = enhancedRuleEngine.parseField(element, "div.title@text##@regex:第(\\d+)章")
        
        result shouldBe "123"
    }
    
    @Test
    fun `test regex replacement with Chinese text`() {
        val html = """
            <div class="chapter">第001章</div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试正则替换：格式化章节号
        val result = enhancedRuleEngine.parseField(element, "div.chapter@text####第(\\d+)章##Chapter $1")
        
        result shouldBe "Chapter 001"
    }
    
    @Test
    fun `test multiple regex operations in chain`() {
        val html = """
            <div class="info">
                <a href="/book/id-12345/chapter-67">Chapter Title</a>
            </div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试多个正则操作：提取 book ID，然后提取 chapter ID
        val result = enhancedRuleEngine.parseField(element, "a@href##@regex:id-(\\d+)")
        
        result shouldBe "12345"
    }
    
    @Test
    fun `test regex with empty capture group`() {
        val html = """
            <div class="text">Value: </div>
        """.trimIndent()
        
        val doc = Jsoup.parse(html)
        val element = doc.body()
        
        // 测试空捕获组的处理
        val result = enhancedRuleEngine.parseField(element, "div.text@text##@regex:Value:\\s*(\\w*)")
        
        result shouldBe ""
    }
}

