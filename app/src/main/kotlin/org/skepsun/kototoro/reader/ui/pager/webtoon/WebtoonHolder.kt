package org.skepsun.kototoro.reader.ui.pager.webtoon

import android.util.Log
import android.view.View
import androidx.lifecycle.LifecycleOwner
import org.skepsun.kototoro.core.exceptions.resolve.ExceptionResolver
import org.skepsun.kototoro.core.os.NetworkState
import org.skepsun.kototoro.databinding.ItemPageWebtoonBinding
import org.skepsun.kototoro.reader.domain.PageLoader
import org.skepsun.kototoro.reader.domain.ReaderPageEnhancementController
import org.skepsun.kototoro.reader.ui.config.ReaderSettings
import org.skepsun.kototoro.reader.ui.pager.BasePageHolder
import org.skepsun.kototoro.reader.ui.pager.ReaderPage
import org.skepsun.kototoro.reader.ui.pager.vm.PageState

class WebtoonHolder(
	owner: LifecycleOwner,
	binding: ItemPageWebtoonBinding,
	loader: PageLoader,
	enhancementController: ReaderPageEnhancementController,
	readerSettingsProducer: ReaderSettings.Producer,
	networkState: NetworkState,
	exceptionResolver: ExceptionResolver,
) : BasePageHolder<ItemPageWebtoonBinding>(
	binding = binding,
	loader = loader,
	enhancementController = enhancementController,
	readerSettingsProducer = readerSettingsProducer,
	networkState = networkState,
	exceptionResolver = exceptionResolver,
	lifecycleOwner = owner,
) {

	override val ssiv = binding.ssiv
	override val animatedView = binding.imageViewAnimated

	private var scrollToRestore = 0
	private var isShowingPreview = false

	init {
		bindingInfo.progressBar.setVisibilityAfterHide(View.GONE)
	}

	override fun onBind(data: ReaderPage) {
		super.onBind(data)
		scrollToRestore = 0
		isShowingPreview = false
		binding.ssiv.resetSourceState()
	}

	override fun shouldShowLoadingPreview() = true

	override fun onStateChanged(state: PageState) {
		when (state) {
			is PageState.Loading -> {
				if (state.preview != null && binding.ssiv.getState() == null && !isShowingPreview) {
					isShowingPreview = true
					binding.ssiv.resetSourceState()
				}
			}

			is PageState.Loaded, is PageState.AwaitingTranslation -> {
				if (isShowingPreview) {
					isShowingPreview = false
					binding.ssiv.resetSourceState()
				}
			}

			else -> Unit
		}
		super.onStateChanged(state)
	}

	override fun onReady() {
		Log.d(
			TAG,
			"onReady: scrollToRestore=$scrollToRestore, isShowingPreview=$isShowingPreview, " +
				"ssiv.size=${binding.ssiv.width}x${binding.ssiv.height}, " +
				"imgSize=${binding.ssiv.sWidth}x${binding.ssiv.sHeight}, isReady=${binding.ssiv.isReady}",
		)
		binding.ssiv.colorFilter = settings.colorFilter?.toColorFilter()
		if (!isShowingPreview && scrollToRestore != 0) {
			binding.ssiv.scrollTo(scrollToRestore)
			scrollToRestore = 0
		}
	}

	fun getScrollY() = binding.ssiv.getScroll()

	fun restoreScroll(scroll: Int) {
		if (!isShowingPreview && binding.ssiv.isScrollReady()) {
			Log.d(TAG, "restoreScroll: scroll=$scroll, immediate")
			binding.ssiv.scrollTo(scroll)
		} else {
			Log.d(TAG, "restoreScroll: scroll=$scroll, deferred (not ready or preview)")
			scrollToRestore = scroll
		}
	}

	companion object {
		private const val TAG = "WebtoonHolder"
	}
}
