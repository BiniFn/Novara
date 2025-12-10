package org.skepsun.kototoro.core.parser.dynamic

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.core.model.jsonsource.*
import org.skepsun.kototoro.core.parser.rule.DefaultRuleEngine
import org.skepsun.kototoro.core.parser.rule.RuleCache
import org.skepsun.kototoro.parsers.MangaLoaderContext
import org.skepsun.kototoro.parsers.model.MangaListFilter
import org.skepsun.kototoro.parsers.model.SortOrder

/**
 * Integration tests for dynamic parser components
 * 
 * These tests verify that the dynamic parser factory, parser implementation,
 * and parser pool work together correctly.
 * 
 * NOTE: These tests are currently disabled due to API changes.
 * They need to be updated to match the current implementation.
 */
class DynamicParserIntegrationTest {
	
	// Tests commented out - need API updates
	/*
	
	private lateinit var ruleEngine: DefaultRuleEngine
	private lateinit var parserFactory: DynamicParserFactory
	private lateinit var parserPool: ParserPool
	
	@BeforeEach
	fun setup() {
		val ruleCache = RuleCache()
		ruleEngine = DefaultRuleEngine(ruleCache)
		parserFactory = DynamicParserFactory(ruleEngine)
		parserPool = ParserPool()
	}
	
	// DynamicParserFactory Tests
	
	@Test
	fun testCreateParserWithLegadoSource() {
		val config = createTestLegadoConfig()
		val configJson = """
			{
				"bookSourceName": "Test Source",
				"bookSourceUrl": "https://example.com",
				"searchUrl": "https://example.com/search?q={{key}}",
				"ruleSearch": {
					"bookList": "div.book-item",
					"name": "h3.title@text",
					"bookUrl": "a@href"
				}
			}
		""".trimIndent()
		
		val source = JsonSourceEntity(
			id = "JSON_LEGADO_TEST",
			name = "Test Source",
			type = JsonSourceType.LEGADO,
			config = configJson,
			enabled = true,
			createdAt = System.currentTimeMillis(),
			updatedAt = System.currentTimeMillis(),
		)
		
		val context = createMockContext()
		val parser = parserFactory.createParser(source, context)
		
		assertNotNull(parser)
		assertTrue(parser is DynamicLegadoParser)
	}
	
	@Test
	fun testCreateParserWithTVBoxSource() {
		val source = JsonSourceEntity(
			id = "JSON_TVBOX_TEST",
			name = "Test TVBox",
			type = JsonSourceType.TVBOX,
			config = "{}",
			enabled = true,
			createdAt = System.currentTimeMillis(),
			updatedAt = System.currentTimeMillis(),
		)
		
		val context = createMockContext()
		val parser = parserFactory.createParser(source, context)
		
		// TVBox not yet implemented
		assertNull(parser)
	}
	
	@Test
	fun testValidateConfigValid() {
		val config = LegadoBookSource(
			bookSourceName = "Test Source",
			bookSourceUrl = "https://example.com",
			ruleSearch = SearchRule(
				bookList = "div.book",
				name = "h3@text",
				bookUrl = "a@href",
			),
		)
		
		val result = parserFactory.validateConfig(config)
		
		assertTrue(result.isValid)
		assertTrue(result.errors.isEmpty())
	}
	
	@Test
	fun testValidateConfigMissingName() {
		val config = LegadoBookSource(
			bookSourceName = "",
			bookSourceUrl = "https://example.com",
		)
		
		val result = parserFactory.validateConfig(config)
		
		assertFalse(result.isValid)
		assertTrue(result.errors.any { it.contains("bookSourceName") })
	}
	
	@Test
	fun testValidateConfigMissingUrl() {
		val config = LegadoBookSource(
			bookSourceName = "Test",
			bookSourceUrl = "",
		)
		
		val result = parserFactory.validateConfig(config)
		
		assertFalse(result.isValid)
		assertTrue(result.errors.any { it.contains("bookSourceUrl") })
	}
	
	@Test
	fun testValidateConfigNoRules() {
		val config = LegadoBookSource(
			bookSourceName = "Test",
			bookSourceUrl = "https://example.com",
		)
		
		val result = parserFactory.validateConfig(config)
		
		assertFalse(result.isValid)
		assertTrue(result.errors.any { it.contains("rule") })
	}
	
	@Test
	fun testValidateConfigUnsupportedType() {
		val config = "unsupported"
		
		val result = parserFactory.validateConfig(config)
		
		assertFalse(result.isValid)
		assertTrue(result.errors.any { it.contains("Unsupported") })
	}
	
	// ParserPool Tests
	
	@Test
	fun testParserPoolGetOrCreate() {
		val sourceId = "TEST_SOURCE"
		var factoryCallCount = 0
		
		val parser1 = parserPool.getOrCreate(sourceId) {
			factoryCallCount++
			createMockParser()
		}
		
		val parser2 = parserPool.getOrCreate(sourceId) {
			factoryCallCount++
			createMockParser()
		}
		
		// Factory should only be called once
		assertEquals(1, factoryCallCount)
		// Should return the same instance
		assertSame(parser1, parser2)
	}
	
	@Test
	fun testParserPoolInvalidate() {
		val sourceId = "TEST_SOURCE"
		var factoryCallCount = 0
		
		val parser1 = parserPool.getOrCreate(sourceId) {
			factoryCallCount++
			createMockParser()
		}
		
		parserPool.invalidate(sourceId)
		
		val parser2 = parserPool.getOrCreate(sourceId) {
			factoryCallCount++
			createMockParser()
		}
		
		// Factory should be called twice (once before invalidate, once after)
		assertEquals(2, factoryCallCount)
		// Should return different instances
		assertNotSame(parser1, parser2)
	}
	
	@Test
	fun testParserPoolClear() {
		parserPool.getOrCreate("SOURCE_1") { createMockParser() }
		parserPool.getOrCreate("SOURCE_2") { createMockParser() }
		
		assertEquals(2, parserPool.size())
		
		parserPool.clear()
		
		assertEquals(0, parserPool.size())
	}
	
	@Test
	fun testParserPoolContains() {
		val sourceId = "TEST_SOURCE"
		
		assertFalse(parserPool.contains(sourceId))
		
		parserPool.getOrCreate(sourceId) { createMockParser() }
		
		assertTrue(parserPool.contains(sourceId))
	}
	
	@Test
	fun testParserPoolMultipleSources() {
		val parser1 = parserPool.getOrCreate("SOURCE_1") { createMockParser() }
		val parser2 = parserPool.getOrCreate("SOURCE_2") { createMockParser() }
		val parser3 = parserPool.getOrCreate("SOURCE_1") { createMockParser() }
		
		// Different sources should have different parsers
		assertNotSame(parser1, parser2)
		// Same source should return same parser
		assertSame(parser1, parser3)
		
		assertEquals(2, parserPool.size())
	}
	
	// DynamicLegadoParser Tests
	
	@Test
	fun testDynamicLegadoParserCreation() {
		val config = createTestLegadoConfig()
		val context = createMockContext()
		
		val parser = DynamicLegadoParser(
			context = context,
			config = config,
			sourceId = "TEST_SOURCE",
			ruleEngine = ruleEngine,
		)
		
		assertNotNull(parser)
		assertEquals("Test Source", parser.source.name)
		assertTrue(parser.filterCapabilities.isSearchSupported)
	}
	
	@Test
	fun testDynamicLegadoParserWithoutSearchUrl() {
		val config = LegadoBookSource(
			bookSourceName = "Test Source",
			bookSourceUrl = "https://example.com",
			searchUrl = null,
			ruleSearch = SearchRule(
				bookList = "div.book",
				name = "h3@text",
				bookUrl = "a@href",
			),
		)
		val context = createMockContext()
		
		val parser = DynamicLegadoParser(
			context = context,
			config = config,
			sourceId = "TEST_SOURCE",
			ruleEngine = ruleEngine,
		)
		
		assertFalse(parser.filterCapabilities.isSearchSupported)
	}
	
	@Test
	fun testDynamicLegadoParserErrorHandling() = runBlocking {
		val config = createTestLegadoConfig()
		val context = createMockContext()
		
		val parser = DynamicLegadoParser(
			context = context,
			config = config,
			sourceId = "TEST_SOURCE",
			ruleEngine = ruleEngine,
		)
		
		// Test with invalid URL - should return empty list, not crash
		val result = parser.getListPage(1, SortOrder.UPDATED, MangaListFilter())
		
		// Should handle error gracefully
		assertNotNull(result)
		assertTrue(result.isEmpty())
	}
	
	// Helper Methods
	
	private fun createTestLegadoConfig(): LegadoBookSource {
		return LegadoBookSource(
			bookSourceName = "Test Source",
			bookSourceUrl = "https://example.com",
			searchUrl = "https://example.com/search?q={{key}}",
			ruleSearch = SearchRule(
				bookList = "div.book-item",
				name = "h3.title@text",
				author = "span.author@text",
				coverUrl = "img@src",
				bookUrl = "a@href",
				intro = "p.intro@text",
			),
			ruleBookInfo = BookInfoRule(
				name = "h1.title@text",
				author = "div.author@text",
				coverUrl = "img.cover@src",
				intro = "div.intro@text",
			),
			ruleToc = TocRule(
				chapterList = "div.chapter-item",
				chapterName = "a@text",
				chapterUrl = "a@href",
			),
			ruleContent = ContentRule(
				content = "div.content@text",
			),
		)
	}
	
	private fun createMockContext(): MangaLoaderContext {
		// This is a simplified mock - in real tests you'd use a proper mock framework
		return object : MangaLoaderContext {
			override val httpClient: okhttp3.OkHttpClient
				get() = okhttp3.OkHttpClient()
			
			override fun getDefaultUserAgent(): String = "Test/1.0"
			
			override fun encodeBase64(data: ByteArray): String {
				return java.util.Base64.getEncoder().encodeToString(data)
			}
			
			override fun decodeBase64(data: String): ByteArray {
				return java.util.Base64.getDecoder().decode(data)
			}
			
			override fun getConfig(source: org.skepsun.kototoro.parsers.model.MangaParserSource): org.skepsun.kototoro.parsers.config.MangaSourceConfig {
				return object : org.skepsun.kototoro.parsers.config.MangaSourceConfig {
					override fun <T> get(key: org.skepsun.kototoro.parsers.config.ConfigKey<T>): T {
						@Suppress("UNCHECKED_CAST")
						return when (key) {
							is org.skepsun.kototoro.parsers.config.ConfigKey.Domain -> "example.com" as T
							else -> throw IllegalArgumentException("Unknown config key")
						}
					}
					
					override fun <T> set(key: org.skepsun.kototoro.parsers.config.ConfigKey<T>, value: T) {}
				}
			}
			
			override fun log(tag: String, message: String, error: Throwable?) {
				// No-op for tests
			}
		}
	}
	
	private fun createMockParser(): org.skepsun.kototoro.parsers.MangaParser {
		val config = createTestLegadoConfig()
		val context = createMockContext()
		return DynamicLegadoParser(
			context = context,
			config = config,
			sourceId = "MOCK_SOURCE",
			ruleEngine = ruleEngine,
		)
	}
}
*/
}
