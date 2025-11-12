package org.skepsun.kototoro.history.domain.model

import org.skepsun.kototoro.core.model.MangaHistory
import org.skepsun.kototoro.parsers.model.Manga

data class MangaWithHistory(
	val manga: Manga,
	val history: MangaHistory
)
