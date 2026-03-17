package org.skepsun.kototoro.list.ui.model

import org.skepsun.kototoro.core.ui.model.ContentOverride
import org.skepsun.kototoro.list.domain.ReadingProgress
import org.skepsun.kototoro.list.ui.ListModelDiffCallback.Companion.PAYLOAD_ANYTHING_CHANGED
import org.skepsun.kototoro.list.ui.ListModelDiffCallback.Companion.PAYLOAD_PROGRESS_CHANGED
import org.skepsun.kototoro.parsers.model.Content

data class ContentGridModel(
	override val manga: Content,
	override val override: ContentOverride?,
	override val counter: Int,
	val progress: ReadingProgress?,
	val isFavorite: Boolean,
	val isSaved: Boolean,
) : ContentListModel() {

	override fun getChangePayload(previousState: ListModel): Any? = when {
		previousState !is ContentGridModel || previousState.manga != manga -> null

		previousState.progress != progress -> PAYLOAD_PROGRESS_CHANGED
		previousState.isFavorite != isFavorite ||
			previousState.isSaved != isSaved -> PAYLOAD_ANYTHING_CHANGED

		else -> super.getChangePayload(previousState)
	}
}
