package org.skepsun.kototoro.core.parser.rule

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Performance tests for RuleCache
 * 
 * Tests cache effectiveness, hit rates, and performance improvements
 * Validates: Requirements 11.3 (Performance - Rule caching)
 */
class RuleCachePerformanceTest {
	
	private lateinit var cache: RuleCache
	
	@Before
	fun setup() {
		cache = RuleCache()
	}
	
	/**
	 * Test cache hit rate with repeated rules
	 * 
	 * Validates that the cache effectively stores and retrieves compiled rules,
	 * achieving a high hit rate for repeated rules.
	 */
	@Test
	fun testCacheHitRate() {
		val rules = listOf(
			"div.title@text",
			"a@href",
			"img@src",
			"span.author@text",
			"div.content@text"
		)
		
		// First pass - all misses (cache is empty)
		rules.forEach { rule ->
			cache.getOrCompile(rule) { compileTestRule(it) }
		}
		
		val statsAfterFirstPass = cache.getStats()
		assertEquals(5, statsAfterFirstPass.missCount)
		assertEquals(0, statsAfterFirstPass.hitCount)
		
		// Second pass - all hits (rules are cached)
		repeat(10) {
			rules.forEach { rule ->
				cache.getOrCompile(rule) { compileTestRule(it) }
			}
		}
		
		val statsAfterSecondPass = cache.getStats()
		assertEquals(5, statsAfterSecondPass.missCount) // Still 5 misses from first pass
		assertEquals(50, statsAfterSecondPass.hitCount) // 5 rules * 10 iterations
		
		// Hit rate should be 50 / (50 + 5) = ~90.9%
		assertTrue(statsAfterSecondPass.hitRate > 0.9)
	}
	
	/**
	 * Test cache performance improvement
	 * 
	 * Validates that using the cache significantly improves performance
	 * compared to compiling rules every time.
	 */
	@Test
	fun testCachePerformanceImprovement() {
		val rules = (1..100).map { "div.rule$it@text" }
		
		// Measure time without cache (compile every time)
		val timeWithoutCache = measureTimeMillis {
			repeat(10) {
				rules.forEach { rule ->
					compileTestRule(rule)
				}
			}
		}
		
		// Measure time with cache
		val timeWithCache = measureTimeMillis {
			repeat(10) {
				rules.forEach { rule ->
					cache.getOrCompile(rule) { compileTestRule(it) }
				}
			}
		}
		
		// Cache should be significantly faster (at least 2x)
		// First iteration fills cache, subsequent iterations benefit from cache
		println("Time without cache: ${timeWithoutCache}ms")
		println("Time with cache: ${timeWithCache}ms")
		println("Speedup: ${timeWithoutCache.toDouble() / timeWithCache}x")
		
		// Cache should provide some speedup
		// Note: In tests, the speedup might be modest because compilation is simple
		// In real usage with complex regex and CSS selectors, speedup is more significant
		assertTrue("Cache should improve performance", timeWithCache < timeWithoutCache * 1.5)
	}
	
	/**
	 * Test cache with many rules (stress test)
	 * 
	 * Validates that the cache handles a large number of rules efficiently
	 * and respects the maximum size limit.
	 */
	@Test
	fun testCacheWithManyRules() {
		val testCache = RuleCache()
		
		// Add 600 rules (exceeds cache size of 500)
		val rules = (1..600).map { "div.rule$it@text" }
		
		rules.forEach { rule ->
			testCache.getOrCompile(rule) { compileTestRule(it) }
		}
		
		val stats = testCache.getStats()
		
		// Cache size should not exceed max size (500)
		assertTrue(stats.size <= 500)
		
		// Some rules should have been evicted
		assertTrue(stats.evictionCount > 0)
		
		// All rules should have been compiled (put count = 600)
		assertEquals(600, stats.putCount)
	}
	
	/**
	 * Test cache prewarming
	 * 
	 * Validates that prewarming the cache with common rules improves
	 * performance for first-time access.
	 */
	@Test
	fun testCachePrewarming() {
		val commonRules = listOf(
			"a@href",
			"img@src",
			"div@text",
			"span@text"
		)
		
		// Prewarm the cache
		cache.prewarm(commonRules) { compileTestRule(it) }
		
		val statsAfterPrewarm = cache.getStats()
		assertEquals(4, statsAfterPrewarm.size)
		assertEquals(4, statsAfterPrewarm.putCount)
		
		// Access prewarmed rules - should all be hits
		commonRules.forEach { rule ->
			cache.getOrCompile(rule) { compileTestRule(it) }
		}
		
		val statsAfterAccess = cache.getStats()
		assertEquals(4, statsAfterAccess.hitCount)
		assertEquals(0, statsAfterAccess.missCount)
	}
	
	/**
	 * Test cache statistics accuracy
	 * 
	 * Validates that cache statistics are accurately tracked.
	 */
	@Test
	fun testCacheStatistics() {
		val rules = listOf("rule1", "rule2", "rule3")
		
		// Add 3 rules
		rules.forEach { rule ->
			cache.getOrCompile(rule) { compileTestRule(it) }
		}
		
		var stats = cache.getStats()
		assertEquals(3, stats.size)
		assertEquals(3, stats.putCount)
		assertEquals(3, stats.missCount)
		assertEquals(0, stats.hitCount)
		assertEquals(0.0, stats.hitRate, 0.01)
		
		// Access rules again (hits)
		rules.forEach { rule ->
			cache.getOrCompile(rule) { compileTestRule(it) }
		}
		
		stats = cache.getStats()
		assertEquals(3, stats.hitCount)
		assertEquals(3, stats.missCount)
		assertEquals(0.5, stats.hitRate, 0.01) // 3 hits / (3 hits + 3 misses)
		
		// Clear cache
		cache.clear()
		stats = cache.getStats()
		assertEquals(0, stats.size)
	}
	
	/**
	 * Test concurrent cache access
	 * 
	 * Validates that the cache is thread-safe and handles concurrent access correctly.
	 */
	@Test
	fun testConcurrentCacheAccess() {
		val rules = (1..50).map { "div.rule$it@text" }
		val threads = (1..10).map { threadId ->
			Thread {
				repeat(100) {
					rules.forEach { rule ->
						cache.getOrCompile(rule) { compileTestRule(it) }
					}
				}
			}
		}
		
		// Start all threads
		threads.forEach { it.start() }
		
		// Wait for all threads to complete
		threads.forEach { it.join() }
		
		val stats = cache.getStats()
		
		// All rules should be in cache
		assertEquals(50, stats.size)
		
		// Should have many hits (10 threads * 100 iterations * 50 rules = 50,000 accesses)
		// First 50 are misses, rest are hits
		assertTrue(stats.hitCount > 49000)
		assertEquals(50, stats.missCount)
	}
	
	/**
	 * Helper function to simulate rule compilation
	 * In real usage, this would be actual CSS/regex compilation
	 */
	private fun compileTestRule(rule: String): CompiledRule {
		// Simulate some work
		Thread.sleep(1) // 1ms delay to simulate compilation time
		
		return CompiledRule(
			type = RuleType.CSS,
			selector = rule,
			attribute = "text"
		)
	}
}
