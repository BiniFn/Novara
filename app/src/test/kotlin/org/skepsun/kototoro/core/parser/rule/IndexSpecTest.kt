package org.skepsun.kototoro.core.parser.rule

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Test for index specifications in rule engine
 * Supports: [0], [0:5], [0,2,4], [!0,1]
 */
class IndexSpecTest {
	
	private lateinit var ruleEngine: DefaultRuleEngine
	private lateinit var ruleCache: RuleCache
	
	@Before
	fun setup() {
		ruleCache = RuleCache()
		ruleEngine = DefaultRuleEngine(ruleCache)
	}
	
	// ========== Single Index Tests ==========
	
	@Test
	fun `test single index - first element`() {
		val html = """
			<div>
				<span>First</span>
				<span>Second</span>
				<span>Third</span>
			</div>
		"""
		val element = Jsoup.parse(html).body()
		
		val rule = "span[0]@text"
		val result = ruleEngine.parseField(element, rule)
		
		assertEquals("First", result)
	}
	
	@Test
	fun `test single index - second element`() {
		val html = """
			<div>
				<span>First</span>
				<span>Second</span>
				<span>Third</span>
			</div>
		"""
		val element = Jsoup.parse(html).body()
		
		val rule = "span[1]@text"
		val result = ruleEngine.parseField(element, rule)
		
		assertEquals("Second", result)
	}
	
	@Test
	fun `test single index - negative index`() {
		val html = """
			<div>
				<span>First</span>
				<span>Second</span>
				<span>Third</span>
			</div>
		"""
		val element = Jsoup.parse(html).body()
		
		val rule = "span[-1]@text"
		val result = ruleEngine.parseField(element, rule)
		
		assertEquals("Third", result)
	}
	
	@Test
	fun `test single index - out of bounds`() {
		val html = """
			<div>
				<span>First</span>
			</div>
		"""
		val element = Jsoup.parse(html).body()
		
		val rule = "span[5]@text"
		val result = ruleEngine.parseField(element, rule)
		
		assertEquals("", result)
	}
	
	// ========== Range Index Tests ==========
	
	@Test
	fun `test range index - simple range`() {
		val html = """
			<div>
				<span>0</span>
				<span>1</span>
				<span>2</span>
				<span>3</span>
				<span>4</span>
			</div>
		"""
		val element = Jsoup.parse(html).body()
		
		// Note: Range returns first element only in current implementation
		// For multiple elements, we'd need to update the logic
		val rule = "span[0:2]@text"
		val result = ruleEngine.parseField(element, rule)
		
		// Should get first element of range
		assertEquals("0", result)
	}
	
	@Test
	fun `test range index - with step`() {
		val html = """
			<div>
				<span>0</span>
				<span>1</span>
				<span>2</span>
				<span>3</span>
				<span>4</span>
			</div>
		"""
		val element = Jsoup.parse(html).body()
		
		val rule = "span[0:4:2]@text"
		val result = ruleEngine.parseField(element, rule)
		
		// Should get first element of range (0, 2, 4)
		assertEquals("0", result)
	}
	
	@Test
	fun `test range index - negative range`() {
		val html = """
			<div>
				<span>0</span>
				<span>1</span>
				<span>2</span>
				<span>3</span>
				<span>4</span>
			</div>
		"""
		val element = Jsoup.parse(html).body()
		
		val rule = "span[-2:-1]@text"
		val result = ruleEngine.parseField(element, rule)
		
		// -2 is index 3, -1 is index 4
		assertEquals("3", result)
	}
	
	// ========== Multiple Index Tests ==========
	
	@Test
	fun `test multiple indexes`() {
		val html = """
			<div>
				<span>0</span>
				<span>1</span>
				<span>2</span>
				<span>3</span>
				<span>4</span>
			</div>
		"""
		val element = Jsoup.parse(html).body()
		
		val rule = "span[0,2,4]@text"
		val result = ruleEngine.parseField(element, rule)
		
		// Should get first element of selection
		assertEquals("0", result)
	}
	
