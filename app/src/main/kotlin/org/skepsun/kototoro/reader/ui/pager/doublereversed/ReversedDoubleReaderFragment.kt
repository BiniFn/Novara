package org.skepsun.kototoro.reader.ui.pager.doublereversed

import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import org.skepsun.kototoro.reader.ui.ReaderState
import org.skepsun.kototoro.reader.ui.resolveVisiblePageSelection
import org.skepsun.kototoro.reader.ui.pager.ReaderPage
import org.skepsun.kototoro.reader.ui.pager.doublepage.DoublePageViewport
import org.skepsun.kototoro.reader.ui.pager.doublepage.DoubleReaderFragment
import org.skepsun.kototoro.reader.ui.pager.doublepage.resolveCurrentDoublePageViewport

class ReversedDoubleReaderFragment : DoubleReaderFragment() {

	override fun switchPageBy(delta: Int) {
		super.switchPageBy(-delta)
	}

	override fun switchPageTo(position: Int, smooth: Boolean) {
		super.switchPageTo(reversed(position), smooth)
	}

	override suspend fun onPagesChanged(pages: List<ReaderPage>, pendingState: ReaderState?) {
		super.onPagesChanged(pages.reversed(), pendingState)
	}

	override fun getCurrentState(): ReaderState? = viewBinding?.run {
		val itemCount = readerAdapter?.itemCount ?: return@run null
		if (itemCount <= 0) {
			return@run null
		}
		val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return@run null
		val viewport = resolveCurrentDoublePageViewport(
			firstCompletelyVisibleItemPosition = lm.findFirstCompletelyVisibleItemPosition(),
			firstVisibleItemPosition = lm.findFirstVisibleItemPosition(),
			itemCount = itemCount,
		) ?: return@run null
		val pages = viewModel.content.value.pages
		val originalLowerPos = reversed(viewport.upperPos)
		val originalUpperPos = reversed(viewport.lowerPos)
		val selectedPos = resolveVisiblePageSelection(
			pages = pages,
			lowerPos = originalLowerPos,
			upperPos = originalUpperPos,
			currentChapterId = viewModel.getCurrentState()?.chapterId,
			boundsPageOffset = BOUNDS_PAGE_OFFSET,
		)
		val page = pages.getOrNull(selectedPos) ?: return@run null
		val state = ReaderState(
			chapterId = page.chapterId,
			page = page.index,
			scroll = 0,
		)
		Log.d(
			LOG_TAG,
			"reversedDouble.getCurrentState: viewport=$viewport, originalRange=$originalLowerPos..$originalUpperPos, " +
				"selectedPos=$selectedPos, page=${page.chapterId}:${page.index}, state=$state",
		)
		state
	}

	override fun notifyPageChanged(lowerPos: Int, upperPos: Int) {
		val originalLowerPos = reversed(upperPos)
		val originalUpperPos = reversed(lowerPos)
		Log.d(
			LOG_TAG,
			"reversedDouble.notifyPageChanged: adapterRange=$lowerPos..$upperPos, " +
				"contentRange=$originalLowerPos..$originalUpperPos",
		)
		viewModel.onCurrentPageChanged(originalLowerPos, originalUpperPos)
	}

	private fun reversed(position: Int): Int {
		return ((readerAdapter?.itemCount ?: 0) - position - 1).coerceAtLeast(0)
	}

	companion object {
		private const val BOUNDS_PAGE_OFFSET = 2
		private const val LOG_TAG = "ReaderDebug"
	}
}
