package org.skepsun.kototoro.core.parser.legado

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class AnalyzeByJSoupIndexSyntaxTest : FunSpec({

    test("旧式点号索引语法应与 MD3 一致") {
        val html = """
            <div class="face-info">
                <span>作者：二雷大叔</span>
                <span>连载中</span>
                <span>123456</span>
                <span>都市</span>
            </div>
        """.trimIndent()

        val analyzer = AnalyzeByJSoup(html)

        analyzer.getStringList(".face-info span.1:3@text") shouldContainExactly listOf(
            "连载中",
            "都市",
        )
    }

    test("方括号反向区间索引语法应与 MD3 一致") {
        val html = """
            <ul>
                <li>A</li>
                <li>B</li>
                <li>C</li>
            </ul>
        """.trimIndent()

        val analyzer = AnalyzeByJSoup(html)

        analyzer.getStringList("tag.li[-1:0]@text") shouldContainExactly listOf("C", "B", "A")
    }

    test("旧式排除索引语法应与 MD3 一致") {
        val html = """
            <ul>
                <li>A</li>
                <li>B</li>
                <li>C</li>
                <li>D</li>
            </ul>
        """.trimIndent()

        val analyzer = AnalyzeByJSoup(html)

        analyzer.getStringList("tag.li!0:2@text") shouldContainExactly listOf("B", "D")
    }

    test("方括号混合索引语法应与 MD3 一致") {
        val html = """
            <ul>
                <li>A</li>
                <li>B</li>
                <li>C</li>
                <li>D</li>
                <li>E</li>
            </ul>
        """.trimIndent()

        val analyzer = AnalyzeByJSoup(html)

        analyzer.getStringList("tag.li[-1,1,3]@text") shouldContainExactly listOf("E", "B", "D")
    }

    test("方括号排除索引语法应与 MD3 一致") {
        val html = """
            <ul>
                <li>A</li>
                <li>B</li>
                <li>C</li>
                <li>D</li>
                <li>E</li>
            </ul>
        """.trimIndent()

        val analyzer = AnalyzeByJSoup(html)

        analyzer.getStringList("tag.li[!0,2,-1]@text") shouldContainExactly listOf("B", "D")
    }

    test("方括号区间步进语法应与 MD3 一致") {
        val html = """
            <ul>
                <li>A</li>
                <li>B</li>
                <li>C</li>
                <li>D</li>
                <li>E</li>
                <li>F</li>
            </ul>
        """.trimIndent()

        val analyzer = AnalyzeByJSoup(html)

        analyzer.getStringList("tag.li[0:5:2]@text") shouldContainExactly listOf("A", "C", "E")
    }

    test("方括号索引语法应容忍空白并与 MD3 一致") {
        val html = """
            <ul>
                <li>A</li>
                <li>B</li>
                <li>C</li>
                <li>D</li>
                <li>E</li>
            </ul>
        """.trimIndent()

        val analyzer = AnalyzeByJSoup(html)

        analyzer.getStringList("tag.li[ -1, 1 : 3 : 2 ]@text") shouldContainExactly listOf("E", "B", "D")
    }

    test("AnalyzeRule 应能消费旧式点号索引语法") {
        val html = """
            <div class="face-info">
                <span>作者：二雷大叔</span>
                <span>连载中</span>
                <span>123456</span>
                <span>都市</span>
            </div>
        """.trimIndent()

        val analyzer = AnalyzeRule(
            content = html,
            runtimeContext = TestLegadoRuleRuntimeContext(),
            baseUrl = "https://example.com/book/1",
        )

        analyzer.getString(".face-info span.1:3@text") shouldBe "连载中\n都市"
    }

    test("显式 @CSS 规则应继续使用 Jsoup 原生 attribute selector") {
        val html = """
            <div class="book">
                <a href="/one" data-role="primary">One</a>
                <a href="/two" data-role="secondary">Two</a>
                <a href="/three" data-role="primary">Three</a>
            </div>
        """.trimIndent()

        val analyzer = AnalyzeByJSoup(html)

        analyzer.getStringList("@CSS:a[data-role=primary]@href") shouldContainExactly listOf("/one", "/three")
    }

    test("未显式声明 @CSS 时 attribute selector 也应与 MD3 一致正常工作") {
        val html = """
            <div class="book">
                <a href="/one" data-role="primary">One</a>
                <a href="/two" data-role="secondary">Two</a>
                <a href="/three" data-role="primary">Three</a>
            </div>
        """.trimIndent()

        val analyzer = AnalyzeRule(
            content = html,
            runtimeContext = TestLegadoRuleRuntimeContext(),
            baseUrl = "https://example.com/book/1",
        )

        analyzer.getString("a[data-role=primary]@href") shouldBe "/one\n/three"
    }
})
