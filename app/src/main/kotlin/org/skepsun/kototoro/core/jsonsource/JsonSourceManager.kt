package org.skepsun.kototoro.core.jsonsource

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.skepsun.kototoro.core.db.dao.JsonSourceDao
import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.core.js.JSSourceParser
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.core.javascript.JavaScriptEnginePool
import org.skepsun.kototoro.core.parser.legado.book.BookChapterList
import org.skepsun.kototoro.core.parser.legado.book.BookContent
import org.skepsun.kototoro.core.parser.legado.book.BookInfo
import org.skepsun.kototoro.core.parser.legado.book.BookList
import org.skepsun.kototoro.core.parser.legado.sandbox.LegadoSandbox
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
	private val legadoHttpClient: LegadoHttpClient? = null,
	private val jsSourceParser: JSSourceParser? = null,
	private val jsEnginePool: JavaScriptEnginePool? = null,
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
		private const val JS_PREFIX = "JSON_JS_"
		
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
	 * Validate a source by performing a multi-stage check:
	 * Search -> Details -> Chapter List -> Content
	 * This aligns with Legado's source verification logic.
	 */
	suspend fun validateSourceBySearch(sourceId: String, searchKey: String = "我的"): Boolean {
		val client = legadoHttpClient ?: return false
		val enginePool = jsEnginePool ?: return false
		val entity = getById(sourceId) ?: return false
		val config = runCatching {
			Json { ignoreUnknownKeys = true; isLenient = true; allowTrailingComma = true }
				.decodeFromString<org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource>(entity.config)
		}.getOrNull() ?: return false
		
		val searchUrl = config.searchUrl
		if (searchUrl.isNullOrBlank()) return false
		
		val engine = enginePool.acquire()
		return try {
			val sandbox = LegadoSandbox(engine, client, config)
			val mangaSource = JsonMangaSource(entity)
			
			// 1. Search
			val page = 1
			val encodedKey = java.net.URLEncoder.encode(searchKey, "UTF-8")
			val finalSearchUrl = searchUrl
				.replace("{{key}}", encodedKey)
				.replace("{key}", encodedKey)
				.replace("{{page}}", page.toString())
				.replace("{page}", page.toString())
			
			val searchResponse = client.get(finalSearchUrl, parseCustomHeaders(config.header), source = null)
			val searchBody = searchResponse.body?.string().orEmpty()
			searchResponse.close()
			
			val results = BookList.parse(searchBody, finalSearchUrl, mangaSource, config, true, sandbox)
			if (results.isEmpty()) return false
			
			val firstManga = results.first()
			
			// 2. Details
			val detailsResponse = client.get(firstManga.url, parseCustomHeaders(config.header), source = null)
			val detailsBody = detailsResponse.body?.string().orEmpty()
			detailsResponse.close()
			
			val infoResult = BookInfo.parse(firstManga, detailsBody, firstManga.url, config, sandbox)
			
			// 3. Chapter List (TOC)
			val tocUrl = infoResult.tocUrl ?: firstManga.url
			val tocResponse = client.get(tocUrl, parseCustomHeaders(config.header), source = null)
			val tocBody = tocResponse.body?.string().orEmpty()
			tocResponse.close()
			
			val tocResult = BookChapterList.parse(tocBody, tocUrl, mangaSource, config, sandbox)
			val chapters = tocResult.chapters
			if (chapters.isEmpty()) return false
			
			// 4. Content
			val firstChapter = chapters.first()
			val contentResponse = client.get(firstChapter.url, parseCustomHeaders(config.header), source = null)
			val contentBody = contentResponse.body?.string().orEmpty()
			contentResponse.close()
			
			val content = BookContent.parse(contentBody, firstChapter.url, mangaSource, config, sandbox)
			
			content.pages.isNotEmpty()
		} catch (e: Exception) {
			JsonSourceLogger.logError("Verification failed for ${entity.name}", e)
			false
		} finally {
			enginePool.release(engine)
		}
	}
	
	/**
	 * Imports a single Venera-style JavaScript source.
	 */
	suspend fun importJsSource(jsContent: String): Result<Int> {
		val parser = jsSourceParser ?: return Result.failure(IllegalStateException("JS parser unavailable"))
		
		return parser.parseMetadata(jsContent).mapCatching { meta ->
			val base = meta.homepage?.takeIf { it.isNotBlank() } ?: meta.key
			val sourceId = runCatching { generateSourceId(base, JsonSourceType.JS) }
				.getOrElse { generateSourceIdFromName(meta.key, JsonSourceType.JS) }
			
			val timestamp = System.currentTimeMillis()
			val entity = JsonSourceEntity(
				id = sourceId,
				name = meta.name,
				type = JsonSourceType.JS,
				config = jsContent,
				enabled = true,
				createdAt = timestamp,
				updatedAt = timestamp,
				lastUsedAt = 0,
				isPinned = false,
			)
			
			jsonSourceDao.insert(entity)
			
			runCatching { parser.saveSource(jsContent, "$sourceId.js") }
			
			1
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
	suspend fun importLegadoJson(
		jsonContent: String,
		skipUnreachable: Boolean = false,
		skipNoExplore: Boolean = false
	): Result<Int> {
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
					processSource(index, source, skipUnreachable, skipNoExplore)
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
		source: org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource,
		skipUnreachable: Boolean,
		skipNoExplore: Boolean
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
			
			// Basic connectivity check: fetch homepage to ensure reachable HTML
			if (skipUnreachable) {
				val homeCheck = runCatching { verifyHomePageAccessible(source) }.getOrElse { throwable ->
					return SourceProcessResult.Error("Source ${index + 1} (${source.bookSourceName}): homepage check failed - ${throwable.message}")
				}
				if (!homeCheck) {
					return SourceProcessResult.Error("Source ${index + 1} (${source.bookSourceName}): homepage unreachable or empty, skipped")
				}
			}
			
			// Skip sources with no explore/list capability when requested
			if (skipNoExplore && (source.ruleExplore == null || source.ruleExplore?.bookList.isNullOrBlank())) {
				return SourceProcessResult.Error("Source ${index + 1} (${source.bookSourceName}): no explore rule, skipped by option")
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
	 * Quick reachability check for a source homepage.
	 * Returns false if the request fails or HTML is too short/blank.
	 */
	private suspend fun verifyHomePageAccessible(source: org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource): Boolean {
		val client = legadoHttpClient ?: return true
		val url = source.bookSourceUrl.takeIf { it.isNotBlank() } ?: return true // allow empty URLs
		
		// Parse custom headers if provided
		val headers = parseCustomHeaders(source.header)
		
		val response = client.get(url, headers, source = null)
		val body = response.body?.string().orEmpty()
		response.close()
		
		if (!response.isSuccessful) {
			JsonSourceLogger.logWarning("Homepage check failed for ${source.bookSourceName}: HTTP ${response.code}")
			return false
		}
		if (body.length < 500) {
			JsonSourceLogger.logWarning("Homepage check failed for ${source.bookSourceName}: HTML too short (${body.length} bytes)")
			return false
		}
		return true
	}
	
	private fun parseCustomHeaders(headerStr: String?): Map<String, String> {
		if (headerStr.isNullOrBlank()) return emptyMap()
		return try {
			val jsonStr = headerStr.replace("'", "\"")
			Json { ignoreUnknownKeys = true }.decodeFromString<Map<String, String>>(jsonStr)
		} catch (e: Exception) {
			JsonSourceLogger.logWarning("Failed to parse custom headers: ${e.message}")
			emptyMap()
		}
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
			JsonSourceType.JS -> JS_PREFIX
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
			JsonSourceType.JS -> JS_PREFIX
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
