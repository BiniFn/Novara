package org.skepsun.kototoro.core.parser.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for RuleCache
 * 
 * Tests caching behavior, cache hits/misses, and eviction
 */
class RuleCacheTest {
	
	private lateinit var cache: RuleCache
	
	@Before
	fun setup() {
		cache = RuleCache()
	}
	
	@Test
	fun testCacheHit() {
		var compileCount = 0
		val compiler: (String) -> CompiledRule = { rule ->
			compileCount++
			CompiledRule(RuleType.CSS, rule)
		}
		
		// First call should compile
		cache.getOrCompile("div.title", compiler)
		assertEquals(1, compileCount)
		
		// Second call should use cache
		cache.getOrCompile("div.title", compiler)
		assertEquals(1, compileCount) // Still 1, not compiled again
	}
	
	@Test
	fun testCacheMiss() {
		var compileCount = 0
		val compiler: (String) -> CompiledRule = { rule ->
			compileCount++
			CompiledRule(RuleType.CSS, rule)
		}
		
		cache.getOrCompile("div.title", compiler)
		cache.getOrCompile("div.author", compiler)
		
		assertEquals(2, compileCount)
	}
	
	@Test
	fun testCacheEviction() {
		val compiler: (String) -> CompiledRule = { rule ->
			CompiledRule(RuleType.CSS, rule)
		}
		
		// Fill cache with many rules (cache size is 500)
		for (i in 1..500) {
			cache.getOrCompile("rule$i", compiler)
		}
		
		assertEquals(500, cache.size())
		
		// Add one more, should evict the oldest
		cache.getOrCompile("rule501", compiler)
		
		// Size should still be 500 (max size)
		assertEquals(500, cache.size())
	}
	
	@Test
	fun testCacheClear() {
		val compiler: (String) -> CompiledRule = { rule ->
			CompiledRule(RuleType.CSS, rule)
		}
		
		cache.getOrCompile("rule1", compiler)
		cache.getOrCompile("rule2", compiler)
		
		assertEquals(2, cache.size())
		
		cache.clear()
		
		assertEquals(0, cache.size())
	}
	
	@Test
	fun testCacheStats() {
		val compiler: (String) -> CompiledRule = { rule ->
			CompiledRule(RuleType.CSS, rule)
		}
		
		// First access - miss
		cache.getOrCompile("rule1", compiler)
		
		// Second access - hit
		cache.getOrCompile("rule1", compiler)
		
		// Third access - miss
		cache.getOrCompile("rule2", compiler)
		
		val stats = cache.getStats()
		
		assertEquals(2, stats.size)
		assertEquals(1, stats.hitCount)
		assertEquals(2, stats.missCount)
		assertEquals(2, stats.putCount)
		
		// Hit rate should be 1/3 = 0.333...
		assertTrue(stats.hitRate > 0.3)
		assertTrue(stats.hitRate < 0.4)
	}
	
	@Test
	fun testCacheStatsWithNoAccess() {
		val stats = cache.getStats()
		
		assertEquals(0, stats.size)
		assertEquals(0, stats.hitCount)
		assertEquals(0, stats.missCount)
		assertEquals(0.0, stats.hitRate, 0.001)
	}
}
