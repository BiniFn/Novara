package org.skepsun.kototoro.settings.sources.extensions
import org.skepsun.kototoro.core.util.ext.setSupportTitle

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.addSupportMenuProvider
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.databinding.FragmentInstalledExtensionsBinding
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.settings.sources.unified.redirectToUnifiedSources
import org.skepsun.kototoro.settings.sources.unified.toUnifiedSourceKind

abstract class BaseInstalledExtensionsFragment<VM> : BaseFragment<FragmentInstalledExtensionsBinding>()
	where VM : ViewModel, VM : InstalledExtensionsScreenModel {

	protected abstract val viewModel: VM

	@get:StringRes
	protected abstract val emptyTitleRes: Int

	@get:StringRes
	protected abstract val emptyTextRes: Int

	@get:StringRes
	protected abstract val extensionCountRes: Int

	@get:StringRes
	protected abstract val sourceCountRes: Int

	@get:StringRes
	protected abstract val titleRes: Int

	protected abstract val extensionType: ExternalExtensionType

	private var adapter: InstalledExtensionsAdapter? = null

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): FragmentInstalledExtensionsBinding {
		return FragmentInstalledExtensionsBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentInstalledExtensionsBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		adapter = InstalledExtensionsAdapter { item ->
			val sources = viewModel.getSourcesForPackage(item.pkgName)
			if (sources.isEmpty()) return@InstalledExtensionsAdapter
			if (sources.size == 1) {
				router.openList(sources.first(), null, null)
			} else {
				val displayNames = sources.map { 
					val lang = org.skepsun.kototoro.extensions.runtime.getExternalExtensionLanguageDisplayName(it.locale)
					if (lang.isNotEmpty()) lang else it.locale.uppercase()
				}.toTypedArray()
				com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
					.setTitle(item.appName)
					.setItems(displayNames) { _, which ->
						router.openList(sources[which], null, null)
					}
					.show()
			}
		}

		with(binding) {
			textEmptyTitle.setText(emptyTitleRes)
			textEmptyText.setText(emptyTextRes)
			recyclerView.layoutManager = LinearLayoutManager(context)
			recyclerView.adapter = adapter
			swipeRefresh.setOnRefreshListener(viewModel::refresh)
		}

		observeViewModel(binding)
		addSupportMenuProvider(ExtensionsMenuProvider())
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onResume() {
		super.onResume()
		setSupportTitle(titleRes)
	}

	override fun onDestroyView() {
		adapter = null
		super.onDestroyView()
	}

	private fun observeViewModel(binding: FragmentInstalledExtensionsBinding) {
		viewModel.extensions.observe(viewLifecycleOwner) { extensions ->
			adapter?.submitList(extensions)
			binding.emptyGroup.isVisible = extensions.isEmpty() && !viewModel.isLoading.value
			binding.recyclerView.isVisible = extensions.isNotEmpty()
		}

		viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
			binding.swipeRefresh.isRefreshing = isLoading
			binding.progressBar.isVisible = isLoading && adapter?.currentList.isNullOrEmpty()
		}

		viewModel.extensionCount.observe(viewLifecycleOwner) { count ->
			binding.textExtensionCount.text = getString(extensionCountRes, count)
			binding.textExtensionCount.isVisible = count > 0
			binding.headerGroup.isVisible = count > 0 || viewModel.sourceCount.value > 0
		}

		viewModel.sourceCount.observe(viewLifecycleOwner) { count ->
			binding.textSourceCount.text = getString(sourceCountRes, count)
			binding.textSourceCount.isVisible = count > 0
			binding.headerGroup.isVisible = count > 0 || viewModel.extensionCount.value > 0
		}
	}

	private inner class ExtensionsMenuProvider : MenuProvider {

		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(org.skepsun.kototoro.R.menu.menu_extensions, menu)
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
			org.skepsun.kototoro.R.id.action_available_extensions -> {
				redirectToUnifiedSources(extensionType.toUnifiedSourceKind())
				true
			}

			org.skepsun.kototoro.R.id.action_manage_repositories -> {
				redirectToUnifiedSources(extensionType.toUnifiedSourceKind())
				true
			}

			else -> false
		}
	}
}
