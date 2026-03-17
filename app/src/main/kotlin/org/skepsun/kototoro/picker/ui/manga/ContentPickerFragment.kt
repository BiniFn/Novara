package org.skepsun.kototoro.picker.ui.manga

import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.list.ui.ContentListFragment
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.picker.ui.PageImagePickActivity

@AndroidEntryPoint
class ContentPickerFragment : ContentListFragment() {

	override val isSwipeRefreshEnabled = false

	override val viewModel by viewModels<ContentPickerViewModel>()

	override fun onScrolledToEnd() = Unit

	override fun onItemClick(item: ContentListModel, view: View) {
		(activity as PageImagePickActivity).onContentPicked(item.manga)
	}

	override fun onResume() {
		super.onResume()
		activity?.setTitle(R.string.pick_manga_page)
	}

	override fun onItemLongClick(item: ContentListModel, view: View): Boolean = false

	override fun onItemContextClick(item: ContentListModel, view: View): Boolean = false
}
