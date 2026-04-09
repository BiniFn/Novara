package org.skepsun.kototoro.list.ui.model

import org.skepsun.kototoro.core.ui.model.ContentOverride
import org.skepsun.kototoro.parsers.model.Content

data class ContentCompactListModel(
	override val manga: Content,
	override val override: ContentOverride?,
	val subtitle: String,
	override val counter: Int,
	override val isPinned: Boolean = false,
) : ContentListModel()
