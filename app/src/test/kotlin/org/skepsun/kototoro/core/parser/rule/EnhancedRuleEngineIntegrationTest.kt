package org.skepsun.kototoro.core.parser.rule

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for enhanced rule engine features
 * 
 * Tests XPath, JSONPath, rule chains, and modifiers
 * Validates: Requirements 3.1, 3.2, 3.3
 */
class EnhancedRuleEngineIntegrationTest {
	
	private lateinit var ruleEngine: RuleEngine
	private lateinit var ruleCache: RuleCache
	
	@Before
	fun setup() {
		ruleCache = RuleCache()
		ruleEngine = DefaultRuleEngine(ruleCache)
	}
	
	// XPath Integration Tests
	
	@Test
	fun testXPathIntegration() {
		val html = """
			<html>
			<body>
				<div class="book">
					<h1 class="title">Book Title</h1>
					<span class="author">John Doe</span>
					<a href="/book/123">Read More</a>
				</div>
			</body>
			</html>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		// Test XPath selector
		val title = ruleEngine.parseField(doc, "//h1[@class='title']")
		assertEquals("Book Title", title)
		
		val author = ruleEngine.parseField(doc, "//span[@class='author']")
		assertEquals("John Doe", author)
	}
	
	// JSONPath Integration Tests
	
	@Test
	fun testJSONPathIntegration() {
		val html = """
			<div>{"book": {"title": "My Book", "author": "Jane Smith", "year": 2023}}</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		// Test JSONPath selector
		val title = ruleEngine.parseField(doc, "$.book.title")
		assertEquals("My Book", title)
		
		val author = ruleEngine.parseField(doc, "$.book.author")
		assertEquals("Jane Smith", author)
	}
	
	// Rule Chain Integration Tests
	
	@Test
	fun testRuleChainIntegration() {
		val html = """
			<div class="container">
				<div class="book-info">
					<a href="/book/id=12345&page=1">Book Link</a>
				</div>
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		// Test rule chain: select container -> select link -> get href -> extract ID
		val result = ruleEngine.parseField(doc, "div.container##a@href##@regex:id=(\\d+)")
		assertEquals("12345", result)
	}
	
	@Test
	fun testComplexRuleChain() {
		val html = """
			<div class="wrapper">
				<div class="content">
					<p>Author: John Doe, Year: 2023</p>
				</div>
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		// Chain: select wrapper -> select content -> get text -> extract author
		val result = ruleEngine.parseField(doc, "div.wrapper##div.content##p@text##@regex:Author: ([^,]+)")
		assertEquals("John Doe", result)
	}
	
	// Modifier Integration Tests
	
	@Test
	fun testModifiersIntegration() {
		val html = """
			<div class="tags">fiction fantasy adventure</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		// Test first modifier
		val first = ruleEngine.parseField(doc, "div.tags@text@first")
		assertEquals("fiction", first)
		
		// Test last modifier
		val last = ruleEngine.parseField(doc, "div.tags@text@last")
		assertEquals("adventure", last)
		
		// Test get modifier
		val second = ruleEngine.parseField(doc, "div.tags@text@get:1")
		assertEquals("fantasy", second)
		
		// Test size modifier
		val size = ruleEngine.parseField(doc, "div.tags@text@size")
		assertEquals("3", size)
	}
	
	@Test
	fun testReplaceModifier() {
		val html = """
			<div class="description">This is a great book</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "div.description@text@replace:great:amazing")
		assertEquals("This is a amazing book", result)
	}
	
	@Test
	fun testSubstringModifier() {
		val html = """
			<div class="code">ABC123XYZ</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		// Extract middle part
		val result = ruleEngine.parseField(doc, "div.code@text@substring:3:6")
		assertEquals("123", result)
	}
	
	@Test
	fun testChainedModifiers() {
		val html = """
			<div class="data">Hello World Test</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		// Get first word, then replace
		val result = ruleEngine.parseField(doc, "div.data@text@first@replace:Hello:Hi")
		assertEquals("Hi", result)
	}
	
	// Combined Features Integration Tests
	
	@Test
	fun testRuleChainWithModifiers() {
		val html = """
			<div class="container">
				<div class="info">
					<span>Title: My Book Name</span>
				</div>
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		// Chain with regex and replace modifier
		val result = ruleEngine.parseField(doc, "div.container##span@text##@regex:Title: (.+)##@replace:Book:Novel")
		// Note: The regex extracts "My Book Name", then replace changes "Book" to "Novel"
		assertTrue(result.contains("Novel"))
	}
	
	@Test
	fun testXPathWithModifiers() {
		val html = """
			<div>
				<p class="tags">tag1 tag2 tag3</p>
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		// XPath with first modifier
		val result = ruleEngine.parseField(doc, "//p[@class='tags']@first")
		assertEquals("tag1", result)
	}
	
	// Real-world Scenario Tests
	
	@Test
	fun testRealWorldBookParsing() {
		val html = """
			<div class="book-item">
				<h3 class="title">The Great Adventure</h3>
				<div class="meta">
					<span class="author">By: John Smith</span>
					<span class="year">Published: 2023</span>
				</div>
				<a href="/books/view?id=789" class="link">Read Now</a>
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		// Extract title
		val title = ruleEngine.parseField(doc, "h3.title@text")
		assertEquals("The Great Adventure", title)
		
		// Extract author using regex
		val author = ruleEngine.parseField(doc, "span.author@text##@regex:By: (.+)")
		assertEquals("John Smith", author)
		
		// Extract year using regex
		val year = ruleEngine.parseField(doc, "span.year@text##@regex:Published: (\\d+)")
		assertEquals("2023", year)
		
		// Extract book ID from URL using regex
		val bookId = ruleEngine.parseField(doc, "a.link@href##@regex:id=(\\d+)")
		assertEquals("789", bookId)
	}
	
	@Test
	fun testRealWorldListParsing() {
		val html = """
			<div class="book-list">
				<div class="item">
					<h4>Book One</h4>
					<a href="/book/1">Link</a>
				</div>
				<div class="item">
					<h4>Book Two</h4>
					<a href="/book/2">Link</a>
				</div>
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val itemRules = mapOf(
			"title" to "h4@text",
			"url" to "a@href",
			"id" to "a@href##@regex:/book/(\\d+)"
		)
		
		val results = ruleEngine.parseList(doc, "div.item", itemRules)
		
		assertEquals(2, results.size)
		assertEquals("Book One", results[0]["title"])
		assertEquals("/book/1", results[0]["url"])
		assertEquals("1", results[0]["id"])
		assertEquals("Book Two", results[1]["title"])
		assertEquals("2", results[1]["id"])
	}
}
