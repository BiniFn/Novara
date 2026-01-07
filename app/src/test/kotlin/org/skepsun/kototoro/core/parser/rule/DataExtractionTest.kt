package org.skepsun.kototoro.core.parser.rule

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.string.shouldContain as shouldContainString
import org.jsoup.Jsoup
import org.jsoup.nodes.Document


/**
 * 数据提取增强功能测试
 * 
 * 测试以下功能：
 * - JSONPath 提取
 * - 增强的 CSS 选择器（class., tag., id., @tag.tagName.index）
 * - 特殊选择器（@textNodes, @data-*, 反向选择）
 * 
 * 使用真实的 Legado 书源选择器进行测试
 */
class DataExtractionTest : FunSpec({
    
    lateinit var ruleCache: RuleCache
    lateinit var defaultRuleEngine: DefaultRuleEngine
    lateinit var jsonPathParser: JsonPathParser
    lateinit var enhancedCssSelector: EnhancedCssSelector
    
    beforeTest {
        ruleCache = RuleCache()
        defaultRuleEngine = DefaultRuleEngine(ruleCache)
        jsonPathParser = JsonPathParser()
        enhancedCssSelector = EnhancedCssSelector()
    }
    
    context("JSONPath 提取测试") {
        
        test("提取简单的 JSON 字段") {
            val json = """
                {
                    "title": "测试书籍",
                    "author": "测试作者",
                    "price": 29.99
                }
            """.trimIndent()
            
            val doc = Jsoup.parse("<div>$json</div>")
            val element = doc.body()
            
            val result = jsonPathParser.parse(json, "$.title")
            result shouldBe "测试书籍"
        }
        
        test("提取嵌套的 JSON 字段") {
            val json = """
                {
                    "store": {
                        "book": {
                            "title": "深入理解计算机系统",
                            "author": "Randal E. Bryant"
                        }
                    }
                }
            """.trimIndent()
            
            val result = jsonPathParser.parse(json, "$.store.book.title")
            result shouldBe "深入理解计算机系统"
        }
        
        test("提取数组中的元素") {
            val json = """
                {
                    "books": [
                        {"title": "书籍1"},
                        {"title": "书籍2"},
                        {"title": "书籍3"}
                    ]
                }
            """.trimIndent()
            
            val result = jsonPathParser.parse(json, "$.books[0].title")
            result shouldBe "书籍1"
        }
        
        test("提取所有数组元素") {
            val json = """
                {
                    "books": [
                        {"title": "书籍1"},
                        {"title": "书籍2"},
                        {"title": "书籍3"}
                    ]
                }
            """.trimIndent()
            
            val result = jsonPathParser.parseList(json, "$.books[*].title")
            result shouldBe listOf("书籍1", "书籍2", "书籍3")
        }
        
        test("使用过滤器提取") {
            val json = """
                {
                    "books": [
                        {"title": "便宜书", "price": 9.99},
                        {"title": "贵书", "price": 99.99},
                        {"title": "中等书", "price": 29.99}
                    ]
                }
            """.trimIndent()
            
            val result = jsonPathParser.parseList(json, "$..books[?(@.price < 30)].title")
            result.size shouldBe 2
            result shouldContain "便宜书"
            result shouldContain "中等书"
        }
        
        test("递归查找字段") {
            val json = """
                {
                    "store": {
                        "book": {
                            "title": "书籍1",
                            "chapter": {
                                "title": "章节1"
                            }
                        }
                    }
                }
            """.trimIndent()
            
            val result = jsonPathParser.parseList(json, "$..title")
            result.size shouldBe 2
            result shouldContain "书籍1"
            result shouldContain "章节1"
        }
    }
    
    context("增强的 CSS 选择器测试") {
        
        test("class.className 语法") {
            val html = """
                <div>
                    <p class="title">标题文本</p>
                    <p class="content">内容文本</p>
                </div>
            """.trimIndent()
            
            val doc = Jsoup.parse(html)
            val elements = enhancedCssSelector.select(doc.body(), "class.title")
            
            elements.size shouldBe 1
            elements.first()?.text() shouldBe "标题文本"
        }
        
        test("tag.tagName 语法") {
            val html = """
                <div>
                    <h1>标题1</h1>
                    <h2>标题2</h2>
                    <p>段落</p>
                </div>
            """.trimIndent()
            
            val doc = Jsoup.parse(html)
            val elements = enhancedCssSelector.select(doc.body(), "tag.h1")
            
            elements.size shouldBe 1
            elements.first()?.text() shouldBe "标题1"
        }
        
        test("id.idName 语法") {
            val html = """
                <div>
                    <div id="header">头部</div>
                    <div id="content">内容</div>
                </div>
            """.trimIndent()
            
            val doc = Jsoup.parse(html)
            val elements = enhancedCssSelector.select(doc.body(), "id.header")
            
            elements.size shouldBe 1
            elements.first()?.text() shouldBe "头部"
        }
        
        test("@tag.tagName.index 语法") {
            val html = """
                <div>
                    <p>第一段</p>
                    <p>第二段</p>
                    <p>第三段</p>
                </div>
            """.trimIndent()
            
            val doc = Jsoup.parse(html)
            val elements = enhancedCssSelector.select(doc.body().child(0), "@tag.p.1")
            
            elements.size shouldBe 1
            elements.first()?.text() shouldBe "第二段"
        }
    }
    
    context("特殊选择器测试") {
        
        test("@textNodes 选择器") {
            val html = """
                <div>
                    直接文本1
                    <span>嵌套文本</span>
                    直接文本2
                </div>
            """.trimIndent()
            
            val doc = Jsoup.parse(html)
            val elements = enhancedCssSelector.select(doc.body().child(0), "@textNodes")
            
            elements.size shouldBe 2
            elements[0].text().trim() shouldBe "直接文本1"
            elements[1].text().trim() shouldBe "直接文本2"
        }
        
        test("@data-* 属性提取") {
            val html = """
                <div>
                    <img src="thumb.jpg" data-src="full.jpg" data-original="original.jpg" />
                </div>
            """.trimIndent()
            
            val doc = Jsoup.parse(html)
            val img = doc.select("img").first()!!
            
            val elements = enhancedCssSelector.select(img, "@data-src")
            elements.size shouldBe 1
            elements.first()?.text() shouldBe "full.jpg"
        }
        
        test("反向选择 [-1:0]") {
            val html = """
                <div>
                    <p>第一段</p>
                    <p>第二段</p>
                    <p>第三段</p>
                </div>
            """.trimIndent()
            
            val doc = Jsoup.parse(html)
            val elements = enhancedCssSelector.select(doc.body(), "p[-1:0]")
            
            elements.size shouldBe 3
            elements[0].text() shouldBe "第三段"
            elements[1].text() shouldBe "第二段"
            elements[2].text() shouldBe "第一段"
        }
    }
    
    context("集成测试 - 使用真实的 Legado 选择器") {
        
        test("笔趣阁书源 - 搜索结果解析") {
            val html = """
                <div id="main">
                    <div class="result-list">
                        <div class="result-item">
                            <h3 class="result-game-item-title">
                                <a href="/book/123">测试小说1</a>
                            </h3>
                            <p class="result-game-item-info">
                                <span class="author">作者1</span>
                            </p>
                        </div>
                        <div class="result-item">
                            <h3 class="result-game-item-title">
                                <a href="/book/456">测试小说2</a>
                            </h3>
                            <p class="result-game-item-info">
                                <span class="author">作者2</span>
                            </p>
                        </div>
                    </div>
                </div>
            """.trimIndent()
            
            val doc = Jsoup.parse(html)
            
            // 测试列表选择
            val items = doc.select(".result-item")
            items.size shouldBe 2
            
            // 测试标题提取 - 使用增强的 CSS 选择器
            val firstItem = items.first()!!
            val titleElements = enhancedCssSelector.select(firstItem, "class.result-game-item-title")
            titleElements.size shouldBe 1
            val title = titleElements.first()?.select("a")?.text() ?: ""
            title shouldBe "测试小说1"
            
            // 测试作者提取
            val authorElements = enhancedCssSelector.select(firstItem, "class.author")
            authorElements.size shouldBe 1
            val author = authorElements.first()?.text() ?: ""
            author shouldBe "作者1"
        }
        
        test("起点中文网书源 - JSON 数据解析") {
            val json = """
                {
                    "data": {
                        "bookList": [
                            {
                                "bookName": "诡秘之主",
                                "authorName": "爱潜水的乌贼",
                                "bookId": "1010868264"
                            },
                            {
                                "bookName": "大奉打更人",
                                "authorName": "卖报小郎君",
                                "bookId": "1010734492"
                            }
                        ]
                    }
                }
            """.trimIndent()
            
            // 测试 JSONPath 提取
            val bookNames = jsonPathParser.parseList(json, "$.data.bookList[*].bookName")
            bookNames.size shouldBe 2
            bookNames shouldContain "诡秘之主"
            bookNames shouldContain "大奉打更人"
            
            // 测试单个字段提取
            val firstBook = jsonPathParser.parse(json, "$.data.bookList[0].bookName")
            firstBook shouldBe "诡秘之主"
        }
        
        test("规则链 - CSS + JSONPath") {
            val html = """
                <div class="book-info">
                    <script type="application/json">
                        {
                            "title": "测试书籍",
                            "author": "测试作者",
                            "chapters": [
                                {"name": "第一章", "url": "/chapter/1"},
                                {"name": "第二章", "url": "/chapter/2"}
                            ]
                        }
                    </script>
                </div>
            """.trimIndent()
            
            val doc = Jsoup.parse(html)
            
            // 先用 CSS 选择器获取 script 标签的内容，再用 JSONPath 提取
            val script = doc.select("script[type='application/json']").first()!!
            val json = script.html()
            
            val title = jsonPathParser.parse(json, "$.title")
            title shouldBe "测试书籍"
            
            val chapterNames = jsonPathParser.parseList(json, "$.chapters[*].name")
            chapterNames shouldBe listOf("第一章", "第二章")
        }
        
        test("综合测试 - 所有增强功能") {
            // 测试 JSONPath
            val json = """{"data": {"value": "test"}}"""
            jsonPathParser.parse(json, "$.data.value") shouldBe "test"
            
            // 测试增强 CSS 选择器
            val html = """<div><p class="test">content</p></div>"""
            val doc = Jsoup.parse(html)
            val elements = enhancedCssSelector.select(doc.body(), "class.test")
            elements.size shouldBe 1
            elements.first()?.text() shouldBe "content"
        }
    }
})
