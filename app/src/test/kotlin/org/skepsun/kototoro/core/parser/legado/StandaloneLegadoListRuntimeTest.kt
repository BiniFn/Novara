package org.skepsun.kototoro.core.parser.legado

import android.content.Context
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.skepsun.kototoro.core.javascript.BookInfo
import org.skepsun.kototoro.core.javascript.ChapterInfo
import org.skepsun.kototoro.core.javascript.RhinoJavaScriptEngine
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.network.cookies.MutableCookieJar
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.core.network.jsonsource.PersistentCookieJar
import org.skepsun.kototoro.core.network.jsonsource.UserAgentManager
import org.skepsun.kototoro.core.network.webview.WebViewExecutor
import org.skepsun.kototoro.core.parser.legado.bridge.KototoroLegadoHttpExecutor
import org.skepsun.kototoro.core.parser.legado.bridge.StandaloneLegadoRuleRuntimeContext
import org.skepsun.kototoro.core.parser.legado.runtime.StandaloneLegadoListRuntime
import org.skepsun.kototoro.parsers.model.ContentSource
import java.net.CookieManager
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

class StandaloneLegadoListRuntimeTest : FunSpec({

    lateinit var engine: RhinoJavaScriptEngine
    lateinit var jsHttpClient: LegadoHttpClient
    lateinit var cookieJar: PersistentCookieJar
    lateinit var androidContext: Context
    lateinit var mockServer: MockWebServer

    beforeTest {
        jsHttpClient = mockk(relaxed = true)
        cookieJar = mockk(relaxed = true)
        androidContext = mockk(relaxed = true)
        mockServer = MockWebServer()
        mockServer.start()
        engine = RhinoJavaScriptEngine(
            httpClient = jsHttpClient,
            cookieManager = CookieManager(),
            cookieJar = cookieJar,
            androidContext = androidContext,
        )
    }

    afterTest {
        engine.dispose()
        mockServer.shutdown()
    }

    test("无 sandbox 最小列表链路可完成 URL 构建与列表解析") {
        val source = LegadoBookSource(
            bookSourceName = "Standalone Test Source",
            bookSourceUrl = "https://example.com",
        )
        val runtime = StandaloneLegadoListRuntime(
            jsEngine = engine,
            source = source,
        )

        val prepared = runtime.prepareRequest(
            ruleUrl = "@js:'https://example.com/search?kw=' + key + '&page=' + page",
            key = "kotlin",
            page = 2,
            baseUrl = source.bookSourceUrl,
        )

        prepared.requestPlan.url shouldBe "https://example.com/search?kw=kotlin&page=2"

        val jsonContent = """
            {
              "items": [
                {"title": "Alpha"},
                {"title": "Beta"}
              ]
            }
        """.trimIndent()

        val items = runtime.parseStringList(
            content = jsonContent,
            rule = "$.items[*].title",
            runtimeContext = prepared.runtimeContext,
            baseUrl = prepared.requestPlan.url,
            isUrl = false,
        )

        items shouldContainExactly listOf("Alpha", "Beta")
    }

    test("无 sandbox runtime 可在 AnalyzeRule 中读取写入变量") {
        val source = LegadoBookSource(
            bookSourceName = "Standalone Variable Source",
            bookSourceUrl = "https://example.com",
        )
        val runtime = StandaloneLegadoListRuntime(
            jsEngine = engine,
            source = source,
        )

        val prepared = runtime.prepareRequest(
            ruleUrl = "https://example.com/list",
            baseUrl = source.bookSourceUrl,
        )

        prepared.runtimeContext.putVariable("category", "ranking")

        val value = runtime.parseString(
            content = mapOf("id" to "42"),
            rule = "@get:{category}-{{$.id}}",
            runtimeContext = prepared.runtimeContext,
            baseUrl = prepared.requestPlan.url,
        )

        value shouldBe "ranking-42"
    }

    test("无 sandbox runtime 可注入 book 和 chapter 上下文供 JS 读取") {
        val source = LegadoBookSource(
            bookSourceName = "Standalone Context Source",
            bookSourceUrl = "https://example.com",
        )
        val runtime = StandaloneLegadoListRuntime(
            jsEngine = engine,
            source = source,
        )

        val prepared = runtime.prepareRequest(
            ruleUrl = "https://example.com/content",
            baseUrl = source.bookSourceUrl,
        )

        runtime.setBook(
            runtimeContext = prepared.runtimeContext,
            book = BookInfo(
                bookUrl = "https://example.com/book/7",
                name = "Book Seven",
                author = "Author A",
            ),
        )
        runtime.setChapter(
            runtimeContext = prepared.runtimeContext,
            chapter = ChapterInfo(
                chapterUrl = "https://example.com/book/7/chapter/3",
                name = "Chapter Three",
                index = 3,
            ),
        )

        val value = runtime.parseString(
            content = "ignored",
            rule = "{{ book.name + ' / ' + chapter.name + ' / ' + chapter.index }}",
            runtimeContext = prepared.runtimeContext,
            baseUrl = prepared.requestPlan.url,
        )

        value shouldBe "Book Seven / Chapter Three / 3"
    }

    test("无 sandbox runtime 可执行副作用脚本并回写 result") {
        val source = LegadoBookSource(
            bookSourceName = "Standalone SideEffect Source",
            bookSourceUrl = "https://example.com",
        )
        val runtimeContext = StandaloneLegadoRuleRuntimeContext(
            jsEngine = engine,
            source = source,
        )

        val executeResult = runtimeContext.executeJs(
            script = "result = result + '-formatted'; result",
            result = "chapter-title",
            baseUrl = source.bookSourceUrl,
        )

        executeResult.toString() shouldBe "chapter-title-formatted"
        runtimeContext.getVariableAny("result").toString() shouldBe "chapter-title-formatted"
        runtimeContext.getVariableAny("src").toString() shouldBe "chapter-title"
    }

    test("无 sandbox 最小 runtime 可跑通真实 HTTP 列表链路") {
        runTest {
            val html = """
                <html>
                <body>
                    <div class="item"><a class="title" href="/book/1">Book A</a></div>
                    <div class="item"><a class="title" href="/book/2">Book B</a></div>
                </body>
                </html>
            """.trimIndent()
            mockServer.enqueue(MockResponse().setBody(html))

            val source = LegadoBookSource(
                bookSourceName = "Standalone Http Source",
                bookSourceUrl = mockServer.url("/").toString(),
                searchUrl = "@js:baseUrl + 'search?kw=' + key + '&page=' + page",
            )
            val runtime = StandaloneLegadoListRuntime(
                jsEngine = engine,
                source = source,
            )
            val prepared = runtime.prepareRequest(
                ruleUrl = source.searchUrl!!,
                key = "android",
                page = 3,
                baseUrl = source.bookSourceUrl,
            )

            val httpClient = LegadoHttpClient(
                okHttpClient = OkHttpClient.Builder().build(),
                cookieJar = mockk<MutableCookieJar>(relaxed = true),
                persistentCookieJar = mockk(relaxed = true),
                userAgentManager = UserAgentManager(),
                webViewExecutor = mockk(relaxed = true),
            )
            val httpExecutor = KototoroLegadoHttpExecutor(
                source = mockk<ContentSource>(relaxed = true),
                config = source,
                httpClient = httpClient,
                rateLimiter = ConcurrentRateLimiter(source.bookSourceUrl, source.concurrentRate),
                configHeadersProvider = { emptyMap() },
                loginHeadersProvider = { emptyMap() },
                sourceUserAgentProvider = { "JUnit-Agent" },
            )

            val response = httpExecutor.execute(prepared.requestPlan)
            prepared.requestPlan.url shouldBe "${mockServer.url("/")}search?kw=android&page=3"

            val titles = runtime.parseStringList(
                content = response.body,
                rule = "class.item@tag.a.0@text",
                runtimeContext = prepared.runtimeContext,
                baseUrl = response.url,
                isUrl = false,
            )

            titles shouldContainExactly listOf("Book A", "Book B")
        }
    }

    test("HTTP 执行器的 retry=0 应保持 MD3 语义仅尝试一次") {
        runTest {
            mockServer.enqueue(MockResponse().setResponseCode(500).setBody("fail"))
            mockServer.enqueue(MockResponse().setBody("ok"))

            val source = LegadoBookSource(
                bookSourceName = "Retry Source",
                bookSourceUrl = mockServer.url("/").toString(),
            )
            val httpClient = LegadoHttpClient(
                okHttpClient = OkHttpClient.Builder().build(),
                cookieJar = mockk<MutableCookieJar>(relaxed = true),
                persistentCookieJar = mockk(relaxed = true),
                userAgentManager = UserAgentManager(),
                webViewExecutor = mockk(relaxed = true),
            )
            val httpExecutor = KototoroLegadoHttpExecutor(
                source = mockk<ContentSource>(relaxed = true),
                config = source,
                httpClient = httpClient,
                rateLimiter = ConcurrentRateLimiter(source.bookSourceUrl, source.concurrentRate),
                configHeadersProvider = { emptyMap() },
                loginHeadersProvider = { emptyMap() },
                sourceUserAgentProvider = { "JUnit-Agent" },
            )

            val response = httpExecutor.execute(
                org.skepsun.kototoro.core.parser.legado.runtime.LegadoRequestPlan(
                    url = mockServer.url("/retry").toString(),
                    retry = 0,
                ),
            )

            response.code shouldBe 500
            response.body shouldBe "fail"
            mockServer.requestCount shouldBe 1
        }
    }

    test("HTTP 执行器在 type 不为空时应按 MD3 返回十六进制响应体") {
        runTest {
            mockServer.enqueue(
                MockResponse().setBody(
                    okio.Buffer().write(byteArrayOf(0x00, 0x7f, 0x80.toByte(), 0xff.toByte())),
                ),
            )

            val source = LegadoBookSource(
                bookSourceName = "Binary Source",
                bookSourceUrl = mockServer.url("/").toString(),
            )
            val httpClient = LegadoHttpClient(
                okHttpClient = OkHttpClient.Builder().build(),
                cookieJar = mockk<MutableCookieJar>(relaxed = true),
                persistentCookieJar = mockk(relaxed = true),
                userAgentManager = UserAgentManager(),
                webViewExecutor = mockk(relaxed = true),
            )
            val httpExecutor = KototoroLegadoHttpExecutor(
                source = mockk<ContentSource>(relaxed = true),
                config = source,
                httpClient = httpClient,
                rateLimiter = ConcurrentRateLimiter(source.bookSourceUrl, source.concurrentRate),
                configHeadersProvider = { emptyMap() },
                loginHeadersProvider = { emptyMap() },
                sourceUserAgentProvider = { "JUnit-Agent" },
            )

            val response = httpExecutor.execute(
                org.skepsun.kototoro.core.parser.legado.runtime.LegadoRequestPlan(
                    url = mockServer.url("/binary").toString(),
                    type = "image/jpeg",
                    bodyJs = "result + '-ignored'",
                    useWebView = true,
                ),
            )

            response.body shouldBe "007f80ff"
        }
    }

    test("HTTP 执行器在 type 不为空且响应为空时应返回空十六进制串") {
        runTest {
            mockServer.enqueue(MockResponse().setBody(okio.Buffer()))

            val source = LegadoBookSource(
                bookSourceName = "Binary Empty Source",
                bookSourceUrl = mockServer.url("/").toString(),
            )
            val httpClient = LegadoHttpClient(
                okHttpClient = OkHttpClient.Builder().build(),
                cookieJar = mockk<MutableCookieJar>(relaxed = true),
                persistentCookieJar = mockk(relaxed = true),
                userAgentManager = UserAgentManager(),
                webViewExecutor = mockk(relaxed = true),
            )
            val httpExecutor = KototoroLegadoHttpExecutor(
                source = mockk<ContentSource>(relaxed = true),
                config = source,
                httpClient = httpClient,
                rateLimiter = ConcurrentRateLimiter(source.bookSourceUrl, source.concurrentRate),
                configHeadersProvider = { emptyMap() },
                loginHeadersProvider = { emptyMap() },
                sourceUserAgentProvider = { "JUnit-Agent" },
            )

            val response = httpExecutor.execute(
                org.skepsun.kototoro.core.parser.legado.runtime.LegadoRequestPlan(
                    url = mockServer.url("/binary-empty").toString(),
                    type = "image/png",
                ),
            )

            response.body.shouldBeEmpty()
        }
    }

    test("HTTP 执行器应支持 MD3 的 data URI 文本响应语义") {
        runTest {
            val source = LegadoBookSource(
                bookSourceName = "Data Uri Source",
                bookSourceUrl = "https://example.com",
            )
            val httpClient = LegadoHttpClient(
                okHttpClient = OkHttpClient.Builder().build(),
                cookieJar = mockk<MutableCookieJar>(relaxed = true),
                persistentCookieJar = mockk(relaxed = true),
                userAgentManager = UserAgentManager(),
                webViewExecutor = mockk(relaxed = true),
            )
            val httpExecutor = KototoroLegadoHttpExecutor(
                source = mockk<ContentSource>(relaxed = true),
                config = source,
                httpClient = httpClient,
                rateLimiter = ConcurrentRateLimiter(source.bookSourceUrl, source.concurrentRate),
                configHeadersProvider = { emptyMap() },
                loginHeadersProvider = { emptyMap() },
                sourceUserAgentProvider = { "JUnit-Agent" },
            )

            val response = httpExecutor.execute(
                org.skepsun.kototoro.core.parser.legado.runtime.LegadoRequestPlan(
                    url = "data:text/plain;base64,SGVsbG8=",
                ),
            )

            response.code shouldBe 200
            response.body shouldBe "Hello"
        }
    }

    test("HTTP 执行器应支持 MD3 的 data URI 十六进制响应语义") {
        runTest {
            val source = LegadoBookSource(
                bookSourceName = "Data Uri Binary Source",
                bookSourceUrl = "https://example.com",
            )
            val httpClient = LegadoHttpClient(
                okHttpClient = OkHttpClient.Builder().build(),
                cookieJar = mockk<MutableCookieJar>(relaxed = true),
                persistentCookieJar = mockk(relaxed = true),
                userAgentManager = UserAgentManager(),
                webViewExecutor = mockk(relaxed = true),
            )
            val httpExecutor = KototoroLegadoHttpExecutor(
                source = mockk<ContentSource>(relaxed = true),
                config = source,
                httpClient = httpClient,
                rateLimiter = ConcurrentRateLimiter(source.bookSourceUrl, source.concurrentRate),
                configHeadersProvider = { emptyMap() },
                loginHeadersProvider = { emptyMap() },
                sourceUserAgentProvider = { "JUnit-Agent" },
            )

            val response = httpExecutor.execute(
                org.skepsun.kototoro.core.parser.legado.runtime.LegadoRequestPlan(
                    url = "data:application/octet-stream;base64,AAEC",
                    type = "image/png",
                ),
            )

            response.code shouldBe 200
            response.body shouldBe "000102"
        }
    }

    test("HTTP 执行器应把 readTimeout 传给底层请求并按 MD3 语义超时") {
        runTest {
            mockServer.enqueue(
                MockResponse()
                    .setHeadersDelay(250, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .setBody("late"),
            )

            val source = LegadoBookSource(
                bookSourceName = "Timeout Source",
                bookSourceUrl = mockServer.url("/").toString(),
            )
            val httpClient = LegadoHttpClient(
                okHttpClient = OkHttpClient.Builder().build(),
                cookieJar = mockk<MutableCookieJar>(relaxed = true),
                persistentCookieJar = mockk(relaxed = true),
                userAgentManager = UserAgentManager(),
                webViewExecutor = mockk(relaxed = true),
            )
            val httpExecutor = KototoroLegadoHttpExecutor(
                source = mockk<ContentSource>(relaxed = true),
                config = source,
                httpClient = httpClient,
                rateLimiter = ConcurrentRateLimiter(source.bookSourceUrl, source.concurrentRate),
                configHeadersProvider = { emptyMap() },
                loginHeadersProvider = { emptyMap() },
                sourceUserAgentProvider = { "JUnit-Agent" },
            )

            shouldThrow<SocketTimeoutException> {
                httpExecutor.execute(
                    org.skepsun.kototoro.core.parser.legado.runtime.LegadoRequestPlan(
                        url = mockServer.url("/timeout-read").toString(),
                        readTimeoutMs = 100L,
                    ),
                )
            }
        }
    }

    test("HTTP 执行器应允许显式 callTimeout 覆盖 readTimeout 推导值") {
        runTest {
            mockServer.enqueue(
                MockResponse()
                    .setHeadersDelay(250, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .setBody("late"),
            )

            val source = LegadoBookSource(
                bookSourceName = "Call Timeout Source",
                bookSourceUrl = mockServer.url("/").toString(),
            )
            val httpClient = LegadoHttpClient(
                okHttpClient = OkHttpClient.Builder().build(),
                cookieJar = mockk<MutableCookieJar>(relaxed = true),
                persistentCookieJar = mockk(relaxed = true),
                userAgentManager = UserAgentManager(),
                webViewExecutor = mockk(relaxed = true),
            )
            val httpExecutor = KototoroLegadoHttpExecutor(
                source = mockk<ContentSource>(relaxed = true),
                config = source,
                httpClient = httpClient,
                rateLimiter = ConcurrentRateLimiter(source.bookSourceUrl, source.concurrentRate),
                configHeadersProvider = { emptyMap() },
                loginHeadersProvider = { emptyMap() },
                sourceUserAgentProvider = { "JUnit-Agent" },
            )

            shouldThrow<InterruptedIOException> {
                httpExecutor.execute(
                    org.skepsun.kototoro.core.parser.legado.runtime.LegadoRequestPlan(
                        url = mockServer.url("/timeout-call").toString(),
                        readTimeoutMs = 5_000L,
                        callTimeoutMs = 100L,
                    ),
                )
            }
        }
    }

    test("HTTP 执行器在 useWebView+sourceRegex 时应走 WebView 嗅探语义") {
        runTest {
            val source = LegadoBookSource(
                bookSourceName = "WebView Sniff Source",
                bookSourceUrl = "https://example.com",
            )
            val webViewExecutor = mockk<WebViewExecutor>()
            coEvery {
                webViewExecutor.sniff(
                    url = "https://example.com/page",
                    headers = any(),
                    delayMs = any(),
                    timeoutMs = any(),
                    sourceRegex = ".*audio\\.mp3",
                    overrideUrlRegex = null,
                    javaScript = "document.querySelector('button')?.click()",
                    blockImages = true,
                )
            } returns WebViewExecutor.WebViewSniffResult(
                url = "https://example.com/page",
                body = "https://cdn.example.com/audio.mp3",
                code = 200,
            )

            val httpClient = LegadoHttpClient(
                okHttpClient = OkHttpClient.Builder().build(),
                cookieJar = mockk<MutableCookieJar>(relaxed = true),
                persistentCookieJar = mockk(relaxed = true),
                userAgentManager = UserAgentManager(),
                webViewExecutor = webViewExecutor,
            )
            val httpExecutor = KototoroLegadoHttpExecutor(
                source = mockk<ContentSource>(relaxed = true),
                config = source,
                httpClient = httpClient,
                rateLimiter = ConcurrentRateLimiter(source.bookSourceUrl, source.concurrentRate),
                configHeadersProvider = { emptyMap() },
                loginHeadersProvider = { emptyMap() },
                sourceUserAgentProvider = { "JUnit-Agent" },
            )

            val response = httpExecutor.execute(
                org.skepsun.kototoro.core.parser.legado.runtime.LegadoRequestPlan(
                    url = "https://example.com/page",
                    useWebView = true,
                    webJs = "document.querySelector('button')?.click()",
                    sourceRegex = ".*audio\\.mp3",
                ),
            )

            response.body shouldBe "https://cdn.example.com/audio.mp3"
            coVerify(exactly = 1) {
                webViewExecutor.sniff(
                    url = "https://example.com/page",
                    headers = any(),
                    delayMs = any(),
                    timeoutMs = any(),
                    sourceRegex = ".*audio\\.mp3",
                    overrideUrlRegex = null,
                    javaScript = "document.querySelector('button')?.click()",
                    blockImages = true,
                )
            }
        }
    }

    test("HTTP 执行器在 useWebView+overrideUrlRegex 时应走 WebView 跳转拦截语义") {
        runTest {
            val source = LegadoBookSource(
                bookSourceName = "WebView Override Source",
                bookSourceUrl = "https://example.com",
            )
            val webViewExecutor = mockk<WebViewExecutor>()
            coEvery {
                webViewExecutor.sniff(
                    url = "https://example.com/page",
                    headers = any(),
                    delayMs = any(),
                    timeoutMs = any(),
                    sourceRegex = null,
                    overrideUrlRegex = ".*token=.*",
                    javaScript = "document.querySelector('a')?.click()",
                    blockImages = true,
                )
            } returns WebViewExecutor.WebViewSniffResult(
                url = "https://example.com/page",
                body = "https://example.com/callback?token=1",
                code = 200,
            )

            val httpClient = LegadoHttpClient(
                okHttpClient = OkHttpClient.Builder().build(),
                cookieJar = mockk<MutableCookieJar>(relaxed = true),
                persistentCookieJar = mockk(relaxed = true),
                userAgentManager = UserAgentManager(),
                webViewExecutor = webViewExecutor,
            )
            val httpExecutor = KototoroLegadoHttpExecutor(
                source = mockk<ContentSource>(relaxed = true),
                config = source,
                httpClient = httpClient,
                rateLimiter = ConcurrentRateLimiter(source.bookSourceUrl, source.concurrentRate),
                configHeadersProvider = { emptyMap() },
                loginHeadersProvider = { emptyMap() },
                sourceUserAgentProvider = { "JUnit-Agent" },
            )

            val response = httpExecutor.execute(
                org.skepsun.kototoro.core.parser.legado.runtime.LegadoRequestPlan(
                    url = "https://example.com/page",
                    useWebView = true,
                    webJs = "document.querySelector('a')?.click()",
                    overrideUrlRegex = ".*token=.*",
                ),
            )

            response.body shouldBe "https://example.com/callback?token=1"
            coVerify(exactly = 1) {
                webViewExecutor.sniff(
                    url = "https://example.com/page",
                    headers = any(),
                    delayMs = any(),
                    timeoutMs = any(),
                    sourceRegex = null,
                    overrideUrlRegex = ".*token=.*",
                    javaScript = "document.querySelector('a')?.click()",
                    blockImages = true,
                )
            }
        }
    }
})
