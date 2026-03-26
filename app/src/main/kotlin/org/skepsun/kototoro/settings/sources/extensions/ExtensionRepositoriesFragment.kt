package org.skepsun.kototoro.settings.sources.extensions

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.exceptions.resolve.ToastErrorObserver
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.ui.dialog.setEditText
import org.skepsun.kototoro.core.util.ext.addMenuProvider
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.databinding.FragmentInstalledExtensionsBinding
import org.skepsun.kototoro.extensions.repo.ExternalExtensionRepo
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType

@AndroidEntryPoint
class ExtensionRepositoriesFragment : BaseFragment<FragmentInstalledExtensionsBinding>() {

	private val viewModel by viewModels<ExtensionRepositoriesViewModel>()
	private var adapter: ExtensionRepositoriesAdapter? = null

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): FragmentInstalledExtensionsBinding {
		return FragmentInstalledExtensionsBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentInstalledExtensionsBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		adapter = ExtensionRepositoriesAdapter(::openWebsite, ::deleteRepo)
		with(binding) {
			recyclerView.layoutManager = LinearLayoutManager(context)
			recyclerView.adapter = adapter
			textEmptyTitle.setText(R.string.no_extension_repositories)
			textEmptyText.setText(R.string.no_extension_repositories_text)
			swipeRefresh.setOnRefreshListener(viewModel::refresh)
		}
		observeViewModel(binding)
		addMenuProvider(RepositoriesMenuProvider())
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onResume() {
		super.onResume()
		activity?.setTitle(
			when (viewModel.type) {
				ExternalExtensionType.MIHON -> R.string.mihon_extension_repositories
				ExternalExtensionType.ANIYOMI -> R.string.aniyomi_extension_repositories
				ExternalExtensionType.IREADER -> R.string.ireader_extension_repositories
				ExternalExtensionType.JAR -> R.string.jar_extension_repositories
			},
		)
	}

	override fun onDestroyView() {
		adapter = null
		super.onDestroyView()
	}

	private fun observeViewModel(binding: FragmentInstalledExtensionsBinding) {
		viewModel.repos.observe(viewLifecycleOwner) { repos ->
			adapter?.submitList(repos)
			binding.recyclerView.isVisible = repos.isNotEmpty()
			binding.emptyGroup.isVisible = repos.isEmpty() && !viewModel.isLoading.value
		}
		viewModel.repoCount.observe(viewLifecycleOwner) { count ->
			binding.textExtensionCount.text = getString(R.string.extension_repo_count, count)
			binding.textExtensionCount.isVisible = count > 0
			binding.textSourceCount.isVisible = false
			binding.headerGroup.isVisible = count > 0
		}
		viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
			binding.swipeRefresh.isRefreshing = isLoading
			binding.progressBar.isVisible = isLoading && adapter?.currentList.isNullOrEmpty()
		}
		viewModel.onMessage.observeEvent(viewLifecycleOwner) { message ->
			Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
		}
		viewModel.onError.observeEvent(viewLifecycleOwner, ToastErrorObserver(binding.root, this))
		viewModel.onTrustPrompt.observeEvent(viewLifecycleOwner, ::openTrustDialog)
	}

	private fun openAddDialog() {
		val builder = MaterialAlertDialogBuilder(requireContext())
		val presets = when (viewModel.type) {
			ExternalExtensionType.MIHON -> listOf(
				"https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
				"https://raw.githubusercontent.com/yuzono/manga-repo/repo/index.min.json",
			)
			ExternalExtensionType.ANIYOMI -> listOf(
				"https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo/index.min.json",
				"https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json",
				"https://raw.githubusercontent.com/KudoAni/aniyomi-extensions/repo/index.min.json",
			)
			ExternalExtensionType.IREADER -> listOf(
				"https://raw.githubusercontent.com/IReaderorg/IReader-extensions/repov2/index.min.json",
			)
			ExternalExtensionType.JAR -> listOf(
				"https://raw.githubusercontent.com/skepsun/kototoro-parsers/repo/index.min.json",
			)
		}
		val input = builder.setEditText(presets, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, false)
		input.hint = getString(R.string.extension_repository_url_hint)

		val dialog = builder
			.setTitle(R.string.add_extension_repository)
			.setMessage(R.string.add_extension_repository_message)
			.setPositiveButton(android.R.string.ok, null)
			.setNegativeButton(android.R.string.cancel, null)
			.setNeutralButton(R.string.recommended_repositories, null)
			.create()
		dialog.setOnShowListener {
			dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
				val popupMenu = androidx.appcompat.widget.PopupMenu(requireContext(), it)
				presets.forEachIndexed { index, url ->
					popupMenu.menu.add(0, index, 0, url)
				}
				popupMenu.setOnMenuItemClickListener { menuItem ->
					input.setText(presets[menuItem.itemId])
					true
				}
				popupMenu.show()
			}
			dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
				val value = input.text?.toString().orEmpty()
				if (value.isBlank()) {
					input.error = getString(R.string.extension_repository_url_required)
				} else {
					viewModel.addRepo(value)
					dialog.dismiss()
				}
			}
		}
		dialog.show()
	}

	private fun openWebsite(repo: ExternalExtensionRepo) {
		startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, repo.website.toUri()))
	}

	private fun openTrustDialog(repo: ExternalExtensionRepo) {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.trust_extension_repository)
			.setMessage(
				getString(
					R.string.trust_extension_repository_message,
					repo.displayName,
					repo.website,
					repo.signingKeyFingerprint.formatExtensionFingerprint(),
				),
			)
			.setPositiveButton(R.string.trust_and_add) { _, _ ->
				viewModel.confirmAddRepo(repo)
			}
			.setNeutralButton(R.string.open_website) { _, _ ->
				openWebsite(repo)
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun deleteRepo(repo: ExternalExtensionRepo) {
		viewModel.deleteRepo(repo)
	}

	private inner class RepositoriesMenuProvider : MenuProvider {
		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(R.menu.menu_extension_repositories, menu)
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
			R.id.action_add_repository -> {
				openAddDialog()
				true
			}

			R.id.action_refresh_repositories -> {
				viewModel.refresh()
				true
			}

			else -> false
		}
	}
}
