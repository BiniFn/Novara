package org.skepsun.kototoro.core.parser.favicon

import android.net.Uri
import org.skepsun.kototoro.parsers.model.MangaSource
import org.skepsun.kototoro.core.jsonsource.JsonMangaSource

const val URI_SCHEME_FAVICON = "favicon"

fun MangaSource.faviconUri(): Uri {
	// 给 JSON 源添加后缀以避开旧缓存，占位符不会一直命中
	val key = if (this is JsonMangaSource && name.startsWith("JSON_")) "${name}_json" else name
	return Uri.fromParts(URI_SCHEME_FAVICON, key, null)
}
