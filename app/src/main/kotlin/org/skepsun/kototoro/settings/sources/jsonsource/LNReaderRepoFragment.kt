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
import coil3.ImageLoader
import coil3.asImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.error
import coil3.request.target
import androidx.core.content.ContextCompat
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.lnreader.LNReaderPluginInfo
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.databinding.FragmentLnreaderRepoBinding
import org.skepsun.kototoro.databinding.ItemLnreaderPluginBinding
import org.skepsun.kototoro.databinding.ItemLnreaderLangHeaderBinding
import androidx.core.view.WindowInsetsCompat
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import org.skepsun.kototoro.core.util.ext.addMenuProvider
import org.skepsun.kototoro.settings.SettingsActivity

@AndroidEntryPoint
class LNReaderRepoFragment : BaseFragment<FragmentLnreaderRepoBinding>() {

	@Inject
	lateinit var imageLoader: ImageLoader

	@Inject
	lateinit var appSettings: AppSettings

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

		val adapter = PluginAdapter(
			imageLoader = imageLoader,
			onInstall = { plugin ->
				viewModel.installPlugin(plugin)
				Toast.makeText(requireContext(), getString(R.string.installing_plugin, plugin.name), Toast.LENGTH_SHORT).show()
			},
			onUninstall = { plugin ->
				viewModel.uninstallPlugin(plugin)
				Toast.makeText(requireContext(), getString(R.string.deleting_plugin, plugin.name), Toast.LENGTH_SHORT).show()
			},
			onToggleLang = { lang ->
				viewModel.toggleLanguageGroup(lang)
			}
		)

		binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
		binding.recyclerView.adapter = adapter



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

		addMenuProvider(LNReaderMenuProvider())
	}

	override fun onResume() {
		super.onResume()
		activity?.setTitle(R.string.lnreader_plugins)
	}

	private fun openLanguageFilterDialog() {
		val availableCodes = viewModel.availableLanguages.value
		val selectedCodes = appSettings.extensionLanguages

		val labels = availableCodes.toTypedArray()
		val checkedItems = availableCodes.map { it in selectedCodes }.toBooleanArray()

		com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.filter_extensions_by_language)
			.setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
				checkedItems[which] = isChecked
			}
			.setPositiveButton(android.R.string.ok) { _, _ ->
				val newSelected = availableCodes.filterIndexed { index, _ -> checkedItems[index] }.toSet()
				viewModel.setSelectedExtensionLanguages(newSelected)
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private inner class LNReaderMenuProvider : MenuProvider {
		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(R.menu.menu_lnreader_repo, menu)
			
			val searchItem = menu.findItem(R.id.action_search)
			val searchView = searchItem.actionView as androidx.appcompat.widget.SearchView
			searchView.queryHint = getString(R.string.search_hint)
			
			searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
				override fun onQueryTextSubmit(query: String?): Boolean {
					viewModel.setSearchQuery(query.orEmpty())
					return true
				}
				override fun onQueryTextChange(newText: String?): Boolean {
					viewModel.setSearchQuery(newText.orEmpty())
					return true
				}
			})
			
			// Restore search query if it exists
			val currentQuery = viewModel.searchQuery.value
			if (currentQuery.isNotEmpty()) {
				searchItem.expandActionView()
				searchView.setQuery(currentQuery, false)
				searchView.clearFocus()
			}
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
			R.id.action_filter -> {
				openLanguageFilterDialog()
				true
			}
			R.id.action_manage_repositories -> {
				(activity as? SettingsActivity)?.openFragment(
					fragmentClass = LNReaderRepositoriesFragment::class.java,
					args = null,
					isFromRoot = false,
				)
				true
			}
			else -> false
		}
	}
}

// ==================== Adapter ====================

private class PluginAdapter(
	private val imageLoader: ImageLoader,
	private val onInstall: (LNReaderPluginInfo) -> Unit,
	private val onUninstall: (LNReaderPluginInfo) -> Unit,
	private val onToggleLang: (String) -> Unit,
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
				LangHeaderViewHolder(binding, onToggleLang)
			}
			else -> {
				val binding = ItemLnreaderPluginBinding.inflate(
					LayoutInflater.from(parent.context), parent, false
				)
				PluginViewHolder(binding, imageLoader, onInstall, onUninstall)
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
	private val onToggleLang: (String) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
	fun bind(item: PluginDisplayItem.LangHeader) {
		val prefix = if (item.isCollapsed) "\u25b6" else "\u25bc"
		binding.textViewLang.text = "$prefix ${item.lang} (${item.count})"
		binding.root.setOnClickListener { onToggleLang(item.lang) }
	}
}

private class PluginViewHolder(
	private val binding: ItemLnreaderPluginBinding,
	private val imageLoader: ImageLoader,
	private val onInstall: (LNReaderPluginInfo) -> Unit,
	private val onUninstall: (LNReaderPluginInfo) -> Unit,
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
				.error(ContextCompat.getDrawable(binding.root.context, R.drawable.ic_placeholder)?.asImage())
				.target(binding.imageViewIcon)
				.build()
			imageLoader.enqueue(request)
		} else {
			binding.imageViewIcon.setImageResource(R.drawable.ic_placeholder)
		}

		val isInstalling = installingIds.contains(plugin.id)
		binding.buttonInstall.isEnabled = !isInstalling
		binding.buttonInstall.text = when {
			isInstalling -> binding.root.context.getString(R.string.importing_)
			item.isInstalled -> binding.root.context.getString(R.string.uninstall)
			else -> binding.root.context.getString(R.string._install)
		}

		binding.buttonInstall.setOnClickListener {
			if (!isInstalling) {
				if (item.isInstalled) {
					onUninstall(plugin)
				} else {
					onInstall(plugin)
				}
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
