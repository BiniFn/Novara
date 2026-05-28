package org.skepsun.kototoro.core.parser.legado

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import androidx.media3.common.MediaItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.skepsun.kototoro.core.javascript.BookInfo
import org.skepsun.kototoro.core.javascript.ChapterInfo
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.parser.legado.runtime.LegadoHttpExecutor
import org.skepsun.kototoro.core.parser.legado.runtime.LegadoHttpResponse
import org.skepsun.kototoro.core.parser.legado.runtime.LegadoRequestPlan

class AnalyzeUrlRuntimeSemanticsTest : FunSpec({

    test("AnalyzeUrl put/get 在 chapter 存在时应优先写入 chapter，贴近 MD3 语义") {
        val runtimeContext = TestLegadoRuleRuntimeContext()
        val chapter = ChapterInfo(
            chapterUrl = "https://example.com/book/1/3",
            name = "第三章",
            index = 3,
        )
        runtimeContext.setChapter(chapter)

        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/list",
            baseUrl = "https://example.com",
            runtimeContext = runtimeContext,
            chapter = chapter,
        )

        analyzer.put("token", "abc") shouldBe "abc"
        analyzer.get("title") shouldBe "第三章"
        analyzer.get("token") shouldBe "abc"
        chapter.getVariable("token") shouldBe "abc"
        runtimeContext.getSourceVariable("token") shouldBe ""
    }

    test("AnalyzeUrl 显式 ruleData 变量应优先于 runtime 全局变量，贴近 MD3 语义") {
        val runtimeContext = TestLegadoRuleRuntimeContext().apply {
            putVariable("token", "runtime")
        }
        val ruleData = RuleData().apply {
            putVariable("token", "ruleData")
        }
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/list",
            baseUrl = "https://example.com",
            runtimeContext = runtimeContext,
            ruleData = ruleData,
        )

        analyzer.get("token") shouldBe "ruleData"
    }

    test("AnalyzeUrl chapter 变量仍应优先于显式 ruleData，贴近 MD3 优先级") {
        val runtimeContext = TestLegadoRuleRuntimeContext()
        val chapter = ChapterInfo(
            chapterUrl = "https://example.com/book/1/5",
            name = "第五章",
            index = 5,
        ).apply {
            putVariable("token", "chapter")
        }
        val ruleData = RuleData().apply {
            putVariable("token", "ruleData")
        }
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/list",
            baseUrl = "https://example.com",
            runtimeContext = runtimeContext,
            chapter = chapter,
            ruleData = ruleData,
        )

        analyzer.get("token") shouldBe "chapter"
    }

    test("AnalyzeUrl evalJS 应注入 page key baseUrl 与 chapter 上下文") {
        val runtimeContext = TestLegadoRuleRuntimeContext()
        val chapter = ChapterInfo(
            chapterUrl = "https://example.com/book/1/3",
            name = "第三章",
            index = 3,
        )
        runtimeContext.setBook(
            BookInfo(
                bookUrl = "https://example.com/book/1",
                name = "测试书",
                author = "作者甲",
            ),
        )
        runtimeContext.setChapter(chapter)

        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/list",
            key = "keyword",
            page = 2,
            baseUrl = "https://example.com",
            runtimeContext = runtimeContext,
            chapter = chapter,
            jsEvaluator = { _, _ ->
                listOf(
                    runtimeContext.getVariable("page"),
                    runtimeContext.getVariable("key"),
                    runtimeContext.getVariable("baseUrl"),
                    runtimeContext.getVariable("title"),
                    runtimeContext.getVariable("chapterUrl"),
                    runtimeContext.getVariable("bookName"),
                ).joinToString("|")
            },
        )

        analyzer.evalJS("ignored", "seed") shouldBe
            "2|keyword|https://example.com|第三章|https://example.com/book/1/3|测试书"
    }

    test("AnalyzeUrl evalJS 应把 java 绑定为当前 AnalyzeUrl 并保留 source/tag 语义") {
        val runtimeContext = TestLegadoRuleRuntimeContext { _, context ->
            val java = requireNotNull(context.getAllVariables()["java"])
            val isPost = java.javaClass.getMethod("isPost").invoke(java)
            val source = java.javaClass.getMethod("getSource").invoke(java)
            val tag = java.javaClass.getMethod("getTag").invoke(java)
            "$isPost|$source|$tag"
        }
        runtimeContext.setSource(
            sourceObject = "source-object",
            sourceTag = "Source-A",
        )

        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/post,{\"method\":\"POST\"}",
            baseUrl = "https://example.com",
            runtimeContext = runtimeContext,
        )

        analyzer.evalJS("ignored") shouldBe "true|source-object|Source-A"
    }

    test("AnalyzeUrl 在 ruleData 为 book-like 对象时 JS 侧应暴露 book，贴近 MD3") {
        val runtimeContext = TestLegadoRuleRuntimeContext(
            jsEvaluator = { _, context ->
                val book = requireNotNull(context.getVariable("book"))
                val name = LegadoReflectiveAccess.readProperty(book, "name")
                val bookUrl = LegadoReflectiveAccess.readProperty(book, "bookUrl")
                "$name|$bookUrl"
            },
        )
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/list",
            baseUrl = "https://example.com",
            runtimeContext = runtimeContext,
            ruleData = LegacyBookLikeRuleData(
                bookUrl = "https://example.com/book/88",
                name = "详情书籍",
            ),
        )

        analyzer.evalJS("ignored", "seed") shouldBe "详情书籍|https://example.com/book/88"
    }

    test("loginCheckJs 桥接应允许修改 headerMap 并重放 getResponse") {
        var capturedPlan: LegadoRequestPlan? = null
        val bridge = AnalyzeUrlLoginJsBridge(
            basePlan = LegadoRequestPlan(
                url = "https://example.com/protected",
                headers = mapOf("User-Agent" to "UA-1"),
            ),
            httpExecutor = object : LegadoHttpExecutor {
                override suspend fun execute(plan: LegadoRequestPlan): LegadoHttpResponse {
                    capturedPlan = plan
                    return LegadoHttpResponse(
                        url = "https://example.com/final",
                        body = "{\"ok\":true}",
                        code = 200,
                        headers = mapOf("Content-Type" to "application/json"),
                    )
                }
            },
            initialHeaders = mapOf("User-Agent" to "UA-1"),
        )

        bridge.getHeaderMap().putAll(mapOf("Authorization" to "Bearer token"))
        val response = bridge.getResponse()

        capturedPlan!!.headers.shouldContain("Authorization" to "Bearer token")
        response.request.url.toString() shouldBe "https://example.com/final"
        response.header("Content-Type") shouldBe "application/json"
        response.body?.string() shouldBe "{\"ok\":true}"
    }

    test("loginCheckJs 结果对象应同时支持 body 字符串与 body().string 语义") {
        val response = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("https://example.com/audio").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .headers(
                okhttp3.Headers.Builder()
                    .add("Content-Type", "application/json")
                    .build(),
            )
            .body("""{"status":40000001}""".toResponseBody("application/json".toMediaType()))
            .build()

        val result = LoginCheckStrResponse(
            response = response,
            bodyText = """{"status":40000001}""",
        )

        result.body shouldBe """{"status":40000001}"""
        result.body().string() shouldBe """{"status":40000001}"""
        result.headers().get("Content-Type") shouldBe "application/json"
        result.url shouldBe "https://example.com/audio"
    }

    test("loginCheckJs 桥接应补齐接近 MD3 的 AnalyzeUrl 辅助能力") {
        val source = LegacyUrlSource(
            tag = "Source-A",
            bookSourceUrl = "https://source.example.com",
        )
        val bridge = AnalyzeUrlLoginJsBridge(
            basePlan = LegadoRequestPlan(
                url = "https://example.com/post",
                method = "POST",
                headers = mapOf("User-Agent" to "UA-POST"),
            ),
            httpExecutor = object : LegadoHttpExecutor {
                override suspend fun execute(plan: LegadoRequestPlan): LegadoHttpResponse {
                    return LegadoHttpResponse(
                        url = "https://example.com/final.bin",
                        body = "BIN",
                        code = 200,
                        headers = mapOf("Content-Type" to "application/octet-stream"),
                    )
                }
            },
            initialHeaders = mapOf("User-Agent" to "UA-POST"),
            sourceObject = source,
            infoMap = linkedMapOf("token" to "seed"),
        )

        bridge.getUserAgent() shouldBe "UA-POST"
        bridge.isPost() shouldBe true
        bridge.getUrlAndHeaders().first shouldBe "https://example.com/post"
        bridge.getGlideUrl().shouldBeInstanceOf<Pair<*, *>>().first shouldBe "https://example.com/post"
        val mediaItem = bridge.getMediaItem()
        mediaItem.shouldBeInstanceOf<MediaItem>()
        mediaItem.mediaId shouldContain "https://example.com/post"
        mediaItem.mediaId shouldContain "User-Agent"
        bridge.getTag() shouldBe "Source-A"
        bridge.getSource() shouldBe source
        bridge.get("token") shouldBe "seed"
        bridge.put("auth", "ok") shouldBe "ok"
        bridge.get("auth") shouldBe "ok"
        source.get("auth") shouldBe "ok"
        bridge.getByteArray().decodeToString() shouldBe "BIN"
        bridge.getInputStream().readBytes().decodeToString() shouldBe "BIN"

        val err = bridge.getErrStrResponse(IllegalStateException("boom"))
        err.code() shouldBe 500
        err.body shouldContain "IllegalStateException: boom"
    }

    test("loginCheckJs 桥接应向 JS 暴露 infoMap 变量") {
        val bridge = AnalyzeUrlLoginJsBridge(
            basePlan = LegadoRequestPlan(url = "https://example.com/post"),
            httpExecutor = object : LegadoHttpExecutor {
                override suspend fun execute(plan: LegadoRequestPlan): LegadoHttpResponse {
                    return LegadoHttpResponse(
                        url = plan.url,
                        body = "ok",
                        code = 200,
                    )
                }
            },
            initialHeaders = emptyMap(),
            infoMap = linkedMapOf("token" to "seed", "username" to "alice"),
        )

        bridge.getBridgeBindings()["infoMap"].shouldBeInstanceOf<Map<*, *>>()["token"] shouldBe "seed"
        bridge.get("username") shouldBe "alice"
    }

    test("loginCheckJs 桥接的 getStrResponse 第三个参数应对齐 MD3 的 useWebView 语义") {
        var capturedPlan: LegadoRequestPlan? = null
        val bridge = AnalyzeUrlLoginJsBridge(
            basePlan = LegadoRequestPlan(
                url = "https://example.com/page",
                useWebView = true,
                webJs = "document.title",
            ),
            httpExecutor = object : LegadoHttpExecutor {
                override suspend fun execute(plan: LegadoRequestPlan): LegadoHttpResponse {
                    capturedPlan = plan
                    return LegadoHttpResponse(
                        url = plan.url,
                        body = "ok",
                        code = 200,
                    )
                }
            },
            initialHeaders = emptyMap(),
        )

        bridge.getStrResponse("document.body.innerHTML", ".*audio.*", false)

        capturedPlan!!.webJs shouldBe "document.body.innerHTML"
        capturedPlan!!.sourceRegex shouldBe ".*audio.*"
        capturedPlan!!.useWebView shouldBe false
    }

    test("AnalyzeUrl 应暴露接近 MD3 的运行时请求方法") {
        var capturedPlan: LegadoRequestPlan? = null
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/api?q=1,{\"headers\":{\"User-Agent\":\"UA-X\",\"Authorization\":\"Bearer a\"}}",
            baseUrl = "https://example.com",
            defaultHeaders = mapOf("Referer" to "https://example.com/"),
            httpExecutor = object : LegadoHttpExecutor {
                override suspend fun execute(plan: LegadoRequestPlan): LegadoHttpResponse {
                    capturedPlan = plan
                    return LegadoHttpResponse(
                        url = "https://example.com/final?q=1",
                        body = """{"ok":true}""",
                        code = 200,
                        headers = mapOf("Content-Type" to "application/json"),
                    )
                }
            },
        )

        analyzer.getHeaderMap().getValue("User-Agent") shouldBe "UA-X"
        analyzer.getHeaderMap().getValue("Authorization") shouldBe "Bearer a"
        analyzer.getUrlAndHeaders().first shouldBe "https://example.com/api?q=1"
        analyzer.getUserAgent() shouldBe "UA-X"

        val strResponse = analyzer.getStrResponse()
        strResponse.body() shouldBe """{"ok":true}"""
        strResponse.headers().get("Content-Type") shouldBe "application/json"

        val response = analyzer.getResponse()
        response.request.url.toString() shouldBe "https://example.com/final?q=1"
        response.body?.string() shouldBe """{"ok":true}"""

        capturedPlan!!.url shouldBe "https://example.com/api?q=1"
        capturedPlan!!.headers["Authorization"] shouldBe "Bearer a"
    }

    test("AnalyzeUrl getHeaderMap 修改后应影响后续请求") {
        var capturedPlan: LegadoRequestPlan? = null
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/protected,{\"headers\":{\"User-Agent\":\"UA-1\"}}",
            baseUrl = "https://example.com",
            httpExecutor = object : LegadoHttpExecutor {
                override suspend fun execute(plan: LegadoRequestPlan): LegadoHttpResponse {
                    capturedPlan = plan
                    return LegadoHttpResponse(
                        url = plan.url,
                        body = "ok",
                        code = 200,
                    )
                }
            },
        )

        analyzer.getHeaderMap()["Authorization"] = "Bearer changed"
        analyzer.getStrResponse()

        capturedPlan!!.headers["Authorization"] shouldBe "Bearer changed"
        analyzer.getUserAgent() shouldBe "UA-1"
    }

    test("AnalyzeUrl 应暴露接近 MD3 的只读状态字段") {
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/file/index.html?q=1,{\"headers\":{\"User-Agent\":\"UA-S\"},\"type\":\"jpg\",\"serverID\":\"42\"}",
            baseUrl = "https://example.com",
        )

        analyzer.ruleUrl shouldBe "https://example.com/file/index.html?q=1,{\"headers\":{\"User-Agent\":\"UA-S\"},\"type\":\"jpg\",\"serverID\":\"42\"}"
        analyzer.url shouldBe "https://example.com/file/index.html?q=1"
        analyzer.urlNoQuery shouldBe "https://example.com/file/index.html"
        analyzer.type shouldBe "jpg"
        analyzer.serverID shouldBe 42L
        analyzer.getHeaderMap()["User-Agent"] shouldBe "UA-S"
    }

    test("AnalyzeUrl source 兼容构造应合并 source.header 与 headerMapF") {
        val source = LegadoBookSource(
            bookSourceName = "源B",
            bookSourceUrl = "https://source.example.com",
            header = """{"User-Agent":"UA-S","Referer":"https://ref.example.com/"}""",
            enabledCookieJar = false,
        )
        val analyzer = AnalyzeUrl(
            mUrl = "/detail/2",
            source = source,
            headerMapF = mapOf("Authorization" to "Bearer token"),
        )

        analyzer.url shouldBe "https://source.example.com/detail/2"
        analyzer.getHeaderMap() shouldBe linkedMapOf(
            "User-Agent" to "UA-S",
            "Referer" to "https://ref.example.com/",
            "Authorization" to "Bearer token",
        )
        analyzer.build().enableCookieJar shouldBe false
    }

    test("AnalyzeUrl 无 source 兼容构造应支持 MD3 的 key timeout 与 headerMapF 参数名") {
        val analyzer = AnalyzeUrl(
            mUrl = "https://example.com/search/{{key}}",
            key = "Alpha",
            baseUrl = "https://example.com",
            headerMapF = linkedMapOf("User-Agent" to "UA-D", "Referer" to "https://ref.example.com/"),
            readTimeout = 3_000L,
            callTimeout = 6_000L,
            jsEvaluator = { script, _ ->
                if (script == "key") "Alpha" else script
            },
        )

        analyzer.url shouldBe "https://example.com/search/Alpha"
        analyzer.headerMap shouldBe linkedMapOf(
            "User-Agent" to "UA-D",
            "Referer" to "https://ref.example.com/",
        )
        analyzer.build().readTimeoutMs shouldBe 3_000L
        analyzer.build().callTimeoutMs shouldBe 6_000L
    }

    test("AnalyzeUrl source 兼容构造应按 MD3 支持 hasLoginHeader 头合并") {
        val runtimeContext = TestLegadoRuleRuntimeContext()
        runtimeContext.setSource(
            sourceObject = object {
                @Suppress("unused")
                fun getHeaderMap(hasLoginHeader: Boolean): Map<String, String> {
                    return linkedMapOf("User-Agent" to "UA-S").apply {
                        if (hasLoginHeader) {
                            put("Authorization", "Bearer login")
                        }
                    }
                }
            },
            sourceTag = "Source-Login",
        )
        val source = LegadoBookSource(
            bookSourceName = "源登录",
            bookSourceUrl = "https://source.example.com",
            header = """{"Ignored":"1"}""",
        )

        val withLoginHeader = AnalyzeUrl(
            mUrl = "/detail/2",
            source = source,
            runtimeContext = runtimeContext,
            hasLoginHeader = true,
        )
        val withoutLoginHeader = AnalyzeUrl(
            mUrl = "/detail/2",
            source = source,
            runtimeContext = runtimeContext,
            hasLoginHeader = false,
        )

        withLoginHeader.headerMap shouldBe linkedMapOf(
            "User-Agent" to "UA-S",
            "Authorization" to "Bearer login",
        )
        withoutLoginHeader.headerMap shouldBe linkedMapOf(
            "User-Agent" to "UA-S",
        )
    }

    test("AnalyzeUrl source 兼容构造在无 runtimeContext 时也应保留 source 与 tag 语义") {
        val source = LegadoBookSource(
            bookSourceName = "源默认上下文",
            bookSourceUrl = "https://source.example.com",
        )
        val analyzer = AnalyzeUrl(
            mUrl = "/detail/9",
            source = source,
        )

        analyzer.getSource() shouldBe source
        analyzer.getTag() shouldBe "源默认上下文"
    }

    test("AnalyzeUrl source 兼容构造应尽量对齐 MD3 走 source-like 对象的 put/get 与 baseUrl") {
        val source = LegacyUrlSource(
            tag = "RSS-Url-Source",
            bookSourceUrl = "https://rss.example.com",
        )
        val analyzer = AnalyzeUrl(
            mUrl = "/entry/7",
            source = source,
        )

        analyzer.url shouldBe "https://rss.example.com/entry/7"
        analyzer.put("token", "Alpha") shouldBe "Alpha"
        analyzer.get("token") shouldBe "Alpha"
        source.get("token") shouldBe "Alpha"
        analyzer.getTag() shouldBe "RSS-Url-Source"
        analyzer.getSource() shouldBe source
    }

    test("AnalyzeUrl put 在 chapter 存在时不应再向 source 变量扩散写入，贴近 MD3 首层命中语义") {
        val runtimeContext = TestLegadoRuleRuntimeContext()
        val source = LegacyUrlSource(
            tag = "RSS-Url-Source",
            bookSourceUrl = "https://rss.example.com",
        )
        val chapter = ChapterInfo(
            chapterUrl = "https://rss.example.com/chapter/11",
            name = "第十一章",
            index = 11,
        )
        runtimeContext.setSource(source, "RSS-Url-Source")
        runtimeContext.setChapter(chapter)
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/list",
            baseUrl = "https://example.com",
            runtimeContext = runtimeContext,
            chapter = chapter,
        )

        analyzer.put("token", "chapterOnly") shouldBe "chapterOnly"
        chapter.getVariable("token") shouldBe "chapterOnly"
        source.get("token") shouldBe ""
        runtimeContext.getSourceVariable("token") shouldBe ""
    }

    test("AnalyzeUrl source.header 应按 MD3 支持 @js 头配置求值") {
        val analyzer = AnalyzeUrl(
            mUrl = "/detail/2",
            source = LegadoBookSource(
                bookSourceName = "源JS",
                bookSourceUrl = "https://source.example.com",
                header = """@js:{"User-Agent":"UA-JS","Referer":"https://js.example.com/"}""",
            ),
            jsEvaluator = { _, _ ->
                """{"User-Agent":"UA-JS","Referer":"https://js.example.com/"}"""
            },
        )

        analyzer.headerMap shouldBe linkedMapOf(
            "User-Agent" to "UA-JS",
            "Referer" to "https://js.example.com/",
        )
    }

    test("AnalyzeUrl initUrl 应重建请求计划并清理运行时 header 覆盖") {
        var capturedPlan: LegadoRequestPlan? = null
        val runtimeContext = TestLegadoRuleRuntimeContext()
        runtimeContext.putVariable("token", "old")
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/api/{{token}},{\"headers\":{\"User-Agent\":\"UA-1\"}}",
            baseUrl = "https://example.com",
            runtimeContext = runtimeContext,
            jsEvaluator = { script, _ ->
                if (script == "token") runtimeContext.getVariable("token") else script
            },
            httpExecutor = object : LegadoHttpExecutor {
                override suspend fun execute(plan: LegadoRequestPlan): LegadoHttpResponse {
                    capturedPlan = plan
                    return LegadoHttpResponse(
                        url = plan.url,
                        body = "ok",
                        code = 200,
                    )
                }
            },
        )

        analyzer.getHeaderMap()["Authorization"] = "Bearer changed"
        runtimeContext.putVariable("token", "new")
        analyzer.initUrl()
        analyzer.getStrResponse()

        capturedPlan!!.url shouldBe "https://example.com/api/new"
        capturedPlan!!.headers.containsKey("Authorization") shouldBe false
    }

    test("AnalyzeUrl getStrResponse 参数应覆盖默认 webJs 与 useWebView") {
        var capturedPlan: LegadoRequestPlan? = null
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/page,{\"webView\":true,\"webJs\":\"document.title\"}",
            baseUrl = "https://example.com",
            httpExecutor = object : LegadoHttpExecutor {
                override suspend fun execute(plan: LegadoRequestPlan): LegadoHttpResponse {
                    capturedPlan = plan
                    return LegadoHttpResponse(
                        url = plan.url,
                        body = "ok",
                        code = 200,
                    )
                }
            },
        )

        analyzer.getStrResponse(jsStr = "document.body.innerHTML", useWebView = false)

        capturedPlan!!.useWebView shouldBe false
        capturedPlan!!.webJs shouldBe "document.body.innerHTML"
    }

    test("AnalyzeUrl getStrResponse 参数应把 sourceRegex 写入执行计划") {
        var capturedPlan: LegadoRequestPlan? = null
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/page,{\"webView\":true}",
            baseUrl = "https://example.com",
            httpExecutor = object : LegadoHttpExecutor {
                override suspend fun execute(plan: LegadoRequestPlan): LegadoHttpResponse {
                    capturedPlan = plan
                    return LegadoHttpResponse(
                        url = plan.url,
                        body = "https://cdn.example.com/audio.mp3",
                        code = 200,
                    )
                }
            },
        )

        analyzer.getStrResponse(sourceRegex = ".*audio\\.mp3", useWebView = true)

        capturedPlan!!.useWebView shouldBe true
        capturedPlan!!.sourceRegex shouldBe ".*audio\\.mp3"
    }

    test("AnalyzeUrl getStrResponse 参数应把 overrideUrlRegex 写入执行计划") {
        var capturedPlan: LegadoRequestPlan? = null
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/page,{\"webView\":true}",
            baseUrl = "https://example.com",
            httpExecutor = object : LegadoHttpExecutor {
                override suspend fun execute(plan: LegadoRequestPlan): LegadoHttpResponse {
                    capturedPlan = plan
                    return LegadoHttpResponse(
                        url = plan.url,
                        body = "https://example.com/callback?token=1",
                        code = 200,
                    )
                }
            },
        )

        analyzer.getStrResponseWithOverrideUrlRegex(
            overrideUrlRegex = ".*token=.*",
            useWebView = true,
        )

        capturedPlan!!.useWebView shouldBe true
        capturedPlan!!.overrideUrlRegex shouldBe ".*token=.*"
    }

    test("AnalyzeUrl getStrResponseAwait 应兼容 MD3 的 isTest 错误码语义") {
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/test-fail",
            baseUrl = "https://example.com",
            httpExecutor = object : LegadoHttpExecutor {
                override suspend fun execute(plan: LegadoRequestPlan): LegadoHttpResponse {
                    throw java.net.UnknownHostException("dns-fail")
                }
            },
        )

        val response = kotlinx.coroutines.runBlocking {
            analyzer.getStrResponseAwait(isTest = true)
        }

        response.body() shouldBe "dns-fail"
        response.callTime() shouldBe -3
    }

    test("AnalyzeUrl headerMap 属性应允许 Kotlin 侧按 MD3 方式直接读取") {
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/file,{\"headers\":{\"User-Agent\":\"UA-K\",\"Referer\":\"https://example.com/\"}}",
            baseUrl = "https://example.com",
        )

        analyzer.headerMap shouldBe linkedMapOf(
            "User-Agent" to "UA-K",
            "Referer" to "https://example.com/",
        )
    }

    test("AnalyzeUrl getStrResponse 应按 MD3 在请求后应用 bodyJs") {
        val runtimeContext = TestLegadoRuleRuntimeContext { script, context ->
            if (script == "result + '-patched'") {
                context.getVariable("result").toString() + "-patched"
            } else {
                context.getVariable("result")
            }
        }
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/api,{\"bodyJs\":\"result + '-patched'\"}",
            baseUrl = "https://example.com",
            runtimeContext = runtimeContext,
            httpExecutor = object : LegadoHttpExecutor {
                override suspend fun execute(plan: LegadoRequestPlan): LegadoHttpResponse {
                    return LegadoHttpResponse(
                        url = plan.url,
                        body = "raw-body",
                        code = 200,
                    )
                }
            },
        )

        analyzer.getStrResponse().body() shouldBe "raw-body-patched"
    }

    test("AnalyzeUrl 应暴露 MD3 风格错误响应") {
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/fail",
            baseUrl = "https://example.com",
        )
        val error = IllegalStateException("boom")

        val response = analyzer.getErrResponse(error)
        val strResponse = analyzer.getErrStrResponse(error)

        response.code shouldBe 500
        response.message shouldBe "boom"
        response.body?.string().orEmpty() shouldContain "IllegalStateException: boom"
        strResponse.code() shouldBe 500
        strResponse.body() shouldContain "IllegalStateException: boom"
    }

    test("AnalyzeUrl getMediaItem 应提供接近 MD3 的 uri 承载语义") {
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/audio.mp3,{\"headers\":{\"User-Agent\":\"UA-A\",\"Referer\":\"https://example.com/book/1\"}}",
            baseUrl = "https://example.com",
        )

        val mediaItem = analyzer.getMediaItem()
        val mediaUri = mediaItem.mediaId

        mediaItem.shouldBeInstanceOf<MediaItem>()
        mediaUri shouldContain "https://example.com/audio.mp3"
        mediaUri shouldContain "User-Agent"
        mediaUri shouldContain "Referer"
    }

    test("AnalyzeUrl getByteArray 与 getInputStream 应走二进制语义而不是 UTF-8 字符串") {
        val binary = byteArrayOf(0x00, 0x7f, 0x10, 0x2a, 0xff.toByte())
        val analyzer = AnalyzeUrl(
            ruleUrl = "https://example.com/image,{\"type\":\"image\"}",
            baseUrl = "https://example.com",
            httpExecutor = object : LegadoHttpExecutor {
                override suspend fun execute(plan: LegadoRequestPlan): LegadoHttpResponse {
                    plan.type shouldBe "image"
                    return LegadoHttpResponse(
                        url = plan.url,
                        body = "007f102aff",
                        code = 200,
                        headers = mapOf("Content-Type" to "image/png"),
                    )
                }
            },
        )

        analyzer.getByteArray().toList() shouldBe binary.toList()
        analyzer.getInputStream().readBytes().toList() shouldBe binary.toList()
        val response = analyzer.getResponse()
        response.body?.bytes()?.toList() shouldBe binary.toList()
        response.header("Content-Type") shouldBe "image/png"
    }

    test("AnalyzeUrl data URI 应支持 getByteArray getInputStream 与 getResponse") {
        val analyzer = AnalyzeUrl(
            ruleUrl = "data:text/plain;base64,SGVsbG8=",
            baseUrl = "https://example.com",
        )

        analyzer.getByteArray().decodeToString() shouldBe "Hello"
        analyzer.getInputStream().readBytes().decodeToString() shouldBe "Hello"
        analyzer.getResponse().body?.bytes()?.decodeToString() shouldBe "Hello"
    }
})

private class LegacyUrlSource(
    private val tag: String,
    val bookSourceUrl: String,
) {
    private val variables = linkedMapOf<String, String>()

    fun put(key: String, value: String) {
        variables[key] = value
    }

    fun get(key: String): String = variables[key].orEmpty()

    fun getTag(): String = tag
}
