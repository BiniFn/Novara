package org.skepsun.kototoro.core.parser.legado

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.core.jsonsource.JsonContentSource
import org.skepsun.kototoro.core.javascript.JavaScriptEngine
import org.skepsun.kototoro.core.javascript.JavaScriptContext
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentListFilter
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.core.network.cookies.MutableCookieJar
import org.skepsun.kototoro.core.network.jsonsource.UserAgentManager
import io.mockk.mockk
import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.model.jsonsource.TocRule
import org.skepsun.kototoro.core.parser.legado.book.BookChapterList

/**
 * Integration test for LegadoRepository
 */
class LegadoRepositoryIntegrationTest {
	
	private lateinit var mockServer: MockWebServer
	private lateinit var httpClient: LegadoHttpClient
	private lateinit var mockJsEngine: JavaScriptEngine
    private lateinit var prefs: SharedPreferences
	
	@BeforeEach
	fun setup() {
		mockServer = MockWebServer()
		mockServer.start()
		
		val okHttpClient = OkHttpClient.Builder().build()
		httpClient = LegadoHttpClient(
			okHttpClient = okHttpClient,
			cookieJar = mockk(relaxed = true),
			persistentCookieJar = mockk(relaxed = true),
			userAgentManager = UserAgentManager(),
			webViewExecutor = mockk(relaxed = true),
		)
		
		mockJsEngine = object : JavaScriptEngine {
			override fun execute(script: String, context: JavaScriptContext): Any? {
				return "JavaScript Result"
			}
			
			override fun evaluate(expression: String, context: JavaScriptContext): Any? {
				return execute(expression, context)
			}
			
			override fun registerGlobalObject(name: String, obj: Any) {}
			
			override fun dispose() {}
		}
        prefs = mockk(relaxed = true)
	}
	
	@AfterEach
	fun tearDown() {
		mockServer.shutdown()
	}
	
	@Test
	fun `test getList with CSS selectors`() = runTest {
		val html = """
			<html>
			<body>
				<div class="item">
					<h3><a href="/book/1">Test Book 1</a></h3>
					<p><a>Author 1</a></p>
					<img src="/cover1.jpg"/>
				</div>
				<div class="item">
					<h3><a href="/book/2">Test Book 2</a></h3>
					<p><a>Author 2</a></p>
					<img src="/cover2.jpg"/>
				</div>
			</body>
			</html>
		""".trimIndent()
		
		mockServer.enqueue(MockResponse().setBody(html))
		
		val config = """
			{
				"bookSourceName":"Test Source",
				"bookSourceUrl":"${mockServer.url("/")}",
				"searchUrl":"/search/?searchkey={{key}}",
				"ruleSearch":{
					"bookList":"class.item",
					"name":"tag.h3.0@tag.a.0@text",
					"author":"tag.p.0@tag.a.0@text",
					"coverUrl":"tag.img.0@src",
					"bookUrl":"tag.a.0@href"
				}
			}
		""".trimIndent()
		
		val entity = JsonSourceEntity(
			id = "JSON_TEST_1",
			name = "Test Source",
			type = JsonSourceType.LEGADO,
			config = config,
			enabled = true,
			createdAt = System.currentTimeMillis(),
			updatedAt = System.currentTimeMillis()
		)
		
		val source = JsonContentSource(entity)
		val repository = LegadoRepository(source, httpClient, mockJsEngine, legadoPrefs = prefs)
		
		val results = repository.getList(0, null, ContentListFilter(query = "test"))
		
		assertEquals(2, results.size)
		assertEquals("Test Book 1", results[0].title)
		assertEquals("Test Book 2", results[1].title)
		assertTrue(results[0].url.contains("/book/1"))
		assertTrue(results[1].url.contains("/book/2"))
	}
	
	@Test
	fun `test getDetails with XPath selectors`() = runTest {
		val html = """
			<html>
			<head>
				<meta property="og:novel:book_name" content="Test Novel"/>
				<meta property="og:novel:author" content="Test Author"/>
				<meta property="og:image" content="/cover.jpg"/>
				<meta property="og:description" content="Test description"/>
				<meta property="og:novel:category" content="Fantasy"/>
			</head>
			<body>
				<div id="content_1">
					<a href="/chapter/1">Chapter 1</a>
					<a href="/chapter/2">Chapter 2</a>
				</div>
			</body>
			</html>
		""".trimIndent()
		
		mockServer.enqueue(MockResponse().setBody(html))
		mockServer.enqueue(MockResponse().setBody(html))
		
		val config = """
			{
				"bookSourceName":"Test Source",
				"bookSourceUrl":"${mockServer.url("/")}",
				"ruleBookInfo":{
					"name":"//meta[@property='og:novel:book_name']/@content",
					"author":"//meta[@property='og:novel:author']/@content",
					"coverUrl":"//meta[@property='og:image']/@content",
					"intro":"//meta[@property='og:description']/@content",
					"kind":"//meta[@property='og:novel:category']/@content"
				},
				"ruleToc":{
					"chapterList":"id.content_1.0@tag.a",
					"chapterName":"text",
					"chapterUrl":"href"
				}
			}
		""".trimIndent()
		
		val entity = JsonSourceEntity(
			id = "JSON_TEST_2",
			name = "Test Source",
			type = JsonSourceType.LEGADO,
			config = config,
			enabled = true,
			createdAt = System.currentTimeMillis(),
			updatedAt = System.currentTimeMillis()
		)
		
		val source = JsonContentSource(entity)
		val repository = LegadoRepository(source, httpClient, mockJsEngine, legadoPrefs = prefs)
		
		val testContent = Content(
			id = 1L,
			title = "Original Title",
			altTitles = emptySet(),
			url = mockServer.url("/book/1").toString(),
			publicUrl = mockServer.url("/book/1").toString(),
			rating = -1f,
			contentRating = null,
			coverUrl = "",
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			largeCoverUrl = null,
			description = null,
			chapters = null,
			source = source
		)
		
		val result = repository.getDetails(testContent)
		
		assertNotNull(result)
		assertEquals("Original Title", result.title)
		assertEquals("Test Author", result.authors.first())
		assertTrue(result.coverUrl?.contains("/cover.jpg") == true)
		assertEquals("Test description", result.description)
	}

	@Test
	fun `test TOC rules can read runtime chapter title and index like MD3`() = runTest {
		val html = """
			<html>
			<body>
					<div id="content_1">
						<a>ChapterOne</a>
						<a>ChapterTwo</a>
					</div>
				</body>
				</html>
		""".trimIndent()

		val config = LegadoBookSource(
			bookSourceName = "Test Source",
			bookSourceUrl = mockServer.url("/").toString(),
			ruleToc = TocRule(
				chapterList = "id.content_1.0@tag.a",
				chapterName = "text",
				chapterUrl = "/chapter/@get:{title}/@get:{index}"
			)
		)
		val source = object : ContentSource {
			override val name: String = "Test Source"
			override val locale: String = "zh"
			override val contentType: ContentType = ContentType.NOVEL
		}
		val result = BookChapterList.parseWithRuntimeContext(
			content = html,
			baseUrl = mockServer.url("/book/runtime").toString(),
			source = source,
			config = config,
			runtimeContext = TestLegadoRuleRuntimeContext(),
		)
		val chapters = result.chapters

		assertEquals(2, chapters.size)
		assertTrue(chapters[0].url.endsWith("/chapter/ChapterOne/1"))
		assertTrue(chapters[1].url.endsWith("/chapter/ChapterTwo/2"))
		assertFalse(result.shouldReverse)
	}
}
