package org.skepsun.kototoro.settings.sources.extensions

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import org.skepsun.kototoro.core.util.ext.addSupportMenuProvider
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.setSupportTitle
import org.skepsun.kototoro.databinding.FragmentInstalledExtensionsBinding
import org.skepsun.kototoro.databinding.ViewDialogAutocompleteBinding
import org.skepsun.kototoro.extensions.repo.ExternalExtensionRepo
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import org.skepsun.kototoro.settings.sources.unified.UnifiedRecommendedRepositories
import org.skepsun.kototoro.settings.sources.unified.redirectToUnifiedSources
import org.skepsun.kototoro.settings.sources.unified.toUnifiedSourceKind

@AndroidEntryPoint
class ExtensionRepositoriesFragment : BaseFragment<FragmentInstalledExtensionsBinding>() {

	private val viewModel by viewModels<ExtensionRepositoriesViewModel>()
	private var adapter: ExtensionRepositoriesAdapter? = null
	private var redirected = false

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): FragmentInstalledExtensionsBinding {
		return FragmentInstalledExtensionsBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentInstalledExtensionsBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		adapter = ExtensionRepositoriesAdapter(
			onOpenWebsite = ::openWebsite,
			onDelete = ::deleteRepo,
			onUpdate = { repo -> viewModel.performUpdate(repo) },
		)
		with(binding) {
			recyclerView.layoutManager = LinearLayoutManager(context)
			recyclerView.adapter = adapter
			textEmptyTitle.setText(R.string.no_extension_repositories)
			textEmptyText.setText(R.string.no_extension_repositories_text)
			swipeRefresh.setOnRefreshListener(viewModel::refresh)
		}
		observeViewModel(binding)
		addSupportMenuProvider(RepositoriesMenuProvider())

		arguments?.getString("add_repo_url")?.let { url ->
			arguments?.remove("add_repo_url")
			openAddDialog(url)
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onResume() {
		super.onResume()
		if (!redirected) {
			redirected = true
			redirectToUnifiedSources(viewModel.type.toUnifiedSourceKind())
			return
		}
		setSupportTitle(
			when (viewModel.type) {
				ExternalExtensionType.MIHON -> R.string.mihon_extension_repositories
				ExternalExtensionType.ANIYOMI -> R.string.aniyomi_extension_repositories
				ExternalExtensionType.IREADER -> R.string.ireader_extension_repositories
				ExternalExtensionType.JAR -> R.string.jar_extension_repositories
				ExternalExtensionType.CLOUDSTREAM -> R.string.cloudstream_extension_repositories
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
		viewModel.updatesAvailable.observe(viewLifecycleOwner) { updates ->
			adapter?.setUpdates(updates)
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

	private fun openAddDialog(prefillUrl: String? = null) {
		val builder = MaterialAlertDialogBuilder(requireContext())
		val recommendedRepos = getRecommendedRepos()
		val inputBinding = ViewDialogAutocompleteBinding.inflate(layoutInflater).apply {
			autoCompleteTextView.setAdapter(
				ArrayAdapter(
					requireContext(),
					android.R.layout.simple_spinner_dropdown_item,
					viewModel.inputHistory.value,
				),
			)
			autoCompleteTextView.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
			dropdown.setOnClickListener {
				autoCompleteTextView.showDropDown()
			}
		}
		builder.setView(inputBinding.root)
		val input = inputBinding.autoCompleteTextView
		input.hint = getString(R.string.extension_repository_url_hint)
		if (!prefillUrl.isNullOrEmpty()) {
			input.setText(prefillUrl)
		}
		input.post {
			input.setSelection(input.text?.length ?: 0)
		}

		val dialog = builder
			.setTitle(R.string.add_extension_repository)
			.setMessage(R.string.add_extension_repository_message)
			.setPositiveButton(android.R.string.ok, null)
			.setNegativeButton(android.R.string.cancel, null)
			.setNeutralButton(R.string.recommended_repositories, null)
			.create()
		dialog.setOnShowListener {
			dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener { anchor ->
				val popupMenu = androidx.appcompat.widget.PopupMenu(requireContext(), anchor)
				recommendedRepos.forEachIndexed { index, repo ->
					popupMenu.menu.add(0, index, 0, repo.name)
				}
				popupMenu.setOnMenuItemClickListener { menuItem ->
					val repo = recommendedRepos[menuItem.itemId]
					MaterialAlertDialogBuilder(requireContext())
						.setTitle(repo.name)
						.setMessage(R.string.welcome_plugins_disclaimer)
						.setPositiveButton(android.R.string.ok) { _, _ ->
							input.setText(repo.url)
							input.setSelection(input.text?.length ?: 0)
						}
						.setNegativeButton(android.R.string.cancel, null)
						.show()
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

	private fun getRecommendedRepos(): List<RecommendedRepo> {
		return UnifiedRecommendedRepositories.byExternalType(viewModel.type).map { repo ->
			RecommendedRepo(
				name = repo.name,
				url = repo.url,
			)
		}
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

	private data class RecommendedRepo(
		val name: String,
		val url: String,
	)
}
