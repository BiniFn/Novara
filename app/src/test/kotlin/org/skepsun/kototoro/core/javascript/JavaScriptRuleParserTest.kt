package org.skepsun.kototoro.core.javascript

import android.content.Context
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.mockk
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.core.network.jsonsource.PersistentCookieJar
import java.net.CookieManager

/**
 * JavaScript 规则解析器测试
 * 
 * 测试 JavaScript 规则的检测、提取和执行功能
 * 使用真实的 Rhino 引擎进行测试
 */
class JavaScriptRuleParserTest : FunSpec({
    
    lateinit var engine: RhinoJavaScriptEngine
    lateinit var parser: JavaScriptRuleParser
    lateinit var mockHttpClient: LegadoHttpClient
    lateinit var mockCookieManager: CookieManager
    lateinit var mockCookieJar: PersistentCookieJar
    lateinit var mockContext: Context
    
    beforeTest {
        // 创建 mock 对象
        mockHttpClient = mockk(relaxed = true)
        mockCookieManager = CookieManager()
        mockCookieJar = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        
        // 创建真实的 Rhino 引擎
        engine = RhinoJavaScriptEngine(
            mockHttpClient,
            mockCookieManager,
            mockCookieJar,
            mockContext
        )
        
        // 创建解析器
        parser = JavaScriptRuleParser(engine)
    }
    
    afterTest {
        engine.dispose()
    }
    
    // ========== 规则检测测试 ==========
    
    test("containsJavaScript should detect js tag") {
        val rule = "<js>result + '测试'</js>"
        parser.containsJavaScript(rule) shouldBe true
    }
    
    test("containsJavaScript should detect js tag case insensitive") {
        val rule = "<JS>result + '测试'</JS>"
        parser.containsJavaScript(rule) shouldBe true
    }
    
    test("containsJavaScript should detect @js prefix") {
        val rule = "@js:result + '测试'"
        parser.containsJavaScript(rule) shouldBe true
    }
    
    test("containsJavaScript should detect @js prefix case insensitive") {
        val rule = "@JS:result + '测试'"
        parser.containsJavaScript(rule) shouldBe true
    }
    
    test("containsJavaScript should detect @js prefix with leading whitespace") {
        val rule = "  @js:result + '测试'"
        parser.containsJavaScript(rule) shouldBe true
    }
    
    test("containsJavaScript should return false for regular rule") {
        val rule = "div.title@text"
        parser.containsJavaScript(rule) shouldBe false
    }
    
    test("containsJavaScript should return false for empty rule") {
        val rule = ""
        parser.containsJavaScript(rule) shouldBe false
    }
    
    test("containsJavaScript should return false for blank rule") {
        val rule = "   "
        parser.containsJavaScript(rule) shouldBe false
    }
    
    // ========== JavaScript 提取测试 ==========
    
    test("extractJavaScript should extract code from js tag") {
        val rule = "<js>result + '测试'</js>"
        val extracted = parser.extractJavaScript(rule)
        extracted shouldBe "result + '测试'"
    }
    
    test("extractJavaScript should extract code from js tag case insensitive") {
        val rule = "<JS>result + '测试'</JS>"
        val extracted = parser.extractJavaScript(rule)
        extracted shouldBe "result + '测试'"
    }
    
    test("extractJavaScript should extract multiline code from js tag") {
        val rule = """
            <js>
            var title = result;
            title = title.replace('【', '');
            title = title.replace('】', '');
            title
            </js>
        """.trimIndent()
        val extracted = parser.extractJavaScript(rule)
        extracted.shouldNotBeNull()
        extracted.contains("var title = result") shouldBe true
        extracted.contains("title.replace") shouldBe true
    }
    
    test("extractJavaScript should extract code from @js prefix") {
        val rule = "@js:result + '测试'"
        val extracted = parser.extractJavaScript(rule)
        extracted shouldBe "result + '测试'"
    }
    
    test("extractJavaScript should extract code from @js prefix case insensitive") {
        val rule = "@JS:result + '测试'"
        val extracted = parser.extractJavaScript(rule)
        extracted shouldBe "result + '测试'"
    }
    
    test("extractJavaScript should handle @js prefix with leading whitespace") {
        val rule = "  @js:result + '测试'"
        val extracted = parser.extractJavaScript(rule)
        extracted shouldBe "result + '测试'"
    }
    
    test("extractJavaScript should return null for regular rule") {
        val rule = "div.title@text"
        val extracted = parser.extractJavaScript(rule)
        extracted.shouldBeNull()
    }
    
    test("extractJavaScript should return null for empty rule") {
        val rule = ""
        val extracted = parser.extractJavaScript(rule)
        extracted.shouldBeNull()
    }
    
    test("extractJavaScript should handle malformed js tag gracefully") {
        val rule = "<js>result + '测试'"
        val extracted = parser.extractJavaScript(rule)
        // 应该返回 null，因为缺少闭合标签
        extracted.shouldBeNull()
    }
    
    // ========== 规则执行测试 ==========
    
    test("executeRule should execute simple js tag rule") {
        val rule = "<js>result + '测试'</js>"
        val context = JavaScriptContext()
        val result = parser.executeRule(rule, "原始", context)
        
        result shouldBe "原始测试"
    }
    
    test("executeRule should execute @js prefix rule") {
        val rule = "@js:result + '测试'"
        val context = JavaScriptContext()
        val result = parser.executeRule(rule, "原始", context)
        
        result shouldBe "原始测试"
    }
    
    test("executeRule should pass result variable to JavaScript") {
        val rule = "<js>result.toUpperCase()</js>"
        val context = JavaScriptContext()
        val result = parser.executeRule(rule, "hello", context)
        
        result shouldBe "HELLO"
    }
    
    test("executeRule should execute complex JavaScript code") {
        val rule = """
            <js>
            var text = result;
            text = text.replace('【', '');
            text = text.replace('】', '');
            text
            </js>
        """.trimIndent()
        val context = JavaScriptContext()
        val result = parser.executeRule(rule, "【测试】", context)
        
        result shouldBe "测试"
    }
    
    test("executeRule should access context variables") {
        val rule = "<js>result + ' - ' + key</js>"
        val context = JavaScriptContext(key = "搜索词")
        val result = parser.executeRule(rule, "标题", context)
        
        result shouldBe "标题 - 搜索词"
    }
    
    test("executeRule should access baseUrl from context") {
        val rule = "<js>baseUrl + result</js>"
        val context = JavaScriptContext(baseUrl = "https://example.com")
        val result = parser.executeRule(rule, "/path", context)
        
        result shouldBe "https://example.com/path"
    }
    
    test("executeRule should access book from context") {
        val rule = "<js>book.name + ' - ' + result</js>"
        val book = BookInfo(bookUrl = "https://example.com/book", name = "测试书籍")
        val context = JavaScriptContext(book = book)
        val result = parser.executeRule(rule, "章节", context)
        
        result shouldBe "测试书籍 - 章节"
    }
    
    test("executeRule should access chapter from context") {
        val rule = "<js>chapter.name + ': ' + result</js>"
        val chapter = ChapterInfo(chapterUrl = "https://example.com/chapter", name = "第一章", index = 0)
        val context = JavaScriptContext(chapter = chapter)
        val result = parser.executeRule(rule, "内容", context)
        
        result shouldBe "第一章: 内容"
    }
    
    test("executeRule should access page from context") {
        val rule = "<js>'第' + page + '页: ' + result</js>"
        val context = JavaScriptContext(page = 1)
        val result = parser.executeRule(rule, "内容", context)
        
        result shouldBe "第1页: 内容"
    }
    
    test("executeRule should return null for non-JavaScript rule") {
        val rule = "div.title@text"
        val context = JavaScriptContext()
        val result = parser.executeRule(rule, "测试", context)
        
        result.shouldBeNull()
    }
    
    test("executeRule should return null for empty rule") {
        val rule = ""
        val context = JavaScriptContext()
        val result = parser.executeRule(rule, "测试", context)
        
        result.shouldBeNull()
    }
    
    test("executeRule should handle JavaScript errors gracefully") {
        val rule = "<js>undefinedVariable.method()</js>"
        val context = JavaScriptContext()
        val result = parser.executeRule(rule, "测试", context)
        
        // 应该返回 null 而不是抛出异常
        result.shouldBeNull()
    }
    
    test("executeRule should handle syntax errors gracefully") {
        val rule = "<js>result + </js>"
        val context = JavaScriptContext()
        val result = parser.executeRule(rule, "测试", context)
        
        // 应该返回 null 而不是抛出异常
        result.shouldBeNull()
    }
    
    // ========== 真实场景测试 ==========
    
    test("executeRule should handle real Legado book source JavaScript - title cleanup") {
        // 真实的 Legado 书源场景：清理标题中的特殊字符
        val rule = """
            <js>
            var title = result;
            title = title.replace(/【/g, '');
            title = title.replace(/】/g, '');
            title = title.replace(/\[/g, '');
            title = title.replace(/\]/g, '');
            title
            </js>
        """.trimIndent()
        val context = JavaScriptContext()
        val result = parser.executeRule(rule, "【完结】测试小说[精品]", context)
        
        result shouldBe "完结测试小说精品"
    }
    
    test("executeRule should handle real Legado book source JavaScript - URL construction") {
        // 真实的 Legado 书源场景：构建完整 URL
        val rule = "<js>baseUrl + result</js>"
        val context = JavaScriptContext(baseUrl = "https://www.example.com")
        val result = parser.executeRule(rule, "/book/123", context)
        
        result shouldBe "https://www.example.com/book/123"
    }
    
    test("executeRule should handle real Legado book source JavaScript - conditional logic") {
        // 真实的 Legado 书源场景：通过显式返回值避免依赖脚本块最后表达式的实现细节
        val rule = """
            <js>
            var url = result;
            url.startsWith('http') ? url : baseUrl + url
            </js>
        """.trimIndent()

        val context1 = JavaScriptContext(baseUrl = "https://www.example.com")
        val result1 = parser.executeRule(rule, "/book/123", context1)
        result1.toString() shouldBe "https://www.example.com/book/123"

        val context2 = JavaScriptContext(baseUrl = "https://www.example.com")
        val result2 = parser.executeRule(rule, "https://other.com/book/456", context2)
        result2.toString() shouldBe "https://other.com/book/456"
    }
    
    test("executeRule should handle real Legado book source JavaScript - array operations") {
        // 真实的 Legado 书源场景：数组操作
        val rule = """
            <js>
            var items = result.split(',');
            items.map(function(item) { return item.trim(); }).join(' | ')
            </js>
        """.trimIndent()
        val context = JavaScriptContext()
        val result = parser.executeRule(rule, "玄幻,修真,都市", context)
        
        result shouldBe "玄幻 | 修真 | 都市"
    }
    
    test("executeRule should handle real Legado book source JavaScript - number formatting") {
        // 真实的 Legado 书源场景：数字格式化
        val rule = """
            <js>
            var count = parseInt(result);
            if (count > 10000) {
                (count / 10000).toFixed(1) + '万'
            } else {
                count.toString()
            }
            </js>
        """.trimIndent()
        
        val context1 = JavaScriptContext()
        val result1 = parser.executeRule(rule, "12345", context1)
        result1 shouldBe "1.2万"
        
        val context2 = JavaScriptContext()
        val result2 = parser.executeRule(rule, "999", context2)
        result2 shouldBe "999"
    }
})
