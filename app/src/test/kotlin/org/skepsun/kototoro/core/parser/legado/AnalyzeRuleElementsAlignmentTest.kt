package org.skepsun.kototoro.core.parser.legado

import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.regex.PatternSyntaxException

class AnalyzeRuleElementsAlignmentTest : FunSpec({

    test("getElement 应执行 makeUpRule 以支持 @put 规则副作用") {
        val runtimeContext = TestLegadoRuleRuntimeContext()
        val analyzer = AnalyzeRule(
            content = "<div><a>Alpha</a><a>Beta</a></div>",
            runtimeContext = runtimeContext,
            baseUrl = "https://example.com",
        )

        val result = analyzer.getElement("@put:{picked:'a@text'}a")

        (result as org.jsoup.select.Elements).size shouldBe 2
        runtimeContext.getVariable("picked") shouldBe "Alpha\nBeta"
    }

    test("getElements 不应执行 makeUpRule，但 @put 仍会通过 putRule 生效") {
        val runtimeContext = TestLegadoRuleRuntimeContext()
        val analyzer = AnalyzeRule(
            content = "<div><a>Alpha</a><a>Beta</a></div>",
            runtimeContext = runtimeContext,
            baseUrl = "https://example.com",
        )

        val result = analyzer.getElements("@put:{picked:'a@text'}a")

        result shouldHaveSize 2
        runtimeContext.getVariable("picked") shouldBe "Alpha\nBeta"
    }

    test("getElements 的 @get 规则在 elements 场景下应保持 MD3 当前异常行为") {
        val runtimeContext = TestLegadoRuleRuntimeContext().apply {
            putVariable("selector", "a")
        }
        val analyzer = AnalyzeRule(
            content = "<div><a>Alpha</a><span>Beta</span></div>",
            runtimeContext = runtimeContext,
            baseUrl = "https://example.com",
        )

        shouldThrow<PatternSyntaxException> {
            analyzer.getElements("@get:{selector}")
        }
    }

    test("elements 场景触发的 Regex 模式不应污染后续普通字符串规则") {
        val analyzer = AnalyzeRule(
            content = "<div><span>Alpha</span></div>",
            runtimeContext = TestLegadoRuleRuntimeContext(),
            baseUrl = "https://example.com",
        )

        analyzer.getElements(":<span>(.*?)</span>") shouldHaveSize 1
        analyzer.getString("span@text") shouldBe "Alpha"
    }
})
