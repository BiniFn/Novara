package org.skepsun.kototoro.core.parser.dynamic

import android.util.Log

/**
 * Logger for dynamic parser operations
 * 
 * Provides structured logging for dynamic parser errors and operations
 */
object DynamicParserLogger {
	
	private const val TAG = "DynamicParser"
	
	/**
	 * Log an error during list page parsing
	 * 
	 * @param sourceId The source identifier
	 * @param url The URL being parsed
	 * @param rule The rule being executed
	 * @param error The exception that occurred
	 */
	fun logListPageError(sourceId: String, url: String, rule: String?, error: Throwable) {
		Log.e(TAG, "[$sourceId] Error parsing list page", error)
		Log.e(TAG, "  URL: $url")
		if (rule != null) {
			Log.e(TAG, "  Rule: $rule")
		}
	}
	
	/**
	 * Log an error during details parsing
	 * 
	 * @param sourceId The source identifier
	 * @param url The URL being parsed
	 * @param rule The rule being executed
	 * @param error The exception that occurred
	 */
	fun logDetailsError(sourceId: String, url: String, rule: String?, error: Throwable) {
		Log.e(TAG, "[$sourceId] Error parsing details", error)
		Log.e(TAG, "  URL: $url")
		if (rule != null) {
			Log.e(TAG, "  Rule: $rule")
		}
	}
	
	/**
	 * Log an error during chapter parsing
	 * 
	 * @param sourceId The source identifier
	 * @param url The URL being parsed
	 * @param rule The rule being executed
	 * @param error The exception that occurred
	 */
	fun logChapterError(sourceId: String, url: String, rule: String?, error: Throwable) {
		Log.e(TAG, "[$sourceId] Error parsing chapter", error)
		Log.e(TAG, "  URL: $url")
		if (rule != null) {
			Log.e(TAG, "  Rule: $rule")
		}
	}
	
	/**
	 * Log an error during content parsing
	 * 
	 * @param sourceId The source identifier
	 * @param url The URL being parsed
	 * @param rule The rule being executed
	 * @param error The exception that occurred
	 */
	fun logContentError(sourceId: String, url: String, rule: String?, error: Throwable) {
		Log.e(TAG, "[$sourceId] Error parsing content", error)
		Log.e(TAG, "  URL: $url")
		if (rule != null) {
			Log.e(TAG, "  Rule: $rule")
		}
	}
	
	/**
	 * Log a rule execution error
	 * 
	 * @param sourceId The source identifier
	 * @param rule The rule that failed
	 * @param error The exception that occurred
	 */
	fun logRuleError(sourceId: String, rule: String, error: Throwable) {
		Log.e(TAG, "[$sourceId] Rule execution failed: $rule", error)
	}
	
	/**
	 * Log a warning
	 * 
	 * @param sourceId The source identifier
	 * @param message The warning message
	 */
	fun logWarning(sourceId: String, message: String) {
		Log.w(TAG, "[$sourceId] $message")
	}
	
	/**
	 * Log an info message
	 * 
	 * @param sourceId The source identifier
	 * @param message The info message
	 */
	fun logInfo(sourceId: String, message: String) {
		Log.i(TAG, "[$sourceId] $message")
	}
	
	/**
	 * Log a debug message
	 * 
	 * @param sourceId The source identifier
	 * @param message The debug message
	 */
	fun logDebug(sourceId: String, message: String) {
		Log.d(TAG, "[$sourceId] $message")
	}
}
