package org.skepsun.kototoro.explore.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.databinding.FragmentExploreHostBinding
import org.skepsun.kototoro.discover.ui.DiscoverFragment

@AndroidEntryPoint
class ExploreFragment : BaseFragment<FragmentExploreHostBinding>() {

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentExploreHostBinding {
		return FragmentExploreHostBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentExploreHostBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		
		binding.viewPager.adapter = object : FragmentStateAdapter(this) {
			override fun getItemCount(): Int = 2

			override fun createFragment(position: Int): Fragment {
				return when (position) {
					0 -> DiscoverFragment()
					1 -> ExploreSourcesFragment()
					else -> throw IllegalArgumentException("Invalid position $position")
				}
			}
		}

		TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
			tab.text = when (position) {
				0 -> getString(R.string.explore_tab_tracking_sites)
				1 -> getString(R.string.explore_tab_sources)
				else -> null
			}
		}.attach()

		// Disable ViewPager2's internal RecyclerView nested scrolling so child
		// fragments' RecyclerView scroll events propagate to outer CoordinatorLayout AppBarLayout
		binding.viewPager.getChildAt(0)?.let { child ->
			if (child is androidx.recyclerview.widget.RecyclerView) {
				child.isNestedScrollingEnabled = false
			}
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		return insets // Do not apply manual offset, Activity handles Appbar and child Fragments handle Recyclerview.
	}
}
