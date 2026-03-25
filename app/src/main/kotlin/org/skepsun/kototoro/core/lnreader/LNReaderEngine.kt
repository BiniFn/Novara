package org.skepsun.kototoro.core.lnreader

import android.content.Context
import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.FunctionBinding
import kotlinx.coroutines.Dispatchers

/**
 * QuickJS-based JavaScript engine for executing LNReader plugins.
 * Mirrors IReader's JSEngine — manages a QuickJS context with injected native bridges.
 * 
 * Uses `com.dokar.quickjs` (dokar3/quickjs-kt) — same library already used by
 * JsContentRepository and TVBoxQuickJsSpiderRuntime in this project.
 */
class LNReaderEngine(
	private val context: Context,
	private val fetchBridge: LNReaderFetchBridge
) {
	companion object {
		private const val TAG = "LNReaderEngine"
		private const val MAX_STACK_SIZE = 1L shl 20   // 1MB
		private const val MEMORY_LIMIT = 64L shl 20    // 64MB
	}
	
	/**
	 * Load a LNReader plugin and set up its execution environment.
	 * Returns a configured QuickJs instance ready to call plugin methods.
	 * 
	 * The caller should use the returned QuickJs in a coroutine scope via `qjs.use { ... }`.
	 *
	 * @param jsCode The compiled JS plugin bundle
	 * @param pluginId Plugin identifier (used for global variable naming)
	 */
	suspend fun createPluginContext(jsCode: String, pluginId: String): QuickJs {
		val qjs = QuickJs.create(jobDispatcher = Dispatchers.Default)
		qjs.maxStackSize = MAX_STACK_SIZE
		qjs.memoryLimit = MEMORY_LIMIT
		
		try {
			// 1. Register native fetch bridge
			registerFetchBridge(qjs)
			
			// 2. Register console polyfill
			registerConsole(qjs)
			
			// 3. Load htmlparser2 polyfill from assets (if available)
			loadHtmlParserPolyfill(qjs)
			
			// 4. Load the plugin code
			qjs.evaluate<Any?>(jsCode, "<lnreader-plugin>")
			
			// 5. Store plugin instance in global scope
			val sanitizedId = pluginId.replace(Regex("[^a-zA-Z0-9_]"), "_")
			qjs.evaluate<Any?>(
				"""
				(function() {
					var plugin = (typeof exports !== 'undefined' && exports.default) || 
					             (typeof exports !== 'undefined' && exports) ||
					             (typeof module !== 'undefined' && module.exports && module.exports.default) ||
					             (typeof module !== 'undefined' && module.exports);
					if (plugin && typeof plugin === 'function') {
						plugin = new plugin();
					}
					globalThis.__plugin_${sanitizedId} = plugin;
				})();
				""".trimIndent(),
				"<plugin-init>"
			)
			
			Log.d(TAG, "Plugin $pluginId loaded successfully")
			return qjs
		} catch (e: Exception) {
			qjs.close()
			Log.e(TAG, "Failed to load plugin $pluginId", e)
			throw LNReaderJSException("Failed to load plugin $pluginId: ${e.message}", e)
		}
	}
	
	/**
	 * Register the native fetch function bridge.
	 * JS code calls fetchApi(url, init) which delegates to OkHttp.
	 */
	private suspend fun registerFetchBridge(qjs: QuickJs) {
		// Register __nativeFetch as a native function
		qjs.evaluate<Any?>(
			"""
			var __fetchBridgeResults = {};
			var __fetchBridgeNextId = 0;
			""".trimIndent(),
			"<fetch-init>"
		)
		
		// Inject fetchApi wrapper that uses synchronous __nativeFetch
		val fetchScript = fetchBridge.toJavaScriptFunction()
		qjs.evaluate<Any?>(fetchScript, "<fetch-bridge>")
	}
	
	/**
	 * Register console.log/warn/error polyfill.
	 */
	private suspend fun registerConsole(qjs: QuickJs) {
		qjs.evaluate<Any?>(
			"""
			var console = {
				log: function() { /* logged via native */ },
				warn: function() { /* logged via native */ },
				error: function() { /* logged via native */ },
				info: function() { /* logged via native */ },
				debug: function() { /* logged via native */ }
			};
			""".trimIndent(),
			"<console>"
		)
	}
	
	/**
	 * Load htmlparser2 polyfill from assets.
	 * LNReader plugins import { Parser } from 'htmlparser2'.
	 */
	private suspend fun loadHtmlParserPolyfill(qjs: QuickJs) {
		try {
			val htmlParserJs = context.assets.open("lnreader/htmlparser2.min.js")
				.bufferedReader().use { it.readText() }
			qjs.evaluate<Any?>(htmlParserJs, "<htmlparser2>")
			Log.d(TAG, "htmlparser2 polyfill loaded")
		} catch (e: Exception) {
			Log.w(TAG, "htmlparser2 polyfill not found in assets, plugins requiring it may fail", e)
		}
		
		// Also set up module resolution stubs for common LNReader imports
		qjs.evaluate<Any?>(
			"""
			// Module stubs for LNReader plugin imports
			if (typeof globalThis.require === 'undefined') {
				globalThis.require = function(name) {
					if (name === 'htmlparser2' && typeof htmlparser2 !== 'undefined') return htmlparser2;
					if (name === 'cheerio' && typeof cheerio !== 'undefined') return cheerio;
					return {};
				};
			}
			// CommonJS module support
			if (typeof globalThis.exports === 'undefined') {
				globalThis.exports = {};
			}
			if (typeof globalThis.module === 'undefined') {
				globalThis.module = { exports: globalThis.exports };
			}
			""".trimIndent(),
			"<module-stubs>"
		)
	}
}

/**
 * Exception thrown by LNReader JS engine operations.
 */
class LNReaderJSException(
	message: String,
	cause: Throwable? = null
) : Exception(message, cause)
