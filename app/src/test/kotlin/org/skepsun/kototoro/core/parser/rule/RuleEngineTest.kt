package org.skepsun.kototoro.core.parser.rule

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for RuleEngine implementation
 * 
 * Tests CSS selector parsing, regex parsing, list parsing, and error handling
 */
class RuleEngineTest {
	
	private lateinit var ruleEngine: RuleEngine
	private lateinit var ruleCache: RuleCache
	
	@Before
	fun setup() {
		ruleCache = RuleCache()
		ruleEngine = DefaultRuleEngine(ruleCache)
	}
	
	// CSS Selector Tests
	
	@Test
	fun testBasicCssSelector() {
		val html = """
			<div class="container">
				<h1 class="title">Test Title</h1>
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "h1.title")
		assertEquals("Test Title", result)
	}
	
	@Test
	fun testCssSelectorWithTextAttribute() {
		val html = """
			<div class="content">
				<p>Paragraph text</p>
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "p@text")
		assertEquals("Paragraph text", result)
	}
	
	@Test
	fun testCssSelectorWithHrefAttribute() {
		val html = """
			<a href="https://example.com" class="link">Click here</a>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "a.link@href")
		assertEquals("https://example.com", result)
	}
	
	@Test
	fun testCssSelectorWithSrcAttribute() {
		val html = """
			<img src="/images/cover.jpg" alt="Cover" />
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "img@src")
		assertEquals("/images/cover.jpg", result)
	}
	
	@Test
	fun testCssSelectorWithHtmlAttribute() {
		val html = """
			<div class="content">
				<p>Text with <strong>bold</strong></p>
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "div.content@html")
		assertTrue(result.contains("<strong>bold</strong>"))
	}
	
	@Test
	fun testCssSelectorNotFound() {
		val html = """
			<div class="container">
				<p>Some text</p>
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "h1.title")
		assertEquals("", result)
	}
	
	// Regex Tests
	
	@Test
	fun testSimpleRegex() {
		val html = """
			<div>The title is: <title>My Book</title></div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "@regex:<title>(.*?)</title>")
		assertEquals("My Book", result)
	}
	
	@Test
	fun testRegexWithCaptureGroup() {
		val html = """
			<div>Author: John Doe, Year: 2023</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "@regex:Author: ([^,]+)")
		assertEquals("John Doe", result)
	}
	
	@Test
	fun testRegexNotFound() {
		val html = """
			<div>Some content without match</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "@regex:<title>(.*?)</title>")
		assertEquals("", result)
	}
	
	// List Parsing Tests
	
	@Test
	fun testParseList() {
		val html = """
			<div class="book-list">
				<div class="book-item">
					<h3 class="title">Book 1</h3>
					<a href="/book1" class="link">Read</a>
				</div>
				<div class="book-item">
					<h3 class="title">Book 2</h3>
					<a href="/book2" class="link">Read</a>
				</div>
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val itemRules = mapOf(
			"title" to "h3.title@text",
			"url" to "a.link@href",
		)
		
		val results = ruleEngine.parseList(doc, "div.book-item", itemRules)
		
		assertEquals(2, results.size)
		assertEquals("Book 1", results[0]["title"])
		assertEquals("/book1", results[0]["url"])
		assertEquals("Book 2", results[1]["title"])
		assertEquals("/book2", results[1]["url"])
	}
	
	@Test
	fun testParseListWithMissingFields() {
		val html = """
			<div class="book-list">
				<div class="book-item">
					<h3 class="title">Book 1</h3>
				</div>
				<div class="book-item">
					<h3 class="title">Book 2</h3>
					<a href="/book2" class="link">Read</a>
				</div>
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val itemRules = mapOf(
			"title" to "h3.title@text",
			"url" to "a.link@href",
		)
		
		val results = ruleEngine.parseList(doc, "div.book-item", itemRules)
		
		assertEquals(2, results.size)
		assertEquals("Book 1", results[0]["title"])
		assertEquals(null, results[0]["url"]) // Missing field
		assertEquals("Book 2", results[1]["title"])
		assertEquals("/book2", results[1]["url"])
	}
	
	@Test
	fun testParseEmptyList() {
		val html = """
			<div class="book-list">
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val itemRules = mapOf(
			"title" to "h3.title@text",
		)
		
		val results = ruleEngine.parseList(doc, "div.book-item", itemRules)
		assertEquals(0, results.size)
	}
	
	// Error Handling Tests
	
	@Test
	fun testInvalidCssSelector() {
		val html = """
			<div>Some content</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		// Invalid selector should return empty string, not crash
		val result = ruleEngine.parseField(doc, "div[[[invalid")
		assertEquals("", result)
	}
	
	@Test
	fun testBlankRule() {
		val html = """
			<div>Some content</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "")
		assertEquals("", result)
	}
	
	@Test
	fun testBlankListRule() {
		val html = """
			<div>Some content</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val results = ruleEngine.parseList(doc, "", mapOf("title" to "h3"))
		assertEquals(0, results.size)
	}
	
	// Rule Compilation Tests
	
	@Test
	fun testCompileCssRule() {
		val rule = ruleEngine.compileRule("div.title@text")
		
		assertEquals(RuleType.CSS, rule.type)
		assertEquals("div.title", rule.selector)
		assertEquals("text", rule.attribute)
	}
	
	@Test
	fun testCompileCssRuleWithoutAttribute() {
		val rule = ruleEngine.compileRule("div.title")
		
		assertEquals(RuleType.CSS, rule.type)
		assertEquals("div.title", rule.selector)
		assertEquals(null, rule.attribute)
	}
	
	@Test
	fun testCompileRegexRule() {
		val rule = ruleEngine.compileRule("@regex:<title>(.*?)</title>")
		
		assertEquals(RuleType.REGEX, rule.type)
		assertEquals("<title>(.*?)</title>", rule.selector)
		assertTrue(rule.regex != null)
	}
	
	@Test(expected = IllegalArgumentException::class)
	fun testCompileInvalidRegex() {
		ruleEngine.compileRule("@regex:[invalid(")
	}
	
	@Test(expected = IllegalArgumentException::class)
	fun testCompileDangerousRegex() {
		// This should throw due to ReDoS protection
		ruleEngine.compileRule("@regex:(.*)*")
	}
	
	@Test(expected = IllegalArgumentException::class)
	fun testCompileTooLongRegex() {
		// Create a regex longer than 500 characters
		val longPattern = "a".repeat(501)
		ruleEngine.compileRule("@regex:$longPattern")
	}
	
	@Test(expected = IllegalArgumentException::class)
	fun testCompileBlankRule() {
		ruleEngine.compileRule("")
	}
	
	// XPath Tests
	
	@Test
	fun testBasicXPath() {
		val html = """
			<div class="container">
				<h1 class="title">XPath Title</h1>
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "@xpath://h1[@class='title']")
		assertEquals("XPath Title", result)
	}
	
	@Test
	fun testXPathWithoutPrefix() {
		val html = """
			<div class="container">
				<p>XPath content</p>
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "//p")
		assertEquals("XPath content", result)
	}
	
	@Test
	fun testXPathNotFound() {
		val html = """
			<div>Some content</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "//h1[@class='missing']")
		assertEquals("", result)
	}
	
	@Test
	fun testCompileXPathRule() {
		val rule = ruleEngine.compileRule("@xpath://div[@class='title']")
		
		assertEquals(RuleType.XPATH, rule.type)
		assertEquals("//div[@class='title']", rule.selector)
	}
	
	// JSONPath Tests
	
	@Test
	fun testBasicJSONPath() {
		val html = """
			<div>{"data": {"title": "JSON Title"}}</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "@jsonpath:$.data.title")
		assertEquals("JSON Title", result)
	}
	
	@Test
	fun testJSONPathWithoutPrefix() {
		val html = """
			<div>{"items": ["item1", "item2"]}</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "$.items")
		assertTrue(result.contains("item1"))
	}
	
	@Test
	fun testJSONPathNotFound() {
		val html = """
			<div>{"data": {}}</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "$.missing.field")
		assertEquals("", result)
	}
	
	@Test
	fun testCompileJSONPathRule() {
		val rule = ruleEngine.compileRule("@jsonpath:$.data.items")
		
		assertEquals(RuleType.JSON_PATH, rule.type)
		assertEquals("$.data.items", rule.selector)
	}
	
	// Rule Chain Tests
	
	@Test
	fun testSimpleRuleChain() {
		val html = """
			<div class="container">
				<a href="/book/123">Book Title</a>
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		// First select the container, then get the link href
		val result = ruleEngine.parseField(doc, "div.container##a@href")
		assertEquals("/book/123", result)
	}
	
	@Test
	fun testRuleChainWithRegex() {
		val html = """
			<div class="container">
				<a href="/book/id=12345">Book Title</a>
			</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		// Select link, get href, then extract ID with regex
		val result = ruleEngine.parseField(doc, "a@href##@regex:id=(\\d+)")
		assertEquals("12345", result)
	}
	
	@Test
	fun testCompileRuleChain() {
		val rule = ruleEngine.compileRule("div.container##a@href##@regex:id=(\\d+)")
		
		assertTrue(rule.chainedRules != null)
		assertEquals(3, rule.chainedRules?.size)
	}
	
	// Modifier Tests
	
	@Test
	fun testFirstModifier() {
		val html = """
			<div>First Second Third</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "div@text@first")
		assertEquals("First", result)
	}
	
	@Test
	fun testLastModifier() {
		val html = """
			<div>First Second Third</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "div@text@last")
		assertEquals("Third", result)
	}
	
	@Test
	fun testGetModifier() {
		val html = """
			<div>First Second Third</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "div@text@get:1")
		assertEquals("Second", result)
	}
	
	@Test
	fun testSizeModifier() {
		val html = """
			<div>First Second Third</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "div@text@size")
		assertEquals("3", result)
	}
	
	@Test
	fun testReplaceModifier() {
		val html = """
			<div>Hello World</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "div@text@replace:World:Universe")
		assertEquals("Hello Universe", result)
	}
	
	@Test
	fun testSubstringModifier() {
		val html = """
			<div>Hello World</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "div@text@substring:0:5")
		assertEquals("Hello", result)
	}
	
	@Test
	fun testAbsoluteURLModifier() {
		val html = """
			<html>
			<head><base href="https://example.com/"></head>
			<body>
				<a href="/path/to/page">Link</a>
			</body>
			</html>
		""".trimIndent()
		val doc = Jsoup.parse(html, "https://example.com/")
		
		val result = ruleEngine.parseField(doc, "a@href@absoluteURL")
		assertTrue(result.contains("https://example.com"))
	}
	
	@Test
	fun testRelativeURLModifier() {
		val html = """
			<a href="https://example.com/path/to/page?query=value">Link</a>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		val result = ruleEngine.parseField(doc, "a@href@relativeURL")
		assertEquals("/path/to/page?query=value", result)
	}
	
	@Test
	fun testMultipleModifiers() {
		val html = """
			<div>Hello World Test</div>
		""".trimIndent()
		val doc = Jsoup.parse(html)
		
		// Get first word, then replace
		val result = ruleEngine.parseField(doc, "div@text@first@replace:Hello:Hi")
		assertEquals("Hi", result)
	}
	
	@Test
	fun testCompileRuleWithModifiers() {
		val rule = ruleEngine.compileRule("div@text@first@replace:old:new")
		
		assertEquals(RuleType.CSS, rule.type)
		assertEquals("div", rule.selector)
		assertEquals("text", rule.attribute)
		assertTrue(rule.modifiers.isNotEmpty())
	}
}
