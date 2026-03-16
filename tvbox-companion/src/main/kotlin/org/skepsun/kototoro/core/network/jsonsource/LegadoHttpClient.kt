package org.skepsun.kototoro.core.network.jsonsource

import android.content.Context
import com.github.tvbox.osc.util.OkGoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.skepsun.kototoro.core.network.CommonHeaders
import org.skepsun.kototoro.parsers.model.MangaSource
import java.net.URL

class LegadoHttpClient(
	private val context: Context,
) {

	suspend fun get(
		url: String,
		headers: Map<String, String> = emptyMap(),
		source: MangaSource? = null,
	): Response = withContext(Dispatchers.IO) {
		OkGoHelper.init()
		OkGoHelper.getDefaultClient().newCall(
			buildRequest(
				url = url,
				customHeaders = headers,
				source = source,
			),
		).execute()
	}

	private fun buildRequest(
		url: String,
		customHeaders: Map<String, String>,
		source: MangaSource?,
	): Request {
		val headersBuilder = Headers.Builder()
		if (customHeaders.keys.none { it.equals(CommonHeaders.USER_AGENT, ignoreCase = true) }) {
			headersBuilder.add(CommonHeaders.USER_AGENT, DEFAULT_USER_AGENT)
		}
		if (customHeaders.keys.none { it.equals(CommonHeaders.REFERER, ignoreCase = true) }) {
			buildReferer(url)?.let { headersBuilder.add(CommonHeaders.REFERER, it) }
		}
		customHeaders.forEach { (key, value) ->
			headersBuilder.add(key, value)
		}
		return Request.Builder()
			.url(url)
			.headers(headersBuilder.build())
			.apply {
				if (source != null) {
					tag(MangaSource::class.java, source)
				}
				tag(Context::class.java, context)
			}
			.get()
			.build()
	}

	private fun buildReferer(url: String): String? {
		val httpUrl = url.toHttpUrlOrNull()
		if (httpUrl != null) {
			return "${httpUrl.scheme}://${httpUrl.host}/"
		}
		return runCatching {
			val parsed = URL(url)
			"${parsed.protocol}://${parsed.host}/"
		}.getOrNull()
	}

	private companion object {
		private const val DEFAULT_USER_AGENT =
			"Mozilla/5.0 (Linux; Android 13; TVBox Companion) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
	}
}
