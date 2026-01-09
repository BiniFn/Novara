package org.skepsun.kototoro.settings.sources.jsonsource

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.jsonsource.SourceGroup
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.ui.util.RecyclerViewOwner
import org.skepsun.kototoro.core.ui.widgets.SelectActionBar
import org.skepsun.kototoro.core.util.ext.addMenuProvider
import org.skepsun.kototoro.core.util.ext.consumeAllSystemBarsInsets
import org.skepsun.kototoro.core.util.ext.end
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.start
import org.skepsun.kototoro.core.util.ext.systemBarsInsets
import org.skepsun.kototoro.databinding.FragmentJsonSourcesBinding

/**
 * Fragment for managing JSON sources with grouped display.
 * 
 * Displays sources organized by groups with options to:
 * - View sources grouped by content type or origin type
 * - Collapse/expand groups
 * - Enable/disable sources
 * - Test sources
 * - Delete sources
 * - Import new sources
 * - Batch operations via SelectActionBar
 */
@AndroidEntryPoint
class JsonSourcesFragment :
	BaseFragment<FragmentJsonSourcesBinding>(),
	GroupedJsonSourcesAdapter.GroupedSourceListener,
	RecyclerViewOwner,
	SelectActionBar.Callback,
	PopupMenu.OnMenuItemClickListener {
	
	private val viewModel by viewModels<JsonSourcesViewModel>()
	private val importViewModel by viewModels<ImportJsonViewModel>()
	private var adapter: GroupedJsonSourcesAdapter? = null
	private val selectedIds = mutableSetOf<String>()
	
	// Current sort and filter state
	private var currentSort = SortOption.NAME
	private var currentFilter = FilterOption.ALL
	
	override val recyclerView: RecyclerView?
		get() = viewBinding?.recyclerView
	
	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentJsonSourcesBinding.inflate(inflater, container, false)
	
	override fun onViewBindingCreated(
		binding: FragmentJsonSourcesBinding,
		savedInstanceState: Bundle?,
	) {
		super.onViewBindingCreated(binding, savedInstanceState)
		
		adapter = GroupedJsonSourcesAdapter(this)
		binding.recyclerView.adapter = adapter
		
		// Setup SelectActionBar
		setupSelectActionBar(binding)
		
		// Observe grouped sources
		viewModel.groupedSources.observe(viewLifecycleOwner) { groupedList ->
			val flatList = groupedList.toFlatList()
			adapter?.submitList(flatList)
			adapter?.selectedIds = selectedIds
			updateCountView()
		}
		
		// Observe validation states to update badges
		viewModel.validationStates.observe(viewLifecycleOwner) { states ->
			adapter?.validationStates = states
			adapter?.notifyDataSetChanged()
		}
		
		// Observe test results
		viewModel.testResult.observe(viewLifecycleOwner) { result ->
			when (result) {
				is TestResult.Testing -> {
					showSnackbar("Testing source...")
				}
				is TestResult.Success -> {
					showSnackbar(result.message)
					viewModel.clearTestResult()
				}
				is TestResult.Error -> {
					showSnackbar("Test failed: ${result.message}")
					viewModel.clearTestResult()
				}
				null -> {
					// No result to show
				}
			}
		}
		
		// Observe last invalid IDs to auto-select invalid sources after validation
		viewModel.lastInvalidIds.observe(viewLifecycleOwner) { invalidIds ->
			if (invalidIds.isNotEmpty()) {
				selectedIds.clear()
				selectedIds.addAll(invalidIds)
				adapter?.selectedIds = selectedIds
				adapter?.notifyDataSetChanged()
				updateCountView()
				showSnackbar("已选中 ${invalidIds.size} 个失效源")
				viewModel.clearLastInvalidIds()
			}
		}
		
		addMenuProvider(JsonSourcesMenuProvider())
	}
	
	private fun setupSelectActionBar(binding: FragmentJsonSourcesBinding) {
		binding.selectActionBar.setMainActionText(R.string.delete)
		binding.selectActionBar.setCallback(this)
		binding.selectActionBar.inflateMenu(R.menu.menu_json_sources_sel)
		binding.selectActionBar.setOnMenuItemClickListener(this)
		updateCountView()
	}
	
	private fun updateCountView() {
		val totalCount = viewModel.getJsonSourceIds().size
		viewBinding?.selectActionBar?.updateCount(selectedIds.size, totalCount)
	}
	
	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		val isTablet = !resources.getBoolean(R.bool.is_tablet)
		v.setPaddingRelative(
			if (isTablet) 0 else barsInsets.start(v),
			barsInsets.top, // avoid status bar overlap
			if (isTablet) 0 else barsInsets.end(v),
			0, // SelectActionBar handles bottom padding
		)
		viewBinding?.selectActionBar?.setPadding(0, 0, 0, barsInsets.bottom)
		return insets.consumeAllSystemBarsInsets()
	}
	
	override fun onResume() {
		super.onResume()
		activity?.setTitle(R.string.json_sources)
	}
	
	override fun onDestroyView() {
		adapter = null
		super.onDestroyView()
	}
	
	// ---- GroupedSourceListener ----
	
	override fun onGroupHeaderClick(group: SourceGroup) {
		viewModel.toggleGroupCollapsed(group)
	}
	
	override fun onToggleEnabled(sourceId: String, enabled: Boolean) {
		viewModel.toggleSource(sourceId, enabled)
	}
	
	override fun onTestSource(sourceId: String) {
		viewModel.testSource(sourceId)
	}
	
	override fun onDeleteSource(sourceId: String) {
		viewModel.deleteSource(sourceId)
		showSnackbar(getString(R.string.source_deleted))
		selectedIds.remove(sourceId)
		adapter?.selectedIds = selectedIds
		updateCountView()
	}
	
	override fun onSelectSource(sourceId: String, selected: Boolean) {
		if (selected) selectedIds.add(sourceId) else selectedIds.remove(sourceId)
		adapter?.selectedIds = selectedIds
		updateCountView()
	}
	
	// ---- SelectActionBar.Callback ----
	
	override fun selectAll(selectAll: Boolean) {
		if (selectAll) {
			val allIds = viewModel.getJsonSourceIds()
			selectedIds.clear()
			selectedIds.addAll(allIds)
		} else {
			selectedIds.clear()
		}
		adapter?.selectedIds = selectedIds
		adapter?.notifyDataSetChanged()
		updateCountView()
	}
	
	override fun revertSelection() {
		val allIds = viewModel.getJsonSourceIds().toSet()
		val newSelection = allIds - selectedIds
		selectedIds.clear()
		selectedIds.addAll(newSelection)
		adapter?.selectedIds = selectedIds
		adapter?.notifyDataSetChanged()
		updateCountView()
	}
	
	override fun onClickMainAction() {
		if (selectedIds.isEmpty()) {
			showSnackbar(getString(R.string.batch_no_selection))
		} else {
			viewModel.batchDelete(selectedIds.toList())
			selectedIds.clear()
			adapter?.selectedIds = selectedIds
			showSnackbar(getString(R.string.delete_selected))
			updateCountView()
		}
	}
	
	// ---- PopupMenu.OnMenuItemClickListener ----
	
	override fun onMenuItemClick(item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.menu_enable_selection -> {
				if (selectedIds.isEmpty()) {
					showSnackbar(getString(R.string.batch_no_selection))
				} else {
					viewModel.batchEnable(selectedIds.toList(), true)
					showSnackbar(getString(R.string.enable_selected))
				}
				true
			}
			R.id.menu_disable_selection -> {
				if (selectedIds.isEmpty()) {
					showSnackbar(getString(R.string.batch_no_selection))
				} else {
					viewModel.batchEnable(selectedIds.toList(), false)
					showSnackbar(getString(R.string.disable_selected))
				}
				true
			}
			R.id.menu_validate_selection -> {
				if (selectedIds.isEmpty()) {
					showSnackbar(getString(R.string.batch_no_selection))
				} else {
					viewModel.batchValidate(selectedIds.toList())
					showSnackbar(getString(R.string.validate_selected))
				}
				true
			}
			else -> false
		}
	}
	
	private fun showSnackbar(message: String) {
		viewBinding?.recyclerView?.let { view ->
			Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show()
		}
	}
	
	private fun showImportDialog() {
		ImportJsonDialogFragment().show(childFragmentManager, "import_json")
	}
	
	private inner class JsonSourcesMenuProvider : MenuProvider {
		
		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(R.menu.menu_json_sources, menu)
		}
		
		override fun onPrepareMenu(menu: Menu) {
			// Update sort checkmarks
			val sortSubMenu = menu.findItem(R.id.action_sort)?.subMenu
			sortSubMenu?.findItem(R.id.menu_sort_name)?.isChecked = currentSort == SortOption.NAME
			sortSubMenu?.findItem(R.id.menu_sort_enabled)?.isChecked = currentSort == SortOption.ENABLED
			
			// Update filter checkmarks
			val filterSubMenu = menu.findItem(R.id.action_filter)?.subMenu
			filterSubMenu?.findItem(R.id.menu_filter_all)?.isChecked = currentFilter == FilterOption.ALL
			filterSubMenu?.findItem(R.id.menu_filter_enabled)?.isChecked = currentFilter == FilterOption.ENABLED
			filterSubMenu?.findItem(R.id.menu_filter_disabled)?.isChecked = currentFilter == FilterOption.DISABLED
			filterSubMenu?.findItem(R.id.menu_filter_invalid)?.isChecked = currentFilter == FilterOption.INVALID
		}
		
		override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
			return when (menuItem.itemId) {
				R.id.action_import -> {
					showImportDialog()
					true
				}
				R.id.menu_sort_name -> {
					currentSort = SortOption.NAME
					viewModel.setSortOption(currentSort)
					activity?.invalidateOptionsMenu()
					true
				}
				R.id.menu_sort_enabled -> {
					currentSort = SortOption.ENABLED
					viewModel.setSortOption(currentSort)
					activity?.invalidateOptionsMenu()
					true
				}
				R.id.menu_filter_all -> {
					currentFilter = FilterOption.ALL
					viewModel.setFilterOption(currentFilter)
					activity?.invalidateOptionsMenu()
					true
				}
				R.id.menu_filter_enabled -> {
					currentFilter = FilterOption.ENABLED
					viewModel.setFilterOption(currentFilter)
					activity?.invalidateOptionsMenu()
					true
				}
				R.id.menu_filter_disabled -> {
					currentFilter = FilterOption.DISABLED
					viewModel.setFilterOption(currentFilter)
					activity?.invalidateOptionsMenu()
					true
				}
				R.id.menu_filter_invalid -> {
					currentFilter = FilterOption.INVALID
					viewModel.setFilterOption(currentFilter)
					activity?.invalidateOptionsMenu()
					true
				}
				else -> false
			}
		}
	}
}

enum class SortOption {
	NAME, ENABLED
}

enum class FilterOption {
	ALL, ENABLED, DISABLED, INVALID
}
