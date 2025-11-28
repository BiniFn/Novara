package org.skepsun.kototoro.details.ui.pager

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.skepsun.kototoro.R
import org.skepsun.kototoro.details.ui.pager.bookmarks.BookmarksFragment
import org.skepsun.kototoro.details.ui.pager.chapters.ChaptersFragment
import org.skepsun.kototoro.details.ui.pager.pages.PagesFragment

class ChaptersPagesAdapter(
	fragment: Fragment,
	val isPagesTabEnabled: Boolean,
	val isBookmarksTabEnabled: Boolean = true,
) : FragmentStateAdapter(fragment),
	TabLayoutMediator.TabConfigurationStrategy {

	override fun getItemCount(): Int = when {
		isPagesTabEnabled && isBookmarksTabEnabled -> 3
		isPagesTabEnabled || isBookmarksTabEnabled -> 2
		else -> 1
	}

	override fun createFragment(position: Int): Fragment = when {
		position == 0 -> ChaptersFragment()
		position == 1 && isPagesTabEnabled -> PagesFragment()
		position == 1 && isBookmarksTabEnabled -> BookmarksFragment()
		position == 2 && isBookmarksTabEnabled -> BookmarksFragment()
		else -> throw IllegalArgumentException("Invalid position $position")
	}

	override fun onConfigureTab(tab: TabLayout.Tab, position: Int) {
		tab.setIcon(
			when {
				position == 0 -> R.drawable.ic_list
				position == 1 && isPagesTabEnabled -> R.drawable.ic_grid
				position == 1 && isBookmarksTabEnabled -> R.drawable.ic_bookmark
				position == 2 && isBookmarksTabEnabled -> R.drawable.ic_bookmark
				else -> 0
			},
		)
		// tab.setText(
		// 	when (position) {
		// 		0 -> R.string.chapters
		// 		1 -> if (isPagesTabEnabled) R.string.pages else R.string.bookmarks
		// 		2 -> R.string.bookmarks
		// 		else -> 0
		// 	},
		// )
	}
}
