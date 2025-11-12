package org.skepsun.kototoro.core.exceptions

import org.skepsun.kototoro.parsers.model.Manga

class UnsupportedSourceException(
	message: String?,
	val manga: Manga?,
) : IllegalArgumentException(message)
