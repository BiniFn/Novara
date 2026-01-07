package org.skepsun.kototoro.core.parser.legado

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.core.jsonsource.JsonMangaSource
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.core.javascript.RhinoJavaScriptEngine
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.MangaListFilter
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.core.network.cookies.MutableCookieJar
import org.skepsun.kototoro.core.network.jsonsource.UserAgentManager
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Integration test for JavaScript support in LegadoRepository
 */
class LegadoJavaScriptIntegrationTest {
	
	private lateinit var mockServer: MockWebServer
	private lateinit var httpClient: LegadoHttpClient
	private lateinit var jsEngine: RhinoJavaScriptEngine
	
	@BeforeEach
	fun setup() {
		mockServer = MockWebServer()
		mockServer.start()
		
		val okHttpClient = OkHttpClient.Builder().build()
		httpClient = LegadoHttpClient(okHttpClient, mockk(relaxed = true), UserAgentManager())
		
		// Create real Rhino engine for testing
		jsEngine = RhinoJavaScriptEngine(
			httpClient = httpClient,
			cookieManager = mockk(relaxed = true),
			androidContext = mockk(relaxed = true),
			cookieJar = mockk(relaxed = true)
		)
	}
	
	@AfterEach
	fun tearDown() {
		jsEngine.dispose()
		mockServer.shutdown()
	}
	
	@Test
	fun `test JavaScript in searchUrl`() = runTest {
		val html = """
			<html><body><div class="item"><h3>Test Book</h3><a href="/book/1">Link</a></div></body></html>
		""".trimIndent()
		
		mockServer.enqueue(MockResponse().setBody(html))
		
		val baseUrl = mockServer.url("/").toString()
		val config = """
			{
				"bookSourceName":"Test Source",
				"bookSourceUrl":"$baseUrl",
				"searchUrl":"@js:baseUrl + 'search?q=' + encodeURIComponent(key) + '&page=' + page",
				"ruleSearch":{
					"bookList":"div.item",
					"name":"h3@text",
					"bookUrl":"a@href"
				}
			}
		""".trimIndent()
		
		val entity = JsonSourceEntity(
			id = "JSON_TEST_JS_URL",
			name = "Test Source",
			type = JsonSourceType.LEGADO,
			config = config,
			enabled = true,
			createdAt = System.currentTimeMillis(),
			updatedAt = System.currentTimeMillis()
		)
		
		val source = JsonMangaSource(entity)
		val repository = LegadoRepository(source, httpClient, jsEngine)
		
		val filter = MangaListFilter(query = "test query")
		val results = repository.getList(0, null, filter)
		
		val request = mockServer.takeRequest()
		assertTrue(request.path!!.contains("search?q=test%20query"))
		assertTrue(request.path!!.contains("page=1"))
		
		assertEquals(1, results.size)
		assertEquals("Test Book", results[0].title)
	}
    
    @Test
	fun `test JavaScript in rules`() = runTest {
		val html = """
			<html><body><div class="item"><h3>test book</h3><a href="/book/12345">Link</a></div></body></html>
		""".trimIndent()
		
		mockServer.enqueue(MockResponse().setBody(html))
		
		val baseUrl = mockServer.url("/").toString()
		val config = """
			{
				"bookSourceName":"Test Source",
				"bookSourceUrl":"$baseUrl",
				"searchUrl":"/search",
				"ruleSearch":{
					"bookList":"class.item",
					"name":"tag.h3.0@text@js:result.toUpperCase()",
					"bookUrl":"tag.a.0@href"
				}
			}
		""".trimIndent()
		
		val entity = JsonSourceEntity(
			id = "JSON_TEST_JS_RULE",
			name = "Test Source",
			type = JsonSourceType.LEGADO,
			config = config,
			enabled = true,
			createdAt = System.currentTimeMillis(),
			updatedAt = System.currentTimeMillis()
		)
		
		val source = JsonMangaSource(entity)
		val repository = LegadoRepository(source, httpClient, jsEngine)
		
		val results = repository.getList(0, null, null)
		
		assertEquals(1, results.size)
		assertEquals("TEST BOOK", results[0].title)
	}
}
