package org.skepsun.kototoro.settings.sources.extensions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.getDisplayName
import org.skepsun.kototoro.core.util.ext.addMenuProvider
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.toLocaleOrNull
import org.skepsun.kototoro.databinding.FragmentInstalledExtensionsBinding
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import org.skepsun.kototoro.settings.SettingsActivity
import java.util.Locale

@AndroidEntryPoint
class ExtensionsBrowserFragment : BaseFragment<FragmentInstalledExtensionsBinding>() {

	@Inject
	lateinit var settings: AppSettings

	private val viewModel by viewModels<ExtensionsBrowserViewModel>()
	private var adapter: ExtensionsBrowserAdapter? = null
	private val installLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
		viewModel.onInstallActivityResult()
	}

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): FragmentInstalledExtensionsBinding {
		return FragmentInstalledExtensionsBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentInstalledExtensionsBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		adapter = ExtensionsBrowserAdapter(
			viewModel::toggleLanguageGroup,
			viewModel::onPrimaryAction,
			viewModel::uninstall,
			viewModel::cancelInstall,
		)
		with(binding) {
			recyclerView.layoutManager = createLayoutManager()
			recyclerView.adapter = adapter
			textEmptyTitle.setText(R.string.no_available_extensions)
			textEmptyText.setText(R.string.no_available_extensions_text)
			swipeRefresh.setOnRefreshListener(viewModel::refresh)
		}
		observeViewModel(binding)
		addMenuProvider(BrowserMenuProvider())
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onDestroyView() {
		adapter = null
		super.onDestroyView()
	}

	private fun observeViewModel(binding: FragmentInstalledExtensionsBinding) {
		viewModel.items.observe(viewLifecycleOwner) { items ->
			adapter?.submitList(items)
			binding.recyclerView.isVisible = items.isNotEmpty()
			binding.emptyGroup.isVisible = items.isEmpty() && !viewModel.isLoading.value
			updateEmptyState(binding, items.isEmpty())
		}
		viewModel.installedCount.observe(viewLifecycleOwner) { installedCount ->
			binding.textExtensionCount.text = getString(R.string.installed_section_count, installedCount)
			binding.textExtensionCount.isVisible = installedCount > 0
			binding.headerGroup.isVisible = installedCount > 0 || viewModel.updateCount.value > 0 || viewModel.availableCount.value > 0
			updateEmptyState(binding, adapter?.currentList.isNullOrEmpty())
		}
		viewModel.updateCount.observe(viewLifecycleOwner) { updateCount ->
			binding.textSourceCount.text = getString(R.string.browser_counts_pattern, updateCount, viewModel.availableCount.value)
			binding.textSourceCount.isVisible = updateCount > 0 || viewModel.availableCount.value > 0
			binding.headerGroup.isVisible = updateCount > 0 || viewModel.availableCount.value > 0 || viewModel.installedCount.value > 0
		}
		viewModel.availableCount.observe(viewLifecycleOwner) { availableCount ->
			binding.textSourceCount.text = getString(R.string.browser_counts_pattern, viewModel.updateCount.value, availableCount)
			binding.textSourceCount.isVisible = viewModel.updateCount.value > 0 || availableCount > 0
			binding.headerGroup.isVisible = availableCount > 0 || viewModel.updateCount.value > 0 || viewModel.installedCount.value > 0
		}
		viewModel.repoCount.observe(viewLifecycleOwner) {
			updateEmptyState(binding, adapter?.currentList.isNullOrEmpty())
		}
		viewModel.currentSearchQuery.observe(viewLifecycleOwner) {
			updateEmptyState(binding, adapter?.currentList.isNullOrEmpty())
		}
		viewModel.currentLanguageFilter.observe(viewLifecycleOwner) {
			updateEmptyState(binding, adapter?.currentList.isNullOrEmpty())
			activity?.invalidateOptionsMenu()
		}
		viewModel.currentCollapsedLanguageGroups.observe(viewLifecycleOwner) {
			binding.recyclerView.layoutManager?.requestLayout()
		}
		viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
			binding.swipeRefresh.isRefreshing = isLoading
			binding.progressBar.isVisible = isLoading && adapter?.currentList.isNullOrEmpty()
			updateEmptyState(binding, adapter?.currentList.isNullOrEmpty())
		}
		viewModel.onInstallIntent.observeEvent(viewLifecycleOwner) { intent ->
			installLauncher.launch(intent)
		}
		viewModel.onUninstallIntent.observeEvent(viewLifecycleOwner) { intent ->
			startActivity(intent)
		}
		viewModel.onStateDetails.observeEvent(viewLifecycleOwner, ::openStateDetailsDialog)
		viewModel.onMessage.observeEvent(viewLifecycleOwner) { message ->
			Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
		}
		viewModel.updateAllInProgress.observe(viewLifecycleOwner) {
			activity?.invalidateOptionsMenu()
		}
		viewModel.updateCount.observe(viewLifecycleOwner) {
			activity?.invalidateOptionsMenu()
		}
	}

	private fun openStateDetailsDialog(item: ExtensionsBrowserListItem.Entry) {
		val (titleRes, message) = when (item.state) {
			ExtensionsBrowserEntryState.UNTRUSTED -> {
				R.string.untrusted_extension to getString(
					R.string.untrusted_extension_message,
					item.name,
					item.pkgName,
					item.extension.signatureHash.formatExtensionFingerprint(),
				)
			}

			ExtensionsBrowserEntryState.INCOMPATIBLE -> {
				R.string.incompatible_extension to getString(
					R.string.incompatible_extension_message,
					item.name,
					item.versionName,
					item.extension.libVersion.toString(),
				)
			}

			else -> return
		}
		val builder = MaterialAlertDialogBuilder(requireContext())
			.setTitle(titleRes)
			.setMessage(message)
			.setNeutralButton(R.string.manage_extension_repositories) { _, _ ->
				openRepositories()
			}
		if (item.installedVersionName != null) {
			builder
				.setPositiveButton(R.string.remove) { _, _ ->
					viewModel.uninstall(item)
				}
				.setNegativeButton(android.R.string.cancel, null)
		} else {
			builder.setPositiveButton(android.R.string.ok, null)
		}
		builder.show()
	}

	private fun openRepositories() {
		(activity as? SettingsActivity)?.openFragment(
			fragmentClass = ExtensionRepositoriesFragment::class.java,
			args = Bundle(1).apply { putString(ARG_EXTENSION_TYPE, viewModel.type.name) },
			isFromRoot = false,
		)
	}

	private fun updateEmptyState(binding: FragmentInstalledExtensionsBinding, isEmpty: Boolean) {
		if (!isEmpty) {
			return
		}
		when {
			viewModel.currentSearchQuery.value.isNotBlank() -> {
				binding.textEmptyTitle.setText(R.string.nothing_found)
				binding.textEmptyText.setText(R.string.text_search_holder_secondary)
			}

			viewModel.repoCount.value == 0 && viewModel.installedCount.value == 0 -> {
				binding.textEmptyTitle.setText(R.string.no_available_extensions)
				binding.textEmptyText.setText(R.string.no_available_extensions_no_repo_text)
			}

			viewModel.currentLanguageFilter.value != ExtensionsLanguageFilter.All -> {
				binding.textEmptyTitle.setText(R.string.no_available_extensions)
				binding.textEmptyText.setText(R.string.no_available_extensions_language_filtered_text)
			}

			else -> {
				binding.textEmptyTitle.setText(R.string.no_available_extensions)
				binding.textEmptyText.setText(R.string.no_available_extensions_text)
			}
		}
	}

	private fun openLanguageFilterDialog() {
		val options = buildLanguageFilterOptions()
		val labels = options.map { it.label }.toTypedArray()
		val checkedIndex = options.indexOfFirst { it.filter == viewModel.currentLanguageFilter.value }
			.takeIf { it >= 0 } ?: 0
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.filter_extensions_by_language)
			.setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
				options.getOrNull(which)?.let { option ->
					viewModel.setLanguageFilter(option.filter)
				}
				dialog.dismiss()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun buildLanguageFilterOptions(): List<LanguageFilterOption> {
		val languageOptions = viewModel.availableLanguageCodes.value
			.distinct()
			.map { code ->
				val locale = if (code.isBlank()) Locale.ROOT else code.toLocaleOrNull()
				LanguageFilterOption(
					filter = ExtensionsLanguageFilter.Single(code),
					label = locale.getDisplayName(requireContext()),
				)
			}
			.sortedBy { it.label.lowercase() }

		return buildList {
			add(LanguageFilterOption(ExtensionsLanguageFilter.All, getString(R.string.all_languages)))
			add(
				LanguageFilterOption(
					ExtensionsLanguageFilter.SelectedContent,
					getString(R.string.selected_content_languages),
				),
			)
			addAll(languageOptions)
		}
	}

	private fun toggleLayoutMode() {
		settings.isExtensionsGridMode = !settings.isExtensionsGridMode
		requireViewBinding().recyclerView.layoutManager = createLayoutManager()
		activity?.invalidateOptionsMenu()
	}

	private fun createLayoutManager(): androidx.recyclerview.widget.RecyclerView.LayoutManager {
		val spanCount = if (settings.isExtensionsGridMode) 2 else 1
		return GridLayoutManager(requireContext(), spanCount).apply {
			spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
				override fun getSpanSize(position: Int): Int {
					return adapter?.getSpanSize(position, spanCount) ?: spanCount
				}
			}
		}
	}

	private inner class BrowserMenuProvider : MenuProvider, MenuItem.OnActionExpandListener, SearchView.OnQueryTextListener {
		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(R.menu.menu_available_extensions, menu)
			menu.findItem(R.id.action_search)?.run {
				setOnActionExpandListener(this@BrowserMenuProvider)
				(actionView as? SearchView)?.apply {
					setOnQueryTextListener(this@BrowserMenuProvider)
					queryHint = context.getString(R.string.search_sources)
				}
			}
		}

		override fun onPrepareMenu(menu: Menu) {
			menu.findItem(R.id.action_update_all_extensions)?.apply {
				isVisible = viewModel.updateCount.value > 0 || viewModel.updateAllInProgress.value
				title = getString(
					if (viewModel.updateAllInProgress.value) {
						R.string.cancel_update_all_extensions
					} else {
						R.string.update_all_extensions
					},
				)
			}
			menu.findItem(R.id.action_filter_languages)?.title = when (val filter = viewModel.currentLanguageFilter.value) {
				ExtensionsLanguageFilter.All -> getString(R.string.filter_extensions_by_language)
				ExtensionsLanguageFilter.SelectedContent -> getString(R.string.filter_extensions_by_selected_content_languages)
				is ExtensionsLanguageFilter.Single -> {
					val locale = if (filter.languageCode.isBlank()) Locale.ROOT else filter.languageCode.toLocaleOrNull()
					getString(R.string.filter_extensions_by_language_with_value, locale.getDisplayName(requireContext()))
				}
			}
			menu.findItem(R.id.action_toggle_extensions_layout)?.title = getString(
				if (settings.isExtensionsGridMode) {
					R.string.show_extensions_in_list
				} else {
					R.string.show_extensions_in_grid
				},
			)
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
			R.id.action_refresh_available_extensions -> {
				viewModel.refresh()
				true
			}

			R.id.action_update_all_extensions -> {
				viewModel.onUpdateAllAction()
				true
			}

			R.id.action_filter_languages -> {
				openLanguageFilterDialog()
				true
			}

			R.id.action_toggle_extensions_layout -> {
				toggleLayoutMode()
				true
			}

			R.id.action_manage_repositories -> {
				openRepositories()
				true
			}

			else -> false
		}

		override fun onMenuItemActionExpand(item: MenuItem): Boolean {
			(item.actionView as? SearchView)?.setQuery(viewModel.currentSearchQuery.value, false)
			return true
		}

		override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
			viewModel.setSearchQuery(null)
			return true
		}

		override fun onQueryTextSubmit(query: String?): Boolean {
			viewModel.setSearchQuery(query)
			return true
		}

		override fun onQueryTextChange(newText: String?): Boolean {
			viewModel.setSearchQuery(newText)
			return true
		}
	}

	private data class LanguageFilterOption(
		val filter: ExtensionsLanguageFilter,
		val label: String,
	)
}
