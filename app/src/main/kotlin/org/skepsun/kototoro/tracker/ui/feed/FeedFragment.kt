package org.skepsun.kototoro.tracker.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.ui.util.MenuInvalidator
import org.skepsun.kototoro.core.ui.util.ReversibleActionObserver
import org.skepsun.kototoro.core.util.ext.addMenuProvider
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.observe
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.skepsun.kototoro.databinding.FragmentContentListBinding
import org.skepsun.kototoro.tracker.ui.feed.compose.FeedScreen
import javax.inject.Inject
import kotlinx.coroutines.flow.drop

@AndroidEntryPoint
class FeedFragment : BaseFragment<FragmentContentListBinding>() {

	@Inject
	lateinit var coil: ImageLoader

	@Inject
	lateinit var settings: org.skepsun.kototoro.core.prefs.AppSettings

	private val viewModel by viewModels<FeedViewModel>()

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentContentListBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentContentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)

		binding.composeView.apply {
			setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
			setContent {
				val items by viewModel.content.collectAsState(initial = emptyList())
				val isRunning by viewModel.isRunning.collectAsState()

				FeedScreen(
					items = items,
					isRefreshing = isRunning,
					onRefresh = { viewModel.update() },
					onLoadMore = { viewModel.requestMoreItems() },
					onFeedItemClick = { item ->
						viewModel.onItemClick(item)
						router.openDetails(item.toContentWithOverride(), this)
					},
					onUpdatedContentItemClick = { contentItem ->
						router.openDetails(contentItem.toContentWithOverride(), this)
					},
					onUpdatedContentMoreClick = {
						// Open updates tab mapping
						router.openMangaUpdates()
					}
				)
			}
		}

		addMenuProvider(FeedMenuProvider(binding.composeView, viewModel))
		viewModel.isHeaderEnabled.drop(1).observe(viewLifecycleOwner, MenuInvalidator(requireActivity()))
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(binding.root, this))
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(binding.root))
		viewModel.isRunning.observe(viewLifecycleOwner, this::onIsTrackerRunningChanged)
	}

	private fun onIsTrackerRunningChanged(isRunning: Boolean) {
		// Used to control refresh state, now mapped into Compose
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		viewBinding?.composeView?.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			bottom = barsInsets.bottom,
		)
		return insets
	}
}
