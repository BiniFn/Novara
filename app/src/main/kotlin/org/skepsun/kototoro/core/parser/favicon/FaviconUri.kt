package org.skepsun.kototoro.core.parser.favicon

import android.net.Uri
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.core.jsonsource.JsonContentSource
import org.skepsun.kototoro.core.jsonsource.JsonSourceListSource

const val URI_SCHEME_FAVICON = "favicon"

fun ContentSource.directFaviconUriOrNull(): Uri? {
	if (this is JsonSourceListSource) {
		iconUrl?.takeIf { it.isNotBlank() }?.let { iconUrl ->
			return directFaviconUri(name, iconUrl)
		}
	}
	if (this is JsonContentSource) {
		entity.iconUrl?.takeIf { it.isNotBlank() }?.let { iconUrl ->
			return directFaviconUri(name, iconUrl)
		}
	}
	return null
}

fun ContentSource.faviconUri(): Uri {
	directFaviconUriOrNull()?.let {
		return it
	}
	// 给 JSON 源添加后缀以避开旧缓存，占位符不会一直命中
	val key = if (this is JsonContentSource && name.startsWith("JSON_")) "${name}_json" else name
	return Uri.fromParts(URI_SCHEME_FAVICON, key, null)
}

private fun directFaviconUri(sourceName: String, iconUrl: String): Uri {
	return Uri.Builder()
		.scheme(URI_SCHEME_FAVICON)
		.encodedAuthority(sourceName)
		.appendQueryParameter("url", iconUrl)
		.build()
}
