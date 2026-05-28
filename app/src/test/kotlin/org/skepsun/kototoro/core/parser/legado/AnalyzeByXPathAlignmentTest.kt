package org.skepsun.kototoro.core.parser.legado

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.shouldBe

class AnalyzeByXPathAlignmentTest : FunSpec({

    test("XPath 的 || 语义应与 MD3 一致") {
        val html = """
            <html>
                <body>
                    <div class="title">标题A</div>
                </body>
            </html>
        """.trimIndent()

        val analyzer = AnalyzeByXPath(html)

        analyzer.getString("//h1/text()||//div[@class='title']/text()") shouldBe "标题A"
    }

    test("XPath 的 && 语义应与 MD3 一致") {
        val html = """
            <html>
                <body>
                    <div class="author">作者甲</div>
                    <div class="category">分类乙</div>
                </body>
            </html>
        """.trimIndent()

        val analyzer = AnalyzeByXPath(html)

        analyzer.getStringList("//div[@class='author']/text()&&//div[@class='category']/text()") shouldContainExactly listOf(
            "作者甲",
            "分类乙",
        )
    }

    test("XPath 的 %% 交织语义应与 MD3 一致") {
        val html = """
            <html>
                <body>
                    <ul>
                        <li><span class="name">A</span><span class="value">1</span></li>
                        <li><span class="name">B</span><span class="value">2</span></li>
                    </ul>
                </body>
            </html>
        """.trimIndent()

        val analyzer = AnalyzeByXPath(html)

        analyzer.getStringList("//span[@class='name']/text()%%//span[@class='value']/text()") shouldContainExactly listOf(
            "A",
            "1",
            "B",
            "2",
        )
    }

    test("XPath getElements 应返回可继续供 AnalyzeRule 链式消费的元素列表") {
        val analyzer = AnalyzeRule(
            content = """
                <html>
                    <body>
                        <div class="book"><span class="name">A</span></div>
                        <div class="book"><span class="name">B</span></div>
                    </body>
                </html>
            """.trimIndent(),
            runtimeContext = TestLegadoRuleRuntimeContext(),
            baseUrl = "https://example.com",
        )

        val result = analyzer.getElement("@XPath://div[@class='book']")

        (result as List<*>).shouldHaveSize(2)
    }

    test("XPath 节点对象应提供接近 MD3 JXNode 的元素语义") {
        val analyzer = AnalyzeByXPath(
            """
                <html>
                    <body>
                        <div class="book"><span class="name">A</span></div>
                    </body>
                </html>
            """.trimIndent(),
        )

        val result = analyzer.getElements("//div[@class='book']")

        val node = result!!.first().shouldBeInstanceOf<LegadoXPathNode>()
        node.isElement shouldBe true
        node.asElement().selectFirst(".name")!!.text() shouldBe "A"
        node.asString() shouldBe """<div class="book"><span class="name">A</span></div>"""
    }

    test("XPath 节点对象应可继续被 JSoup 规则消费以对齐 MD3 AnalyzeByJSoup") {
        val xpathAnalyzer = AnalyzeByXPath(
            """
                <html>
                    <body>
                        <div class="book"><span class="name">A</span><span class="author">甲</span></div>
                        <div class="book"><span class="name">B</span><span class="author">乙</span></div>
                    </body>
                </html>
            """.trimIndent(),
        )

        val firstBook = xpathAnalyzer.getElements("//div[@class='book']")!!.first()
        AnalyzeByJSoup(firstBook).getStringList(".name@text") shouldContainExactly listOf("A")
    }
})
