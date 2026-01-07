package org.skepsun.kototoro.core.jsonsource

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldStartWith
import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.skepsun.kototoro.core.db.dao.JsonSourceDao
import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.core.db.entity.JsonSourceType

/**
 * Property-based tests for JSON source identifier generation and type recognition.
 * 
 * Feature: runtime-json-parser
 * 
 * These tests validate the correctness properties defined in the design document:
 * - Property 1: JSON source identifier uniqueness
 * - Property 2: Source type identification consistency
 * - Property 10: Source identifier format specification
 */
class SourceIdentifierPropertyTest : StringSpec({
	
	/**
	 * Property 1: JSON Source Identifier Uniqueness
	 * Validates: Requirements 11.1, 11.2, 11.3
	 * 
	 * For any two different JSON sources, their identifiers must be different,
	 * even if source names are similar.
	 */
	"generated identifiers are unique for different source names".config(invocations = 100) {
		val mockDao = TestJsonSourceDao()
		val manager = JsonSourceManager(mockDao)
		
		checkAll(
			Arb.list(Arb.string(1..50), 2..10),
			Arb.enum<JsonSourceType>()
		) { sourceNames, sourceType ->
			val generatedIds = mutableSetOf<String>()
			
			for (name in sourceNames) {
				val id = manager.generateSourceId(name, sourceType)
				generatedIds.add(id)
				
				// Simulate adding to database for next iteration
				mockDao.addSource(JsonSourceEntity(
					id = id,
					name = name,
					type = sourceType,
					config = "{}",
					enabled = true,
					createdAt = System.currentTimeMillis(),
					updatedAt = System.currentTimeMillis()
				))
			}
			
			// All generated IDs should be unique (even for duplicate names)
			// The number of unique IDs should equal the total number of sources
			generatedIds.size shouldBe sourceNames.size
		}
	}
	
	/**
	 * Property 1 (variant): Duplicate names get unique identifiers with numeric suffixes
	 * Validates: Requirements 11.2, 11.3
	 */
	"duplicate source names get unique identifiers with numeric suffixes".config(invocations = 50) {
		val mockDao = TestJsonSourceDao()
		val manager = JsonSourceManager(mockDao)
		
		checkAll(
			Arb.string(1..30),
			Arb.enum<JsonSourceType>()
		) { sourceName, sourceType ->
			// Generate multiple IDs for the same source name
			val id1 = manager.generateSourceId(sourceName, sourceType)
			mockDao.addSource(JsonSourceEntity(
				id = id1,
				name = sourceName,
				type = sourceType,
				config = "{}",
				enabled = true,
				createdAt = System.currentTimeMillis(),
				updatedAt = System.currentTimeMillis()
			))
			
			val id2 = manager.generateSourceId(sourceName, sourceType)
			mockDao.addSource(JsonSourceEntity(
				id = id2,
				name = sourceName,
				type = sourceType,
				config = "{}",
				enabled = true,
				createdAt = System.currentTimeMillis(),
				updatedAt = System.currentTimeMillis()
			))
			
			val id3 = manager.generateSourceId(sourceName, sourceType)
			
			// All IDs should be different
			id1 shouldNotBe id2
			id2 shouldNotBe id3
			id1 shouldNotBe id3
			
			// Second and third IDs should have numeric suffixes
			id2 shouldMatch Regex(".*_\\d+$")
			id3 shouldMatch Regex(".*_\\d+$")
		}
	}
	
	/**
	 * Property 2: Source Type Identification Consistency
	 * Validates: Requirements 2.5, 11.1
	 * 
	 * For any source identifier, if it starts with "JSON_", then isJsonSource() must return true,
	 * and getSourceType() must return a JSON-related type.
	 */
	"source type identification is consistent with identifier prefix".config(invocations = 100) {
		val identifier = SourceTypeIdentifier()
		val mockDao = TestJsonSourceDao()
		val manager = JsonSourceManager(mockDao)
		
		checkAll(
			Arb.string(1..30),
			Arb.enum<JsonSourceType>()
		) { sourceName, sourceType ->
			val sourceId = manager.generateSourceId(sourceName, sourceType)
			
			// All generated JSON source IDs should start with JSON_
			sourceId shouldStartWith "JSON_"
			
			// isJsonSource should return true
			identifier.isJsonSource(sourceId) shouldBe true
			
			// getSourceType should return a JSON type
			val detectedType = identifier.getSourceType(sourceId)
			assert(detectedType == SourceType.JSON_LEGADO || detectedType == SourceType.JSON_TVBOX) {
				"Expected JSON type but got $detectedType for ID $sourceId"
			}
			
			// getJsonSourceType should return the correct type
			val jsonType = identifier.getJsonSourceType(sourceId)
			jsonType shouldNotBe null
		}
	}
	
	/**
	 * Property 2 (variant): Native sources are correctly identified
	 * Validates: Requirements 2.5, 11.1
	 */
	"native source identifiers are correctly identified".config(invocations = 50) {
		val identifier = SourceTypeIdentifier()
		
		val nativeSourceIds = listOf(
			"MANGADEX",
			"MANGANELO",
			"LOCAL_STORAGE",
			"ZLIBRARY",
			"NOVELIA"
		)
		
		for (sourceId in nativeSourceIds) {
			identifier.isJsonSource(sourceId) shouldBe false
			identifier.getSourceType(sourceId) shouldBe SourceType.NATIVE
			identifier.getJsonSourceType(sourceId) shouldBe null
		}
	}
	
	/**
	 * Property 10: Source Identifier Format Specification
	 * Validates: Requirements 11.1, 11.2
	 * 
	 * For any generated JSON source identifier, it must conform to the format:
	 * "JSON_" + uppercase alphanumeric characters and underscores
	 */
	"generated identifiers follow the correct format".config(invocations = 100) {
		val mockDao = TestJsonSourceDao()
		val manager = JsonSourceManager(mockDao)
		
		// Use alphanumeric strings to ensure we get valid identifiers
		checkAll<String, JsonSourceType>(
			Arb.string(1..50),
			Arb.enum()
		) { sourceName, sourceType ->
			// Skip if source name is empty or only special characters
			if (sourceName.isBlank()) return@checkAll
			
			val sourceId = manager.generateSourceId(sourceName, sourceType)
			
			// Must start with JSON_LEGADO_ or JSON_TVBOX_
			val expectedPrefix = when (sourceType) {
				JsonSourceType.LEGADO -> "JSON_LEGADO_"
				JsonSourceType.TVBOX -> "JSON_TVBOX_"
				JsonSourceType.JS -> "JSON_JS_"
			}
			sourceId shouldStartWith expectedPrefix
			
			// Must contain only uppercase letters, numbers, and underscores
			sourceId shouldMatch Regex("^JSON_(LEGADO|TVBOX)_[A-Z0-9_]+(_\\d+)?$")
		}
	}
	
	/**
	 * Property 10 (variant): Special characters are properly normalized
	 * Validates: Requirements 11.2
	 */
	"special characters in source names are properly normalized".config(invocations = 50) {
		val mockDao = TestJsonSourceDao()
		val manager = JsonSourceManager(mockDao)
		
		val testCases = listOf(
			"My Source!" to "MY_SOURCE",
			"Test@Source#123" to "TESTSOURCE123",  // Special chars removed
			"Source (v2.0)" to "SOURCE_V20",  // Parentheses and dot removed
			"中文源" to "SOURCE",  // Non-ASCII characters removed, defaults to SOURCE
			"Source   With   Spaces" to "SOURCE_WITH_SPACES",
			"___Multiple___Underscores___" to "MULTIPLE_UNDERSCORES",
			"@#$%" to "SOURCE"  // All special chars removed, defaults to SOURCE
		)
		
		for ((input, expectedNormalized) in testCases) {
			val sourceId = manager.generateSourceId(input, JsonSourceType.LEGADO)
			
			// Should contain the normalized name
			assert(sourceId.contains(expectedNormalized)) {
				"Expected $sourceId to contain $expectedNormalized for input '$input'"
			}
			
			// Should still follow the format
			sourceId shouldMatch Regex("^JSON_LEGADO_[A-Z0-9_]+(_\\d+)?$")
		}
	}
	
	/**
	 * Property: Type labels are consistent with source types
	 * Validates: Requirements 2.5, 11.1
	 */
	"type labels are consistent with detected source types".config(invocations = 50) {
		val identifier = SourceTypeIdentifier()
		val mockDao = TestJsonSourceDao()
		val manager = JsonSourceManager(mockDao)
		
		checkAll(
			Arb.string(1..30),
			Arb.enum<JsonSourceType>()
		) { sourceName, sourceType ->
			val sourceId = manager.generateSourceId(sourceName, sourceType)
			val detectedType = identifier.getSourceType(sourceId)
			val label = identifier.getSourceTypeLabel(sourceId)
			
			// Label should match the detected type
			when (detectedType) {
				SourceType.NATIVE -> label shouldBe "原生源"
				SourceType.JSON_LEGADO -> label shouldBe "JSON 源 (Legado)"
				SourceType.JSON_TVBOX -> label shouldBe "JSON 源 (TVBox)"
				SourceType.JSON_JS -> label shouldBe "JavaScript 源"
				SourceType.MIHON -> label shouldBe "Mihon 扩展"
				SourceType.ANIYOMI -> label shouldBe "Aniyomi 扩展"
				SourceType.EXTERNAL -> label shouldBe "外部源"
			}
		}
	}
})

