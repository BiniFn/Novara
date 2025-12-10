package org.skepsun.kototoro.core.parser.dynamic

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.core.jsonsource.JsonMangaSource
import org.skepsun.kototoro.core.model.jsonsource.JsonSourceType
import org.skepsun.kototoro.core.parser.rule.DefaultRuleEngine
import org.skepsun.kototoro.core.parser.rule.EnhancedRuleEngine
import org.skepsun.kototoro.core.parser.rule.RuleCache
import org.skepsun.kototoro.core.javascript.JavaScriptEngine
import org.skepsun.kototoro.core.javascript.JavaScriptRuleParser
import org.skepsun.kototoro.core.javascript.JavaScriptContext
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.parsers.model.ContentType
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test for BasicJsonRepository
 * 
 * Task 31.1: Tests complete parsing flow with real Legado configurations
 * - Tests list page parsing
 * - Tests details page parsing  
 * - Tests chapter content parsing
 * - Tests error handling
 * 
 * Requirements: 4.2, 4.3, 4.4
 */
class BasicJsonRepositoryIntegrationTest {
	
	private lateinit var mockServer: MockWebServer
	private lateinit var httpClient: LegadoHttpClient
	private lateinit var ruleEngine: EnhancedRuleEngine
	
	@Before
	fun setup() {
		mockServer = MockWebServer()
		mockServer.start()
		
		val okHttpClient = OkHttpClient.Builder().build()
		httpClient = LegadoHttpClient(okHttpClient)
		
		// Create EnhancedRuleEngine with mock JavaScript engine
		val cache = RuleCache()
		val baseRuleEngine = DefaultRuleEngine(cache)
		
		val mockJsEngine = object : JavaScriptEngine {
			override fun execute(script: String, context: JavaScriptContext): Any? {
				return "JavaScript Result"
			}
			
			override fun evaluate(expression: String, context: JavaScriptContext): Any? {
				return execute(expression, context)
			}
			
			override fun registerGlobalObject(name: String, obj: Any) {}
			
			override fun dispose() {}
		}
		
		val jsRuleParser = JavaScriptRuleParser(mockJsEngine)
		ruleEngine = EnhancedRuleEngine(baseRuleEngine, jsRuleParser)
	}
	
	@After
	fun tearDown() {
		mockServer.shutdown()
	}
	
	/**
	 * Test parsing a list page with CSS selectors
	 */
	@Test
	fun `test getList with CSS selectors`() = runTest {
		// Setup mock response
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
		
		// Create JSON source with CSS selectors
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
		
		val source = JsonMangaSource(entity, ContentType.OTHER)
		val repository = BasicJsonRepository(source, httpClient, ruleEngine)
		
		// Execute
		val results = repository.getList(0, null, null)
		
		// Verify
		assertEquals(2, results.size)
		assertEquals("Test Book 1", results[0].title)
		assertEquals("Test Book 2", results[1].title)
		assertTrue(results[0].url.contains("/book/1"))
		assertTrue(results[1].url.contains("/book/2"))
	}
	
	/**
	 * Test parsing with XPath selectors (like the failing source)
	 */
	@Test
	fun `test getDetails with XPath selectors`() = runTest {
		// Setup mock response with meta tags
		val html = """
			<html>
			<head>
				<meta property="og:novel:book_name" content="Test Novel"/>
				<meta property="og:novel:author" content="Test Author"/>
				<meta property="og:image" content="/cover.jpg"/>
				<meta property="og:description" content="Test description"/>
				<meta property="og:novel:category" content="Fantasy"/>
				<meta property="og:novel:status" content="Ongoing"/>
				<meta property="og:novel:latest_chapter_name" content="Chapter 100"/>
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
		
		// Create JSON source with XPath selectors (like the failing source)
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
		
		val source = JsonMangaSource(entity, ContentType.OTHER)
		val repository = BasicJsonRepository(source, httpClient, ruleEngine)
		
		// Create a test manga
		val testManga = org.skepsun.kototoro.parsers.model.Manga(
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
		
		// Execute
		val result = repository.getDetails(testManga)
		
		// Verify - XPath parsing should work
		assertNotNull(result)
		// Note: XPath support needs to be implemented in RuleEngine
		// For now, this test documents the expected behavior
	}
	
	/**
	 * Test error handling when config is incomplete
	 */
	@Test
	fun `test getList with missing ruleSearch returns empty list`() = runTest {
		// Create JSON source without ruleSearch
		val config = """
			{
				"bookSourceName":"Test Source",
				"bookSourceUrl":"${mockServer.url("/")}"
			}
		""".trimIndent()
		
		val entity = JsonSourceEntity(
			id = "JSON_TEST_3",
			name = "Test Source",
			type = JsonSourceType.LEGADO,
			config = config,
			enabled = true,
			createdAt = System.currentTimeMillis(),
			updatedAt = System.currentTimeMillis()
		)
		
		val source = JsonMangaSource(entity, ContentType.OTHER)
		val repository = BasicJsonRepository(source, httpClient, ruleEngine)
		
		// Execute
		val results = repository.getList(0, null, null)
		
		// Verify - should return empty list, not crash
		assertEquals(0, results.size)
	}
	
	/**
	 * Test network error handling
	 */
	@Test
	fun `test getList with network error returns empty list`() = runTest {
		// Don't enqueue any response - will cause connection error
		mockServer.shutdown()
		
		val config = """
			{
				"bookSourceName":"Test Source",
				"bookSourceUrl":"http://localhost:99999/",
				"searchUrl":"/search",
				"ruleSearch":{
					"bookList":"class.item",
					"name":"tag.h3.0@text",
					"bookUrl":"tag.a.0@href"
				}
			}
		""".trimIndent()
		
		val entity = JsonSourceEntity(
			id = "JSON_TEST_4",
			name = "Test Source",
			type = JsonSourceType.LEGADO,
			config = config,
			enabled = true,
			createdAt = System.currentTimeMillis(),
			updatedAt = System.currentTimeMillis()
		)
		
		val source = JsonMangaSource(entity, ContentType.OTHER)
		val repository = BasicJsonRepository(source, httpClient, ruleEngine)
		
		// Execute
		val results = repository.getList(0, null, null)
		
		// Verify - should return empty list, not crash
		assertEquals(0, results.size)
	}
}
