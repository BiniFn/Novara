package org.skepsun.kototoro.core.model

import org.skepsun.kototoro.parsers.model.MangaSource

data class MangaSourceInfo(
	val mangaSource: MangaSource,
	val isEnabled: Boolean,
	val isPinned: Boolean,
) : MangaSource by mangaSource
