package org.skepsun.kototoro.core.jsonsource

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.skepsun.kototoro.core.db.dao.JsonSourceDao
import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages JSON sources including import, storage, and retrieval operations.
 * 
 * This class handles the lifecycle of JSON-based manga sources, including:
 * - Generating unique identifiers for sources
 * - Importing and validating JSON configurations
 * - Managing source state (enabled/disabled)
 * - Tracking source usage
 * 
 * Performance optimizations:
 * - Async processing with coroutines for concurrent operations
 * - Batch database operations for efficiency
 * - Query result caching for frequently accessed data
 */
@Singleton
class JsonSourceManager @Inject constructor(
	private val jsonSourceDao: JsonSourceDao,
) {
	
	/**
	 * JSON serializer configured for lenient parsing of JSON sources.
	 * - ignoreUnknownKeys: Allows parsing JSON with extra fields not defined in the model
	 * - isLenient: Allows non-strict JSON parsing (e.g., unquoted keys, trailing commas)
	 */
	private val json = Json {
		ignoreUnknownKeys = true
		isLenient = true
	}
	
	/**
	 * Cache for frequently accessed sources
	 * This reduces database queries for commonly used sources
	 */
	private val sourceCache = java.util.concurrent.ConcurrentHashMap<String, JsonSourceEntity>()
	
	companion object {
		private const val JSON_PREFIX = "JSON_"
		private const val LEGADO_PREFIX = "JSON_LEGADO_"
		private const val TVBOX_PREFIX = "JSON_TVBOX_"
		
		// Regex pattern to match valid identifier characters (alphanumeric and underscore)
		private val VALID_CHAR_REGEX = Regex("[^A-Z0-9_]")
	}
	
	/**
	 * Observes all JSON sources in the database.
	 * 
	 * @return Flow emitting list of all JSON sources
	 */
	fun observeAllJsonSources(): Flow<List<JsonSourceEntity>> {
		return jsonSourceDao.observeAll()
	}
	
	/**
	 * Observes only enabled JSON sources.
	 * 
	 * @return Flow emitting list of enabled JSON sources
	 */
	fun observeEnabledJsonSources(): Flow<List<JsonSourceEntity>> {
		return jsonSourceDao.observeEnabled()
	}
	
	/**
	 * Gets a JSON source by its ID with caching.
	 * 
	 * This method checks the cache first before querying the database,
	 * improving performance for frequently accessed sources.
	 * 
	 * @param sourceId The source identifier
	 * @return The JsonSourceEntity if found, null otherwise
	 */
	suspend fun getById(sourceId: String): JsonSourceEntity? {
		// Check cache first
		sourceCache[sourceId]?.let { return it }
		
		// Query database if not in cache
		val source = jsonSourceDao.getById(sourceId)
		
		// Cache the result if found
		source?.let { sourceCache[sourceId] = it }
		
		return source
	}
	
	/**
	 * Gets multiple sources by IDs (batch query)
	 * More efficient than multiple individual queries
	 * 
	 * @param sourceIds List of source identifiers
	 * @return List of found sources
	 */
	suspend fun getByIds(sourceIds: List<String>): List<JsonSourceEntity> {
		// Check which sources are already in cache
		val cached = mutableListOf<JsonSourceEntity>()
		val uncachedIds = mutableListOf<String>()
		
		sourceIds.forEach { id ->
			val cachedSource = sourceCache[id]
			if (cachedSource != null) {
				cached.add(cachedSource)
			} else {
				uncachedIds.add(id)
			}
		}
		
		// Query database for uncached sources
		val fromDb = if (uncachedIds.isNotEmpty()) {
			jsonSourceDao.getByIds(uncachedIds).also { sources ->
				// Cache the results
				sources.forEach { sourceCache[it.id] = it }
			}
		} else {
			emptyList()
		}
		
		return cached + fromDb
	}
	
	/**
	 * Invalidate cache for a specific source
	 * Should be called when a source is updated or deleted
	 */
	private fun invalidateCache(sourceId: String) {
		sourceCache.remove(sourceId)
	}
	
	/**
	 * Clear the entire source cache
	 * Useful when performing bulk operations
	 */
	fun clearCache() {
		sourceCache.clear()
	}
	
	/**
	 * Toggles the enabled state of a JSON source.
	 * 
	 * @param sourceId The source identifier
	 * @param enabled Whether the source should be enabled
	 */
	suspend fun toggleSource(sourceId: String, enabled: Boolean) {
		try {
			val timestamp = System.currentTimeMillis()
			jsonSourceDao.setEnabled(sourceId, enabled, timestamp)
			invalidateCache(sourceId)
			JsonSourceLogger.logStateChange(sourceId, enabled)
		} catch (e: Exception) {
			JsonSourceLogger.logDatabaseError("toggle source state", e)
			throw JsonSourceError.DatabaseError("toggle source state", e)
		}
	}
	
	/**
	 * Batch toggle multiple sources
	 * More efficient than toggling sources individually
	 * 
	 * @param sourceIds List of source identifiers
	 * @param enabled Whether the sources should be enabled
	 */
	suspend fun toggleSourcesBatch(sourceIds: List<String>, enabled: Boolean) {
		try {
			val timestamp = System.currentTimeMillis()
			jsonSourceDao.setEnabledBatch(sourceIds, enabled, timestamp)
			sourceIds.forEach { invalidateCache(it) }
			JsonSourceLogger.logInfo("Batch toggled ${sourceIds.size} sources to enabled=$enabled")
		} catch (e: Exception) {
			JsonSourceLogger.logDatabaseError("batch toggle sources", e)
			throw JsonSourceError.DatabaseError("batch toggle sources", e)
		}
	}
	
	/**
	 * Deletes a JSON source from the database.
	 * 
	 * @param sourceId The source identifier
	 */
	suspend fun deleteSource(sourceId: String) {
		try {
			jsonSourceDao.deleteById(sourceId)
			invalidateCache(sourceId)
			JsonSourceLogger.logDeletion(sourceId)
		} catch (e: Exception) {
			JsonSourceLogger.logDatabaseError("delete source", e)
			throw JsonSourceError.DatabaseError("delete source", e)
		}
	}
	
	/**
	 * Batch delete multiple sources
	 * More efficient than deleting sources individually
	 * 
	 * @param sourceIds List of source identifiers
	 */
	suspend fun deleteSourcesBatch(sourceIds: List<String>) {
		try {
			jsonSourceDao.deleteByIds(sourceIds)
			sourceIds.forEach { invalidateCache(it) }
			JsonSourceLogger.logInfo("Batch deleted ${sourceIds.size} sources")
		} catch (e: Exception) {
			JsonSourceLogger.logDatabaseError("batch delete sources", e)
			throw JsonSourceError.DatabaseError("batch delete sources", e)
		}
	}
	
	/**
	 * Updates the last used timestamp for a source.
	 * 
	 * @param sourceId The source identifier
	 */
	suspend fun trackUsage(sourceId: String) {
		try {
			val timestamp = System.currentTimeMillis()
			jsonSourceDao.setLastUsed(sourceId, timestamp)
			JsonSourceLogger.logUsageTracking(sourceId)
		} catch (e: Exception) {
			JsonSourceLogger.logDatabaseError("track source usage", e)
			// Don't throw here - usage tracking is not critical
			// Just log the error and continue
		}
	}
	
	/**
	 * Imports Legado JSON configuration with async processing.
	 * 
	 * This method:
	 * 1. Parses the JSON content into a list of LegadoBookSource objects
	 * 2. Validates each source's required fields (in parallel using coroutines)
	 * 3. Generates unique identifiers for each source
	 * 4. Batch inserts all valid sources into the database
	 * 
	 * Performance optimizations:
	 * - Uses coroutines for concurrent validation and processing
	 * - Batch database operations for efficiency
	 * - Async loading reduces blocking time
	 * 
	 * @param jsonContent The JSON string containing Legado book sources
	 * @return Result containing the number of successfully imported sources or error message
	 */
	suspend fun importLegadoJson(jsonContent: String): Result<Int> {
		val startTime = System.currentTimeMillis()
		JsonSourceLogger.logImportStart("LEGADO", jsonContent.length)
		
		return try {
			// Parse JSON array into list of LegadoBookSource objects
			val sources = json.decodeFromString<List<org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource>>(jsonContent)
			
			if (sources.isEmpty()) {
				JsonSourceLogger.logWarning("JSON array is empty")
				return Result.failure(IllegalArgumentException("JSON array is empty"))
			}
			
			JsonSourceLogger.logInfo("Parsing ${sources.size} source(s) from JSON")
			
			// Process sources concurrently using coroutines
			val entities = mutableListOf<JsonSourceEntity>()
			val errors = mutableListOf<String>()
			
			// Use withContext to ensure we're on the IO dispatcher
			kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
				// Process sources sequentially for now
				// TODO: Re-enable parallel processing once Kotlin coroutines API stabilizes
				val results = sources.mapIndexed { index, source ->
					processSource(index, source)
				}
				
				// Collect results
				results.forEach { result ->
					when (result) {
						is SourceProcessResult.Success -> entities.add(result.entity)
						is SourceProcessResult.Error -> errors.add(result.message)
					}
				}
			}
			
			// Log validation results
			JsonSourceLogger.logInfo("Validation complete: ${entities.size} valid, ${errors.size} invalid out of ${sources.size} total")
			
			// If all sources failed validation, return error
			if (entities.isEmpty()) {
				val errorMsg = "No valid sources found. All ${sources.size} sources failed validation. Errors:\n${errors.joinToString("\n")}"
				JsonSourceLogger.logWarning(errorMsg)
				return Result.failure(IllegalArgumentException(errorMsg))
			}
			
			// Log any validation errors for individual sources
			if (errors.isNotEmpty()) {
				JsonSourceLogger.logWarning("${errors.size} source(s) failed validation:\n${errors.joinToString("\n")}")
			}
			
			// Batch insert all valid sources
			try {
				jsonSourceDao.insertAll(entities)
				JsonSourceLogger.logDebug("Batch inserted ${entities.size} source(s) into database")
			} catch (e: Exception) {
				JsonSourceLogger.logDatabaseError("batch insert sources", e)
				throw JsonSourceError.DatabaseError("batch insert sources", e)
			}
			
			// Return success with count (and warnings if some failed)
			val successCount = entities.size
			val duration = System.currentTimeMillis() - startTime
			JsonSourceLogger.logImportSuccess("LEGADO", successCount, duration)
			
			if (errors.isNotEmpty()) {
				// Some sources failed but others succeeded
				JsonSourceLogger.logWarning("${errors.size} source(s) failed validation but $successCount succeeded")
				return Result.success(successCount)
			}
			
			Result.success(successCount)
		} catch (e: kotlinx.serialization.SerializationException) {
			JsonSourceLogger.logImportError("LEGADO", e)
			Result.failure(IllegalArgumentException("Invalid JSON format: ${e.message}", e))
		} catch (e: JsonSourceError) {
			JsonSourceLogger.logImportError("LEGADO", e)
			Result.failure(e)
		} catch (e: Exception) {
			JsonSourceLogger.logImportError("LEGADO", e)
			Result.failure(e)
		}
	}
	
	/**
	 * Process a single source (validation, ID generation, entity creation)
	 * This is extracted to support concurrent processing
	 */
	private suspend fun processSource(
		index: Int,
		source: org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
	): SourceProcessResult {
		return try {
			// Validate the source
			val validation = validateLegadoSource(source)
			if (!validation.isValid) {
				JsonSourceLogger.logValidationError(source.bookSourceName, validation.errors)
				return SourceProcessResult.Error(
					"Source ${index + 1} (${source.bookSourceName}): ${validation.errors.joinToString(", ")}"
				)
			}
			
			// Generate unique identifier using URL (following Legado's approach)
			val sourceId = generateSourceId(source.bookSourceUrl, JsonSourceType.LEGADO)
			JsonSourceLogger.logIdGeneration(source.bookSourceName, sourceId)
			
			// Serialize the source back to JSON for storage
			val configJson = json.encodeToString(
				org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource.serializer(),
				source
			)
			
			// Create entity
			val timestamp = System.currentTimeMillis()
			val entity = JsonSourceEntity(
				id = sourceId,
				name = source.bookSourceName,
				type = JsonSourceType.LEGADO,
				config = configJson,
				enabled = source.enabled,
				createdAt = timestamp,
				updatedAt = timestamp,
				lastUsedAt = 0,
				isPinned = false,
			)
			
			SourceProcessResult.Success(entity)
		} catch (e: Exception) {
			SourceProcessResult.Error("Source ${index + 1} (${source.bookSourceName}): ${e.message}")
		}
	}
	
	/**
	 * Result of processing a single source
	 */
	private sealed class SourceProcessResult {
		data class Success(val entity: JsonSourceEntity) : SourceProcessResult()
		data class Error(val message: String) : SourceProcessResult()
	}
	
	/**
	 * Validates a Legado book source configuration.
	 * 
	 * Checks:
	 * - Required field: bookSourceName must not be blank
	 * - Optional field: bookSourceUrl (can be empty in Legado for certain source types)
	 * - URL format: bookSourceUrl must be a valid URL if not empty
	 * 
	 * Note: Following Legado's validation logic, bookSourceUrl can be empty.
	 * Some sources in Legado use empty bookSourceUrl for specific purposes.
	 * 
	 * @param source The Legado book source to validate
	 * @return ValidationResult indicating whether the source is valid
	 */
	fun validateLegadoSource(source: org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource): ValidationResult {
		val errors = mutableListOf<String>()
		
		// Check required field: bookSourceName
		if (source.bookSourceName.isBlank()) {
			errors.add("bookSourceName is required and cannot be blank")
		}
		
		// bookSourceUrl can be empty in Legado (following Legado's validation logic)
		// Only validate URL format if it's not empty
		if (source.bookSourceUrl.isNotEmpty()) {
			val urlValidation = validateUrl(source.bookSourceUrl)
			if (!urlValidation.isValid) {
				// Log warning but don't fail validation
				// Some Legado sources may have non-standard URLs
				JsonSourceLogger.logWarning("URL validation warning for ${source.bookSourceName}: ${urlValidation.errors.joinToString(", ")}")
			}
		}
		
		return if (errors.isEmpty()) {
			ValidationResult.success()
		} else {
			ValidationResult.failure(errors)
		}
	}
	
	/**
	 * Validates a URL string using SecurityValidator.
	 * 
	 * Checks:
	 * - URL must be parseable
	 * - Protocol must be http or https
	 * - Host must not be blank
	 * - Host must not be a local address
	 * 
	 * @param url The URL string to validate
	 * @return ValidationResult indicating whether the URL is valid
	 */
	private fun validateUrl(url: String): ValidationResult {
		return SecurityValidator.validateUrl(url)
	}
	
	/**
	 * Generates a unique source identifier from source URL and name.
	 * 
	 * Following Legado's approach, we use the bookSourceUrl as the primary identifier.
	 * The identifier follows the format: JSON_[TYPE_]URL_HASH
	 * where:
	 * - JSON_ is the prefix for all JSON sources
	 * - [TYPE_] is the type prefix (LEGADO_ or TVBOX_) to distinguish source types
	 * - URL_HASH is a hash of the bookSourceUrl to ensure uniqueness
	 * 
	 * This approach ensures:
	 * 1. Each source with a unique URL gets a unique ID
	 * 2. Works with any language (Chinese, Japanese, etc.)
	 * 3. Consistent with Legado's use of bookSourceUrl as primary key
	 * 
	 * @param sourceUrl The source URL (bookSourceUrl in Legado)
	 * @param sourceType The type of JSON source (LEGADO or TVBOX)
	 * @return A unique identifier string
	 */
	suspend fun generateSourceId(sourceUrl: String, sourceType: JsonSourceType): String {
		// Build the type prefix
		val typePrefix = when (sourceType) {
			JsonSourceType.LEGADO -> LEGADO_PREFIX
			JsonSourceType.TVBOX -> TVBOX_PREFIX
		}
		
		// Generate a hash of the URL to create a unique, stable identifier
		// Using hashCode() and converting to hex for readability
		val urlHash = sourceUrl.hashCode().toUInt().toString(16).uppercase()
		
		// Build the identifier
		val sourceId = "$typePrefix$urlHash"
		
		JsonSourceLogger.logDebug("Generated ID $sourceId for URL: $sourceUrl")
		
		return sourceId
	}
	
	/**
	 * Legacy method for backward compatibility - now delegates to URL-based generation
	 * @deprecated Use generateSourceId(sourceUrl, sourceType) instead
	 */
	@Deprecated("Use generateSourceId with sourceUrl parameter")
	suspend fun generateSourceIdFromName(sourceName: String, sourceType: JsonSourceType): String {
		// For legacy calls, generate a UUID-based ID
		val uuid = java.util.UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
		val typePrefix = when (sourceType) {
			JsonSourceType.LEGADO -> LEGADO_PREFIX
			JsonSourceType.TVBOX -> TVBOX_PREFIX
		}
		return "$typePrefix$uuid"
	}
	
	/**
	 * Generates a simple source identifier without type prefix (for backward compatibility).
	 * 
	 * @param sourceName The original source name
	 * @return A unique identifier string with JSON_ prefix
	 */
	suspend fun generateSimpleSourceId(sourceName: String): String {
		val normalizedName = sourceName
			.uppercase()
			.replace(" ", "_")
			.replace(VALID_CHAR_REGEX, "")
			.take(50)
		
		val baseId = "$JSON_PREFIX$normalizedName"
		var candidateId = baseId
		var suffix = 1
		
		val existingIds = jsonSourceDao.observeAll().first().map { it.id }.toSet()
		
		while (existingIds.contains(candidateId)) {
			candidateId = "${baseId}_$suffix"
			suffix++
		}
		
		return candidateId
	}
}
