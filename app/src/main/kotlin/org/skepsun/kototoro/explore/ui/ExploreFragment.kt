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
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.databinding.FragmentExploreHostBinding
import org.skepsun.kototoro.discover.ui.DiscoverFragment

import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import org.skepsun.kototoro.core.util.ext.addMenuProvider
import org.skepsun.kototoro.main.ui.SearchBarFilterViewController
import org.skepsun.kototoro.main.ui.owners.AppBarOwner
import org.skepsun.kototoro.explore.ui.model.BrowseGroupTab
import org.skepsun.kototoro.explore.ui.model.SourceTag
import javax.inject.Inject

@AndroidEntryPoint
class ExploreFragment : BaseFragment<FragmentExploreHostBinding>(), SearchBarFilterViewController.Callback {

	@Inject
	lateinit var settings: org.skepsun.kototoro.core.prefs.AppSettings

	private val viewModel by viewModels<ExploreViewModel>()
	private var filterMenuProvider: SearchBarFilterViewController? = null

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentExploreHostBinding {
		return FragmentExploreHostBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentExploreHostBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		
		binding.viewPager.adapter = object : FragmentStateAdapter(this) {
			override fun getItemCount(): Int = 2

			override fun createFragment(position: Int): Fragment {
				return when (position) {
					0 -> ExploreSourcesFragment()
					1 -> DiscoverFragment()
					else -> throw IllegalArgumentException("Invalid position $position")
				}
			}
		}

		TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
			tab.text = when (position) {
				0 -> getString(R.string.explore_tab_sources)
				1 -> getString(R.string.explore_tab_tracking_sites)
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

		// Set up SearchBar filter icons
		filterMenuProvider = SearchBarFilterViewController(this)
		filterMenuProvider?.attachTo(this)

		binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
			override fun onPageSelected(position: Int) {
				filterMenuProvider?.updateVisibility()
			}
		})

		viewModel.currentGroupTab.observe(viewLifecycleOwner) { _ ->
			filterMenuProvider?.updateIcons()
		}
		viewModel.currentSourceTags.observe(viewLifecycleOwner) { _ ->
			filterMenuProvider?.updateIcons()
		}
		viewModel.availableTabs.observe(viewLifecycleOwner) { _ ->
			filterMenuProvider?.updateVisibility()
			filterMenuProvider?.updateIcons()
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		return insets // Do not apply manual offset, Activity handles Appbar and child Fragments handle Recyclerview.
	}

	override fun onDestroyView() {
		super.onDestroyView()
		filterMenuProvider = null
	}

	// === SearchBarFilterViewController.Callback implementation ===

	override fun onContentTypeSelected(tab: BrowseGroupTab) {
		viewModel.setSelectedGroupTab(tab)
	}

	override fun onSourceTagSelected(tag: SourceTag?) {
		val selectedTags = if (tag != null) setOf(tag) else emptySet()
		viewModel.setSelectedSourceTags(selectedTags)
	}

	override fun getSelectedContentType(): BrowseGroupTab = viewModel.getSelectedGroupTab()

	override fun getSelectedSourceTags(): Set<SourceTag> = viewModel.currentSourceTags.value ?: emptySet()

	override fun getSourceTagEntries(): List<SourceTag> = SourceTag.quickFilterEntries

	override fun isContentTypeFilterVisible(): Boolean = !settings.isSearchBarFilterHidden

	override fun isSourceTagFilterVisible(): Boolean = !settings.isSearchBarFilterHidden

	override fun isContentTypeEnabled(tab: BrowseGroupTab): Boolean {
		val selectedTags = viewModel.currentSourceTags.value ?: emptySet()
		return selectedTags.isEmpty() || selectedTags.any { it.supportsContentTab(tab) }
	}

	override fun isSourceTagEnabled(tag: SourceTag): Boolean {
		return viewModel.getSelectedGroupTab().supportsSourceTag(tag)
	}

	override fun onFilterIconClicked(anchor: View): Boolean {
		if (viewBinding?.viewPager?.currentItem == 1) {
			val discoverFragment = childFragmentManager.fragments.firstOrNull { it is DiscoverFragment } as? DiscoverFragment
			discoverFragment?.showServicePopup(anchor)
			return true // Handled
		}
		return false // Default behavior (show SourceTag popup)
	}
}
