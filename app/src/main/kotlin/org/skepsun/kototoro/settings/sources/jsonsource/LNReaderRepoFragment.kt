package org.skepsun.kototoro.settings.sources.jsonsource

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.lnreader.LNReaderPluginInfo
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.databinding.FragmentLnreaderRepoBinding
import org.skepsun.kototoro.databinding.ItemLnreaderPluginBinding
import org.skepsun.kototoro.databinding.ItemLnreaderLangHeaderBinding
import androidx.core.view.WindowInsetsCompat

@AndroidEntryPoint
class LNReaderRepoFragment : BaseFragment<FragmentLnreaderRepoBinding>() {

	private val viewModel: LNReaderRepoViewModel by viewModels()

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): FragmentLnreaderRepoBinding {
		return FragmentLnreaderRepoBinding.inflate(inflater, container, false)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onViewBindingCreated(binding: FragmentLnreaderRepoBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)

		val adapter = PluginAdapter { plugin ->
			viewModel.installPlugin(plugin)
			Toast.makeText(requireContext(), getString(R.string.installing_plugin, plugin.name), Toast.LENGTH_SHORT).show()
		}

		binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
		binding.recyclerView.adapter = adapter

		binding.editTextSearch.doAfterTextChanged { text ->
			viewModel.setSearchQuery(text?.toString().orEmpty())
		}

		viewModel.displayPlugins.observe(viewLifecycleOwner) { items ->
			adapter.submitList(items)
			binding.textViewEmpty.isVisible = items.isEmpty() && viewModel.uiState.value !is RepoUiState.Loading
		}

		viewModel.uiState.observe(viewLifecycleOwner) { state ->
			binding.progressBar.isVisible = state is RepoUiState.Loading
			when (state) {
				is RepoUiState.Error -> {
					binding.textViewEmpty.isVisible = true
					binding.textViewEmpty.text = state.message
				}
				is RepoUiState.Loaded -> {
					binding.textViewEmpty.isVisible = false
				}
				else -> {}
			}
		}

		viewModel.installingPluginIds.observe(viewLifecycleOwner) {
			adapter.installingIds = it
			adapter.notifyDataSetChanged()
		}

		// Auto-load on creation
		if (viewModel.uiState.value is RepoUiState.Idle) {
			viewModel.loadPlugins()
		}
	}

	override fun onResume() {
		super.onResume()
		activity?.setTitle(R.string.lnreader_plugins)
	}
}

// ==================== Adapter ====================

private class PluginAdapter(
	private val onInstall: (LNReaderPluginInfo) -> Unit,
) : ListAdapter<PluginDisplayItem, RecyclerView.ViewHolder>(PluginDiffCallback()) {

	var installingIds: Set<String> = emptySet()

	companion object {
		private const val TYPE_HEADER = 0
		private const val TYPE_PLUGIN = 1
	}

	override fun getItemViewType(position: Int): Int = when (getItem(position)) {
		is PluginDisplayItem.LangHeader -> TYPE_HEADER
		is PluginDisplayItem.Plugin -> TYPE_PLUGIN
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		return when (viewType) {
			TYPE_HEADER -> {
				val binding = ItemLnreaderLangHeaderBinding.inflate(
					LayoutInflater.from(parent.context), parent, false
				)
				LangHeaderViewHolder(binding)
			}
			else -> {
				val binding = ItemLnreaderPluginBinding.inflate(
					LayoutInflater.from(parent.context), parent, false
				)
				PluginViewHolder(binding, onInstall)
			}
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (val item = getItem(position)) {
			is PluginDisplayItem.LangHeader -> (holder as LangHeaderViewHolder).bind(item)
			is PluginDisplayItem.Plugin -> (holder as PluginViewHolder).bind(item, installingIds)
		}
	}
}

private class LangHeaderViewHolder(
	private val binding: ItemLnreaderLangHeaderBinding,
) : RecyclerView.ViewHolder(binding.root) {
	fun bind(item: PluginDisplayItem.LangHeader) {
		binding.textViewLang.text = "${item.lang} (${item.count})"
	}
}

private class PluginViewHolder(
	private val binding: ItemLnreaderPluginBinding,
	private val onInstall: (LNReaderPluginInfo) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
	fun bind(item: PluginDisplayItem.Plugin, installingIds: Set<String>) {
		val plugin = item.info
		binding.textViewName.text = plugin.name
		binding.textViewSite.text = plugin.site
		binding.textViewVersion.text = "v${plugin.version}"

		if (plugin.iconUrl.isNotBlank()) {
			val request = ImageRequest.Builder(binding.root.context)
				.data(plugin.iconUrl)
				.crossfade(true)
				.error(R.drawable.ic_placeholder)
				.target(binding.imageViewIcon)
				.build()
			binding.root.context.imageLoader.enqueue(request)
		} else {
			binding.imageViewIcon.setImageResource(R.drawable.ic_placeholder)
		}

		val isInstalling = installingIds.contains(plugin.id)
		binding.buttonInstall.isEnabled = !isInstalling && !item.isInstalled
		binding.buttonInstall.text = when {
			isInstalling -> binding.root.context.getString(R.string.importing_)
			item.isInstalled -> binding.root.context.getString(R.string.installed)
			else -> binding.root.context.getString(R.string._install)
		}

		binding.buttonInstall.setOnClickListener {
			if (!item.isInstalled && !isInstalling) {
				onInstall(plugin)
			}
		}
	}
}

private class PluginDiffCallback : DiffUtil.ItemCallback<PluginDisplayItem>() {
	override fun areItemsTheSame(oldItem: PluginDisplayItem, newItem: PluginDisplayItem): Boolean {
		return when {
			oldItem is PluginDisplayItem.LangHeader && newItem is PluginDisplayItem.LangHeader ->
				oldItem.lang == newItem.lang
			oldItem is PluginDisplayItem.Plugin && newItem is PluginDisplayItem.Plugin ->
				oldItem.info.id == newItem.info.id
			else -> false
		}
	}

	override fun areContentsTheSame(oldItem: PluginDisplayItem, newItem: PluginDisplayItem): Boolean {
		return oldItem == newItem
	}
}
