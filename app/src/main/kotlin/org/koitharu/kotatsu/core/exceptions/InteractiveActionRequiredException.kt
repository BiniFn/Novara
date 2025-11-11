package org.skepsun.kototoro.core.exceptions

import okio.IOException
import org.skepsun.kototoro.parsers.model.MangaSource

class InteractiveActionRequiredException(
	val source: MangaSource,
	val url: String,
) : IOException("Interactive action is required for ${source.name}")
