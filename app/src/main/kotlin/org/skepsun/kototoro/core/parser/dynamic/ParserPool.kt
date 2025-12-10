package org.skepsun.kototoro.core.parser.dynamic

import org.skepsun.kototoro.parsers.MangaParser
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pool for managing dynamic parser instances
 * 
 * This pool implements lazy loading - parsers are only created when first needed,
 * and are cached for reuse. This improves performance by:
 * - Avoiding upfront creation of all parsers
 * - Reusing parser instances across requests
 * - Supporting concurrent access from multiple threads
 * 
 * Performance characteristics:
 * - O(1) lookup for cached parsers
 * - Thread-safe concurrent access
 * - Lazy initialization reduces startup time
 */
@Singleton
class ParserPool @Inject constructor() {
	
	private val pool = ConcurrentHashMap<String, MangaParser>()
	
	/**
	 * Get a parser from the pool, or create it if not present
	 * 
	 * This method is thread-safe and uses lazy initialization.
	 * The factory function is only called if the parser is not in the pool.
	 * 
	 * @param sourceId The unique identifier for the source
	 * @param factory Function to create the parser if not cached
	 * @return The parser instance
	 */
	fun getOrCreate(sourceId: String, factory: () -> MangaParser): MangaParser {
		return pool.getOrPut(sourceId) {
			factory()
		}
	}
	
	/**
	 * Invalidate a parser, removing it from the pool
	 * 
	 * This should be called when a source configuration is updated,
	 * to ensure the next request gets a fresh parser with the new config.
	 * 
	 * @param sourceId The source identifier
	 */
	fun invalidate(sourceId: String) {
		pool.remove(sourceId)
	}
	
	/**
	 * Invalidate all parsers, clearing the entire pool
	 * 
	 * This can be used when performing bulk updates or during testing.
	 */
	fun invalidateAll() {
		pool.clear()
	}
	
	/**
	 * Get the number of parsers currently in the pool
	 */
	fun size(): Int = pool.size
	
	/**
	 * Check if a parser exists in the pool
	 */
	fun contains(sourceId: String): Boolean = pool.containsKey(sourceId)
	
	/**
	 * Get statistics about the parser pool
	 */
	fun getStats(): PoolStats {
		return PoolStats(
			size = pool.size,
			sourceIds = pool.keys.toList()
		)
	}
}

/**
 * Statistics about the parser pool
 */
data class PoolStats(
	val size: Int,
	val sourceIds: List<String>
)
