package org.skepsun.kototoro.details.ui.related

import android.view.Menu
import android.view.MenuInflater
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.list.ListSelectionController
import org.skepsun.kototoro.list.ui.ContentListFragment

@AndroidEntryPoint
class RelatedListFragment : ContentListFragment() {

	override val viewModel by viewModels<RelatedListViewModel>()
	override val isSwipeRefreshEnabled = false

	override fun onScrolledToEnd() = Unit


}

