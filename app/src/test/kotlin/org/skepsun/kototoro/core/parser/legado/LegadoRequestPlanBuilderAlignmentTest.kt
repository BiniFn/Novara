package org.skepsun.kototoro.core.parser.legado

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LegadoRequestPlanBuilderAlignmentTest : FunSpec({

    test("GET query 参数应按键值对编码而不是整体 URL 编码") {
        val plan = AnalyzeUrl(
            ruleUrl = "https://example.com/search?q=中文 词&from=1,{'charset':'utf-8'}",
            baseUrl = "https://example.com",
        ).build()

        plan.url shouldBe "https://example.com/search?q=%E4%B8%AD%E6%96%87%20%E8%AF%8D&from=1"
    }

    test("charset=escape 时 query 参数应使用 escape 语义") {
        val plan = AnalyzeUrl(
            ruleUrl = "https://example.com/search?q=中文&tag=A B,{'charset':'escape'}",
            baseUrl = "https://example.com",
        ).build()

        plan.url shouldBe "https://example.com/search?q=%u4e2d%u6587&tag=A%20B"
    }

    test("bodyJs 在 MD3 中应保留到响应阶段而不是请求阶段执行") {
        val plan = AnalyzeUrl(
            ruleUrl = "https://example.com/api,{'method':'POST','body':'page=1','bodyJs':'result + \"-after\"'}",
            baseUrl = "https://example.com",
            jsEvaluator = { script, result -> "$script|$result" },
        ).build()

        plan.body shouldBe "page=1"
        plan.bodyJs shouldBe "result + \"-after\""
        plan.bodyIsForm shouldBe true
    }

    test("url option js 应在请求阶段改写最终 URL") {
        val plan = AnalyzeUrl(
            ruleUrl = "https://example.com/api/list,{'js':'https://cdn.example.com/final'}",
            baseUrl = "https://example.com",
            jsEvaluator = { _, _ -> "https://cdn.example.com/final" },
        ).build()

        plan.url shouldBe "https://cdn.example.com/final"
    }

    test("webView 相关 option 应保留到请求计划中") {
        val plan = AnalyzeUrl(
            ruleUrl = "https://example.com/page,{'webView':true,'webJs':'document.title','webViewDelayTime':1800,'serverID':'7','dnsIp':'1.1.1.1','proxy':'http://127.0.0.1:7890'}",
            baseUrl = "https://example.com",
        ).build()

        plan.useWebView shouldBe true
        plan.webJs shouldBe "document.title"
        plan.webViewDelayTime shouldBe 1800L
        plan.serverId shouldBe 7L
        plan.dnsIp shouldBe "1.1.1.1"
        plan.proxy shouldBe "http://127.0.0.1:7890"
    }

    test("request plan 应保留 AnalyzeUrl 传入的 timeout 配置") {
        val plan = AnalyzeUrl(
            ruleUrl = "https://example.com/page,{'method':'POST','body':'a=1'}",
            baseUrl = "https://example.com",
            readTimeoutMs = 4_000L,
            callTimeoutMs = 12_000L,
        ).build()

        plan.readTimeoutMs shouldBe 4_000L
        plan.callTimeoutMs shouldBe 12_000L
    }

    test("默认 headers 应进入请求计划，且 proxy 头应提升为请求选项") {
        val plan = AnalyzeUrl(
            ruleUrl = "https://example.com/page",
            baseUrl = "https://example.com",
            defaultHeaders = mapOf(
                "User-Agent" to "UA-1",
                "proxy" to "http://127.0.0.1:8899",
            ),
        ).build()

        plan.headers shouldBe mapOf("User-Agent" to "UA-1")
        plan.proxy shouldBe "http://127.0.0.1:8899"
    }

    test("option headers 应覆盖默认 headers") {
        val plan = AnalyzeUrl(
            ruleUrl = "https://example.com/page,{'headers':{'User-Agent':'UA-2','Referer':'https://ref.example.com/'}}",
            baseUrl = "https://example.com",
            defaultHeaders = mapOf(
                "User-Agent" to "UA-1",
                "Accept" to "application/json",
            ),
        ).build()

        plan.headers shouldBe mapOf(
            "User-Agent" to "UA-2",
            "Accept" to "application/json",
            "Referer" to "https://ref.example.com/",
        )
    }

    test("双花括号 key 变量应按 MD3 走运行时求值而不是模板层预编码") {
        val plan = AnalyzeUrl(
            ruleUrl = "https://example.com/search/{{key}},{'charset':'utf-8'}",
            key = "中 文",
            baseUrl = "https://example.com",
            jsEvaluator = { script, _ ->
                when (script.trim()) {
                    "key" -> "中 文"
                    else -> script
                }
            },
        ).build()

        plan.url shouldBe "https://example.com/search/中 文"
    }

    test("双花括号 page 与 baseUrl 变量应按 MD3 走运行时求值") {
        val plan = AnalyzeUrl(
            ruleUrl = "https://example.com/{{page}}?from={{baseUrl}}",
            page = 3,
            baseUrl = "https://host.example.com/root",
            jsEvaluator = { script, _ ->
                when (script.trim()) {
                    "page" -> "3"
                    "baseUrl" -> "https://host.example.com/root"
                    else -> script
                }
            },
        ).build()

        plan.url shouldBe "https://example.com/3?from=https://host.example.com/root"
    }
})