/**
 * Mock implementation of JsonSourceDao for testing purposes.
 * This allows us to test the JsonSourceManager without a real database.
 */
private class TestJsonSourceDao : JsonSourceDao {
	private val sources = mutableListOf<JsonSourceEntity>()
	private val flowState = MutableStateFlow<List<JsonSourceEntity>>(emptyList())
	
	fun addSource(source: JsonSourceEntity) {
		sources.add(source)
		flowState.value = sources.toList()
	}
	
	override fun observeEnabled() = flowState
	override fun observeAll() = flowState
	
	override suspend fun getById(id: String) = sources.firstOrNull { it.id == id }
	
	override suspend fun insert(source: JsonSourceEntity) {
		sources.add(source)
		flowState.value = sources.toList()
	}
	
	override suspend fun insertAll(sources: List<JsonSourceEntity>) {
		this.sources.addAll(sources)
		flowState.value = this.sources.toList()
	}
	
	override suspend fun setEnabled(id: String, enabled: Boolean, timestamp: Long) {
		val index = sources.indexOfFirst { it.id == id }
		if (index >= 0) {
			sources[index] = sources[index].copy(enabled = enabled, updatedAt = timestamp)
			flowState.value = sources.toList()
		}
	}
	
	override suspend fun setLastUsed(id: String, timestamp: Long) {
		val index = sources.indexOfFirst { it.id == id }
		if (index >= 0) {
			sources[index] = sources[index].copy(lastUsedAt = timestamp)
			flowState.value = sources.toList()
		}
	}
	
