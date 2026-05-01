package org.skepsun.kototoro.settings.sources.jsonsource
import org.skepsun.kototoro.core.util.ext.setSupportTitle

import android.os.Bundle
import android.text.InputType
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.lnreader.LNReaderRepository
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.ui.dialog.setEditText
import org.skepsun.kototoro.core.util.ext.addSupportMenuProvider
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.databinding.FragmentInstalledExtensionsBinding
import org.skepsun.kototoro.settings.sources.unified.UnifiedSourceKind
import org.skepsun.kototoro.settings.sources.unified.redirectToUnifiedSources

@AndroidEntryPoint
class LNReaderRepositoriesFragment : BaseFragment<FragmentInstalledExtensionsBinding>() {

	private val viewModel by viewModels<LNReaderRepositoriesViewModel>()
	private var adapter: LNReaderRepositoriesAdapter? = null
	private var redirected = false

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): FragmentInstalledExtensionsBinding {
		return FragmentInstalledExtensionsBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentInstalledExtensionsBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		adapter = LNReaderRepositoriesAdapter(::deleteRepo)
		with(binding) {
			recyclerView.layoutManager = LinearLayoutManager(context)
			recyclerView.adapter = adapter
			textEmptyTitle.setText(R.string.no_extension_repositories)
			textEmptyText.setText(R.string.no_extension_repositories_text)
			swipeRefresh.isEnabled = false // No refresh needed for LNReader repos
		}
		observeViewModel(binding)
		addSupportMenuProvider(RepositoriesMenuProvider())
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onResume() {
		super.onResume()
		if (!redirected) {
			redirected = true
			redirectToUnifiedSources(UnifiedSourceKind.LNREADER)
			return
		}
		setSupportTitle("LNReader Repositories")
	}

	override fun onDestroyView() {
		adapter = null
		super.onDestroyView()
	}

	private fun observeViewModel(binding: FragmentInstalledExtensionsBinding) {
		viewModel.repos.observe(viewLifecycleOwner) { repos ->
			adapter?.submitList(repos)
			binding.recyclerView.isVisible = repos.isNotEmpty()
			binding.emptyGroup.isVisible = repos.isEmpty()
		}
		viewModel.repoCount.observe(viewLifecycleOwner) { count ->
			binding.textExtensionCount.text = getString(R.string.extension_repo_count, count)
			binding.textExtensionCount.isVisible = count > 0
			binding.textSourceCount.isVisible = false
			binding.headerGroup.isVisible = count > 0
		}
		viewModel.onMessage.observeEvent(viewLifecycleOwner) { message ->
			Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
		}
	}

	private fun openAddDialog() {
		val builder = MaterialAlertDialogBuilder(requireContext())
		val presets = listOf(
			LNReaderRepository.OFFICIAL_REPO_URL
		)
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
					val url = presets[menuItem.itemId]
					MaterialAlertDialogBuilder(requireContext())
						.setTitle(R.string.recommended_repositories)
						.setMessage(R.string.welcome_plugins_disclaimer)
						.setPositiveButton(android.R.string.ok) { _, _ ->
							input.setText(url)
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

	private fun deleteRepo(repo: String) {
		viewModel.deleteRepo(repo)
	}

	private inner class RepositoriesMenuProvider : MenuProvider {
		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(R.menu.menu_extension_repositories, menu)
			menu.findItem(R.id.action_refresh_repositories)?.isVisible = false
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
			R.id.action_add_repository -> {
				openAddDialog()
				true
			}
			else -> false
		}
	}
}
