package org.skepsun.kototoro.core.jsonsource

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.skepsun.kototoro.core.db.dao.JsonSourceDao
import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import kotlin.system.measureTimeMillis

/**
 * Performance tests for JSON source loading
 * 
 * Tests loading performance with large numbers of sources (100+)
 * Validates: Requirements 11.1, 11.2 (Performance - Source loading)
 * 
 * NOTE: These tests are currently disabled as they require Mockito which is not
 * available in the project. They can be re-enabled if Mockito is added as a dependency.
 */
class JsonSourceLoadingPerformanceTest {
	
	// Tests commented out - require Mockito
	/*
	
	/**
	 * Test loading 100+ sources
	 * 
	 * Validates that the system can efficiently load and manage
	 * a large number of JSON sources.
	 */
	@Test
	fun testLoadingManySourcesPerformance() = runTest {
		// Create 150 test sources
		val sources = (1..150).map { index ->
			createTestSource(
				id = "JSON_LEGADO_SOURCE_$index",
				name = "Test Source $index",
				type = JsonSourceType.LEGADO
			)
		}
		
		// Mock DAO
		val dao = mock(JsonSourceDao::class.java)
		`when`(dao.observeAll()).thenReturn(kotlinx.coroutines.flow.flowOf(sources))
		
		val manager = JsonSourceManager(dao)
		
		// Measure time to load all sources
		val loadTime = measureTimeMillis {
			val loadedSources = manager.observeAllJsonSources().first()
			assertEquals(150, loadedSources.size)
		}
		
		println("Time to load 150 sources: ${loadTime}ms")
		
		// Loading should be fast (< 1 second)
		assertTrue("Loading 150 sources should be fast", loadTime < 1000)
	}
	
	/**
	 * Test concurrent source import
	 * 
	 * Validates that importing multiple sources concurrently
	 * is faster than sequential import.
	 */
	@Test
	fun testConcurrentImportPerformance() = runTest {
		// Create JSON with 50 sources
		val sources = (1..50).map { index ->
			"""
			{
				"bookSourceName": "Test Source $index",
				"bookSourceUrl": "https://example$index.com",
				"enabled": true,
				"ruleSearch": {
					"bookList": "div.book",
					"name": "h2@text",
					"bookUrl": "a@href"
				}
			}
			""".trimIndent()
		}
		
		val jsonContent = "[${sources.joinToString(",")}]"
		
		// Mock DAO
		val dao = mock(JsonSourceDao::class.java)
		`when`(dao.observeAll()).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))
		
		val manager = JsonSourceManager(dao)
		
		// Measure import time
		val importTime = measureTimeMillis {
			val result = manager.importLegadoJson(jsonContent)
			assertTrue(result.isSuccess)
		}
		
		println("Time to import 50 sources: ${importTime}ms")
		
		// Import should be reasonably fast (< 5 seconds)
		// Note: Actual time depends on validation and ID generation
		assertTrue("Importing 50 sources should complete in reasonable time", importTime < 5000)
	}
	
	/**
	 * Test cache effectiveness with repeated queries
	 * 
	 * Validates that the source cache improves performance
	 * for repeated queries.
	 */
	@Test
	fun testCacheEffectiveness() = runTest {
		val testSource = createTestSource(
			id = "JSON_LEGADO_TEST",
			name = "Test Source",
			type = JsonSourceType.LEGADO
		)
		
		// Mock DAO
		val dao = mock(JsonSourceDao::class.java)
		`when`(dao.getById("JSON_LEGADO_TEST")).thenReturn(testSource)
		
		val manager = JsonSourceManager(dao)
		
		// First query - cache miss
		val firstQueryTime = measureTimeMillis {
			val source = manager.getById("JSON_LEGADO_TEST")
			assertNotNull(source)
		}
		
		// Subsequent queries - cache hits
		val cachedQueryTime = measureTimeMillis {
			repeat(100) {
				val source = manager.getById("JSON_LEGADO_TEST")
				assertNotNull(source)
			}
		}
		
		println("First query time: ${firstQueryTime}ms")
		println("100 cached queries time: ${cachedQueryTime}ms")
		
		// Verify DAO was only called once (first query)
		verify(dao, times(1)).getById("JSON_LEGADO_TEST")
		
		// Cached queries should be much faster than 100 individual DB queries
		assertTrue("Cached queries should be fast", cachedQueryTime < firstQueryTime * 10)
	}
	
	/**
	 * Test batch operations performance
	 * 
	 * Validates that batch operations are more efficient than
	 * individual operations.
	 */
	@Test
	fun testBatchOperationsPerformance() = runTest {
		val sourceIds = (1..50).map { "JSON_LEGADO_SOURCE_$it" }
		
		// Mock DAO
		val dao = mock(JsonSourceDao::class.java)
		
		val manager = JsonSourceManager(dao)
		
		// Measure batch toggle time
		val batchTime = measureTimeMillis {
			manager.toggleSourcesBatch(sourceIds, true)
		}
		
		println("Batch toggle 50 sources: ${batchTime}ms")
		
		// Verify batch method was called once
		verify(dao, times(1)).setEnabledBatch(any(), any(), any())
		
		// Batch operation should be fast
		assertTrue("Batch operation should be fast", batchTime < 1000)
	}
	
	/**
	 * Test memory usage with many sources
	 * 
	 * Validates that the system doesn't consume excessive memory
	 * when loading many sources.
	 */
	@Test
	fun testMemoryUsageWithManySources() = runTest {
		// Create 200 sources with realistic config sizes
		val sources = (1..200).map { index ->
			createTestSource(
				id = "JSON_LEGADO_SOURCE_$index",
				name = "Test Source $index",
				type = JsonSourceType.LEGADO,
				configSize = 5000 // ~5KB per source
			)
		}
		
		// Mock DAO
		val dao = mock(JsonSourceDao::class.java)
		`when`(dao.observeAll()).thenReturn(kotlinx.coroutines.flow.flowOf(sources))
		
		val manager = JsonSourceManager(dao)
		
		// Get memory before loading
		System.gc()
		val memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
		
		// Load all sources
		val loadedSources = manager.observeAllJsonSources().first()
		assertEquals(200, loadedSources.size)
		
		// Get memory after loading
		val memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
		val memoryUsed = (memoryAfter - memoryBefore) / 1024 / 1024 // Convert to MB
		
		println("Memory used for 200 sources: ${memoryUsed}MB")
		
		// Memory usage should be reasonable (< 50MB for 200 sources)
		// Note: This is a rough estimate and can vary
		assertTrue("Memory usage should be reasonable", memoryUsed < 50)
	}
	
	/**
	 * Helper function to create a test source
	 */
	private fun createTestSource(
		id: String,
		name: String,
		type: JsonSourceType,
		configSize: Int = 1000
	): JsonSourceEntity {
		// Create a config string of approximately the specified size
		val config = """
		{
			"bookSourceName": "$name",
			"bookSourceUrl": "https://example.com",
			"enabled": true,
			"ruleSearch": {
				"bookList": "div.book",
				"name": "h2@text",
				"bookUrl": "a@href"
			},
			"padding": "${"x".repeat(maxOf(0, configSize - 200))}"
		}
		""".trimIndent()
		
		return JsonSourceEntity(
			id = id,
			name = name,
			type = type,
			config = config,
			enabled = true,
			createdAt = System.currentTimeMillis(),
			updatedAt = System.currentTimeMillis(),
			lastUsedAt = 0,
			isPinned = false
		)
	}
	*/
}
