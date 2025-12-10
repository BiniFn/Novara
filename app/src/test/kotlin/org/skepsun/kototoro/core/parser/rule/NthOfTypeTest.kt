package org.skepsun.kototoro.core.parser.rule

import org.jsoup.Jsoup
import org.junit.Test
import org.junit.Assert.*

/**
 * Test :nth-of-type() CSS pseudo-class selector support
 */
class NthOfTypeTest {
	
	private val html = """
		<div class="list-item">
			<p><a href="/book1">书名1</a></p>
			<p class="gray">最新章节1</p>
			<img src="cover1.jpg" data-lazy="lazy1.jpg">
		</div>
		<div class="list-item">
			<p><a href="/book2">书名2</a></p>
			<p class="gray">最新章节2</p>
			<img src="cover2.jpg" data-lazy="lazy2.jpg">
		</div>
	""".trimIndent()
	
	@Test
	fun `test nth-of-type selector in Jsoup`() {
		val doc = Jsoup.parse(html)
		
		// Test :nth-of-type(1) selector
		val selector1 = "p:nth-of-type(1) a"
		val elements1 = doc.select(selector1)
		println("Selector: $selector1")
		println("Found ${elements1.size} elements")
		elements1.forEachIndexed { i, el ->
			println("  [$i]: ${el.text()} - ${el.attr("href")}")
		}
		
		assertEquals("Should find 2 elements with :nth-of-type(1)", 2, elements1.size)
		assertEquals("书名1", elements1[0].text())
		assertEquals("书名2", elements1[1].text())
		
		// Test :first-of-type selector
		val selector2 = "p:first-of-type a"
		val elements2 = doc.select(selector2)
		println("\nSelector: $selector2")
		println("Found ${elements2.size} elements")
		
		assertEquals("Should find 2 elements with :first-of-type", 2, elements2.size)
		
		// Test within a specific context
		val listItems = doc.select(".list-item")
		println("\nFound ${listItems.size} list items")
		
		listItems.forEachIndexed { i, item ->
			println("\nList item $i:")
			val selector3 = "p:nth-of-type(1) a"
			val links = item.select(selector3)
			println("  Selector: $selector3")
			println("  Found ${links.size} links")
			links.forEach { link ->
				println("    Text: ${link.text()}")
				println("    Href: ${link.attr("href")}")
			}
			
			assertNotNull("Should find at least one link", links.firstOrNull())
		}
	}
	
	@Test
	fun `test rule parsing with nth-of-type`() {
		val cache = RuleCache()
		val engine = DefaultRuleEngine(cache)
		
		val rule = "p:nth-of-type(1) a@text"
		println("Parsing rule: $rule")
		
		val compiled = engine.compileRule(rule)
		println("Compiled rule:")
		println("  Type: ${compiled.type}")
		println("  Selector: ${compiled.selector}")
		println("  Attribute: ${compiled.attribute}")
		
		assertEquals(RuleType.CSS, compiled.type)
		assertEquals("p:nth-of-type(1) a", compiled.selector)
		assertEquals("text", compiled.attribute)
		
		// Test execution
		val doc = Jsoup.parse(html)
		val listItem = doc.selectFirst(".list-item")
		assertNotNull("Should find list item", listItem)
		
		val result = engine.parseField(listItem!!, rule)
		println("\nExecution result: '$result'")
		
		assertEquals("Should extract the book name", "书名1", result)
	}
	
	@Test
	fun `test rule parsing with href attribute`() {
		val cache = RuleCache()
		val engine = DefaultRuleEngine(cache)
		
		val rule = "p:nth-of-type(1) a@href"
		println("Parsing rule: $rule")
		
		val compiled = engine.compileRule(rule)
		println("Compiled rule:")
		println("  Type: ${compiled.type}")
		println("  Selector: ${compiled.selector}")
		println("  Attribute: ${compiled.attribute}")
		
		assertEquals(RuleType.CSS, compiled.type)
		assertEquals("p:nth-of-type(1) a", compiled.selector)
		assertEquals("href", compiled.attribute)
		
		// Test execution
		val doc = Jsoup.parse(html)
		val listItem = doc.selectFirst(".list-item")
		assertNotNull("Should find list item", listItem)
		
		val result = engine.parseField(listItem!!, rule)
		println("\nExecution result: '$result'")
		
		assertEquals("Should extract the book URL", "/book1", result)
	}
}
