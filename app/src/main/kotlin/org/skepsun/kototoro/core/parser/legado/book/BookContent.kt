package org.skepsun.kototoro.core.parser.legado.book

import org.json.JSONObject
import org.skepsun.kototoro.core.model.jsonsource.LegadoBookSource
import org.skepsun.kototoro.core.parser.legado.AnalyzeRule
import org.skepsun.kototoro.core.parser.legado.sandbox.LegadoSandbox
import org.skepsun.kototoro.parsers.exception.ContentUnavailableException
import org.skepsun.kototoro.parsers.model.MangaPage
import org.skepsun.kototoro.parsers.model.MangaSource

/**
 * Handles chapter content parsing and image extraction.
 */
object BookContent {

    private const val TAG = "LegadoRepository"
    private val optionsSplitRegex = Regex("\\s*,\\s*(?=\\{)")

    data class ParseResult(
        val pages: List<MangaPage>,
        val nextPageUrls: List<String>
    )
    
    fun parse(
        content: String,
        baseUrl: String,
        source: MangaSource,
        config: LegadoBookSource,
        sandbox: LegadoSandbox
    ): ParseResult {
        val rule = config.ruleContent ?: return ParseResult(emptyList(), emptyList())
        val analyzeRule = AnalyzeRule(content, sandbox, baseUrl)

        // 避免打印超长（多行）规则导致 logcat 刷屏；仅保留长度信息便于定位。
        val ruleContent = rule.content.orEmpty()
        android.util.Log.d(
            TAG,
            "[BookContent] Rule length=${ruleContent.length}, HTML length=${content.length}, Type=${config.bookSourceType}"
        )
        
        // Legado content rule can return multiple image URLs or text
        val rawContentList = analyzeRule.getStringList(ruleContent) ?: emptyList()
        
        android.util.Log.d(TAG, "[BookContent] Extracted ${rawContentList.size} items from rule")
        
        val processedContent = applyReplace(rawContentList, rule)
        
        val pages = ArrayList<MangaPage>()
        val isManga = config.bookSourceType == 2
        
        if (isManga) {
            // For manga, merge all items and perform deep image extraction (Legado-style)
            val fullContent = processedContent.joinToString("\n")
            val doc = org.jsoup.Jsoup.parse(fullContent)
            val imgs = doc.select("img")
            
            if (imgs.isNotEmpty()) {
                imgs.forEach { img ->
                    val urlRaw = img.attr("data-original").takeIf { it.isNotBlank() }
                        ?: img.attr("data-src").takeIf { it.isNotBlank() }
                        ?: img.attr("src").takeIf { it.isNotBlank() }
                        ?: return@forEach

                    val (url, headers) = splitUrlAndHeaders(urlRaw)
                    if (url.isBlank()) return@forEach
                    val absoluteUrl = normalizeMangaImageUrl(resolveUrl(baseUrl, url))
                    if (absoluteUrl.isBlank()) return@forEach
                    val finalHeaders = mergeDefaultImageHeaders(headers, baseUrl, absoluteUrl)
                    pages.add(MangaPage(
                        id = (source.name.hashCode().toLong() shl 32) + pages.size,
                        url = absoluteUrl,
                        preview = absoluteUrl,
                        headers = finalHeaders,
                        source = source
                    ))
                }
                android.util.Log.d(TAG, "[BookContent] Deeply extracted ${pages.size} image URLs from HTML block")
            } else {
                // If no <img> found, fall back to interpreting items as raw URLs (for compatibility)
                processedContent.forEach { raw ->
                    val candidate = raw.trim()
                    if (candidate.isBlank()) return@forEach
                    if (isLikelyUrl(candidate)) {
                        val (url, headers) = splitUrlAndHeaders(candidate)
                        val absoluteUrl = normalizeMangaImageUrl(resolveUrl(baseUrl, url))
                        val finalHeaders = mergeDefaultImageHeaders(headers, baseUrl, absoluteUrl)
                        pages.add(MangaPage(
                            id = (source.name.hashCode().toLong() shl 32) + pages.size,
                            url = absoluteUrl,
                            preview = absoluteUrl,
                            headers = finalHeaders,
                            source = source
                        ))
                    }
                }
                android.util.Log.d(TAG, "[BookContent] Extracted ${pages.size} raw URLs from result list")
            }
        } else {
            // Novel mode: proceed as individual text items
            processedContent.forEach { raw ->
                val refined = refineContent(raw, config)
                if (refined.isBlank()) return@forEach
                
                val isUrl = isLikelyUrl(refined)
                val absoluteUrl = if (isUrl) {
                    resolveUrl(baseUrl, refined)
                } else {
                    // Wrap text content in data URL for NovelContentLoader
                    val base64 = android.util.Base64.encodeToString(
                        refined.toByteArray(Charsets.UTF_8),
                        android.util.Base64.NO_WRAP
                    )
                    "data:text/html;base64,$base64"
                }

                pages.add(MangaPage(
                    id = (source.name.hashCode().toLong() shl 32) + pages.size,
                    url = absoluteUrl,
                    preview = if (isUrl) absoluteUrl else "TEXT",
                    headers = emptyMap(),
                    source = source
                ))
            }
        }
        
	        if (pages.isNotEmpty()) {
	            android.util.Log.d(TAG, "[Content] Page[0] URL: ${pages[0].url}")
	        } else if (isManga) {
	            detectApiErrorMessage(content)?.let { message ->
	                throw ContentUnavailableException(message)
	            }
	        }

        val nextPageUrls = rule.nextContentUrl
            ?.let { analyzeRule.getStringList(it) }
            ?.mapNotNull { it.takeIf { url -> url.isNotBlank() } }
            ?.map { resolveUrl(baseUrl, it) }
            ?: emptyList()
        
        if (nextPageUrls.isNotEmpty()) {
            android.util.Log.d(TAG, "[Content] nextPageUrls found: ${nextPageUrls.take(3)}")
        }

        return ParseResult(pages, nextPageUrls)
    }

