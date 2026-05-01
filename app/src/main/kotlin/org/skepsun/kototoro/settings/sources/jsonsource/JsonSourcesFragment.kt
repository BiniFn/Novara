package org.skepsun.kototoro.settings.sources.jsonsource
import org.skepsun.kototoro.core.util.ext.setSupportTitle

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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.core.jsonsource.SourceGroup
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.ui.util.RecyclerViewOwner
import org.skepsun.kototoro.core.ui.widgets.SelectActionBar
import org.skepsun.kototoro.core.util.ext.addSupportMenuProvider
import org.skepsun.kototoro.core.util.ext.invalidateSupportMenu
import org.skepsun.kototoro.core.util.ext.consumeAllSystemBarsInsets
import org.skepsun.kototoro.core.util.ext.end
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.start
import org.skepsun.kototoro.core.util.ext.systemBarsInsets
import org.skepsun.kototoro.databinding.FragmentJsonSourcesBinding
import org.skepsun.kototoro.settings.sources.unified.redirectToUnifiedSources
import org.skepsun.kototoro.settings.sources.unified.toUnifiedSourceKind

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
	private val tvBoxRepositoryMenuIds = linkedMapOf<Int, String>()
	
	// Current sort and filter state
	private var currentSort: SortOption = SortOption.NAME
	private var currentFilter: FilterOption = FilterOption.ALL
	private var currentGrouping = org.skepsun.kototoro.core.jsonsource.GroupingStrategy.BY_ORIGIN
	private var redirected = false

	companion object {
		private const val TVBOX_REPOSITORY_MENU_BASE_ID = 50_000
		const val ARG_SOURCE_TYPE = "json_source_type"
	}
	
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
			adapter?.activeTvBoxRepositoryLocator = viewModel.activeTvBoxRepositoryLocator.value
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
				is TestResult.Success -> showSnackbar(getString(R.string.test_success))
				is TestResult.Error -> showSnackbar(getString(R.string.test_failed, result.message))
				null -> {
					// No result to show
				}
			}
		}
		
		// Observe validation progress
		var progressSnackbar: Snackbar? = null
		viewModel.validationProgress.observe(viewLifecycleOwner) { progress ->
			if (progress != null) {
				val (current, total) = progress
				val message = "正在校验源 ($current/$total)"
				if (progressSnackbar == null) {
					viewBinding?.root?.let { view ->
						progressSnackbar = Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE)
						progressSnackbar?.setAction("取消") {
							viewModel.stopValidation()
						}
						progressSnackbar?.show()
					}
				} else {
					progressSnackbar?.setText(message)
				}
			} else {
				progressSnackbar?.dismiss()
				progressSnackbar = null
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
		
		// Observe available groups to refresh menu if needed
		viewModel.availableGroups.observe(viewLifecycleOwner) {
			invalidateSupportMenu()
		}
		viewModel.tvBoxRepositories.observe(viewLifecycleOwner) {
			adapter?.activeTvBoxRepositoryLocator = viewModel.activeTvBoxRepositoryLocator.value
			adapter?.notifyDataSetChanged()
			invalidateSupportMenu()
		}
		
		addSupportMenuProvider(JsonSourcesMenuProvider())
	}

	override fun onResume() {
		super.onResume()
		if (!redirected) {
			redirected = true
			arguments
				?.getString(ARG_SOURCE_TYPE)
				?.let { runCatching { enumValueOf<JsonSourceType>(it) }.getOrNull() }
				?.let { redirectToUnifiedSources(it.toUnifiedSourceKind()) }
				?: redirectToUnifiedSources()
			return
		}
		if (parentFragment == null) {
			setSupportTitle(R.string.json_sources_directory)
		}
	}

	private fun setupSelectActionBar(binding: FragmentJsonSourcesBinding) {
		binding.selectActionBar.setMainActionText(R.string.delete)
		binding.selectActionBar.setCallback(this)
		binding.selectActionBar.inflateMenu(R.menu.menu_json_sources_sel)
		binding.selectActionBar.setOnMenuItemClickListener(this)
		updateCountView()
	}
	
	private fun updateCountView() {
		val totalCount = viewModel.getVisibleJsonSourceIds().size
		val selectedCount = selectedIds.size
		viewBinding?.selectActionBar?.updateCount(selectedCount, totalCount)
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

	override fun onEditSource(sourceId: String) {
		val intent = android.content.Intent(requireContext(), org.skepsun.kototoro.settings.sources.jsonsource.edit.JsonSourceEditActivity::class.java).apply {
			putExtra(org.skepsun.kototoro.settings.sources.jsonsource.edit.JsonSourceEditActivity.EXTRA_SOURCE_ID, sourceId)
		}
		startActivity(intent)
	}
	
	// ---- SelectActionBar.Callback ----
	
	override fun selectAll(selectAll: Boolean) {
		if (selectAll) {
			val allIds = viewModel.getVisibleJsonSourceIds()
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
		val visibleIds = viewModel.getVisibleJsonSourceIds().toSet()
		val currentVisibleSelection = visibleIds.intersect(selectedIds)
		val newVisibleSelection = visibleIds - currentVisibleSelection
		
		// Remove all visible ones first, then add the new ones
		selectedIds.removeAll(visibleIds)
		selectedIds.addAll(newVisibleSelection)
		
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
			R.id.menu_export_selection -> {
				if (selectedIds.isEmpty()) {
					showSnackbar(getString(R.string.batch_no_selection))
				} else {
					exportSources()
				}
				true
			}
			R.id.menu_share_selection -> {
				if (selectedIds.isEmpty()) {
					showSnackbar(getString(R.string.batch_no_selection))
				} else {
					shareSources()
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

	private fun showTvBoxRepositorySwitchDialog() {
		val repositories = viewModel.tvBoxRepositories.value
		if (repositories.isEmpty()) {
			showSnackbar(getString(R.string.tvbox_repository_switch_empty))
			return
		}
		val currentLocator = viewModel.activeTvBoxRepositoryLocator.value
		val labels = repositories.map { repository ->
			"${repository.title} (${repository.enabledCount}/${repository.sourceCount})"
		}.toTypedArray()
		val checkedItem = repositories.indexOfFirst { it.locator == currentLocator }
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.tvbox_repository_switch)
			.setSingleChoiceItems(labels, checkedItem) { dialog, which ->
				val repository = repositories.getOrNull(which) ?: return@setSingleChoiceItems
				viewModel.activateTvBoxRepository(repository.locator)
				showSnackbar(getString(R.string.tvbox_repository_switched, repository.title))
				dialog.dismiss()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}
	
	private fun exportSources() {
		viewLifecycleOwner.lifecycleScope.launch {
			val (json, count) = viewModel.exportSources(selectedIds.toList())
			if (count > 0) {
				// Save to file
				val fileName = "json_sources_${System.currentTimeMillis()}.json"
				val file = java.io.File(requireContext().cacheDir, fileName)
				file.writeText(json)
				
				// Open share intent to save file
				val uri = androidx.core.content.FileProvider.getUriForFile(
					requireContext(),
					"${requireContext().packageName}.fileprovider",
					file
				)
				val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
					type = "application/json"
					putExtra(android.content.Intent.EXTRA_STREAM, uri)
					addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
				}
				startActivity(android.content.Intent.createChooser(intent, getString(R.string.export_sources)))
				showSnackbar(getString(R.string.export_success_count, count))
			}
		}
	}
	
	private fun shareSources() {
		viewLifecycleOwner.lifecycleScope.launch {
			val (json, count) = viewModel.exportSources(selectedIds.toList())
			if (count > 0) {
				// Share as text
				val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
					type = "text/plain"
					putExtra(android.content.Intent.EXTRA_TEXT, json)
				}
				startActivity(android.content.Intent.createChooser(intent, getString(R.string.share_sources)))
			}
		}
	}
	
	private inner class JsonSourcesMenuProvider : MenuProvider {
		
		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(R.menu.menu_json_sources, menu)
			
			// Setup Search
			val searchItem = menu.findItem(R.id.action_search)
			val searchView = searchItem.actionView as androidx.appcompat.widget.SearchView
			searchView.queryHint = getString(R.string.search_json_source)
			searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
				override fun onQueryTextSubmit(query: String?): Boolean = false
				
				override fun onQueryTextChange(newText: String?): Boolean {
					viewModel.setSearchQuery(newText ?: "")
					return true
				}
			})
		}
		
		override fun onPrepareMenu(menu: Menu) {
			// Update sort checkmarks
			val sortSubMenu = menu.findItem(R.id.action_sort)?.subMenu
			sortSubMenu?.findItem(R.id.menu_sort_name)?.isChecked = currentSort == SortOption.NAME
			sortSubMenu?.findItem(R.id.menu_sort_enabled)?.isChecked = currentSort == SortOption.ENABLED
			
			// Update filter checkmarks
			val filterSubMenu = menu.findItem(R.id.action_filter)?.subMenu
			val statusSubMenu = filterSubMenu?.findItem(R.id.menu_filter_status)?.subMenu
			
			statusSubMenu?.findItem(R.id.menu_filter_all)?.isChecked = currentFilter is FilterOption.ALL
			statusSubMenu?.findItem(R.id.menu_filter_enabled)?.isChecked = currentFilter is FilterOption.ENABLED
			statusSubMenu?.findItem(R.id.menu_filter_disabled)?.isChecked = currentFilter is FilterOption.DISABLED
			statusSubMenu?.findItem(R.id.menu_filter_need_login)?.isChecked = currentFilter is FilterOption.NEED_LOGIN
			statusSubMenu?.findItem(R.id.menu_filter_no_group)?.isChecked = currentFilter is FilterOption.NO_GROUP
			statusSubMenu?.findItem(R.id.menu_filter_explore_enabled)?.isChecked = currentFilter is FilterOption.EXPLORE_ENABLED
			statusSubMenu?.findItem(R.id.menu_filter_explore_disabled)?.isChecked = currentFilter is FilterOption.EXPLORE_DISABLED
			statusSubMenu?.findItem(R.id.menu_filter_invalid)?.isChecked = currentFilter is FilterOption.INVALID
			
			// Dynamic source groups
			val groupsSubMenu = filterSubMenu?.findItem(R.id.menu_source_groups)?.subMenu
			groupsSubMenu?.clear()
			val availableGroups = viewModel.availableGroups.value
			availableGroups.forEach { groupName ->
				val item = groupsSubMenu?.add(Menu.NONE, Menu.NONE, Menu.NONE, groupName)
				item?.isCheckable = true
				item?.isChecked = currentFilter is FilterOption.GROUP && (currentFilter as FilterOption.GROUP).name == groupName
			}

			val repositoriesSubMenu = filterSubMenu?.findItem(R.id.menu_tvbox_repositories)?.subMenu
			repositoriesSubMenu?.clear()
			tvBoxRepositoryMenuIds.clear()
			val tvBoxRepositories = viewModel.tvBoxRepositories.value
			val isTvBoxPage = viewModel.sourceTypeFilter == JsonSourceType.TVBOX
			filterSubMenu?.findItem(R.id.menu_tvbox_repositories)?.isVisible = isTvBoxPage && tvBoxRepositories.isNotEmpty()
			tvBoxRepositories.forEachIndexed { index, repository ->
				val itemId = TVBOX_REPOSITORY_MENU_BASE_ID + index
				tvBoxRepositoryMenuIds[itemId] = repository.locator
				repositoriesSubMenu?.add(Menu.NONE, itemId, Menu.NONE, "${repository.title} (${repository.enabledCount}/${repository.sourceCount})")
					?.apply {
						isCheckable = true
						isChecked = currentFilter is FilterOption.TVBOX_REPOSITORY &&
							(currentFilter as FilterOption.TVBOX_REPOSITORY).locator == repository.locator
					}
			}
			
			// Update grouping checkmarks
			val groupBySubMenu = filterSubMenu?.findItem(R.id.action_group_by)?.subMenu
			groupBySubMenu?.findItem(R.id.menu_group_by_origin)?.isChecked = 
				currentGrouping == org.skepsun.kototoro.core.jsonsource.GroupingStrategy.BY_ORIGIN
			groupBySubMenu?.findItem(R.id.menu_group_by_type)?.isChecked = 
				currentGrouping == org.skepsun.kototoro.core.jsonsource.GroupingStrategy.BY_CONTENT
			groupBySubMenu?.findItem(R.id.menu_group_by_tvbox_repository)?.apply {
				isVisible = isTvBoxPage && viewModel.tvBoxRepositories.value.isNotEmpty()
				isChecked = currentGrouping == org.skepsun.kototoro.core.jsonsource.GroupingStrategy.BY_TVBOX_REPOSITORY
			}
			menu.findItem(R.id.action_switch_tvbox_repository)?.let { item ->
				item.isVisible = isTvBoxPage && viewModel.tvBoxRepositories.value.isNotEmpty()
				item.title = viewModel.getActiveTvBoxRepositoryTitle()?.let { activeTitle ->
					getString(R.string.tvbox_repository_switch_with_current, activeTitle)
				} ?: getString(R.string.tvbox_repository_switch)
			}
		}
		
		override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
			val itemId = menuItem.itemId
			
			// Check if it's a dynamic group item
			val availableGroups = viewModel.availableGroups.value
			val groupName = menuItem.title?.toString()
			if (groupName != null && availableGroups.contains(groupName)) {
				currentFilter = FilterOption.GROUP(groupName)
				viewModel.setFilterOption(currentFilter)
				invalidateSupportMenu()
				return true
			}

			tvBoxRepositoryMenuIds[menuItem.itemId]?.let { locator ->
				currentFilter = FilterOption.TVBOX_REPOSITORY(locator)
				viewModel.setFilterOption(currentFilter)
				invalidateSupportMenu()
				return true
			}
			
			return when (itemId) {
				R.id.action_import -> {
					showImportDialog()
					true
				}
				R.id.action_switch_tvbox_repository -> {
					showTvBoxRepositorySwitchDialog()
					true
				}
				R.id.menu_manage_groups -> {
					showSnackbar("分组管理功能开发中")
					true
				}
				R.id.menu_sort_name -> {
					currentSort = SortOption.NAME
					viewModel.setSortOption(currentSort)
					invalidateSupportMenu()
					true
				}
				R.id.menu_sort_enabled -> {
					currentSort = SortOption.ENABLED
					viewModel.setSortOption(currentSort)
					invalidateSupportMenu()
					true
				}
				R.id.menu_filter_all -> {
					currentFilter = FilterOption.ALL
					viewModel.setFilterOption(currentFilter)
					invalidateSupportMenu()
					true
				}
				R.id.menu_filter_enabled -> {
					currentFilter = FilterOption.ENABLED
					viewModel.setFilterOption(currentFilter)
					invalidateSupportMenu()
					true
				}
				R.id.menu_filter_disabled -> {
					currentFilter = FilterOption.DISABLED
					viewModel.setFilterOption(currentFilter)
					invalidateSupportMenu()
					true
				}
				R.id.menu_filter_need_login -> {
					currentFilter = FilterOption.NEED_LOGIN
					viewModel.setFilterOption(currentFilter)
					invalidateSupportMenu()
					true
				}
				R.id.menu_filter_no_group -> {
					currentFilter = FilterOption.NO_GROUP
					viewModel.setFilterOption(currentFilter)
					invalidateSupportMenu()
					true
				}
				R.id.menu_filter_explore_enabled -> {
					currentFilter = FilterOption.EXPLORE_ENABLED
					viewModel.setFilterOption(currentFilter)
					invalidateSupportMenu()
					true
				}
				R.id.menu_filter_explore_disabled -> {
					currentFilter = FilterOption.EXPLORE_DISABLED
					viewModel.setFilterOption(currentFilter)
					invalidateSupportMenu()
					true
				}
				R.id.menu_filter_invalid -> {
					currentFilter = FilterOption.INVALID
					viewModel.setFilterOption(currentFilter)
					invalidateSupportMenu()
					true
				}
				R.id.menu_group_by_origin -> {
					currentGrouping = org.skepsun.kototoro.core.jsonsource.GroupingStrategy.BY_ORIGIN
					viewModel.setGroupingStrategy(currentGrouping)
					invalidateSupportMenu()
					true
				}
				R.id.menu_group_by_type -> {
					currentGrouping = org.skepsun.kototoro.core.jsonsource.GroupingStrategy.BY_CONTENT
					viewModel.setGroupingStrategy(currentGrouping)
					invalidateSupportMenu()
					true
				}
				R.id.menu_group_by_tvbox_repository -> {
					currentGrouping = org.skepsun.kototoro.core.jsonsource.GroupingStrategy.BY_TVBOX_REPOSITORY
					viewModel.setGroupingStrategy(currentGrouping)
					invalidateSupportMenu()
					true
				}
				else -> false
			}
		}
	}
}
