package org.skepsun.kototoro.core.parser.dynamic

import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.core.jsonsource.ValidationResult
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.parser.rule.EnhancedRuleEngine
import org.skepsun.kototoro.parsers.MangaLoaderContext
import org.skepsun.kototoro.parsers.MangaParser
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating dynamic parser instances from JSON configurations
 * 
 * This factory creates parser instances at runtime based on JSON source configurations,
 * allowing users to add new sources without recompiling the application.
 * 
 * Performance optimizations:
 * - Uses ParserPool for lazy loading and caching
 * - Parsers are only created when first needed
 * - Cached parsers are reused across requests
 * 
 * TODO: This class is temporarily disabled until DynamicLegadoParser is refactored
 * to not extend PagedMangaParser (which requires MangaParserSource enum).
 */
@Singleton
class DynamicParserFactory @Inject constructor(
	private val ruleEngine: EnhancedRuleEngine,
	private val parserPool: ParserPool,
) {
	

	
	private val json = Json {
		ignoreUnknownKeys = true
		isLenient = true
	}
	
	/**
	 * Creates a dynamic parser instance from a JSON source entity
	 * 
	 * This method uses lazy loading - the parser is only created when first needed,
	 * and is cached in the parser pool for reuse.
	 * 
	 * @param source The JSON source entity containing the configuration
	 * @param context The manga loader context for network operations
	 * @return A MangaParser instance, or null if the source type is not supported
	 */
	fun createParser(
		source: JsonSourceEntity,
		context: MangaLoaderContext,
	): MangaParser? {
		return parserPool.getOrCreate(source.id) {
			when (source.type) {
				JsonSourceType.LEGADO -> createLegadoParser(source, context)
				JsonSourceType.TVBOX -> null // TVBox not yet implemented
			} ?: throw IllegalStateException("Failed to create parser for source ${source.id}")
		}
	}
	
	/**
	 * Invalidates a cached parser, forcing it to be recreated on next use
	 * 
	 * This should be called when a source configuration is updated.
	 * 
	 * @param sourceId The source identifier
	 */
	fun invalidateParser(sourceId: String) {
		parserPool.invalidate(sourceId)
	}
	
	/**
	 * Creates a Legado parser instance
	 * 
	 * @param source The JSON source entity
	 * @param context The manga loader context
	 * @return A DynamicLegadoParser instance
	 */
	private fun createLegadoParser(
		source: JsonSourceEntity,
		context: MangaLoaderContext,
	): MangaParser {
		// TODO: Temporarily return null until DynamicLegadoParser is refactored
		throw UnsupportedOperationException("DynamicLegadoParser is temporarily disabled")
		/*
		val config = json.decodeFromString<LegadoBookSource>(source.config)
		return DynamicLegadoParser(
			context = context,
			config = config,
			sourceId = source.id,
			ruleEngine = ruleEngine,
		)
		*/
	}
	
	/**
	 * Validates a JSON configuration
	 * 
	 * @param config The configuration object to validate
	 * @return ValidationResult indicating whether the configuration is valid
	 */
	fun validateConfig(config: Any): ValidationResult {
		return when (config) {
			is LegadoBookSource -> validateLegadoConfig(config)
			else -> ValidationResult.failure("Unsupported configuration type: ${config::class.simpleName}")
		}
	}
	
	/**
	 * Validates a Legado book source configuration
	 * 
	 * @param config The Legado configuration to validate
	 * @return ValidationResult with any validation errors
	 */
	private fun validateLegadoConfig(config: LegadoBookSource): ValidationResult {
		val errors = mutableListOf<String>()
		
		// Check required fields
		if (config.bookSourceName.isBlank()) {
			errors.add("bookSourceName is required")
		}
		
		if (config.bookSourceUrl.isBlank()) {
			errors.add("bookSourceUrl is required")
		}
		
		// Check that at least one rule is defined
		val hasAnyRule = config.ruleSearch != null ||
			config.ruleBookInfo != null ||
			config.ruleToc != null ||
			config.ruleContent != null
		
		if (!hasAnyRule) {
			errors.add("At least one rule (ruleSearch, ruleBookInfo, ruleToc, or ruleContent) must be defined")
		}
		
		return if (errors.isEmpty()) {
			ValidationResult.success()
		} else {
			ValidationResult.failure(errors)
		}
	}
}
