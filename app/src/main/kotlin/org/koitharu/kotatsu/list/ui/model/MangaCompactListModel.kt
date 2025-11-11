package org.skepsun.kototoro.list.ui.model

import org.skepsun.kototoro.core.ui.model.MangaOverride
import org.skepsun.kototoro.parsers.model.Manga

data class MangaCompactListModel(
	override val manga: Manga,
	override val override: MangaOverride?,
	val subtitle: String,
	override val counter: Int,
) : MangaListModel()
