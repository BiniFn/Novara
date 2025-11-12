package org.skepsun.kototoro.list.ui.size

import android.view.View
import android.widget.TextView
import org.skepsun.kototoro.history.ui.util.ReadingProgressView

interface ItemSizeResolver {

	val cellWidth: Int

	fun attachToView(
		view: View,
		textView: TextView?,
		progressView: ReadingProgressView?,
	)
}