	override suspend fun delete(source: JsonSourceEntity) {
		sources.remove(source)
		flowState.value = sources.toList()
	}
	
	override suspend fun deleteById(id: String) {
		sources.removeIf { it.id == id }
		flowState.value = sources.toList()
	}
	
	override fun observeByType(type: JsonSourceType) = flowState
	override fun observeEnabledByType(type: JsonSourceType) = flowState
	override fun observeRecentlyUsed(limit: Int) = flowState
	override suspend fun getByIds(ids: List<String>) = sources.filter { it.id in ids }
	override suspend fun countByType(type: JsonSourceType) = sources.count { it.type == type }
	override suspend fun countEnabled() = sources.count { it.enabled }
	override suspend fun setEnabledBatch(ids: List<String>, enabled: Boolean, timestamp: Long) {
		ids.forEach { id ->
			val index = sources.indexOfFirst { it.id == id }
			if (index >= 0) {
				sources[index] = sources[index].copy(enabled = enabled, updatedAt = timestamp)
			}
		}
		flowState.value = sources.toList()
	}
	override suspend fun deleteByIds(ids: List<String>) {
		sources.removeIf { it.id in ids }
		flowState.value = sources.toList()
	}
	override suspend fun deleteByType(type: JsonSourceType) {
		sources.removeIf { it.type == type }
		flowState.value = sources.toList()
	}
}
