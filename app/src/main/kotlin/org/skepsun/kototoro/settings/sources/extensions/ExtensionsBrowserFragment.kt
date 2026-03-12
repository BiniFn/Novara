package org.skepsun.kototoro.settings.sources.extensions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.addMenuProvider
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.databinding.FragmentInstalledExtensionsBinding
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import org.skepsun.kototoro.settings.SettingsActivity

@AndroidEntryPoint
class ExtensionsBrowserFragment : BaseFragment<FragmentInstalledExtensionsBinding>() {

	private val viewModel by viewModels<ExtensionsBrowserViewModel>()
	private var adapter: ExtensionsBrowserAdapter? = null

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): FragmentInstalledExtensionsBinding {
		return FragmentInstalledExtensionsBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentInstalledExtensionsBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		adapter = ExtensionsBrowserAdapter(viewModel::onPrimaryAction, viewModel::uninstall)
		with(binding) {
			recyclerView.layoutManager = LinearLayoutManager(context)
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
		viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
			binding.swipeRefresh.isRefreshing = isLoading
			binding.progressBar.isVisible = isLoading && adapter?.currentList.isNullOrEmpty()
			updateEmptyState(binding, adapter?.currentList.isNullOrEmpty())
		}
		viewModel.onInstallIntent.observeEvent(viewLifecycleOwner) { intent ->
			startActivity(intent)
		}
		viewModel.onUninstallIntent.observeEvent(viewLifecycleOwner) { intent ->
			startActivity(intent)
		}
		viewModel.onStateDetails.observeEvent(viewLifecycleOwner, ::openStateDetailsDialog)
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

			else -> {
				binding.textEmptyTitle.setText(R.string.no_available_extensions)
				binding.textEmptyText.setText(R.string.no_available_extensions_text)
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

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
			R.id.action_refresh_available_extensions -> {
				viewModel.refresh()
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
}
