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
import org.skepsun.kototoro.core.extensions.GlobalExtensionManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.documentfile.provider.DocumentFile
import java.io.File
import android.net.Uri
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.getDisplayName
import org.skepsun.kototoro.core.util.ext.addSupportMenuProvider
import org.skepsun.kototoro.core.util.ext.invalidateSupportMenu
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.toLocaleOrNull
import org.skepsun.kototoro.databinding.FragmentInstalledExtensionsBinding
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import java.util.Locale
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.settings.sources.unified.redirectToUnifiedSources
import org.skepsun.kototoro.settings.sources.unified.toUnifiedSourceKind

@AndroidEntryPoint
class ExtensionsBrowserFragment : BaseFragment<FragmentInstalledExtensionsBinding>() {

	@Inject
	lateinit var settings: AppSettings

	private val viewModel by viewModels<ExtensionsBrowserViewModel>()
	private var adapter: ExtensionsBrowserAdapter? = null
	private var isSearchExpanded = false
	private var redirected = false
	private val installLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
		viewModel.onInstallActivityResult()
	}

	private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
		if (uri == null) return@registerForActivityResult
		viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
			try {
				val documentFile = DocumentFile.fromSingleUri(requireContext(), uri) ?: throw Exception("Invalid file URI")
				val fileName = documentFile.name ?: "plugin_${System.currentTimeMillis()}.jar"
				val pluginsDir = File(requireContext().filesDir, "plugins")
				if (!pluginsDir.exists()) pluginsDir.mkdirs()

				val destinationFile = File(pluginsDir, fileName)
				requireContext().contentResolver.openInputStream(uri)?.use { input ->
					destinationFile.outputStream().use { output ->
						input.copyTo(output)
					}
				} ?: throw Exception("Cannot open input stream")

				GlobalExtensionManager.initialize(requireContext())
				withContext(Dispatchers.Main) {
					Toast.makeText(requireContext(), "Imported plugin: $fileName", Toast.LENGTH_SHORT).show()
				}
			} catch (e: Exception) {
				withContext(Dispatchers.Main) {
					Toast.makeText(requireContext(), "Failed to import plugin: ${e.message}", Toast.LENGTH_SHORT).show()
				}
			}
		}
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
			::onExtensionClick,
		)
		with(binding) {
			recyclerView.layoutManager = createLayoutManager()
			recyclerView.adapter = adapter
			textEmptyTitle.setText(R.string.no_available_extensions)
			textEmptyText.setText(R.string.no_available_extensions_text)
			swipeRefresh.setOnRefreshListener(viewModel::refresh)
		}
		observeViewModel(binding)
		addSupportMenuProvider(BrowserMenuProvider())
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onResume() {
		super.onResume()
		if (!redirected) {
			redirected = true
			redirectToUnifiedSources(viewModel.type.toUnifiedSourceKind())
		}
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
		viewModel.availableLanguageCodes.observe(viewLifecycleOwner) {}

		viewModel.currentCollapsedLanguageGroups.observe(viewLifecycleOwner) {
			binding.recyclerView.layoutManager?.requestLayout()
		}
		viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
			binding.swipeRefresh.isRefreshing = isLoading
			binding.progressBar.isVisible = isLoading && adapter?.currentList.isNullOrEmpty()
			updateEmptyState(binding, adapter?.currentList.isNullOrEmpty())
		}
		viewModel.onInstallIntent.observeEvent(viewLifecycleOwner) { intent ->
			try {
				startActivity(intent)
				viewModel.onInstallActivityResult()
			} catch (e: Exception) {
				Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
			}
		}
		viewModel.onUninstallIntent.observeEvent(viewLifecycleOwner) { intent ->
			startActivity(intent)
		}
		viewModel.onStateDetails.observeEvent(viewLifecycleOwner, ::openStateDetailsDialog)
		viewModel.onMessage.observeEvent(viewLifecycleOwner) { message ->
			Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
		}
		viewModel.updateAllInProgress.observe(viewLifecycleOwner) {
			safeInvalidateOptionsMenu()
		}
		viewModel.updateCount.observe(viewLifecycleOwner) {
			safeInvalidateOptionsMenu()
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
		redirectToUnifiedSources(viewModel.type.toUnifiedSourceKind())
	}

	private fun onExtensionClick(item: ExtensionsBrowserListItem.Entry) {
		val sources = viewModel.getSourcesForPackage(item.pkgName)
		if (sources.isEmpty()) return
		if (sources.size == 1) {
			router.openList(sources.first(), null, null)
		} else {
			val displayNames = sources.map { 
				val lang = org.skepsun.kototoro.extensions.runtime.getExternalExtensionLanguageDisplayName(it.locale)
				if (lang.isNotEmpty()) lang else it.locale.uppercase()
			}.toTypedArray()
			com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
				.setTitle(item.name)
				.setItems(displayNames) { _, which ->
					router.openList(sources[which], null, null)
				}
				.show()
		}
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

			settings.extensionLanguages.isNotEmpty() -> {
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
		val availableCodes = viewModel.availableLanguageCodes.value
		val selectedCodes = settings.extensionLanguages.map { it.normalizeExtensionLanguageCode() }.toSet()

		val labels = availableCodes.map { code ->
			val locale = if (code.isBlank()) Locale.ROOT else code.toLocaleOrNull()
			locale.getDisplayName(requireContext())
		}.toTypedArray()

		val checkedItems = availableCodes.map { it in selectedCodes }.toBooleanArray()

		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.filter_extensions_by_language)
			.setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
				checkedItems[which] = isChecked
			}
			.setPositiveButton(android.R.string.ok) { _, _ ->
				val newSelected = availableCodes.filterIndexed { index, _ -> checkedItems[index] }.toSet()
				viewModel.setSelectedExtensionLanguages(newSelected)
				safeInvalidateOptionsMenu()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun createLayoutManager(): androidx.recyclerview.widget.RecyclerView.LayoutManager {
		return LinearLayoutManager(requireContext())
	}

	private inner class BrowserMenuProvider : MenuProvider, MenuItem.OnActionExpandListener, SearchView.OnQueryTextListener {
		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(R.menu.menu_available_extensions, menu)
			menu.findItem(R.id.action_local_search)?.run {
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
			menu.findItem(R.id.action_import_local_jar)?.isVisible = viewModel.type == ExternalExtensionType.JAR
			menu.findItem(R.id.action_filter_languages)?.title = if (settings.extensionLanguages.isEmpty()) {
				getString(R.string.filter_extensions_by_language)
			} else {
				val size = settings.extensionLanguages.size
				val selectedFirst = settings.extensionLanguages.first().let { code ->
					if (code.isBlank()) Locale.ROOT else code.toLocaleOrNull()
				}.getDisplayName(requireContext())
				if (size == 1) {
					getString(R.string.filter_extensions_by_language_with_value, selectedFirst)
				} else {
					getString(R.string.filter_extensions_by_language_with_value, "$selectedFirst +${size - 1}")
				}
			}
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

			R.id.action_manage_repositories -> {
				openRepositories()
				true
			}

			R.id.action_import_local_jar -> {
				importLauncher.launch("*/*")
				true
			}

			else -> false
		}

		override fun onMenuItemActionExpand(item: MenuItem): Boolean {
			(item.actionView as? SearchView)?.setQuery(viewModel.currentSearchQuery.value, false)
			isSearchExpanded = true
			return true
		}

		override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
			viewModel.setSearchQuery(null)
			isSearchExpanded = false
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

	/**
	 * Only invalidate the options menu when the search is not active,
	 * to prevent the SearchView from collapsing mid-typing.
	 */
	private fun safeInvalidateOptionsMenu() {
		if (!isSearchExpanded) {
			invalidateSupportMenu()
		}
	}

}