	@Test
	fun `test multiple indexes with negative`() {
		val html = """
			<div>
				<span>0</span>
				<span>1</span>
				<span>2</span>
				<span>3</span>
				<span>4</span>
			</div>
		"""
		val element = Jsoup.parse(html).body()
		
		val rule = "span[0,-1]@text"
		val result = ruleEngine.parseField(element, rule)
		
		// Should get first element (0 or 4)
		assertEquals("0", result)
	}
	
	// ========== Exclude Index Tests ==========
	
	@Test
	fun `test exclude indexes`() {
		val html = """
			<div>
				<span>0</span>
				<span>1</span>
				<span>2</span>
				<span>3</span>
				<span>4</span>
			</div>
		"""
		val element = Jsoup.parse(html).body()
		
		val rule = "span[!0,1]@text"
		val result = ruleEngine.parseField(element, rule)
		
		// Should get first non-excluded element (2)
		assertEquals("2", result)
	}
	
	@Test
	fun `test exclude with negative indexes`() {
		val html = """
			<div>
				<span>0</span>
				<span>1</span>
				<span>2</span>
				<span>3</span>
				<span>4</span>
			</div>
		"""
		val element = Jsoup.parse(html).body()
		
		val rule = "span[!-1,-2]@text"
		val result = ruleEngine.parseField(element, rule)
		
		// Exclude last two elements (-1=4, -2=3)
		// Should get first non-excluded (0)
		assertEquals("0", result)
	}
	
	// ========== Combined with Other Features ==========
	
	@Test
	fun `test index with class selector`() {
		val html = """
			<div>
				<span class="item">First</span>
				<span class="item">Second</span>
				<span class="item">Third</span>
			</div>
		"""
		val element = Jsoup.parse(html).body()
		
		val rule = ".item[1]@text"
		val result = ruleEngine.parseField(element, rule)
		
		assertEquals("Second", result)
	}
	
	@Test
	fun `test index with attribute`() {
		val html = """
			<div>
				<a href="/1">First</a>
				<a href="/2">Second</a>
				<a href="/3">Third</a>
			</div>
		"""
		val element = Jsoup.parse(html).body()
		
		val rule = "a[1]@href"
		val result = ruleEngine.parseField(element, rule)
		
		assertEquals("/2", result)
	}
	
	@Test
	fun `test index with regex replacement`() {
		val html = """
			<div>
				<span>Item 1</span>
				<span>Item 2</span>
				<span>Item 3</span>
			</div>
		"""
		val element = Jsoup.parse(html).body()
		
		val rule = "span[1]@text##Item\\s"
		val result = ruleEngine.parseField(element, rule)
		
		assertEquals("2", result)
	}
	
	@Test
	fun `test index with OR operator`() {
		val html = """
			<div>
				<span class="a">A1</span>
				<span class="a">A2</span>
				<span class="b">B1</span>
			</div>
		"""
		val element = Jsoup.parse(html).body()
		
		val rule = ".a[1]@text||.b[0]@text"
		val result = ruleEngine.parseField(element, rule)
		
		assertEquals("A2", result)
	}
	
	// ========== Edge Cases ==========
	
	@Test
	fun `test empty elements`() {
		val html = """<div></div>"""
		val element = Jsoup.parse(html).body()
		
		val rule = "span[0]@text"
		val result = ruleEngine.parseField(element, rule)
		
		assertEquals("", result)
	}
	
	@Test
	fun `test invalid index expression`() {
		val html = """
			<div>
				<span>Text</span>
			</div>
		"""
		val element = Jsoup.parse(html).body()
		
		// Invalid index expression should be ignored
		val rule = "span[abc]@text"
		val result = ruleEngine.parseField(element, rule)
		
		// Should treat as regular selector
		assertEquals("", result)
	}
}
