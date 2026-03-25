package org.skepsun.kototoro.core.lnreader

import android.util.Log
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI

/**
 * Native fetch API bridge for LNReader JS plugins.
 * Mirrors IReader's JSFetchApi — registered as __nativeFetch in QuickJS context.
 * 
 * LNReader plugins call `fetchApi(url)` which resolves to this bridge.
 * Returns a map matching the Fetch Response interface: {ok, status, statusText, url, text, headers}
 */
class LNReaderFetchBridge(
	private val httpClient: OkHttpClient,
	private val pluginId: String
) {
	companion object {
		private const val TAG = "LNReaderFetchBridge"
		private const val DEFAULT_USER_AGENT =
			"Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
	}
	
	/**
	 * Performs an HTTP request matching the Fetch API contract.
	 * Called from JavaScript via native bridge.
	 * 
	 * @param url The URL to fetch
	 * @param init Optional RequestInit map (method, headers, body)
	 * @return Response map {ok, status, statusText, url, text, headers}
	 */
	fun fetch(url: String, init: Map<String, Any?>? = null): Map<String, Any?> {
		return try {
			Log.d(TAG, "[$pluginId] Fetching: $url")
			
			// Validate URL
			if (!isValidUrl(url)) {
				return errorResponse(url, 0, "Security Error", "Invalid URL: $url")
			}
			
			val method = (init?.get("method") as? String)?.uppercase() ?: "GET"
			val headersMap = extractHeaders(init)
			val body = extractBody(init)
			
			// Build OkHttp request
			val requestBuilder = Request.Builder()
				.url(url)
			
			// Add headers
			val headerBuilder = Headers.Builder()
			if (!headersMap.containsKey("User-Agent")) {
				headerBuilder.add("User-Agent", DEFAULT_USER_AGENT)
			}
			headersMap.forEach { (key, value) ->
				headerBuilder.add(key, value)
			}
			requestBuilder.headers(headerBuilder.build())
			
			// Set method and body
			when (method) {
				"GET" -> requestBuilder.get()
				"POST" -> {
					val contentType = headersMap["Content-Type"] ?: "application/x-www-form-urlencoded"
					val requestBody = (body ?: "").toRequestBody(contentType.toMediaType())
					requestBuilder.post(requestBody)
				}
				"PUT" -> {
					val contentType = headersMap["Content-Type"] ?: "application/x-www-form-urlencoded"
					val requestBody = (body ?: "").toRequestBody(contentType.toMediaType())
					requestBuilder.put(requestBody)
				}
				"DELETE" -> requestBuilder.delete()
				else -> requestBuilder.method(method, null)
			}
			
			// Execute request
			val response = httpClient.newCall(requestBuilder.build()).execute()
			val responseBody = response.body?.string() ?: ""
			val responseHeaders = mutableMapOf<String, String>()
			response.headers.forEach { (name, value) ->
				responseHeaders[name] = value
			}
			
			Log.d(TAG, "[$pluginId] Success: ${response.code} (${responseBody.length} bytes)")
			
			mapOf(
				"ok" to response.isSuccessful,
				"status" to response.code,
				"statusText" to (response.message.ifEmpty { "OK" }),
				"url" to url,
				"text" to responseBody,
				"headers" to responseHeaders
			)
		} catch (e: IOException) {
			Log.e(TAG, "[$pluginId] Network error: ${e.message}")
			errorResponse(url, 0, "Network Error", e.message ?: "Network error")
		} catch (e: Exception) {
			Log.e(TAG, "[$pluginId] Fatal error: ${e.message}")
			errorResponse(url, 0, "Fatal Error", "Fatal error: ${e.message}")
		}
	}
	
	/**
	 * Generate the JavaScript wrapper function for injection into QuickJS.
	 * Matches IReader's JSFetchApi.toJavaScriptFunction().
	 */
	fun toJavaScriptFunction(): String {
		return """
			function fetchApi(url, init) {
				var response = __nativeFetch(url, init || {});
				return {
					ok: response.ok,
					status: response.status,
					statusText: response.statusText,
					url: response.url,
					headers: response.headers || {},
					text: function() { return Promise.resolve(response.text || ''); },
					json: function() { return Promise.resolve(JSON.parse(response.text || '{}')); }
				};
			}
		""".trimIndent()
	}
	
	private fun extractHeaders(init: Map<String, Any?>?): Map<String, String> {
		val headers = init?.get("headers") ?: return emptyMap()
		return when (headers) {
			is Map<*, *> -> headers.mapNotNull { (k, v) ->
				if (k is String && v is String) k to v else null
			}.toMap()
			else -> emptyMap()
		}
	}
	
	private fun extractBody(init: Map<String, Any?>?): String? {
		val body = init?.get("body") ?: return null
		return when (body) {
			is String -> body
			is Map<*, *> -> {
				// Form data encoding
				@Suppress("UNCHECKED_CAST")
				(body as? Map<String, Any?>)?.entries?.joinToString("&") { (key, value) ->
					"$key=${java.net.URLEncoder.encode(value.toString(), "UTF-8")}"
				}
			}
			else -> body.toString()
		}
	}
	
	private fun isValidUrl(url: String): Boolean {
		return try {
			val uri = URI(url)
			val scheme = uri.scheme?.lowercase()
			scheme == "http" || scheme == "https"
		} catch (e: Exception) {
			false
		}
	}
	
	private fun errorResponse(url: String, status: Int, statusText: String, error: String): Map<String, Any?> {
		return mapOf(
			"ok" to false,
			"status" to status,
			"statusText" to statusText,
			"url" to url,
			"text" to "",
			"error" to error
		)
	}
}
