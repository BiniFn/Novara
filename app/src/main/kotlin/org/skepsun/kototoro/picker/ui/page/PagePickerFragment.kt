package org.skepsun.kototoro.picker.ui.page

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.util.ext.consumeAll
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.databinding.FragmentPagePickerBinding
import org.skepsun.kototoro.details.ui.pager.pages.compose.PagesScreen
import org.skepsun.kototoro.parsers.util.ifNullOrEmpty
import org.skepsun.kototoro.picker.ui.PageImagePickActivity

@AndroidEntryPoint
class PagePickerFragment : BaseFragment<FragmentPagePickerBinding>() {

	private val viewModel by viewModels<PagePickerViewModel>()

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentPagePickerBinding {
		return FragmentPagePickerBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentPagePickerBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.composeView.apply {
			setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
			setContent {
				val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle(initialValue = emptyList())
				val isLoading by viewModel.isLoading.collectAsStateWithLifecycle(initialValue = false)
				val isNoChapters by viewModel.isNoChapters.collectAsStateWithLifecycle(initialValue = false)
				val gridScale by viewModel.gridScale.collectAsStateWithLifecycle(initialValue = 1f)
				val selectedIds = remember { emptySet<Long>() }

				KototoroTheme {
					PagesScreen(
						items = thumbnails,
						gridMinSize = (120.dp / gridScale.coerceIn(0.5f, 1.5f)),
						selectedItemIds = selectedIds,
						emptyMessageResId = if (isNoChapters) R.string.no_chapters else null,
						isLoading = isLoading,
						onLoadNext = viewModel::loadNextChapter,
						onItemClick = { item ->
							val manga = viewModel.manga.value?.toContent() ?: return@PagesScreen
							(activity as PageImagePickActivity).onPagePicked(manga, item.page)
						},
						onItemLongClick = {},
						onSelectionActionClick = {},
						onClearSelection = {},
					)
				}
			}
		}
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(binding.composeView, this))
		viewModel.manga.observe(viewLifecycleOwner, Lifecycle.State.RESUMED) {
			activity?.title = it?.toContent()?.title.ifNullOrEmpty { getString(R.string.pick_manga_page) }
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		viewBinding?.composeView?.setPadding(
			barsInsets.left,
			barsInsets.top,
			barsInsets.right,
			barsInsets.bottom,
		)
		return insets.consumeAll(typeMask)
	}
}
