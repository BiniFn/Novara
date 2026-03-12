package org.skepsun.kototoro.settings.sources.extensions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
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
class AvailableExtensionsFragment : BaseFragment<FragmentInstalledExtensionsBinding>() {

	private val viewModel by viewModels<AvailableExtensionsViewModel>()
	private var adapter: AvailableExtensionsAdapter? = null

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): FragmentInstalledExtensionsBinding {
		return FragmentInstalledExtensionsBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentInstalledExtensionsBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		adapter = AvailableExtensionsAdapter(viewModel::install)
		with(binding) {
			recyclerView.layoutManager = LinearLayoutManager(context)
			recyclerView.adapter = adapter
			textEmptyTitle.setText(R.string.no_available_extensions)
			textEmptyText.setText(R.string.no_available_extensions_text)
			swipeRefresh.setOnRefreshListener(viewModel::refresh)
		}
		observeViewModel(binding)
		addMenuProvider(AvailableMenuProvider())
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onResume() {
		super.onResume()
		activity?.setTitle(
			when (viewModel.type) {
				ExternalExtensionType.MIHON -> R.string.mihon_available_extensions
				ExternalExtensionType.ANIYOMI -> R.string.aniyomi_available_extensions
			},
		)
	}

	override fun onDestroyView() {
		adapter = null
		super.onDestroyView()
	}

	private fun observeViewModel(binding: FragmentInstalledExtensionsBinding) {
		viewModel.items.observe(viewLifecycleOwner) { items ->
			adapter?.submitList(items)
			binding.recyclerView.isVisible = items.isNotEmpty()
			binding.emptyGroup.isVisible = items.isEmpty() && !viewModel.isLoading.value
			binding.textEmptyText.setText(
				if (viewModel.repoCount.value == 0) {
					R.string.no_available_extensions_no_repo_text
				} else {
					R.string.no_available_extensions_text
				},
			)
		}
		viewModel.availableCount.observe(viewLifecycleOwner) { count ->
			binding.textExtensionCount.text = getString(R.string.available_extension_count, count)
			binding.textExtensionCount.isVisible = count > 0
			binding.headerGroup.isVisible = count > 0 || viewModel.updateCount.value > 0
		}
		viewModel.updateCount.observe(viewLifecycleOwner) { count ->
			binding.textSourceCount.text = getString(R.string.available_extension_update_count, count)
			binding.textSourceCount.isVisible = count > 0
			binding.headerGroup.isVisible = count > 0 || viewModel.availableCount.value > 0
		}
		viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
			binding.swipeRefresh.isRefreshing = isLoading
			binding.progressBar.isVisible = isLoading && adapter?.currentList.isNullOrEmpty()
		}
		viewModel.onInstallIntent.observeEvent(viewLifecycleOwner) { intent ->
			startActivity(intent)
		}
		viewModel.onMessage.observeEvent(viewLifecycleOwner) { message ->
			Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
		}
	}

	private inner class AvailableMenuProvider : MenuProvider {
		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(R.menu.menu_available_extensions, menu)
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
			R.id.action_refresh_available_extensions -> {
				viewModel.refresh()
				true
			}

			R.id.action_manage_repositories -> {
				(activity as? SettingsActivity)?.openFragment(
					fragmentClass = ExtensionRepositoriesFragment::class.java,
					args = Bundle(1).apply { putString(ARG_EXTENSION_TYPE, viewModel.type.name) },
					isFromRoot = false,
				)
				true
			}

			else -> false
		}
	}
}
