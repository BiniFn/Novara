package org.skepsun.kototoro.core.parser.legado

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.skepsun.kototoro.core.model.jsonsource.BookInfoRule
import org.skepsun.kototoro.core.model.jsonsource.ContentRule
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.model.jsonsource.SearchRule
import org.skepsun.kototoro.core.parser.legado.book.BookContent
import org.skepsun.kototoro.core.parser.legado.book.BookInfo
import org.skepsun.kototoro.parsers.exception.ParseException
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentType

class BookInfoAndContentAlignmentTest : FunSpec({

    fun testContent(source: ContentSource = mockk(relaxed = true)): Content {
        return Content(
            id = 1L,
            title = "Original Title",
            altTitles = emptySet(),
            url = "https://example.com/book/1",
            publicUrl = "https://example.com/book/1",
            rating = -1f,
            contentRating = null,
            coverUrl = null,
            tags = emptySet(),
            state = null,
            authors = emptySet(),
            largeCoverUrl = null,
            description = null,
            chapters = null,
            source = source,
        )
    }

    test("BookInfo 声明 downloadUrls 规则时应按 web file 分支处理") {
        val source = mockk<ContentSource> {
            every { name } returns "Test Source"
            every { locale } returns "zh"
            every { contentType } returns org.skepsun.kototoro.parsers.model.ContentType.OTHER
        }
        val config = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "https://example.com",
            ruleSearch = SearchRule(),
            ruleBookInfo = BookInfoRule(
                downloadUrls = "a@href",
            ),
        )
        val html = """
            <html><body><a href="https://cdn.example.com/book.zip">下载</a></body></html>
        """.trimIndent()

        val result = BookInfo.parseWithRuntimeContext(
            manga = testContent(source),
            content = html,
            baseUrl = "https://example.com/book/1",
            config = config,
            runtimeContext = TestLegadoRuleRuntimeContext(),
        )

        result.isWebFile shouldBe true
        result.tocUrl shouldBe null
    }

    test("BookInfo 声明 downloadUrls 规则但未解析出链接时应与 MD3 一样报错") {
        val config = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "https://example.com",
            ruleSearch = SearchRule(),
            ruleBookInfo = BookInfoRule(
                downloadUrls = "a@href",
            ),
        )

        shouldThrow<ParseException> {
            BookInfo.parseWithRuntimeContext(
                manga = testContent(),
                content = "<html><body><div>empty</div></body></html>",
                baseUrl = "https://example.com/book/1",
                config = config,
                runtimeContext = TestLegadoRuleRuntimeContext(),
            )
        }.shortMessage shouldBe "下载链接为空"
    }

    test("BookContent finalizeNovelChapter 应复用 AnalyzeRule 的整段 replaceFirst 语义") {
        val runtimeContext = TestLegadoRuleRuntimeContext().apply {
            putVariable("chapterName", "第1章")
        }
        val config = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "https://example.com",
            ruleSearch = SearchRule(),
            ruleContent = ContentRule(
                replaceRegex = "##foo(\\d+)##bar##1",
            ),
        )

        val result = BookContent.finalizeNovelChapter(
            pageContents = listOf("abc foo12 xyz"),
            baseUrl = "https://example.com/chapter/1",
            config = config,
            runtimeContext = runtimeContext,
        )

        result shouldBe "bar"
    }

    test("BookContent parseNovelPage 应做基础 HTML 净化与实体反转义") {
        val runtimeContext = TestLegadoRuleRuntimeContext()
        val config = LegadoBookSource(
            bookSourceName = "Test Source",
            bookSourceUrl = "https://example.com",
            ruleSearch = SearchRule(),
            ruleContent = ContentRule(
                content = "#content@html",
            ),
        )
        val source = mockk<ContentSource> {
            every { name } returns "Test Source"
            every { locale } returns "zh"
            every { contentType } returns ContentType.NOVEL
        }
        val html = """
            <html><body>
            <div id="content"><script>alert(1)</script>第一行&lt;br&gt;第二行<br>第三行&nbsp;</div>
            </body></html>
        """.trimIndent()

        val result = BookContent.parseNovelPageWithRuntimeContext(
            content = html,
            baseUrl = "https://example.com/chapter/1",
            source = source,
            config = config,
            runtimeContext = runtimeContext,
        )

        result.content shouldBe "第一行<br>第二行\n第三行"
    }
})
