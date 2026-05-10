package com.lagradost.nicehttp

import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class NiceResponse(
	private val rawResponse: Response,
	private val bodyBytes: ByteArray,
	private val contentType: String?,
	private val responseParser: ResponseParser?,
) {
	val okhttpResponse: Response
		get() = rawResponse

	val body: ResponseBody
		get() = bodyBytes.toResponseBody(contentType?.toMediaTypeOrNull())

	val parser: ResponseParser?
		get() = responseParser

	val text: String by lazy(LazyThreadSafetyMode.NONE) {
		bodyBytes.toString(Charsets.UTF_8)
	}

	val document: Document by lazy(LazyThreadSafetyMode.NONE) {
		Jsoup.parse(text, url)
	}

	val url: String
		get() = rawResponse.request.url.toString()

	val headers: Headers
		get() = rawResponse.headers

	val cookies: Map<String, String> by lazy(LazyThreadSafetyMode.NONE) {
		parseCookies(rawResponse)
	}

	override fun toString(): String = text

	private fun parseCookies(response: Response): Map<String, String> {
		return response.headers("Set-Cookie")
			.mapNotNull { header ->
				val cookie = header.substringBefore(';')
				val separatorIndex = cookie.indexOf('=')
				if (separatorIndex <= 0) {
					null
				} else {
					cookie.substring(0, separatorIndex) to cookie.substring(separatorIndex + 1)
				}
			}
			.toMap()
	}
}

inline fun <reified T : Any> NiceResponse.parsed(): T {
	val activeParser = parser ?: error("No ResponseParser configured for ${T::class.qualifiedName}")
	return activeParser.parse(text, T::class)
}

inline fun <reified T : Any> NiceResponse.parsedSafe(): T? {
	val activeParser = parser ?: return null
	return activeParser.parseSafe(text, T::class)
}

val Request.cookies: Map<String, String>
	get() = headers.values("Cookie")
		.flatMap { header ->
			header.split(';').mapNotNull { part ->
				val normalized = part.trim()
				val separatorIndex = normalized.indexOf('=')
				if (separatorIndex <= 0) {
					null
				} else {
					normalized.substring(0, separatorIndex) to normalized.substring(separatorIndex + 1)
				}
			}
		}
		.toMap()
