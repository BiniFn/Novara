package org.skepsun.kototoro.core.exceptions

import okio.IOException
import org.skepsun.kototoro.parsers.model.ContentSource

class InteractiveActionRequiredException(
	val source: ContentSource,
	val url: String,
) : IOException("Interactive action is required for ${source.name}")
