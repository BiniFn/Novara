package org.skepsun.kototoro.core.parser.legado

import android.util.Log
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.skepsun.kototoro.core.parser.legado.sandbox.LegadoSandbox
import java.net.URLEncoder
import java.nio.charset.Charset

/**
 * URL builder with placeholder substitution and JavaScript support.
 * Handles Legado URL templates and request configuration.
 * 
 * Based on legado-with-MD3 AnalyzeUrl pattern.
 * 
 * Supports:
 * - Template vars: {{key}}, {{page}}, {{baseUrl}}
 * - POST body with method specification
 * - Header configuration
 * - JavaScript URL generation
 */
class AnalyzeUrl(
    private var ruleUrl: String,
    private val key: String? = null,
    private val page: Int = 1,
    private var baseUrl: String = "",
    private val ruleData: RuleDataInterface? = null,
    private val sandbox: LegadoSandbox? = null
) {
    
    companion object {
        private const val TAG = "AnalyzeUrl"
        
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        // Regex for @js: or <js>...</js>
        private val JS_PATTERN = Regex("@js:([\\s\\S]*)|<js>([\\s\\S]*?)</js>", RegexOption.IGNORE_CASE)
    }
    
    data class UrlResult(
        val url: String,
        val method: String = "GET",
        val body: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val charset: Charset = Charsets.UTF_8,
        val useWebView: Boolean = false,
        val webJs: String? = null,
        val webViewDelayTime: Long = 0,
        val retry: Int = 0,
        val type: String? = null
    )
    
    /**
     * Build the final URL with all substitutions applied
     */
    fun build(): UrlResult {
        if (ruleUrl.isBlank()) {
            return UrlResult(url = baseUrl)
        }
        
        // 1. Execute top-level JS if present
        analyzeJs()
        
        // 2. Map {{key}}, {{page}}, etc.
        ruleUrl = applyTemplateVars(ruleUrl)
        
        // 3. Parse URL and options
        return parseUrlWithOptions()
    }

    /**
     * Identify and execute @js: or <js> blocks in the ruleUrl
     */
    private fun analyzeJs() {
        val matches = JS_PATTERN.findAll(ruleUrl)
        if (matches.none()) return

        var result = ruleUrl
        matches.forEach { match ->
            val script = match.groups[1]?.value ?: match.groups[2]?.value ?: ""
            if (script.isNotBlank()) {
                val evalResult = evalJS(script, result)
                result = evalResult?.toString() ?: ""
            }
        }
        ruleUrl = result
    }
    
    /**
     * Parse URL possibly with options JSON
     * Format: url,{"method":"POST","body":"...","headers":{...}}
     */
    private fun parseUrlWithOptions(): UrlResult {
        // Find the first comma followed by a brace (ignoring those inside braces if possible, but Legado is usually simple)
        val commaIndex = ruleUrl.indexOf(",{")
        
        if (commaIndex == -1) {
            return UrlResult(url = resolveUrl(baseUrl, ruleUrl.trim()))
        }
        
        val urlPart = ruleUrl.substring(0, commaIndex).trim()
        val optionsPart = ruleUrl.substring(commaIndex + 1).trim()
        
        val finalUrl = resolveUrl(baseUrl, urlPart)
        
        return try {
            // Normalize quotes for JSON parsing (Legado often uses single quotes)
            val normalizedOptions = if (optionsPart.contains("'")) {
                optionsPart.replace("'", "\"")
            } else optionsPart
            
            val optionsJson = JSONObject(normalizedOptions)
            
            val method = optionsJson.optString("method", "GET").uppercase()
            val body = if (optionsJson.has("body")) optionsJson.optString("body") else null
            val headersObj = optionsJson.optJSONObject("headers")
            val headers = mutableMapOf<String, String>()
            headersObj?.keys()?.forEach { key ->
                headers[key] = headersObj.optString(key)
            }
            
            val charsetStr = if (optionsJson.has("charset")) optionsJson.optString("charset") else null
            val charset = charsetStr?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
            
            // Experimental webView support
            val useWebView = if (optionsJson.has("webView")) {
                val wv = optionsJson.opt("webView")
                val wvStr = wv?.toString()
                wvStr == "true" || wvStr == "1"
            } else false
            
            val webJs = if (optionsJson.has("webJs")) optionsJson.optString("webJs") else null
            val webViewDelayTime = optionsJson.optLong("webViewDelayTime", 0L)
            val retry = optionsJson.optInt("retry", 0)
            val type = if (optionsJson.has("type")) optionsJson.optString("type") else null
            
            // If there is another JS suffix in options? Legado sometimes does this.
            var finalProcessedUrl = finalUrl
            if (optionsJson.has("js")) {
                val jsStr = optionsJson.optString("js")
                val jsResult = evalJS(jsStr, finalProcessedUrl)
                finalProcessedUrl = jsResult?.toString() ?: finalProcessedUrl
            }

            UrlResult(
                url = finalProcessedUrl,
                method = method,
                body = body,
                headers = headers,
                charset = charset,
                useWebView = useWebView,
                webJs = webJs,
                webViewDelayTime = webViewDelayTime,
                retry = retry,
                type = type
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse URL options: $optionsPart", e)
            UrlResult(url = finalUrl)
        }
    }
    
    /**
     * Evaluate JavaScript using the sandbox
     */
    private fun evalJS(script: String, result: Any? = null): Any? {
        if (sandbox == null) {
            Log.w(TAG, "Sandbox not available for JS evaluation")
            return result
        }
        
        sandbox.putVariable("key", key)
        sandbox.putVariable("page", page.toString())
        sandbox.putVariable("baseUrl", baseUrl)
        sandbox.setResult(result)
        
        return sandbox.eval(script)
    }

    /**
     * Apply template variable substitutions
     */
    private fun applyTemplateVars(input: String): String {
        if (input.isBlank()) return input
        
        var result = input
        
        // Replace {{key}} and {key}
        val encodedKey = key?.let { URLEncoder.encode(it, "UTF-8") } ?: ""
        result = result
            .replace("{{key}}", encodedKey)
            .replace("{key}", encodedKey)
            .replace("{{searchKey}}", encodedKey)
            .replace("{searchKey}", encodedKey)
        
        // Replace {{page}} and {page}
        result = result
            .replace("{{page}}", page.toString())
            .replace("{page}", page.toString())
        
        // Replace {{baseUrl}}
        result = result.replace("{{baseUrl}}", baseUrl)
        
        // Replace variables from RuleData
        ruleData?.getVariableMap()?.forEach { (k, v) ->
            result = result
                .replace("{{$k}}", v)
                .replace("{$k}", v)
        }
        
        return result
    }
    
    
    /**
     * Resolve relative URL against base URL
     */
    private fun resolveUrl(base: String, relative: String): String {
        if (relative.isBlank()) return base
        if (relative.startsWith("http://") || relative.startsWith("https://") || relative.startsWith("data:")) {
            return relative
        }
        
        return try {
            val resolved = java.net.URL(java.net.URL(base), relative).toString()
            resolved
        } catch (e: Exception) {
            if (relative.startsWith("/")) {
                try {
                    val baseUri = java.net.URL(base)
                    "${baseUri.protocol}://${baseUri.host}$relative"
                } catch (e2: Exception) {
                    relative
                }
            } else {
                relative
            }
        }
    }
}