    private fun normalizeMangaImageUrl(url: String): String {
        if (url.isBlank()) return url
        // Android 9+ 默认可能禁用 cleartext；对 mkzcdn 等常见图床统一升级 https。
        if (url.startsWith("http://", ignoreCase = true)) {
            val host = runCatching { java.net.URL(url).host.orEmpty() }.getOrDefault("")
            if (host.endsWith("mkzcdn.com", ignoreCase = true)) {
                return "https://" + url.removePrefix("http://")
            }
        }
        return url
    }

    private fun mergeDefaultImageHeaders(
        original: Map<String, String>,
        baseUrl: String,
        imageUrl: String
    ): Map<String, String> {
        val host = runCatching { java.net.URL(imageUrl).host.orEmpty() }.getOrDefault("")
        if (!host.endsWith("mkzcdn.com", ignoreCase = true)) return original

        val lower = original.keys.associateBy { it.lowercase() }
        if (lower.containsKey("referer")) return original

        val referer = runCatching {
            val u = java.net.URL(baseUrl)
            "${u.protocol}://${u.host}/"
        }.getOrNull() ?: "https://comic.mkzhan.com/"

        return buildMap {
            putAll(original)
            put("Referer", referer)
        }
    }

    private fun refineContent(content: String, config: LegadoBookSource): String {
        return content.trim()
    }

    private fun applyReplace(content: List<String>, rule: org.skepsun.kototoro.core.model.jsonsource.ContentRule): List<String> {
        val replace = rule.replaceRegex
        if (replace.isNullOrBlank()) return content

        // 支持 Legado 常见的 "regex##replacement" 写法
        val parts = replace.split("##", limit = 2)
        if (parts.size != 2) return content
        val pattern = runCatching { Regex(parts[0]) }.getOrNull() ?: return content
        val replacement = parts[1]
        return content.map { text -> text.replace(pattern, replacement) }
    }

    private fun isLikelyUrl(s: String): Boolean {
        val trimmed = s.trim()
        if (trimmed.length > 2048) return false // Too long for a URL
        val lower = trimmed.lowercase()
        return lower.startsWith("http://") || 
               lower.startsWith("https://") || 
               lower.startsWith("data:") || 
               lower.startsWith("file://") ||
               lower.startsWith("ftp://") ||
               (trimmed.contains(".") && !trimmed.contains(" ") && !trimmed.contains("\n"))
    }

	    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
	        if (relativeUrl.isBlank() || relativeUrl.startsWith("http")) return relativeUrl
	        return try {
	            java.net.URL(java.net.URL(baseUrl), relativeUrl).toString()
	        } catch (e: Exception) {
	            relativeUrl
	        }
	    }

	    private fun detectApiErrorMessage(content: String): String? {
	        val trimmed = content.trim()
	        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
	        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null

	        val codeValue = obj.opt("code")?.toString()?.trim().orEmpty()
	        val statusValue = obj.opt("status")?.toString()?.trim().orEmpty()
	        val message = (obj.optString("message").ifBlank { obj.optString("msg") }).trim()
	        if (message.isBlank()) return null

	        // 常见约定：
	        // - code: 0 / success
	        // - status: 0 / 200 / ok
	        val isSuccess = codeValue == "0" ||
	            codeValue.equals("success", ignoreCase = true) ||
	            statusValue == "0" ||
	            statusValue == "200" ||
	            statusValue.equals("ok", ignoreCase = true)
	        return if (isSuccess) null else message
	    }

        private fun splitUrlAndHeaders(raw: String): Pair<String, Map<String, String>> {
            val splitMatch = optionsSplitRegex.find(raw)
            if (splitMatch == null) return raw.trim() to emptyMap()

            val urlPart = raw.substring(0, splitMatch.range.first).trim()
            val optionsPart = raw.substring(splitMatch.range.last + 1).trim()

            val headers = runCatching {
                val normalizedOptions = if (optionsPart.contains("'")) {
                    optionsPart.replace("'", "\"")
                } else optionsPart
                val optionsJson = JSONObject(normalizedOptions)
                val headersObj = optionsJson.optJSONObject("headers") ?: return@runCatching emptyMap()
                buildMap<String, String> {
                    headersObj.keys().forEach { key ->
                        put(key, headersObj.optString(key))
                    }
                }
            }.getOrDefault(emptyMap())

            return urlPart to headers
        }
}
