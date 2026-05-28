package org.skepsun.kototoro.core.parser.legado

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject

class AnalyzeRuleModeAlignmentTest : FunSpec({

    test("Map 内容不应仅因宿主对象类型自动进入 Json 模式") {
        val analyzer = AnalyzeRule(
            content = mapOf("name" to "Alpha"),
            runtimeContext = TestLegadoRuleRuntimeContext(),
            baseUrl = "https://example.com",
        )

        analyzer.getString("name") shouldBe ""
    }

    test("List 内容不应仅因宿主对象类型自动进入 Json 模式") {
        val analyzer = AnalyzeRule(
            content = listOf("Alpha", "Beta"),
            runtimeContext = TestLegadoRuleRuntimeContext(),
            baseUrl = "https://example.com",
        )

        analyzer.getString("0") shouldBe ""
    }

    test("非完整 JSON 字符串不应仅因前缀像 JSON 自动进入 Json 模式") {
        val analyzer = AnalyzeRule(
            content = """{"name":"Alpha"""",
            runtimeContext = TestLegadoRuleRuntimeContext(),
            baseUrl = "https://example.com",
        )

        analyzer.getString("$.name") shouldBe ""
    }

    test("完整 JSON 字符串仍应自动进入 Json 模式") {
        val analyzer = AnalyzeRule(
            content = """{"name":"Alpha"}""",
            runtimeContext = TestLegadoRuleRuntimeContext(),
            baseUrl = "https://example.com",
        )

        analyzer.getString("$.name") shouldBe "Alpha"
    }

    test("NativeObject 仍应保留 MD3 的直接属性访问捷径") {
        val context = Context.enter()
        try {
            context.optimizationLevel = -1
            val scope = context.initStandardObjects()
            val obj = context.newObject(scope)
            ScriptableObject.putProperty(obj, "name", "Alpha")

            val analyzer = AnalyzeRule(
                content = obj,
                runtimeContext = TestLegadoRuleRuntimeContext(),
                baseUrl = "https://example.com",
            )

            analyzer.getString("name") shouldBe "Alpha"
        } finally {
            Context.exit()
        }
    }
})
