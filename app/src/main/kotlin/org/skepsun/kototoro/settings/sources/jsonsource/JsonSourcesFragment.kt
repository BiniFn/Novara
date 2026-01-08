package org.skepsun.kototoro.settings.sources.jsonsource

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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
 */
@AndroidEntryPoint
class JsonSourcesFragment :
	BaseFragment<FragmentJsonSourcesBinding>(),
	GroupedJsonSourcesAdapter.GroupedSourceListener,
	RecyclerViewOwner {
	
	private val viewModel by viewModels<JsonSourcesViewModel>()
	private val importViewModel by viewModels<ImportJsonViewModel>()
	private var adapter: GroupedJsonSourcesAdapter? = null
	private val selectedIds = mutableSetOf<String>()
	
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
		
		// Observe grouped sources
		viewModel.groupedSources.observe(viewLifecycleOwner) { groupedList ->
			val flatList = groupedList.toFlatList()
			adapter?.submitList(flatList)
			adapter?.selectedIds = selectedIds
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
		
		addMenuProvider(JsonSourcesMenuProvider())
	}
	
	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		val isTablet = !resources.getBoolean(R.bool.is_tablet)
		v.setPaddingRelative(
			if (isTablet) 0 else barsInsets.start(v),
			barsInsets.top, // avoid status bar overlap
			if (isTablet) 0 else barsInsets.end(v),
			barsInsets.bottom,
		)
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
		adapter?.notifyDataSetChanged()
	}
	
	override fun onSelectSource(sourceId: String, selected: Boolean) {
		if (selected) selectedIds.add(sourceId) else selectedIds.remove(sourceId)
		adapter?.selectedIds = selectedIds
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
			menuInflater.inflate(R.menu.opt_json_sources, menu)
		}
		
		override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
			return when (menuItem.itemId) {
				R.id.action_import -> {
					showImportDialog()
					true
				}
				R.id.action_enable_selected -> {
					if (selectedIds.isEmpty()) {
						showSnackbar(getString(R.string.batch_no_selection))
						true
					} else {
						viewModel.batchEnable(selectedIds.toList(), true)
						showSnackbar(getString(R.string.enable_selected))
						true
					}
				}
				R.id.action_disable_selected -> {
					if (selectedIds.isEmpty()) {
						showSnackbar(getString(R.string.batch_no_selection))
						true
					} else {
						viewModel.batchEnable(selectedIds.toList(), false)
						showSnackbar(getString(R.string.disable_selected))
						true
					}
				}
				R.id.action_delete_selected -> {
					if (selectedIds.isEmpty()) {
						showSnackbar(getString(R.string.batch_no_selection))
						true
					} else {
						viewModel.batchDelete(selectedIds.toList())
						selectedIds.clear()
						adapter?.selectedIds = selectedIds
						showSnackbar(getString(R.string.delete_selected))
						true
					}
				}
				R.id.action_validate_selected -> {
					if (selectedIds.isEmpty()) {
						showSnackbar(getString(R.string.batch_no_selection))
						true
					} else {
						viewModel.batchValidate(selectedIds.toList())
						showSnackbar(getString(R.string.validate_selected))
						true
					}
				}
				R.id.action_select_all -> {
					val allIds = viewModel.getJsonSourceIds()
					selectedIds.clear()
					selectedIds.addAll(allIds)
					adapter?.selectedIds = selectedIds
					adapter?.notifyDataSetChanged()
					true
				}
				R.id.action_deselect_all -> {
					selectedIds.clear()
					adapter?.selectedIds = selectedIds
					adapter?.notifyDataSetChanged()
					true
				}
				else -> false
			}
		}
	}
}
