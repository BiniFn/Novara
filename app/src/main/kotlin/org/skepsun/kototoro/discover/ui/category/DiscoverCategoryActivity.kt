package org.skepsun.kototoro.discover.ui.category

import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.core.ui.FragmentContainerActivity

@AndroidEntryPoint
class DiscoverCategoryActivity : FragmentContainerActivity(DiscoverCategoryFragment::class.java), org.skepsun.kototoro.filter.ui.FilterCoordinator.Owner {
	private val viewModel: DiscoverCategoryViewModel by viewModels()

	override val filterCoordinator: org.skepsun.kototoro.filter.ui.FilterCoordinator
		get() = viewModel.filterCoordinator
}
