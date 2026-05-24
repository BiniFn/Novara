package org.skepsun.kototoro.reader.ui.pager.doublepage

import android.util.Log
import androidx.recyclerview.widget.RecyclerView

internal data class DoublePageViewport(
	val lowerPos: Int,
	val upperPos: Int,
)

internal fun resolveCurrentDoublePageViewport(
	firstCompletelyVisibleItemPosition: Int,
	firstVisibleItemPosition: Int,
	itemCount: Int,
): DoublePageViewport? {
	if (itemCount <= 0) {
		Log.d(LOG_TAG, "resolveCurrentDoublePageViewport: empty itemCount=$itemCount")
		return null
	}
	val lastIndex = itemCount - 1
	val anchor = when {
		firstCompletelyVisibleItemPosition != RecyclerView.NO_POSITION -> firstCompletelyVisibleItemPosition
		firstVisibleItemPosition != RecyclerView.NO_POSITION -> firstVisibleItemPosition
		else -> return null
	}.coerceIn(0, lastIndex)
	val lowerPos = anchor and 1.inv()
	val viewport = DoublePageViewport(
		lowerPos = lowerPos,
		upperPos = (lowerPos + 1).coerceAtMost(lastIndex),
	)
	Log.d(
		LOG_TAG,
		"resolveCurrentDoublePageViewport: full=$firstCompletelyVisibleItemPosition, " +
			"firstVisible=$firstVisibleItemPosition, itemCount=$itemCount, anchor=$anchor, viewport=$viewport",
	)
	return viewport
}

private const val LOG_TAG = "ReaderDebug"
