package org.skepsun.kototoro.core.parser.legado

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AnalyzeByJsonPathAlignmentTest : FunSpec({

    test("缺失的 JSONPath 在 MD3 中应返回空串") {
        val analyzer = AnalyzeByJsonPath("""{"name":"Alpha"}""")

        analyzer.getString("$.missing") shouldBe ""
    }

    test("JSONPath 列表中的 null 在 MD3 中应保留为字符串 null") {
        val analyzer = AnalyzeByJsonPath("""{"items":[1,null,"x"]}""")

        analyzer.getStringList("$.items[*]") shouldContainExactly listOf("1", "null", "x")
    }

    test("JSONPath 单值 null 在 getStringList 中应与 MD3 一样返回空列表") {
        val analyzer = AnalyzeByJsonPath("""{"value":null}""")

        analyzer.getStringList("$.value") shouldContainExactly emptyList()
    }

    test("JSONPath 单值 null 在 getList 中应与 MD3 一样透传为 null") {
        val analyzer = AnalyzeByJsonPath("""{"value":null}""")

        analyzer.getList("$.value").shouldBeNull()
    }
})
