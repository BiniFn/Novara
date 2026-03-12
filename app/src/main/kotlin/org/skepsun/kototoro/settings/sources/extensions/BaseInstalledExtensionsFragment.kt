package org.skepsun.kototoro.settings.sources.extensions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.databinding.FragmentInstalledExtensionsBinding

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

	private var adapter: InstalledExtensionsAdapter? = null

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): FragmentInstalledExtensionsBinding {
		return FragmentInstalledExtensionsBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentInstalledExtensionsBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		adapter = InstalledExtensionsAdapter()

		with(binding) {
			textEmptyTitle.setText(emptyTitleRes)
			textEmptyText.setText(emptyTextRes)
			recyclerView.layoutManager = LinearLayoutManager(context)
			recyclerView.adapter = adapter
			swipeRefresh.setOnRefreshListener(viewModel::refresh)
		}

		observeViewModel(binding)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

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
}
